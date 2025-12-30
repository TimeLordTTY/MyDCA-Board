#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
快照服务层

提供快照生成与读取功能：
- 生成 daily.csv 和 daily_balance.csv
- 采集净值并生成快照
- 读取最新快照数据
- 获取资产汇总信息

UI 和 CLI 都通过此服务操作快照数据，避免业务逻辑重复。
"""
import csv
import logging
from datetime import date
from decimal import Decimal
from pathlib import Path
from typing import List, Dict, Optional
from dataclasses import dataclass, field
from io import StringIO

from data.config_loader import get_project_root, load_products

logger = logging.getLogger(__name__)


@dataclass
class PortfolioSummary:
    """资产汇总"""
    fetch_date: str
    global_value: Decimal = Decimal('0')
    global_pnl: Decimal = Decimal('0')  # 生命周期总盈亏
    unrealized_pnl: Decimal = Decimal('0')  # 浮动盈亏
    global_return: Decimal = Decimal('0')
    fund_total: Decimal = Decimal('0')
    wenlibao_total: Decimal = Decimal('0')
    ylb_total: Decimal = Decimal('0')
    
    def to_dict(self) -> Dict:
        return {
            'fetch_date': self.fetch_date,
            'global_value': str(self.global_value),
            'global_pnl': str(self.global_pnl),
            'unrealized_pnl': str(self.unrealized_pnl),
            'global_return': f"{self.global_return:.2%}" if self.global_return else '0%',
            'fund_total': str(self.fund_total),
            'wenlibao_total': str(self.wenlibao_total),
            'ylb_total': str(self.ylb_total)
        }


@dataclass
class SyncResult:
    """同步结果"""
    nav_collected: int = 0       # 采集到的净值数量
    nav_saved: int = 0           # 保存的净值数量
    nav_skipped: int = 0         # 跳过的净值数量（已存在）
    daily_records: int = 0       # daily.csv 记录数
    balance_records: int = 0     # daily_balance.csv 记录数
    errors: List[str] = field(default_factory=list)


def build_all_snapshots(fetch_date: str = None, project_root: Path = None) -> int:
    """
    生成 daily.csv 和 daily_balance.csv
    
    Args:
        fetch_date: 快照日期（默认今天）
        project_root: 项目根目录
    
    Returns:
        生成的 daily_balance 记录数
    """
    if project_root is None:
        project_root = get_project_root()
    
    if fetch_date is None:
        fetch_date = date.today().strftime('%Y-%m-%d')
    
    # 生成 daily.csv（通过 snapshot 模块）
    from core.snapshot import create_daily_snapshot
    from data.nav_reader import get_latest_nav
    
    products = load_products()
    
    # 构建 nav_records 和 products_map
    nav_records = {}
    products_map = {}
    products_order = []
    category_map = {}
    market_map = {}
    
    for product in products:
        product_code = product['product_code']
        product_name = product['product_name']
        channel = product.get('channel', 'OTC')
        
        products_map[product_code] = product_name
        products_order.append(product_code)
        category_map[product_code] = product.get('category', 'fund')
        market_map[product_code] = product.get('market', 'cn')
        
        # 获取最新 NAV（场内产品使用实时行情价格，场外产品使用净值）
        if channel == 'EXCHANGE':
            # 场内产品：从实时行情获取最新价格
            from core.market_quote_service import get_latest_quote
            from data.product_service import get_product_by_code
            product_info = get_product_by_code(product_code)
            if product_info and product_info.get('id'):
                quote = get_latest_quote(product_info['id'])
                if quote and quote.get('price'):
                    # 使用当前日期作为 nav_date，价格作为 nav
                    nav_records[product_code] = {
                        'nav_date': fetch_date,
                        'nav': quote.get('price')
                    }
        else:
            # 场外产品：从净值表获取
            latest_nav = get_latest_nav(product_code)
            if latest_nav:
                nav_date, nav = latest_nav
                nav_records[product_code] = {
                    'nav_date': nav_date,
                    'nav': nav
                }
    
    # 调用 create_daily_snapshot 生成快照
    try:
        create_daily_snapshot(
            nav_records, 
            {}, 
            products_map,
            products_order=products_order,
            category_map=category_map,
            market_map=market_map
        )
    except Exception as e:
        logger.warning(f"生成快照失败: {e}")
    
    # 生成 daily_balance.csv
    from core.daily_balance import create_daily_balance_snapshot
    count = create_daily_balance_snapshot(project_root, fetch_date)
    
    return count


def auto_generate_couple_pocket_income(fetch_date: str = None) -> int:
    """
    自动生成情侣小荷包(000686)的利息收益记录
    
    逻辑：
    1. 查询 000686 最近一次 dividend 记录的日期
    2. 从该日期的下一天到今天，每天生成一笔收益记录
    3. 收益金额 = 持有份额 × 万份收益 / 10000
    4. 同时生成 transactions 表的 dividend 记录和 ledger 表的利息收益记录
    
    Args:
        fetch_date: 截止日期（默认今天）
    
    Returns:
        生成的记录数
    """
    from datetime import datetime, timedelta
    from decimal import Decimal
    from data.data_store import load_transactions, append_transaction, append_ledger
    from adaptor.fund_client import query_nav_history
    from core.holdings_calculator import HoldingsCalculator
    
    if fetch_date is None:
        fetch_date = date.today().strftime('%Y-%m-%d')
    
    product_code = '000686'
    couple_pocket_account = 'couple_pocket'
    
    # 1. 查询最近一次 000686 的 dividend 记录日期
    all_transactions = load_transactions()
    dividend_records = [
        t for t in all_transactions 
        if t.get('product_code') == product_code and t.get('action') == 'dividend'
    ]
    
    if dividend_records:
        # 按日期排序，取最新的
        dividend_records.sort(key=lambda x: x.get('date', ''), reverse=True)
        last_dividend_date = dividend_records[0].get('date', '')
        # 从下一天开始
        start_date = (datetime.strptime(last_dividend_date, '%Y-%m-%d') + timedelta(days=1)).strftime('%Y-%m-%d')
    else:
        # 如果没有 dividend 记录，从最早的交易记录开始
        product_transactions = [t for t in all_transactions if t.get('product_code') == product_code]
        if product_transactions:
            product_transactions.sort(key=lambda x: x.get('date', ''))
            start_date = product_transactions[0].get('date', fetch_date)
        else:
            # 没有任何交易记录，不需要生成
            return 0
    
    # 如果开始日期已经超过截止日期，不需要生成
    if start_date > fetch_date:
        return 0
    
    # 2. 获取日期范围内的历史净值数据（包含万份收益）
    try:
        nav_history = query_nav_history(product_code, start_date, fetch_date)
    except Exception as e:
        logger.warning(f"获取 {product_code} 历史净值失败: {e}")
        return 0
    
    if not nav_history:
        return 0
    
    # 3. 获取持仓份额计算器
    calc = HoldingsCalculator()
    
    # 4. 为每一天生成收益记录
    generated_count = 0
    for nav_record in nav_history:
        nav_date = nav_record.get('nav_date', '')
        if nav_date < start_date or nav_date > fetch_date:
            continue
        
        # 检查是否已经存在该日期的 dividend 记录
        existing = [
            t for t in all_transactions 
            if t.get('product_code') == product_code 
            and t.get('action') == 'dividend' 
            and t.get('date') == nav_date
        ]
        if existing:
            continue  # 跳过已存在的记录
        
        # 获取该日期前一天的持仓份额（因为收益是基于前一天的份额）
        prev_date = (datetime.strptime(nav_date, '%Y-%m-%d') - timedelta(days=1)).strftime('%Y-%m-%d')
        holdings = calc.get_holdings_as_of(prev_date)
        product_holding = holdings.get(product_code, {})
        shares = product_holding.get('shares', Decimal('0'))
        
        if shares <= 0:
            continue  # 没有持仓，不生成收益
        
        # 计算收益：货币基金的 nav 字段实际返回的是万份收益（单位：元）
        # 例如：nav=0.3250 表示每万份每天收益 0.3250 元
        daily_income_per_10k_str = nav_record.get('nav', '0')
        try:
            daily_income_per_10k = Decimal(daily_income_per_10k_str)
        except:
            daily_income_per_10k = Decimal('0')
        
        if daily_income_per_10k <= 0:
            continue
        
        # 万份收益计算：收益 = 份额 × 万份收益 / 10000
        income_amount = shares * daily_income_per_10k / Decimal('10000')
        income_amount = income_amount.quantize(Decimal('0.0001'))  # 保留4位小数
        
        if income_amount <= 0:
            continue
        
        # 事件时间固定为 03:00:00
        event_time = f"{nav_date} 03:00:00"
        
        # 5. 生成 dividend 交易记录
        tx_record = {
            'date': nav_date,
            'product_code': product_code,
            'action': 'dividend',
            'amount': '',
            'shares': str(income_amount),  # 货币基金分红以份额形式增加
            'fee': '0',
            'nav': '1',
            'nav_date': nav_date,
            'order_id': '',
            'note': f'货币基金每日收益',
            'created_at': event_time
        }
        append_transaction(tx_record)
        
        # 6. 生成 ledger 利息收益记录
        ledger_record = {
            'event_time': event_time,
            'entry_type': 'income',
            'amount': str(income_amount),
            'category_l1': '理财盈利',
            'category_l2': '利息收益',
            'account_from': '',
            'account_to': couple_pocket_account,
            'discount': '0',
            'reimbursable': '0',
            'note': f'小荷包利息收益 ({nav_date})'
        }
        append_ledger(ledger_record)
        
        generated_count += 1
        
        # 更新 all_transactions 以便后续检查
        all_transactions.append(tx_record)
    
    if generated_count > 0:
        logger.info(f"自动生成 {product_code} 利息收益记录 {generated_count} 条")
    
    return generated_count


def collect_nav_and_build_snapshots(fetch_date: str = None, silent: bool = False) -> SyncResult:
    """
    采集净值并生成快照（一键日更）
    
    Args:
        fetch_date: 快照日期（默认今天）
        silent: 是否静默模式
    
    Returns:
        SyncResult 包含采集和生成结果
    """
    result = SyncResult()
    
    if fetch_date is None:
        fetch_date = date.today().strftime('%Y-%m-%d')
    
    project_root = get_project_root()
    
    # 静默模式下抑制输出
    if silent:
        import sys
        from io import StringIO
        old_stdout = sys.stdout
        old_stderr = sys.stderr
        sys.stdout = StringIO()
        sys.stderr = StringIO()
        old_level = logger.level
        logger.setLevel(logging.CRITICAL)
    
    try:
        # 采集净值
        from core.nav_collector import collect_and_store
        collect_and_store()
        
        # 自动生成小荷包利息收益（在生成快照之前）
        try:
            income_count = auto_generate_couple_pocket_income(fetch_date)
            if income_count > 0:
                logger.info(f"自动生成小荷包利息收益 {income_count} 条")
        except Exception as e:
            logger.warning(f"自动生成小荷包利息收益失败: {e}")
        
        # 生成快照
        result.balance_records = build_all_snapshots(fetch_date, project_root)
        
    except Exception as e:
        result.errors.append(str(e))
    finally:
        if silent:
            sys.stdout = old_stdout
            sys.stderr = old_stderr
            logger.setLevel(old_level)
    
    return result


def read_latest_daily(project_root: Path = None) -> List[Dict]:
    """
    读取最新 daily 数据（从数据库）
    
    Args:
        project_root: 项目根目录（已忽略，直接从数据库读取）
    
    Returns:
        最新日期的 daily 记录列表
    """
    from data.db_connector import execute_query, execute_one
    
    # 获取最新日期
    date_sql = "SELECT MAX(fetch_date) as latest_date FROM daily_snapshot"
    date_result = execute_one(date_sql)
    
    if not date_result or not date_result.get('latest_date'):
        return []
    
    latest_date = date_result['latest_date']
    if hasattr(latest_date, 'strftime'):
        latest_date = latest_date.strftime('%Y-%m-%d')
    
    # 获取该日期的所有记录
    sql = """
        SELECT DATE_FORMAT(fetch_date, '%%Y-%%m-%%d') as fetch_date,
               product_code, product_name, category,
               DATE_FORMAT(nav_date, '%%Y-%%m-%%d') as nav_date,
               nav, shares, `value`, pnl_day, cost,
               unrealized_pnl, return_rate, cash_in_transit,
               total_value, principal_total, total_redemption,
               total_pnl, real_return, fetched_at
        FROM daily_snapshot
        WHERE fetch_date = %s
        ORDER BY product_code
    """
    return execute_query(sql, (latest_date,))


def read_latest_daily_balance(project_root: Path = None) -> List[Dict]:
    """
    读取最新 daily_balance 数据（从数据库），并动态添加收益字段
    
    Args:
        project_root: 项目根目录（已忽略，直接从数据库读取）
    
    Returns:
        最新日期的 daily_balance 记录列表（包含收益字段）
    """
    from data.db_connector import execute_query, execute_one
    
    # 获取最新日期
    date_sql = "SELECT MAX(fetch_date) as latest_date FROM daily_balance"
    date_result = execute_one(date_sql)
    
    if not date_result or not date_result.get('latest_date'):
        return []
    
    latest_date = date_result['latest_date']
    if hasattr(latest_date, 'strftime'):
        latest_date = latest_date.strftime('%Y-%m-%d')
    
    # 获取该日期的所有记录
    sql = """
        SELECT DATE_FORMAT(fetch_date, '%%Y-%%m-%%d') as fetch_date,
               account_id, account_name, account_type,
               balance, related_product, product_value, diff, note
        FROM daily_balance
        WHERE fetch_date = %s
        ORDER BY account_id
    """
    records = execute_query(sql, (latest_date,))
    
    # 动态添加收益字段：从 daily 数据计算
    daily_records = read_latest_daily(project_root)
    daily_map = {r.get('product_code'): r for r in daily_records}
    
    # 获取所有 fund_mapped 账户关联的产品代码（这些不计入基金总和）
    fund_mapped_products = set()
    for r in records:
        if r.get('account_type') == 'fund_mapped':
            linked = r.get('related_product')
            if linked:
                fund_mapped_products.add(linked)
    
    for record in records:
        account_id = record.get('account_id', '')
        account_type = record.get('account_type', '')
        related_product = record.get('related_product', '')
        
        yesterday_pnl = Decimal('0')
        unrealized_pnl = Decimal('0')
        total_pnl = Decimal('0')
        
        # 基金账户：汇总所有基金产品的收益
        if account_type == 'fund_total':
            for code, info in daily_map.items():
                if info.get('category') == 'fund' and code not in fund_mapped_products:
                    yesterday_pnl += Decimal(str(info.get('pnl_day', '0') or '0'))
                    unrealized_pnl += Decimal(str(info.get('unrealized_pnl', '0') or '0'))
                    total_pnl += Decimal(str(info.get('total_pnl', '0') or '0'))
        # 产品子账户（稳利宝）：从关联产品获取收益
        elif account_type == 'product_sub' and related_product:
            if related_product in daily_map:
                product_info = daily_map[related_product]
                yesterday_pnl = Decimal(str(product_info.get('pnl_day', '0') or '0'))
                unrealized_pnl = Decimal(str(product_info.get('unrealized_pnl', '0') or '0'))
                total_pnl = Decimal(str(product_info.get('total_pnl', '0') or '0'))
        # 余利宝生活费：如果有关联产品，从产品获取收益
        elif account_id == 'ylb_life' and related_product:
            if related_product in daily_map:
                product_info = daily_map[related_product]
                yesterday_pnl = Decimal(str(product_info.get('pnl_day', '0') or '0'))
                unrealized_pnl = Decimal(str(product_info.get('unrealized_pnl', '0') or '0'))
                total_pnl = Decimal(str(product_info.get('total_pnl', '0') or '0'))
        
        record['yesterday_pnl'] = f"{yesterday_pnl:.2f}" if yesterday_pnl != 0 else ''
        record['unrealized_pnl'] = f"{unrealized_pnl:.2f}" if unrealized_pnl != 0 else ''
        record['total_pnl'] = f"{total_pnl:.2f}" if total_pnl != 0 else ''
    
    return records


def get_portfolio_summary(project_root: Path = None) -> PortfolioSummary:
    """
    获取资产汇总信息
    
    总资产计算规则：
    - 余利宝合计：从 daily_balance.csv 读取 ylb_life + ylb_finance 的 balance
    - 稳利宝产品市值：从 daily.csv 读取 FBAE41126E 的 total_value（产品市值）
    - 基金市值合计：从 daily.csv 读取所有 fund 产品的 value（市值，不含货币基金）
    - 小荷包金额：从 daily_balance.csv 读取 couple_pocket 的 product_value（包含收益）
    
    Args:
        project_root: 项目根目录
    
    Returns:
        PortfolioSummary 资产汇总
    """
    if project_root is None:
        project_root = get_project_root()
    
    fetch_date = date.today().strftime('%Y-%m-%d')
    summary = PortfolioSummary(fetch_date=fetch_date)
    
    # 从 daily.csv 读取产品市值
    daily_records = read_latest_daily(project_root)
    
    for record in daily_records:
        product_code = record.get('product_code', '')
        category = record.get('category', '')
        
        try:
            value = Decimal(record.get('value', '0') or '0')
            total_value = Decimal(record.get('total_value', '0') or '0')
        except:
            continue
        
        # 稳利宝：使用 total_value（产品市值，包含在途资金）
        if product_code == 'FBAE41126E':
            summary.wenlibao_total = total_value
        # 货币基金不计入基金总额（已映射到小荷包）
        elif product_code == '000686':
            pass
        # 其他基金：使用 value（纯市值）
        elif category == 'fund':
            summary.fund_total += value
    
    # 从 daily_balance.csv 读取账户余额
    balance_records = read_latest_daily_balance(project_root)
    
    couple_pocket_value = Decimal('0')
    
    for record in balance_records:
        account_id = record.get('account_id', '')
        account_type = record.get('account_type', '')
        
        try:
            balance = Decimal(record.get('balance', '0') or '0')
            product_value = Decimal(record.get('product_value', '0') or '0')
        except:
            continue
        
        # 余利宝：使用 balance（账户余额）
        if account_id in ['ylb_life', 'ylb_finance']:
            summary.ylb_total += balance
        # 小荷包：使用 product_value（包含收益的市值）
        elif account_id == 'couple_pocket':
            couple_pocket_value = product_value
        
        # 更新最新日期
        row_date = record.get('fetch_date', '')
        if row_date:
            summary.fetch_date = row_date
    
    # 计算全局汇总：余利宝合计 + 稳利宝产品市值 + 基金市值 + 小荷包金额
    summary.global_value = summary.ylb_total + summary.wenlibao_total + summary.fund_total + couple_pocket_value
    
    # 计算两个盈亏指标
    total_principal = Decimal('0')
    total_cost = Decimal('0')
    
    for record in daily_records:
        product_code = record.get('product_code', '')
        category = record.get('category', '')
        
        try:
            # 生命周期总盈亏（包含已赎回收益）
            total_pnl = Decimal(record.get('total_pnl', '0') or '0')
            summary.global_pnl += total_pnl
            
            # 浮动盈亏（仅当前持仓）
            unrealized = Decimal(record.get('unrealized_pnl', '0') or '0')
            principal = Decimal(record.get('principal_total', '0') or '0')
            cost = Decimal(record.get('cost', '0') or '0')
            
            # 只计算基金和稳利宝的浮动盈亏（排除货币基金）
            if product_code == 'FBAE41126E':
                # 稳利宝
                summary.unrealized_pnl += unrealized
                total_principal += principal
                total_cost += cost
            elif product_code != '000686' and category == 'fund':
                # 其他基金（排除货币基金）
                summary.unrealized_pnl += unrealized
                total_principal += principal
                total_cost += cost
        except:
            pass
    
    # 计算收益率（使用生命周期总盈亏）
    if total_principal > 0:
        summary.global_return = summary.global_pnl / total_principal
    
    return summary


def read_daily_by_category(category: str = None, project_root: Path = None) -> List[Dict]:
    """
    按分类筛选 daily.csv 数据
    
    Args:
        category: 分类筛选（fund/bank）
        project_root: 项目根目录
    
    Returns:
        筛选后的记录列表
    """
    records = read_latest_daily(project_root)
    
    if category:
        records = [r for r in records if r.get('category') == category]
    
    return records


def read_balance_by_type(account_type: str = None, project_root: Path = None) -> List[Dict]:
    """
    按账户类型筛选 daily_balance.csv 数据
    
    Args:
        account_type: 账户类型筛选（cash/fund_mapped/product_sub/summary）
        project_root: 项目根目录
    
    Returns:
        筛选后的记录列表
    """
    records = read_latest_daily_balance(project_root)
    
    if account_type:
        records = [r for r in records if r.get('account_type') == account_type]
    
    return records


def read_balance_by_group(group_keyword: str = None, project_root: Path = None) -> List[Dict]:
    """
    按组关键词筛选 daily_balance.csv 数据
    
    Args:
        group_keyword: 组关键词（ylb/wenlibao/fund）
        project_root: 项目根目录
    
    Returns:
        筛选后的记录列表
    """
    records = read_latest_daily_balance(project_root)
    
    if group_keyword:
        records = [r for r in records 
                   if group_keyword in r.get('account_id', '').lower()]
    
    return records


def get_fund_account_balance(project_root: Path = None) -> Decimal:
    """
    计算基金账户的总余额（从交易记录累计）
    
    基金账户余额 = 买入增加 - 赎回减少
    - buy: 增加（旧模式，shares × nav，表示买入的市值）
    - buy_confirm: 增加（shares × nav）
    - sell / sell_confirm: 减少（amount，赎回到账金额）
    
    只计算 category=fund 的产品
    
    Args:
        project_root: 项目根目录（已忽略）
    
    Returns:
        基金账户总余额
    """
    from data.db_connector import execute_query
    from data.config_loader import load_products
    
    # 获取所有基金产品代码
    products = load_products()
    fund_codes = [p['product_code'] for p in products if p.get('category') == 'fund']
    
    if not fund_codes:
        return Decimal('0')
    
    # 从交易记录计算
    placeholders = ','.join(['%s'] * len(fund_codes))
    sql = f"""
        SELECT action, product_code, amount, shares, nav
        FROM transactions
        WHERE product_code IN ({placeholders})
    """
    
    transactions = execute_query(sql, tuple(fund_codes))
    
    total = Decimal('0')
    for tx in transactions:
        action = (tx.get('action') or '').lower()
        shares = tx.get('shares')
        nav = tx.get('nav')
        
        if action in ['buy', 'buy_confirm']:
            # 买入：增加（shares × nav，表示买入的市值）
            if shares and nav:
                try:
                    calc_amount = Decimal(str(shares)) * Decimal(str(nav))
                    total += calc_amount
                except:
                    pass
        elif action in ['sell', 'sell_confirm']:
            # 赎回：减少（amount，赎回到账金额）
            amount = tx.get('amount')
            if amount:
                try:
                    total -= Decimal(str(amount))
                except:
                    pass
    
    return total