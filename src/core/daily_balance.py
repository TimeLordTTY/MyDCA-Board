# -*- coding: utf-8 -*-
"""
账户余额快照模块

支持两种存储模式：
1. CSV 文件存储（默认）
2. MySQL 数据库存储（配置 use_database=true）

生成 daily_balance.csv，展示各账户余额和关联产品市值。

核心设计原则：
- balance 永远来自 ledger 计算（本金分桶余额），不含收益
- product_value 是展示口径：默认等于 balance；profit_account 额外包含收益
- diff 仅 profit_account 和汇总行显示收益/亏损
- 收益分配是纯展示层优化，不写入 ledger，不改变本金口径

账户类型说明：
- cash：现金账户（余利宝生活费、余利宝理财金等）
- fund_mapped：映射到基金的账户（小荷包 -> 000686），收益自动入账
- product_sub：产品子账户（稳利宝子账户），收益展示分配到 profit_account
- fund_total：基金账户汇总
- summary：汇总行

字段说明：
- fetch_date：快照日期
- account_id：账户ID
- account_name：账户名称  
- account_type：账户类型
- balance：账户余额（从ledger计算，本金分桶）
- related_product：关联产品代码
- product_value：展示市值（profit_account = balance + group_profit）
- diff：收益/差异（仅 profit_account 和汇总行显示）
- note：备注

收益分配展示规则（product_sub）：
1. group_profit = 父产品 total_value - 子账户 balance 合计
2. profit_account 查找：account_groups.profit_account 或 receives_profit=true
3. profit_account 行：product_value = balance + group_profit, diff = group_profit
4. 其他子账户行：product_value = balance, diff 为空

小荷包收益计算规则（fund_mapped）：
- 关联货币基金（如 000686 建信嘉薪宝）
- 收益公式：日收益 = 持有份额 / 10000 * 万份收益
- 余额：balance = 市值 + 当日收益（收益自动入账）
"""
import csv
import glob
import logging
import os
from datetime import datetime, date, timedelta
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from utils.trade_calendar import is_trade_day, prev_trade_day

logger = logging.getLogger(__name__)

# 导入数据库模块（强制使用数据库）
from data.db_connector import execute_query, execute_one, execute_insert, execute_many


def _use_database() -> bool:
    """始终使用数据库"""
    return True

# 字段顺序
FIELDNAMES = [
    'fetch_date', 'account_id', 'account_name', 'account_type',
    'balance', 'related_product', 'product_value', 'diff', 'note'
]

CHINESE_HEADERS = {
    'fetch_date': '采集日期',
    'account_id': '账户ID',
    'account_name': '账户名称',
    'account_type': '账户类型',
    'balance': '账户余额',
    'related_product': '关联产品',
    'product_value': '产品市值',
    'diff': '差异',
    'note': '备注'
}


def safe_decimal(value, default=Decimal('0')) -> Decimal:
    """安全解析为Decimal"""
    if isinstance(value, Decimal):
        return value
    if value is None or value == '' or value == '-':
        return default
    try:
        return Decimal(str(value).strip().replace(',', ''))
    except:
        return default


def load_money_fund_yield(project_root: Path, product_code: str, nav_date: str = None) -> Optional[Decimal]:
    """
    加载货币基金的万份收益（从数据库读取）
    
    :param project_root: 项目根目录（已忽略）
    :param product_code: 产品代码（如 000686）
    :param nav_date: 净值日期，None则取最新
    :return: 万份收益，或 None（如果找不到）
    """
    if nav_date:
        sql = "SELECT nav FROM nav WHERE product_code = %s AND nav_date = %s"
        result = execute_one(sql, (product_code, nav_date))
    else:
        sql = """
            SELECT nav FROM nav 
            WHERE product_code = %s 
            ORDER BY nav_date DESC 
            LIMIT 1
        """
        result = execute_one(sql, (product_code,))
    
    if result and result.get('nav'):
        return safe_decimal(result['nav'])
    return None


