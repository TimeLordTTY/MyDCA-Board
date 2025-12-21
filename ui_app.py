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
from decimal import Decimal, InvalidOperation

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


# 账户组过滤选项
ACCOUNT_GROUP_FILTERS = {
    '全部': None,
    '余利宝': ['ylb_life', 'ylb_finance'],
    '稳利宝': ['wenlibao_project', 'wenlibao_safe', 'wenlibao_rent', 'wenlibao_finance', 'wenlibao_active'],
    '小荷包': ['couple_pocket']
}

# 产品到支付账户的映射（基金定投默认从余利宝理财金扣）
PRODUCT_ACCOUNT_MAP = {
    'FBAE41126E': 'wenlibao_finance',  # 稳利宝 -> 稳利宝理财金
    '000686': 'couple_pocket',  # 小荷包货币基金
}
DEFAULT_FUND_ACCOUNT = 'ylb_finance'  # 其他基金默认从余利宝理财金

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
    'buy_debit': '买入',
    'buy_confirm': '买入确认', 
    'buy': '买入',
    'sell': '卖出',
    'sell_confirm': '卖出确认',
    'dividend': '分红'
}


def get_account_name(account_id: str) -> str:
    """获取账户中文名称"""
    return ACCOUNT_NAME_MAP.get(account_id, account_id or '')


def get_tx_account(product_code: str) -> str:
    """获取理财交易对应的支付账户"""
    return PRODUCT_ACCOUNT_MAP.get(product_code, DEFAULT_FUND_ACCOUNT)


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


def format_colored_amount(amount, is_expense: bool) -> str:
    """格式化带符号的金额"""
    try:
        val = float(amount) if amount else 0
        if is_expense:
            return f"-{abs(val):.2f}"
        else:
            return f"+{abs(val):.2f}"
    except:
        return str(amount) if amount else ''


