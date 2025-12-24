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

# 添加 src 目录到路径
sys.path.insert(0, str(Path(__file__).parent / "src"))

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
# Page 1: Dashboard
# ============================================================
def page_dashboard():
    st.markdown('<p class="main-header">📊 Dashboard</p>', unsafe_allow_html=True)
    
    # 操作按钮区
    col1, col2, col3, col4 = st.columns(4)
    
    with col1:
        if st.button("🔄 一键日更", use_container_width=True, type="primary"):
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
        if st.button("✅ 运行校验", use_container_width=True):
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
        
        st.dataframe(df_display, use_container_width=True, hide_index=True)
    else:
        st.info("暂无账户余额数据，请点击「一键日更」生成快照")
    
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
            
            # 显示时四舍五入到两位小数
            shares_display = _round_2(row.get("shares"))
            total_pnl = _round_2(row.get("total_pnl"))
            
            row["shares"] = f"{shares_display:.2f}"
            row["value"] = f"{value:.2f}"
            row["total_pnl"] = f"{total_pnl:.2f}"
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
        
        st.dataframe(df_display, use_container_width=True, hide_index=True)
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
            use_container_width=True, 
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
                if st.button("💾 保存修改", type="primary", key="save_ledger_edit", use_container_width=True):
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
                if st.button("🗑️ 删除", type="secondary", key="delete_ledger_edit", use_container_width=True):
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
                st.info(f"💡 预计手续费: ¥{fee:.2f}（费率 {product['buy_fee_rate']*100:.2f}%）")
                st.info(f"💡 净申购额: ¥{Decimal(str(buy_amount)) - fee:.2f}")
                
                buy_fee_override = st.number_input("手续费（可覆盖）", min_value=0.0, value=float(fee), step=0.01, key="buy_fee")
        
        with col2:
            if buy_product:
                product = product_dict[buy_product]
                dates = calc_trade_dates(product['code'])
                if dates:
                    st.info(f"📅 交易日期: {dates['trade_date']}")
                    st.info(f"📅 净值日期: {dates['nav_date']}")
                    st.info(f"📅 确认日期: {dates['confirm_date']}")
            
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
                        requested_at = datetime.now().replace(
                            hour=int(time_parts[0]),
                            minute=int(time_parts[1]),
                            second=int(time_parts[2]) if len(time_parts) > 2 else 0
                        )
                    except:
                        requested_at = datetime.now()
                    
                    order_id = add_buy_debit(
                        product_code=product['code'],
                        amount=Decimal(str(buy_amount)),
                        fee=Decimal(str(buy_fee_override)) if 'buy_fee_override' in dir() else None,
                        requested_at=requested_at,
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
            if redeem_product:
                product = product_dict[redeem_product]
                from core.invest_service import calc_trade_date as calc_td, calc_confirm_date as calc_cd
                from datetime import datetime as dt
                trade_date = calc_td(dt.now(), product.get('cutoff_time', '15:00'))
                confirm_date = calc_cd(trade_date, product.get('sell_confirm_offset', 1))
                st.info(f"📅 交易日期: {trade_date}")
                st.info(f"📅 确认日期: {confirm_date}")
            
            redeem_note = st.text_input("备注（可选）", key="redeem_note")
        
        if st.button("提交赎回发起", type="primary", key="submit_redeem"):
            if not redeem_product or redeem_shares <= 0:
                st.error("❌ 请选择产品并输入份额！")
            else:
                try:
                    product = product_dict[redeem_product]
                    order_id = add_redeem_request(
                        product_code=product['code'],
                        shares=Decimal(str(redeem_shares)),
                        holding_days=redeem_holding_days,
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
            elif action in ['sell', 'sell_confirm'] and shares:
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
            
            raw_records.append(r)
            rows.append({
                'ID': r.get('id'),
                '时间': time_str,
                '产品': product_code,
                '类型': ACTION_DISPLAY_MAP.get(action, action),
                '金额': format_invest_amount(amount, is_shares_increase, is_pending),
                '份额': shares,
                '产品份额': f"{product_shares_after:.2f}",  # 该产品在此交易后的累计份额
                '备注': r.get('note', ''),
                '_account': account,
                '_action': action,
                '_is_pending': is_pending
            })
        
        if rows:
            df_tx = pd.DataFrame(rows)
            # 保存原始索引供分页后使用
            df_tx['_original_idx'] = range(len(df_tx))
            
            # 分页
            df_page = paginate_dataframe(df_tx, "invest_tx_records", page_size=50)
            original_indices = df_page['_original_idx'].tolist()
            
            display_cols = ['ID', '时间', '产品', '类型', '金额', '份额', '产品份额', '备注']
            
            # 为金额列着色，待确认类型使用白色
            # 理财视角（按份额）：份额增加（买入/分红）红色，份额减少（卖出）绿色
            def color_tx_amount(row):
                is_pending = df_page.loc[row.name, '_is_pending'] if '_is_pending' in df_page.columns else False
                return [color_invest_amount(val, is_pending) if col == '金额' else '' for col, val in row.items()]
            
            # 显示带颜色和行选择的表格（不显示原始索引列）
            styled_df = df_page[display_cols].style.apply(color_tx_amount, axis=1)
            event = st.dataframe(
                styled_df, 
                use_container_width=True, 
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
                    if st.button("💾 保存修改", type="primary", key="save_tx_edit", use_container_width=True):
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
                    if st.button("🗑️ 删除", type="secondary", key="delete_tx_edit", use_container_width=True):
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
            orig_amount = order.get('amount', '')
            display_amount = f"{float(orig_amount):.2f}" if orig_amount else '-'
            
            rows.append({
                '订单号': order.get('order_id', ''),
                '产品代码': product_code,
                '类型': '买入扣款' if order_type == 'buy_debit' else '赎回发起',
                '金额': display_amount,
                '份额': display_shares,
                '确认日期': confirm_date,
                '状态': order.get('status', ''),
                '净值': preview.get('nav', '-') if preview.get('success') else '-',
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
            use_container_width=True,
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
            if st.button("⚡ 结算今日可结算", use_container_width=True):
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
            if st.button("📋 结算全部到期", use_container_width=True):
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
            
            # 已完成的订单，从交易记录中获取份额和净值
            display_shares = order.get('shares', '') or ''
            display_nav = ''
            
            if status == 'done':
                # 从 transactions 中查找对应的 buy_confirm 记录
                from core.invest_service import list_recent_transactions
                order_id = order.get('order_id', '')
                txs = list_recent_transactions(100)
                for tx in txs:
                    if tx.get('order_id') == order_id and tx.get('action') in ['buy_confirm', 'sell_confirm']:
                        display_shares = tx.get('shares', '') or display_shares
                        display_nav = tx.get('nav', '') or ''
                        break
            elif status == 'pending':
                # 待处理的订单，预览计算份额
                preview = preview_settle(order['order_id'])
                if preview.get('success') and preview.get('shares'):
                    display_shares = f"{preview.get('shares'):.2f}"
                    display_nav = preview.get('nav', '')
            
            # 格式化份额（两位小数）
            if display_shares and display_shares != '-':
                try:
                    display_shares = f"{float(display_shares):.2f}"
                except:
                    pass
            
            # 格式化金额（两位小数）
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
        
        st.dataframe(df_page, use_container_width=True, hide_index=True)
    else:
        st.info("暂无订单")


# ============================================================
# 主程序
# ============================================================
def main():
    # 侧边栏导航
    st.sidebar.title("💰 财富中枢")
    st.sidebar.divider()
    
    page = st.sidebar.radio(
        "导航",
        ["📊 Dashboard", "📝 生活记账", "📈 理财录入", "📋 订单结算"],
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


if __name__ == "__main__":
    main()

