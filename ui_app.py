#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
财富中枢 - 本地 UI (Streamlit)

使用方法：
    streamlit run ui_app.py

页面：
1. Dashboard - 资产总览
2. 生活记账 - 收入/支出/转账/退款
3. 理财录入 - 买入扣款/赎回发起/补录历史
4. 订单结算 - 查看待结算订单/执行结算
"""
import sys
from pathlib import Path
from datetime import datetime, date
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP, ROUND_DOWN
from typing import Any, Dict

import streamlit as st
import pandas as pd
import logging
import json

# 添加 src 目录到路径
sys.path.insert(0, str(Path(__file__).parent / "src"))

logger = logging.getLogger(__name__)

from data.config_loader import get_project_root
from core.ledger_service import (
    add_expense, add_income, add_transfer, add_refund,
    list_recent_ledger, list_expenses, validate_ledger,
    get_account_options, get_category_options, update_ledger_entry
)
from core.invest_service import (
    add_buy_debit, add_redeem_request, add_history_trade,
    list_pending_orders, list_all_orders, settle_orders,
    settle_single_order, preview_settle, get_order_by_id,
    validate_transactions_orders, get_product_options,
    calc_buy_fee, calc_trade_dates, list_recent_transactions,
    update_transaction_entry
)
from core.snapshot_service import (
    build_all_snapshots, collect_nav_and_build_snapshots,
    read_latest_daily,
    get_portfolio_summary, read_balance_by_group
)
# 已废弃：不再使用 daily_balance 快照
# from core.daily_balance import create_daily_balance_snapshot
from data.config_loader import get_sell_fee_rate, get_product
from core.strategy_lab_service import (
    list_backtest_summaries, get_backtest_summary,
    get_backtest_daily_records, get_backtest_trades,
    run_backtest, run_param_comparison, delete_backtest_summary,
    get_product_data_range
)
# 先导入策略模块以确保策略被注册
import strategy_lab.strategy  # noqa: F401
from strategy_lab.framework.registry import list_strategies, get_strategy_info
from data.product_service import get_products
from core.strategy_manager import (
    list_strategy_files, save_strategy, load_strategy_code, delete_strategy
)

# 页面配置
st.set_page_config(
    page_title="财富中枢",
    page_icon="💰",
    layout="wide",
    initial_sidebar_state="expanded"
)


def format_product_display_name(product: Dict) -> str:
    """
    格式化产品显示名称，包含场内/场外标注
    
    Args:
        product: 产品字典，应包含 code, name/product_name, channel 字段
    
    Returns:
        格式化后的产品显示名称，例如: "515450 - 红利低波ETF (场内)"
    """
    code = product.get('code', '') or product.get('product_code', '')
    name = product.get('name') or product.get('product_name', '')
    channel = product.get('channel', '')
    
    # 判断场内/场外
    channel_label = '场内' if channel == 'EXCHANGE' else '场外'
    
    return f"{code} - {name} ({channel_label})"


def get_product_linked_accounts(product_code: str) -> list:
    """
    获取产品关联的账户列表
    
    Args:
        product_code: 产品代码
    
    Returns:
        账户列表，如果没有关联账户则返回空列表
    """
    try:
        from data.product_service import get_product_by_code
        from data.db_connector import execute_query
        from data.config_loader import get_accounts_by_group
        
        # 获取产品信息
        product = get_product_by_code(product_code)
        if not product:
            return []
        
        product_id = product.get('id')
        if not product_id:
            return []
        
        # 查找关联的账户组
        sql = """
            SELECT ag.group_code
            FROM account_groups ag
            WHERE ag.linked_product_id = %s
            LIMIT 1
        """
        group_rows = execute_query(sql, (product_id,))
        
        if not group_rows:
            return []
        
        group_code = group_rows[0]['group_code']
        
        # 获取该组下的所有账户
        accounts = get_accounts_by_group(group_code)
        return accounts
    except Exception as e:
        logger.warning(f"获取产品关联账户失败: {e}")
        return []

# 自定义样式
st.markdown("""
<style>
    .main-header {
        font-size: 2rem;
        font-weight: bold;
        color: #1f77b4;
        margin-bottom: 1rem;
    }
    .metric-card {
        background-color: #f0f2f6;
        padding: 1rem;
        border-radius: 0.5rem;
        margin: 0.5rem 0;
    }
    .success-msg {
        color: #28a745;
        font-weight: bold;
    }
    .error-msg {
        color: #dc3545;
        font-weight: bold;
    }
    /* 红绿颜色样式 */
    .expense-row { color: #dc3545 !important; }
    .income-row { color: #28a745 !important; }
</style>
""", unsafe_allow_html=True)


# ============ 分页组件 ============
def paginate_dataframe(df: pd.DataFrame, key: str, page_size: int = 50) -> pd.DataFrame:
    """
    通用分页组件
    
    Args:
        df: 要分页的 DataFrame
        key: 唯一标识符，用于 session_state 存储页码
        page_size: 每页显示条数，默认50条
    
    Returns:
        当前页的 DataFrame
    """
    if df.empty:
        return df
    
    total_rows = len(df)
    total_pages = (total_rows + page_size - 1) // page_size  # 向上取整
    
    # 初始化页码
    page_key = f"page_{key}"
    if page_key not in st.session_state:
        st.session_state[page_key] = 1
    
    # 确保页码在有效范围内
    current_page = st.session_state[page_key]
    if current_page > total_pages:
        current_page = total_pages
        st.session_state[page_key] = current_page
    if current_page < 1:
        current_page = 1
        st.session_state[page_key] = current_page
    
    # 计算当前页数据范围
    start_idx = (current_page - 1) * page_size
    end_idx = min(start_idx + page_size, total_rows)
    
    # 分页控件
    col1, col2, col3, col4, col5 = st.columns([1, 1, 2, 1, 1])
    
    with col1:
        if st.button("⏮ 首页", key=f"first_{key}", disabled=(current_page == 1)):
            st.session_state[page_key] = 1
            st.rerun()
    
    with col2:
        if st.button("◀ 上一页", key=f"prev_{key}", disabled=(current_page == 1)):
            st.session_state[page_key] = current_page - 1
            st.rerun()
    
    with col3:
        st.markdown(
            f"<div style='text-align: center; padding: 0.5rem;'>"
            f"第 {current_page} / {total_pages} 页 (共 {total_rows} 条)"
            f"</div>",
            unsafe_allow_html=True
        )
    
    with col4:
        if st.button("下一页 ▶", key=f"next_{key}", disabled=(current_page == total_pages)):
            st.session_state[page_key] = current_page + 1
            st.rerun()
    
    with col5:
        if st.button("末页 ⏭", key=f"last_{key}", disabled=(current_page == total_pages)):
            st.session_state[page_key] = total_pages
            st.rerun()
    
    # 返回当前页数据
    return df.iloc[start_idx:end_idx].reset_index(drop=True)


# 账户组过滤选项
ACCOUNT_GROUP_FILTERS = {
    '全部': None,
    '余利宝': ['ylb_life', 'ylb_finance'],
    '稳利宝': ['wenlibao_project', 'wenlibao_safe', 'wenlibao_rent', 'wenlibao_finance', 'wenlibao_active'],
    '小荷包': ['couple_pocket']
}

# 产品到扣款账户的映射（定投扣款来源）
PRODUCT_DEBIT_ACCOUNT_MAP = {
    'FBAE41126E': 'wenlibao_finance',  # 稳利宝 -> 从稳利宝理财金扣款
    '000686': 'couple_pocket',  # 小荷包货币基金
}
DEFAULT_FUND_DEBIT_ACCOUNT = 'ylb_finance'  # 其他基金默认从余利宝理财金扣款

# 产品到到账账户的映射（份额确认后增加到哪个账户）
PRODUCT_CONFIRM_ACCOUNT_MAP = {
    'FBAE41126E': 'wenlibao_finance',  # 稳利宝 -> 稳利宝相关账户
    '000686': 'couple_pocket',  # 小荷包货币基金
}
DEFAULT_FUND_CONFIRM_ACCOUNT = 'fund_account'  # 基金确认后增加到基金账户

# 账户ID到中文名称的基础映射（兜底，主要用于老数据）
ACCOUNT_NAME_MAP = {
    'ylb_life': '余利宝生活费',
    'ylb_finance': '余利宝理财金',
    'wenlibao_rent': '稳利宝-房租',
    'wenlibao_safe': '稳利宝-安全金',
    'wenlibao_project': '稳利宝-项目',
    'wenlibao_finance': '稳利宝-理财金',
    'wenlibao_active': '稳利宝-主动投入',
    'couple_pocket': '小荷包',
    'bank_card': '银行卡',
    'wechat': '微信零钱',
    'fund_account': '基金账户',
    'other': '其他'
}

# 账户余额缓存：{account_code: Decimal(balance)}
ACCOUNT_BALANCE_MAP: Dict[str, Decimal] = {}

# 启动时从账户表补充映射：保证「生活记账」等页面统一展示账户表里的中文名称，
# 同时把当前余额缓存下来，供各个页面快速展示。
try:
    from data.account_service import get_accounts  # 放在函数外，避免循环导入

    _accounts_for_name = get_accounts(is_active=True)
    for _acc in _accounts_for_name:
        _code = _acc.get('account_code') or _acc.get('account_id')
        _name = _acc.get('account_name')
        _bal_raw = _acc.get('balance', 0) or 0
        if _code:
            if _name:
                # 不覆盖手工写死的别名，只在缺省时补充
                ACCOUNT_NAME_MAP.setdefault(_code, _name)
            try:
                ACCOUNT_BALANCE_MAP[_code] = Decimal(str(_bal_raw))
            except Exception:
                ACCOUNT_BALANCE_MAP[_code] = Decimal('0')
except Exception as _e:
    # 这里失败不影响主流程，只是名字可能退回到英文ID / 余额显示为0
    logger.warning("加载账户中文名称/余额映射失败: %s", _e)


def estimate_account_product_shares(product_code: str, linked_accounts: list[Dict[str, Any]]) -> Dict[str, Decimal]:
    """
    估算每个子账户在某个产品中的份额（用于赎回界面展示提示）
    
    思路：
    - 从 daily_snapshot 读取该产品的总份额 shares
    - 从 ACCOUNT_BALANCE_MAP 读取所有关联账户的余额（本金口径）
    - 按余额占比把总份额在各账户之间按比例拆分：
        shares_account = total_shares * (account_balance / sum_balances)
    - 这样可以保证「子账户份额之和 ≈ 总份额」，用于 UI 提示
    """
    from core.snapshot_service import read_latest_daily

    try:
        daily_records = read_latest_daily()
    except Exception:
        return {}

    if not daily_records:
        return {}

    daily_map = {r.get("product_code"): r for r in daily_records}
    info = daily_map.get(product_code)
    if not info:
        return {}

    try:
        total_shares = Decimal(str(info.get("shares") or "0"))
    except Exception:
        total_shares = Decimal("0")

    if total_shares <= 0:
        return {}

    # 计算所有关联账户的余额总和
    balances: Dict[str, Decimal] = {}
    total_balance = Decimal("0")
    for acc in linked_accounts:
        acc_id = acc.get("id")
        if not acc_id:
            continue
        bal = get_account_balance(acc_id)
        if bal <= 0:
            continue
        balances[acc_id] = bal
        total_balance += bal

    if total_balance <= 0 or not balances:
        return {}

    # 按余额占比分配份额
    result: Dict[str, Decimal] = {}
    allocated = Decimal("0")
    acc_ids = list(balances.keys())
    for i, acc_id in enumerate(acc_ids):
        bal = balances[acc_id]
        if i < len(acc_ids) - 1:
            shares = (total_shares * bal / total_balance).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)
            allocated += shares
        else:
            shares = (total_shares - allocated).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)
        if shares > 0:
            result[acc_id] = shares

    return result

# 理财操作类型映射（统一显示）
ACTION_DISPLAY_MAP = {
    'buy_debit': '买入待确认',
    'buy_confirm': '买入确认', 
    'buy': '买入',
    'sell': '卖出',
    'sell_confirm': '卖出确认',
    'redeem_request': '卖出待确认',
    'dividend': '分红',
    'transfer_out': '转托管转出',
    'transfer_in': '转托管转入'
}

# 待确认类型（白色显示，不带+/-号）
PENDING_ACTIONS = ['buy_debit', 'redeem_request']

# fund_mapped 账户到产品代码的映射
FUND_MAPPED_ACCOUNTS = {
    'couple_pocket': '000686',  # 小荷包 -> 建信嘉薪宝货币基金
}


def sync_fund_mapped_transaction(account_id: str, amount: Decimal, is_expense: bool, 
                                  event_time: str, note: str = ''):
    """
    同步 fund_mapped 账户的支出交易到对应产品的 transactions 表
    
    对于货币基金，金额 = 份额（净值固定为1）
    注意：只处理支出（卖出），收入在单独的逻辑中处理
    
    Args:
        account_id: 账户ID
        amount: 金额
        is_expense: 是否为支出（True=卖出/减少）
        event_time: 事件时间
        note: 备注
    """
    if not is_expense:
        # 收入的同步在调用处单独处理
        return
    
    product_code = FUND_MAPPED_ACCOUNTS.get(account_id)
    if not product_code:
        return  # 不是 fund_mapped 账户，不需要同步
    
    from data.data_store import append_transaction
    
    # 货币基金：金额 = 份额（净值固定为1）
    shares = amount
    
    tx_record = {
        'date': event_time[:10],  # 只取日期部分
        'product_code': product_code,
        'action': 'sell',  # 支出 = 卖出
        'amount': str(amount),
        'shares': str(shares),
        'fee': '0',
        'nav': '1',  # 货币基金净值固定为1
        'nav_date': event_time[:10],
        'order_id': '',
        'note': f"同步自记账: {note}" if note else "同步自记账",
        'created_at': event_time
    }
    append_transaction(tx_record)


def get_account_name(account_id: str) -> str:
    """获取账户中文名称"""
    return ACCOUNT_NAME_MAP.get(account_id, account_id or '')


def get_account_balance(account_id: str) -> Decimal:
    """获取账户当前余额（从 accounts.balance 缓存中读取）"""
    return ACCOUNT_BALANCE_MAP.get(account_id, Decimal('0'))


def get_tx_account(product_code: str, action: str = 'buy_debit') -> str:
    """
    获取理财交易对应的账户
    
    Args:
        product_code: 产品代码
        action: 交易类型
            - buy_debit: 扣款账户（钱从哪里扣，如余利宝理财金）
            - buy: 旧模式买入，视为已确认，返回到账账户（基金账户）
            - buy_confirm: 到账账户（份额确认后增加到哪里，基金账户）
            - sell/sell_confirm: 卖出到账账户（基金账户减少，资金回到扣款账户）
    
    Returns:
        账户ID
    """
    # 买入确认 或 旧模式buy（已确认）-> 资产增加到对应账户
    if action in ['buy_confirm', 'buy']:
        return PRODUCT_CONFIRM_ACCOUNT_MAP.get(product_code, DEFAULT_FUND_CONFIRM_ACCOUNT)
    
    # 卖出 -> 从对应账户减少
    if action in ['sell', 'sell_confirm']:
        return PRODUCT_CONFIRM_ACCOUNT_MAP.get(product_code, DEFAULT_FUND_CONFIRM_ACCOUNT)
    
    # 扣款类型 (buy_debit) -> 返回扣款账户
    return PRODUCT_DEBIT_ACCOUNT_MAP.get(product_code, DEFAULT_FUND_DEBIT_ACCOUNT)


def get_account_group_name(account_id: str) -> str:
    """获取账户所属的组名"""
    if account_id in ['ylb_life', 'ylb_finance']:
        return '余利宝'
    elif account_id.startswith('wenlibao'):
        return '稳利宝'
    elif account_id == 'couple_pocket':
        return '小荷包'
    return ''


def filter_records_by_account_group(records, group_name):
    """根据账户组过滤记录"""
    if group_name == '全部' or group_name not in ACCOUNT_GROUP_FILTERS:
        return records
    
    accounts = ACCOUNT_GROUP_FILTERS[group_name]
    if not accounts:
        return records
    
    filtered = []
    for r in records:
        account_from = r.get('account_from', '') or ''
        account_to = r.get('account_to', '') or ''
        account = r.get('account', '') or ''
        if account_from in accounts or account_to in accounts or account in accounts:
            filtered.append(r)
    return filtered


def merge_account_column(record):
    """合并账户列：根据类型返回对应账户"""
    entry_type = record.get('entry_type', '')
    if entry_type in ['expense', 'transfer']:
        return record.get('account_from', '') or ''
    else:
        return record.get('account_to', '') or ''


def format_colored_amount(amount, is_expense: bool, is_pending: bool = False) -> str:
    """格式化带符号的金额（生活记账）
    Args:
        amount: 金额
        is_expense: 是否为支出（红色 -）
        is_pending: 是否为待确认（白色，无+/-号）
    """
    try:
        val = float(amount) if amount else 0
        if is_pending:
            return f"{abs(val):.2f}"  # 待确认：无+/-号
        elif is_expense:
            return f"-{abs(val):.2f}"
        else:
            return f"+{abs(val):.2f}"
    except:
        return str(amount) if amount else ''


def format_invest_amount(amount, is_shares_increase: bool, is_pending: bool = False) -> str:
    """格式化带符号的金额（理财记录）
    
    理财视角 - 按份额变化：
    - 份额增加（买入/买入确认/分红）= + 红色
    - 份额减少（卖出/卖出确认）= - 绿色
    
    Args:
        amount: 金额
        is_shares_increase: 是否为份额增加（买入/分红），显示 + 红色
        is_pending: 是否为待确认（白色，无+/-号）
    """
    try:
        val = float(amount) if amount else 0
        if is_pending:
            return f"{abs(val):.2f}"  # 待确认：无+/-号
        elif is_shares_increase:
            return f"+{abs(val):.2f}"  # 份额增加：+ 红色
        else:
            return f"-{abs(val):.2f}"  # 份额减少：- 绿色
    except:
        return str(amount) if amount else ''


def color_amount(val, is_pending: bool = False):
    """为金额列着色（生活记账）：负数红色（支出），正数绿色（收入），待确认白色"""
    if pd.isna(val) or val == '':
        return ''
    if is_pending:
        return ''  # 待确认：默认颜色（白色）
    val_str = str(val)
    if val_str.startswith('-'):
        return 'color: #dc3545'  # 红色
    elif val_str.startswith('+'):
        return 'color: #28a745'  # 绿色
    return ''


def color_invest_amount(val, is_pending: bool = False):
    """为金额列着色（理财记录）：+号红色（份额增加），-号绿色（份额减少），待确认白色
    
    理财视角 - 按份额变化：
    - 份额增加（买入/分红）= + 红色
    - 份额减少（卖出）= - 绿色
    """
    if pd.isna(val) or val == '':
        return ''
    if is_pending:
        return ''  # 待确认：默认颜色（白色）
    val_str = str(val)
    if val_str.startswith('+'):
        return 'color: #dc3545'  # 红色（份额增加：买入/分红）
    elif val_str.startswith('-'):
        return 'color: #28a745'  # 绿色（份额减少：卖出）
    return ''


def format_decimal(value, decimals=2):
    """格式化数值"""
    if value is None or value == '':
        return ''
    try:
        return f"{float(value):,.{decimals}f}"
    except:
        return str(value)


def format_percent(value):
    """格式化百分比"""
    if value is None or value == '':
        return ''
    try:
        return f"{float(value)*100:.2f}%"
    except:
        return str(value)


# ============================================================
# 产品行情组件
# ============================================================
def render_product_quote():
    """渲染产品行情组件（支持场内和场外）"""
    from data.product_service import get_products
    from core.market_quote_service import get_latest_realtime_quote, get_latest_qdii_premium, fetch_and_save_realtime_quote, fetch_and_save_qdii_premium
    from data.nav_reader import get_latest_nav
    from core.snapshot_service import read_latest_daily
    from datetime import datetime
    import time
    
    # 获取所有产品
    all_products = get_products(is_active=True)
    
    if not all_products:
        st.info("暂无产品，请先在产品管理中添加产品")
        return
    
    # 分离场内和场外产品
    exchange_products = [p for p in all_products if p.get('channel') == 'EXCHANGE']
    otc_products = [p for p in all_products if p.get('channel') == 'OTC']
    
    # 场内场外选择（默认场内）
    quote_channel = st.radio(
        "交易类型",
        ["场内", "场外"],
        index=0,  # 默认场内
        key="quote_channel",
        horizontal=True
    )
    
    # 根据选择显示不同的产品列表
    if quote_channel == "场内":
        products_to_show = exchange_products
    else:
        products_to_show = otc_products
    
    if not products_to_show:
        st.warning(f"⚠️ 暂无{quote_channel}产品，请先在产品管理中添加{quote_channel}产品")
        return
    
    # 产品选择
    product_options = {p['id']: f"{p.get('code', '')} - {p.get('name') or p.get('product_name', '')}" 
                      for p in products_to_show}
    
    # 默认选择第一个产品
    default_idx = 0
    selected_product_id = st.selectbox("选择产品", 
                                     options=list(product_options.keys()),
                                     format_func=lambda x: product_options[x],
                                     index=default_idx if default_idx < len(product_options) else 0,
                                     key="product_quote_select")
    
    if selected_product_id:
        product = next((p for p in all_products if p['id'] == selected_product_id), None)
        if not product:
            return
        
        channel = product.get('channel', 'OTC')
        product_code = product.get('code', '')
        product_name = product.get('name') or product.get('product_name', '')
        
        # 自动刷新逻辑（场内产品：交易时间段内每分钟自动刷新）
        if channel == 'EXCHANGE':
            now = datetime.now()
            hour = now.hour
            minute = now.minute
            # 交易时间：9:00-11:30, 13:00-15:00（周一至周五）
            is_trading_hours = (9 <= hour < 11) or (hour == 11 and minute <= 30) or (13 <= hour < 15)
            is_weekday = now.weekday() < 5
            
            if is_trading_hours and is_weekday:
                # 显示自动刷新提示
                st.info("🔄 交易时间段内，调度器将自动采集行情数据（每分钟）")
            else:
                # 非交易时间段，显示提示
                if not is_weekday:
                    st.info("📅 当前为非交易日")
                else:
                    st.info("⏰ 当前为非交易时间段（交易时间：9:00-11:30, 13:00-15:00）")
        
        # 手动刷新按钮（保留，用于立即刷新所有产品）
        if st.button("🔄 立即刷新行情", key="manual_refresh_quote", use_container_width=True):
            with st.spinner("正在获取所有产品最新行情并计算指标..."):
                try:
                    from core.market_quote_service import collect_realtime_quotes
                    from advisor.indicator_job import calculate_indicators_for_all_products
                    from advisor.advisor_service import run_for_all_products
                    from data.product_service import get_products
                    
                    # 获取所有活跃产品
                    all_products = get_products(is_active=True)
                    exchange_products = [p for p in all_products if p.get('channel') == 'EXCHANGE']
                    otc_products = [p for p in all_products if p.get('channel') == 'OTC']
                    
                    success_count = 0
                    fail_count = 0
                    
                    # 刷新场内产品行情
                    if exchange_products:
                        product_ids = [p['id'] for p in exchange_products]
                        results = collect_realtime_quotes(product_ids)
                        success_count += sum(1 for v in results.values() if v)
                        fail_count += sum(1 for v in results.values() if not v)
                    
                    # 刷新场外产品净值
                    if otc_products:
                        from core.nav_collector import collect_and_store
                        collect_and_store()
                    
                    # 为所有场内产品计算指标
                    if exchange_products:
                        indicator_results = calculate_indicators_for_all_products()
                        success_count += indicator_results.get('success_count', 0)
                        fail_count += indicator_results.get('fail_count', 0)
                    
                    # 为所有场内产品生成建议
                    if exchange_products:
                        advisor_results = run_for_all_products()
                        success_count += advisor_results.get('success_count', 0)
                        fail_count += advisor_results.get('fail_count', 0)
                    
                    st.success(f"✅ 刷新完成！成功: {success_count}, 失败: {fail_count}")
                    st.rerun()
                except Exception as e:
                    st.error(f"❌ 刷新失败: {e}")
                    import traceback
                    st.code(traceback.format_exc())
        
        st.divider()
        
        if channel == 'EXCHANGE':
            # 场内产品：显示实时行情
            _render_exchange_quote(product, selected_product_id)
        else:
            # 场外产品：显示净值行情
            _render_otc_quote(product, product_code)
        


def _render_exchange_quote(product, product_id):
    """渲染场内产品行情"""
    from core.market_quote_service import get_latest_realtime_quote, get_latest_qdii_premium
    from core.premium_brake import apply_premium_brake
    
    st.markdown(f"**{product.get('name') or product.get('product_name', '')}** ({product.get('code', '')}) - 场内实时行情")
    
    # 实时行情
    latest_quote = get_latest_realtime_quote(product_id)
    
    # 调试信息：显示获取到的字段（可选，用于排查问题）
    # if latest_quote:
    #     st.json(latest_quote)  # 取消注释以查看原始数据
    
    if latest_quote:
        # ========== ① 价格 & 估值核心（最重要） ==========
        st.markdown("**💰 价格 & 估值核心**")
        col1, col2, col3, col4 = st.columns(4)
        with col1:
            price_val = latest_quote.get('price')
            if price_val is not None:
                price = float(price_val)
                st.metric("最新价", f"{price:.4f}", help="当前最新成交价，用于判断买入时机")
            else:
                st.metric("最新价", "N/A", help="当前最新成交价，用于判断买入时机")
        with col2:
            # IOPV 实时估值
            iopv_val = latest_quote.get('iopv')
            if iopv_val is not None:
                iopv = float(iopv_val)
                st.metric("IOPV实时估值", f"{iopv:.4f}", help="ETF的实时参考净值（Indicative Optimized Portfolio Value），用于计算溢价率。溢价率 = (最新价 - IOPV) / IOPV")
            else:
                st.metric("IOPV实时估值", "N/A", help="ETF的实时参考净值（Indicative Optimized Portfolio Value），用于计算溢价率")
        with col3:
            # 溢价率（从实时行情中获取，如果没有则从 QDII 溢价率表获取）
            premium_rate_val = latest_quote.get('premium_rate')
            if premium_rate_val is not None:
                premium_rate = float(premium_rate_val) * 100
                # 根据溢价率显示颜色
                if premium_rate <= 1:
                    delta_color = "normal"
                    label = "✅ 可买"
                elif premium_rate <= 3:
                    delta_color = "normal"
                    label = "⚠️ 减半"
                else:
                    delta_color = "inverse"
                    label = "❌ 等待池"
                st.metric("溢价率", f"{premium_rate:.2f}%", delta=label, delta_color=delta_color, 
                         help="溢价率 = (最新价 - IOPV) / IOPV × 100%。QDII产品买入规则：≤1%正常买入，1%-2%半买，>2%进入等待池")
            else:
                st.metric("溢价率", "N/A", help="溢价率 = (最新价 - IOPV) / IOPV × 100%。QDII产品买入规则：≤1%正常买入，1%-2%半买，>2%进入等待池")
        with col4:
            pct_chg_val = latest_quote.get('pct_chg')
            if pct_chg_val is not None:
                pct_chg = float(pct_chg_val) * 100
                st.metric("涨跌幅", f"{pct_chg:.2f}%", delta=f"{pct_chg:.2f}%", 
                         help="当日涨跌幅 = (最新价 - 昨收价) / 昨收价 × 100%")
            else:
                st.metric("涨跌幅", "N/A", help="当日涨跌幅 = (最新价 - 昨收价) / 昨收价 × 100%")
        
        # ========== ② 基础价格时间序列（用于高低位判断） ==========
        st.divider()
        st.markdown("**📊 基础价格时间序列**")
        col1, col2, col3, col4, col5 = st.columns([1, 1, 1, 1, 1.2])  # 给行情时间更多空间
        with col1:
            open_val = latest_quote.get('open')
            if open_val is not None:
                open_price = float(open_val)
                st.metric("开盘价", f"{open_price:.4f}", help="当日开盘价，用于判断价格波动范围")
            else:
                st.metric("开盘价", "N/A", help="当日开盘价，用于判断价格波动范围")
        with col2:
            high_val = latest_quote.get('high')
            if high_val is not None:
                high_price = float(high_val)
                st.metric("最高价", f"{high_price:.4f}", help="当日最高价，用于判断价格波动范围")
            else:
                st.metric("最高价", "N/A", help="当日最高价，用于判断价格波动范围")
        with col3:
            low_val = latest_quote.get('low')
            if low_val is not None:
                low_price = float(low_val)
                st.metric("最低价", f"{low_price:.4f}", help="当日最低价，用于判断价格波动范围")
            else:
                st.metric("最低价", "N/A", help="当日最低价，用于判断价格波动范围")
        with col4:
            prev_close_val = latest_quote.get('prev_close')
            if prev_close_val is not None:
                prev_close = float(prev_close_val)
                st.metric("昨收价", f"{prev_close:.4f}", help="前一交易日的收盘价，用于计算涨跌幅和判断价格相对位置")
            else:
                st.metric("昨收价", "N/A", help="前一交易日的收盘价，用于计算涨跌幅和判断价格相对位置")
        with col5:
            # 使用自定义显示方式，避免 st.metric 的截断问题
            quote_time = latest_quote.get('quote_time', '')
            time_str = "N/A"
            if isinstance(quote_time, str):
                # 确保完整显示日期和时间
                if len(quote_time) >= 19:
                    time_str = quote_time[:19]
                elif len(quote_time) >= 10:
                    time_str = quote_time[:10]
                else:
                    time_str = quote_time
            elif hasattr(quote_time, 'strftime'):
                time_str = quote_time.strftime('%Y-%m-%d %H:%M:%S')
            elif quote_time:
                time_str = str(quote_time)
            
            # 使用 markdown 和 HTML 来显示，确保完整显示且样式一致
            st.markdown(f"""
            <div style="padding: 0.5rem 0;">
                <div style="font-size: 0.875rem; color: rgb(128, 128, 128); margin-bottom: 0.25rem;">行情时间</div>
                <div style="font-size: 1.5rem; font-weight: 600; color: rgb(49, 51, 63);">{time_str}</div>
            </div>
            """, unsafe_allow_html=True)
        
        # ========== ③ 流动性与可交易性（质量监控） ==========
        st.divider()
        st.markdown("**💧 流动性与可交易性**")
        col1, col2, col3, col4 = st.columns(4)
        with col1:
            volume_val = latest_quote.get('volume')
            if volume_val is not None:
                volume = float(volume_val)
                st.metric("成交量", f"{volume:,.0f}", help="当日累计成交量（手数），用于判断流动性。成交量越大，流动性越好")
            else:
                st.metric("成交量", "N/A", help="当日累计成交量（手数），用于判断流动性")
        with col2:
            amount_val = latest_quote.get('amount')
            if amount_val is not None:
                amount = float(amount_val)
                st.metric("成交额", f"{amount:,.2f}", help="当日累计成交额（元），用于判断流动性。成交额 = 成交量 × 成交价")
            else:
                st.metric("成交额", "N/A", help="当日累计成交额（元），用于判断流动性")
        with col3:
            turnover_rate_val = latest_quote.get('turnover_rate')
            if turnover_rate_val is not None:
                turnover_rate = float(turnover_rate_val) * 100
                st.metric("换手率", f"{turnover_rate:.2f}%", help="换手率 = 成交量 / 流通股本 × 100%，用于判断交易活跃度。换手率越高，交易越活跃")
            else:
                st.metric("换手率", "N/A", help="换手率 = 成交量 / 流通股本 × 100%，用于判断交易活跃度")
        with col4:
            amplitude_val = latest_quote.get('amplitude')
            if amplitude_val is not None:
                amplitude = float(amplitude_val) * 100
                st.metric("振幅", f"{amplitude:.2f}%", help="振幅 = (最高价 - 最低价) / 昨收价 × 100%，用于判断价格波动幅度")
            else:
                st.metric("振幅", "N/A", help="振幅 = (最高价 - 最低价) / 昨收价 × 100%，用于判断价格波动幅度")
        
        # ========== QDII 溢价率决策建议（如果溢价率未在实时行情中） ==========
        if product.get('is_qdii'):
            # 如果实时行情中没有溢价率，尝试从 QDII 溢价率表获取
            if latest_quote.get('premium_rate') is None:
                st.divider()
                st.markdown("**💰 QDII溢价率（从溢价率表）**")
                latest_premium = get_latest_qdii_premium(product_id)
                
                if latest_premium:
                    premium_rate = float(latest_premium.get('premium_rate', 0)) * 100
                    iopv = float(latest_premium.get('iopv', 0))
                    
                    col1, col2, col3 = st.columns(3)
                    with col1:
                        st.metric("溢价率", f"{premium_rate:.2f}%", 
                                 help="溢价率 = (最新价 - IOPV) / IOPV × 100%。QDII产品买入规则：≤1%正常买入，1%-2%半买，>2%进入等待池")
                    with col2:
                        st.metric("IOPV", f"{iopv:.4f}", 
                                 help="ETF的实时参考净值（Indicative Optimized Portfolio Value），用于计算溢价率")
                    with col3:
                        # 买入建议
                        if premium_rate <= 1:
                            st.metric("买入建议", "✅ 正常买入", delta="100%", 
                                     help="溢价率≤1%，建议正常买入100%预算")
                        elif premium_rate <= 3:
                            st.metric("买入建议", "⚠️ 买入一半", delta="50%", 
                                     help="溢价率1%-3%，建议买入50%预算，剩余50%进入等待池")
                        else:
                            st.metric("买入建议", "❌ 暂停买入", delta="0%", 
                                     help="溢价率>3%，建议暂停买入，全部预算进入等待池")
                else:
                    st.info("暂无溢价率数据")
        
        # ========== 技术指标展示 ==========
        st.divider()
        st.markdown("**📈 技术指标**")
        try:
            from advisor.repos.indicator_daily_repo import get_latest_indicator
            from advisor.repos.product_strategy_bind_repo import get_bind_by_product_id
            from advisor.advisor_service import get_strategy_config
            
            bind = get_bind_by_product_id(product_id)
            indicator = None
            window_days = 750
            
            if bind:
                param_json = get_strategy_config(bind.get('strategy_code', ''), bind.get('param_set_id', 'default'))
                if param_json:
                    window_days = param_json.get('window_days', 750)
                    indicator = get_latest_indicator(product_id, window_days)
            
            if indicator:
                price_val = latest_quote.get('price')
                current_price = float(price_val) if price_val is not None else None
                
                # 获取指标值
                pct_rank = indicator.get('pct_rank')
                q_buy_price = indicator.get('q_buy_price')
                peak_close = indicator.get('peak_close')
                drawdown_from_peak = indicator.get('drawdown_from_peak')
                ma20 = indicator.get('ma20')
                ma60 = indicator.get('ma60')
                
                col1, col2, col3, col4 = st.columns(4)
                
                with col1:
                    if pct_rank is not None:
                        pct_rank_pct = float(pct_rank) * 100
                        st.metric("分位排名", f"{pct_rank_pct:.2f}%", 
                                 help=f"当前价格在最近{window_days}个交易日中的分位位置（0-100%）。分位越高，说明当前价格处于历史高位。")
                    else:
                        st.metric("分位排名", "N/A", 
                                 help=f"当前价格在最近{window_days}个交易日中的分位位置。指标未计算。")
                
                with col2:
                    # 获取策略参数以显示命中档位（不再显示 q_buy_price）
                    from advisor.repos.product_strategy_bind_repo import get_bind_by_product_id
                    from advisor.advisor_service import get_strategy_config
                    bind_detail = get_bind_by_product_id(product_id)
                    matched_tier_label = None
                    matched_tier_ratio = None
                    if bind_detail and bind_detail.get('strategy_code') == 'percentile':
                        param_json_detail = get_strategy_config('percentile', bind_detail.get('param_set_id', 'default'))
                        if param_json_detail and pct_rank is not None:
                            tiers = param_json_detail.get('tiers')
                            if tiers and isinstance(tiers, list):
                                pct_rank_float = float(pct_rank)
                                for tier in tiers:
                                    max_rank = float(tier.get('max_rank', 1.01))
                                    if pct_rank_float < max_rank:
                                        matched_tier_label = tier.get('label', '未知档位')
                                        matched_tier_ratio = float(tier.get('suggest_ratio', 0.0))
                                        break
                    
                    if matched_tier_label:
                        pct_rank_display = float(pct_rank) * 100 if pct_rank is not None else 0
                        matched_tier_ratio_pct = float(matched_tier_ratio) * 100
                        st.metric("命中档位", matched_tier_label,
                                 delta=f"建议比例{matched_tier_ratio_pct:.0f}%",
                                 help=f"根据分位排名{pct_rank_display:.2f}%命中的策略档位，决定买入建议比例。")
                    elif pct_rank is not None:
                        st.metric("命中档位", "未命中",
                                 help="分位排名未命中任何档位（可能分位过高）。")
                    else:
                        st.metric("命中档位", "N/A",
                                 help="分位排名未计算，无法确定命中档位。")
                
                with col3:
                    if peak_close is not None:
                        peak = float(peak_close)
                        st.metric("峰值价格", f"{peak:.4f}",
                                 help=f"最近{window_days}个交易日内的最高收盘价。用于计算回撤幅度。")
                    else:
                        st.metric("峰值价格", "N/A",
                                 help=f"最近{window_days}个交易日内的最高收盘价。指标未计算。")
                
                with col4:
                    if drawdown_from_peak is not None:
                        drawdown_pct = abs(float(drawdown_from_peak)) * 100
                        st.metric("回撤幅度", f"{drawdown_pct:.2f}%",
                                 help=f"相对峰值的回撤百分比。回撤越大，说明价格从高点下跌越多，可能是买入机会。")
                    else:
                        st.metric("回撤幅度", "N/A",
                                 help="相对峰值的回撤百分比。指标未计算。")
                
                col5, col6 = st.columns(2)
                
                with col5:
                    if ma20 is not None:
                        ma20_val = float(ma20)
                        if current_price is not None:
                            price_ma20_ratio = (current_price / ma20_val * 100) if ma20_val > 0 else 0
                            st.metric("MA20", f"{ma20_val:.4f}", delta=f"{price_ma20_ratio:.1f}%",
                                     help=f"20日移动平均线。当前价/MA20 = {price_ma20_ratio:.1f}%，表示当前价{'高于' if price_ma20_ratio > 100 else '低于'}MA20的{abs(price_ma20_ratio - 100):.1f}%。")
                        else:
                            st.metric("MA20", f"{ma20_val:.4f}",
                                     help="20日移动平均线。用于判断价格趋势。")
                    else:
                        st.metric("MA20", "N/A",
                                 help="20日移动平均线。指标未计算。")
                
                with col6:
                    if ma60 is not None:
                        ma60_val = float(ma60)
                        if current_price is not None:
                            price_ma60_ratio = (current_price / ma60_val * 100) if ma60_val > 0 else 0
                            st.metric("MA60", f"{ma60_val:.4f}", delta=f"{price_ma60_ratio:.1f}%",
                                     help=f"60日移动平均线。当前价/MA60 = {price_ma60_ratio:.1f}%，表示当前价{'高于' if price_ma60_ratio > 100 else '低于'}MA60的{abs(price_ma60_ratio - 100):.1f}%。")
                        else:
                            st.metric("MA60", f"{ma60_val:.4f}",
                                     help="60日移动平均线。用于判断价格趋势。")
                    else:
                        st.metric("MA60", "N/A",
                                 help="60日移动平均线。指标未计算。")
            else:
                st.info("指标未就绪：产品未绑定策略或指标未计算。指标会在实时行情更新时自动计算。")
        except Exception as e:
            import traceback
            st.error(f"加载指标失败: {e}")
            st.exception(e)
            logger.error(f"加载指标失败: {e}", exc_info=True)
        
        # ========== 生产建议展示 ==========
        st.divider()
        st.markdown("**💡 生产建议（Advisor）**")
        try:
            from advisor.repos.advisor_suggestion_repo import get_latest_suggestion
            from advisor.repos.product_strategy_bind_repo import get_bind_by_product_id
            from core.pending_buy_service import get_pending_pool
            
            suggestion = get_latest_suggestion(product_id)
            bind = get_bind_by_product_id(product_id)
            
            # 读取所有策略绑定（支持多策略组合）
            from advisor.repos.product_strategy_bind_repo import get_binds_by_product_id
            binds = get_binds_by_product_id(product_id)
            
            if binds:
                # 显示策略组合
                strategy_codes = [b.get('strategy_code', '') for b in binds]
                strategy_types = [b.get('strategy_type', 'TRIGGER') for b in binds]
                param_set_ids = [b.get('param_set_id', '') for b in binds]
                
                col1, col2 = st.columns([1, 1])
                with col1:
                    # 显示策略组合（多个策略badges）
                    strategy_badges = []
                    for i, (code, stype, pid) in enumerate(zip(strategy_codes, strategy_types, param_set_ids)):
                        type_label = {'VETO': '否决', 'TRIGGER': '触发', 'SCORE': '强度'}.get(stype, stype)
                        strategy_badges.append(f"{code}@{pid} ({type_label})")
                    st.info(f"**策略组合**: {' + '.join(strategy_badges)}")
                    st.caption(f"策略类型：VETO=否决层（任一命中即拒绝），TRIGGER=触发层（任一命中即考虑），SCORE=强度层（决定买入金额）。参数集：{param_set_ids[0]}（通常为default，可在策略实验室中创建不同参数集）。")
                
                if suggestion:
                    # 使用ViewModel字段（优先使用扩展字段，兼容旧数据）
                    # 安全转换None值
                    def safe_float(value, default=0.0):
                        """安全转换为float，处理None值"""
                        if value is None:
                            return default
                        try:
                            return float(value)
                        except (ValueError, TypeError):
                            return default
                    
                    action = suggestion.get('action', 'HOLD')
                    budget_to_execute = safe_float(suggestion.get('budget_to_execute') or suggestion.get('suggest_amount'), 0.0)
                    budget_to_wait_pool = safe_float(suggestion.get('budget_to_wait_pool') or suggestion.get('moved_to_wait_pool'), 0.0)
                    execute_ratio = safe_float(suggestion.get('execute_ratio') or suggestion.get('suggest_ratio'), 0.0)
                    wait_ratio = safe_float(suggestion.get('wait_ratio'), 0.0)
                    # 如果数据库中的值为0或缺失，使用实时计算值
                    from advisor.advisor_service import get_budget_amount, get_pending_amount_sum
                    from core.ledger_service import calc_account_balance
                    from data.db_connector import execute_query
                    
                    # 实时计算计划预算
                    realtime_plan_budget = get_budget_amount(product_id, include_below_min=True)
                    plan_budget_today = safe_float(suggestion.get('plan_budget_today'), 0.0)
                    # 如果数据库中的值为0但实时计算有值，使用实时计算值
                    if plan_budget_today == 0.0 and realtime_plan_budget > 0:
                        plan_budget_today = float(realtime_plan_budget)
                    
                    # 实时计算可用现金
                    realtime_cash_available = Decimal('0')
                    sql_cash = """
                        SELECT DISTINCT apr.from_account_id, a.account_code
                        FROM account_pool_rules apr
                        INNER JOIN accounts a ON apr.from_account_id = a.id
                        WHERE apr.to_product_id = %s 
                          AND apr.is_active = 1
                          AND a.is_active = 1
                    """
                    cash_rules = execute_query(sql_cash, (product_id,))
                    for rule in cash_rules:
                        account_code = rule.get('account_code', '')
                        if account_code:
                            try:
                                balance = calc_account_balance(account_code)
                                realtime_cash_available += Decimal(str(balance))
                            except:
                                pass
                    cash_available = safe_float(suggestion.get('cash_available'), 0.0)
                    # 如果数据库中的值为0但实时计算有值，使用实时计算值
                    if cash_available == 0.0 and realtime_cash_available > 0:
                        cash_available = float(realtime_cash_available)
                    
                    # 实时计算等待池余额
                    realtime_wait_pool = get_pending_amount_sum(product_id) or Decimal('0')
                    wait_pool_balance = safe_float(suggestion.get('wait_pool_balance'), 0.0)
                    # 如果数据库中的值为0但实时计算有值，使用实时计算值
                    if wait_pool_balance == 0.0 and realtime_wait_pool > 0:
                        wait_pool_balance = float(realtime_wait_pool)
                    
                    # 重新计算budget_for_execution
                    budget_for_execution = min(plan_budget_today, cash_available)
                    reason = suggestion.get('reason', '')
                    as_of_time = suggestion.get('as_of_time', '')
                    premium_rate_sug = suggestion.get('premium_rate')
                    reason_blocks = suggestion.get('reason_blocks', [])
                    limit_price_hint = suggestion.get('limit_price_hint')
                    time_window_hint = suggestion.get('time_window_hint')
                    
                    # 获取交易约束信息
                    first_bind = binds[0]
                    min_trade_amount = safe_float(first_bind.get('min_trade_amount'), 1000.0)
                    ideal_trade_amount = safe_float(first_bind.get('ideal_trade_amount'), 2000.0)
                    fee_rate = safe_float(first_bind.get('fee_rate'), 0.000845)
                    fee_min = safe_float(first_bind.get('fee_min'), 0.20)
                    estimated_fee = max(budget_to_execute * fee_rate, fee_min) if budget_to_execute > 0 else 0.0
                    
                    # 检查持仓情况
                    from core.exchange_holdings_calculator import calculate_exchange_holdings
                    holding_info = calculate_exchange_holdings(product_id)
                    has_holding = holding_info.get('shares', Decimal('0')) > Decimal('0')
                    
                    # 动作颜色和标签
                    if action == 'BUY':
                        action_color = "🟢"
                        action_label = "建仓" if not has_holding else "买入"
                        action_help = "建议买入。当前价格满足策略买入条件，且预算充足。如果没有持仓则为建仓，已有持仓则为加仓。"
                    elif action == 'WAIT':
                        action_color = "🟡"
                        action_label = "等待"
                        action_help = "建议等待。资金已进入等待池，等待条件满足后再买入。具体原因请查看下方原因说明。"
                    else:
                        action_color = "⚪"
                        if not has_holding:
                            action_label = "等待建仓"
                            action_help = "当前价格未满足策略买入条件（如分位排名偏高），暂不建议建仓。等待价格回落至合适分位时再建仓。"
                        else:
                            action_label = "持有"
                            action_help = "建议持有。当前价格未满足策略买入条件，但已有持仓，继续持有等待更好的买入时机。"
                    
                    with col2:
                        st.info(f"**建议动作**: {action_color} {action_label}")
                        st.caption(action_help)
                    
                    # ========== 预算三件套展示 ==========
                    st.markdown("**💰 资金与预算**")
                    
                    # 获取新字段（如果存在）
                    new_budget = safe_float(suggestion.get('new_budget'), plan_budget_today)
                    wait_pool_before = safe_float(suggestion.get('wait_pool_before'), wait_pool_balance)
                    planned_amount = safe_float(suggestion.get('planned_amount'), new_budget + wait_pool_before)
                    
                    # 第一行：三个核心金额
                    col1, col2, col3 = st.columns(3)
                    with col1:
                        st.metric("本轮新增预算", f"¥{new_budget:.2f}",
                                 help="本轮新增预算 = 根据资金规则（account_pool_rules）计算出的本轮新可投入金额。这是本次评估周期新产生的可支配额度。")
                    with col2:
                        st.metric("待买入池余额", f"¥{wait_pool_before:.2f}",
                                 help="待买入池余额（before）= 之前已经决定要买、但因为条件不满足而延期的保留资金，按产品维度累计。跨天累计，永不凭空消失，除非被实际成交扣减消耗。")
                    with col3:
                        st.metric("本轮可用于买入", f"¥{planned_amount:.2f}",
                                 help="本轮可用于买入 = 本轮新增预算 + 待买入池余额。这是本轮最多可用于该产品的预算上限，不是必须全花。")
                    
                    # 第二行：执行和延期
                    col1, col2, col3, col4 = st.columns(4)
                    with col1:
                        st.metric("实际执行", f"¥{budget_to_execute:.2f}",
                                 help="本次建议实际执行金额 = 最终BUY的金额（可能为0）。这是真正会用于买入的金额。")
                    with col2:
                        st.metric("转等待池", f"¥{budget_to_wait_pool:.2f}",
                                 help="本次应转入等待池的预算金额 = 由溢价刹车/门槛导致无法立即买入的金额。这些资金会进入等待池，等待条件满足后再使用。")
                    with col3:
                        st.metric("可用现金", f"¥{cash_available:.2f}",
                                 help="可用现金池余额 = 从所有相关账户汇总的余额（通过account_pool_rules）。这是今天可用于执行的现金总额。")
                    with col4:
                        wait_pool_after = wait_pool_before + budget_to_wait_pool
                        st.metric("等待池余额（after）", f"¥{wait_pool_after:.2f}",
                                 help="等待池余额（after）= 待买入池余额（before）+ 本次转等待池金额。Advisor不扣减等待池，所以after = before + moved_to_wait。")
                    
                    # 等待池累计金额说明
                    if wait_pool_balance > 0 or budget_to_wait_pool > 0:
                        st.info(f"**等待池说明**：待买入池余额（before）= ¥{wait_pool_before:.2f}，本次转入 = ¥{budget_to_wait_pool:.2f}，等待池余额（after）= ¥{wait_pool_after:.2f}。等待池用于暂存因溢价过高、预算不足最小成交额、指标未就绪等原因无法立即买入的资金。等待池金额会参与下次预算计算，当条件满足时自动使用。")
                    
                    # ========== 比例展示 ==========
                    st.markdown("**📊 执行比例**")
                    col1, col2 = st.columns(2)
                    with col1:
                        # 重新计算执行比例（基于实时计算的budget_for_execution）
                        realtime_execute_ratio = (budget_to_execute / budget_for_execution) if budget_for_execution > 0 else 0.0
                        st.metric("执行比例", f"{realtime_execute_ratio*100:.1f}%",
                                 help=f"本次预算中用于执行的比例 = budget_to_execute / budget_for_execution × 100%。当前为 {realtime_execute_ratio*100:.1f}%，表示 {realtime_execute_ratio*100:.1f}% 的预算用于买入。")
                    with col2:
                        # 重新计算转等待池比例（基于实时计算的budget_for_execution）
                        realtime_wait_ratio = (budget_to_wait_pool / budget_for_execution) if budget_for_execution > 0 else 0.0
                        st.metric("转等待池比例", f"{realtime_wait_ratio*100:.1f}%",
                                 help=f"本次预算中进入等待池的比例 = budget_to_wait_pool / budget_for_execution × 100%。当前为 {realtime_wait_ratio*100:.1f}%，表示 {realtime_wait_ratio*100:.1f}% 的预算进入等待池。")
                    
                    # ========== 交易约束与成本 ==========
                    st.markdown("**⚙️ 交易约束与成本**")
                    col1, col2, col3, col4 = st.columns(4)
                    with col1:
                        constraint_status = "✅ 满足" if budget_to_execute >= min_trade_amount or budget_to_execute == 0 else "❌ 不满足"
                        st.metric("最小成交额", f"¥{min_trade_amount:.2f}", delta=constraint_status,
                                 help=f"最小有效成交金额 = {min_trade_amount:.2f}。BUY时建议金额必须 ≥ 此值，否则进入等待池。")
                    with col2:
                        st.metric("理想成交额", f"¥{ideal_trade_amount:.2f}",
                                 help=f"理想成交金额 = {ideal_trade_amount:.2f}。策略会尽量接近此金额，但可能因预算限制而调整。")
                    with col3:
                        st.metric("预计手续费", f"¥{estimated_fee:.2f}",
                                 help=f"预计手续费 = max(买入金额 × {fee_rate*10000:.2f}‱, {fee_min:.2f}元)。当前为 ¥{estimated_fee:.2f}。")
                    with col4:
                        if premium_rate_sug is not None:
                            premium_pct = safe_float(premium_rate_sug, 0.0) * 100
                            if premium_pct > 2:
                                brake_status = "🚫 全部等待"
                            elif premium_pct > 1:
                                brake_status = "⚠️ 半买"
                            else:
                                brake_status = "✅ 正常"
                            st.metric("溢价率", f"{premium_pct:.2f}%", delta=brake_status,
                                     help=f"QDII产品的溢价率 = (最新价 - IOPV) / IOPV × 100%。当前溢价率 {premium_pct:.2f}%。买入规则：≤1%正常买入，1%-2%半买，>2%进入等待池。")
                        else:
                            st.metric("溢价率", "N/A",
                                     help="QDII产品的溢价率 = (最新价 - IOPV) / IOPV × 100%。非QDII产品或溢价率数据缺失时显示N/A。")
                    
                    # 溢价刹车结果详情
                    if premium_rate_sug is not None and safe_float(premium_rate_sug, 0.0) > 0.01:
                        premium_pct = safe_float(premium_rate_sug, 0.0) * 100
                        if premium_pct > 2:
                            st.warning(f"**溢价刹车触发**：溢价率 {premium_pct:.2f}% > 2%，全部预算进入等待池。建议窗口：{time_window_hint or '10:30-11:15/13:30-14:30'}；建议限价：{limit_price_hint or '昨收×0.998' if limit_price_hint else 'N/A'}。")
                        elif premium_pct > 1:
                            st.info(f"**溢价刹车触发**：溢价率 {premium_pct:.2f}% 处于(1%,2%]，执行半买策略。建议窗口：{time_window_hint or '10:30-11:15/13:30-14:30'}；建议限价：{limit_price_hint or '昨收×0.998' if limit_price_hint else 'N/A'}。")
                    
                    # 计算逻辑说明（并排展示策略参数集，共同展开折叠）
                    with st.expander("📊 计算逻辑 & ⚙️ 策略参数集", expanded=False):
                        col_calc, col_param = st.columns([1, 1])
                        
                        with col_calc:
                            st.markdown("**📊 计算逻辑**")
                            # 预算计算公式
                            st.markdown("**预算计算公式：**")
                            from advisor.advisor_service import get_budget_amount
                            from data.db_connector import execute_query
                            from core.ledger_service import calc_account_balance
                            # Decimal已在文件顶部导入，无需重复导入
                            
                            # 获取资金池规则详情
                            sql_rules = """
                                SELECT apr.from_account_id, apr.ratio, apr.min_amount, apr.round_step,
                                       a.account_code, a.account_name
                                FROM account_pool_rules apr
                                INNER JOIN accounts a ON apr.from_account_id = a.id
                                WHERE apr.to_product_id = %s 
                                  AND apr.is_active = 1
                                  AND a.is_active = 1
                            """
                            rules_detail = execute_query(sql_rules, (product_id,))
                            
                            if rules_detail:
                                total_pool_balance = Decimal('0')
                                account_details = []
                                for rule in rules_detail:
                                    account_code = rule.get('account_code', '')
                                    account_name = rule.get('account_name', '')
                                    if account_code:
                                        try:
                                            balance = calc_account_balance(account_code)
                                            total_pool_balance += balance
                                            account_details.append({
                                                'name': account_name,
                                                'code': account_code,
                                                'balance': balance
                                            })
                                        except:
                                            pass
                                
                                if account_details:
                                    min_amount = float(rules_detail[0].get('min_amount', 0))
                                    round_step = float(rules_detail[0].get('round_step', 1))
                                    
                                    st.markdown(f"**1. 各账户按比例分配 = Σ(账户余额 × 该账户分配比例)**")
                                    allocated = Decimal('0')
                                    for rule in rules_detail:
                                        account_code = rule.get('account_code', '')
                                        account_name = rule.get('account_name', '')
                                        ratio = float(rule.get('ratio', 0))
                                        if account_code:
                                            for acc in account_details:
                                                if acc['code'] == account_code:
                                                    account_allocated = acc['balance'] * Decimal(str(ratio))
                                                    allocated += account_allocated
                                                    st.caption(f"   - {account_name} ({account_code}): ¥{acc['balance']:.2f} × {ratio*100:.2f}% = ¥{account_allocated:.2f}")
                                                    break
                                    st.caption(f"   **合计**: ¥{allocated:.2f}")
                                    
                                    st.markdown(f"**3. 应用最小金额约束**")
                                    if allocated < Decimal(str(min_amount)):
                                        st.caption(f"   ¥{allocated:.2f} < ¥{min_amount:.2f} (最小金额) → 预算 = ¥0.00")
                                    else:
                                        st.caption(f"   ¥{allocated:.2f} ≥ ¥{min_amount:.2f} (最小金额) → 继续")
                                    
                                    if round_step > 0 and allocated >= Decimal(str(min_amount)):
                                        st.markdown(f"**4. 取整（粒度={round_step}）**")
                                        allocated_rounded = (allocated / Decimal(str(round_step))).quantize(Decimal('1'), rounding=ROUND_DOWN) * Decimal(str(round_step))
                                        st.caption(f"   = ¥{allocated:.2f} → ¥{allocated_rounded:.2f}")
                                    
                                    st.markdown(f"**5. 最终预算 = 分配金额 + 等待池金额**")
                                    # 计算实际预算和等待池金额
                                    from advisor.advisor_service import get_pending_amount_sum
                                    total_pending = get_pending_amount_sum(product_id) or Decimal('0')
                                    actual_budget = allocated_rounded if round_step > 0 and allocated >= Decimal(str(min_amount)) else allocated
                                    total_budget = actual_budget + total_pending
                                    st.caption(f"   = ¥{float(actual_budget):.2f} + ¥{float(total_pending):.2f} = ¥{float(total_budget):.2f}")
                            else:
                                st.caption("未配置资金池规则")
                            
                            # 分位策略档位说明（仅对percentile策略）
                            # 使用第一个策略的code（兼容多策略组合）
                            first_strategy_code = strategy_codes[0] if strategy_codes else ''
                            if first_strategy_code == 'percentile':
                                st.markdown("**分位策略档位说明：**")
                                from advisor.repos.indicator_daily_repo import get_latest_indicator
                                from advisor.repos.product_strategy_bind_repo import get_bind_by_product_id
                                from advisor.advisor_service import get_strategy_config
                                from data.db_connector import execute_one
                                from datetime import date, timedelta
                                
                                bind_detail = get_bind_by_product_id(product_id)
                                if bind_detail:
                                    param_json_detail = get_strategy_config(first_strategy_code, bind_detail.get('param_set_id', 'default'))
                                    if param_json_detail:
                                        window_days = param_json_detail.get('window_days', 750)
                                        tiers = param_json_detail.get('tiers')
                                        min_required = max(int(window_days * 0.6), 300)
                                        
                                        indicator_detail = get_latest_indicator(product_id, window_days)
                                        pct_rank_val = indicator_detail.get('pct_rank') if indicator_detail else None
                                        
                                        if tiers and isinstance(tiers, list) and pct_rank_val is not None:
                                            pct_rank_float = float(pct_rank_val)
                                            trade_date = indicator_detail.get('trade_date') if indicator_detail else None
                                            
                                            st.caption(f"**1. 获取最近N={window_days}个交易日的收盘价数据**")
                                            st.caption(f"**2. 计算当前价格的分位排名（pct_rank）**")
                                            pct_rank_display_val = float(pct_rank_float) * 100
                                            st.caption(f"   **当前分位排名 = {pct_rank_display_val:.2f}%**（计算日期：{trade_date}）")
                                            st.caption(f"**3. 根据分位排名命中对应档位：**")
                                            
                                            matched_tier = None
                                            for tier in tiers:
                                                max_rank = float(tier.get('max_rank', 1.01))
                                                suggest_ratio = float(tier.get('suggest_ratio', 0.0))
                                                label = tier.get('label', '未知档位')
                                                
                                                if pct_rank_float < max_rank and matched_tier is None:
                                                    matched_tier = tier
                                                    st.caption(f"   ✅ **命中档位：{label}**（max_rank<{max_rank*100:.0f}%，建议比例={suggest_ratio*100:.0f}%）")
                                                else:
                                                    st.caption(f"   - {label}（max_rank<{max_rank*100:.0f}%，建议比例={suggest_ratio*100:.0f}%）")
                                            
                                            if matched_tier:
                                                st.caption(f"**4. 根据命中档位的建议比例计算买入金额**")
                                        else:
                                            # 诊断为什么分位排名未就绪
                                            st.caption("**分位排名未就绪，诊断信息：**")
                                            
                                            # 检查历史数据量
                                            yesterday = date.today() - timedelta(days=1)
                                            sql_data_count = """
                                                SELECT COUNT(*) as cnt
                                                FROM market_bar_d
                                                WHERE product_id = %s
                                                  AND trade_date < %s
                                                  AND trade_date >= DATE_SUB(%s, INTERVAL %s DAY)
                                            """
                                            data_count_row = execute_one(sql_data_count, (product_id, yesterday, yesterday, window_days))
                                            data_count = data_count_row.get('cnt', 0) if data_count_row else 0
                                            
                                            st.caption(f"   - 需要至少{min_required}个交易日的数据")
                                            st.caption(f"   - 实际可用数据：{data_count}条")
                                            
                                            if data_count < min_required:
                                                st.caption(f"   - **数据不足**：缺少{min_required - data_count}条数据")
                                                st.caption(f"   - 建议：等待更多历史数据或减少window_days参数")
                                            else:
                                                st.caption(f"   - 数据量充足，但指标未计算")
                                                st.caption(f"   - 建议：检查指标计算任务（indicator_daily_update）是否正常运行")
                                            
                                            # 检查是否有指标记录
                                            if indicator_detail:
                                                st.caption(f"   - 存在指标记录，但pct_rank为NULL（计算失败）")
                                            else:
                                                st.caption(f"   - 不存在指标记录（指标计算任务可能未运行）")
                                            
                                            if not tiers or not isinstance(tiers, list) or len(tiers) == 0:
                                                st.caption(f"   - **参数配置错误**：参数集中未找到 tiers 配置")
                        
                        with col_param:
                            st.markdown("**⚙️ 策略参数集**")
                            # 获取所有策略绑定
                            from advisor.repos.product_strategy_bind_repo import get_binds_by_product_id
                            from advisor.advisor_service import get_strategy_config
                            from advisor.repos.indicator_daily_repo import get_latest_indicator
                            from data.db_connector import execute_one
                            import json
                            
                            binds = get_binds_by_product_id(product_id)
                            if binds:
                                for i, bind in enumerate(binds, 1):
                                    strategy_code = bind.get('strategy_code', '')
                                    param_set_id = bind.get('param_set_id', 'default')
                                    strategy_type = bind.get('strategy_type', 'TRIGGER')
                                    priority = bind.get('priority', 0)
                                    
                                    type_labels = {'VETO': '否决层', 'TRIGGER': '触发层', 'SCORE': '强度层'}
                                    type_label = type_labels.get(strategy_type, strategy_type)
                                    
                                    st.markdown(f"**策略 {i}: {strategy_code}@{param_set_id}**")
                                    st.caption(f"类型: {type_label} | 优先级: {priority}")
                                    
                                    # 获取参数配置和更新时间
                                    param_config = get_strategy_config(strategy_code, param_set_id)
                                    param_updated_at = None
                                    if param_config:
                                        # 查询参数更新时间
                                        sql_param = """
                                            SELECT updated_at
                                            FROM strategy_config
                                            WHERE strategy_key = %s AND param_set_id = %s AND is_active = 1
                                            LIMIT 1
                                        """
                                        param_row = execute_one(sql_param, (strategy_code, param_set_id))
                                        if param_row:
                                            param_updated_at = param_row.get('updated_at')
                                        
                                        # 显示参数更新时间
                                        if param_updated_at:
                                            st.caption(f"📅 参数更新时间: {param_updated_at}")
                                        
                                        st.json(param_config)
                                    else:
                                        st.warning(f"⚠️ 参数集 {param_set_id} 未找到配置")
                                    
                                    # 获取指标交易日期
                                    if param_config:
                                        window_days = param_config.get('window_days', 750)
                                        indicator = get_latest_indicator(product_id, window_days)
                                        if indicator:
                                            indicator_trade_date = indicator.get('trade_date')
                                            if indicator_trade_date:
                                                st.caption(f"📊 指标计算日期: {indicator_trade_date}")
                                    
                                    if i < len(binds):
                                        st.divider()
                            else:
                                st.warning("⚠️ 产品未绑定策略")
                    
                    # ========== 原因说明（使用reason_blocks） ==========
                    st.markdown("**📝 建议动作的原因**")
                    if reason_blocks:
                        # 使用reason_blocks分条显示（只显示决策，不显示输入值）
                        for i, block in enumerate(reason_blocks, 1):
                            rule_name = block.get('rule_name', f'原因{i}')
                            decision = block.get('decision', '')
                            
                            # 只显示决策部分，作为建议动作的原因
                            if decision:
                                st.markdown(f"**{i}. {rule_name}**")
                                st.caption(decision)
                    elif reason:
                        # 优化reason显示：解析结构化reason，分步骤展示
                        import re
                        # 尝试解析带【】标记的结构化reason
                        steps = re.split(r'【(\d+)\.\s*([^】]+)】', reason)
                        if len(steps) > 1:
                            # 有结构化标记，按步骤展示
                            i = 1
                            while i < len(steps):
                                step_num = steps[i]
                                step_title = steps[i+1] if i+1 < len(steps) else ''
                                step_content = steps[i+2] if i+2 < len(steps) else ''
                                if step_title and step_content:
                                    # 清理内容（去除多余的分隔符）
                                    step_content = step_content.strip('；。，')
                                    if step_content:
                                        st.markdown(f"**{step_num}. {step_title}**")
                                        st.caption(step_content)
                                i += 3
                        else:
                            # 没有结构化标记，使用原始reason文本
                            import html
                            escaped_reason = html.escape(reason)
                            formatted_reason = escaped_reason.replace('；', '<br>').replace('\n', '<br>')
                            st.markdown(f'<div style="padding: 10px; background-color: #f0f2f6; border-radius: 5px; white-space: pre-wrap; word-wrap: break-word;">{formatted_reason}</div>', unsafe_allow_html=True)
                    else:
                        st.info("暂无原因说明")
                    
                    # ========== 停机坪建议（PARK_CASH） ==========
                    # 从reason_blocks中查找"停机坪建议"
                    park_cash_block = None
                    for block in reason_blocks:
                        if block.get('rule_name') == '停机坪建议':
                            park_cash_block = block
                            break
                    
                    if park_cash_block:
                        park_cash_decision = park_cash_block.get('decision', '')
                        if park_cash_decision:
                            st.info(f"**🛫 停机坪建议**：{park_cash_decision}")
                    
                    # 更新时间
                    if as_of_time:
                        if isinstance(as_of_time, str):
                            time_str = as_of_time[:19] if len(as_of_time) >= 19 else as_of_time
                        else:
                            time_str = str(as_of_time)
                        st.caption(f"最后更新时间: {time_str}")
                else:
                    st.warning("暂无建议数据，请等待调度器生成")
            else:
                st.warning("产品未绑定策略，请在产品管理页配置策略绑定")
            
            # 非交易日/非交易时段提示
            if suggestion:
                from utils.trade_calendar import is_trade_day, is_trade_time
                from datetime import datetime
                as_of_dt = suggestion.get('as_of_time')
                if as_of_dt:
                    if isinstance(as_of_dt, str):
                        try:
                            as_of_dt = datetime.strptime(as_of_dt[:19], '%Y-%m-%d %H:%M:%S')
                        except:
                            as_of_dt = None
                    
                    if as_of_dt:
                        trade_day = is_trade_day(as_of_dt.date())
                        trade_time = is_trade_time(as_of_dt)
                        
                        if not trade_day:
                            st.warning("⚠️ **非交易日**：当前不在交易日，建议仅供参考，不执行买入操作。")
                        elif not trade_time:
                            st.info("ℹ️ **非交易时段**：当前不在交易时段（9:30-11:30或13:00-15:00），建议仅供参考；下次开盘再执行。")
        except Exception as e:
            st.warning(f"加载建议失败: {e}")
    else:
        st.warning("暂无实时行情数据，请点击刷新按钮获取")


def _render_otc_quote(product, product_code):
    """渲染场外产品行情"""
    from data.nav_reader import get_latest_nav
    from core.snapshot_service import read_latest_daily
    
    st.markdown(f"**{product.get('name') or product.get('product_name', '')}** ({product_code}) - 净值行情")
    
    # 最新净值
    nav_data = get_latest_nav(product_code)
    if nav_data:
        nav_date, nav = nav_data
        col1, col2 = st.columns(2)
        with col1:
            st.metric("最新净值", f"{float(nav):.4f}")
        with col2:
            st.metric("净值日期", nav_date)
    else:
        st.warning("暂无净值数据")
    
    # 持仓信息（从快照获取）
    daily_data = read_latest_daily()
    product_snapshot = next((d for d in daily_data if d.get('product_code') == product_code), None)
    
    if product_snapshot:
        st.divider()
        st.markdown("**📊 持仓信息**")
        col1, col2, col3, col4 = st.columns(4)
        with col1:
            shares = float(product_snapshot.get('shares', 0))
            st.metric("持有份额", f"{shares:,.4f}")
        with col2:
            value = float(product_snapshot.get('value', 0))
            st.metric("持仓市值", f"¥{value:,.2f}")
        with col3:
            cost = float(product_snapshot.get('cost', 0))
            st.metric("持仓成本", f"¥{cost:,.2f}")
        with col4:
            unrealized_pnl = float(product_snapshot.get('unrealized_pnl', 0))
            return_rate = float(product_snapshot.get('return_rate', 0)) * 100
            st.metric("浮动盈亏", f"¥{unrealized_pnl:,.2f}", delta=f"{return_rate:.2f}%")


# ============================================================
# Page 1: Dashboard
# ============================================================
def page_dashboard():
    """
    Dashboard页面
    在交易日的交易时间段内，自动定时刷新行情数据
    """
    # 前台自动刷新逻辑：在交易时间段内定时查询数据库
    from utils.trade_calendar import is_trade_day, is_trade_time
    from datetime import datetime
    import time
    
    # 检查是否在交易时间段内
    now = datetime.now()
    is_trading = is_trade_day(now.date()) and is_trade_time(now)
    
    # 初始化session_state中的刷新控制
    if 'auto_refresh_enabled' not in st.session_state:
        st.session_state.auto_refresh_enabled = True
    if 'last_refresh_time' not in st.session_state:
        st.session_state.last_refresh_time = time.time()
    
    # 如果不在交易时间段，显示提示
    if not is_trading:
        if not is_trade_day(now.date()):
            st.info("⏰ 当前为非交易日，自动刷新已暂停")
        else:
            st.info("⏰ 当前不在交易时段（交易时间：9:30-11:30, 13:00-15:00），自动刷新已暂停")
        # 重置刷新时间戳，避免非交易时段累积时间
        st.session_state.last_refresh_time = time.time()
    else:
        # 在交易时间段内，设置自动刷新（每60秒刷新一次）
        auto_refresh_interval = 60  # 60秒刷新一次
        
        # 检查是否需要刷新
        current_time = time.time()
        time_since_last_refresh = current_time - st.session_state.last_refresh_time
        
        # 显示自动刷新状态和倒计时
        next_refresh_in = max(0, auto_refresh_interval - int(time_since_last_refresh))
        refresh_status = st.empty()
        if next_refresh_in > 0:
            refresh_status.caption(f"🔄 自动刷新已启用（交易时段内每{auto_refresh_interval}秒刷新一次，{next_refresh_in}秒后刷新）")
        else:
            refresh_status.caption(f"🔄 自动刷新已启用（交易时段内每{auto_refresh_interval}秒刷新一次，正在刷新...）")
            # 更新时间戳
            st.session_state.last_refresh_time = current_time
            # 触发页面重新渲染（数据会从数据库重新读取）
            time.sleep(0.5)  # 短暂延迟，确保状态更新
            st.rerun()
    
    st.markdown('<p class="main-header">📊 Dashboard</p>', unsafe_allow_html=True)
    
    # 待结算订单（移到最上方）
    st.subheader("📋 待结算订单")
    st.caption("💡 选择订单可单独确认，到期订单份额已按净值计算")
    
    pending = list_pending_orders()
    
    if pending:
        # 构建显示数据
        rows = []
        raw_orders = []
        for order in pending:
            product_code = order.get('product_code', '')
            order_type = order.get('order_type', '')
            confirm_date = order.get('confirm_date', '')
            
            # 预览结算结果
            preview = preview_settle(order['order_id'])
            
            # 如果有净值，直接显示计算出的份额；否则显示订单原有份额
            if preview.get('success') and preview.get('shares'):
                display_shares = f"{preview.get('shares'):.2f}"
            else:
                # 格式化原有份额
                orig_shares = order.get('shares', '')
                display_shares = f"{float(orig_shares):.2f}" if orig_shares else '-'
            
            # 格式化金额（两位小数）
            # 对于赎回订单，如果预览成功，使用预览计算出的金额；否则使用订单原有金额
            if order_type == 'redeem_request' and preview.get('success') and preview.get('amount') is not None:
                display_amount = f"{preview.get('amount'):.2f}"
            else:
                orig_amount = order.get('amount', '')
                display_amount = f"{float(orig_amount):.2f}" if orig_amount else '-'
            
            # 处理净值列，确保类型一致（避免 Arrow 序列化错误）
            nav_value = preview.get('nav', None) if preview.get('success') else None
            nav_display = f"{nav_value:.4f}" if nav_value is not None else '-'
            
            rows.append({
                '订单号': order.get('order_id', ''),
                '产品代码': product_code,
                '类型': '买入扣款' if order_type == 'buy_debit' else '赎回发起',
                '金额': display_amount,
                '份额': display_shares,
                '确认日期': confirm_date,
                '状态': order.get('status', ''),
                '净值': nav_display,
                '备注': order.get('note', '')
            })
            raw_orders.append(order)
        
        df_pending = pd.DataFrame(rows)
        # 保存原始索引供分页后使用
        df_pending['_original_idx'] = range(len(df_pending))
        
        # 分页
        df_page = paginate_dataframe(df_pending, "pending_orders", page_size=50)
        original_indices = df_page['_original_idx'].tolist()
        
        # 显示表格（带行选择，不显示原始索引列）
        display_cols = ['订单号', '产品代码', '类型', '金额', '份额', '确认日期', '状态', '净值', '备注']
        event = st.dataframe(
            df_page[display_cols],
            width='stretch',
            hide_index=True,
            on_select="rerun",
            selection_mode="single-row",
            key="pending_orders_table"
        )
        
        # 批量结算按钮（表格下方，小按钮）
        col_btn1, col_btn2 = st.columns([1, 1])
        with col_btn1:
            if st.button("⚡ 结算今日可结算", key="batch_settle_today", use_container_width=True):
                with st.spinner("正在结算..."):
                    try:
                        today = date.today().strftime('%Y-%m-%d')
                        result = settle_orders(today)
                        
                        if result.settled:
                            st.success(f"✅ 成功结算 {len(result.settled)} 个订单")
                            for item in result.settled:
                                order = item['order']
                                st.write(f"  - {order['order_id']}: {order['order_type']} {order['product_code']}")
                        
                        if result.skipped:
                            st.info(f"ℹ️ 跳过 {len(result.skipped)} 个订单（已存在确认记录）")
                        
                        if result.errors:
                            st.warning(f"⚠️ 失败 {len(result.errors)} 个订单")
                            for item in result.errors:
                                order = item['order']
                                reason = item['reason']
                                st.write(f"  - {order['order_id']}: {reason}")
                        
                        if not result.settled and not result.skipped and not result.errors:
                            st.info("没有需要结算的订单")
                        
                        # 刷新快照
                        try:
                            collect_nav_and_build_snapshots(silent=True)
                        except:
                            pass
                        
                        st.rerun()
                        
                    except Exception as e:
                        st.error(f"❌ 结算失败: {e}")
        
        with col_btn2:
            if st.button("📋 结算全部到期", key="batch_settle_all", use_container_width=True):
                with st.spinner("正在结算..."):
                    try:
                        result = settle_orders()
                        
                        if result.settled:
                            st.success(f"✅ 成功结算 {len(result.settled)} 个订单")
                        
                        if result.skipped:
                            st.info(f"ℹ️ 跳过 {len(result.skipped)} 个订单")
                        
                        if result.errors:
                            st.warning(f"⚠️ 失败 {len(result.errors)} 个订单")
                            for item in result.errors:
                                st.write(f"  - {item['order']['order_id']}: {item['reason']}")
                        
                        try:
                            collect_nav_and_build_snapshots(silent=True)
                        except:
                            pass
                        
                        st.rerun()
                        
                    except Exception as e:
                        st.error(f"❌ 结算失败: {e}")
        
        # 检查是否选中了某行
        selected_rows = event.selection.rows if event.selection else []
        
        if selected_rows:
            page_idx = selected_rows[0]
            # 获取原始记录索引
            selected_idx = original_indices[page_idx] if page_idx < len(original_indices) else page_idx
            selected_order = raw_orders[selected_idx]
            order_id = selected_order['order_id']
            product_code = selected_order['product_code']
            order_type = selected_order['order_type']
            
            st.markdown("### 📝 确认订单")
            
            # 获取净值预览
            preview = preview_settle(order_id)
            
            col1, col2 = st.columns(2)
            
            with col1:
                st.info(f"**订单号**: {order_id}")
                st.info(f"**产品**: {product_code}")
                st.info(f"**类型**: {'买入扣款' if order_type == 'buy_debit' else '赎回发起'}")
                
                if order_type == 'buy_debit':
                    st.info(f"**扣款金额**: ¥{selected_order.get('amount', 0)}")
                    st.info(f"**手续费**: ¥{selected_order.get('fee', 0)}")
                else:
                    st.info(f"**赎回份额**: {selected_order.get('shares', 0)}")
            
            with col2:
                if preview.get('success'):
                    st.success(f"**当前净值**: {preview.get('nav', '-')}")
                    if order_type == 'buy_debit':
                        st.success(f"**预计获得份额**: {preview.get('shares', 0):.4f}")
                        st.success(f"**净申购额**: ¥{preview.get('net_amount', 0):.2f}")
                    else:
                        st.success(f"**预计到账**: ¥{preview.get('amount', 0):.2f}")
                        st.success(f"**赎回费**: ¥{preview.get('fee', 0):.2f}")
                else:
                    st.warning(f"⚠️ {preview.get('message', '无法预览')}")
            
            # 结算时间输入
            st.markdown("#### ⏰ 结算时间")
            time_col1, time_col2 = st.columns(2)
            
            # 默认使用订单的确认日期
            default_confirm_date = selected_order.get('confirm_date', date.today().strftime('%Y-%m-%d'))
            try:
                default_date = datetime.strptime(default_confirm_date, '%Y-%m-%d').date()
            except:
                default_date = date.today()
            
            with time_col1:
                settle_date = st.date_input(
                    "确认日期", 
                    value=default_date, 
                    key="settle_confirm_date"
                )
            
            with time_col2:
                settle_time = st.text_input(
                    "确认时间 (HH:MM:SS)", 
                    value=datetime.now().strftime('%H:%M:%S'), 
                    key="settle_confirm_time",
                    help="份额确认的时间，精确到秒"
                )
            
            # 组合成完整的确认时间
            confirm_datetime = f"{settle_date} {settle_time}"
            
            # 确认按钮
            if st.button(f"✅ 确认此订单 ({order_id})", type="primary", key="confirm_single_order"):
                with st.spinner("正在确认..."):
                    try:
                        result = settle_single_order(order_id, confirm_datetime=confirm_datetime)
                        
                        if result.success:
                            st.success(f"✅ {result.message}")
                            # 刷新快照
                            try:
                                collect_nav_and_build_snapshots(silent=True)
                            except:
                                pass
                            st.rerun()
                        else:
                            st.error(f"❌ 确认失败: {result.message}")
                    except Exception as e:
                        st.error(f"❌ 确认失败: {e}")
    else:
        st.info("暂无待结算订单")
    
    st.divider()
    
    # 产品行情
    st.subheader("📈 产品行情")
    render_product_quote()


# ============================================================
# Page: 资产详情
# ============================================================
def page_asset_details():
    st.markdown('<p class="main-header">💼 资产详情</p>', unsafe_allow_html=True)
    
    # 资产总览
    summary = get_portfolio_summary()
    
    col1, col2, col3, col4, col5 = st.columns(5)
    
    with col1:
        st.metric(
            label="💰 总资产",
            value=f"¥ {format_decimal(summary.global_value)}"
        )
    
    with col2:
        unrealized_delta = f"¥ {format_decimal(summary.unrealized_pnl)}"
        st.metric(
            label="📊 浮动盈亏",
            value=unrealized_delta
        )
    
    with col3:
        pnl_delta = f"¥ {format_decimal(summary.global_pnl)}"
        st.metric(
            label="📈 总盈亏",
            value=pnl_delta,
            delta=format_percent(summary.global_return) if summary.global_return else None
        )
    
    with col4:
        st.metric(
            label="🏦 基金总值",
            value=f"¥ {format_decimal(summary.fund_total)}"
        )
    
    with col5:
        st.metric(
            label="📅 数据日期",
            value=summary.fetch_date
        )
    
    st.divider()
    
    # 账户余额表格
    st.subheader("📋 账户余额")
    
    # 筛选器
    filter_col1, filter_col2 = st.columns([1, 3])
    with filter_col1:
        group_filter = st.selectbox(
            "筛选组",
            ["全部", "余利宝(ylb)", "稳利宝(wenlibao)", "基金(fund)"],
            key="balance_filter"
        )
    
    # 直接从accounts表读取余额（实时数据）
    from data.account_service import get_accounts
    from data.config_loader import load_account_groups
    from data.db_connector import execute_query
    from data.product_service import get_product_by_id
    from core.snapshot_service import read_latest_daily
    
    # 从数据库读取账户（包含balance字段）
    accounts_db = get_accounts(is_active=True)
    account_groups = load_account_groups()
    daily_data = read_latest_daily()
    
    # 构建产品代码到daily数据的映射
    daily_map = {r.get('product_code'): r for r in daily_data}
    
    # 构建账户余额数据（直接从accounts表读取balance）
    balance_data = []
    group_totals = {}  # {group_code: {'balance': Decimal, 'product_value': Decimal, 'accounts': []}}
    
    # 获取所有账户的份额（从 accounts.shares 字段读取）
    from data.account_service import get_account_shares
    
    for acc in accounts_db:
        account_code = acc.get('account_code') or acc.get('account_id', '')
        account_name = acc.get('account_name', '')
        account_type = acc.get('account_type', '')
        product_id = acc.get('product_id')
        balance = Decimal(str(acc.get('balance', 0) or 0))
        shares = Decimal(str(acc.get('shares', 0) or 0))  # 读取份额字段
        
        # 查找linked_product和group
        linked_product = ''
        group = ''
        if product_id:
            product = get_product_by_id(product_id)
            if product:
                linked_product = product.get('code', '')
            
            sql = """
                SELECT group_code
                FROM account_groups
                WHERE linked_product_id = %s
                LIMIT 1
            """
            group_rows = execute_query(sql, (product_id,))
            if group_rows:
                group = group_rows[0].get('group_code', '')
        
        # 判断账户类型（兼容旧逻辑）
        if account_type == 'FUND_TOTAL':
            account_type_display = 'fund_total'
        elif account_type == 'PRODUCT_SUB':
            account_type_display = 'product_sub'
        elif account_type == 'FUND_MAPPED':
            account_type_display = 'fund_mapped'
        else:
            account_type_display = 'cash'
        
        # 计算product_value（对于product_sub，需要从daily数据获取产品市值）
        product_value = balance
        if account_type_display == 'product_sub' and linked_product and linked_product in daily_map:
            # 稳利宝子账户：如果有份额，使用份额×净值计算市值；否则使用余额
            product_info = daily_map[linked_product]
            current_nav = Decimal(str(product_info.get('nav', 1) or 1))
            
            if shares > 0:
                # 使用份额×净值计算市值
                product_value = shares * current_nav
            else:
                # 没有份额记录，使用余额
                product_value = balance
            
            # 计算该组所有账户的余额总和
            if group:
                if group not in group_totals:
                    group_totals[group] = {'balance': Decimal('0'), 'accounts': []}
                group_totals[group]['balance'] += balance
                group_totals[group]['accounts'].append(account_code)
        elif account_type_display == 'fund_mapped':
            # 基金映射账户（如小荷包）：直接使用账户的 balance 字段
            # 不使用 daily_snapshot 中的 total_value，因为可能不准确
            product_value = balance
        
        balance_data.append({
            'account_id': account_code,
            'account_name': account_name,
            'account_type': account_type_display,
            'balance': str(balance),
            'shares': str(shares),  # 添加份额字段
            'product_value': str(product_value),
            'diff': '',
            'related_product': linked_product,
            'note': acc.get('note', ''),
            'group': group
        })
    
    # 处理汇总行（稳利宝合计等）
    summary_rows = []
    for group_code, group_info in group_totals.items():
        group_config = account_groups.get(group_code, {})
        group_name = group_config.get('name', group_code)
        linked_product_code = group_config.get('linked_product', '')
        
        # 计算该组的产品市值
        group_product_value = Decimal('0')
        if linked_product_code and linked_product_code in daily_map:
            product_info = daily_map[linked_product_code]
            group_product_value = Decimal(str(product_info.get('total_value', 0) or 0))
        
        group_balance = group_info['balance']
        group_profit = group_product_value - group_balance
        
        # 计算该组的总份额（从组内账户累加）
        group_total_shares = Decimal('0')
        for record in balance_data:
            if record.get('group') == group_code and record.get('account_type') == 'product_sub':
                try:
                    group_total_shares += Decimal(str(record.get('shares', 0) or 0))
                except:
                    pass
        
        # 更新profit_account的product_value和diff
        profit_account_code = group_config.get('profit_account')
        if profit_account_code:
            for record in balance_data:
                if record['account_id'] == profit_account_code and record.get('group') == group_code:
                    record['product_value'] = str(Decimal(record['balance']) + group_profit)
                    record['diff'] = str(group_profit)
                    break
        
        # 添加汇总行
        summary_rows.append({
            'account_id': f'{group_code}_total',
            'account_name': f'{group_name}(合计)',
            'account_type': 'summary',
            'balance': str(group_balance),
            'shares': str(group_total_shares),  # 汇总行显示组内份额总和
            'product_value': str(group_product_value),
            'diff': str(group_profit),
            'related_product': linked_product_code,
            'note': f'收益={group_profit:.2f}',
            'group': group_code
        })
    
    # 添加汇总行到balance_data
    balance_data.extend(summary_rows)
    
    # 添加余利宝合计
    ylb_accounts = [r for r in balance_data if r['account_id'] in ['ylb_life', 'ylb_finance']]
    if ylb_accounts:
        ylb_total = sum(Decimal(r['balance']) for r in ylb_accounts)
        balance_data.append({
            'account_id': 'ylb_total',
            'account_name': '余利宝(合计)',
            'account_type': 'summary',
            'balance': str(ylb_total),
            'product_value': str(ylb_total),
            'diff': '',
            'related_product': '',
            'note': '余利宝生活费+理财金合计',
            'group': 'ylb'
        })
    
    if balance_data:
        # 筛选
        if group_filter != "全部":
            keyword = group_filter.split("(")[1].rstrip(")")
            balance_data = [r for r in balance_data if keyword in r.get('account_id', '').lower()]
        
        # 处理基金账户：将 fund_total 改为场外基金账户，并增加场内基金账户和合计账户
        from data.product_service import get_products
        
        all_products = get_products(is_active=True)
        product_channel_map = {p.get('code', ''): p.get('channel', 'OTC') for p in all_products}
        
        # daily_data已经在上面读取过了，这里不需要重复读取
        exchange_fund_value = Decimal('0')
        exchange_fund_pnl_day = Decimal('0')
        exchange_fund_unrealized_pnl = Decimal('0')
        exchange_fund_total_pnl = Decimal('0')
        
        otc_fund_value = Decimal('0')
        otc_fund_pnl_day = Decimal('0')
        otc_fund_unrealized_pnl = Decimal('0')
        otc_fund_total_pnl = Decimal('0')
        
        # 计算场内和场外基金市值和收益
        for record in daily_data:
            product_code = record.get('product_code', '')
            channel = product_channel_map.get(product_code, 'OTC')
            if record.get('category') == 'fund':
                value = Decimal(str(record.get('value', 0) or 0))
                pnl_day = Decimal(str(record.get('pnl_day', 0) or 0))
                unrealized_pnl = Decimal(str(record.get('unrealized_pnl', 0) or 0))
                total_pnl = Decimal(str(record.get('total_pnl', 0) or 0))
                
                if channel == 'EXCHANGE':
                    exchange_fund_value += value
                    exchange_fund_pnl_day += pnl_day
                    exchange_fund_unrealized_pnl += unrealized_pnl
                    exchange_fund_total_pnl += total_pnl
                else:
                    otc_fund_value += value
                    otc_fund_pnl_day += pnl_day
                    otc_fund_unrealized_pnl += unrealized_pnl
                    otc_fund_total_pnl += total_pnl
        
        # 修改账户名称并添加新账户
        processed_balance_data = []
        fund_total_account_added = False  # 标记是否已添加基金(合计)账户
        fund_accounts = []  # 存储基金账户，稍后统一插入
        
        for record in balance_data:
            account_type = record.get('account_type', '')
            account_name = record.get('account_name', '')
            account_id = record.get('account_id', '')
            
            # 跳过已存在的基金(合计)账户（避免重复）
            if account_id == 'fund_total_account' or account_name == '基金(合计)账户' or account_name == '基金(合计)':
                continue
            
            # 将 fund_total 账户名称改为"场外基金账户"，并暂存到 fund_accounts
            if account_type == 'fund_total':
                record['account_name'] = '场外基金账户'
                # 更新为场外基金的值
                record['balance'] = str(otc_fund_value)
                if 'shares' not in record:
                    record['shares'] = '0'  # 汇总账户没有份额
                record['product_value'] = str(otc_fund_value)
                record['yesterday_pnl'] = f"{otc_fund_pnl_day:.2f}" if otc_fund_pnl_day != 0 else ''
                record['unrealized_pnl'] = f"{otc_fund_unrealized_pnl:.2f}" if otc_fund_unrealized_pnl != 0 else ''
                record['total_pnl'] = f"{otc_fund_total_pnl:.2f}" if otc_fund_total_pnl != 0 else ''
                fund_accounts.append(record)  # 暂存到场外基金账户
            else:
                processed_balance_data.append(record)
        
        # 添加场外基金账户
        if fund_accounts:
            processed_balance_data.extend(fund_accounts)
        
        # 添加场内基金账户（放在场外基金账户后面）
        processed_balance_data.append({
            'account_id': 'exchange_fund_account',
            'account_name': '场内基金账户',
            'account_type': 'fund_total',
            'balance': str(exchange_fund_value),
            'shares': '0',  # 汇总账户没有份额
            'product_value': str(exchange_fund_value),
            'diff': '',
            'yesterday_pnl': f"{exchange_fund_pnl_day:.2f}" if exchange_fund_pnl_day != 0 else '',
            'unrealized_pnl': f"{exchange_fund_unrealized_pnl:.2f}" if exchange_fund_unrealized_pnl != 0 else '',
            'total_pnl': f"{exchange_fund_total_pnl:.2f}" if exchange_fund_total_pnl != 0 else '',
            'related_product': None,
            'note': '场内基金账户汇总'
        })
        
        # 添加基金(合计)账户（放在场外和场内基金账户后面）
        if not fund_total_account_added:
            total_fund_value = exchange_fund_value + otc_fund_value
            total_fund_pnl_day = exchange_fund_pnl_day + otc_fund_pnl_day
            total_fund_unrealized_pnl = exchange_fund_unrealized_pnl + otc_fund_unrealized_pnl
            total_fund_total_pnl = exchange_fund_total_pnl + otc_fund_total_pnl
            
            processed_balance_data.append({
                'account_id': 'fund_total_account',
                'account_name': '基金(合计)账户',
                'account_type': 'summary',
                'balance': str(total_fund_value),
                'shares': '0',  # 汇总账户没有份额
                'product_value': str(total_fund_value),
                'diff': '',
                'yesterday_pnl': f"{total_fund_pnl_day:.2f}" if total_fund_pnl_day != 0 else '',
                'unrealized_pnl': f"{total_fund_unrealized_pnl:.2f}" if total_fund_unrealized_pnl != 0 else '',
                'total_pnl': f"{total_fund_total_pnl:.2f}" if total_fund_total_pnl != 0 else '',
                'related_product': None,
                'note': '场内+场外基金账户合计'
            })
            fund_total_account_added = True
        
        # 添加收益字段（从daily数据计算）
        # 重新构建daily_map（因为daily_data可能已经更新）
        daily_map = {r.get('product_code'): r for r in daily_data}
        
        for record in processed_balance_data:
            account_id = record.get('account_id', '')
            account_type = record.get('account_type', '')
            related_product = record.get('related_product', '')
            
            yesterday_pnl = Decimal('0')
            unrealized_pnl = Decimal('0')
            total_pnl = Decimal('0')
            
            # 产品子账户（稳利宝）：从关联产品获取收益
            if account_type == 'product_sub' and related_product and related_product in daily_map:
                product_info = daily_map[related_product]
                yesterday_pnl = Decimal(str(product_info.get('pnl_day', 0) or 0))
                unrealized_pnl = Decimal(str(product_info.get('unrealized_pnl', 0) or 0))
                total_pnl = Decimal(str(product_info.get('total_pnl', 0) or 0))
            # 基金映射账户（小荷包）：从关联产品获取收益
            elif account_type == 'fund_mapped' and related_product and related_product in daily_map:
                product_info = daily_map[related_product]
                yesterday_pnl = Decimal(str(product_info.get('pnl_day', 0) or 0))
                unrealized_pnl = Decimal(str(product_info.get('unrealized_pnl', 0) or 0))
                total_pnl = Decimal(str(product_info.get('total_pnl', 0) or 0))
            
            record['yesterday_pnl'] = f"{yesterday_pnl:.2f}" if yesterday_pnl != 0 else ''
            record['unrealized_pnl'] = f"{unrealized_pnl:.2f}" if unrealized_pnl != 0 else ''
            record['total_pnl'] = f"{total_pnl:.2f}" if total_pnl != 0 else ''
        
        # 转换为 DataFrame
        df_balance = pd.DataFrame(processed_balance_data)
        
        # 选择显示的列（去掉余额和差异列，只保留市值和份额）
        display_cols = ['account_name', 'account_type', 'shares', 'product_value', 
                       'yesterday_pnl', 'unrealized_pnl', 'total_pnl']
        display_cols = [c for c in display_cols if c in df_balance.columns]
        
        # 重命名列
        col_names = {
            'account_name': '账户名称',
            'account_type': '类型',
            'shares': '份额',
            'product_value': '市值',
            'yesterday_pnl': '昨日收益',
            'unrealized_pnl': '持有收益',
            'total_pnl': '累计收益'
        }
        
        # 格式化份额列（product_sub 和 summary 类型显示数值，其他类型显示为空）
        if 'shares' in df_balance.columns:
            for idx, row in df_balance.iterrows():
                account_type = row.get('account_type', '')
                shares_val = row.get('shares', '0')
                # product_sub 和 summary（如稳利宝合计）显示份额
                if account_type in ('product_sub', 'summary'):
                    try:
                        shares_decimal = Decimal(str(shares_val))
                        if shares_decimal > 0:
                            # 总是显示实际数值（保留2位小数）
                            df_balance.at[idx, 'shares'] = f"{shares_decimal:.2f}"
                        else:
                            df_balance.at[idx, 'shares'] = '-'
                    except:
                        df_balance.at[idx, 'shares'] = '-'
                else:
                    df_balance.at[idx, 'shares'] = '-'
        
        # 格式化市值列（保留2位小数）
        if 'product_value' in df_balance.columns:
            for idx, row in df_balance.iterrows():
                pv_val = row.get('product_value', '0')
                try:
                    pv_decimal = Decimal(str(pv_val))
                    df_balance.at[idx, 'product_value'] = f"{pv_decimal:.2f}"
                except:
                    df_balance.at[idx, 'product_value'] = '0.00'
        
        df_display = df_balance[display_cols].copy()
        df_display = df_display.rename(columns=col_names)
        
        st.dataframe(df_display, width='stretch', hide_index=True)
    else:
        st.info("暂无账户余额数据，请点击侧边栏「🔄」按钮生成快照")
    
    st.divider()
    
    # 产品持仓表格
    st.subheader("📊 产品持仓")
    
    # 场内场外选择（默认场内）
    holdings_channel = st.radio(
        "交易类型",
        ["场内", "场外"],
        index=0,  # 默认场内
        key="holdings_channel",
        horizontal=True
    )
    
    daily_data = read_latest_daily()
    
    if daily_data:
        # 根据选择筛选产品
        from data.product_service import get_products
        all_products = get_products(is_active=True)

        # 一个产品代码可能同时存在场内和场外两个版本（如 163406），
        # 因此这里为每个 code 记录「有哪些 channel」，以及对应的 product_id
        # 场内视图：只显示场内产品的持仓
        # 场外视图：只显示场外产品的持仓
        product_channels_map: Dict[str, set] = {}
        # 记录 (code, channel) -> product_id 的映射
        code_channel_to_product_id: Dict[tuple, int] = {}
        for p in all_products:
            code = p.get("code", "")
            ch = p.get("channel", "OTC")
            pid = p.get("id")
            if not code:
                continue
            if code not in product_channels_map:
                product_channels_map[code] = set()
            product_channels_map[code].add(ch)
            code_channel_to_product_id[(code, ch)] = pid
        
        # 找出同时有场内和场外版本的产品代码
        dual_channel_codes = {code for code, channels in product_channels_map.items() if len(channels) > 1}

        if holdings_channel == "场内":
            # 场内视图：
            # 1. 对于只有场内版本的产品，使用 daily_snapshot 数据
            # 2. 对于同时有场内和场外版本的产品，使用场内实际持仓数据
            filtered_data = []
            for r in daily_data:
                code = r.get("product_code", "")
                channels = product_channels_map.get(code, set())
                if "EXCHANGE" not in channels:
                    continue  # 该产品没有场内版本，跳过
                
                # 所有场内产品都使用实时计算的持仓数据（不依赖daily_snapshot缓存）
                exchange_product_id = code_channel_to_product_id.get((code, "EXCHANGE"))
                if exchange_product_id:
                    from core.exchange_holdings_calculator import calculate_exchange_holdings
                    from core.market_quote_service import get_latest_quote
                    try:
                        holdings = calculate_exchange_holdings(exchange_product_id)
                        quote = get_latest_quote(exchange_product_id)
                        price = Decimal(str(quote.get('price', 0))) if quote else Decimal('0')
                        shares = Decimal(str(holdings.get('current_qty', 0))) if holdings else Decimal('0')
                        value = shares * price
                        r_copy = dict(r)
                        r_copy['product_id'] = exchange_product_id  # 添加 product_id，用于区分场内场外
                        r_copy['shares'] = str(shares)
                        r_copy['value'] = str(value)
                        r_copy['nav'] = str(price)
                        r_copy['total_pnl'] = str(holdings.get('total_pnl', 0)) if holdings else '0'
                        # 场内产品使用 total_cost 作为成本（用于计算收益率）
                        r_copy['total_cost'] = str(holdings.get('total_cost', 0)) if holdings else '0'
                        r_copy['avg_cost'] = str(holdings.get('avg_cost', 0)) if holdings else '0'
                        # 清空可能来自场外的数据
                        r_copy['principal_total'] = '0'  # 场内产品不使用 principal_total
                        r_copy['cost'] = str(holdings.get('total_cost', 0)) if holdings else '0'  # 使用 total_cost 作为 cost
                        filtered_data.append(r_copy)
                    except Exception as e:
                        logger.warning(f"计算场内持仓失败: {code}, error={e}")
                        # 计算失败时仍显示，但份额为0
                        r_copy = dict(r)
                        r_copy['product_id'] = exchange_product_id
                        r_copy['shares'] = '0'
                        r_copy['value'] = '0'
                        r_copy['total_pnl'] = '0'
                        r_copy['total_cost'] = '0'
                        r_copy['principal_total'] = '0'
                        r_copy['cost'] = '0'
                        filtered_data.append(r_copy)
            daily_data = filtered_data
        else:
            # 场外视图：
            # 1. 对于只有场外版本的产品，使用 daily_snapshot 数据
            # 2. 对于同时有场内和场外版本的产品，使用场外实际持仓数据
            filtered_data = []
            for r in daily_data:
                code = r.get("product_code", "")
                channels = product_channels_map.get(code, set())
                if "OTC" not in channels:
                    continue  # 该产品没有场外版本，跳过
                
                if code in dual_channel_codes:
                    # 同时有场内和场外版本，使用场外实际持仓数据
                    from core.holdings_calculator import HoldingsCalculator
                    from data.nav_reader import get_latest_nav
                    try:
                        calc = HoldingsCalculator()
                        all_holdings = calc.get_all_holdings_data_as_of(date.today().strftime('%Y-%m-%d'))
                        nav_data = get_latest_nav(code)
                        nav = Decimal(str(nav_data[1])) if nav_data else Decimal('0')
                        
                        if code in all_holdings:
                            h = all_holdings[code]
                            shares = Decimal(str(h.get('shares', 0)))
                            value = shares * nav
                            r_copy = dict(r)
                            r_copy['shares'] = str(shares)
                            r_copy['value'] = str(value)
                            r_copy['nav'] = str(nav)
                            r_copy['cost'] = str(h.get('cost', 0))
                            cost = Decimal(str(h.get('cost', 0)))
                            unrealized_pnl = value - cost if cost > 0 else Decimal('0')
                            r_copy['unrealized_pnl'] = str(unrealized_pnl)
                            filtered_data.append(r_copy)
                        else:
                            # 没有持仓数据，显示份额为0
                            r_copy = dict(r)
                            r_copy['shares'] = '0'
                            r_copy['value'] = '0'
                            r_copy['nav'] = str(nav)
                            filtered_data.append(r_copy)
                    except Exception as e:
                        logger.warning(f"计算场外持仓失败: {code}, error={e}")
                        r_copy = dict(r)
                        r_copy['shares'] = '0'
                        r_copy['value'] = '0'
                        filtered_data.append(r_copy)
                else:
                    # 只有场外版本，直接使用 daily_snapshot 数据
                    filtered_data.append(r)
            daily_data = filtered_data
    
    if daily_data:
        df_daily = pd.DataFrame(daily_data)
        
        # 统一数值精度：份额、市值、盈亏都按两位小数计算并展示
        def _round_2(value: Any) -> Decimal:
            try:
                return Decimal(str(value or "0")).quantize(Decimal("0.00"), rounding=ROUND_HALF_UP)
            except Exception:
                return Decimal("0.00")
        
        def _recalc_row(row: pd.Series) -> pd.Series:
            # 始终使用原始精度的 nav 和 shares 重新计算市值，确保精度准确
            # 数据库中的 value 字段可能因为存储时的四舍五入导致误差
            try:
                nav_raw = row.get("nav")
                shares_raw = row.get("shares")
                
                if nav_raw and shares_raw:
                    # 转换为 Decimal，保持数据库中的完整精度（nav: 6位小数，shares: 6位小数）
                    nav = Decimal(str(nav_raw))
                    shares = Decimal(str(shares_raw))
                    
                    # 使用完整精度计算市值，然后四舍五入到两位小数
                    # 这样可以避免使用数据库中可能已经四舍五入的 value 字段
                    # 注意：使用 ROUND_HALF_UP 确保 0.5 向上舍入
                    value = (nav * shares).quantize(Decimal("0.00"), rounding=ROUND_HALF_UP)
                    
                    # 调试：如果计算出的 value 与数据库中的 value 不一致，记录日志
                    db_value = row.get("value")
                    if db_value:
                        db_value_dec = Decimal(str(db_value))
                        if abs(value - db_value_dec) > Decimal("0.01"):
                            # 差异超过 0.01，可能是精度问题
                            pass  # 可以在这里添加日志
                else:
                    # 如果 nav 或 shares 不存在，尝试使用数据库中的 value 字段
                    value_raw = row.get("value")
                    if value_raw and str(value_raw).strip():
                        value = Decimal(str(value_raw)).quantize(Decimal("0.00"), rounding=ROUND_HALF_UP)
                    else:
                        value = Decimal("0.00")
            except Exception as e:
                # 如果计算失败，尝试使用数据库中的 value 字段
                try:
                    value_raw = row.get("value")
                    if value_raw and str(value_raw).strip():
                        value = Decimal(str(value_raw)).quantize(Decimal("0.00"), rounding=ROUND_HALF_UP)
                    else:
                        value = Decimal("0.00")
                except:
                    value = Decimal("0.00")
            
            # 格式化展示
            # 份额保留2位小数，净值保留6位小数，市值保留2位小数，盈亏保留2位小数，收益率转为百分比
            from src.utils.decimal_utils import format_shares, format_money, format_nav
            
            shares_display = format_shares(row.get("shares"), places=2)
            nav_display = format_nav(row.get("nav"), places=6)
            total_pnl = format_money(row.get("total_pnl"), places=2)
            
            # 收益率转为百分比
            # 注意：场内产品和场外产品的收益率计算方式不同
            # 场内产品：使用 total_pnl / total_cost（基于 trade_fills）
            # 场外产品：使用 total_pnl / principal_total（基于 transactions）
            shares = Decimal(str(row.get("shares", 0) or 0))
            total_pnl = Decimal(str(row.get("total_pnl", 0) or 0))
            
            # 如果份额为0，收益率直接为0
            if shares == 0:
                row["real_return"] = "0.00%"
            else:
                # 判断是场内还是场外产品（通过 product_id 或 total_cost 字段）
                total_cost = Decimal(str(row.get("total_cost", 0) or 0))
                principal_total = Decimal(str(row.get("principal_total", 0) or 0))
                cost = Decimal(str(row.get("cost", 0) or 0))
                
                # 优先使用 total_cost（场内产品）
                if total_cost > 0:
                    # 场内产品：使用 total_pnl / total_cost
                    return_val = float((total_pnl / total_cost * 100).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP))
                    row["real_return"] = f"{return_val:.2f}%"
                elif principal_total > 0:
                    # 场外产品：使用 total_pnl / principal_total
                    return_val = float((total_pnl / principal_total * 100).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP))
                    row["real_return"] = f"{return_val:.2f}%"
                elif cost > 0:
                    # 备选：使用 cost（持仓成本）计算浮动盈亏收益率
                    unrealized_pnl = Decimal(str(row.get("unrealized_pnl", 0) or 0))
                    if unrealized_pnl != 0:
                        return_val = float((unrealized_pnl / cost * 100).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP))
                        row["real_return"] = f"{return_val:.2f}%"
                    else:
                        row["real_return"] = "0.00%"
                else:
                    # 无法计算收益率
                    row["real_return"] = "0.00%"
            
            row["shares"] = shares_display
            row["nav"] = nav_display
            row["value"] = format_money(value, places=2)
            row["total_pnl"] = total_pnl
            return row
        
        df_daily = df_daily.apply(_recalc_row, axis=1)
        
        # 在产品名称中添加产品代码（小字显示）
        # 由于 st.dataframe 不支持 HTML，我们直接在名称后面添加代码
        if 'product_code' in df_daily.columns and 'product_name' in df_daily.columns:
            df_daily['product_name'] = df_daily.apply(
                lambda row: f"{row.get('product_name', '')} ({row.get('product_code', '')})",
                axis=1
            )
        
        # 选择显示的列（增加净值日期）
        display_cols = ['product_name', 'nav_date', 'nav', 'shares', 'value', 'total_pnl', 'real_return']
        display_cols = [c for c in display_cols if c in df_daily.columns]
        
        col_names = {
            'product_name': '产品名称',
            'nav_date': '净值日期',
            'nav': '净值',
            'shares': '份额',
            'value': '市值',
            'total_pnl': '总盈亏',
            'real_return': '收益率'
        }
        
        df_display = df_daily[display_cols].copy()
        df_display = df_display.rename(columns=col_names)
        
        # 使用 st.dataframe 显示，但产品名称列需要特殊处理（HTML格式）
        # 由于 st.dataframe 不支持 HTML，我们需要使用 st.markdown 或者修改显示方式
        # 这里先使用 st.dataframe，产品代码会在名称后面显示
        st.dataframe(df_display, width='stretch', hide_index=True)
    else:
        st.info("暂无产品持仓数据")


# ============================================================
# Page 2: 生活记账
# ============================================================
def page_ledger():
    st.markdown('<p class="main-header">📝 生活记账</p>', unsafe_allow_html=True)
    
    # 录入区
    tab1, tab2, tab3, tab4 = st.tabs(["💸 支出", "💰 收入", "🔄 转账", "↩️ 退款"])
    
    # 获取选项
    account_options = get_account_options()
    account_dict = {acc['name']: acc['id'] for acc in account_options}
    account_names = list(account_dict.keys())
    
    with tab1:
        st.subheader("新增支出")
        
        col1, col2 = st.columns(2)
        
        with col1:
            expense_account = st.selectbox("支出账户", account_names, key="expense_account")
            expense_amount = st.number_input("金额", min_value=0.01, step=0.01, key="expense_amount")
            
            # 分类选择
            expense_categories = get_category_options('expense')
            cat_l1_options = list(expense_categories.keys())
            expense_cat_l1 = st.selectbox("一级分类", cat_l1_options, key="expense_cat_l1")
            
            cat_l2_options = expense_categories.get(expense_cat_l1, [])
            expense_cat_l2 = st.selectbox("二级分类", [""] + cat_l2_options, key="expense_cat_l2") if cat_l2_options else ""
        
        with col2:
            expense_time = st.date_input("日期", value=date.today(), key="expense_date")
            expense_time_str = st.text_input("时间 (HH:MM:SS)", value=datetime.now().strftime("%H:%M:%S"), key="expense_time")
            expense_note = st.text_input("备注", key="expense_note")
            expense_discount = st.number_input("优惠金额", min_value=0.0, step=0.01, value=0.0, key="expense_discount")
            expense_reimbursable = st.checkbox("可报销", key="expense_reimbursable")
        
        if st.button("提交支出", type="primary", key="submit_expense"):
            try:
                event_time = f"{expense_time} {expense_time_str}"
                account_id = account_dict[expense_account]
                amount = Decimal(str(expense_amount))
                
                result = add_expense(
                    account_from=account_id,
                    amount=amount,
                    category_l1=expense_cat_l1,
                    category_l2=expense_cat_l2 or '',
                    event_time=event_time,
                    note=expense_note,
                    discount=Decimal(str(expense_discount)),
                    reimbursable=expense_reimbursable
                )
                
                # 如果是 fund_mapped 账户，同步到对应产品
                sync_fund_mapped_transaction(
                    account_id=account_id,
                    amount=amount,
                    is_expense=True,
                    event_time=event_time,
                    note=expense_note
                )
                
                # 显示余额信息
                balance_msg = f"💰 {expense_account} 余额: {result.get('balance_after', '-')}"
                if result.get('parent_balance_after'):
                    balance_msg += f" | 父账户余额: {result.get('parent_balance_after')}"
                st.success(f"✅ 支出记录已保存！\n{balance_msg}")
                # 账户余额已实时更新，无需生成快照
            except Exception as e:
                st.error(f"❌ 保存失败: {e}")
    
    with tab2:
        st.subheader("新增收入")
        
        col1, col2 = st.columns(2)
        
        with col1:
            income_account = st.selectbox("收入账户", account_names, key="income_account")
            income_amount = st.number_input("金额", min_value=0.01, step=0.01, key="income_amount")
            
            income_categories = get_category_options('income')
            income_cat_l1_options = list(income_categories.keys())
            income_cat_l1 = st.selectbox("一级分类", income_cat_l1_options, key="income_cat_l1")
            
            income_cat_l2_options = income_categories.get(income_cat_l1, [])
            income_cat_l2 = st.selectbox("二级分类", [""] + income_cat_l2_options, key="income_cat_l2") if income_cat_l2_options else ""
        
        with col2:
            income_date = st.date_input("日期", value=date.today(), key="income_date")
            income_time = st.text_input("时间 (HH:MM:SS)", value=datetime.now().strftime("%H:%M:%S"), key="income_time")
            income_note = st.text_input("备注", key="income_note")
        
        if st.button("提交收入", type="primary", key="submit_income"):
            try:
                event_time = f"{income_date} {income_time}"
                account_id = account_dict[income_account]
                amount = Decimal(str(income_amount))
                
                result = add_income(
                    account_to=account_id,
                    amount=amount,
                    category_l1=income_cat_l1,
                    category_l2=income_cat_l2 or '',
                    event_time=event_time,
                    note=income_note
                )
                
                # 如果是 fund_mapped 账户，同步到对应产品
                # 对于利息收益，同步为 dividend；其他收入同步为 buy
                # 检查是否需要跳过同步（防止循环：补录历史时已手动创建交易）
                skip_sync = st.session_state.get('_skip_ledger_tx_sync', False)
                if account_id in FUND_MAPPED_ACCOUNTS and not skip_sync:
                    from data.data_store import append_transaction
                    product_code = FUND_MAPPED_ACCOUNTS[account_id]
                    
                    # 利息收益 -> dividend，其他 -> buy
                    is_dividend = (income_cat_l1 == '理财盈利' and income_cat_l2 == '利息收益')
                    
                    tx_record = {
                        'date': str(income_date),
                        'product_code': product_code,
                        'action': 'dividend' if is_dividend else 'buy',
                        'amount': str(amount) if not is_dividend else '',
                        'shares': str(amount),  # 货币基金：金额 = 份额
                        'fee': '0',
                        'nav': '1',
                        'nav_date': str(income_date),
                        'order_id': '',
                        'note': f"同步自记账: {income_note}" if income_note else "同步自记账",
                        'created_at': event_time
                    }
                    append_transaction(tx_record)
                
                # 显示余额信息
                balance_msg = f"💰 {income_account} 余额: {result.get('balance_after', '-')}"
                if result.get('parent_balance_after'):
                    balance_msg += f" | 父账户余额: {result.get('parent_balance_after')}"
                st.success(f"✅ 收入记录已保存！\n{balance_msg}")
                # 账户余额已实时更新，无需生成快照
            except Exception as e:
                st.error(f"❌ 保存失败: {e}")
    
    with tab3:
        st.subheader("新增转账")
        
        col1, col2 = st.columns(2)
        
        with col1:
            transfer_from = st.selectbox("转出账户", account_names, key="transfer_from")
            transfer_to = st.selectbox("转入账户", account_names, key="transfer_to")
            transfer_amount = st.number_input("金额", min_value=0.01, step=0.01, key="transfer_amount")
            # 显示转出/转入账户当前余额
            from_balance = get_account_balance(account_dict[transfer_from])
            to_balance = get_account_balance(account_dict[transfer_to])
            st.caption(f"转出账户当前余额：¥{format_decimal(from_balance)} | 转入账户当前余额：¥{format_decimal(to_balance)}")
        
        with col2:
            transfer_date = st.date_input("日期", value=date.today(), key="transfer_date")
            transfer_time = st.text_input("时间 (HH:MM:SS)", value=datetime.now().strftime("%H:%M:%S"), key="transfer_time")
            transfer_note = st.text_input("备注", key="transfer_note")
        
        if st.button("提交转账", type="primary", key="submit_transfer"):
            if transfer_from == transfer_to:
                st.error("❌ 转出和转入账户不能相同！")
            else:
                try:
                    event_time = f"{transfer_date} {transfer_time}"
                    result = add_transfer(
                        account_from=account_dict[transfer_from],
                        account_to=account_dict[transfer_to],
                        amount=Decimal(str(transfer_amount)),
                        event_time=event_time,
                        note=transfer_note
                    )
                    # 显示余额信息（转出账户）
                    balance_msg = f"💰 {transfer_from} 余额: {result.get('balance_after', '-')}"
                    if result.get('parent_balance_after'):
                        balance_msg += f" | 父账户余额: {result.get('parent_balance_after')}"
                    st.success(f"✅ 转账记录已保存！\n{balance_msg}")
                    # 账户余额已实时更新，无需生成快照
                except Exception as e:
                    st.error(f"❌ 保存失败: {e}")
    
    with tab4:
        st.subheader("退款")
        
        # 获取最近支出
        expenses = list_expenses(20)
        
        if not expenses:
            st.info("暂无支出记录可退款")
        else:
            # 构建选项
            expense_options = []
            for i, e in enumerate(expenses):
                note = e.get('note') or ''
                label = f"{(e.get('event_time') or '')[:16]} | ¥{e.get('amount', '')} | {e.get('category_l1', '')} | {note[:20]}"
                expense_options.append((label, i, e))
            
            selected_idx = st.selectbox(
                "选择要退款的支出",
                range(len(expense_options)),
                format_func=lambda x: expense_options[x][0],
                key="refund_select"
            )
            
            if selected_idx is not None:
                original = expense_options[selected_idx][2]
                orig_amount = Decimal(original.get('amount', '0'))
                orig_account = original.get('account_from', '')
                
                col1, col2 = st.columns(2)
                
                with col1:
                    refund_amount = st.number_input(
                        "退款金额",
                        min_value=0.01,
                        max_value=float(orig_amount) * 1.5,
                        value=float(orig_amount),
                        step=0.01,
                        key="refund_amount"
                    )
                    
                    # 退款账户
                    orig_account_name = next((acc['name'] for acc in account_options if acc['id'] == orig_account), orig_account)
                    default_idx = account_names.index(orig_account_name) if orig_account_name in account_names else 0
                    refund_account = st.selectbox("退款账户", account_names, index=default_idx, key="refund_account")
                
                with col2:
                    refund_date = st.date_input("退款日期", value=date.today(), key="refund_date")
                    refund_time = st.text_input("时间 (HH:MM:SS)", value=datetime.now().strftime("%H:%M:%S"), key="refund_time")
                    refund_note = st.text_input("备注", value=f"退款: {original.get('note', '')}", key="refund_note")
                
                if st.button("提交退款", type="primary", key="submit_refund"):
                    try:
                        event_time = f"{refund_date} {refund_time}"
                        result = add_refund(
                            original_expense=original,
                            refund_amount=Decimal(str(refund_amount)),
                            refund_account=account_dict[refund_account],
                            event_time=event_time,
                            note=refund_note
                        )
                        # 显示余额信息
                        balance_msg = f"💰 {refund_account} 余额: {result.get('balance_after', '-')}"
                        if result.get('parent_balance_after'):
                            balance_msg += f" | 父账户余额: {result.get('parent_balance_after')}"
                        st.success(f"✅ 退款记录已保存！\n{balance_msg}")
                        # 账户余额已实时更新，无需生成快照
                    except Exception as e:
                        st.error(f"❌ 保存失败: {e}")
    
    st.divider()
    
    # 最近记账记录
    st.subheader("📋 最近记账记录")
    st.caption('💡 点击表格中的任意行可编辑 | 「余额」列为动态计算，可用于对账')
    
    # 过滤器
    filter_col1, filter_col2 = st.columns([1, 3])
    with filter_col1:
        ledger_group_filter = st.selectbox(
            "筛选账户组",
            list(ACCOUNT_GROUP_FILTERS.keys()),
            key="ledger_account_filter"
        )
    
    recent = list_recent_ledger(200, with_balances=True)  # 加载更多记录用于分页
    recent = filter_records_by_account_group(recent, ledger_group_filter)
    
    if recent:
        # 处理数据
        rows = []
        raw_records = []
        for r in recent:
            entry_type = r.get('entry_type', '')
            is_expense = entry_type in ['expense', 'transfer']
            amount = r.get('amount', '0')
            account = merge_account_column(r)
            
            raw_records.append(r)
            rows.append({
                'ID': r.get('id'),
                '时间': r.get('event_time', ''),
                '金额': format_colored_amount(amount, is_expense),
                '分类': f"{r.get('category_l1', '')} > {r.get('category_l2', '')}" if r.get('category_l2') else r.get('category_l1', ''),
                '账户': get_account_name(account),
                '余额': r.get('balance_after', ''),
                '父账户余额': r.get('parent_balance_after', '') or '',
                '备注': r.get('note', '')
            })
        
        df = pd.DataFrame(rows)
        # 保存原始索引供分页后使用
        df['_original_idx'] = range(len(df))
        
        # 分页
        df_page = paginate_dataframe(df, "ledger_records", page_size=50)
        original_indices = df_page['_original_idx'].tolist()
        
        # 显示带颜色和行选择的表格（不显示原始索引列）
        display_cols = ['ID', '时间', '金额', '分类', '账户', '余额', '父账户余额', '备注']
        styled_df = df_page[display_cols].style.map(color_amount, subset=['金额'])
        event = st.dataframe(
            styled_df, 
            width='stretch', 
            hide_index=True,
            on_select="rerun",
            selection_mode="single-row",
            key="ledger_table"
        )
        
        # 检查是否选中了某行
        selected_rows = event.selection.rows if event.selection else []
        
        if selected_rows:
            page_idx = selected_rows[0]
            # 获取原始记录索引
            selected_idx = original_indices[page_idx] if page_idx < len(original_indices) else page_idx
            selected_record = raw_records[selected_idx]
            entry_type = selected_record.get('entry_type', '')
            
            st.markdown("### 📝 编辑记录")
            
            # 准备下拉框选项
            account_id_to_name = {acc['id']: acc['name'] for acc in account_options}
            account_name_to_id = {acc['name']: acc['id'] for acc in account_options}
            
            # 获取分类选项
            if entry_type == 'expense':
                categories = get_category_options('expense')
            else:
                categories = get_category_options('income')
            cat_l1_list = list(categories.keys())
            
            col1, col2 = st.columns(2)
            
            with col1:
                edit_time = st.text_input("时间", value=selected_record.get('event_time', ''), key="ledger_edit_time")
                edit_amount = st.number_input("金额", value=float(selected_record.get('amount', 0)), step=0.01, key="ledger_edit_amount")
                
                # 一级分类下拉框
                current_cat_l1 = selected_record.get('category_l1', '')
                cat_l1_idx = cat_l1_list.index(current_cat_l1) if current_cat_l1 in cat_l1_list else 0
                edit_cat_l1 = st.selectbox("一级分类", cat_l1_list, index=cat_l1_idx, key="ledger_edit_cat1")
                
                # 二级分类下拉框
                cat_l2_list = [""] + categories.get(edit_cat_l1, [])
                current_cat_l2 = selected_record.get('category_l2', '') or ''
                cat_l2_idx = cat_l2_list.index(current_cat_l2) if current_cat_l2 in cat_l2_list else 0
                edit_cat_l2 = st.selectbox("二级分类", cat_l2_list, index=cat_l2_idx, key="ledger_edit_cat2")
            
            with col2:
                # 账户下拉框
                if entry_type in ['expense', 'transfer']:
                    current_from = selected_record.get('account_from', '') or ''
                    current_from_name = account_id_to_name.get(current_from, current_from)
                    from_idx = account_names.index(current_from_name) if current_from_name in account_names else 0
                    edit_account_from_name = st.selectbox("支出账户", account_names, index=from_idx, key="ledger_edit_from")
                    edit_account_from = account_name_to_id.get(edit_account_from_name, edit_account_from_name)
                else:
                    edit_account_from = selected_record.get('account_from', '') or ''
                    st.text_input("支出账户", value=edit_account_from, disabled=True, key="ledger_edit_from")
                
                if entry_type in ['income', 'transfer']:
                    current_to = selected_record.get('account_to', '') or ''
                    current_to_name = account_id_to_name.get(current_to, current_to)
                    to_idx = account_names.index(current_to_name) if current_to_name in account_names else 0
                    edit_account_to_name = st.selectbox("收入账户", account_names, index=to_idx, key="ledger_edit_to")
                    edit_account_to = account_name_to_id.get(edit_account_to_name, edit_account_to_name)
                else:
                    edit_account_to = selected_record.get('account_to', '') or ''
                    st.text_input("收入账户", value=edit_account_to or '', disabled=True, key="ledger_edit_to")
                
                edit_note = st.text_input("备注", value=selected_record.get('note', ''), key="ledger_edit_note")
            
            col_save, col_delete = st.columns([3, 1])
            
            with col_save:
                if st.button("💾 保存修改", type="primary", key="save_ledger_edit", width='stretch'):
                    updated_record = {
                        'event_time': edit_time,
                        'entry_type': entry_type,
                        'amount': str(edit_amount),
                        'category_l1': edit_cat_l1,
                        'category_l2': edit_cat_l2 if edit_cat_l2 else '',
                        'account_from': edit_account_from,
                        'account_to': edit_account_to,
                        'note': edit_note
                    }
                    
                    # 检查是否是转账记录，如果是则同时更新配对记录
                    if edit_cat_l1 == '转账':
                        from core.cascade_delete import update_transfer_pair_ledger
                        result = update_transfer_pair_ledger(selected_record['id'], selected_record, updated_record)
                        if result['main_updated']:
                            msg = "✅ 保存成功！"
                            if result['pair_updated']:
                                msg += " 已同步更新转账配对记录"
                            if result['errors']:
                                msg += f"\n⚠️ {', '.join(result['errors'])}"
                            st.success(msg)
                            st.rerun()
                        else:
                            error_msg = "❌ 保存失败"
                            if result['errors']:
                                error_msg += f": {', '.join(result['errors'])}"
                            st.error(error_msg)
                    else:
                        # 非转账记录，直接更新
                        if update_ledger_entry(selected_record['id'], updated_record):
                            st.success("✅ 保存成功！")
                            st.rerun()
                        else:
                            st.error("❌ 保存失败")
            
            with col_delete:
                if st.button("🗑️ 删除", type="secondary", key="delete_ledger_edit", width='stretch'):
                    st.session_state['pending_delete_ledger_id'] = selected_record['id']
            
            # 删除确认
            if st.session_state.get('pending_delete_ledger_id') == selected_record['id']:
                st.warning(f"⚠️ 确定要删除这条记录吗？（{selected_record.get('event_time', '')} - {edit_cat_l1} - ¥{edit_amount}）")
                col_confirm, col_cancel = st.columns(2)
                with col_confirm:
                    if st.button("✅ 确认删除", type="primary", key="do_delete_ledger"):
                        from core.cascade_delete import cascade_delete_ledger
                        result = cascade_delete_ledger(selected_record['id'], selected_record)
                        if result['ledger_deleted']:
                            msg = "✅ 删除成功！"
                            if result.get('transfer_pair_deleted'):
                                msg += " 已同时删除转账配对记录"
                            if result['transactions_deleted'] > 0:
                                msg += f" 已删除 {result['transactions_deleted']} 条关联理财记录"
                            if result['order_deleted']:
                                msg += f"，已删除关联订单"
                            if result.get('shares_restored'):
                                msg += f"（已恢复 {len(result['shares_restored'])} 笔份额）"
                            if result['errors']:
                                msg += f"\n⚠️ 部分删除失败: {', '.join(result['errors'])}"
                            st.success(msg)
                            st.session_state.pop('pending_delete_ledger_id', None)
                            st.rerun()
                        else:
                            error_msg = "❌ 删除失败"
                            if result['errors']:
                                error_msg += f": {', '.join(result['errors'])}"
                            st.error(error_msg)
                with col_cancel:
                    if st.button("❌ 取消", key="cancel_delete_ledger"):
                        st.session_state.pop('pending_delete_ledger_id', None)
                        st.rerun()
    else:
        st.info("暂无记录")


# ============================================================
# Page 3: 理财录入
# ============================================================
def page_invest():
    st.markdown('<p class="main-header">📈 理财录入</p>', unsafe_allow_html=True)
    
    tab1, tab2, tab3, tab4 = st.tabs(["💳 买入/成交", "📤 赎回发起", "📝 补录历史", "🔁 转托管（场外→场内）"])
    
    # 获取产品选项（需要包含 channel 字段）
    from data.product_service import get_products
    all_products = get_products(is_active=True)
    
    # 分离场内和场外产品
    exchange_products = [p for p in all_products if p.get('channel') == 'EXCHANGE']
    otc_products = [p for p in all_products if p.get('channel') == 'OTC']
    
    # 默认显示场内产品（优先）
    exchange_product_dict = {}
    exchange_product_names = []
    for p in exchange_products:
        display_name = format_product_display_name(p)
        exchange_product_dict[display_name] = p
        exchange_product_names.append(display_name)
    
    otc_product_dict = {}
    otc_product_names = []
    for p in otc_products:
        display_name = format_product_display_name(p)
        otc_product_dict[display_name] = p
        otc_product_names.append(display_name)
    
    # 合并产品列表（场内优先）
    all_product_dict = {}
    all_product_names = []
    for p in exchange_products + otc_products:
        display_name = format_product_display_name(p)
        all_product_dict[display_name] = p
        all_product_names.append(display_name)
    
    with tab1:
        # 选择场内/场外（默认场内）
        buy_channel = st.radio(
            "交易类型",
            ["场内", "场外"],
            index=0,  # 默认场内
            key="buy_channel",
            horizontal=True
        )
        
        # 根据选择显示不同的产品列表
        # 使用容器来隔离场内和场外模式的内容
        otc_placeholder = st.empty()
        
        if buy_channel == "场内":
            # 清空场外模式的内容
            with otc_placeholder:
                st.empty()
            
            # 场内模式：使用场内成交录入（自动扣款和确认）
            st.subheader("场内成交录入")
            st.info("💡 场内交易：提交成交记录后会自动从账户扣款并创建买入确认记录，无需单独提交买入扣款")
            
            # 清空场外模式的session state，避免按钮残留
            if 'submit_buy_otc' in st.session_state:
                del st.session_state['submit_buy_otc']
            
            if not exchange_product_names:
                st.warning("⚠️ 暂无场内产品，请先在产品管理中添加场内ETF/LOF产品")
            else:
                col1, col2 = st.columns(2)
                
                with col1:
                    buy_product = st.selectbox("选择产品 *", exchange_product_names, key="buy_product_exchange")
                    buy_trade_type = st.selectbox(
                        "成交类型 *",
                        ["BUY"],
                        format_func=lambda x: {"BUY": "买入"}[x],
                        key="buy_trade_type"
                    )
                    
                    # 账户选择
                    account_options = get_account_options()
                    account_dict = {acc['name']: acc['id'] for acc in account_options}
                    account_names = list(account_dict.keys())
                    
                    default_account_idx = 0
                    if '余利宝理财金' in account_names:
                        default_account_idx = account_names.index('余利宝理财金')
                    
                    buy_account_name = st.selectbox(
                        "资金来源账户 *",
                        account_names,
                        index=default_account_idx,
                        key="buy_account"
                    )
                    buy_account_id = account_dict[buy_account_name]
                    # 显示资金来源账户当前余额
                    buy_account_balance = get_account_balance(buy_account_id)
                    st.caption(f"当前余额：¥{format_decimal(buy_account_balance)}")
                    
                    buy_amount = st.number_input(
                        "成交金额 *",
                        min_value=0.01,
                        step=100.0,
                        key="buy_amount_exchange",
                        help="成交金额（含手续费）"
                    )
                    
                    buy_shares = st.number_input(
                        "成交份额 *",
                        min_value=0.0001,
                        step=100.0,
                        format="%.4f",
                        key="buy_shares",
                        help="成交份额（支持4位小数）"
                    )
                
                with col2:
                    buy_trade_date = st.date_input(
                        "成交日期 *",
                        value=date.today(),
                        key="buy_trade_date_exchange"
                    )
                    
                    buy_trade_time = st.text_input(
                        "成交时间（HH:MM） *",
                        value=datetime.now().strftime('%H:%M'),
                        key="buy_trade_time",
                        help="成交时间，精确到分钟"
                    )
                    
                    buy_price = st.number_input(
                        "成交价（可选）",
                        min_value=0.0,
                        step=0.01,
                        value=0.0,
                        key="buy_price",
                        help="如果不填，将根据金额和份额自动计算"
                    )
                    
                    # 手续费自动计算
                    default_fee = 0.0
                    if buy_product and buy_amount > 0:
                        from core.exchange_trade_service import calc_default_fee
                        default_fee = float(calc_default_fee(Decimal(str(buy_amount))))
                        st.info(f"💡 默认手续费: ¥{default_fee:.2f}（万0.845，最低0.20）")
                    
                    buy_fee = st.number_input(
                        "手续费（可选）",
                        min_value=0.0,
                        step=0.01,
                        value=default_fee,
                        key="buy_fee_exchange",
                        help="如果不填，将自动计算"
                    )
                    
                    buy_note = st.text_input("备注（可选）", key="buy_note_exchange")
                
                if st.button("提交成交记录", type="primary", key="submit_buy_exchange"):
                    if not buy_product or buy_amount <= 0 or buy_shares <= 0:
                        st.error("❌ 请填写必填项：产品、成交金额、成交份额")
                    else:
                        try:
                            product = exchange_product_dict[buy_product]
                            product_id = product['id']
                            
                            # 解析成交时间
                            try:
                                time_parts = buy_trade_time.split(':')
                                if len(time_parts) == 2:
                                    trade_time = datetime.combine(
                                        buy_trade_date,
                                        datetime.strptime(buy_trade_time, '%H:%M').time()
                                    )
                                else:
                                    trade_time = datetime.combine(buy_trade_date, datetime.now().time())
                            except:
                                trade_time = datetime.combine(buy_trade_date, datetime.now().time())
                            
                            # 调用服务保存成交
                            from core.exchange_trade_service import save_exchange_trade
                            
                            price = Decimal(str(buy_price)) if buy_price > 0 else None
                            fee = Decimal(str(buy_fee)) if buy_fee > 0 else None
                            
                            success, message, result = save_exchange_trade(
                                product_id=product_id,
                                account_id=buy_account_id,
                                trade_date=buy_trade_date,
                                trade_time=trade_time,
                                trade_type='BUY',
                                amount=Decimal(str(buy_amount)),
                                shares=Decimal(str(buy_shares)),
                                price=price,
                                fee=fee,
                                remark=buy_note or None
                            )
                            
                            if success:
                                st.success(f"✅ {message}")
                                
                                # 显示详细信息
                                if result:
                                    buy_confirm_order_id = result.get('buy_confirm_order_id')
                                    if buy_confirm_order_id:
                                        st.info(f"📝 已自动创建买入确认记录，订单号: {buy_confirm_order_id}")
                                    
                                    holdings_before = result.get('holdings_before', {})
                                    holdings_after = result.get('holdings_after', {})
                                    deduction_result = result.get('deduction_result')
                                    suggestion = result.get('suggestion')
                                    warnings = result.get('warnings', [])
                                    
                                    if warnings:
                                        for warning in warnings:
                                            st.warning(f"⚠️ {warning}")
                                    
                                    st.divider()
                                    st.subheader("📊 变化详情")
                                    
                                    col1, col2, col3 = st.columns(3)
                                    
                                    with col1:
                                        st.metric(
                                            "持仓份额",
                                            f"{holdings_after.get('current_qty', 0):.4f}",
                                            f"{holdings_after.get('current_qty', 0) - holdings_before.get('current_qty', 0):.4f}"
                                        )
                                    
                                    with col2:
                                        if deduction_result:
                                            wait_pool_before = deduction_result.wait_pool_before
                                            wait_pool_after = deduction_result.wait_pool_after
                                            st.metric(
                                                "等待池余额",
                                                f"¥{wait_pool_after:.2f}",
                                                f"¥{wait_pool_after - wait_pool_before:.2f}"
                                            )
                                    
                                    with col3:
                                        if deduction_result:
                                            cash_pool_before = deduction_result.cash_pool_before
                                            cash_pool_after = deduction_result.cash_pool_after
                                            st.metric(
                                                "现金池余额",
                                                f"¥{cash_pool_after:.2f}",
                                                f"¥{cash_pool_after - cash_pool_before:.2f}"
                                            )
                                    
                                    # 显示扣减明细
                                    if deduction_result:
                                        st.info(
                                            f"💰 资金扣减明细：\n"
                                            f"- 从等待池扣减: ¥{deduction_result.wait_pool_deducted:.2f}\n"
                                            f"- 从现金池扣减: ¥{deduction_result.cash_pool_deducted:.2f}\n"
                                            f"- 合计扣减: ¥{deduction_result.total_deducted:.2f}"
                                        )
                                    
                                    # 显示最新建议
                                    if suggestion:
                                        st.divider()
                                        st.subheader("💡 最新Advisor建议")
                                        action = suggestion.get('action', 'HOLD')
                                        action_label = {'BUY': '买入', 'HOLD': '持有', 'WAIT': '等待', 'SKIP': '跳过'}.get(action, action)
                                        st.info(f"**建议动作**: {action_label}")
                                        st.caption(suggestion.get('reason', ''))
                            else:
                                st.error(f"❌ {message}")
                        except Exception as e:
                            st.error(f"❌ 提交失败: {e}")
                            import traceback
                            st.code(traceback.format_exc())
        elif buy_channel == "场外":
            # 场外模式：使用原有的买入扣款逻辑
            with otc_placeholder.container():
                st.subheader("场外买入扣款")
                
                # 清空场内模式的session state，避免按钮残留
                if 'submit_buy_exchange' in st.session_state:
                    del st.session_state['submit_buy_exchange']
                
                col1, col2 = st.columns(2)
                
                with col1:
                    # 场外产品列表（默认显示场外产品）
                    buy_product = st.selectbox("选择产品", otc_product_names if otc_product_names else all_product_names, key="buy_product_otc")
                    
                    # 扣款账户选择（资金来源账户）
                    account_options = get_account_options()
                    account_dict = {acc['name']: acc['id'] for acc in account_options}
                    account_names = list(account_dict.keys())
                    
                    # 默认扣款账户：根据产品自动获取，如果没有则使用默认账户
                    default_debit_account = None
                    if buy_product:
                        product = otc_product_dict.get(buy_product) or all_product_dict.get(buy_product)
                        if product:
                            default_debit_account = get_tx_account(product['code'], 'buy_debit')
                    
                    # 找到默认账户在列表中的索引
                    default_debit_account_idx = 0
                    if default_debit_account:
                        for idx, acc in enumerate(account_options):
                            if acc['id'] == default_debit_account:
                                default_debit_account_idx = idx
                                break
                    
                    buy_debit_account_name = st.selectbox(
                        "扣款账户（资金来源）*",
                        account_names,
                        index=default_debit_account_idx,
                        key="buy_debit_account_otc",
                        help="从哪个账户扣款"
                    )
                    buy_debit_account_id = account_dict[buy_debit_account_name]
                    buy_debit_account_balance = get_account_balance(buy_debit_account_id)
                    st.caption(f"当前余额：¥{format_decimal(buy_debit_account_balance)}")
                    
                    # 检查产品是否关联有子账户（这些是买入后持仓会增加的账户）
                    linked_accounts = []
                    if buy_product:
                        product = otc_product_dict.get(buy_product) or all_product_dict.get(buy_product)
                        if product:
                            linked_accounts = get_product_linked_accounts(product['code'])
                    
                    buy_amount = 0.0
                    account_amounts = {}  # {account_id: amount} 子账户分配金额
                    
                    if linked_accounts:
                        # 如果产品关联有子账户，分别填写每个子账户的分配金额
                        st.info("💡 该产品关联有子账户，请分别填写每个子账户的分配金额（买入后持仓会增加）")
                        total_amount = Decimal('0')
                        
                        for acc in linked_accounts:
                            acc_id = acc['id']
                            acc_name = acc['name']
                            acc_amount_key = f"buy_account_amount_{acc_id}"
                            acc_balance = get_account_balance(acc_id)
                            st.caption(f"{acc_name} 当前余额：¥{format_decimal(acc_balance)}")
                            acc_amount = st.number_input(
                                f"{acc_name} ({acc_id}) 分配金额",
                                min_value=0.0,
                                step=100.0,
                                key=acc_amount_key,
                                help="该子账户分配的购买金额"
                            )
                            if acc_amount > 0:
                                account_amounts[acc_id] = Decimal(str(acc_amount))
                                total_amount += account_amounts[acc_id]
                        
                        if total_amount > 0:
                            buy_amount = float(total_amount)
                            st.success(f"✓ 总购买金额: ¥{total_amount:.2f}")
                            for acc_id, acc_amount in account_amounts.items():
                                st.caption(f"  - {get_account_name(acc_id)}: ¥{acc_amount:.2f}")
                    else:
                        # 如果没有关联子账户，使用原来的单金额输入
                        buy_amount = st.number_input("扣款金额（含手续费）", min_value=0.01, step=100.0, key="buy_amount_otc")
                    
                    buy_fee_override = 0.0  # 默认值
                    if buy_product and buy_amount > 0:
                        # 使用合并的产品字典
                        product = otc_product_dict.get(buy_product) or all_product_dict.get(buy_product)
                        if product:
                            from core.invest_service import calc_buy_fee
                            fee = calc_buy_fee(product['code'], Decimal(str(buy_amount)))
                            buy_fee_rate = float(product.get('buy_fee_rate') or 0)
                            st.info(f"💡 预计手续费: ¥{fee:.2f}（费率 {buy_fee_rate*100:.2f}%）")
                            st.info(f"💡 净申购额: ¥{Decimal(str(buy_amount)) - fee:.2f}")
                            
                            buy_fee_override = st.number_input("手续费（可覆盖）", min_value=0.0, value=float(fee), step=0.01, key="buy_fee_otc")
                
                with col2:
                    # 交易日期输入（可编辑，默认当前日期）
                    buy_trade_date = st.date_input(
                        "交易日期", 
                        value=date.today(), 
                        key="buy_trade_date_otc",
                        help="扣款发生的日期，修改后会自动计算净值日期和确认日期"
                    )
                    
                    if buy_product:
                        # 使用合并的产品字典
                        product = otc_product_dict.get(buy_product) or all_product_dict.get(buy_product)
                        if product:
                            # 根据交易日期计算净值日期和确认日期
                            from core.invest_service import calc_confirm_date
                            buy_confirm_offset = product.get('buy_confirm_offset', 1)
                            
                            # 净值日期 = 交易日期
                            buy_nav_date = buy_trade_date
                            # 确认日期 = 交易日期 + confirm_offset 个交易日
                            buy_confirm_date = calc_confirm_date(buy_trade_date, buy_confirm_offset)
                            
                            st.info(f"📅 净值日期: {buy_nav_date}")
                            st.info(f"📅 确认日期: {buy_confirm_date}")
                    
                    # 二级分类选择
                    buy_category_l2_options = ["基金定投", "定期理财", "基金补仓"]
                    buy_category_l2 = st.selectbox("交易类型", buy_category_l2_options, key="buy_category_l2")
                    
                    # 请求时间（时分秒），默认当前时间
                    buy_time = st.text_input(
                        "请求时间（HH:MM:SS）", 
                        value=datetime.now().strftime('%H:%M:%S'), 
                        key="buy_time_otc",
                        help="扣款发生的时间，精确到秒"
                    )
                    buy_note = st.text_input("备注（可选）", key="buy_note_otc")
                
                if st.button("提交买入扣款", type="primary", key="submit_buy_otc"):
                    if not buy_product or buy_amount <= 0:
                        st.error("❌ 请选择产品并输入金额！")
                    elif linked_accounts and len(account_amounts) == 0:
                        st.error("❌ 请至少填写一个子账户的分配金额！")
                    else:
                        try:
                            # 使用合并的产品字典
                            product = otc_product_dict.get(buy_product) or all_product_dict.get(buy_product)
                            if not product:
                                st.error(f"❌ 产品不存在: {buy_product}")
                            else:
                                # 解析时间
                                try:
                                    time_parts = buy_time.split(':')
                                    requested_at = datetime.combine(
                                        buy_trade_date,
                                        datetime.strptime(buy_time, '%H:%M:%S').time() if len(time_parts) == 3 
                                        else datetime.strptime(buy_time, '%H:%M').time()
                                    )
                                except:
                                    requested_at = datetime.combine(buy_trade_date, datetime.now().time())
                                
                                # 如果有账户分配，在备注中记录
                                note = buy_note or ""
                                if account_amounts:
                                    account_info = '|'.join([f"{acc_id}:{acc_amount}" for acc_id, acc_amount in account_amounts.items()])
                                    if note:
                                        note = f"{note}|account_amounts:{account_info}"
                                    else:
                                        note = f"|account_amounts:{account_info}"
                                
                                order_id = add_buy_debit(
                                    product_code=product['code'],
                                    amount=Decimal(str(buy_amount)),
                                    fee=Decimal(str(buy_fee_override)) if buy_fee_override > 0 else None,
                                    requested_at=requested_at,
                                    trade_date=buy_trade_date,
                                    note=note or None
                                )
                                
                                # 从扣款账户扣款
                                event_time = requested_at.strftime('%Y-%m-%d %H:%M:%S')
                                # 统一从选择的扣款账户扣款
                                add_expense(
                                    account_from=buy_debit_account_id,
                                    amount=Decimal(str(buy_amount)),
                                    category_l1="理财投资",
                                    category_l2=buy_category_l2,
                                    event_time=event_time,
                                    note=f"{product.get('name') or product.get('product_name', '')} 买入 (订单号: {order_id})"
                                )
                                
                                st.success(f"✅ 买入扣款已提交！订单号: {order_id}")
                                st.info(f"💰 已从 {buy_debit_account_name} 扣款: ¥{buy_amount:.2f}")
                                if account_amounts:
                                    st.info(f"📊 子账户分配：")
                                    for acc_id, acc_amount in account_amounts.items():
                                        st.caption(f"  - {get_account_name(acc_id)}: ¥{acc_amount:.2f}")
                                try:
                                    collect_nav_and_build_snapshots(silent=True)
                                except:
                                    pass
                        except Exception as e:
                            st.error(f"❌ 提交失败: {e}")
                            import traceback
                            st.code(traceback.format_exc())
        else:
            # 如果既不是场内也不是场外（理论上不应该发生），显示提示
            st.warning("⚠️ 请选择交易类型（场内或场外）")
    
    # ------------------------------------------------------------
    # Tab 4: 场外基金转托管到场内（LOF）
    # ------------------------------------------------------------
    with tab4:
        st.subheader("🔁 场外基金转托管到场内（LOF）")
        st.info("💡 转托管将自动更新场外和场内持仓，并生成关联的理财记录。")

        # 只列出同时存在 OTC 和 EXCHANGE 两个版本的基金代码
        code_to_channels: Dict[str, set] = {}
        for p in all_products:
            code = p.get("code", "")
            ch = p.get("channel", "OTC")
            if not code:
                continue
            if code not in code_to_channels:
                code_to_channels[code] = set()
            code_to_channels[code].add(ch)

        eligible_codes = [code for code, chs in code_to_channels.items() if "OTC" in chs and "EXCHANGE" in chs]
        eligible_products = [p for p in all_products if p.get("code") in eligible_codes]

        if not eligible_products:
            st.warning("当前没有同时存在场外和场内版本的基金产品。")
        else:
            # 构造展示名称：163406 兴全合润混合A
            code_to_name = {}
            for p in eligible_products:
                code = p.get("code")
                name = p.get("product_name") or ""
                # 同一代码可能有两条记录（OTC/EXCHANGE），这里取一个代表名字即可
                if code and code not in code_to_name:
                    code_to_name[code] = name

            display_options = [f"{code} {name}" for code, name in code_to_name.items()]
            display_options.sort()

            col1, col2 = st.columns(2)
            with col1:
                selected_display = st.selectbox("选择基金（需要同时有场外和场内版本）", display_options, key="custody_product")
                if selected_display:
                    product_code = selected_display.split()[0]
                else:
                    product_code = None

                from datetime import date as _date
                transfer_date = st.date_input("转托管日期", value=_date.today(), key="custody_transfer_date")
                
                transfer_time = st.text_input("转托管时间（HH:MM:SS）", value="10:00:00", key="custody_transfer_time",
                                             help="默认 10:00:00")

                transfer_shares = st.number_input(
                    "转托管份额 *（从场外转到场内）",
                    min_value=0.0001,
                    step=100.0,
                    format="%.4f",
                    key="custody_transfer_shares",
                )
                
                # 成交价格（场内价格）
                transfer_price = st.number_input(
                    "成交价格 *（场内价格）",
                    min_value=0.0001,
                    step=0.01,
                    format="%.4f",
                    key="custody_transfer_price",
                    help="场内成交价格，用于计算金额"
                )
                
                # 自动计算金额
                if transfer_shares > 0 and transfer_price > 0:
                    calculated_amount = Decimal(str(transfer_shares)) * Decimal(str(transfer_price))
                    st.info(f"💰 计算金额：¥{format_decimal(calculated_amount)}（价格 × 份额）")
                
            with col2:
                # 费用
                transfer_fee = st.number_input(
                    "费用",
                    min_value=0.0,
                    step=0.01,
                    value=0.0,
                    format="%.2f",
                    key="custody_transfer_fee",
                    help="转托管费用，默认 0"
                )
                
                custody_note = st.text_input("备注（可选）", key="custody_transfer_note")

                # 展示该产品当前总份额，方便你参考
                try:
                    from core.snapshot_service import read_latest_daily
                    daily_records = read_latest_daily()
                    daily_map = {r.get("product_code"): r for r in daily_records}
                    if product_code and product_code in daily_map:
                        info = daily_map[product_code]
                        try:
                            total_shares = Decimal(str(info.get("shares") or "0"))
                            total_value = Decimal(str(info.get("value") or "0"))
                            st.caption(
                                f"当前总份额：{total_shares:.4f} | 当前市值约：¥{format_decimal(total_value)} "
                                "(包含场内+场外)"
                            )
                        except Exception:
                            pass
                except Exception:
                    pass

            st.divider()
            if st.button("保存转托管记录", type="primary", key="custody_transfer_submit"):
                if not product_code:
                    st.error("❌ 请先选择基金。")
                elif transfer_shares <= 0:
                    st.error("❌ 转托管份额必须大于0。")
                elif transfer_price <= 0:
                    st.error("❌ 成交价格必须大于0。")
                else:
                    try:
                        from core.custody_transfer_service import add_fund_custody_transfer

                        result = add_fund_custody_transfer(
                            product_code=product_code,
                            transfer_shares=Decimal(str(transfer_shares)),
                            price=Decimal(str(transfer_price)),
                            transfer_date=transfer_date,
                            fee=Decimal(str(transfer_fee)),
                            transfer_time=transfer_time,
                            note=custody_note,
                        )
                        if result.get('success'):
                            st.success(f"✅ {result.get('message', '转托管成功')}：{product_code} {transfer_shares:.4f} 份 @ {transfer_price} 场外→场内")
                            st.rerun()
                        else:
                            st.error(f"❌ {result.get('message', '转托管失败')}")
                    except Exception as e:
                        st.error(f"❌ 保存失败: {e}")
                        import traceback
                        st.exception(e)
    with tab2:
        st.subheader("赎回发起")
        
        col1, col2 = st.columns(2)
        
        with col1:
            # 默认显示场内产品（优先）
            redeem_product = st.selectbox("选择产品", all_product_names, key="redeem_product")
            
            # 检查产品是否关联有账户
            redeem_linked_accounts = []
            if redeem_product:
                product = all_product_dict[redeem_product]
                if product:
                    redeem_linked_accounts = get_product_linked_accounts(product['code'])
                    # 显示产品当前总份额和市值（从 daily_snapshot）
                    from core.snapshot_service import read_latest_daily
                    daily_records = read_latest_daily()
                    daily_map = {r.get('product_code'): r for r in daily_records}
                    daily_info = daily_map.get(product['code'])
                    if daily_info:
                        try:
                            current_shares = Decimal(str(daily_info.get('shares') or '0'))
                            current_nav = Decimal(str(daily_info.get('nav') or '1'))
                            current_value = current_shares * current_nav
                            st.caption(
                                f"产品当前总份额：{current_shares:.4f} | 最新净值：{current_nav:.4f} | 总市值约：¥{format_decimal(current_value)}"
                            )
                        except Exception:
                            pass
            
            redeem_shares = st.number_input("赎回份额", min_value=0.01, step=100.0, key="redeem_shares")
            redeem_holding_days = st.number_input("持有天数", min_value=1, step=1, value=30, key="redeem_holding_days")
            
            # 显示赎回费率
            if redeem_product:
                product = all_product_dict[redeem_product]
                product_config = get_product(product['code'])
                if product_config:
                    fee_rate = get_sell_fee_rate(product_config, redeem_holding_days)
                    st.info(f"💡 赎回费率: {float(fee_rate)*100:.2f}%（手续费将在结算时根据净值自动计算，也可手动填写）")
        
        with col2:
            # 交易日期输入（可编辑，默认当前日期）
            redeem_trade_date = st.date_input(
                "交易日期", 
                value=date.today(), 
                key="redeem_trade_date",
                help="赎回发生的日期，修改后会自动计算确认日期"
            )
            
            if redeem_product:
                product = all_product_dict[redeem_product]
                # 根据交易日期计算确认日期
                from core.invest_service import calc_confirm_date
                sell_confirm_offset = product.get('sell_confirm_offset', 1)
                redeem_confirm_date = calc_confirm_date(redeem_trade_date, sell_confirm_offset)
                st.info(f"📅 确认日期: {redeem_confirm_date}")
            
            # 请求时间（日期+时分秒），默认当前时间
            redeem_request_date = st.date_input(
                "请求日期", 
                value=date.today(), 
                key="redeem_request_date",
                help="赎回请求的日期"
            )
            redeem_request_time = st.text_input(
                "请求时间（HH:MM:SS）", 
                value=datetime.now().strftime('%H:%M:%S'), 
                key="redeem_request_time",
                help="赎回请求的时间，精确到秒"
            )
            
            # 赎回账户选择
            # 如果产品关联了多个账户，支持选择主账户和固定金额，如果份额不足则自动提示补充账户
            redeem_from_account = None  # 主账户
            redeem_fixed_amount = None  # 固定金额（可选）
            redeem_supplement_accounts = []  # 补充账户列表 [{account_id, shares}]
            redeem_account = None  # 资金到账账户
            
            if redeem_linked_accounts:
                # 如果产品关联有账户，支持选择主账户和固定金额
                st.info("💡 该产品关联有账户，请选择赎回来源账户，如果份额不足系统会自动提示补充账户")
                
                linked_account_dict = {acc['name']: acc['id'] for acc in redeem_linked_accounts}
                linked_account_names = list(linked_account_dict.keys())
                
                # 获取每个子账户的实际份额（从 accounts.shares 字段读取）
                from data.account_service import get_account_shares
                account_shares_map = {}
                for acc in redeem_linked_accounts:
                    acc_id = acc['id']
                    account_shares_map[acc_id] = get_account_shares(acc_id)
                
                # 选择主账户
                redeem_from_account_name = st.selectbox(
                    "赎回来源账户 *（从哪个账户赎回份额）", 
                    linked_account_names, 
                    key="redeem_from_account"
                )
                redeem_from_account = linked_account_dict[redeem_from_account_name]
                
                # 显示主账户当前余额和实际份额
                redeem_from_balance = get_account_balance(redeem_from_account)
                main_account_shares = account_shares_map.get(redeem_from_account, Decimal('0'))
                
                # 获取确认日期的净值用于计算固定金额所需份额
                # 优先使用确认日期的净值，如果没有再使用最新净值
                estimated_nav = Decimal('1.5')  # 默认值
                nav_source = "默认"
                if redeem_product:
                    product = all_product_dict[redeem_product]
                    from data.nav_reader import get_nav, get_latest_nav
                    
                    # 1. 首先尝试获取确认日期的净值（已在上面计算）
                    if redeem_confirm_date:
                        nav_result = get_nav(product['code'], str(redeem_confirm_date))
                        if nav_result:
                            estimated_nav = nav_result
                            nav_source = f"确认日 {redeem_confirm_date}"
                    
                    # 2. 如果没有确认日期净值，尝试交易日期净值
                    if nav_source == "默认":
                        nav_result = get_nav(product['code'], str(redeem_trade_date))
                        if nav_result:
                            estimated_nav = nav_result
                            nav_source = f"交易日 {redeem_trade_date}"
                    
                    # 3. 如果都没有，使用最新净值
                    if nav_source == "默认":
                        latest_nav = get_latest_nav(product['code'])
                        if latest_nav:
                            estimated_nav = latest_nav[1]
                            nav_source = f"最新 {latest_nav[0]}"
                
                st.caption(
                    f"{redeem_from_account_name} 当前余额：¥{format_decimal(redeem_from_balance)} | 实际持有份额：{main_account_shares:.6f}"
                )
                
                # 固定金额输入（可选）
                use_fixed_amount = st.checkbox(
                    "使用固定金额赎回",
                    key="use_fixed_amount",
                    help="如果勾选，将按固定金额赎回，否则按份额赎回"
                )
                
                if use_fixed_amount:
                    redeem_fixed_amount = st.number_input(
                        "主账户固定赎回金额",
                        min_value=0.01,
                        step=100.0,
                        key="redeem_fixed_amount",
                        help="主账户必须赎回的固定金额（如：房租账户必须赎回4000），剩余份额由其他账户补充"
                    )
                    
                    # 计算主账户固定金额对应的份额
                    if estimated_nav > 0:
                        main_account_fixed_shares = (Decimal(str(redeem_fixed_amount)) / estimated_nav).quantize(Decimal('0.000001'), rounding=ROUND_HALF_UP)
                        st.caption(f"主账户按净值 {estimated_nav:.4f}（{nav_source}）赎回份额：{main_account_fixed_shares:.6f}")
                
                # 检查主账户份额是否充足，并计算需要补充的份额
                if redeem_shares > 0:
                    total_redeem_shares = Decimal(str(redeem_shares))  # 总赎回份额
                    
                    if use_fixed_amount and redeem_fixed_amount:
                        # 固定金额模式：主账户赎回固定金额对应的份额，剩余由其他账户补充
                        main_account_fixed_shares = (Decimal(str(redeem_fixed_amount)) / estimated_nav).quantize(Decimal('0.000001'), rounding=ROUND_HALF_UP)
                        
                        # 检查主账户是否有足够份额赎回固定金额
                        if main_account_shares < main_account_fixed_shares:
                            st.error(f"❌ 主账户份额不足！需要 {main_account_fixed_shares:.6f} 份额（固定金额 ¥{redeem_fixed_amount}），但只有 {main_account_shares:.6f}")
                            shares_needed = main_account_fixed_shares  # 仍然标记需要的份额
                        else:
                            shares_needed = main_account_fixed_shares  # 主账户需要赎回的份额
                        
                        # 计算剩余需要从其他账户补充的份额
                        remaining_shares = total_redeem_shares - main_account_fixed_shares
                        if remaining_shares > Decimal('0.000001'):
                            remaining_amount = remaining_shares * estimated_nav
                            st.info(
                                f"📊 赎回份额分配：\n"
                                f"- 总赎回份额：{total_redeem_shares:.6f}\n"
                                f"- 主账户赎回：{main_account_fixed_shares:.6f}（固定金额 ¥{redeem_fixed_amount}）\n"
                                f"- 需要其他账户补充：{remaining_shares:.6f}（约合 ¥{remaining_amount:.2f}）"
                            )
                    else:
                        # 普通模式：检查主账户份额是否充足
                        shares_needed = total_redeem_shares
                        if main_account_shares < shares_needed:
                            remaining_shares = shares_needed - main_account_shares
                            remaining_amount = remaining_shares * estimated_nav
                    
                    # 需要补充账户的情况（普通模式：主账户不足；固定金额模式：有剩余份额）
                    need_supplement = False
                    if use_fixed_amount and redeem_fixed_amount:
                        # 固定金额模式：总份额 > 主账户固定份额 时需要补充
                        need_supplement = total_redeem_shares > main_account_fixed_shares + Decimal('0.000001')
                        if need_supplement:
                            remaining_shares = total_redeem_shares - main_account_fixed_shares
                            remaining_amount = remaining_shares * estimated_nav
                    else:
                        # 普通模式：主账户份额不足时需要补充
                        need_supplement = main_account_shares < shares_needed
                        if need_supplement:
                            remaining_shares = shares_needed - main_account_shares
                            remaining_amount = remaining_shares * estimated_nav
                    
                    if need_supplement:
                        if not (use_fixed_amount and redeem_fixed_amount):
                            # 普通模式下显示主账户份额不足的警告
                            st.warning(
                                f"⚠️ 主账户份额不足！\n"
                                f"- 主账户当前份额：{main_account_shares:.6f}\n"
                                f"- 需要份额：{shares_needed:.6f}\n"
                                f"- 还需份额：{remaining_shares:.6f}（按净值 {estimated_nav:.4f} 计算约合 ¥{remaining_amount:.2f}）"
                            )
                        
                        # 显示补充账户选择
                        st.markdown("**请选择补充账户：**")
                        
                        # 获取可用的补充账户（排除主账户）
                        supplement_account_options = [
                            acc for acc in redeem_linked_accounts 
                            if acc['id'] != redeem_from_account
                        ]
                        
                        if supplement_account_options:
                            # 第一个补充账户
                            supp1_key = "redeem_supplement_account_1"
                            supp1_name = st.selectbox(
                                "补充账户 1",
                                [acc['name'] for acc in supplement_account_options],
                                key=supp1_key
                            )
                            supp1_id = next(acc['id'] for acc in supplement_account_options if acc['name'] == supp1_name)
                            supp1_shares = account_shares_map.get(supp1_id, Decimal('0'))
                            
                            # 计算第一个补充账户能提供的份额
                            supp1_available = min(supp1_shares, remaining_shares)
                            supp1_shares_input = st.number_input(
                                f"{supp1_name} 补充份额（可用：{supp1_shares:.6f}）",
                                min_value=0.0,
                                max_value=float(supp1_shares),
                                value=float(supp1_available),
                                step=0.0001,
                                key="redeem_supplement_shares_1"
                            )
                            
                            if supp1_shares_input > 0:
                                redeem_supplement_accounts.append({
                                    'account_id': supp1_id,
                                    'shares': Decimal(str(supp1_shares_input))
                                })
                                
                                # 检查是否还需要第二个补充账户
                                remaining_after_supp1 = remaining_shares - Decimal(str(supp1_shares_input))
                                if remaining_after_supp1 > Decimal('0.000001'):
                                    st.info(f"还需补充份额：{remaining_after_supp1:.6f}（约合 ¥{remaining_after_supp1 * estimated_nav:.2f}）")
                                    
                                    # 第二个补充账户（排除主账户和第一个补充账户）
                                    supp2_options = [
                                        acc for acc in supplement_account_options 
                                        if acc['id'] != supp1_id
                                    ]
                                    
                                    if supp2_options:
                                        supp2_key = "redeem_supplement_account_2"
                                        supp2_name = st.selectbox(
                                            "补充账户 2",
                                            [acc['name'] for acc in supp2_options],
                                            key=supp2_key
                                        )
                                        supp2_id = next(acc['id'] for acc in supp2_options if acc['name'] == supp2_name)
                                        supp2_shares = account_shares_map.get(supp2_id, Decimal('0'))
                                        
                                        supp2_available = min(supp2_shares, remaining_after_supp1)
                                        supp2_shares_input = st.number_input(
                                            f"{supp2_name} 补充份额（可用：{supp2_shares:.6f}）",
                                            min_value=0.0,
                                            max_value=float(supp2_shares),
                                            value=float(supp2_available),
                                            step=0.0001,
                                            key="redeem_supplement_shares_2"
                                        )
                                        
                                        if supp2_shares_input > 0:
                                            redeem_supplement_accounts.append({
                                                'account_id': supp2_id,
                                                'shares': Decimal(str(supp2_shares_input))
                                            })
                        else:
                            st.error("❌ 没有可用的补充账户！")
                
                # 资金到账账户（默认余利宝理财金）
                account_options = get_account_options()
                account_dict = {acc['name']: acc['id'] for acc in account_options}
                account_names = list(account_dict.keys())
                
                default_redeem_account_name = '余利宝理财金'
                default_redeem_account_idx = 0
                if default_redeem_account_name in account_names:
                    default_redeem_account_idx = account_names.index(default_redeem_account_name)
                
                redeem_account_name = st.selectbox(
                    "资金到账账户（赎回后资金到账的账户）", 
                    account_names, 
                    index=default_redeem_account_idx,
                    key="redeem_account"
                )
                redeem_account = account_dict[redeem_account_name]
                redeem_account_balance = get_account_balance(redeem_account)
                st.caption(f"资金到账账户当前余额：¥{format_decimal(redeem_account_balance)}")
            else:
                # 如果没有关联账户，只需要选择资金到账账户
                account_options = get_account_options()
                account_dict = {acc['name']: acc['id'] for acc in account_options}
                account_names = list(account_dict.keys())
                
                # 默认选择余利宝理财金
                default_redeem_account_name = '余利宝理财金'
                default_redeem_account_idx = 0
                if default_redeem_account_name in account_names:
                    default_redeem_account_idx = account_names.index(default_redeem_account_name)
                
                redeem_account_name = st.selectbox(
                    "资金到账账户", 
                    account_names, 
                    index=default_redeem_account_idx,
                    key="redeem_account"
                )
                redeem_account = account_dict[redeem_account_name]
                redeem_account_balance = get_account_balance(redeem_account)
                st.caption(f"资金到账账户当前余额：¥{format_decimal(redeem_account_balance)}")
            
            # 手续费输入（可选，默认按费率计算）
            redeem_fee_override = st.number_input(
                "手续费（可选，默认按费率计算）",
                min_value=0.0,
                step=0.01,
                value=0.0,
                key="redeem_fee_override",
                help="如果不填或填0，将按费率自动计算；如果填写，将使用此值"
            )
            
            redeem_note = st.text_input("备注（可选）", key="redeem_note")
        
        if st.button("提交赎回发起", type="primary", key="submit_redeem"):
            if not redeem_product or redeem_shares <= 0:
                st.error("❌ 请选择产品并输入份额！")
            else:
                try:
                    product = all_product_dict[redeem_product]
                    # 解析请求时间
                    try:
                        time_parts = redeem_request_time.split(':')
                        requested_at = datetime.combine(
                            redeem_request_date,
                            datetime.strptime(redeem_request_time, '%H:%M:%S').time() if len(time_parts) == 3 
                            else datetime.strptime(redeem_request_time, '%H:%M').time()
                        )
                    except:
                        requested_at = datetime.combine(redeem_request_date, datetime.now().time())
                    
                    # 如果用户填写了手续费，则传入；否则传None，让系统按费率计算
                    fee_override = Decimal(str(redeem_fee_override)) if redeem_fee_override > 0 else None
                    
                    # 准备固定金额和补充账户参数
                    fixed_amount_param = None
                    if redeem_linked_accounts and redeem_from_account:
                        if use_fixed_amount and redeem_fixed_amount:
                            fixed_amount_param = Decimal(str(redeem_fixed_amount))
                    
                    order_id = add_redeem_request(
                        product_code=product['code'],
                        shares=Decimal(str(redeem_shares)),
                        holding_days=redeem_holding_days,
                        requested_at=requested_at,
                        trade_date=redeem_trade_date,
                        redeem_account=redeem_account,
                        redeem_from_account=redeem_from_account if redeem_linked_accounts and redeem_from_account else None,
                        redeem_from_accounts=None,  # 新格式不再使用此参数
                        redeem_fixed_amount=fixed_amount_param,
                        redeem_supplement_accounts=redeem_supplement_accounts if redeem_supplement_accounts else None,
                        note=redeem_note or None,
                        fee_override=fee_override
                    )
                    st.success(f"✅ 赎回发起已提交！订单号: {order_id}")
                    try:
                        collect_nav_and_build_snapshots(silent=True)
                    except:
                        pass
                except Exception as e:
                    st.error(f"❌ 提交失败: {e}")
    
    with tab3:
        st.subheader("补录历史交易")
        
        col1, col2 = st.columns(2)
        
        with col1:
            # 默认显示场内产品（优先）
            history_product = st.selectbox("选择产品", all_product_names, key="history_product")
            history_action = st.selectbox("交易类型", ["buy", "sell", "dividend"], 
                                          format_func=lambda x: {"buy": "买入", "sell": "卖出", "dividend": "分红"}[x],
                                          key="history_action")
            history_confirm_date = st.date_input("确认日期", value=date.today(), key="history_confirm_date")
            history_confirm_time = st.text_input(
                "确认时间（HH:MM:SS）", 
                value="09:30:00", 
                key="history_confirm_time",
                help="交易确认的时间，精确到秒"
            )
        
        with col2:
            history_shares = st.number_input("份额", min_value=0.01, step=100.0, key="history_shares")
            
            if history_action in ['buy', 'sell']:
                history_amount = st.number_input("金额", min_value=0.01, step=100.0, key="history_amount")
                history_fee = st.number_input("手续费", min_value=0.0, step=0.01, key="history_fee")
                history_nav = st.number_input("净值", min_value=0.0001, step=0.0001, format="%.4f", key="history_nav")
                history_nav_date = st.date_input("净值日期", value=date.today(), key="history_nav_date")
            else:
                history_amount = None
                history_fee = None
                history_nav = None
                history_nav_date = None
            
            history_note = st.text_input("备注（可选）", key="history_note")
        
        if st.button("提交补录", type="primary", key="submit_history"):
            if not history_product or history_shares <= 0:
                st.error("❌ 请填写必要信息！")
            else:
                try:
                    product = all_product_dict[history_product]
                    add_history_trade(
                        product_code=product['code'],
                        action=history_action,
                        confirm_date=str(history_confirm_date),
                        shares=Decimal(str(history_shares)),
                        amount=Decimal(str(history_amount)) if history_amount else None,
                        fee=Decimal(str(history_fee)) if history_fee else None,
                        nav=Decimal(str(history_nav)) if history_nav else None,
                        nav_date=str(history_nav_date) if history_nav_date else None,
                        note=history_note or None,
                        confirm_time=history_confirm_time  # 传递确认时间
                    )
                    
                    # 对于情侣小荷包(000686)的分红，自动添加利息收益记录
                    if product['code'] == '000686' and history_action == 'dividend':
                        # 货币基金分红按份额*净值计算金额
                        dividend_nav = Decimal(str(history_nav)) if history_nav else Decimal('1')
                        dividend_amount = Decimal(str(history_shares)) * dividend_nav
                        event_time = f"{history_confirm_date} {history_confirm_time}"
                        # 设置标志位，防止 add_income 触发重复的交易同步
                        st.session_state['_skip_ledger_tx_sync'] = True
                        add_income(
                            account_to='couple_pocket',  # 情侣小荷包账户
                            amount=dividend_amount,
                            category_l1="理财盈利",
                            category_l2="利息收益",
                            event_time=event_time,
                            note=f"货币基金利息: {product['name']}"
                        )
                        st.session_state['_skip_ledger_tx_sync'] = False
                    
                    st.success("✅ 历史交易已补录！")
                    try:
                        collect_nav_and_build_snapshots(silent=True)
                    except:
                        pass
                except Exception as e:
                    st.error(f"❌ 补录失败: {e}")
    
    
    st.divider()
    
    # 最近理财记录
    st.subheader("📋 最近理财记录")
    st.caption('💡 点击表格中的任意行可编辑')
    
    # 过滤器
    # 获取完整产品信息（包含 channel 字段）
    all_products_for_filter = get_products(is_active=True)
    product_filter_options = ['全部'] + [format_product_display_name(p) for p in all_products_for_filter]
    tx_product_filter = st.selectbox("筛选产品", product_filter_options, key="tx_product_filter")
    
    recent_tx = list_recent_transactions(200)  # 显示更多记录
    
    # 产品过滤
    if tx_product_filter != '全部' and recent_tx:
        filter_code = tx_product_filter.split(' - ')[0]
        recent_tx = [r for r in recent_tx if r.get('product_code') == filter_code]
    
    if recent_tx:
        # 计算每个产品的当前份额，用于倒推
        # 注意：转托管转出和转入使用相同的 product_code，但 product_id 不同（场外和场内）
        # 因此需要按 product_id 来区分计算份额
        from core.holdings_calculator import get_all_product_positions
        from core.exchange_holdings_calculator import get_exchange_holdings_summary
        from data.product_service import get_products
        from datetime import date as date_cls
        
        # 获取所有产品信息，建立 product_id -> product_code 的映射
        all_products = get_products(is_active=True)
        product_id_to_code = {p.get('id'): p.get('code') for p in all_products}
        
        # 获取场外产品持仓（按 product_code）
        current_positions = get_all_product_positions(date_cls.today().strftime('%Y-%m-%d'))
        product_shares_by_code = {code: pos[0] for code, pos in current_positions.items()}
        
        # 获取场内产品持仓（按 product_id）
        exchange_product_ids = [p.get('id') for p in all_products if p.get('channel') == 'EXCHANGE']
        exchange_holdings = get_exchange_holdings_summary(exchange_product_ids)
        product_shares_by_id = {pid: Decimal(str(h.get('current_qty', 0))) if h else Decimal('0') 
                                for pid, h in exchange_holdings.items()}
        
        # recent_tx 已经是按时间倒序的，从最新开始倒推份额
        for r in recent_tx:
            action = r.get('action', '')
            product_code = r.get('product_code', '')
            product_id = r.get('product_id')
            shares = r.get('shares', '')
            
            # 确定当前记录后的产品份额
            # 转托管转出是场外产品，转托管转入是场内产品
            if action == 'transfer_out':
                # 转托管转出：使用场外产品份额
                r['_product_shares_after'] = product_shares_by_code.get(product_code, Decimal('0'))
            elif action == 'transfer_in':
                # 转托管转入：使用场内产品份额
                if product_id:
                    r['_product_shares_after'] = product_shares_by_id.get(product_id, Decimal('0'))
                else:
                    r['_product_shares_after'] = Decimal('0')
            else:
                # 其他操作：使用 product_code（场外产品）
                r['_product_shares_after'] = product_shares_by_code.get(product_code, Decimal('0'))
            
            # 倒推：计算这笔交易前的份额
            if action in ['buy', 'buy_confirm'] and shares:
                try:
                    product_shares_by_code[product_code] = product_shares_by_code.get(product_code, Decimal('0')) - Decimal(str(shares))
                except:
                    pass
            elif action in ['sell', 'sell_confirm', 'redeem_request'] and shares:
                try:
                    product_shares_by_code[product_code] = product_shares_by_code.get(product_code, Decimal('0')) + Decimal(str(shares))
                except:
                    pass
            elif action == 'transfer_out' and shares:
                # 转托管转出：场外产品份额减少
                try:
                    product_shares_by_code[product_code] = product_shares_by_code.get(product_code, Decimal('0')) + Decimal(str(shares))
                except:
                    pass
            elif action == 'transfer_in' and shares and product_id:
                # 转托管转入：场内产品份额减少（倒推）
                try:
                    product_shares_by_id[product_id] = product_shares_by_id.get(product_id, Decimal('0')) - Decimal(str(shares))
                except:
                    pass
        
        # 处理数据（按原来的倒序显示）
        # 理财视角（按份额变化）：买入/分红/转托管转入 = 份额增加（红色+），卖出/转托管转出 = 份额减少（绿色-）
        shares_increase_actions = ['buy', 'buy_confirm', 'dividend', 'transfer_in']  # 份额增加类（红色）
        
        rows = []
        raw_records = []
        for r in recent_tx:
            action = r.get('action', '')
            is_pending = action in PENDING_ACTIONS  # 待确认类型（白色，无+/-号）
            is_shares_increase = action in shares_increase_actions  # 份额增加类 = 红色
            amount = r.get('amount', '') or ''
            product_code = r.get('product_code', '')
            shares = r.get('shares', '')
            nav = r.get('nav', '')
            
            # buy 和 buy_confirm 需要计算金额：shares × nav
            if action in ['buy', 'buy_confirm'] and shares and nav:
                try:
                    calc_amount = float(shares) * float(nav)
                    amount = f"{calc_amount:.2f}"
                except:
                    pass
            
            # transfer_in 需要计算金额：shares × nav（价格）
            if action == 'transfer_in' and shares and nav:
                try:
                    calc_amount = float(shares) * float(nav)
                    amount = f"{calc_amount:.2f}"
                except:
                    pass
            
            # sell 和 sell_confirm 也需要计算金额
            if action in ['sell', 'sell_confirm'] and shares and nav:
                try:
                    calc_amount = float(shares) * float(nav)
                    amount = f"{calc_amount:.2f}"
                except:
                    pass
            
            account = get_tx_account(product_code, action)
            account_group = get_account_group_name(account)
            
            # 时间（使用 created_at）
            tx_time = r.get('created_at')
            time_str = str(tx_time)[:19] if tx_time else str(r.get('date', '')) + ' 00:00:00'
            
            # 获取该产品在此交易后的份额
            product_shares_after = r.get('_product_shares_after', Decimal('0'))
            
            # 格式化份额显示（添加+/-号）
            shares_display = ''
            if shares:
                try:
                    shares_val = float(shares)
                    if is_pending:
                        shares_display = f"{shares_val:.4f}"  # 待确认：无+/-号
                    elif is_shares_increase:
                        shares_display = f"+{shares_val:.4f}"  # 份额增加：+ 红色
                    else:
                        shares_display = f"-{shares_val:.4f}"  # 份额减少：- 绿色
                except:
                    shares_display = str(shares)
            
            # 格式化金额显示
            # 赎回时（sell_confirm, redeem_request）：金额用黑色（不显示+/-号）
            # 转托管转出：不显示金额（空字符串）
            # 购买时：保持原有逻辑（份额增加用红色+，份额减少用绿色-）
            if action == 'transfer_out':
                # 转托管转出：不显示金额
                amount_display = ''
            elif action in ['sell_confirm', 'redeem_request']:
                # 赎回时，金额用黑色显示，不添加+/-号
                try:
                    amount_val = float(amount) if amount else 0
                    amount_display = f"{abs(amount_val):.2f}" if amount_val else ''
                except:
                    amount_display = str(amount) if amount else ''
            else:
                # 购买时，使用原有逻辑
                amount_display = format_invest_amount(amount, is_shares_increase, is_pending)
            
            raw_records.append(r)
            rows.append({
                'ID': r.get('id'),
                '时间': time_str,
                '产品': product_code,
                '类型': ACTION_DISPLAY_MAP.get(action, action),
                '金额': amount_display,
                '份额': shares_display,
                '产品份额': f"{product_shares_after:.2f}",  # 该产品在此交易后的累计份额
                '备注': r.get('note', ''),
                '_account': account,
                '_action': action,
                '_is_pending': is_pending,
                '_is_shares_increase': is_shares_increase  # 保存份额增减标志，用于着色
            })
        
        if rows:
            df_tx = pd.DataFrame(rows)
            # 保存原始索引供分页后使用
            df_tx['_original_idx'] = range(len(df_tx))
            
            # 分页
            df_page = paginate_dataframe(df_tx, "invest_tx_records", page_size=50)
            original_indices = df_page['_original_idx'].tolist()
            
            display_cols = ['ID', '时间', '产品', '类型', '金额', '份额', '产品份额', '备注']
            
            # 为金额和份额列着色
            # 金额：赎回时黑色，购买时按份额增减着色（红色+/绿色-）
            # 份额：份额增加红色+，份额减少绿色-，待确认白色
            def color_tx_amount(row):
                is_pending = df_page.loc[row.name, '_is_pending'] if '_is_pending' in df_page.columns else False
                is_shares_increase = df_page.loc[row.name, '_is_shares_increase'] if '_is_shares_increase' in df_page.columns else False
                action = df_page.loc[row.name, '_action'] if '_action' in df_page.columns else ''
                
                styles = []
                for col, val in row.items():
                    if col == '金额':
                        # 赎回时：黑色（不显示+/-号）
                        if action in ['sell_confirm', 'redeem_request']:
                            styles.append('')  # 黑色（默认）
                        else:
                            # 购买时：按份额增减着色
                            styles.append(color_invest_amount(val, is_pending))
                    elif col == '份额':
                        # 份额：份额增加红色+，份额减少绿色-，待确认白色
                        if is_pending:
                            styles.append('')  # 待确认：白色（默认）
                        elif is_shares_increase:
                            styles.append('color: #dc3545')  # 份额增加：红色
                        else:
                            styles.append('color: #28a745')  # 份额减少：绿色
                    else:
                        styles.append('')
                return styles
            
            # 显示带颜色和行选择的表格（不显示原始索引列）
            styled_df = df_page[display_cols].style.apply(color_tx_amount, axis=1)
            event = st.dataframe(
                styled_df, 
                width='stretch', 
                hide_index=True,
                on_select="rerun",
                selection_mode="single-row",
                key="tx_table"
            )
            
            # 检查是否选中了某行
            selected_rows = event.selection.rows if event.selection else []
            
            if selected_rows:
                page_idx = selected_rows[0]
                # 获取原始记录索引
                selected_idx = original_indices[page_idx] if page_idx < len(original_indices) else page_idx
                selected_record = raw_records[selected_idx]
                
                st.markdown("### 📝 编辑记录")
                
                # 准备产品下拉框选项（需要包含 channel 字段）
                all_products_for_edit = get_products(is_active=True)
                product_code_to_name = {p['code']: format_product_display_name(p) for p in all_products_for_edit}
                product_name_to_code = {format_product_display_name(p): p['code'] for p in all_products_for_edit}
                product_display_names = list(product_name_to_code.keys())
                
                col1, col2 = st.columns(2)
                
                with col1:
                    edit_date = st.date_input("日期", value=selected_record.get('date'), key="tx_edit_date")
                    
                    # 产品下拉框
                    current_product = selected_record.get('product_code', '')
                    current_product_display = product_code_to_name.get(current_product, current_product)
                    product_idx = product_display_names.index(current_product_display) if current_product_display in product_display_names else 0
                    edit_product_display = st.selectbox("产品", product_display_names, index=product_idx, key="tx_edit_product")
                    edit_product = product_name_to_code.get(edit_product_display, edit_product_display)
                    
                    edit_amount = st.number_input("金额", value=float(selected_record.get('amount', 0) or 0), step=0.01, key="tx_edit_amount")
                
                with col2:
                    edit_shares = st.number_input("份额", value=float(selected_record.get('shares', 0) or 0), step=0.01, format="%.4f", key="tx_edit_shares")
                    # 转托管记录：nav 字段显示为"价格"；其他记录：显示为"净值"
                    nav_label = "价格" if selected_record.get('action') in ['transfer_in', 'transfer_out'] else "净值"
                    edit_nav = st.number_input(nav_label, value=float(selected_record.get('nav', 0) or 0), step=0.0001, format="%.4f", key="tx_edit_nav",
                                             help="转托管时表示成交价格，其他情况表示净值")
                    # 如果是赎回类型（sell_confirm）或转托管类型，显示手续费编辑
                    edit_fee = None
                    if selected_record.get('action') in ['sell_confirm', 'sell', 'transfer_out', 'transfer_in']:
                        edit_fee = st.number_input(
                            "手续费", 
                            value=float(selected_record.get('fee', 0) or 0), 
                            step=0.01, 
                            key="tx_edit_fee",
                            help="赎回或转托管手续费"
                        )
                    edit_note = st.text_input("备注", value=selected_record.get('note', '') or '', key="tx_edit_note")
                
                col_save, col_delete = st.columns([3, 1])
                
                with col_save:
                    if st.button("💾 保存修改", type="primary", key="save_tx_edit", width='stretch'):
                        updated_record = {
                            'date': str(edit_date),
                            'product_code': edit_product,
                            'action': selected_record.get('action'),
                            'amount': str(edit_amount) if edit_amount else None,
                            'shares': str(edit_shares) if edit_shares else None,
                            'nav': str(edit_nav) if edit_nav else None,
                            'fee': str(edit_fee) if edit_fee is not None and edit_fee > 0 else None,
                            'note': edit_note
                        }
                        
                        # 使用联动更新，同时更新关联的记账记录
                        from core.cascade_delete import cascade_update_transaction
                        result = cascade_update_transaction(selected_record['id'], selected_record, updated_record)
                        
                        if result['transaction_updated']:
                            msg = "✅ 保存成功！"
                            if result['ledgers_updated'] > 0:
                                msg += f" 已同步更新 {result['ledgers_updated']} 条关联记账记录"
                            if result['errors']:
                                msg += f"\n⚠️ {', '.join(result['errors'])}"
                            st.success(msg)
                            st.rerun()
                        else:
                            error_msg = "❌ 保存失败"
                            if result['errors']:
                                error_msg += f": {', '.join(result['errors'])}"
                            st.error(error_msg)
                
                with col_delete:
                    if st.button("🗑️ 删除", type="secondary", key="delete_tx_edit", width='stretch'):
                        st.session_state['pending_delete_tx_id'] = selected_record['id']
                
                # 删除确认
                if st.session_state.get('pending_delete_tx_id') == selected_record['id']:
                    action_display = ACTION_DISPLAY_MAP.get(selected_record.get('action', ''), selected_record.get('action', ''))
                    st.warning(f"⚠️ 确定要删除这条记录吗？（{selected_record.get('date', '')} - {action_display} - {edit_product}）")
                    col_confirm, col_cancel = st.columns(2)
                    with col_confirm:
                        if st.button("✅ 确认删除", type="primary", key="do_delete_tx"):
                            from core.cascade_delete import cascade_delete_transaction
                            result = cascade_delete_transaction(selected_record['id'], selected_record)
                            if result['transaction_deleted']:
                                msg = "✅ 删除成功！"
                                if result['order_deleted']:
                                    msg += f" 已删除关联订单"
                                if result['related_transactions_deleted'] > 0:
                                    msg += f"，已删除 {result['related_transactions_deleted']} 条关联理财记录"
                                if result['ledgers_deleted'] > 0:
                                    msg += f"，已删除 {result['ledgers_deleted']} 条关联记账记录"
                                if result.get('trade_fills_deleted', 0) > 0:
                                    msg += f"，已删除 {result['trade_fills_deleted']} 条场内成交记录"
                                if result.get('shares_restored'):
                                    msg += f"（已恢复 {len(result['shares_restored'])} 笔份额）"
                                if result['errors']:
                                    msg += f"\n⚠️ 部分删除失败: {', '.join(result['errors'])}"
                                st.success(msg)
                                st.session_state.pop('pending_delete_tx_id', None)
                                st.rerun()
                            else:
                                error_msg = "❌ 删除失败"
                                if result['errors']:
                                    error_msg += f": {', '.join(result['errors'])}"
                                st.error(error_msg)
                    with col_cancel:
                        if st.button("❌ 取消", key="cancel_delete_tx"):
                            st.session_state.pop('pending_delete_tx_id', None)
                            st.rerun()
        else:
            st.info("暂无匹配的理财记录")
    else:
        st.info("暂无理财记录")


# ============================================================
# Page 4: 订单管理
# ============================================================
def page_orders():
    st.markdown('<p class="main-header">📋 订单管理</p>', unsafe_allow_html=True)
    
    # 全部订单
    st.subheader("📋 全部订单")
    
    all_orders = list_all_orders()
    
    if all_orders:
        # 筛选器
        status_filter = st.selectbox(
            "状态筛选", 
            ["全部", "pending", "done", "cancelled"],
            format_func=lambda x: {"全部": "全部", "pending": "待处理", "done": "已完成", "cancelled": "已取消"}.get(x, x),
            key="order_status_filter"
        )
        
        if status_filter != "全部":
            all_orders = [o for o in all_orders if o.get('status') == status_filter]
        
        # 构建显示数据（与待结算订单保持一致）
        rows = []
        for order in all_orders:  # 显示全部，用分页控制
            product_code = order.get('product_code', '')
            order_type = order.get('order_type', '')
            status = order.get('status', '')
            
            # 已完成的订单，从交易记录中获取份额、净值和金额
            display_shares = order.get('shares', '') or ''
            display_nav = '-'  # 初始化为字符串，避免类型不一致
            display_amount = ''  # 初始化为空，后续从交易记录或订单中获取
            
            if status == 'done':
                # 从 transactions 中查找对应的 buy_confirm 或 sell_confirm 记录
                from core.invest_service import list_recent_transactions
                order_id = order.get('order_id', '')
                txs = list_recent_transactions(100)
                for tx in txs:
                    if tx.get('order_id') == order_id and tx.get('action') in ['buy_confirm', 'sell_confirm']:
                        display_shares = tx.get('shares', '') or display_shares
                        # 处理净值列，确保类型一致（避免 Arrow 序列化错误）
                        nav_value = tx.get('nav', '')
                        if nav_value:
                            try:
                                display_nav = f"{float(nav_value):.4f}"
                            except (ValueError, TypeError):
                                display_nav = str(nav_value) if nav_value else '-'
                        else:
                            display_nav = '-'
                        # 对于赎回订单（sell_confirm），从交易记录中获取金额
                        if tx.get('action') == 'sell_confirm':
                            amount_value = tx.get('amount', '')
                            if amount_value:
                                try:
                                    display_amount = f"{float(amount_value):.2f}"
                                except (ValueError, TypeError):
                                    display_amount = str(amount_value) if amount_value else '-'
                        break
            elif status == 'pending':
                # 待处理的订单，预览计算份额
                preview = preview_settle(order['order_id'])
                if preview.get('success') and preview.get('shares'):
                    display_shares = f"{preview.get('shares'):.2f}"
                    # 处理净值列，确保类型一致（避免 Arrow 序列化错误）
                    nav_value = preview.get('nav', None)
                    display_nav = f"{nav_value:.4f}" if nav_value is not None else '-'
                else:
                    display_nav = '-'
            else:
                display_nav = '-'
            
            # 格式化份额（两位小数）
            if display_shares and display_shares != '-':
                try:
                    display_shares = f"{float(display_shares):.2f}"
                except:
                    pass
            
            # 格式化金额（两位小数）
            # 如果还没有从交易记录中获取到金额，则从订单中获取
            if not display_amount:
                orig_amount = order.get('amount', '')
                display_amount = f"{float(orig_amount):.2f}" if orig_amount else '-'
            
            # 状态中文化
            status_map = {'pending': '待处理', 'done': '已完成', 'cancelled': '已取消'}
            
            rows.append({
                '订单号': order.get('order_id', ''),
                '产品代码': product_code,
                '类型': '买入扣款' if order_type == 'buy_debit' else ('赎回发起' if order_type == 'redeem_request' else order_type),
                '金额': display_amount,
                '份额': display_shares or '-',
                '确认日期': order.get('confirm_date', ''),
                '状态': status_map.get(status, status),
                '净值': display_nav,
                '备注': order.get('note', '')
            })
        
        df_all = pd.DataFrame(rows)
        # 分页
        df_page = paginate_dataframe(df_all, "all_orders", page_size=50)
        
        # 选择订单进行删除或重新结算
        if len(df_page) > 0:
            st.caption("💡 选择订单可以删除或重新结算")
            
            tab_delete, tab_resettle = st.tabs(["🗑️ 删除订单", "🔄 重新结算"])
            
            with tab_delete:
                selected_indices_delete = st.multiselect(
                    "选择要删除的订单",
                    options=df_page.index.tolist(),
                    format_func=lambda idx: f"{df_page.loc[idx, '订单号']} - {df_page.loc[idx, '产品代码']} - {df_page.loc[idx, '状态']}",
                    key="delete_order_select"
                )
                
                if selected_indices_delete:
                    selected_orders_delete = []
                    for idx in selected_indices_delete:
                        order_row = df_page.loc[idx]
                        order_id = order_row['订单号']
                        # 从原始订单列表中找到对应的订单
                        for order in all_orders:
                            if order.get('order_id') == order_id:
                                selected_orders_delete.append(order)
                                break
                    
                    if selected_orders_delete:
                        st.warning(f"⚠️ 将删除 {len(selected_orders_delete)} 个订单。这将：\n1. 删除订单本身\n2. 删除对应的理财记录（buy_debit/buy_confirm 或 redeem_request/sell_confirm）\n3. 删除对应的记账记录")
                        
                        if st.button("🗑️ 确认删除", type="primary", key="do_delete_orders"):
                            from core.cascade_delete import cascade_delete_order
                            
                            success_count = 0
                            error_count = 0
                            total_shares_restored = 0
                            
                            for order in selected_orders_delete:
                                order_id = order.get('order_id')
                                try:
                                    result = cascade_delete_order(order_id)
                                    if result['order_deleted']:
                                        success_count += 1
                                        shares_count = len(result.get('shares_restored', []))
                                        total_shares_restored += shares_count
                                        msg = f"✅ 订单 {order_id} 已删除"
                                        if result['transactions_deleted'] > 0 or result['ledgers_deleted'] > 0:
                                            msg += f"，同时删除了 {result['transactions_deleted']} 条理财记录和 {result['ledgers_deleted']} 条记账记录"
                                        if shares_count > 0:
                                            msg += f"（已恢复 {shares_count} 笔份额）"
                                        st.info(msg)
                                    else:
                                        error_count += 1
                                        if result['errors']:
                                            st.error(f"❌ 删除订单失败 {order_id}: {', '.join(result['errors'])}")
                                        else:
                                            st.error(f"❌ 删除订单失败: {order_id}")
                                except Exception as e:
                                    error_count += 1
                                    st.error(f"❌ 删除订单异常 {order_id}: {e}")
                            
                            if success_count > 0:
                                msg = f"✅ 成功删除 {success_count} 个订单！"
                                if total_shares_restored > 0:
                                    msg += f"（共恢复 {total_shares_restored} 笔份额）"
                                st.success(msg)
                                # 刷新快照
                                try:
                                    from core.snapshot_service import collect_nav_and_build_snapshots
                                    collect_nav_and_build_snapshots(silent=True)
                                except:
                                    pass
                                st.rerun()
                            else:
                                st.error(f"❌ 删除失败 {error_count} 个订单")
            
            with tab_resettle:
                selected_indices = st.multiselect(
                    "选择要重新结算的订单（仅限已完成状态）",
                    options=df_page.index.tolist(),
                    format_func=lambda idx: f"{df_page.loc[idx, '订单号']} - {df_page.loc[idx, '产品代码']}",
                    key="resettle_order_select"
                )
            
            if selected_indices:
                selected_orders = []
                for idx in selected_indices:
                    order_row = df_page.loc[idx]
                    order_id = order_row['订单号']
                    # 从原始订单列表中找到对应的订单
                    for order in all_orders:
                        if order.get('order_id') == order_id and order.get('status') == 'done':
                            selected_orders.append(order)
                            break
                
                if selected_orders:
                    st.warning(f"⚠️ 将重新结算 {len(selected_orders)} 个订单。这将：\n1. 删除对应的 buy_confirm/sell_confirm 交易记录\n2. 将订单状态重置为 pending\n3. 然后可以重新结算")
                    
                    if st.button("🔄 确认重新结算", type="primary", key="do_resettle"):
                        from data.data_store import delete_transaction, update_order_status
                        from core.invest_service import list_recent_transactions
                        from core.cascade_delete import restore_shares_for_order_reset
                        
                        success_count = 0
                        error_count = 0
                        shares_restored_count = 0
                        
                        for order in selected_orders:
                            order_id = order.get('order_id')
                            try:
                                # 0. 先恢复份额（在删除记录之前）
                                restore_result = restore_shares_for_order_reset(order_id)
                                if restore_result['shares_restored']:
                                    shares_restored_count += len(restore_result['shares_restored'])
                                
                                # 1. 查找并删除对应的确认交易记录
                                txs = list_recent_transactions(1000)  # 获取更多记录
                                deleted = False
                                for tx in txs:
                                    if tx.get('order_id') == order_id and tx.get('action') in ['buy_confirm', 'sell_confirm']:
                                        tx_id = tx.get('id')
                                        if tx_id and delete_transaction(tx_id):
                                            deleted = True
                                
                                if deleted:
                                    # 2. 将订单状态重置为 pending
                                    if update_order_status(order_id, 'pending'):
                                        success_count += 1
                                    else:
                                        error_count += 1
                                        st.error(f"❌ 重置订单状态失败: {order_id}")
                                else:
                                    error_count += 1
                                    st.warning(f"⚠️ 未找到确认记录: {order_id}")
                            except Exception as e:
                                error_count += 1
                                st.error(f"❌ 处理订单失败 {order_id}: {e}")
                        
                        if success_count > 0:
                            msg = f"✅ 成功重置 {success_count} 个订单，现在可以重新结算了！"
                            if shares_restored_count > 0:
                                msg += f"（已恢复 {shares_restored_count} 笔份额）"
                            st.success(msg)
                            # 刷新快照
                            try:
                                from core.snapshot_service import collect_nav_and_build_snapshots
                                collect_nav_and_build_snapshots(silent=True)
                            except:
                                pass
                            st.rerun()
                        else:
                            st.error(f"❌ 重置失败 {error_count} 个订单")
        
        st.dataframe(df_page, width='stretch', hide_index=True)
        
        # 订单编辑功能
        st.divider()
        st.subheader("✏️ 编辑订单")
        
        # 选择要编辑的订单
        order_options = [(o.get('order_id', ''), f"{o.get('order_id', '')} - {o.get('product_code', '')} - {'买入' if o.get('order_type') == 'buy_debit' else '赎回'}") for o in all_orders]
        
        if order_options:
            # 使用 session_state 来跟踪当前选择的订单，确保选择变化时表单内容更新
            if 'edit_order_selected_id' not in st.session_state:
                st.session_state['edit_order_selected_id'] = order_options[0][0] if order_options else None
            
            # 计算默认索引
            default_index = 0
            saved_id = st.session_state.get('edit_order_selected_id')
            if saved_id:
                for i, o in enumerate(order_options):
                    if o[0] == saved_id:
                        default_index = i
                        break
            
            selected_order_id = st.selectbox(
                "选择要编辑的订单",
                options=[o[0] for o in order_options],
                format_func=lambda x: next((o[1] for o in order_options if o[0] == x), x),
                key="edit_order_select",
                index=default_index
            )
            
            # 更新 session_state
            if selected_order_id != st.session_state.get('edit_order_selected_id'):
                st.session_state['edit_order_selected_id'] = selected_order_id
                # 清空相关的输入框状态，强制重新渲染
                for key in list(st.session_state.keys()):
                    if key.startswith('order_edit_'):
                        del st.session_state[key]
            
            if selected_order_id:
                # 找到选中的订单
                selected_order = None
                for order in all_orders:
                    if order.get('order_id') == selected_order_id:
                        selected_order = order
                        break
                
                if selected_order:
                    order_type = selected_order.get('order_type', '')
                    status = selected_order.get('status', '')
                    
                    # 使用订单ID作为key的一部分，确保每个订单的输入框是独立的
                    key_suffix = f"_{selected_order_id}"
                    
                    col1, col2 = st.columns(2)
                    
                    with col1:
                        # 只读信息
                        st.text_input("订单号", value=selected_order_id, disabled=True, key=f"order_edit_id{key_suffix}")
                        st.text_input("产品代码", value=selected_order.get('product_code', ''), disabled=True, key=f"order_edit_product{key_suffix}")
                        st.text_input("订单类型", value='买入扣款' if order_type == 'buy_debit' else '赎回发起', disabled=True, key=f"order_edit_type{key_suffix}")
                        st.text_input("状态", value={'pending': '待处理', 'done': '已完成', 'cancelled': '已取消'}.get(status, status), disabled=True, key=f"order_edit_status{key_suffix}")
                    
                    with col2:
                        # 可编辑字段 - 使用订单ID作为key的一部分
                        # 净值日期
                        nav_date_str = selected_order.get('nav_date', '')
                        if nav_date_str:
                            try:
                                nav_date_value = datetime.strptime(nav_date_str, '%Y-%m-%d').date()
                            except:
                                nav_date_value = datetime.now().date()
                        else:
                            nav_date_value = datetime.now().date()
                        
                        edit_nav_date = st.date_input(
                            "净值日期",
                            value=nav_date_value,
                            key=f"order_edit_nav_date{key_suffix}"
                        )
                        
                        # 确认日期
                        confirm_date_str = selected_order.get('confirm_date', '')
                        if confirm_date_str:
                            try:
                                confirm_date_value = datetime.strptime(confirm_date_str, '%Y-%m-%d').date()
                            except:
                                confirm_date_value = datetime.now().date()
                        else:
                            confirm_date_value = datetime.now().date()
                        
                        edit_confirm_date = st.date_input(
                            "确认日期",
                            value=confirm_date_value,
                            key=f"order_edit_confirm_date{key_suffix}"
                        )
                        
                        if order_type == 'buy_debit':
                            edit_amount = st.number_input(
                                "金额",
                                value=float(selected_order.get('amount', 0) or 0),
                                step=0.01,
                                key=f"order_edit_amount{key_suffix}"
                            )
                            edit_shares = None
                        else:
                            edit_shares = st.number_input(
                                "份额",
                                value=float(selected_order.get('shares', 0) or 0),
                                step=0.01,
                                format="%.4f",
                                key=f"order_edit_shares{key_suffix}"
                            )
                            edit_amount = None
                        
                        edit_note = st.text_input("备注", value=selected_order.get('note', '') or '', key=f"order_edit_note{key_suffix}")
                    
                    # 保存按钮
                    if st.button("💾 保存订单修改", type="primary", key="save_order_edit"):
                        # 构建更新数据
                        updated_fields = {
                            'nav_date': str(edit_nav_date),
                            'confirm_date': str(edit_confirm_date),
                        }
                        
                        if edit_amount is not None:
                            updated_fields['amount'] = str(edit_amount)
                        if edit_shares is not None:
                            updated_fields['shares'] = str(edit_shares)
                        
                        # 只有备注有变化才更新
                        original_note = selected_order.get('note', '') or ''
                        # 提取原始备注（不含账户信息）
                        clean_original_note = original_note
                        for sep in ['|redeem_account:', '|redeem_from_account:', '|redeem_from_accounts:', '|redeem_supplement_accounts:', '|fee_override:']:
                            if sep in clean_original_note:
                                clean_original_note = clean_original_note.split(sep)[0]
                        
                        if edit_note != clean_original_note:
                            # 保留原始的账户信息部分
                            account_info = ''
                            for sep in ['|redeem_account:', '|redeem_from_account:', '|redeem_from_accounts:', '|redeem_supplement_accounts:', '|fee_override:']:
                                if sep in original_note:
                                    idx = original_note.find(sep)
                                    account_info = original_note[idx:]
                                    break
                            updated_fields['note'] = edit_note + account_info
                        
                        # 使用联动更新
                        from core.cascade_delete import cascade_update_order
                        result = cascade_update_order(selected_order_id, selected_order, updated_fields)
                        
                        if result['order_updated']:
                            msg = "✅ 订单保存成功！"
                            if result['transactions_updated'] > 0:
                                msg += f" 已同步更新 {result['transactions_updated']} 条理财记录"
                            if result['ledgers_updated'] > 0:
                                msg += f"，{result['ledgers_updated']} 条记账记录"
                            if result['errors']:
                                msg += f"\n⚠️ {', '.join(result['errors'])}"
                            st.success(msg)
                            st.rerun()
                        else:
                            error_msg = "❌ 保存失败"
                            if result['errors']:
                                error_msg += f": {', '.join(result['errors'])}"
                            st.error(error_msg)
    else:
        st.info("暂无订单")


# ============================================================
# 产品管理页面
# ============================================================
def page_product_management():
    st.title("🏷️ 产品管理")
    
    from data.product_service import get_products, get_product_by_id, create_product, update_product, delete_product
    
    tab1, tab2 = st.tabs(["📋 产品列表", "➕ 新增产品"])
    
    with tab1:
        st.subheader("产品列表")
        
        # 筛选选项
        col1, col2, col3 = st.columns(3)
        with col1:
            channel_filter = st.selectbox("渠道筛选", ["全部", "场内", "场外"], key="prod_channel")
        with col2:
            asset_type_filter = st.selectbox("资产类型", ["全部", "ETF", "LOF", "FUND", "MMF", "BANK_WM_NAV"], key="prod_asset")
        with col3:
            show_inactive = st.checkbox("显示已停用", key="prod_show_inactive")
        
        # 获取产品列表
        channel = None if channel_filter == "全部" else ("EXCHANGE" if channel_filter == "场内" else "OTC")
        asset_type = None if asset_type_filter == "全部" else asset_type_filter
        products = get_products(channel=channel, asset_type=asset_type, is_active=not show_inactive)
        
        if products:
            # 构建显示数据
            display_data = []
            for p in products:
                display_data.append({
                    'ID': p.get('id'),
                    '代码': p.get('code', ''),
                    '名称': p.get('name') or p.get('product_name', ''),
                    '渠道': p.get('channel', 'OTC'),
                    '市场': p.get('market', 'NA'),
                    '类型': p.get('asset_type', 'FUND'),
                    'QDII': '是' if p.get('is_qdii') else '否',
                    '申购费率': f"{float(p.get('buy_fee_rate', 0)) * 100:.4f}%",
                    '赎回费率': f"{float(p.get('sell_fee_rate', 0)) * 100:.4f}%",
                    '状态': '启用' if p.get('is_active', 1) else '停用'
                })
            
            df = pd.DataFrame(display_data)
            
            # 选择要编辑的产品（使用产品ID作为选项值，确保唯一性）
            product_options = {p.get('id'): format_product_display_name(p) for p in products}
            selected_product_id = st.selectbox("选择产品进行编辑", 
                                             options=list(product_options.keys()),
                                             format_func=lambda x: product_options[x],
                                             key="select_product_edit")
            
            if selected_product_id:
                # 根据ID查找产品
                product = next((p for p in products if p.get('id') == selected_product_id), None)
                
                if product:
                    st.divider()
                    st.subheader(f"编辑产品: {product.get('code', '')} - {product.get('name') or product.get('product_name', '')}")
                    
                    # 使用产品ID作为key的一部分，确保每个产品的输入框是独立的
                    edit_key_suffix = f"_{selected_product_id}"
                    
                    col1, col2 = st.columns(2)
                    with col1:
                        new_name = st.text_input("产品名称", 
                                                value=product.get('name') or product.get('product_name', ''), 
                                                key=f"edit_name{edit_key_suffix}")
                        buy_fee_rate = float(product.get('buy_fee_rate') or 0) * 100
                        new_buy_fee = st.number_input("申购费率 (%)", 
                                                      value=buy_fee_rate, 
                                                      min_value=0.0, max_value=10.0, step=0.0001, 
                                                      key=f"edit_buy_fee{edit_key_suffix}")
                        sell_fee_rate = float(product.get('sell_fee_rate') or 0) * 100
                        new_sell_fee = st.number_input("赎回费率 (%)", 
                                                      value=sell_fee_rate, 
                                                      min_value=0.0, max_value=10.0, step=0.0001, 
                                                      key=f"edit_sell_fee{edit_key_suffix}")
                    with col2:
                        new_is_qdii = st.checkbox("是否QDII", 
                                                 value=bool(product.get('is_qdii')), 
                                                 key=f"edit_qdii{edit_key_suffix}")
                        new_is_active = st.checkbox("是否启用", 
                                                    value=bool(product.get('is_active', 1)), 
                                                    key=f"edit_active{edit_key_suffix}")
                        new_track_index = st.text_input("跟踪指数", 
                                                        value=product.get('track_index') or '', 
                                                        key=f"edit_index{edit_key_suffix}")
                    
                    if st.button("💾 保存修改", key=f"save_product_{selected_product_id}"):
                        try:
                            update_data = {
                                'product_name': new_name,
                                'buy_fee_rate': new_buy_fee / 100,
                                'sell_fee_rate': new_sell_fee / 100,
                                'is_qdii': 1 if new_is_qdii else 0,
                                'is_active': 1 if new_is_active else 0,
                                'track_index': new_track_index if new_track_index else None
                            }
                            update_product(selected_product_id, update_data)
                            st.success("✅ 产品更新成功！")
                            st.rerun()
                        except Exception as e:
                            st.error(f"❌ 更新失败: {e}")
                    
                    if st.button("🗑️ 删除产品（软删除）", key=f"delete_product_{selected_product_id}"):
                        try:
                            delete_product(selected_product_id)
                            st.success("✅ 产品已停用！")
                            st.rerun()
                        except Exception as e:
                            st.error(f"❌ 删除失败: {e}")
                    
                    # 策略绑定编辑（仅场内产品）
                    if product.get('channel') == 'EXCHANGE':
                        st.divider()
                        st.subheader("📊 策略绑定配置")
                        try:
                            from advisor.repos.product_strategy_bind_repo import get_binds_by_product_id, create_or_update_bind
                            from data.db_connector import execute_query, execute_update
                            
                            # 获取所有已绑定的策略
                            binds = get_binds_by_product_id(selected_product_id)
                            
                            # 显示已绑定的策略列表
                            if binds:
                                st.markdown("**已绑定的策略：**")
                                for i, bind in enumerate(binds):
                                    strategy_code = bind.get('strategy_code', '')
                                    param_set_id = bind.get('param_set_id', 'default')
                                    strategy_type = bind.get('strategy_type', 'TRIGGER')
                                    priority = bind.get('priority', 0)
                                    type_label = {'VETO': '否决', 'TRIGGER': '触发', 'SCORE': '强度'}.get(strategy_type, strategy_type)
                                    
                                    # 获取策略参数详情
                                    from advisor.advisor_service import get_strategy_config
                                    param_config = get_strategy_config(strategy_code, param_set_id)
                                    
                                    with st.expander(f"{i+1}. {strategy_code}@{param_set_id} ({type_label}, 优先级:{priority})", expanded=False):
                                        col_info, col_action = st.columns([3, 1])
                                        with col_info:
                                            st.markdown(f"**策略代码**: {strategy_code}")
                                            st.markdown(f"**参数集ID**: {param_set_id}")
                                            st.markdown(f"**策略类型**: {type_label}")
                                            st.markdown(f"**优先级**: {priority}")
                                            min_trade = float(bind.get('min_trade_amount', 1000) or 1000)
                                            ideal_trade = float(bind.get('ideal_trade_amount', 2000) or 2000)
                                            fee_rate_val = float(bind.get('fee_rate', 0.000845) or 0.000845)
                                            fee_min_val = float(bind.get('fee_min', 0.20) or 0.20)
                                            
                                            st.markdown(f"**最小成交额**: ¥{min_trade:.2f}")
                                            st.markdown(f"**理想成交额**: ¥{ideal_trade:.2f}")
                                            st.markdown(f"**手续费率**: {fee_rate_val*10000:.2f}‱")
                                            st.markdown(f"**最低手续费**: ¥{fee_min_val:.2f}")
                                            
                                            # 显示参数详情
                                            if param_config:
                                                st.markdown("**参数配置**:")
                                                st.json(param_config)
                                            else:
                                                st.warning("⚠️ 未找到参数配置")
                                        
                                        with col_action:
                                            # 删除按钮
                                            if st.button("🗑️ 删除绑定", key=f"delete_bind_{selected_product_id}_{bind.get('id')}", type="secondary"):
                                                try:
                                                    execute_update(
                                                        "UPDATE product_strategy_bind SET enabled = 0 WHERE id = %s",
                                                        (bind.get('id'),)
                                                    )
                                                    st.success("✅ 策略绑定已删除！")
                                                    st.rerun()
                                                except Exception as e:
                                                    st.error(f"❌ 删除失败: {e}")
                            
                            st.divider()
                            st.markdown("**新增策略绑定：**")
                            
                            # 获取策略列表（包含dca_4pct）
                            strategy_options = ['percentile', 'drawdown', 'profit_recycle', 'simple', 'dca_4pct']
                            
                            col1, col2, col3 = st.columns(3)
                            with col1:
                                new_strategy_code = st.selectbox(
                                    "策略代码",
                                    options=strategy_options,
                                    key=f"bind_strategy_{selected_product_id}"
                                )
                                
                                # 获取参数集列表
                                param_sets = []
                                if new_strategy_code:
                                    sql = """
                                        SELECT DISTINCT param_set_id
                                        FROM strategy_config
                                        WHERE strategy_key = %s AND is_active = 1
                                        ORDER BY param_set_id
                                    """
                                    param_rows = execute_query(sql, (new_strategy_code,))
                                    param_sets = [row['param_set_id'] for row in param_rows] if param_rows else ['default']
                            
                            with col2:
                                if param_sets:
                                    new_param_set_id = st.selectbox(
                                        "参数集ID",
                                        options=param_sets,
                                        key=f"bind_param_{selected_product_id}"
                                    )
                                else:
                                    new_param_set_id = st.text_input(
                                        "参数集ID",
                                        value='default',
                                        key=f"bind_param_{selected_product_id}"
                                    )
                                
                                # 策略类型选择
                                strategy_type_options = ['VETO', 'TRIGGER', 'SCORE']
                                new_strategy_type = st.selectbox(
                                    "策略类型",
                                    options=strategy_type_options,
                                    index=1,  # 默认TRIGGER
                                    key=f"bind_type_{selected_product_id}",
                                    help="VETO=否决层（任一命中即拒绝），TRIGGER=触发层（任一命中即考虑），SCORE=强度层（决定买入金额）"
                                )
                            
                            with col3:
                                new_priority = st.number_input(
                                    "优先级",
                                    value=0,
                                    min_value=0,
                                    step=1,
                                    key=f"bind_priority_{selected_product_id}",
                                    help="数字越小越优先，同层内按此排序"
                                )
                            
                            col1, col2, col3, col4 = st.columns(4)
                            with col1:
                                new_min_trade = st.number_input(
                                    "最小成交额",
                                    value=1000.0,
                                    min_value=0.0,
                                    step=100.0,
                                    key=f"bind_min_trade_{selected_product_id}"
                                )
                            with col2:
                                new_ideal_trade = st.number_input(
                                    "理想成交额",
                                    value=2000.0,
                                    min_value=0.0,
                                    step=100.0,
                                    key=f"bind_ideal_trade_{selected_product_id}"
                                )
                            with col3:
                                new_fee_rate = st.number_input(
                                    "手续费率",
                                    value=0.000845,
                                    min_value=0.0,
                                    max_value=1.0,
                                    step=0.000001,
                                    format="%.6f",
                                    key=f"bind_fee_rate_{selected_product_id}"
                                )
                            with col4:
                                new_fee_min = st.number_input(
                                    "最低手续费",
                                    value=0.20,
                                    min_value=0.0,
                                    step=0.01,
                                    key=f"bind_fee_min_{selected_product_id}"
                                )
                            
                            if st.button("💾 保存策略绑定", key=f"save_bind_{selected_product_id}"):
                                try:
                                    # 检查 param_set_id 是否发生变化
                                    old_binds = get_binds_by_product_id(selected_product_id)
                                    old_param_set_id = None
                                    if old_binds and len(old_binds) > 0:
                                        # 检查是否有相同 strategy_code 的绑定
                                        for old_bind in old_binds:
                                            if old_bind.get('strategy_code') == new_strategy_code:
                                                old_param_set_id = old_bind.get('param_set_id')
                                                break
                                    
                                    bind_data = {
                                        'product_id': selected_product_id,
                                        'strategy_code': new_strategy_code,
                                        'param_set_id': new_param_set_id,
                                        'enabled': 1,
                                        'strategy_type': new_strategy_type,
                                        'priority': new_priority,
                                        'min_trade_amount': new_min_trade,
                                        'ideal_trade_amount': new_ideal_trade,
                                        'fee_rate': new_fee_rate,
                                        'fee_min': new_fee_min
                                    }
                                    create_or_update_bind(bind_data)
                                    
                                    # 如果 param_set_id 发生变化，立即触发指标重算
                                    if old_param_set_id and old_param_set_id != new_param_set_id:
                                        from advisor.indicator_job import calculate_indicators_for_product
                                        with st.spinner("参数集已变更，正在重新计算指标..."):
                                            calculate_indicators_for_product(selected_product_id)
                                        st.info(f"✅ 参数集已从 {old_param_set_id} 切换到 {new_param_set_id}，指标已重新计算")
                                    
                                    st.success("✅ 策略绑定保存成功！")
                                    st.rerun()
                                except Exception as e:
                                    st.error(f"❌ 保存失败: {e}")
                        except Exception as e:
                            st.warning(f"策略绑定功能加载失败: {e}")
            
            st.dataframe(df, width='stretch', hide_index=True)
        else:
            st.info("暂无产品")
    
    with tab2:
        st.subheader("新增产品")
        
        col1, col2 = st.columns(2)
        with col1:
            new_code = st.text_input("产品代码 *", key="new_code")
            new_name = st.text_input("产品名称 *", key="new_name")
            new_channel = st.selectbox("渠道 *", ["OTC", "EXCHANGE"], key="new_channel")
            new_market = st.selectbox("市场", ["NA", "SH", "SZ"], key="new_market", index=0)
            new_asset_type = st.selectbox("资产类型 *", ["FUND", "ETF", "LOF", "MMF", "BANK_WM_NAV", "BANK_WM_BOX"], key="new_asset")
        with col2:
            new_currency = st.selectbox("货币", ["CNY", "USD", "HKD"], key="new_currency", index=0)
            new_is_qdii = st.checkbox("是否QDII", key="new_qdii")
            new_track_index = st.text_input("跟踪指数", key="new_track_index")
            new_buy_fee = st.number_input("申购费率 (%)", value=0.0, min_value=0.0, max_value=10.0, step=0.0001, key="new_buy_fee")
            new_sell_fee = st.number_input("赎回费率 (%)", value=0.0, min_value=0.0, max_value=10.0, step=0.0001, key="new_sell_fee")
        
        if st.button("➕ 创建产品", key="create_product"):
            if not new_code or not new_name:
                st.error("❌ 产品代码和名称不能为空")
            else:
                try:
                    product_data = {
                        'code': new_code,
                        'product_name': new_name,
                        'channel': new_channel,
                        'market': new_market,
                        'asset_type': new_asset_type,
                        'currency': new_currency,
                        'is_qdii': 1 if new_is_qdii else 0,
                        'track_index': new_track_index if new_track_index else None,
                        'buy_fee_rate': new_buy_fee / 100,
                        'sell_fee_rate': new_sell_fee / 100,
                        'category': 'fund' if new_asset_type in ['FUND', 'ETF', 'LOF', 'MMF'] else 'bank',
                        'source': 'fund' if new_asset_type in ['FUND', 'ETF', 'LOF', 'MMF'] else 'bank'
                    }
                    product_id = create_product(product_data)
                    st.success(f"✅ 产品创建成功！ID: {product_id}")
                    st.rerun()
                except Exception as e:
                    st.error(f"❌ 创建失败: {e}")


# ============================================================
# 账户管理页面
# ============================================================
def page_account_management():
    st.title("💳 账户管理")
    
    from data.account_service import get_accounts, get_account_by_id, create_account, update_account, delete_account
    from data.product_service import get_products
    
    tab1, tab2 = st.tabs(["📋 账户列表", "➕ 新增账户"])
    
    with tab1:
        st.subheader("账户列表")
        
        # 筛选选项
        account_type_filter = st.selectbox("账户类型筛选", ["全部", "CASH", "BUCKET", "FUND_MAPPED", "PRODUCT_SUB", "FUND_TOTAL", "SUMMARY"], key="acc_type")
        show_inactive = st.checkbox("显示已停用", key="acc_show_inactive")
        
        # 获取账户列表
        account_type = None if account_type_filter == "全部" else account_type_filter
        accounts = get_accounts(account_type=account_type, is_active=not show_inactive)
        
        if accounts:
            # 获取产品映射
            products = {p['id']: p for p in get_products()}
            
            # 构建显示数据
            display_data = []
            for a in accounts:
                product_name = ""
                if a.get('product_id'):
                    product = products.get(a['product_id'])
                    if product:
                        product_name = f"{product.get('code', '')} - {product.get('name') or product.get('product_name', '')}"
                
                display_data.append({
                    'ID': a.get('id'),
                    '账户代码': a.get('account_code', ''),
                    '账户名称': a.get('account_name', ''),
                    '账户类型': a.get('account_type', ''),
                    '关联产品': product_name,
                    '货币': a.get('currency', 'CNY'),
                    '状态': '启用' if a.get('is_active', 1) else '停用'
                })
            
            df = pd.DataFrame(display_data)
            
            # 选择要编辑的账户（使用账户ID作为选项值，确保唯一性）
            account_options = {a.get('id'): f"{a.get('account_code', '')} - {a.get('account_name', '')}" for a in accounts}
            selected_account_id = st.selectbox("选择账户进行编辑", 
                                             options=list(account_options.keys()),
                                             format_func=lambda x: account_options[x],
                                             key="select_account_edit")
            
            if selected_account_id:
                # 根据ID查找账户
                account = next((a for a in accounts if a.get('id') == selected_account_id), None)
                
                if account:
                    st.divider()
                    st.subheader(f"编辑账户: {account.get('account_code', '')} - {account.get('account_name', '')}")
                    
                    col1, col2 = st.columns(2)
                    with col1:
                        # 使用账户ID作为key的一部分，确保每个账户的输入框是独立的
                        edit_key_suffix = f"_{selected_account_id}"
                        new_name = st.text_input("账户名称", value=account.get('account_name', ''), key=f"edit_acc_name{edit_key_suffix}")
                        
                        account_type_list = ["CASH", "BUCKET", "FUND_MAPPED", "PRODUCT_SUB", "FUND_TOTAL", "SUMMARY"]
                        current_type = account.get('account_type', 'CASH')
                        type_index = account_type_list.index(current_type) if current_type in account_type_list else 0
                        new_type = st.selectbox("账户类型", account_type_list, index=type_index, key=f"edit_acc_type{edit_key_suffix}")
                    with col2:
                        currency_list = ["CNY", "USD", "HKD"]
                        current_currency = account.get('currency', 'CNY')
                        currency_index = currency_list.index(current_currency) if current_currency in currency_list else 0
                        new_currency = st.selectbox("货币", currency_list, index=currency_index, key=f"edit_acc_currency{edit_key_suffix}")
                        new_is_active = st.checkbox("是否启用", value=bool(account.get('is_active', 1)), key=f"edit_acc_active{edit_key_suffix}")
                    
                    # 产品选择
                    all_products = get_products()
                    product_options = {0: "无"}
                    for p in all_products:
                        product_options[p['id']] = format_product_display_name(p)
                    
                    current_product_id = account.get('product_id') or 0
                    product_keys = list(product_options.keys())
                    product_index = product_keys.index(current_product_id) if current_product_id in product_keys else 0
                    selected_product_id = st.selectbox("关联产品", 
                                                     options=product_keys,
                                                     format_func=lambda x: product_options[x],
                                                     index=product_index,
                                                     key=f"edit_acc_product{edit_key_suffix}")
                    
                    if st.button("💾 保存修改", key=f"save_account_{selected_account_id}"):
                        try:
                            update_data = {
                                'account_name': new_name,
                                'account_type': new_type,
                                'currency': new_currency,
                                'is_active': 1 if new_is_active else 0,
                                'product_id': selected_product_id if selected_product_id else None
                            }
                            update_account(selected_account_id, update_data)
                            st.success("✅ 账户更新成功！")
                            st.rerun()
                        except Exception as e:
                            st.error(f"❌ 更新失败: {e}")
                    
                    if st.button("🗑️ 删除账户（软删除）", key=f"delete_account_{selected_account_id}"):
                        try:
                            delete_account(selected_account_id)
                            st.success("✅ 账户已停用！")
                            st.rerun()
                        except Exception as e:
                            st.error(f"❌ 删除失败: {e}")
            
            st.dataframe(df, width='stretch', hide_index=True)
        else:
            st.info("暂无账户")
    
    with tab2:
        st.subheader("新增账户")
        
        col1, col2 = st.columns(2)
        with col1:
            new_code = st.text_input("账户代码 *", key="new_acc_code")
            new_name = st.text_input("账户名称 *", key="new_acc_name")
            new_type = st.selectbox("账户类型 *", ["CASH", "BUCKET", "FUND_MAPPED", "PRODUCT_SUB", "FUND_TOTAL", "SUMMARY"], key="new_acc_type")
        with col2:
            new_currency = st.selectbox("货币", ["CNY", "USD", "HKD"], key="new_acc_currency", index=0)
        
        # 产品选择
        all_products = get_products()
        product_options = {0: "无"}
        for p in all_products:
            product_options[p['id']] = format_product_display_name(p)
        
        selected_product_id = st.selectbox("关联产品", options=list(product_options.keys()), 
                                         format_func=lambda x: product_options[x],
                                         key="new_acc_product")
        
        if st.button("➕ 创建账户", key="create_account"):
            if not new_code or not new_name:
                st.error("❌ 账户代码和名称不能为空")
            else:
                try:
                    account_data = {
                        'account_code': new_code,
                        'account_name': new_name,
                        'account_type': new_type,
                        'currency': new_currency,
                        'product_id': selected_product_id if selected_product_id else None
                    }
                    account_id = create_account(account_data)
                    st.success(f"✅ 账户创建成功！ID: {account_id}")
                    st.rerun()
                except Exception as e:
                    st.error(f"❌ 创建失败: {e}")


# ============================================================
# 资金池规则页面
# ============================================================
def page_pool_rules():
    st.title("💰 资金池规则")
    
    from data.account_service import load_account_pool_rules, get_account_pool_rule, add_account_pool_rule, update_account_pool_rule, delete_account_pool_rule, get_accounts
    from data.product_service import get_products
    
    tab1, tab2 = st.tabs(["📋 规则列表", "➕ 新增规则"])
    
    with tab1:
        st.subheader("资金池规则列表")
        
        rules = load_account_pool_rules()
        
        if rules:
            # 获取账户和产品映射
            accounts = {a['id']: a for a in get_accounts()}
            products = {p['id']: p for p in get_products()}
            
            # 构建显示数据
            display_data = []
            for r in rules:
                from_account = accounts.get(r.get('from_account_id'), {})
                to_product = products.get(r.get('to_product_id'), {})
                
                display_data.append({
                    'ID': r.get('id'),
                    '来源账户': f"{from_account.get('account_code', '')} - {from_account.get('account_name', '')}",
                    '目标产品': f"{to_product.get('code', '')} - {to_product.get('name') or to_product.get('product_name', '')}",
                    '分配比例': f"{float(r.get('ratio', 0)) * 100:.2f}%",
                    '最小金额': f"{float(r.get('min_amount', 0)):.2f}",
                    '取整粒度': f"{float(r.get('round_step', 1)):.2f}",
                    '状态': '启用' if r.get('is_active', 1) else '停用'
                })
            
            df = pd.DataFrame(display_data)
            
            # 选择要编辑的规则
            selected_idx = st.selectbox("选择规则进行编辑", range(len(rules)), 
                                      format_func=lambda x: f"{display_data[x]['来源账户']} -> {display_data[x]['目标产品']}")
            
            if selected_idx is not None:
                rule = rules[selected_idx]
                st.divider()
                st.subheader("编辑资金池规则")
                
                col1, col2 = st.columns(2)
                with col1:
                    new_ratio = st.number_input("分配比例 (%)", value=float(rule.get('ratio', 0)) * 100, min_value=0.0, max_value=100.0, step=0.01, key="edit_ratio")
                    new_min_amount = st.number_input("最小金额", value=float(rule.get('min_amount', 0)), min_value=0.0, step=0.01, key="edit_min")
                with col2:
                    new_round_step = st.number_input("取整粒度", value=float(rule.get('round_step', 1)), min_value=0.01, step=0.01, key="edit_round")
                    new_is_active = st.checkbox("是否启用", value=bool(rule.get('is_active', 1)), key="edit_rule_active")
                
                if st.button("💾 保存修改", key="save_rule"):
                    try:
                        update_data = {
                            'ratio': new_ratio / 100,
                            'min_amount': new_min_amount,
                            'round_step': new_round_step,
                            'is_active': 1 if new_is_active else 0
                        }
                        update_account_pool_rule(rule.get('id'), update_data)
                        st.success("✅ 规则更新成功！")
                        st.rerun()
                    except Exception as e:
                        st.error(f"❌ 更新失败: {e}")
                
                if st.button("🗑️ 删除规则", key="delete_rule"):
                    try:
                        delete_account_pool_rule(rule.get('id'))
                        st.success("✅ 规则已删除！")
                        st.rerun()
                    except Exception as e:
                        st.error(f"❌ 删除失败: {e}")
            
            st.dataframe(df, width='stretch', hide_index=True)
        else:
            st.info("暂无资金池规则")
    
    with tab2:
        st.subheader("新增资金池规则")
        
        # 账户选择：获取所有可用作资金池来源的账户（CASH + PRODUCT_SUB类型）
        accounts_cash = get_accounts(account_type='CASH', is_active=True)
        accounts_product_sub = get_accounts(account_type='PRODUCT_SUB', is_active=True)
        accounts = accounts_cash + accounts_product_sub
        account_options = {}
        for a in accounts:
            account_options[a['id']] = f"{a.get('account_code', '')} - {a.get('account_name', '')}"
        
        selected_from_account = st.selectbox("来源账户 *", options=list(account_options.keys()), 
                                            format_func=lambda x: account_options[x],
                                            key="new_rule_from")
        
        # 产品选择
        products = get_products(is_active=True)
        product_options = {}
        for p in products:
            product_options[p['id']] = format_product_display_name(p)
        
        selected_to_product = st.selectbox("目标产品 *", options=list(product_options.keys()), 
                                          format_func=lambda x: product_options[x],
                                          key="new_rule_to")
        
        col1, col2 = st.columns(2)
        with col1:
            new_ratio = st.number_input("分配比例 (%) *", value=0.0, min_value=0.0, max_value=100.0, step=0.01, key="new_rule_ratio")
            new_min_amount = st.number_input("最小金额", value=0.0, min_value=0.0, step=0.01, key="new_rule_min")
        with col2:
            new_round_step = st.number_input("取整粒度", value=1.0, min_value=0.01, step=0.01, key="new_rule_round")
        
        if st.button("➕ 创建规则", key="create_rule"):
            try:
                rule_data = {
                    'from_account_id': selected_from_account,
                    'to_product_id': selected_to_product,
                    'ratio': new_ratio / 100,
                    'min_amount': new_min_amount,
                    'round_step': new_round_step,
                    'is_active': 1
                }
                rule_id = add_account_pool_rule(rule_data)
                st.success(f"✅ 规则创建成功！ID: {rule_id}")
                st.rerun()
            except Exception as e:
                st.error(f"❌ 创建失败: {e}")


# ============================================================
# 策略实验室页面
# ============================================================
def page_strategy_lab():
    """策略实验室页面"""
    st.title("🔬 策略实验室")
    st.markdown("---")
    
    # 标签页
    tab1, tab2, tab3 = st.tabs(["📊 回测运行", "📈 回测结果", "🔀 参数对比"])
    
    with tab1:
        _page_backtest_run()
    
    with tab2:
        _page_backtest_results()
    
    with tab3:
        _page_param_comparison()


def _page_backtest_run():
    """回测运行页面"""
    st.subheader("📊 运行回测")
    
    # 策略管理标签页
    tab_strategy, tab_param, tab_backtest = st.tabs(["🔧 策略管理", "⚙️ 参数管理", "📊 运行回测"])
    
    with tab_strategy:
        _page_strategy_management()
    
    with tab_param:
        _page_param_management(context="backtest_run")
    
    with tab_backtest:
        _page_backtest_run_content()


def _page_strategy_management():
    """策略管理页面"""
    st.subheader("🔧 策略管理")
    
    col1, col2 = st.columns([2, 1])
    
    with col1:
        # 策略列表
        st.write("**已注册的策略**")
        try:
            strategies = list_strategies()
            if strategies:
                for strategy_key, versions in strategies.items():
                    with st.expander(f"📋 {strategy_key} (版本: {', '.join(versions)})"):
                        try:
                            info = get_strategy_info(strategy_key)
                            st.write(f"**显示名称**: {info.get('display_name', 'N/A')}")
                            st.write(f"**策略标识**: {info.get('strategy_key', 'N/A')}")
                            st.write(f"**版本**: {info.get('strategy_version', 'N/A')}")
                            
                            # 显示默认参数
                            default_params = info.get('default_params', {})
                            if default_params:
                                st.write("**默认参数**:")
                                st.json(default_params)
                            
                            # 显示参数 schema
                            param_schema = info.get('param_schema', {})
                            if param_schema:
                                st.write("**参数 Schema**:")
                                st.json(param_schema)
                            
                            # 编辑和删除按钮
                            col_edit, col_del = st.columns(2)
                            with col_edit:
                                if st.button("✏️ 编辑", key=f"edit_{strategy_key}"):
                                    st.session_state[f"editing_{strategy_key}"] = True
                                    st.session_state[f"edit_strategy_key"] = strategy_key
                            
                            with col_del:
                                if st.button("🗑️ 删除", key=f"delete_{strategy_key}", type="secondary"):
                                    st.session_state[f"confirm_delete_{strategy_key}"] = True
                            
                            # 删除确认
                            if st.session_state.get(f"confirm_delete_{strategy_key}", False):
                                st.warning(f"⚠️ 确定要删除策略 '{strategy_key}' 吗？")
                                col_yes, col_no = st.columns(2)
                                with col_yes:
                                    if st.button("✅ 确认删除", key=f"confirm_yes_{strategy_key}", type="primary"):
                                        result = delete_strategy(strategy_key)
                                        if result.get('success'):
                                            st.success(f"✅ {result.get('message')}")
                                            st.session_state[f"confirm_delete_{strategy_key}"] = False
                                            st.rerun()
                                        else:
                                            st.error(f"❌ 删除失败: {result.get('error')}")
                                with col_no:
                                    if st.button("❌ 取消", key=f"confirm_no_{strategy_key}"):
                                        st.session_state[f"confirm_delete_{strategy_key}"] = False
                                        st.rerun()
                            
                            # 编辑界面
                            if st.session_state.get(f"editing_{strategy_key}", False):
                                st.divider()
                                st.write("**编辑策略**")
                                
                                # 加载策略代码
                                strategy_code = load_strategy_code(strategy_key)
                                
                                if strategy_code:
                                    edited_code = st.text_area(
                                        "策略代码",
                                        value=strategy_code,
                                        height=400,
                                        key=f"edit_code_{strategy_key}"
                                    )
                                    
                                    col_save, col_cancel = st.columns(2)
                                    with col_save:
                                        if st.button("💾 保存", key=f"save_edit_{strategy_key}", type="primary"):
                                            result = save_strategy(
                                                strategy_key=strategy_key,
                                                strategy_code=edited_code,
                                                overwrite=True
                                            )
                                            if result.get('success'):
                                                st.success(f"✅ 策略已更新: {result.get('filename')}")
                                                st.session_state[f"editing_{strategy_key}"] = False
                                                st.rerun()
                                            else:
                                                st.error(f"❌ 更新失败: {result.get('error')}")
                                    
                                    with col_cancel:
                                        if st.button("❌ 取消", key=f"cancel_edit_{strategy_key}"):
                                            st.session_state[f"editing_{strategy_key}"] = False
                                            st.rerun()
                                else:
                                    st.warning(f"无法加载策略代码: {strategy_key}")
                                    if st.button("❌ 关闭", key=f"close_edit_{strategy_key}"):
                                        st.session_state[f"editing_{strategy_key}"] = False
                                        st.rerun()
                                        
                        except Exception as e:
                            st.error(f"获取策略信息失败: {e}")
            else:
                st.info("暂无已注册的策略")
        except Exception as e:
            st.error(f"获取策略列表失败: {e}")
    
    with col2:
        # 新增策略
        st.write("**新增策略**")
        with st.form("new_strategy_form"):
            new_strategy_key = st.text_input("策略标识 *", key="new_strategy_key", 
                                            help="例如: my_custom_strategy")
            new_display_name = st.text_input("显示名称", key="new_display_name",
                                            help="例如: 我的自定义策略")
            new_version = st.text_input("版本", value="default", key="new_strategy_version")
            
            overwrite_existing = st.checkbox("覆盖已存在文件", value=False, key="overwrite_existing",
                                           help="如果策略文件已存在，是否覆盖")
            
            use_template = st.checkbox("使用模板代码", value=False, key="use_template",
                                     help="如果取消勾选，需要在下方的代码编辑器中输入策略代码")
            
            # 策略代码编辑器
            if not use_template:
                strategy_code = st.text_area(
                    "策略代码 *",
                    height=400,
                    key="strategy_code_input",
                    help="请输入完整的策略代码（Python代码）",
                    placeholder="# 请输入策略代码，例如：\nfrom typing import Dict, Any, Optional\nfrom ..framework.base import Strategy\n..."
                )
            else:
                strategy_code = None
            
            if st.form_submit_button("➕ 创建策略"):
                if new_strategy_key:
                    if not use_template and (not strategy_code or not strategy_code.strip()):
                        st.error("❌ 请输入策略代码或勾选'使用模板代码'")
                    else:
                        result = save_strategy(
                            strategy_key=new_strategy_key,
                            strategy_code=strategy_code if strategy_code else None,
                            display_name=new_display_name or None,
                            strategy_version=new_version,
                            overwrite=overwrite_existing
                        )
                        if result.get('success'):
                            st.success(f"✅ 策略创建成功: {result.get('filename')}")
                            st.rerun()
                        else:
                            st.error(f"❌ 创建失败: {result.get('error')}")
                else:
                    st.error("请输入策略标识")


def _page_param_management(context: str = "default"):
    """参数管理页面
    
    Args:
        context: 上下文标识，用于区分不同的调用场景，避免key重复
    """
    st.subheader("⚙️ 策略参数管理")
    
    from data.db_connector import execute_query, execute_one, execute_update
    from advisor.advisor_service import get_strategy_config
    import json
    
    # 筛选选项（使用context确保key唯一）
    col1, col2 = st.columns(2)
    with col1:
        strategy_filter = st.selectbox(
            "筛选策略",
            options=["全部策略"] + ['percentile', 'drawdown', 'profit_recycle', 'simple', 'dca_4pct'],
            key=f"param_mgmt_strategy_filter_{context}"
        )
    
    with col2:
        show_inactive = st.checkbox("显示已停用参数", key=f"param_mgmt_show_inactive_{context}")
    
    # 查询参数列表（包含绑定产品信息）
    sql = """
        SELECT 
            sc.id, sc.strategy_key, sc.strategy_version, sc.param_set_id,
            sc.param_json, sc.is_active, sc.created_at, sc.updated_at,
            COUNT(DISTINCT psb.id) as bind_count,
            COUNT(DISTINCT bs.id) as backtest_count,
            GROUP_CONCAT(DISTINCT CONCAT(p.code, ':', p.product_name) ORDER BY p.code SEPARATOR '|') as bound_products
        FROM strategy_config sc
        LEFT JOIN product_strategy_bind psb ON sc.strategy_key = psb.strategy_code 
            AND sc.param_set_id = psb.param_set_id 
            AND psb.enabled = 1
        LEFT JOIN products p ON psb.product_id = p.id
        LEFT JOIN backtest_summary bs ON sc.strategy_key = bs.strategy_key 
            AND sc.strategy_version = bs.strategy_version 
            AND sc.param_set_id = bs.param_set_id
        WHERE 1=1
    """
    params = []
    
    if strategy_filter != "全部策略":
        sql += " AND sc.strategy_key = %s"
        params.append(strategy_filter)
    
    if not show_inactive:
        sql += " AND sc.is_active = 1"
    
    sql += " GROUP BY sc.id, sc.strategy_key, sc.strategy_version, sc.param_set_id, sc.param_json, sc.is_active, sc.created_at, sc.updated_at"
    sql += " ORDER BY sc.strategy_key, sc.param_set_id, sc.created_at DESC"
    
    param_configs = execute_query(sql, tuple(params))
    
    if not param_configs:
        st.info("暂无参数配置")
        return
    
    # 显示参数列表
    st.markdown(f"**共找到 {len(param_configs)} 个参数配置**")
    
    for config in param_configs:
        config_id = config.get('id')
        strategy_key = config.get('strategy_key')
        strategy_version = config.get('strategy_version', 'default')
        param_set_id = config.get('param_set_id')
        param_json = config.get('param_json')
        is_active = config.get('is_active', 1)
        bind_count = config.get('bind_count', 0) or 0
        backtest_count = config.get('backtest_count', 0) or 0
        created_at = config.get('created_at')
        updated_at = config.get('updated_at')
        
        # 解析参数JSON
        try:
            param_dict = json.loads(param_json) if param_json else {}
        except:
            param_dict = {}
        
        # 状态标签
        status_label = "✅ 启用" if is_active else "❌ 停用"
        
        with st.expander(
            f"{status_label} | {strategy_key}@{strategy_version}#{param_set_id} | "
            f"绑定:{bind_count} | 回测:{backtest_count}",
            expanded=False
        ):
            col_info, col_action = st.columns([3, 1])
            
            with col_info:
                st.markdown(f"**策略标识**: {strategy_key}")
                st.markdown(f"**策略版本**: {strategy_version}")
                st.markdown(f"**参数集ID**: {param_set_id}")
                st.markdown(f"**状态**: {'启用' if is_active else '停用'}")
                st.markdown(f"**产品绑定数**: {bind_count}")
                
                # 显示绑定的产品列表
                bound_products_str = config.get('bound_products', '')
                if bound_products_str:
                    bound_products = [p.split(':', 1) for p in bound_products_str.split('|') if p]
                    if bound_products:
                        st.markdown("**绑定的产品**:")
                        for product_code, product_name in bound_products:
                            st.caption(f"  • {product_code} - {product_name}")
                elif bind_count > 0:
                    st.caption("  (产品信息加载中...)")
                else:
                    st.caption("  (无绑定产品)")
                
                st.markdown(f"**回测结果数**: {backtest_count}")
                st.markdown(f"**创建时间**: {created_at}")
                st.markdown(f"**更新时间**: {updated_at}")
                
                st.divider()
                st.markdown("**参数详情**:")
                
                # 检查是否在编辑模式
                edit_key = f"editing_param_{config_id}_{context}"
                is_editing = st.session_state.get(edit_key, False)
                
                if is_editing:
                    # 编辑模式：动态生成参数编辑表单
                    st.info("📝 编辑模式：修改参数值后点击保存")
                    
                    # 使用 session_state 保存编辑后的参数（初始化为原始参数）
                    edited_params_key = f"edited_params_{config_id}_{context}"
                    if edited_params_key not in st.session_state:
                        st.session_state[edited_params_key] = json.loads(json.dumps(param_dict))  # 深拷贝
                    
                    edited_params = st.session_state[edited_params_key]
                    
                    # 用于保存所有 data_editor 的 key，以便在保存时重新读取
                    data_editor_keys = []
                    
                    # 递归函数：根据参数类型生成对应的输入控件，直接修改 edited_params
                    def render_param_editor(key: str, value: Any, param_dict_ref: dict, param_path: str = ""):
                        """递归渲染参数编辑器，直接修改 param_dict_ref"""
                        display_key = f"{param_path}.{key}" if param_path else key
                        
                        if isinstance(value, dict):
                            # 字典类型：展开显示
                            st.markdown(f"**{display_key}** (对象):")
                            with st.container():
                                for sub_key, sub_value in value.items():
                                    render_param_editor(sub_key, sub_value, param_dict_ref[key], display_key)
                        elif isinstance(value, list):
                            # 列表类型：特殊处理（如 tiers）
                            st.markdown(f"**{display_key}** (数组):")
                            
                            # 检查是否是 tiers 数组（包含 max_rank, suggest_ratio, label）
                            if value and isinstance(value[0], dict) and 'max_rank' in value[0]:
                                # tiers 数组：使用表格编辑
                                st.caption("提示：tiers 数组包含多个档位配置，可直接在表格中编辑")
                                
                                # 显示当前 tiers（从 edited_params 读取，而不是原始 param_dict）
                                tiers_data = []
                                current_tiers = param_dict_ref.get(key, value)
                                for i, tier in enumerate(current_tiers):
                                    tiers_data.append({
                                        '档位': i + 1,
                                        'max_rank': tier.get('max_rank', 0),
                                        'suggest_ratio': tier.get('suggest_ratio', 0),
                                        'label': tier.get('label', '')
                                    })
                                
                                if tiers_data:
                                    df_tiers = pd.DataFrame(tiers_data)
                                    editor_key = f"tiers_editor_{display_key}_{config_id}_{context}"
                                    data_editor_keys.append((editor_key, key, param_dict_ref, param_path))
                                    
                                    edited_tiers_df = st.data_editor(
                                        df_tiers,
                                        key=editor_key,
                                        num_rows="fixed",
                                        column_config={
                                            '档位': st.column_config.NumberColumn('档位', disabled=True),
                                            'max_rank': st.column_config.NumberColumn('max_rank', min_value=0.0, max_value=1.0, step=0.01, format="%.2f"),
                                            'suggest_ratio': st.column_config.NumberColumn('suggest_ratio', min_value=0.0, max_value=1.0, step=0.01, format="%.2f"),
                                            'label': st.column_config.TextColumn('label')
                                        }
                                    )
                                    
                                    # 立即更新到 param_dict_ref（每次 rerun 时都会执行）
                                    # 注意：st.data_editor 返回的 DataFrame 已经包含用户编辑后的值
                                    edited_tiers = []
                                    for _, row in edited_tiers_df.iterrows():
                                        # 确保类型转换正确
                                        max_rank_val = float(row['max_rank']) if pd.notna(row['max_rank']) else 0.0
                                        suggest_ratio_val = float(row['suggest_ratio']) if pd.notna(row['suggest_ratio']) else 0.0
                                        label_val = str(row['label']) if pd.notna(row['label']) else ''
                                        
                                        edited_tiers.append({
                                            'max_rank': max_rank_val,
                                            'suggest_ratio': suggest_ratio_val,
                                            'label': label_val
                                        })
                                    
                                    # 直接更新到 param_dict_ref
                                    param_dict_ref[key] = edited_tiers
                                else:
                                    param_dict_ref[key] = []
                            else:
                                # 普通数组：使用文本区域编辑 JSON
                                json_str = st.text_area(
                                    f"{display_key} (JSON数组)",
                                    value=json.dumps(value, ensure_ascii=False, indent=2),
                                    key=f"list_editor_{display_key}_{config_id}_{context}",
                                    height=150
                                )
                                try:
                                    param_dict_ref[key] = json.loads(json_str)
                                except Exception as e:
                                    st.error(f"❌ {display_key} JSON格式错误: {e}")
                        elif isinstance(value, (int, float)):
                            # 数字类型
                            if isinstance(value, int):
                                edited_value = st.number_input(
                                    display_key,
                                    value=int(value),
                                    key=f"param_{display_key}_{config_id}_{context}",
                                    step=1
                                )
                                param_dict_ref[key] = int(edited_value)
                            else:
                                edited_value = st.number_input(
                                    display_key,
                                    value=float(value),
                                    key=f"param_{display_key}_{config_id}_{context}",
                                    step=0.01,
                                    format="%.2f"
                                )
                                param_dict_ref[key] = float(edited_value)
                        elif isinstance(value, bool):
                            # 布尔类型
                            edited_value = st.checkbox(
                                display_key,
                                value=bool(value),
                                key=f"param_{display_key}_{config_id}_{context}"
                            )
                            # 直接更新到 param_dict_ref
                            param_dict_ref[key] = bool(edited_value)
                        elif isinstance(value, str):
                            # 字符串类型
                            edited_value = st.text_input(
                                display_key,
                                value=str(value),
                                key=f"param_{display_key}_{config_id}_{context}"
                            )
                            # 直接更新到 param_dict_ref
                            param_dict_ref[key] = str(edited_value)
                        else:
                            # 其他类型：使用文本区域编辑 JSON
                            json_str = st.text_area(
                                f"{display_key} (JSON)",
                                value=json.dumps(value, ensure_ascii=False, indent=2),
                                key=f"json_editor_{display_key}_{config_id}_{context}",
                                height=100
                            )
                            try:
                                param_dict_ref[key] = json.loads(json_str)
                            except Exception as e:
                                st.error(f"❌ {display_key} JSON格式错误: {e}")
                    
                    # 渲染所有参数
                    for key, value in param_dict.items():
                        render_param_editor(key, value, edited_params)
                    
                    # 保存和取消按钮
                    col_save, col_cancel = st.columns(2)
                    with col_save:
                        if st.button("💾 保存修改", key=f"save_param_{config_id}_{context}", type="primary"):
                            try:
                                # 重新读取所有 data_editor 的值（确保获取最新的编辑结果）
                                # 注意：st.data_editor 返回的 DataFrame 在按钮点击时已经是最新的
                                # 但为了确保，我们从 session_state 读取（如果存在）
                                for editor_key, param_key, param_dict_ref, param_path in data_editor_keys:
                                    # 从 session_state 读取编辑后的 DataFrame
                                    if editor_key in st.session_state:
                                        edited_df = st.session_state[editor_key]
                                        
                                        # 转换回字典列表
                                        edited_tiers = []
                                        for _, row in edited_df.iterrows():
                                            max_rank_val = float(row['max_rank']) if pd.notna(row['max_rank']) else 0.0
                                            suggest_ratio_val = float(row['suggest_ratio']) if pd.notna(row['suggest_ratio']) else 0.0
                                            label_val = str(row['label']) if pd.notna(row['label']) else ''
                                            
                                            edited_tiers.append({
                                                'max_rank': max_rank_val,
                                                'suggest_ratio': suggest_ratio_val,
                                                'label': label_val
                                            })
                                        
                                        # 更新到 edited_params（通过 param_dict_ref 的引用）
                                        param_dict_ref[param_key] = edited_tiers
                                
                                # 更新 session_state
                                st.session_state[edited_params_key] = edited_params
                                
                                # 更新数据库
                                param_json_str = json.dumps(edited_params, ensure_ascii=False)
                                execute_update(
                                    "UPDATE strategy_config SET param_json = %s, updated_at = CURRENT_TIMESTAMP WHERE id = %s",
                                    (param_json_str, config_id)
                                )
                                
                                # 清除编辑状态
                                st.session_state[edit_key] = False
                                if edited_params_key in st.session_state:
                                    del st.session_state[edited_params_key]
                                
                                st.success("✅ 参数已保存！")
                                
                                # 如果参数被产品绑定，提示需要重新计算指标
                                if bind_count > 0:
                                    st.info(f"💡 提示：该参数已被 {bind_count} 个产品绑定，建议刷新行情以重新计算指标。")
                                
                                st.rerun()
                            except Exception as e:
                                st.error(f"❌ 保存失败: {e}")
                                import traceback
                                st.exception(e)
                    
                    with col_cancel:
                        if st.button("❌ 取消", key=f"cancel_param_{config_id}_{context}"):
                            st.session_state[edit_key] = False
                            if edited_params_key in st.session_state:
                                del st.session_state[edited_params_key]
                            st.rerun()
                else:
                    # 只读模式：显示 JSON
                    st.json(param_dict)
                    
                    # 编辑按钮
                    if st.button("✏️ 编辑参数", key=f"edit_param_{config_id}_{context}"):
                        st.session_state[edit_key] = True
                        st.rerun()
            
            with col_action:
                # 删除按钮（使用context确保key唯一）
                delete_key = f"delete_param_{config_id}_{context}"
                confirm_key = f"confirm_delete_param_{config_id}_{context}"
                cancel_key = f"cancel_delete_param_{config_id}_{context}"
                
                # 检查是否在确认删除状态
                if st.session_state.get(confirm_key, False):
                    # 显示确认提示
                    if bind_count > 0 or backtest_count > 0:
                        st.warning(f"⚠️ 该参数已被 {bind_count} 个产品绑定，且有 {backtest_count} 个回测结果。删除将同时删除绑定关系和回测结果。")
                    else:
                        st.warning(f"⚠️ 确定要删除该参数配置吗？")
                    
                    col_yes, col_no = st.columns(2)
                    with col_yes:
                        if st.button("✅ 确认删除", key=f"yes_{confirm_key}_{context}", type="primary"):
                            try:
                                # 1. 删除产品绑定关系
                                if bind_count > 0:
                                    execute_update(
                                        "UPDATE product_strategy_bind SET enabled = 0 WHERE strategy_code = %s AND param_set_id = %s",
                                        (strategy_key, param_set_id)
                                    )
                                    st.info(f"✅ 已删除 {bind_count} 个产品绑定关系")
                                
                                # 2. 删除回测结果（级联删除关联数据）
                                if backtest_count > 0:
                                    # 获取所有相关的回测汇总ID
                                    summary_ids_sql = """
                                        SELECT id FROM backtest_summary 
                                        WHERE strategy_key = %s 
                                          AND strategy_version = %s 
                                          AND param_set_id = %s
                                    """
                                    summary_ids = execute_query(summary_ids_sql, (strategy_key, strategy_version, param_set_id))
                                    
                                    deleted_backtest_count = 0
                                    for summary_row in summary_ids:
                                        summary_id = summary_row.get('id')
                                        if delete_backtest_summary(summary_id):
                                            deleted_backtest_count += 1
                                    
                                    st.info(f"✅ 已删除 {deleted_backtest_count} 个回测结果")
                                
                                # 3. 删除参数配置
                                execute_update(
                                    "UPDATE strategy_config SET is_active = 0 WHERE id = %s",
                                    (config_id,)
                                )
                                
                                st.session_state[confirm_key] = False
                                st.success(f"✅ 参数配置已删除！")
                                st.rerun()
                            except Exception as e:
                                st.error(f"❌ 删除失败: {e}")
                    
                    with col_no:
                        if st.button("❌ 取消", key=f"no_{cancel_key}_{context}"):
                            st.session_state[confirm_key] = False
                            st.rerun()
                else:
                    # 显示删除按钮
                    if st.button("🗑️ 删除参数", key=delete_key, type="secondary"):
                        st.session_state[confirm_key] = True
                        st.rerun()


def _page_backtest_run_content():
    """回测运行内容页面"""
    # 获取产品列表
    products = get_products(is_active=True)
    if not products:
        st.warning("暂无可用产品")
        return
    
    product_options = {}
    for p in products:
        product_options[p['id']] = format_product_display_name(p)
    
    # 获取策略列表
    try:
        strategies = list_strategies()
        # 构建策略选项列表（包含版本信息）
        strategy_options = []
        for strategy_key, versions in strategies.items():
            if len(versions) == 1:
                strategy_options.append(strategy_key)
            else:
                for version in versions:
                    strategy_options.append(f"{strategy_key}@{version}")
        
        if not strategy_options:
            st.warning("暂无可用策略，请先在策略管理中创建策略")
            return
        
        # 策略选择
        col1, col2 = st.columns([1, 1])
        
        with col1:
            selected_strategy_full = st.selectbox(
                "选择策略 *",
                options=strategy_options,
                key="backtest_strategy",
                help="选择要回测的策略"
            )
            
            # 解析策略标识和版本
            if "@" in selected_strategy_full:
                selected_strategy, selected_version = selected_strategy_full.split("@", 1)
            else:
                selected_strategy = selected_strategy_full
                selected_version = None
        
        with col2:
            # 获取策略信息并显示参数编辑界面
            if selected_strategy:
                try:
                    strategy_info = get_strategy_info(selected_strategy, selected_version)
                    st.caption(f"策略: {strategy_info.get('display_name', selected_strategy)}")
                    if selected_version:
                        st.caption(f"版本: {selected_version}")
                except Exception as e:
                    st.caption(f"策略: {selected_strategy}")
    except Exception as e:
        st.error(f"获取策略列表失败: {e}")
        return
    
    # 策略参数编辑
    params = {}
    if selected_strategy:
        st.subheader("策略参数")
        try:
            strategy_info = get_strategy_info(selected_strategy, selected_version)
            default_params = strategy_info.get('default_params', {})
            param_schema = strategy_info.get('param_schema', {})
            
            # 总是使用输入框形式，优先使用 schema，如果没有 schema 则从 default_params 推断
            params = {}
            
            if param_schema:
                # 使用 schema 生成输入框
                for param_name, param_config in param_schema.items():
                    param_type = param_config.get('type', 'str')
                    param_default = param_config.get('default', default_params.get(param_name))
                    param_desc = param_config.get('description', param_name)
                    
                    if param_type == 'float':
                        params[param_name] = st.number_input(
                            param_desc,
                            value=float(param_default) if param_default is not None else 0.0,
                            min_value=float(param_config.get('min', 0)) if 'min' in param_config else None,
                            max_value=float(param_config.get('max', 1000000)) if 'max' in param_config else None,
                            step=0.01,
                            key=f"param_{selected_strategy}_{param_name}"
                        )
                    elif param_type == 'int':
                        params[param_name] = st.number_input(
                            param_desc,
                            value=int(param_default) if param_default is not None else 0,
                            min_value=int(param_config.get('min', 0)) if 'min' in param_config else None,
                            max_value=int(param_config.get('max', 1000000)) if 'max' in param_config else None,
                            step=1,
                            key=f"param_{selected_strategy}_{param_name}"
                        )
                    elif param_type == 'bool':
                        params[param_name] = st.checkbox(
                            param_desc,
                            value=bool(param_default) if param_default is not None else False,
                            key=f"param_{selected_strategy}_{param_name}"
                        )
                    elif param_type == 'list' and 'options' in param_config:
                        options = param_config['options']
                        params[param_name] = st.selectbox(
                            param_desc,
                            options=options,
                            index=options.index(param_default) if param_default in options else 0,
                            key=f"param_{selected_strategy}_{param_name}"
                        )
                    elif param_type == 'str' and isinstance(param_default, str) and param_default.strip().startswith('['):
                        # 字符串类型但看起来是JSON列表，尝试解析
                        # 根据参数名提供更具体的帮助信息
                        if param_name == 'deep_dip_levels':
                            help_text = "请输入JSON格式的列表，例如: [{\"threshold\": -0.10, \"use_ratio\": 0.50}, {\"threshold\": -0.15, \"use_ratio\": 1.00}]"
                        else:
                            help_text = "请输入JSON格式的列表，例如: [0.02, 0.04, 0.08]"
                        
                        list_str = st.text_input(
                            param_desc,
                            value=param_default,
                            key=f"param_{selected_strategy}_{param_name}",
                            help=help_text
                        )
                        try:
                            parsed_value = json.loads(list_str) if list_str.strip() else []
                            # 对于 deep_dip_levels，保持为字符串格式（策略会自己解析）
                            if param_name == 'deep_dip_levels':
                                params[param_name] = list_str
                            else:
                                params[param_name] = parsed_value
                        except json.JSONDecodeError as e:
                            st.warning(f"参数 {param_name} 的JSON格式错误: {e}")
                            # 对于 deep_dip_levels，保持原始字符串；其他参数使用空列表
                            if param_name == 'deep_dip_levels':
                                params[param_name] = param_default
                            else:
                                params[param_name] = []
                    else:
                        # 字符串或其他类型，使用文本输入
                        params[param_name] = st.text_input(
                            param_desc,
                            value=str(param_default) if param_default is not None else "",
                            key=f"param_{selected_strategy}_{param_name}"
                        )
            elif default_params:
                # 没有 schema，但从 default_params 推断类型并生成输入框
                for param_name, param_value in default_params.items():
                    if isinstance(param_value, (int, float)):
                        if isinstance(param_value, float):
                            params[param_name] = st.number_input(
                                param_name,
                                value=float(param_value),
                                step=0.01,
                                key=f"param_{selected_strategy}_{param_name}"
                            )
                        else:
                            params[param_name] = st.number_input(
                                param_name,
                                value=int(param_value),
                                step=1,
                                key=f"param_{selected_strategy}_{param_name}"
                            )
                    elif isinstance(param_value, bool):
                        params[param_name] = st.checkbox(
                            param_name,
                            value=bool(param_value),
                            key=f"param_{selected_strategy}_{param_name}"
                        )
                    elif isinstance(param_value, list):
                        # 列表类型，使用文本输入（用户可以输入JSON格式）
                        list_str = st.text_input(
                            param_name,
                            value=json.dumps(param_value, ensure_ascii=False),
                            key=f"param_{selected_strategy}_{param_name}",
                            help="请输入JSON格式的列表，例如: [0.02, 0.04, 0.08]"
                        )
                        # 尝试解析JSON
                        try:
                            params[param_name] = json.loads(list_str) if list_str.strip() else param_value
                        except json.JSONDecodeError:
                            st.warning(f"参数 {param_name} 的JSON格式错误，使用默认值")
                            params[param_name] = param_value
                    else:
                        # 字符串或其他类型
                        params[param_name] = st.text_input(
                            param_name,
                            value=str(param_value) if param_value is not None else "",
                            key=f"param_{selected_strategy}_{param_name}"
                        )
            else:
                # 既没有 schema 也没有 default_params，显示提示
                st.info("该策略暂无参数配置")
                params = {}
        except Exception as e:
            st.warning(f"获取策略参数失败: {e}")
            params = {}
    
    # 场内场外选择（默认场内）
    backtest_channel = st.radio(
        "交易类型",
        ["场内", "场外"],
        index=0,  # 默认场内
        key="backtest_channel",
        horizontal=True
    )
    
    # 根据选择筛选产品
    if backtest_channel == "场内":
        filtered_products = {pid: name for pid, name in product_options.items() 
                            if any(p['id'] == pid and p.get('channel') == 'EXCHANGE' for p in products)}
    else:
        filtered_products = {pid: name for pid, name in product_options.items() 
                            if any(p['id'] == pid and p.get('channel') == 'OTC' for p in products)}
    
    if not filtered_products:
        st.warning(f"⚠️ 暂无{backtest_channel}产品，请先在产品管理中添加{backtest_channel}产品")
        return
    
    # 产品选择（移到前面，以便显示行情范围）
    selected_product_id = st.selectbox(
        "选择产品 *",
        options=list(filtered_products.keys()),
        format_func=lambda x: filtered_products[x],
        key="backtest_product"
    )
    
    # 显示产品行情范围
    if selected_product_id:
        data_range = get_product_data_range(selected_product_id)
        if data_range:
            channel_label = "场内" if data_range.get('channel') == 'EXCHANGE' else "场外"
            earliest = data_range.get('earliest_date', '无数据')
            latest = data_range.get('latest_date', '无数据')
            record_count = data_range.get('record_count', 0)
            
            if earliest and latest:
                st.info(f"📊 **{channel_label}行情范围**: {earliest} 至 {latest} (共 {record_count:,} 条记录)")
            elif record_count == 0:
                st.warning(f"⚠️ **{channel_label}行情数据**: 暂无数据，回测时将尝试自动获取")
            else:
                st.info(f"📊 **{channel_label}行情数据**: 共 {record_count:,} 条记录")
    
    # 基础配置
    col1, col2 = st.columns(2)
    
    with col1:
        
        initial_cash = st.number_input(
            "初始现金 *",
            value=10000.0,
            min_value=0.0,
            step=100.0,
            key="backtest_initial_cash"
        )
        
        monthly_deposit = st.number_input(
            "每月入金",
            value=1000.0,
            min_value=0.0,
            step=100.0,
            key="backtest_monthly_deposit"
        )
    
    with col2:
        deposit_day = st.number_input(
            "入金日期（每月几号）",
            value=10,
            min_value=1,
            max_value=31,
            step=1,
            key="backtest_deposit_day"
        )
        
        min_trade_amount = st.number_input(
            "最小成交金额",
            value=1000.0,
            min_value=0.0,
            step=100.0,
            key="backtest_min_trade"
        )
        
        start_date = st.date_input(
            "开始日期",
            value=date(2023, 1, 1),
            min_value=date(2000, 1, 1),
            max_value=date(2100, 12, 31),
            key="backtest_start_date"
        )
        
        end_date = st.date_input(
            "结束日期",
            value=date.today(),
            min_value=date(2000, 1, 1),
            max_value=date(2100, 12, 31),
            key="backtest_end_date"
        )
    
    # 运行按钮
    if st.button("🚀 开始回测", type="primary", key="run_backtest"):
        with st.spinner("回测运行中，请稍候..."):
            result = run_backtest(
                product_id=selected_product_id,
                strategy_key=selected_strategy,
                strategy_version=selected_version,
                params=params,
                initial_cash=initial_cash,
                monthly_deposit=monthly_deposit if monthly_deposit > 0 else None,
                deposit_day=deposit_day,
                min_trade_amount=min_trade_amount,
                start_date=start_date,
                end_date=end_date
            )
            
            if result.get('success'):
                st.success(f"✅ {result.get('message')}")
                if result.get('summary_id'):
                    st.info(f"📊 回测汇总ID: {result['summary_id']}")
                    metrics = result.get('metrics', {})
                    if metrics:
                        # 第一行：核心指标
                        col1, col2, col3, col4 = st.columns(4)
                        with col1:
                            st.metric("年化收益", f"{metrics.get('annual_return', 0):.2%}")
                        with col2:
                            st.metric("总收益率", f"{metrics.get('total_return', 0):.2%}")
                        with col3:
                            st.metric("最大回撤", f"{metrics.get('max_drawdown', 0):.2%}")
                        with col4:
                            st.metric("成交次数", f"{metrics.get('trade_count', 0)}")
                        
                        # 第二行：资金指标
                        col1, col2, col3, col4 = st.columns(4)
                        with col1:
                            initial = metrics.get('initial_cash', 0) or metrics.get('total_invested', 0)
                            st.metric("累计投入", f"{initial:,.2f}")
                        with col2:
                            final = metrics.get('final_value', 0)
                            st.metric("最终资产", f"{final:,.2f}")
                        with col3:
                            profit = final - initial
                            st.metric("绝对收益", f"{profit:,.2f}")
                        with col4:
                            st.metric("手续费总额", f"{metrics.get('total_fees', 0):,.2f}")
                        
                        # 第三行：其他指标
                        col1, col2, col3, col4 = st.columns(4)
                        with col1:
                            st.metric("手续费占比", f"{metrics.get('fee_ratio', 0):.2%}")
                        with col2:
                            st.metric("平均月成交", f"{metrics.get('avg_monthly_trades', 0):.1f}")
                        with col3:
                            st.metric("等待池比例", f"{metrics.get('wait_pool_ratio', 0):.2%}")
                        with col4:
                            # 计算夏普比率（如果有数据）
                            st.metric("回测天数", f"{len(result.get('daily_records', []))}")
            else:
                st.error(f"❌ {result.get('message')}")


def _page_backtest_results():
    """回测结果查看页面"""
    st.subheader("📈 回测结果")
    
    # 筛选条件
    col1, col2, col3 = st.columns(3)
    
    with col1:
        products = get_products(is_active=True)
        product_options = {0: "全部产品"}
        for p in products:
            product_options[p['id']] = format_product_display_name(p)
        
        filter_product_id = st.selectbox(
            "筛选产品",
            options=list(product_options.keys()),
            format_func=lambda x: product_options[x],
            key="filter_product"
        )
    
    with col2:
        try:
            strategies = list_strategies()
            strategy_options = ["全部策略"] + list(strategies.keys())
            filter_strategy = st.selectbox(
                "筛选策略",
                options=strategy_options,
                key="filter_strategy"
            )
        except:
            filter_strategy = "全部策略"
    
    with col3:
        limit = st.number_input("显示条数", value=50, min_value=1, max_value=500, key="result_limit")
    
    # 查询结果
    summaries = list_backtest_summaries(
        product_id=filter_product_id if filter_product_id > 0 else None,
        strategy_key=filter_strategy if filter_strategy != "全部策略" else None,
        limit=limit
    )
    
    if not summaries:
        st.info("暂无回测结果")
        return
    
    # 显示汇总列表
    df_data = []
    for s in summaries:
        # 计算绝对收益
        initial_cash = float(s.get('initial_cash', 0) or 0)
        final_value = float(s.get('final_value', 0) or 0)
        absolute_profit = final_value - initial_cash
        
        # 计算平均月成交
        days_diff = s.get('days_diff', 0) or 0
        months = max(days_diff / 30.0, 1.0 / 30.0)  # 至少1个月
        trade_count = s.get('trade_count', 0) or 0
        avg_monthly_trades = trade_count / months if months > 0 else 0.0
        
        df_data.append({
            'ID': s['id'],
            '产品': f"{s.get('product_code', '')} - {s.get('product_name', '')}",
            '策略': f"{s['strategy_key']}@{s.get('strategy_version', 'default')}",
            '参数ID': s.get('param_set_id', ''),
            '开始日期': s['start_date'],
            '结束日期': s['end_date'],
            '年化收益': f"{float(s.get('annual_return', 0)):.2%}",
            '总收益率': f"{float(s.get('total_return', 0)):.2%}",
            '最大回撤': f"{float(s.get('max_drawdown', 0)):.2%}",
            '成交次数': trade_count,
            '累计投入': f"{initial_cash:,.2f}",
            '最终资产': f"{final_value:,.2f}",
            '绝对收益': f"{absolute_profit:,.2f}",
            '手续费总额': f"{float(s.get('total_fees', 0) or 0):,.2f}",
            '手续费占比': f"{float(s.get('fee_ratio', 0)):.2%}",
            '平均月成交': f"{avg_monthly_trades:.1f}",
            '等待池比例': f"{float(s.get('wait_pool_ratio', 0)):.2%}",
            '创建时间': s.get('created_at', '')
        })
    
    # 显示表格（支持多选）
    df = pd.DataFrame(df_data)
    if not df.empty:
        # 使用多选模式
        event = st.dataframe(
            df,
            width='stretch',
            height=400,
            on_select="rerun",
            selection_mode="multi-row",
            key="backtest_results_table"
        )
        
        # 获取选中的行
        selected_rows = event.selection.rows if event.selection else []
        
        # 如果有选中的行，显示删除按钮
        if selected_rows:
            selected_ids = [df_data[i]['ID'] for i in selected_rows if i < len(df_data)]
            if selected_ids:
                st.info(f"已选中 {len(selected_ids)} 条记录，ID: {', '.join(map(str, selected_ids))}")
                
                col_del, col_cancel = st.columns([1, 4])
                with col_del:
                    if st.button("🗑️ 删除选中记录", key="delete_selected_backtest", type="primary"):
                        # 批量删除
                        success_count = 0
                        fail_count = 0
                        for summary_id in selected_ids:
                            if delete_backtest_summary(summary_id):
                                success_count += 1
                            else:
                                fail_count += 1
                        
                        if fail_count == 0:
                            st.success(f"✅ 成功删除 {success_count} 条记录")
                        else:
                            st.warning(f"⚠️ 成功删除 {success_count} 条，失败 {fail_count} 条")
                        
                        st.rerun()
                with col_cancel:
                    if st.button("❌ 取消选择", key="cancel_delete_selected"):
                        st.rerun()
    
    # 查看详情
    st.subheader("查看详情")
    selected_id = st.number_input("输入汇总ID", value=0, min_value=0, key="view_summary_id")
    
    if selected_id > 0:
        summary = get_backtest_summary(selected_id)
        if summary:
            # 显示汇总信息
            col1, col2, col3, col4 = st.columns(4)
            with col1:
                st.metric("年化收益", f"{float(summary.get('annual_return', 0)):.2%}")
            with col2:
                st.metric("最大回撤", f"{float(summary.get('max_drawdown', 0)):.2%}")
            with col3:
                st.metric("成交次数", f"{summary.get('trade_count', 0)}")
            with col4:
                st.metric("手续费占比", f"{float(summary.get('fee_ratio', 0)):.2%}")
            
            # 显示每日记录
            st.subheader("每日记录")
            daily_records = get_backtest_daily_records(selected_id)
            if daily_records:
                df_daily = pd.DataFrame(daily_records)
                st.dataframe(df_daily, use_container_width=True, height=300)
            
            # 显示成交记录
            st.subheader("成交记录")
            trades = get_backtest_trades(selected_id)
            if trades:
                df_trades = pd.DataFrame(trades)
                st.dataframe(df_trades, use_container_width=True, height=300)
        else:
            st.warning(f"未找到汇总ID: {selected_id}")


def _page_param_comparison():
    """参数对比页面 - 对比同一策略的不同参数组合"""
    st.subheader("🔀 参数组合对比")
    st.caption("选择同一策略的不同参数组合，对比回测结果")
    
    # 筛选条件
    col1, col2, col3 = st.columns(3)
    
    with col1:
        products = get_products(is_active=True)
        product_options = {0: "全部产品"}
        for p in products:
            product_options[p['id']] = format_product_display_name(p)
        
        filter_product_id = st.selectbox(
            "筛选产品",
            options=list(product_options.keys()),
            format_func=lambda x: product_options[x],
            key="param_filter_product"
        )
    
    with col2:
        try:
            strategies = list_strategies()
            strategy_options = ["全部策略"] + list(strategies.keys())
            filter_strategy = st.selectbox(
                "筛选策略 *",
                options=strategy_options,
                key="param_filter_strategy"
            )
        except Exception as e:
            st.error(f"获取策略列表失败: {e}")
            filter_strategy = "全部策略"
    
    with col3:
        limit = st.number_input("显示条数", value=100, min_value=1, max_value=500, key="param_result_limit")
    
    if filter_strategy == "全部策略":
        st.warning("请选择一个策略进行参数对比")
        return
    
    # 查询该策略的所有回测结果
    summaries = list_backtest_summaries(
        product_id=filter_product_id if filter_product_id > 0 else None,
        strategy_key=filter_strategy,
        limit=limit
    )
    
    if not summaries:
        st.info(f"策略 '{filter_strategy}' 暂无回测结果")
        return
    
    # 按参数ID分组显示
    param_groups = {}
    for s in summaries:
        param_set_id = s.get('param_set_id', 'default')
        if param_set_id not in param_groups:
            param_groups[param_set_id] = []
        param_groups[param_set_id].append(s)
    
    # 显示对比结果表格
    st.subheader(f"参数对比结果 - {filter_strategy}")
    st.caption(f"共找到 {len(summaries)} 条回测记录，{len(param_groups)} 个不同的参数组合")
    
    # 构建对比表格
    df_data = []
    for param_set_id, group_summaries in param_groups.items():
        # 取最新的一条记录作为代表（或者可以取平均值）
        latest = max(group_summaries, key=lambda x: x.get('created_at', ''))
        
        # 解析参数内容
        param_json_str = latest.get('param_json')
        params_display = "默认参数"
        
        # 如果 param_json 为空或None，尝试从策略获取默认参数
        if not param_json_str:
            try:
                strategy_key = latest.get('strategy_key')
                strategy_version = latest.get('strategy_version')
                if strategy_key:
                    strategy_info = get_strategy_info(strategy_key, strategy_version)
                    default_params = strategy_info.get('default_params', {})
                    if default_params:
                        param_json_str = json.dumps(default_params, ensure_ascii=False)
            except Exception as e:
                logger.debug(f"获取策略默认参数失败: {e}")
        
        if param_json_str:
            try:
                params_dict = json.loads(param_json_str)
                # 格式化参数显示：key: value 的形式，每行一个参数
                param_lines = []
                for key, value in sorted(params_dict.items()):
                    if isinstance(value, (list, dict)):
                        value_str = json.dumps(value, ensure_ascii=False)
                    else:
                        value_str = str(value)
                    param_lines.append(f"{key}: {value_str}")
                params_display = "\n".join(param_lines) if param_lines else "默认参数"
            except (json.JSONDecodeError, TypeError):
                params_display = param_json_str[:100] if param_json_str else "默认参数"
        
        # 计算绝对收益
        initial_cash = float(latest.get('initial_cash', 0) or 0)
        final_value = float(latest.get('final_value', 0) or 0)
        absolute_profit = final_value - initial_cash
        
        # 计算平均月成交
        start_date = latest.get('start_date')
        end_date = latest.get('end_date')
        if start_date and end_date:
            if isinstance(start_date, str):
                from datetime import datetime
                start_date = datetime.strptime(start_date, '%Y-%m-%d').date()
            if isinstance(end_date, str):
                from datetime import datetime
                end_date = datetime.strptime(end_date, '%Y-%m-%d').date()
            if hasattr(end_date, '__sub__') and hasattr(start_date, '__sub__'):
                days_diff = (end_date - start_date).days
            else:
                days_diff = 0
        else:
            days_diff = 0
        months = max(days_diff / 30.0, 1.0 / 30.0)
        trade_count = latest.get('trade_count', 0) or 0
        avg_monthly_trades = trade_count / months if months > 0 else 0.0
        
        df_data.append({
            '参数ID': param_set_id,
            '策略参数': params_display,  # 添加参数内容列
            '汇总ID': latest['id'],
            '开始日期': latest['start_date'],
            '结束日期': latest['end_date'],
            '年化收益': f"{float(latest.get('annual_return', 0)):.2%}",
            '总收益率': f"{float(latest.get('total_return', 0)):.2%}",
            '最大回撤': f"{float(latest.get('max_drawdown', 0)):.2%}",
            '成交次数': trade_count,
            '累计投入': f"{initial_cash:,.2f}",
            '最终资产': f"{final_value:,.2f}",
            '绝对收益': f"{absolute_profit:,.2f}",
            '手续费总额': f"{float(latest.get('total_fees', 0) or 0):,.2f}",
            '手续费占比': f"{float(latest.get('fee_ratio', 0)):.2%}",
            '平均月成交': f"{avg_monthly_trades:.1f}",
            '等待池比例': f"{float(latest.get('wait_pool_ratio', 0)):.2%}",
            '创建时间': latest.get('created_at', '')
        })
    
    if df_data:
        df = pd.DataFrame(df_data)
        # 按年化收益排序（降序）
        if '年化收益' in df.columns:
            # 提取数值进行排序
            df['_sort_key'] = df['年化收益'].str.rstrip('%').astype(float)
            df = df.sort_values('_sort_key', ascending=False)
            df = df.drop('_sort_key', axis=1)
        
        # 调整列顺序，将策略参数放在前面
        cols = df.columns.tolist()
        if '策略参数' in cols:
            cols.remove('策略参数')
            cols.insert(1, '策略参数')  # 放在参数ID后面
            df = df[cols]
        
        st.dataframe(df, width='stretch', height=400)
    else:
        st.info("暂无数据")


# ============================================================
# 主程序
# ============================================================
def main():
    # 初始化调度器（只在第一次运行时启动）
    if 'scheduler_initialized' not in st.session_state:
        try:
            from core.scheduler_service import start_scheduler, is_scheduler_running
            if not is_scheduler_running():
                if start_scheduler():
                    st.session_state.scheduler_initialized = True
                    logger.info("调度器已在 UI 中启动")
                else:
                    logger.warning("调度器启动失败")
            else:
                st.session_state.scheduler_initialized = True
                logger.info("调度器已在运行")
        except Exception as e:
            logger.error(f"初始化调度器失败: {e}", exc_info=True)
            st.session_state.scheduler_initialized = False
    
    # 侧边栏导航
    st.sidebar.title("💰 财富中枢")
    
    # 一键日更按钮（标题下方，紧凑布局）
    if st.sidebar.button("🔄 一键日更", key="sidebar_daily_update", use_container_width=True):
        with st.spinner("正在同步数据..."):
            try:
                result = collect_nav_and_build_snapshots()
                # 自动更新账户余额（因为 PRODUCT_SUB 账户的余额 = 份额 × 净值）
                from data.account_service import recalculate_all_account_balances
                recalculate_all_account_balances()
                st.sidebar.success(f"✅ 同步完成！")
                st.rerun()
            except Exception as e:
                st.sidebar.error(f"❌ 同步失败: {e}")
    
    # 重新计算账户余额按钮
    if st.sidebar.button("💰 重新计算账户余额", key="sidebar_recalc_balances", use_container_width=True):
        with st.spinner("正在重新计算所有账户余额..."):
            try:
                from data.account_service import recalculate_all_account_balances
                balances = recalculate_all_account_balances()
                st.sidebar.success(f"✅ 已重新计算 {len(balances)} 个账户余额！")
                st.rerun()
            except Exception as e:
                st.sidebar.error(f"❌ 计算失败: {e}")
    
    st.sidebar.divider()
    
    page = st.sidebar.radio(
        "导航",
        ["📊 Dashboard", "💼 资产详情", "📝 生活记账", "📈 理财录入", "📋 订单管理", 
         "🏷️ 产品管理", "💳 账户管理", "💰 资金池规则", "🔬 策略实验室"],
        label_visibility="collapsed"
    )
    
    st.sidebar.divider()
    st.sidebar.caption("MyDCA-Board v2.0")
    st.sidebar.caption(f"📅 {date.today()}")
    
    # 页面路由
    if page == "📊 Dashboard":
        page_dashboard()
    elif page == "💼 资产详情":
        page_asset_details()
    elif page == "📝 生活记账":
        page_ledger()
    elif page == "📈 理财录入":
        page_invest()
    elif page == "📋 订单管理":
        page_orders()
    elif page == "🏷️ 产品管理":
        page_product_management()
    elif page == "💳 账户管理":
        page_account_management()
    elif page == "💰 资金池规则":
        page_pool_rules()
    elif page == "🔬 策略实验室":
        page_strategy_lab()


if __name__ == "__main__":
    main()