def color_amount(val):
    """为金额列着色：负数红色，正数绿色"""
    if pd.isna(val) or val == '':
        return ''
    val_str = str(val)
    if val_str.startswith('-'):
        return 'color: #dc3545'  # 红色
    elif val_str.startswith('+'):
        return 'color: #28a745'  # 绿色
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
        if st.button("📸 仅生成快照", use_container_width=True):
            with st.spinner("正在生成快照..."):
                try:
                    count = build_all_snapshots()
                    st.success(f"✅ 快照生成完成！{count} 条记录")
                    st.rerun()
                except Exception as e:
                    st.error(f"❌ 生成失败: {e}")
    
    with col3:
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
    
    col1, col2, col3, col4 = st.columns(4)
    
    with col1:
        st.metric(
            label="💰 总资产",
            value=f"¥ {format_decimal(summary.global_value)}"
        )
    
    with col2:
        pnl_delta = f"¥ {format_decimal(summary.global_pnl)}"
        st.metric(
            label="📈 总盈亏",
            value=pnl_delta,
            delta=format_percent(summary.global_return) if summary.global_return else None
        )
    
    with col3:
        st.metric(
            label="🏦 基金总值",
            value=f"¥ {format_decimal(summary.fund_total)}"
        )
    
    with col4:
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
        
        # 选择显示的列
        display_cols = ['account_name', 'account_type', 'balance', 'product_value', 'diff', 'note']
        display_cols = [c for c in display_cols if c in df_balance.columns]
        
        # 重命名列
        col_names = {
            'account_name': '账户名称',
            'account_type': '类型',
            'balance': '余额',
            'product_value': '产品市值',
            'diff': '差异',
            'note': '备注'
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
        
        # 选择显示的列
        display_cols = ['product_name', 'nav', 'shares', 'value', 'total_pnl', 'real_return']
        display_cols = [c for c in display_cols if c in df_daily.columns]
        
        col_names = {
            'product_name': '产品名称',
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
    
    st.divider()
    
    # 最近记录（记账 + 理财 合并）
    st.subheader("📋 最近记录")
    st.caption("🔴 支出/买入  🟢 收入/赎回")
    
    # 过滤器
    filter_col1, filter_col2 = st.columns([1, 3])
    with filter_col1:
        dash_group_filter = st.selectbox(
            "筛选账户组",
            list(ACCOUNT_GROUP_FILTERS.keys()),
            key="dash_account_filter"
        )
    
    # 获取最近记账记录
    recent_ledger = list_recent_ledger(20, with_balances=True)
    
    # 获取最近理财记录
    recent_tx = list_recent_transactions(20)
    
    # 合并记录
    combined_rows = []
    
    # 处理记账记录
    for r in recent_ledger:
        entry_type = r.get('entry_type', '')
        is_expense = entry_type in ['expense', 'transfer']
        account = merge_account_column(r)
        
        combined_rows.append({
            'sort_time': r.get('event_time', ''),
            '时间': r.get('event_time', ''),
            '金额': format_colored_amount(r.get('amount', ''), is_expense),
            '分类': f"{r.get('category_l1', '')} > {r.get('category_l2', '')}" if r.get('category_l2') else r.get('category_l1', ''),
            '账户': get_account_name(account),
            '账户组': get_account_group_name(account),
            '余额': r.get('balance_after', ''),
            '父账户余额': r.get('parent_balance_after', '') or '',
            '备注': r.get('note', ''),
            'account': account  # 用于过滤
        })
    
    # 处理理财记录（买入红色，卖出绿色）
    from core.ledger_service import calc_account_balance, calc_group_balance, get_account_parent_group
    buy_actions = ['buy_debit', 'buy_confirm', 'buy']
    
    # 预先计算各账户的当前余额（理财记录显示当前余额更有意义）
    account_balances = {}
    
    for r in recent_tx:
        action = r.get('action', '')
        is_buy = action in buy_actions
        product_code = r.get('product_code', '')
        account = get_tx_account(product_code)
        
        # 使用 created_at 作为时间（如果有的话）
        tx_time = r.get('created_at')
        if tx_time:
            time_str = str(tx_time)[:19]  # 取到秒
        else:
            time_str = str(r.get('date', '')) + ' 00:00:00'
        
        # 计算当前账户余额（如果还没计算过）
        if account not in account_balances:
            balance = calc_account_balance(account)  # 当前余额
            parent_group = get_account_parent_group(account)
            if parent_group:
                parent_balance = calc_group_balance(parent_group['accounts'])
            else:
                parent_balance = balance
            account_balances[account] = (balance, parent_balance)
        
        balance, parent_balance = account_balances[account]
        
        combined_rows.append({
            'sort_time': time_str,
            '时间': time_str,
            '金额': format_colored_amount(r.get('amount', ''), is_buy),
            '分类': f"理财 > {ACTION_DISPLAY_MAP.get(action, action)}",
            '账户': get_account_name(account),
            '账户组': get_account_group_name(account),
            '余额': format_decimal(balance),
            '父账户余额': format_decimal(parent_balance),
            '备注': f"{product_code} {r.get('note', '')}",
            'account': account
        })
    
    # 按时间倒序排序
    combined_rows.sort(key=lambda x: x['sort_time'], reverse=True)
    
    # 过滤
    combined_rows = filter_records_by_account_group(combined_rows, dash_group_filter)
    
    if combined_rows:
        df = pd.DataFrame(combined_rows)
        display_cols = ['时间', '金额', '分类', '账户', '账户组', '余额', '父账户余额', '备注']
        # 只对金额列着色
        styled_df = df[display_cols].style.map(color_amount, subset=['金额'])
        st.dataframe(styled_df, use_container_width=True, hide_index=True)
    else:
        st.info("暂无记录")


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
                result = add_expense(
                    account_from=account_dict[expense_account],
                    amount=Decimal(str(expense_amount)),
                    category_l1=expense_cat_l1,
                    category_l2=expense_cat_l2 or '',
                    event_time=event_time,
                    note=expense_note,
                    discount=Decimal(str(expense_discount)),
                    reimbursable=expense_reimbursable
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
                result = add_income(
                    account_to=account_dict[income_account],
                    amount=Decimal(str(income_amount)),
                    category_l1=income_cat_l1,
                    category_l2=income_cat_l2 or '',
                    event_time=event_time,
                    note=income_note
                )
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
    
    recent = list_recent_ledger(30, with_balances=True)
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
        
        # 显示带颜色和行选择的表格
        styled_df = df.style.map(color_amount, subset=['金额'])
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
            selected_idx = selected_rows[0]
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
            
            if st.button("💾 保存修改", type="primary", key="save_ledger_edit"):
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
    filter_col1, filter_col2 = st.columns(2)
    with filter_col1:
        tx_group_filter = st.selectbox("筛选账户组", list(ACCOUNT_GROUP_FILTERS.keys()), key="tx_group_filter")
    with filter_col2:
        product_filter_options = ['全部'] + [f"{p['code']} - {p['name']}" for p in product_options]
        tx_product_filter = st.selectbox("筛选产品", product_filter_options, key="tx_product_filter")
    
    recent_tx = list_recent_transactions(30)
    
    # 产品过滤
    if tx_product_filter != '全部' and recent_tx:
        filter_code = tx_product_filter.split(' - ')[0]
        recent_tx = [r for r in recent_tx if r.get('product_code') == filter_code]
    
    if recent_tx:
        from core.ledger_service import calc_account_balance, calc_group_balance, get_account_parent_group
        
        buy_actions = ['buy_debit', 'buy_confirm', 'buy']
        
        # 处理数据
        rows = []
        raw_records = []
        for r in recent_tx:
            action = r.get('action', '')
            is_buy = action in buy_actions
            amount = r.get('amount', '') or ''
            product_code = r.get('product_code', '')
            
            account = get_tx_account(product_code)
            account_group = get_account_group_name(account)
            
            # 时间（使用 created_at）
            tx_time = r.get('created_at')
            time_str = str(tx_time)[:19] if tx_time else str(r.get('date', '')) + ' 00:00:00'
            
            # 计算当前账户余额（理财记录显示当前余额更有意义）
            balance = calc_account_balance(account)  # 当前余额
            parent_group = get_account_parent_group(account)
            if parent_group:
                parent_balance = calc_group_balance(parent_group['accounts'])
            else:
                # 没有父账户组的（如小荷包），显示自己的余额
                parent_balance = balance
            
            raw_records.append(r)
            rows.append({
                'ID': r.get('id'),
                '时间': time_str,
                '产品': product_code,
                '类型': ACTION_DISPLAY_MAP.get(action, action),
                '金额': format_colored_amount(amount, is_buy),
                '份额': r.get('shares', ''),
                '账户': get_account_name(account),
                '账户组': account_group,
                '余额': format_decimal(balance),
                '父账户余额': format_decimal(parent_balance),
                '备注': r.get('note', ''),
                '_account': account,
                '_action': action
            })
        
        # 账户组过滤
        if tx_group_filter != '全部':
            filtered_rows = []
            filtered_records = []
            for i, row in enumerate(rows):
                if row['账户组'] == tx_group_filter:
                    filtered_rows.append(row)
                    filtered_records.append(raw_records[i])
            rows = filtered_rows
            raw_records = filtered_records
        
        if rows:
            df_tx = pd.DataFrame(rows)
            
            display_cols = ['ID', '时间', '产品', '类型', '金额', '份额', '账户', '账户组', '余额', '父账户余额', '备注']
            
            # 显示带颜色和行选择的表格
            styled_df = df_tx[display_cols].style.map(color_amount, subset=['金额'])
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
                selected_idx = selected_rows[0]
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
                
                if st.button("💾 保存修改", type="primary", key="save_tx_edit"):
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
        else:
            st.info("暂无匹配的理财记录")
    else:
        st.info("暂无理财记录")


# ============================================================
# Page 4: 订单结算
# ============================================================
def page_orders():
    st.markdown('<p class="main-header">📋 订单结算</p>', unsafe_allow_html=True)
    
    # 结算按钮
    col1, col2, col3 = st.columns(3)
    
    with col1:
        if st.button("⚡ 结算今日可结算", use_container_width=True, type="primary"):
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
                        
                except Exception as e:
                    st.error(f"❌ 结算失败: {e}")
    
    with col3:
        st.write("")  # 占位
    
    st.divider()
    
    # 待结算订单
    st.subheader("📋 待结算订单")
    
    pending = list_pending_orders()
    
    if pending:
        df = pd.DataFrame(pending)
        display_cols = ['order_id', 'product_code', 'order_type', 'amount', 'shares', 'confirm_date', 'status', 'note']
        display_cols = [c for c in display_cols if c in df.columns]
        
        col_names = {
            'order_id': '订单号',
            'product_code': '产品代码',
            'order_type': '类型',
            'amount': '金额',
            'shares': '份额',
            'confirm_date': '确认日期',
            'status': '状态',
            'note': '备注'
        }
        
        df_display = df[display_cols].copy()
        df_display = df_display.rename(columns=col_names)
        
        st.dataframe(df_display, use_container_width=True, hide_index=True)
    else:
        st.info("暂无待结算订单")
    
    st.divider()
    
    # 所有订单
    st.subheader("📋 全部订单")
    
    all_orders = list_all_orders()
    
    if all_orders:
        # 筛选器
        status_filter = st.selectbox("状态筛选", ["全部", "pending", "done", "cancelled"], key="order_status_filter")
        
        if status_filter != "全部":
            all_orders = [o for o in all_orders if o.get('status') == status_filter]
        
        df = pd.DataFrame(all_orders[-50:])  # 只显示最近 50 条
        display_cols = ['order_id', 'product_code', 'order_type', 'amount', 'shares', 'confirm_date', 'status']
        display_cols = [c for c in display_cols if c in df.columns]
        
        col_names = {
            'order_id': '订单号',
            'product_code': '产品代码',
            'order_type': '类型',
            'amount': '金额',
            'shares': '份额',
            'confirm_date': '确认日期',
            'status': '状态'
        }
        
        df_display = df[display_cols].copy()
        df_display = df_display.rename(columns=col_names)
        
        st.dataframe(df_display, use_container_width=True, hide_index=True)
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

