# -*- coding: utf-8 -*-
"""
账户余额快照模块

生成 daily_balance.csv，展示各账户余额和关联产品市值。

账户类型说明：
- cash：现金账户（余利宝生活费、余利宝理财金等）
- fund_mapped：映射到基金的账户（小荷包 -> 000686）
- product_sub：产品子账户（稳利宝子账户）
- fund_total：基金账户汇总

字段说明：
- fetch_date：快照日期
- account_id：账户ID
- account_name：账户名称  
- account_type：账户类型
- balance：账户余额（从ledger计算）
- related_product：关联产品代码
- product_value：关联产品市值
- diff：差异（product_value - balance）
- note：备注

小荷包收益计算规则：
- 关联货币基金（如 000686 建信嘉薪宝）
- 收益公式：日收益 = 持有份额 / 10000 * 万份收益
- 收益发放规则：余额等待下个交易日由基金公司确认份额，份额确认后的次日发放收益
- 交易日为不含节假日的周一到周五，以15:00为界
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
    加载货币基金的万份收益
    
    :param project_root: 项目根目录
    :param product_code: 产品代码（如 000686）
    :param nav_date: 净值日期，None则取最新
    :return: 万份收益，或 None（如果找不到）
    """
    nav_dir = project_root / "data" / "nav"
    if not nav_dir.exists():
        return None
    
    # 查找对应产品的 NAV 文件
    pattern = str(nav_dir / f"{product_code}_*.csv")
    files = glob.glob(pattern)
    if not files:
        return None
    
    nav_file = Path(files[0])
    if not nav_file.exists():
        return None
    
    latest_yield = None
    latest_date = None
    
    with open(nav_file, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            # 跳过中文表头行
            row_date = row.get('nav_date', '')
            if row_date.startswith('净值') or row_date.startswith('产品'):
                continue
            
            # 如果指定日期，只读取该日期
            if nav_date and row_date != nav_date:
                continue
            
            # 取最新日期的万份收益（nav 字段存储万份收益）
            if not latest_date or row_date >= latest_date:
                latest_date = row_date
                nav_value = row.get('nav', '0')
                latest_yield = safe_decimal(nav_value)
    
    return latest_yield


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
    """加载账本记录"""
    ledger_path = project_root / "data" / "ledger.csv"
    if not ledger_path.exists():
        return []
    
    records = []
    with open(ledger_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            records.append(row)
    return records


def load_daily_csv(project_root: Path, fetch_date: str = None) -> Dict[str, Dict]:
    """
    加载daily.csv，返回产品市值信息
    
    :param project_root: 项目根目录
    :param fetch_date: 指定日期，None则取最新
    :return: {product_code: {value, shares, nav, ...}}
    """
    daily_path = project_root / "data" / "snapshots" / "daily.csv"
    if not daily_path.exists():
        return {}
    
    products = {}
    target_date = fetch_date
    
    with open(daily_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            # 跳过中文表头行
            if row.get('fetch_date', '').startswith('采集'):
                continue
            
            row_date = row.get('fetch_date', '')
            product_code = row.get('product_code', '')
            
            if not product_code:
                continue
            
            # 如果指定日期，只读取该日期
            if fetch_date and row_date != fetch_date:
                continue
            
            # 记录最新日期
            if not target_date or row_date >= target_date:
                target_date = row_date
            
            products[product_code] = {
                'fetch_date': row_date,
                'product_name': row.get('product_name', ''),
                'value': safe_decimal(row.get('value', '0')),
                'total_value': safe_decimal(row.get('total_value', '0')),
                'shares': safe_decimal(row.get('shares', '0')),
                'nav': safe_decimal(row.get('nav', '0')),
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


def generate_daily_balance(project_root: Path, fetch_date: str = None) -> List[Dict]:
    """
    生成账户余额快照
    
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
    wenlibao_total = Decimal('0')
    wenlibao_sub_total = Decimal('0')  # 子账户余额合计
    ylb_total = Decimal('0')  # 余利宝合计
    
    # 获取所有 fund_mapped 账户关联的产品代码（这些不计入基金总和）
    fund_mapped_products = set()
    for acc in accounts:
        if get_account_type(acc) == 'fund_mapped':
            linked = acc.get('linked_product')
            if linked:
                fund_mapped_products.add(linked)
    
    for account in accounts:
        account_id = account.get('id', '')
        account_name = account.get('name', '')
        linked_product = account.get('linked_product')
        initial_balance = safe_decimal(account.get('initial_balance', '0'))
        account_type = get_account_type(account)
        
        # 计算账户余额（从 ledger 计算，不再使用 accounts.json 中的 initial_balance）
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
                    fund_total += info.get('total_value', Decimal('0'))
            balance = fund_total
            product_value = fund_total
        # 产品子账户（稳利宝）：不显示父产品市值，只显示余额
        # 父产品市值在汇总行显示
        elif account_type == 'product_sub':
            # 只记录余额，不显示产品市值
            wenlibao_sub_total += balance
        # 基金映射账户（小荷包）：余额 = 关联产品市值 + 当日收益
        elif account_type == 'fund_mapped' and linked_product:
            if linked_product in daily_products:
                product_info = daily_products[linked_product]
                product_value = product_info.get('total_value', Decimal('0'))
                shares = product_info.get('shares', Decimal('0'))
                
                # 计算货币基金当日收益
                # 收益公式：日收益 = 持有份额 / 10000 * 万份收益
                daily_income = Decimal('0')
                yield_per_10k = load_money_fund_yield(project_root, linked_product)
                if yield_per_10k and shares > 0:
                    daily_income = calc_money_fund_daily_income(shares, yield_per_10k)
                
                # 小荷包余额 = 市值 + 当日收益（收益自动入账）
                ledger_balance = balance  # 保存 ledger 计算的值用于对比
                balance = product_value + daily_income  # 余额 = 市值 + 当日收益
                product_value = balance  # 产品市值也更新为含收益的值
                
                if daily_income > 0:
                    note = f"万份收益={yield_per_10k}，日收益=+{daily_income}"
                
                diff = balance - ledger_balance  # 差异 = 当前余额 - ledger记录值
        # 余利宝账户：统计到余利宝合计
        elif account_type == 'cash':
            # 检查是否为余利宝账户
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
    
    # 获取稳利宝产品总市值
    wenlibao_product = daily_products.get('FBAE41126E', {})
    if wenlibao_product:
        wenlibao_total = wenlibao_product.get('total_value', Decimal('0'))
    
    # 添加稳利宝汇总行
    wenlibao_profit = wenlibao_total - wenlibao_sub_total
    records.append({
        'fetch_date': fetch_date,
        'account_id': 'wenlibao_total',
        'account_name': '稳利宝(合计)',
        'account_type': 'summary',
        'balance': f"{wenlibao_sub_total:.2f}",
        'related_product': 'FBAE41126E',
        'product_value': f"{wenlibao_total:.2f}",
        'diff': f"{wenlibao_profit:.2f}",
        'note': f"收益={wenlibao_profit:.2f}（归入项目资金）"
    })
    
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
        records.append({
            'fetch_date': fetch_date,
            'account_id': 'fund_total',
            'account_name': '基金(合计)',
            'account_type': 'summary',
            'balance': f"{fund_total:.2f}",
            'related_product': '',
            'product_value': f"{fund_total:.2f}",
            'diff': '',
            'note': '所有基金市值合计（不含货币基金）'
        })
    
    return records


def write_daily_balance(records: List[Dict], output_path: Path):
    """
    写入账户余额快照文件
    
    使用原子写入：先写临时文件，再替换
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
        
        # 原子替换
        if output_path.exists():
            output_path.unlink()
        tmp_path.rename(output_path)
        
        logger.info(f"✓ 写入账户余额快照: {output_path} ({len(records)} 条)")
        
    except Exception as e:
        if tmp_path.exists():
            tmp_path.unlink()
        raise e


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