def calc_money_fund_daily_income(shares: Decimal, yield_per_10k: Decimal) -> Decimal:
    """
    计算货币基金日收益
    
    :param shares: 持有份额
    :param yield_per_10k: 万份收益
    :return: 日收益
    """
    if shares <= 0 or yield_per_10k <= 0:
        return Decimal('0')
    
    # 日收益 = 持有份额 / 10000 * 万份收益
    daily_income = (shares / Decimal('10000')) * yield_per_10k
    # 四舍五入到两位小数
    return daily_income.quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)


def load_accounts(project_root: Path) -> Dict:
    """加载账户配置"""
    import json
    accounts_path = project_root / "config" / "accounts.json"
    if not accounts_path.exists():
        return {"accounts": [], "account_groups": {}}
    
    with open(accounts_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def load_ledger(project_root: Path) -> List[Dict]:
    """加载账本记录（从数据库）"""
    sql = """
        SELECT DATE_FORMAT(event_time, '%%Y-%%m-%%d %%H:%%i:%%s') as event_time,
               entry_type, amount, category_l1, category_l2,
               account_from, account_to, discount,
               CASE WHEN reimbursable = 1 THEN 'y' ELSE '' END as reimbursable,
               note
        FROM ledger
        ORDER BY event_time, id
    """
    return execute_query(sql)


def load_daily_csv(project_root: Path, fetch_date: str = None) -> Dict[str, Dict]:
    """
    加载 daily 数据（从数据库），返回产品市值信息
    
    :param project_root: 项目根目录（已忽略）
    :param fetch_date: 指定日期，None则取最新
    :return: {product_code: {value, shares, nav, ...}}
    """
    # 如果没有指定日期，获取最新日期
    if not fetch_date:
        date_sql = "SELECT MAX(fetch_date) as latest_date FROM daily_snapshot"
        date_result = execute_one(date_sql)
        if not date_result or not date_result.get('latest_date'):
            return {}
        fetch_date = date_result['latest_date']
        if hasattr(fetch_date, 'strftime'):
            fetch_date = fetch_date.strftime('%Y-%m-%d')
    
    sql = """
        SELECT DATE_FORMAT(fetch_date, '%%Y-%%m-%%d') as fetch_date,
               product_code, product_name, category,
               `value`, total_value, shares, nav, cash_in_transit
        FROM daily_snapshot
        WHERE fetch_date = %s
    """
    rows = execute_query(sql, (fetch_date,))
    
    products = {}
    for row in rows:
        product_code = row.get('product_code', '')
        if not product_code:
            continue
        
        products[product_code] = {
            'fetch_date': row.get('fetch_date', ''),
            'product_name': row.get('product_name', ''),
            'value': safe_decimal(row.get('value', '0')),
            'total_value': safe_decimal(row.get('total_value', '0')),
            'shares': safe_decimal(row.get('shares', '0')),
            'nav': safe_decimal(row.get('nav', '0')),
            'cash_in_transit': safe_decimal(row.get('cash_in_transit', '0')),
            'category': row.get('category', 'fund')
        }
    
    return products


def calc_account_balance(account_id: str, ledger: List[Dict], 
                         initial_balance: Decimal = Decimal('0'),
                         as_of_date: str = None) -> Decimal:
    """
    计算账户余额 = 初始余额 + Σ(入账) - Σ(出账)
    
    入账：
    - income 且 account_to == account_id
    - transfer 且 account_to == account_id
    
    出账：
    - expense 且 account_from == account_id
    - transfer 且 account_from == account_id
    """
    balance = initial_balance
    
    for record in ledger:
        entry_type = record.get('entry_type', '').lower()
        amount = safe_decimal(record.get('amount', '0'))
        account_from = record.get('account_from', '')
        account_to = record.get('account_to', '')
        event_time = record.get('event_time', '')
        
        # 日期过滤
        if as_of_date and event_time[:10] > as_of_date:
            continue
        
        if entry_type == 'income':
            if account_to == account_id:
                balance += amount
        elif entry_type == 'expense':
            if account_from == account_id:
                balance -= amount
        elif entry_type == 'transfer':
            if account_to == account_id:
                balance += amount
            if account_from == account_id:
                balance -= amount
    
    return balance


def get_account_type(account: Dict) -> str:
    """判断账户类型"""
    if account.get('is_fund_account'):
        return 'fund_total'
    
    linked_product = account.get('linked_product')
    group = account.get('group')
    
    if group == 'wenlibao' or (linked_product and linked_product.startswith('FBAE')):
        return 'product_sub'
    elif linked_product:
        return 'fund_mapped'
    else:
        return 'cash'


def _find_profit_account(group_id: str, group_config: Dict, accounts: List[Dict]) -> Optional[str]:
    """
    查找收益归属账户
    
    优先级：
    1. account_groups[group_id].profit_account
    2. 该 group 内 receives_profit=true 的子账户
    3. 找不到返回 None
    
    :param group_id: 组ID（如 'wenlibao'）
    :param group_config: 组配置
    :param accounts: 所有账户列表
    :return: profit_account 的 account_id，或 None
    """
    # 优先级1：从 group_config 读取 profit_account
    profit_account = group_config.get('profit_account')
    if profit_account:
        return profit_account
    
    # 优先级2：在该 group 的子账户中寻找 receives_profit=true
    for acc in accounts:
        if acc.get('group') == group_id and acc.get('receives_profit'):
            return acc.get('id')
    
    return None


def _get_profit_account_name(profit_account_id: str, accounts: List[Dict]) -> str:
    """获取 profit_account 的账户名称"""
    for acc in accounts:
        if acc.get('id') == profit_account_id:
            return acc.get('name', profit_account_id)
    return profit_account_id


def generate_daily_balance(project_root: Path, fetch_date: str = None) -> List[Dict]:
    """
    生成账户余额快照
    
    设计说明：
    - balance 永远来自 ledger 计算（本金分桶余额），不含收益
    - product_value 是展示口径：
      - 默认等于 balance
      - profit_account 额外包含 group_profit（收益/亏损）
    - diff 仅 profit_account 显示 group_profit
    
    :param project_root: 项目根目录
    :param fetch_date: 快照日期，None则使用今天
    :return: 账户余额记录列表
    """
    if not fetch_date:
        fetch_date = date.today().strftime('%Y-%m-%d')
    
    # 加载数据
    accounts_config = load_accounts(project_root)
    accounts = accounts_config.get('accounts', [])
    account_groups = accounts_config.get('account_groups', {})
    ledger = load_ledger(project_root)
    daily_products = load_daily_csv(project_root, fetch_date)
    
    records = []
    
    # 统计变量
    fund_total = Decimal('0')
    fund_value = Decimal('0')  # 纯市值（份额×净值）
    fund_transit = Decimal('0')  # 在途资金
    ylb_total = Decimal('0')  # 余利宝合计
    
    # 获取所有 fund_mapped 账户关联的产品代码（这些不计入基金总和）
    fund_mapped_products = set()
    for acc in accounts:
        if get_account_type(acc) == 'fund_mapped':
            linked = acc.get('linked_product')
            if linked:
                fund_mapped_products.add(linked)
    
    # ========== 第一阶段：收集所有子账户信息（用于后续收益分配） ==========
    # group_sub_info: {group_id: {account_id: {'balance': Decimal, 'record_idx': int, ...}}}
    group_sub_info: Dict[str, Dict[str, Dict]] = {}
    # account_id -> record 索引映射
    account_record_map: Dict[str, int] = {}
    
    for account in accounts:
        account_id = account.get('id', '')
        account_name = account.get('name', '')
        linked_product = account.get('linked_product')
        initial_balance = safe_decimal(account.get('initial_balance', '0'))
        account_type = get_account_type(account)
        group = account.get('group', '')
        
        # 计算账户余额（从 ledger 计算）
        balance = calc_account_balance(account_id, ledger, initial_balance, fetch_date)
        
        # 获取关联产品市值
        product_value = Decimal('0')
        diff = Decimal('0')
        note = account.get('note', '')
        
        # 基金账户特殊处理：value = daily.csv 所有基金市值之和（排除 fund_mapped 关联产品）
        if account_type == 'fund_total':
            for code, info in daily_products.items():
                # 只计入 category='fund' 且不是 fund_mapped 关联产品的基金
                if info.get('category') == 'fund' and code not in fund_mapped_products:
                    fund_value += info.get('value', Decimal('0'))
                    fund_transit += info.get('cash_in_transit', Decimal('0'))
            fund_total = fund_value + fund_transit
            balance = fund_total
            product_value = fund_total
        
        # 产品子账户（稳利宝等）：先记录 balance，product_value/diff 在第二阶段设置
        elif account_type == 'product_sub':
            # 收集到 group_sub_info
            if group:
                if group not in group_sub_info:
                    group_sub_info[group] = {}
                group_sub_info[group][account_id] = {
                    'balance': balance,
                    'name': account_name,
                    'linked_product': linked_product,
                }
            # 暂时 product_value = balance，后续会更新 profit_account
            product_value = balance
        
        # 基金映射账户（小荷包）：余额 = 关联产品市值 + 当日收益
        elif account_type == 'fund_mapped' and linked_product:
            if linked_product in daily_products:
                product_info = daily_products[linked_product]
                product_value = product_info.get('total_value', Decimal('0'))
                shares = product_info.get('shares', Decimal('0'))
                
                # 计算货币基金当日收益
                daily_income = Decimal('0')
                yield_per_10k = load_money_fund_yield(project_root, linked_product)
                if yield_per_10k and shares > 0:
                    daily_income = calc_money_fund_daily_income(shares, yield_per_10k)
                
                # 小荷包余额 = 市值 + 当日收益（收益自动入账）
                ledger_balance = balance
                balance = product_value + daily_income
                product_value = balance
                
                if daily_income > 0:
                    note = f"万份收益={yield_per_10k}，日收益=+{daily_income}"
                
                diff = balance - ledger_balance
        
        # 余利宝账户：统计到余利宝合计
        elif account_type == 'cash':
            ylb_group = account_groups.get('ylb', {})
            ylb_account_ids = ylb_group.get('accounts', [])
            if account_id in ylb_account_ids:
                ylb_total += balance
        
        record = {
            'fetch_date': fetch_date,
            'account_id': account_id,
            'account_name': account_name,
            'account_type': account_type,
            'balance': f"{balance:.2f}",
            'related_product': linked_product or '',
            'product_value': f"{product_value:.2f}" if product_value > 0 else '',
            'diff': f"{diff:.2f}" if diff != 0 else '',
            'note': note
        }
        records.append(record)
        account_record_map[account_id] = len(records) - 1
    
    # ========== 第二阶段：为每个 account_group 分配收益展示 ==========
    # 存储汇总行信息（稍后添加）
    summary_rows = []
    
    for group_id, group_config in account_groups.items():
        linked_product = group_config.get('linked_product')
        if not linked_product:
            continue  # 该 group 没有关联产品，跳过
        
        # 获取父产品 total_value
        parent_product = daily_products.get(linked_product, {})
        parent_total_value = parent_product.get('total_value', Decimal('0'))
        
        if parent_total_value == 0:
            # 父产品数据缺失，不做分配展示
            continue
        
        # 获取该 group 的子账户信息
        sub_accounts = group_sub_info.get(group_id, {})
        if not sub_accounts:
            continue
        
        # 计算子账户余额合计
        sub_total_balance = sum(info['balance'] for info in sub_accounts.values())
        
        # 计算 group_profit（收益/亏损）
        group_profit = parent_total_value - sub_total_balance
        
        # 查找 profit_account
        profit_account_id = _find_profit_account(group_id, group_config, accounts)
        profit_account_name = _get_profit_account_name(profit_account_id, accounts) if profit_account_id else None
        
        # 更新子账户的 product_value 和 diff
        for acc_id, info in sub_accounts.items():
            if acc_id not in account_record_map:
                continue
            
            record_idx = account_record_map[acc_id]
            record = records[record_idx]
            balance = info['balance']
            
            if profit_account_id and acc_id == profit_account_id:
                # profit_account：product_value = balance + group_profit, diff = group_profit
                display_value = balance + group_profit
                record['product_value'] = f"{display_value:.2f}"
                record['diff'] = f"{group_profit:.2f}"
                # 更新 note
                original_note = record.get('note', '')
                profit_note = f"（含收益展示 {group_profit:+.2f}）"
                if original_note:
                    record['note'] = f"{original_note} {profit_note}"
                else:
                    record['note'] = profit_note.strip('（）')
            else:
                # 其他子账户：product_value = balance, diff 为空
                record['product_value'] = f"{balance:.2f}"
                record['diff'] = ''
        
        # 添加汇总行
        summary_note = f"收益={group_profit:.2f}"
        if profit_account_name:
            summary_note += f"（展示归入：{profit_account_name}）"
        else:
            summary_note += "（未配置 profit_account）"
        
        summary_rows.append({
            'fetch_date': fetch_date,
            'account_id': f'{group_id}_total',
            'account_name': f"{group_config.get('name', group_id)}(合计)",
            'account_type': 'summary',
            'balance': f"{sub_total_balance:.2f}",
            'related_product': linked_product,
            'product_value': f"{parent_total_value:.2f}",
            'diff': f"{group_profit:.2f}",
            'note': summary_note
        })
    
    # 添加汇总行（优先添加已处理的 group 汇总）
    records.extend(summary_rows)
    
    # 添加余利宝汇总行（如果有）
    if ylb_total > 0:
        records.append({
            'fetch_date': fetch_date,
            'account_id': 'ylb_total',
            'account_name': '余利宝(合计)',
            'account_type': 'summary',
            'balance': f"{ylb_total:.2f}",
            'related_product': '',
            'product_value': '',
            'diff': '',
            'note': '余利宝生活费+理财金合计'
        })
    
    # 添加基金汇总行（如果有）
    if fund_total > 0:
        # 构建备注，展示市值和在途资金分解
        if fund_transit > 0:
            fund_note = f"市值{fund_value:.2f} + 在途{fund_transit:.2f}（不含货币基金）"
        else:
            fund_note = f"市值{fund_value:.2f}（不含货币基金）"
        
        records.append({
            'fetch_date': fetch_date,
            'account_id': 'fund_total',
            'account_name': '基金(合计)',
            'account_type': 'summary',
            'balance': f"{fund_total:.2f}",
            'related_product': '',
            'product_value': f"{fund_total:.2f}",
            'diff': '',
            'note': fund_note
        })
    
    return records


def write_daily_balance(records: List[Dict], output_path: Path):
    """
    写入账户余额快照文件
    
    使用原子写入：先写临时文件，再替换
    同时同步到数据库（如果启用）
    """
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    tmp_path = output_path.parent / f"{output_path.stem}.tmp"
    
    try:
        with open(tmp_path, 'w', newline='', encoding='utf-8-sig') as f:
            writer = csv.DictWriter(f, fieldnames=FIELDNAMES)
            writer.writeheader()
            
            # 写入中文表头行
            chinese_row = {field: CHINESE_HEADERS.get(field, field) for field in FIELDNAMES}
            writer.writerow(chinese_row)
            
            for record in records:
                writer.writerow(record)
        
        # 原子替换（os.replace 是真正的原子操作）
        os.replace(str(tmp_path), str(output_path))
        
        logger.info(f"✓ 写入账户余额快照: {output_path} ({len(records)} 条)")
        
    except Exception as e:
        if tmp_path.exists():
            tmp_path.unlink()
        raise e
    
    # 同步到数据库
    if _use_database():
        _db_sync_daily_balance(records)


def _db_sync_daily_balance(records: List[Dict]):
    """同步账户余额到数据库"""
    if not records:
        return
    
    # 获取 fetch_date
    fetch_date = records[0].get('fetch_date') if records else None
    if not fetch_date:
        return
    
    # 先删除当天的数据
    from data.db_connector import execute_update
    execute_update("DELETE FROM daily_balance WHERE fetch_date = %s", (fetch_date,))
    
    # 批量插入
    sql = """
        INSERT INTO daily_balance 
        (fetch_date, account_id, account_name, account_type, balance,
         related_product, product_value, diff, note)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    
    params_list = []
    for r in records:
        # 解析数值字段
        balance = r.get('balance', '0').replace(',', '') or None
        product_value = r.get('product_value', '').replace(',', '') or None
        diff = r.get('diff', '').replace(',', '') or None
        
        params_list.append((
            r.get('fetch_date'),
            r.get('account_id'),
            r.get('account_name'),
            r.get('account_type'),
            balance,
            r.get('related_product') or None,
            product_value,
            diff,
            r.get('note') or None
        ))
    
    if params_list:
        affected = execute_many(sql, params_list)
        logger.info(f"✓ 数据库同步: {affected} 条账户余额")


def create_daily_balance_snapshot(project_root: Path = None, fetch_date: str = None) -> int:
    """
    生成并保存账户余额快照
    
    :param project_root: 项目根目录，None则自动检测
    :param fetch_date: 快照日期，None则使用今天
    :return: 生成的记录数
    """
    if project_root is None:
        from data.config_loader import get_project_root
        project_root = get_project_root()
    
    if not fetch_date:
        fetch_date = date.today().strftime('%Y-%m-%d')
    
    records = generate_daily_balance(project_root, fetch_date)
    
    output_path = project_root / "data" / "snapshots" / "daily_balance.csv"
    write_daily_balance(records, output_path)
    
    return len(records)


def display_account_balances(project_root: Path = None, fetch_date: str = None):
    """
    在控制台显示账户余额
    
    :param project_root: 项目根目录
    :param fetch_date: 日期
    """
    if project_root is None:
        from data.config_loader import get_project_root
        project_root = get_project_root()
    
    records = generate_daily_balance(project_root, fetch_date)
    
    print("\n" + "=" * 80)
    print(f"账户余额快照 ({fetch_date or date.today().strftime('%Y-%m-%d')})")
    print("=" * 80)
    
    # 分类显示
    cash_accounts = []
    mapped_accounts = []
    sub_accounts = []
    summary_accounts = []
    
    for r in records:
        account_type = r.get('account_type', '')
        if account_type == 'cash':
            cash_accounts.append(r)
        elif account_type == 'fund_mapped':
            mapped_accounts.append(r)
        elif account_type == 'product_sub':
            sub_accounts.append(r)
        elif account_type in ('fund_total', 'summary'):
            summary_accounts.append(r)
    
    def print_records(title: str, items: List[Dict]):
        if not items:
            return
        print(f"\n【{title}】")
        print("-" * 60)
        for r in items:
            balance = r.get('balance', '0')
            product_value = r.get('product_value', '')
            diff = r.get('diff', '')
            
            line = f"  {r['account_name']:<20} 余额: {balance:>12}"
            if product_value:
                line += f"  市值: {product_value:>12}"
            if diff and diff != '0.00':
                line += f"  差异: {diff:>10}"
            print(line)
    
    print_records("现金账户", cash_accounts)
    print_records("基金映射账户", mapped_accounts)
    print_records("产品子账户", sub_accounts)
    print_records("汇总", summary_accounts)
    
    print("\n" + "=" * 80)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    
    from data.config_loader import get_project_root
    root = get_project_root()
    
    # 生成快照
    count = create_daily_balance_snapshot(root)
    print(f"生成 {count} 条账户余额记录")
    
    # 显示余额
    display_account_balances(root)

