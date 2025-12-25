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
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from typing import Any

import streamlit as st
import pandas as pd
import logging

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
    read_latest_daily, read_latest_daily_balance,
    get_portfolio_summary, read_balance_by_group
)
from core.daily_balance import create_daily_balance_snapshot
from data.config_loader import get_sell_fee_rate, get_product

# 页面配置
st.set_page_config(
    page_title="财富中枢",
    page_icon="💰",
    layout="wide",
    initial_sidebar_state="expanded"
)

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

# 账户ID到中文名称的映射
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

# 理财操作类型映射（统一显示）
ACTION_DISPLAY_MAP = {
    'buy_debit': '买入待确认',
    'buy_confirm': '买入确认', 
    'buy': '买入',
    'sell': '卖出',
    'sell_confirm': '卖出确认',
    'redeem_request': '卖出待确认',
    'dividend': '分红'
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
    
    # 产品选择
    product_options = {p['id']: f"{p.get('code', '')} - {p.get('name') or p.get('product_name', '')} ({'场内' if p.get('channel') == 'EXCHANGE' else '场外'})" 
                      for p in all_products}
    selected_product_id = st.selectbox("选择产品", 
                                     options=list(product_options.keys()),
                                     format_func=lambda x: product_options[x],
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
        
        # 手动刷新按钮（保留，用于立即刷新）
        if st.button("🔄 立即刷新", key="manual_refresh_quote"):
            with st.spinner("正在获取最新行情..."):
                try:
                    if channel == 'EXCHANGE':
                        fetch_and_save_realtime_quote(selected_product_id, product_code)
                        if product.get('is_qdii'):
                            fetch_and_save_qdii_premium(selected_product_id, product_code)
                    else:
                        # 场外产品触发净值采集
                        from core.nav_collector import collect_and_store
                        collect_and_store()
                    st.success("✅ 行情已更新")
                    st.rerun()
                except Exception as e:
                    st.error(f"❌ 刷新失败: {e}")
        
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
                st.metric("最新价", f"{price:.4f}")
            else:
                st.metric("最新价", "N/A")
        with col2:
            # IOPV 实时估值
            iopv_val = latest_quote.get('iopv')
            if iopv_val is not None:
                iopv = float(iopv_val)
                st.metric("IOPV实时估值", f"{iopv:.4f}")
            else:
                st.metric("IOPV实时估值", "N/A")
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
                st.metric("溢价率", f"{premium_rate:.2f}%", delta=label, delta_color=delta_color)
            else:
                st.metric("溢价率", "N/A")
        with col4:
            pct_chg_val = latest_quote.get('pct_chg')
            if pct_chg_val is not None:
                pct_chg = float(pct_chg_val) * 100
                st.metric("涨跌幅", f"{pct_chg:.2f}%", delta=f"{pct_chg:.2f}%")
            else:
                st.metric("涨跌幅", "N/A")
        
        # ========== ② 基础价格时间序列（用于高低位判断） ==========
        st.divider()
        st.markdown("**📊 基础价格时间序列**")
        col1, col2, col3, col4, col5 = st.columns([1, 1, 1, 1, 1.2])  # 给行情时间更多空间
        with col1:
            open_val = latest_quote.get('open')
            if open_val is not None:
                open_price = float(open_val)
                st.metric("开盘价", f"{open_price:.4f}")
            else:
                st.metric("开盘价", "N/A")
        with col2:
            high_val = latest_quote.get('high')
            if high_val is not None:
                high_price = float(high_val)
                st.metric("最高价", f"{high_price:.4f}")
            else:
                st.metric("最高价", "N/A")
        with col3:
            low_val = latest_quote.get('low')
            if low_val is not None:
                low_price = float(low_val)
                st.metric("最低价", f"{low_price:.4f}")
            else:
                st.metric("最低价", "N/A")
        with col4:
            prev_close_val = latest_quote.get('prev_close')
            if prev_close_val is not None:
                prev_close = float(prev_close_val)
                st.metric("昨收价", f"{prev_close:.4f}")
            else:
                st.metric("昨收价", "N/A")
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
                st.metric("成交量", f"{volume:,.0f}")
            else:
                st.metric("成交量", "N/A")
        with col2:
            amount_val = latest_quote.get('amount')
            if amount_val is not None:
                amount = float(amount_val)
                st.metric("成交额", f"{amount:,.2f}")
            else:
                st.metric("成交额", "N/A")
        with col3:
            turnover_rate_val = latest_quote.get('turnover_rate')
            if turnover_rate_val is not None:
                turnover_rate = float(turnover_rate_val) * 100
                st.metric("换手率", f"{turnover_rate:.2f}%")
            else:
                st.metric("换手率", "N/A")
        with col4:
            amplitude_val = latest_quote.get('amplitude')
            if amplitude_val is not None:
                amplitude = float(amplitude_val) * 100
                st.metric("振幅", f"{amplitude:.2f}%")
            else:
                st.metric("振幅", "N/A")
        
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
                        st.metric("溢价率", f"{premium_rate:.2f}%")
                    with col2:
                        st.metric("IOPV", f"{iopv:.4f}")
                    with col3:
                        # 买入建议
                        if premium_rate <= 1:
                            st.metric("买入建议", "✅ 正常买入", delta="100%")
                        elif premium_rate <= 3:
                            st.metric("买入建议", "⚠️ 买入一半", delta="50%")
                        else:
                            st.metric("买入建议", "❌ 暂停买入", delta="0%")
                else:
                    st.info("暂无溢价率数据")
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
    st.markdown('<p class="main-header">📊 Dashboard</p>', unsafe_allow_html=True)
    
    # P0-6: 口径说明
    with st.expander("ℹ️ 系统口径说明", expanded=False):
        st.info("""
        **系统以交易流水+净值/行情为唯一真值；平台显示可能因舍入/口径存在差异。**
        
        **精度规范**：
        - 份额 (shares): 保留 6 位小数
        - 金额 (amount/cost/value/pnl/fee): 保留 2 位小数
        - 净值 (nav): 保留 4 位小数
        
        **计算口径**：
        - 成本使用"净申购额"口径（amount - fee）
        - 日变动 (pnl_day) 仅反映市场波动，剔除资金流影响
        - 总盈亏 (total_pnl) = 总资产 + 累计赎回 - 累计投入本金
        """)
    
    # 操作按钮区
    col1, col2, col3, col4 = st.columns(4)
    
    with col1:
        if st.button("🔄 一键日更", width='stretch', type="primary"):
            with st.spinner("正在同步数据..."):
                try:
                    result = collect_nav_and_build_snapshots()
                    st.success(f"✅ 同步完成！生成 {result.balance_records} 条账户记录")
                    if result.errors:
                        st.warning(f"⚠️ 有 {len(result.errors)} 个错误: {result.errors[:3]}")
                    st.rerun()
                except Exception as e:
                    st.error(f"❌ 同步失败: {e}")
    
    with col2:
        if st.button("✅ 运行校验", width='stretch'):
            with st.spinner("正在校验..."):
                ledger_result = validate_ledger()
                invest_result = validate_transactions_orders()
                
                if ledger_result.success and invest_result.success:
                    st.success("✅ 校验通过！")
                else:
                    errors = ledger_result.errors + invest_result.errors
                    st.error(f"❌ 校验失败: {len(errors)} 个错误")
                    for err in errors[:5]:
                        st.write(f"  - {err}")
    
    with col4:
        st.write("")  # 占位
    
    st.divider()
    
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
    
    # 读取数据
    balance_data = read_latest_daily_balance()
    
    if balance_data:
        # 筛选
        if group_filter != "全部":
            keyword = group_filter.split("(")[1].rstrip(")")
            balance_data = [r for r in balance_data if keyword in r.get('account_id', '').lower()]
        
        # 转换为 DataFrame
        df_balance = pd.DataFrame(balance_data)
        
        # 选择显示的列（添加收益字段，去掉备注）
        display_cols = ['account_name', 'account_type', 'balance', 'product_value', 'diff', 
                       'yesterday_pnl', 'unrealized_pnl', 'total_pnl']
        display_cols = [c for c in display_cols if c in df_balance.columns]
        
        # 重命名列
        col_names = {
            'account_name': '账户名称',
            'account_type': '类型',
            'balance': '余额',
            'product_value': '产品市值',
            'diff': '差异',
            'yesterday_pnl': '昨日收益',
            'unrealized_pnl': '持有收益',
            'total_pnl': '累计收益'
        }
        
        df_display = df_balance[display_cols].copy()
        df_display = df_display.rename(columns=col_names)
        
        st.dataframe(df_display, width='stretch', hide_index=True)
    else:
        st.info("暂无账户余额数据，请点击「一键日更」生成快照")
    
    st.divider()
    
    # 产品行情
    st.subheader("📈 产品行情")
    render_product_quote()
    
    st.divider()
    
    # 产品持仓表格
    st.subheader("📊 产品持仓")
    
    daily_data = read_latest_daily()
    
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
            
            # P0-6: 统一精度展示
            # 份额保留6位，金额保留2位，净值保留4位
            from src.utils.decimal_utils import format_shares, format_money, format_nav
            
            shares_display = format_shares(row.get("shares"), places=6)
            nav_display = format_nav(row.get("nav"), places=4)
            total_pnl = format_money(row.get("total_pnl"), places=2)
            
            row["shares"] = shares_display
            row["nav"] = nav_display
            row["value"] = format_money(value, places=2)
            row["total_pnl"] = total_pnl
            return row
        
        df_daily = df_daily.apply(_recalc_row, axis=1)
        
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
                # 刷新账户余额快照
                try:
                    create_daily_balance_snapshot()
                except:
                    pass
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
                try:
                    create_daily_balance_snapshot()
                except:
                    pass
            except Exception as e:
                st.error(f"❌ 保存失败: {e}")
    
    with tab3:
        st.subheader("新增转账")
        
        col1, col2 = st.columns(2)
        
        with col1:
            transfer_from = st.selectbox("转出账户", account_names, key="transfer_from")
            transfer_to = st.selectbox("转入账户", account_names, key="transfer_to")
            transfer_amount = st.number_input("金额", min_value=0.01, step=0.01, key="transfer_amount")
        
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
                    try:
                        create_daily_balance_snapshot()
                    except:
                        pass
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
                label = f"{e.get('event_time', '')[:16]} | ¥{e.get('amount', '')} | {e.get('category_l1', '')} | {e.get('note', '')[:20]}"
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
                        try:
                            create_daily_balance_snapshot()
                        except:
                            pass
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
                        from data.data_store import delete_ledger
                        if delete_ledger(selected_record['id']):
                            st.success("✅ 删除成功！")
                            st.session_state.pop('pending_delete_ledger_id', None)
                            st.rerun()
                        else:
                            st.error("❌ 删除失败")
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
    
    tab1, tab2, tab3 = st.tabs(["💳 买入扣款", "📤 赎回发起", "📝 补录历史"])
    
    # 获取产品选项
    product_options = get_product_options()
    product_dict = {f"{p['code']} - {p['name']}": p for p in product_options}
    product_names = list(product_dict.keys())
    
    with tab1:
        st.subheader("买入扣款")
        
        col1, col2 = st.columns(2)
        
        with col1:
            buy_product = st.selectbox("选择产品", product_names, key="buy_product")
            buy_amount = st.number_input("扣款金额（含手续费）", min_value=0.01, step=100.0, key="buy_amount")
            
            if buy_product and buy_amount > 0:
                product = product_dict[buy_product]
                fee = calc_buy_fee(product['code'], Decimal(str(buy_amount)))
                buy_fee_rate = float(product.get('buy_fee_rate') or 0)
                st.info(f"💡 预计手续费: ¥{fee:.2f}（费率 {buy_fee_rate*100:.2f}%）")
                st.info(f"💡 净申购额: ¥{Decimal(str(buy_amount)) - fee:.2f}")
                
                buy_fee_override = st.number_input("手续费（可覆盖）", min_value=0.0, value=float(fee), step=0.01, key="buy_fee")
        
        with col2:
            # 交易日期输入（可编辑，默认当前日期）
            buy_trade_date = st.date_input(
                "交易日期", 
                value=date.today(), 
                key="buy_trade_date",
                help="扣款发生的日期，修改后会自动计算净值日期和确认日期"
            )
            
            if buy_product:
                product = product_dict[buy_product]
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
                key="buy_time",
                help="扣款发生的时间，精确到秒"
            )
            buy_note = st.text_input("备注（可选）", key="buy_note")
        
        if st.button("提交买入扣款", type="primary", key="submit_buy"):
            if not buy_product or buy_amount <= 0:
                st.error("❌ 请选择产品并输入金额！")
            else:
                try:
                    product = product_dict[buy_product]
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
                    
                    order_id = add_buy_debit(
                        product_code=product['code'],
                        amount=Decimal(str(buy_amount)),
                        fee=Decimal(str(buy_fee_override)) if 'buy_fee_override' in dir() else None,
                        requested_at=requested_at,
                        trade_date=buy_trade_date,
                        note=buy_note or None
                    )
                    
                    # 同时在生活记账中添加一笔支出记录
                    debit_account = get_tx_account(product['code'], 'buy_debit')  # 扣款账户
                    event_time = requested_at.strftime('%Y-%m-%d %H:%M:%S')
                    add_expense(
                        account_from=debit_account,
                        amount=Decimal(str(buy_amount)),
                        category_l1="理财投资",
                        category_l2=buy_category_l2,
                        event_time=event_time,
                        note=f"{product['name']} (订单号: {order_id})"
                    )
                    
                    st.success(f"✅ 买入扣款已提交！订单号: {order_id}")
                    try:
                        collect_nav_and_build_snapshots(silent=True)
                    except:
                        pass
                except Exception as e:
                    st.error(f"❌ 提交失败: {e}")
    
    with tab2:
        st.subheader("赎回发起")
        
        col1, col2 = st.columns(2)
        
        with col1:
            redeem_product = st.selectbox("选择产品", product_names, key="redeem_product")
            redeem_shares = st.number_input("赎回份额", min_value=0.01, step=100.0, key="redeem_shares")
            redeem_holding_days = st.number_input("持有天数", min_value=1, step=1, value=30, key="redeem_holding_days")
            
            if redeem_product:
                product = product_dict[redeem_product]
                product_config = get_product(product['code'])
                if product_config:
                    fee_rate = get_sell_fee_rate(product_config, redeem_holding_days)
                    st.info(f"💡 赎回费率: {float(fee_rate)*100:.2f}%")
        
        with col2:
            # 交易日期输入（可编辑，默认当前日期）
            redeem_trade_date = st.date_input(
                "交易日期", 
                value=date.today(), 
                key="redeem_trade_date",
                help="赎回发生的日期，修改后会自动计算确认日期"
            )
            
            if redeem_product:
                product = product_dict[redeem_product]
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
            account_options = get_account_options()
            account_dict = {acc['name']: acc['id'] for acc in account_options}
            account_names = list(account_dict.keys())
            
            # 默认选择余利宝理财金
            default_redeem_account_name = '余利宝理财金'
            default_redeem_account_idx = 0
            if default_redeem_account_name in account_names:
                default_redeem_account_idx = account_names.index(default_redeem_account_name)
            
            redeem_account_name = st.selectbox(
                "赎回账户", 
                account_names, 
                index=default_redeem_account_idx,
                key="redeem_account"
            )
            redeem_account = account_dict[redeem_account_name]
            
            redeem_note = st.text_input("备注（可选）", key="redeem_note")
        
        if st.button("提交赎回发起", type="primary", key="submit_redeem"):
            if not redeem_product or redeem_shares <= 0:
                st.error("❌ 请选择产品并输入份额！")
            else:
                try:
                    product = product_dict[redeem_product]
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
                    
                    order_id = add_redeem_request(
                        product_code=product['code'],
                        shares=Decimal(str(redeem_shares)),
                        holding_days=redeem_holding_days,
                        requested_at=requested_at,
                        trade_date=redeem_trade_date,
                        redeem_account=redeem_account,
                        note=redeem_note or None
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
            history_product = st.selectbox("选择产品", product_names, key="history_product")
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
                    product = product_dict[history_product]
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
    product_filter_options = ['全部'] + [f"{p['code']} - {p['name']}" for p in product_options]
    tx_product_filter = st.selectbox("筛选产品", product_filter_options, key="tx_product_filter")
    
    recent_tx = list_recent_transactions(200)  # 显示更多记录
    
    # 产品过滤
    if tx_product_filter != '全部' and recent_tx:
        filter_code = tx_product_filter.split(' - ')[0]
        recent_tx = [r for r in recent_tx if r.get('product_code') == filter_code]
    
    if recent_tx:
        # 计算每个产品的当前份额，用于倒推
        from core.holdings_calculator import get_all_product_positions
        from datetime import date as date_cls
        current_positions = get_all_product_positions(date_cls.today().strftime('%Y-%m-%d'))
        # {product_code: (shares, cost)}
        product_shares = {code: pos[0] for code, pos in current_positions.items()}
        
        # recent_tx 已经是按时间倒序的，从最新开始倒推份额
        for r in recent_tx:
            action = r.get('action', '')
            product_code = r.get('product_code', '')
            shares = r.get('shares', '')
            
            # 当前记录后的产品份额
            r['_product_shares_after'] = product_shares.get(product_code, Decimal('0'))
            
            # 倒推：计算这笔交易前的份额
            if action in ['buy', 'buy_confirm'] and shares:
                try:
                    product_shares[product_code] = product_shares.get(product_code, Decimal('0')) - Decimal(str(shares))
                except:
                    pass
            elif action in ['sell', 'sell_confirm', 'redeem_request'] and shares:
                try:
                    product_shares[product_code] = product_shares.get(product_code, Decimal('0')) + Decimal(str(shares))
                except:
                    pass
        
        # 处理数据（按原来的倒序显示）
        # 理财视角（按份额变化）：买入/分红 = 份额增加（红色+），卖出 = 份额减少（绿色-）
        shares_increase_actions = ['buy', 'buy_confirm', 'dividend']  # 份额增加类（红色）
        
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
            # 购买时：保持原有逻辑（份额增加用红色+，份额减少用绿色-）
            if action in ['sell_confirm', 'redeem_request']:
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
                
                # 准备产品下拉框选项
                product_code_to_name = {p['code']: f"{p['code']} - {p['name']}" for p in product_options}
                product_name_to_code = {f"{p['code']} - {p['name']}": p['code'] for p in product_options}
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
                    edit_nav = st.number_input("净值", value=float(selected_record.get('nav', 0) or 0), step=0.0001, format="%.4f", key="tx_edit_nav")
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
                            'note': edit_note
                        }
                        if update_transaction_entry(selected_record['id'], updated_record):
                            st.success("✅ 保存成功！")
                            st.rerun()
                        else:
                            st.error("❌ 保存失败")
                
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
                            from data.data_store import delete_transaction
                            if delete_transaction(selected_record['id']):
                                st.success("✅ 删除成功！")
                                st.session_state.pop('pending_delete_tx_id', None)
                                st.rerun()
                            else:
                                st.error("❌ 删除失败")
                    with col_cancel:
                        if st.button("❌ 取消", key="cancel_delete_tx"):
                            st.session_state.pop('pending_delete_tx_id', None)
                            st.rerun()
        else:
            st.info("暂无匹配的理财记录")
    else:
        st.info("暂无理财记录")


# ============================================================
# Page 4: 订单结算
# ============================================================
def page_orders():
    st.markdown('<p class="main-header">📋 订单结算</p>', unsafe_allow_html=True)
    
    st.divider()
    
    # 待结算订单（带单个确认功能）
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
    
    # 批量结算按钮（移到底部，不常用）
    with st.expander("📋 批量结算（高级）", expanded=False):
        st.caption("⚠️ 批量结算会一次性处理所有到期订单，建议使用上方的单个确认功能")
        
        col1, col2 = st.columns(2)
        
        with col1:
            if st.button("⚡ 结算今日可结算", width='stretch'):
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
    
        with col2:
            if st.button("📋 结算全部到期", width='stretch'):
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
    
    st.divider()
    
    # 所有订单
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
        
        # 选择订单进行重新结算
        if len(df_page) > 0:
            st.caption("💡 选择已完成的订单可以重新结算（删除确认记录并重置订单状态）")
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
                        
                        success_count = 0
                        error_count = 0
                        
                        for order in selected_orders:
                            order_id = order.get('order_id')
                            try:
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
                            st.success(f"✅ 成功重置 {success_count} 个订单，现在可以重新结算了！")
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
            product_options = {p.get('id'): f"{p.get('code', '')} - {p.get('name') or p.get('product_name', '')}" for p in products}
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
                        product_options[p['id']] = f"{p.get('code', '')} - {p.get('name') or p.get('product_name', '')}"
                    
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
            product_options[p['id']] = f"{p.get('code', '')} - {p.get('name') or p.get('product_name', '')}"
        
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
        
        # 账户选择
        accounts = get_accounts(account_type='CASH', is_active=True)
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
            product_options[p['id']] = f"{p.get('code', '')} - {p.get('name') or p.get('product_name', '')}"
        
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
    st.sidebar.divider()
    
    page = st.sidebar.radio(
        "导航",
        ["📊 Dashboard", "📝 生活记账", "📈 理财录入", "📋 订单结算", 
         "🏷️ 产品管理", "💳 账户管理", "💰 资金池规则"],
        label_visibility="collapsed"
    )
    
    st.sidebar.divider()
    st.sidebar.caption("MyDCA-Board v2.0")
    st.sidebar.caption(f"📅 {date.today()}")
    
    # 页面路由
    if page == "📊 Dashboard":
        page_dashboard()
    elif page == "📝 生活记账":
        page_ledger()
    elif page == "📈 理财录入":
        page_invest()
    elif page == "📋 订单结算":
        page_orders()
    elif page == "🏷️ 产品管理":
        page_product_management()
    elif page == "💳 账户管理":
        page_account_management()
    elif page == "💰 资金池规则":
        page_pool_rules()


if __name__ == "__main__":
    main()

