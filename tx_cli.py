#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
财富中枢 CLI 工具

统一入口，支持：
1. 记账 - 生活收支记录 (ledger.csv)
2. 理财 - 买入扣款/赎回发起/结算确认 (orders.csv + transactions.csv)
3. 工具 - 查看账户余额/账本/订单/交易/校验

特性：
- 启动时自动同步净值和账户余额
- 记账/理财操作后自动后台同步

用法：
  python tx_cli.py              # 交互模式
  python tx_cli.py add          # 快速新增
  python tx_cli.py settle       # 结算确认
  python tx_cli.py list-ledger  # 查看账本
  python tx_cli.py list-orders  # 查看订单
  python tx_cli.py list-tx      # 查看交易
  python tx_cli.py check        # 数据校验
  python tx_cli.py collect      # 手动同步（一般不需要）
"""
import sys
import io
from pathlib import Path
from datetime import datetime, date, timedelta
from decimal import Decimal, ROUND_HALF_UP, InvalidOperation

# 设置 stdout 编码
if sys.stdout.encoding != 'utf-8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

# 添加 src 目录到路径
sys.path.insert(0, str(Path(__file__).parent / "src"))

# 导入统一的交易日历模块
from utils.trade_calendar import (
    is_trade_day, next_trade_day, prev_trade_day,
    add_trade_days, subtract_trade_days
)

from data.config_loader import (
    get_project_root, load_products, get_product,
    load_accounts, load_categories, load_account_groups,
    get_account, get_accounts_by_group, get_account_name,
    get_sell_fee_rate, format_sell_fee_tiers
)
from data.data_store import (
    # transactions
    TRANSACTIONS_FIELDNAMES, VALID_ACTIONS,
    load_transactions, append_transaction, transaction_exists,
    # orders  
    ORDERS_FIELDNAMES, VALID_ORDER_TYPES, VALID_ORDER_STATUS,
    load_orders, append_order, get_pending_orders, update_order_status,
    # ledger
    LEDGER_FIELDNAMES, VALID_ENTRY_TYPES,
    load_ledger, append_ledger,
    # utils
    generate_order_id, format_decimal, parse_decimal
)
from data.nav_reader import get_nav, get_latest_nav
from core.nav_collector import collect_and_store


# ============================================================
# 交易日辅助函数
# ============================================================

def calc_trade_date(requested_at, cutoff_time='15:00'):
    """
    计算交易日期
    规则：
    - 如果 requested_at 在交易日且时间 <= cutoff_time，则 trade_date = 当天
    - 否则 trade_date = next_trade_day(当天)
    """
    request_date = requested_at.date()
    
    # 解析截止时间
    cutoff = datetime.strptime(cutoff_time, '%H:%M').time()
    request_time = requested_at.time()
    
    if is_trade_day(request_date) and request_time <= cutoff:
        return request_date
    else:
        return next_trade_day(request_date)


def calc_confirm_date(trade_date, confirm_offset):
    """计算确认日期"""
    return add_trade_days(trade_date, confirm_offset)


# ============================================================
# 输入辅助函数
# ============================================================

def input_date(prompt: str, default: str = None) -> str:
    """输入日期"""
    if default is None:
        default = datetime.now().strftime('%Y-%m-%d')
    
    while True:
        value = input(f"{prompt} (默认 {default}): ").strip()
        if value == '':
            return default
        
        try:
            datetime.strptime(value, '%Y-%m-%d')
            return value
        except ValueError:
            print("✗ 日期格式错误，请使用 YYYY-MM-DD")


def input_datetime(prompt: str, default: str = None) -> str:
    """输入日期时间"""
    if default is None:
        default = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    while True:
        value = input(f"{prompt} (默认 {default}): ").strip()
        if value == '':
            return default
        
        # 尝试解析多种格式
        for fmt in ['%Y-%m-%d %H:%M:%S', '%Y-%m-%d %H:%M', '%Y-%m-%d']:
            try:
                datetime.strptime(value, fmt)
                return value
            except ValueError:
                continue
        
        print("✗ 时间格式错误，请使用 YYYY-MM-DD HH:MM:SS")


def input_decimal(prompt: str, required: bool = True, 
                  must_positive: bool = False, default: Decimal = None) -> Decimal:
    """输入 Decimal 数值"""
    default_hint = f" (默认 {default})" if default is not None else ""
    required_hint = " [必填]" if required else " [可选]"
    
    while True:
        value = input(f"{prompt}{default_hint}{required_hint}: ").strip()
        
        if value == '':
            if default is not None:
                return default
            if not required:
                return Decimal('0')
            print("✗ 此字段必填")
            continue
        
        try:
            d = Decimal(value.replace(',', ''))
        except InvalidOperation:
            print("✗ 数值格式错误")
            continue
        
        if must_positive and d <= 0:
            print("✗ 必须为正数")
            continue
        
        if d < 0:
            print("✗ 不能为负数")
            continue
        
        return d


def input_choice(prompt: str, choices: list, allow_index: bool = True) -> str:
    """输入选择"""
    while True:
        value = input(f"{prompt}: ").strip()
        
        if allow_index:
            try:
                idx = int(value) - 1
                if 0 <= idx < len(choices):
                    return choices[idx]
            except ValueError:
                pass
        
        if value in choices:
            return value
        
        print(f"✗ 无效选择，可选: {choices}")


def select_product() -> dict:
    """交互选择产品"""
    products = load_products()
    
    print("\n=== 选择产品 ===")
    for i, p in enumerate(products, 1):
        market = p.get('market', 'cn')
        fee_rate = p.get('buy_fee_rate', 0) * 100
        print(f"  [{i}] {p['product_code']} - {p['product_name']} ({market}, 费率{fee_rate:.2f}%)")
    
    while True:
        choice = input("\n请输入序号或产品代码: ").strip()
        
        try:
            idx = int(choice) - 1
            if 0 <= idx < len(products):
                return products[idx]
        except ValueError:
            pass
        
        for p in products:
            if p['product_code'] == choice:
                return p
        
        print("✗ 无效选择")


def select_account(prompt: str = "选择账户") -> str:
    """交互选择账户"""
    accounts = load_accounts()
    
    print(f"\n=== {prompt} ===")
    for i, acc in enumerate(accounts, 1):
        print(f"  [{i}] {acc['id']} - {acc['name']}")
    
    while True:
        choice = input("\n请输入序号或账户ID: ").strip()
        
        try:
            idx = int(choice) - 1
            if 0 <= idx < len(accounts):
                return accounts[idx]['id']
        except ValueError:
            pass
        
        for acc in accounts:
            if acc['id'] == choice:
                return acc['id']
        
        print("✗ 无效选择")


def select_category(entry_type: str) -> tuple:
    """交互选择分类，返回 (category_l1, category_l2)"""
    categories = load_categories()
    
    if entry_type == 'transfer':
        return ('转账', '')
    
    cat_dict = categories.get(entry_type, {})
    if not cat_dict:
        return ('其他', '')
    
    # 一级分类
    l1_list = list(cat_dict.keys())
    print(f"\n=== 选择一级分类 ({entry_type}) ===")
    for i, l1 in enumerate(l1_list, 1):
        print(f"  [{i}] {l1}")
    
    while True:
        choice = input("\n请输入序号: ").strip()
        try:
            idx = int(choice) - 1
            if 0 <= idx < len(l1_list):
                category_l1 = l1_list[idx]
                break
        except ValueError:
            pass
        print("✗ 无效选择")
    
    # 二级分类
    l2_list = cat_dict.get(category_l1, [])
    if not l2_list:
        return (category_l1, '')
    
    print(f"\n=== 选择二级分类 ===")
    print("  [0] 不选择")
    for i, l2 in enumerate(l2_list, 1):
        print(f"  [{i}] {l2}")
    
    while True:
        choice = input("\n请输入序号: ").strip()
        if choice == '0' or choice == '':
            return (category_l1, '')
        try:
            idx = int(choice) - 1
            if 0 <= idx < len(l2_list):
                return (category_l1, l2_list[idx])
        except ValueError:
            pass
        print("✗ 无效选择")


# ============================================================
# 记账功能 (ledger.csv)
# ============================================================

def add_ledger_entry():
    """添加账本记录"""
    print("\n" + "=" * 50)
    print("新增账本记录")
    print("=" * 50)
    
    # 选择类型
    print("\n选择类型：")
    print("  [1] expense - 支出")
    print("  [2] income - 收入")
    print("  [3] transfer - 转账")
    print("  [0] 返回")
    
    choice = input("\n请输入序号: ").strip()
    if choice == '0':
        return
    elif choice == '1':
        entry_type = 'expense'
    elif choice == '2':
        entry_type = 'income'
    elif choice == '3':
        entry_type = 'transfer'
    else:
        print("✗ 无效选择")
        return
    print(f"✓ 类型: {entry_type}")
    
    # 输入时间
    event_time = input_datetime("时间")
    print(f"✓ 时间: {event_time}")
    
    # 输入金额
    amount = input_decimal("金额", must_positive=True)
    print(f"✓ 金额: {format_decimal(amount, 2)}")
    
    # 选择分类
    category_l1, category_l2 = select_category(entry_type)
    print(f"✓ 分类: {category_l1}" + (f" > {category_l2}" if category_l2 else ""))
    
    # 选择账户
    if entry_type == 'expense':
        account_from = select_account("选择支出账户")
        account_to = ''
    elif entry_type == 'income':
        account_from = ''
        account_to = select_account("选择收入账户")
    else:  # transfer
        account_from = select_account("选择转出账户")
        account_to = select_account("选择转入账户")
        if account_from == account_to:
            print("✗ 转出和转入账户不能相同")
            return
    
    # 优惠/报销
    discount = input_decimal("优惠金额", required=False, default=Decimal('0'))
    reimbursable_str = input("是否可报销 (y/N): ").strip().lower()
    reimbursable = 1 if reimbursable_str == 'y' else 0
    
    # 备注
    note = input("备注 [可选]: ").strip()
    
    # 确认
    print("\n" + "-" * 50)
    print("请确认：")
    print(f"  类型: {entry_type}")
    print(f"  时间: {event_time}")
    print(f"  金额: {format_decimal(amount, 2)}")
    print(f"  分类: {category_l1}" + (f" > {category_l2}" if category_l2 else ""))
    if account_from:
        print(f"  支出账户: {account_from}")
    if account_to:
        print(f"  收入账户: {account_to}")
    if discount > 0:
        print(f"  优惠: {format_decimal(discount, 2)}")
    if reimbursable:
        print(f"  可报销: 是")
    if note:
        print(f"  备注: {note}")
    
    confirm = input("\n确认写入? (Y/n): ").strip().lower()
    if confirm == 'n':
        print("已取消")
        return
    
    # 写入
    record = {
        'event_time': event_time,
        'entry_type': entry_type,
        'amount': format_decimal(amount, 2),
        'category_l1': category_l1,
        'category_l2': category_l2,
        'account_from': account_from,
        'account_to': account_to,
        'discount': format_decimal(discount, 2) if discount > 0 else '0',
        'reimbursable': str(reimbursable),
        'note': note
    }
    
    append_ledger(record)
    print(f"\n✓ 已写入账本")


def add_refund_entry():
    """添加退款记录"""
    print("\n" + "=" * 50)
    print("退款记录")
    print("=" * 50)
    
    # 加载最近的支出记录
    ledger = load_ledger()
    expenses = [e for e in ledger if e.get('entry_type') == 'expense']
    
    if not expenses:
        print("暂无支出记录")
        return
    
    # 显示最近 20 条支出
    recent = expenses[-20:]
    print("\n=== 选择要退款的支出记录 ===")
    print(f"{'#':<4} {'时间':<20} {'金额':>10} {'分类':<15} {'账户':<12} {'备注':<20}")
    print("-" * 85)
    
    for i, e in enumerate(recent, 1):
        event_time = e.get('event_time', '')[:16]
        amount = e.get('amount', '')
        cat = e.get('category_l1', '')
        if e.get('category_l2'):
            cat += f">{e['category_l2']}"
        account = e.get('account_from', '')
        note = e.get('note', '')[:18]
        print(f"{i:<4} {event_time:<20} {amount:>10} {cat:<15} {account:<12} {note:<20}")
    
    # 选择记录
    while True:
        choice = input("\n请输入序号 (0取消): ").strip()
        if choice == '0':
            print("已取消")
            return
        try:
            idx = int(choice) - 1
            if 0 <= idx < len(recent):
                original = recent[idx]
                break
        except ValueError:
            pass
        print("✗ 无效选择")
    
    # 显示原记录信息
    orig_amount = Decimal(original.get('amount', '0'))
    orig_account = original.get('account_from', '')
    orig_cat = original.get('category_l1', '')
    orig_note = original.get('note', '')
    
    print(f"\n原记录：")
    print(f"  时间: {original.get('event_time', '')}")
    print(f"  金额: {orig_amount}")
    print(f"  分类: {orig_cat}")
    print(f"  账户: {orig_account} ({get_account_name(orig_account)})")
    if orig_note:
        print(f"  备注: {orig_note}")
    
    # 输入退款金额（默认原金额）
    print(f"\n退款金额 (默认 {orig_amount}，可输入部分退款金额)")
    refund_amount = input_decimal("退款金额", required=False, default=orig_amount)
    
    if refund_amount > orig_amount:
        print(f"⚠ 退款金额 {refund_amount} 大于原金额 {orig_amount}")
        cont = input("继续? (Y/n): ").strip().lower()
        if cont == 'n':
            print("已取消")
            return
    
    # 退款账户（默认原账户）
    print(f"\n退款账户 (默认 {orig_account})")
    change_account = input("回车使用原账户 / 输入 c 更换: ").strip().lower()
    if change_account == 'c':
        refund_account = select_account("选择退款账户")
    else:
        refund_account = orig_account
    
    # 退款时间
    event_time = input_datetime("退款时间")
    
    # 备注
    default_note = f"退款: {orig_note}" if orig_note else "退款"
    note_input = input(f"备注 (默认: {default_note}): ").strip()
    note = note_input if note_input else default_note
    
    # 确认
    print("\n" + "-" * 50)
    print("请确认：")
    print(f"  类型: income (退款)")
    print(f"  时间: {event_time}")
    print(f"  退款金额: {format_decimal(refund_amount, 2)}")
    print(f"  原金额: {format_decimal(orig_amount, 2)}")
    print(f"  退款账户: {refund_account} ({get_account_name(refund_account)})")
    print(f"  分类: 退款")
    print(f"  备注: {note}")
    
    confirm = input("\n确认写入? (Y/n): ").strip().lower()
    if confirm == 'n':
        print("已取消")
        return
    
    # 写入（作为 income）
    record = {
        'event_time': event_time,
        'entry_type': 'income',
        'amount': format_decimal(refund_amount, 2),
        'category_l1': '退款',
        'category_l2': orig_cat,  # 用二级分类记录原分类
        'account_from': '',
        'account_to': refund_account,
        'discount': '0',
        'reimbursable': '0',
        'note': note
    }
    
    append_ledger(record)
    print(f"\n✓ 已写入退款记录")


# ============================================================
# 理财功能 (orders.csv + transactions.csv)
# ============================================================

def add_buy_debit():
    """添加买入扣款"""
    print("\n" + "=" * 50)
    print("买入扣款 (buy_debit)")
    print("=" * 50)
    
    # 选择产品
    product = select_product()
    product_code = product['product_code']
    product_name = product['product_name']
    buy_fee_rate = Decimal(str(product.get('buy_fee_rate', 0)))
    buy_confirm_offset = product.get('buy_confirm_offset', 1)
    cutoff_time = product.get('cutoff_time', '15:00')
    
    print(f"✓ 产品: {product_code} - {product_name}")
    print(f"  申购费率: {buy_fee_rate * 100:.2f}%")
    print(f"  确认延迟: T+{buy_confirm_offset}")
    
    # 输入扣款金额（含手续费）
    amount = input_decimal("扣款金额（含手续费）", must_positive=True)
    
    # 计算手续费
    calculated_fee = (amount * buy_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
    print(f"\n系统计算手续费: {format_decimal(calculated_fee, 2)}")
    
    fee_input = input(f"回车确认 / 输入覆盖: ").strip()
    if fee_input:
        fee = Decimal(fee_input)
    else:
        fee = calculated_fee
    
    print(f"✓ 手续费: {format_decimal(fee, 2)}")
    print(f"✓ 净申购额: {format_decimal(amount - fee, 2)}")
    
    # 计算交易日期和确认日期
    requested_at = datetime.now()
    trade_date = calc_trade_date(requested_at, cutoff_time)
    nav_date = trade_date
    confirm_date = calc_confirm_date(trade_date, buy_confirm_offset)
    
    print(f"\n交易日期计算：")
    print(f"  请求时间: {requested_at.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"  交易日期: {trade_date}")
    print(f"  净值日期: {nav_date}")
    print(f"  确认日期: {confirm_date}")
    
    # 生成订单号
    order_id = generate_order_id(product_code)
    note = product_name  # 直接使用产品名称
    
    # 确认
    confirm = input("\n确认写入? (Y/n): ").strip().lower()
    if confirm == 'n':
        print("已取消")
        return
    
    # 写入 transactions.csv (buy_debit)
    tx_record = {
        'date': str(trade_date),
        'product_code': product_code,
        'action': 'buy_debit',
        'amount': format_decimal(amount, 2),
        'shares': '',
        'fee': format_decimal(fee, 2),
        'nav': '',
        'nav_date': '',
        'order_id': order_id,
        'note': note
    }
    append_transaction(tx_record)
    
    # 写入 orders.csv
    order_record = {
        'order_id': order_id,
        'product_code': product_code,
        'order_type': 'buy_debit',
        'amount': format_decimal(amount, 2),
        'fee': format_decimal(fee, 2),
        'shares': '',
        'requested_at': requested_at.strftime('%Y-%m-%d %H:%M:%S'),
        'trade_date': str(trade_date),
        'nav_date': str(nav_date),
        'confirm_date': str(confirm_date),
        'holding_days': '',
        'sell_fee_rate': '',
        'status': 'pending',
        'note': note
    }
    append_order(order_record)
    
    print(f"\n✓ 已写入")
    print(f"  订单号: {order_id}")
    print(f"  等待 {confirm_date} 结算确认")


def add_redeem_request():
    """添加赎回发起"""
    print("\n" + "=" * 50)
    print("赎回发起 (redeem_request)")
    print("=" * 50)
    
    # 选择产品
    product = select_product()
    product_code = product['product_code']
    product_name = product['product_name']
    sell_confirm_offset = product.get('sell_confirm_offset', 1)
    cutoff_time = product.get('cutoff_time', '15:00')
    
    print(f"✓ 产品: {product_code} - {product_name}")
    print(f"  确认延迟: T+{sell_confirm_offset}")
    
    # 显示赎回费率阶梯
    print(f"\n赎回费率阶梯：")
    print(format_sell_fee_tiers(product))
    
    # 输入赎回份额
    shares = input_decimal("\n赎回份额", must_positive=True)
    print(f"✓ 赎回份额: {format_decimal(shares, 2)}")
    
    # 输入持有天数
    holding_days_str = input("持有天数 (用于确定赎回费率): ").strip()
    try:
        holding_days = int(holding_days_str)
    except ValueError:
        print("✗ 持有天数必须是整数")
        return
    
    # 获取对应费率
    sell_fee_rate = Decimal(str(get_sell_fee_rate(product, holding_days)))
    print(f"✓ 持有 {holding_days} 天，赎回费率: {sell_fee_rate * 100:.2f}%")
    
    # 计算交易日期和确认日期
    requested_at = datetime.now()
    trade_date = calc_trade_date(requested_at, cutoff_time)
    nav_date = trade_date
    confirm_date = calc_confirm_date(trade_date, sell_confirm_offset)
    
    print(f"\n交易日期计算：")
    print(f"  请求时间: {requested_at.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"  交易日期: {trade_date}")
    print(f"  净值日期: {nav_date}")
    print(f"  确认日期: {confirm_date}")
    
    # 生成订单号
    order_id = generate_order_id(product_code)
    note = product_name  # 直接使用产品名称
    
    # 确认
    confirm = input("\n确认写入? (Y/n): ").strip().lower()
    if confirm == 'n':
        print("已取消")
        return
    
    # 只写入 orders.csv（不写 transactions.csv）
    order_record = {
        'order_id': order_id,
        'product_code': product_code,
        'order_type': 'redeem_request',
        'amount': '',
        'fee': '',
        'shares': format_decimal(shares, 4),  # 份额精度至少4位
        'requested_at': requested_at.strftime('%Y-%m-%d %H:%M:%S'),
        'trade_date': str(trade_date),
        'nav_date': str(nav_date),
        'confirm_date': str(confirm_date),
        'holding_days': str(holding_days),
        'sell_fee_rate': str(sell_fee_rate),
        'status': 'pending',
        'note': note
    }
    append_order(order_record)
    
    print(f"\n✓ 已写入")
    print(f"  订单号: {order_id}")
    print(f"  等待 {confirm_date} 结算确认")


def _do_history_trade(action: str):
    """执行一次历史交易补录"""
    # 选择产品
    product = select_product()
    product_code = product['product_code']
    product_name = product['product_name']
    buy_fee_rate = Decimal(str(product.get('buy_fee_rate', 0)))
    buy_confirm_offset = product.get('buy_confirm_offset', 1)
    sell_confirm_offset = product.get('sell_confirm_offset', 1)
    
    print(f"\n✓ 产品: {product_code} - {product_name}")
    print(f"✓ 类型: {action}")
    if action == 'buy':
        print(f"  申购费率: {buy_fee_rate * 100:.2f}%")
        print(f"  确认延迟: T+{buy_confirm_offset}（净值日期 = 确认日期 - {buy_confirm_offset} 个交易日）")
    elif action == 'sell':
        print(f"  确认延迟: T+{sell_confirm_offset}（净值日期 = 确认日期 - {sell_confirm_offset} 个交易日）")
        print(f"\n赎回费率阶梯：")
        print(format_sell_fee_tiers(product))
    
    # 输入确认日期（份额到账日期）
    confirm_date_str = input_date("\n确认日期（份额到账日期）")
    confirm_date = datetime.strptime(confirm_date_str, '%Y-%m-%d').date()
    print(f"✓ 确认日期: {confirm_date_str}")
    
    # 根据类型输入不同字段
    if action == 'buy':
        # 计算净值日期（确认日期 - confirm_offset 个交易日）
        nav_date_obj = subtract_trade_days(confirm_date, buy_confirm_offset)
        nav_date = nav_date_obj.strftime('%Y-%m-%d')
        print(f"✓ 净值日期: {nav_date}（自动计算）")
        
        # 自动读取净值
        nav = get_nav(product_code, nav_date)
        if nav is None:
            print(f"✗ 无法获取 {product_code} 在 {nav_date} 的净值")
            nav_input = input("请手动输入净值: ").strip()
            if not nav_input:
                print("已取消")
                return
            nav = Decimal(nav_input)
        else:
            print(f"✓ 净值: {nav}（自动读取）")
        
        # 输入扣款金额
        amount = input_decimal("扣款金额（含手续费）", must_positive=True)
        
        # 自动计算手续费
        calculated_fee = (amount * buy_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
        print(f"  系统计算手续费: {format_decimal(calculated_fee, 2)}")
        
        fee_input = input(f"  回车确认 / 输入覆盖: ").strip()
        if fee_input:
            fee = Decimal(fee_input)
        else:
            fee = calculated_fee
        print(f"✓ 手续费: {format_decimal(fee, 2)}")
        
        # 自动计算份额
        net_amount = amount - fee
        calculated_shares = (net_amount / nav).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
        print(f"  系统计算份额: {format_decimal(calculated_shares, 2)}（净申购额 / 净值）")
        
        shares_input = input(f"  回车确认 / 输入覆盖: ").strip()
        if shares_input:
            shares = Decimal(shares_input)
        else:
            shares = calculated_shares
        print(f"✓ 确认份额: {format_decimal(shares, 2)}")
        
        # 用确认日期作为交易日期
        tx_date = confirm_date_str
        
        print(f"\n确认信息：")
        print(f"  确认日期: {confirm_date_str}")
        print(f"  净值日期: {nav_date}")
        print(f"  净值: {format_decimal(nav, 4)}")
        print(f"  扣款金额: {format_decimal(amount, 2)}")
        print(f"  手续费: {format_decimal(fee, 2)}")
        print(f"  净申购额: {format_decimal(net_amount, 2)}")
        print(f"  确认份额: {format_decimal(shares, 2)}")
        
    elif action == 'sell':
        # 计算净值日期（确认日期 - confirm_offset 个交易日）
        nav_date_obj = subtract_trade_days(confirm_date, sell_confirm_offset)
        nav_date = nav_date_obj.strftime('%Y-%m-%d')
        print(f"✓ 净值日期: {nav_date}（自动计算）")
        
        # 自动读取净值
        nav = get_nav(product_code, nav_date)
        if nav is None:
            print(f"✗ 无法获取 {product_code} 在 {nav_date} 的净值")
            nav_input = input("请手动输入净值: ").strip()
            if not nav_input:
                print("已取消")
                return
            nav = Decimal(nav_input)
        else:
            print(f"✓ 净值: {nav}（自动读取）")
        
        # 输入赎回份额
        shares = input_decimal("赎回份额", must_positive=True)
        
        gross = shares * nav
        print(f"  总金额（份额×净值）: {format_decimal(gross, 2)}")
        
        # 输入持有天数，自动计算费率
        holding_days_str = input("持有天数 (用于确定赎回费率): ").strip()
        try:
            holding_days = int(holding_days_str)
        except ValueError:
            print("✗ 持有天数必须是整数")
            return
        
        sell_fee_rate = Decimal(str(get_sell_fee_rate(product, holding_days)))
        calculated_fee = (gross * sell_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
        print(f"  持有 {holding_days} 天，费率: {sell_fee_rate * 100:.2f}%")
        print(f"  系统计算赎回费: {format_decimal(calculated_fee, 2)}")
        
        fee_input = input(f"  回车确认 / 输入覆盖: ").strip()
        if fee_input:
            fee = Decimal(fee_input)
        else:
            fee = calculated_fee
        
        amount = gross - fee  # 到账净额
        
        # 用确认日期作为交易日期
        tx_date = confirm_date_str
        
        print(f"\n确认信息：")
        print(f"  确认日期: {confirm_date_str}")
        print(f"  净值日期: {nav_date}")
        print(f"  净值: {format_decimal(nav, 4)}")
        print(f"  赎回份额: {format_decimal(shares, 2)}")
        print(f"  总金额: {format_decimal(gross, 2)}")
        print(f"  持有天数: {holding_days}，费率: {sell_fee_rate * 100:.2f}%")
        print(f"  赎回手续费: {format_decimal(fee, 2)}")
        print(f"  到账净额: {format_decimal(amount, 2)}")
        
    else:  # dividend
        # 分红：份额
        shares = input_decimal("分红份额", must_positive=True)
        amount = Decimal('0')
        fee = Decimal('0')
        nav = Decimal('0')
        nav_date = confirm_date_str
        tx_date = confirm_date_str
        
        print(f"\n确认信息：")
        print(f"  分红日期: {confirm_date_str}")
        print(f"  分红份额: {format_decimal(shares, 2)}")
    
    note = product_name  # 直接使用产品名称
    
    # 确认
    confirm = input("\n确认写入? (Y/n): ").strip().lower()
    if confirm == 'n':
        print("已取消")
        return
    
    # 写入 transactions.csv（不走 orders）
    tx_record = {
        'date': tx_date,
        'product_code': product_code,
        'action': action,
        'amount': format_decimal(amount, 2) if amount > 0 else '',
        'shares': format_decimal(shares, 4),  # 份额精度至少4位
        'fee': format_decimal(fee, 2) if fee > 0 else '',
        'nav': format_decimal(nav, 4) if nav > 0 else '',
        'nav_date': nav_date if nav > 0 else '',
        'order_id': '',  # 补录不需要 order_id
        'note': note
    }
    append_transaction(tx_record)
    
    print(f"\n✓ 已写入 transactions.csv")
    print(f"  action: {action}")
    print(f"  日期: {tx_date}")
    return True  # 返回成功标志


def add_history_trade():
    """补录历史交易（已完成的 buy/sell）- 带循环"""
    while True:
        print("\n" + "=" * 50)
        print("补录历史交易")
        print("=" * 50)
        print("适用于：历史已完成的买入/卖出，不需要走 orders 流程")
        
        # 选择类型
        print("\n选择交易类型：")
        print("  [1] buy - 买入（已完成）")
        print("  [2] sell - 卖出（已完成）")
        print("  [3] dividend - 分红")
        print("  [0] 返回")
        
        type_choice = input("\n请选择: ").strip()
        if type_choice == '0':
            break
        elif type_choice == '1':
            action = 'buy'
            action_name = '买入'
        elif type_choice == '2':
            action = 'sell'
            action_name = '卖出'
        elif type_choice == '3':
            action = 'dividend'
            action_name = '分红'
        else:
            continue
        
        # 同类型循环录入
        while True:
            _do_history_trade(action)
            print("\n" + "-" * 30)
            cont = input(f"继续补录{action_name}? (回车继续 / 0退出): ").strip()
            if cont == '0':
                break


def settle_orders():
    """结算确认 - 处理到期的 pending 订单"""
    print("\n" + "=" * 50)
    print("结算确认 (settle)")
    print("=" * 50)
    
    today = date.today().strftime('%Y-%m-%d')
    pending = get_pending_orders(before_date=today)
    
    if not pending:
        print(f"\n没有需要结算的订单（confirm_date <= {today}）")
        return
    
    print(f"\n找到 {len(pending)} 个待结算订单：")
    for order in pending:
        print(f"  - {order['order_id']}: {order['order_type']} {order['product_code']} @ {order['confirm_date']}")
    
    settled_count = 0
    skipped_count = 0
    error_count = 0
    
    for order in pending:
        order_id = order['order_id']
        order_type = order['order_type']
        product_code = order['product_code']
        nav_date = order.get('nav_date', '')
        
        print(f"\n--- 处理订单 {order_id} ---")
        
        # 幂等性检查
        confirm_action = 'buy_confirm' if order_type == 'buy_debit' else 'sell_confirm'
        if transaction_exists(order_id, confirm_action):
            print(f"  ⚠ 已存在确认记录，跳过并标记完成")
            update_order_status(order_id, 'done')
            skipped_count += 1
            continue
        
        # 获取净值
        nav = get_nav(product_code, nav_date)
        if nav is None:
            print(f"  ⚠ 缺少净值: {product_code} @ {nav_date}，保持 pending")
            error_count += 1
            continue
        
        print(f"  净值: {nav_date} = {nav}")
        
        # 获取产品配置
        product = get_product(product_code)
        if product is None:
            print(f"  ✗ 找不到产品配置: {product_code}")
            error_count += 1
            continue
        
        if order_type == 'buy_debit':
            # 买入确认
            amount = parse_decimal(order.get('amount', 0))
            fee = parse_decimal(order.get('fee', 0))
            net_amount = amount - fee
            
            # 计算份额（精度至少 4 位小数）
            shares = (net_amount / nav).quantize(Decimal('0.0001'), rounding=ROUND_HALF_UP)
            
            print(f"  净申购额: {format_decimal(net_amount, 2)}")
            print(f"  确认份额: {format_decimal(shares, 4)}")
            
            # 写入 buy_confirm（fee=0，避免重复扣费）
            tx_record = {
                'date': order.get('confirm_date', today),
                'product_code': product_code,
                'action': 'buy_confirm',
                'amount': '',  # confirm 不需要 amount
                'shares': format_decimal(shares, 4),  # 份额精度至少4位
                'fee': '0',    # 费用已在 buy_debit 扣除
                'nav': str(nav),
                'nav_date': nav_date,
                'order_id': order_id,
                'note': order.get('note', '')
            }
            append_transaction(tx_record)
            
        elif order_type == 'redeem_request':
            # 赎回确认
            shares = parse_decimal(order.get('shares', 0))
            
            # 从订单中读取费率（赎回发起时已根据持有天数确定）
            sell_fee_rate_str = order.get('sell_fee_rate', '0')
            sell_fee_rate = Decimal(str(sell_fee_rate_str)) if sell_fee_rate_str else Decimal('0')
            holding_days = order.get('holding_days', '')
            
            # 计算到账金额
            gross = shares * nav
            fee = (gross * sell_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
            amount = gross - fee
            
            print(f"  赎回份额: {format_decimal(shares, 4)}")
            if holding_days:
                print(f"  持有天数: {holding_days}，费率: {sell_fee_rate * 100:.2f}%")
            print(f"  总金额: {format_decimal(gross, 2)}")
            print(f"  赎回费: {format_decimal(fee, 2)}")
            print(f"  到账净额: {format_decimal(amount, 2)}")
            
            # 写入 sell_confirm
            tx_record = {
                'date': order.get('confirm_date', today),
                'product_code': product_code,
                'action': 'sell_confirm',
                'amount': format_decimal(amount, 2),  # 到账净额
                'shares': format_decimal(shares, 4),  # 份额精度至少4位
                'fee': format_decimal(fee, 2),
                'nav': str(nav),
                'nav_date': nav_date,
                'order_id': order_id,
                'note': order.get('note', '')
            }
            append_transaction(tx_record)
        
        # 标记订单完成
        update_order_status(order_id, 'done')
        print(f"  ✓ 已生成 {confirm_action}")
        settled_count += 1
    
    print(f"\n=== 结算完成 ===")
    print(f"  成功: {settled_count}")
    print(f"  跳过（已存在）: {skipped_count}")
    print(f"  失败（缺净值）: {error_count}")


# ============================================================
# 查看列表
# ============================================================

def list_ledger(n: int = 20):
    """查看账本记录"""
    records = load_ledger()
    recent = records[-n:] if len(records) > n else records
    
    print(f"\n=== 最近 {len(recent)} 条账本记录 ===\n")
    
    if not recent:
        print("暂无记录")
        return
    
    header = f"{'#':<4} {'时间':<20} {'类型':<10} {'金额':>10} {'分类':<15} {'账户':<15}"
    print(header)
    print("-" * 80)
    
    for i, r in enumerate(recent, len(records) - len(recent) + 1):
        event_time = r.get('event_time', '')[:16]
        entry_type = r.get('entry_type', '')
        amount = r.get('amount', '')
        cat = r.get('category_l1', '')
        if r.get('category_l2'):
            cat += f">{r['category_l2']}"
        
        account = r.get('account_from', '') or r.get('account_to', '')
        
        print(f"{i:<4} {event_time:<20} {entry_type:<10} {amount:>10} {cat:<15} {account:<15}")
    
    print(f"\n共 {len(records)} 条记录")


def list_orders(n: int = 20):
    """查看订单"""
    orders = load_orders()
    recent = orders[-n:] if len(orders) > n else orders
    
    print(f"\n=== 最近 {len(recent)} 条订单 ===\n")
    
    if not recent:
        print("暂无订单")
        return
    
    header = f"{'#':<4} {'订单号':<25} {'类型':<15} {'产品':<12} {'状态':<10} {'确认日':<12}"
    print(header)
    print("-" * 90)
    
    for i, o in enumerate(recent, len(orders) - len(recent) + 1):
        order_id = o.get('order_id', '')[:23]
        order_type = o.get('order_type', '')
        product_code = o.get('product_code', '')
        status = o.get('status', '')
        confirm_date = o.get('confirm_date', '')
        
        print(f"{i:<4} {order_id:<25} {order_type:<15} {product_code:<12} {status:<10} {confirm_date:<12}")
    
    pending = [o for o in orders if o.get('status') == 'pending']
    print(f"\n共 {len(orders)} 条订单，{len(pending)} 条待处理")


def list_transactions(n: int = 20):
    """查看交易记录"""
    transactions = load_transactions()
    recent = transactions[-n:] if len(transactions) > n else transactions
    
    print(f"\n=== 最近 {len(recent)} 条交易记录 ===\n")
    
    if not recent:
        print("暂无记录")
        return
    
    header = f"{'#':<4} {'日期':<12} {'产品':<12} {'操作':<12} {'金额':>10} {'份额':>10} {'净值':>8}"
    print(header)
    print("-" * 80)
    
    for i, tx in enumerate(recent, len(transactions) - len(recent) + 1):
        date = tx.get('date', '')
        product_code = tx.get('product_code', '')
        action = tx.get('action', '')
        amount = tx.get('amount', '')
        shares = tx.get('shares', '')
        nav = tx.get('nav', '')
        
        print(f"{i:<4} {date:<12} {product_code:<12} {action:<12} {amount:>10} {shares:>10} {nav:>8}")
    
    print(f"\n共 {len(transactions)} 条记录")


# ============================================================
# 账户余额
# ============================================================

def get_fund_total_from_daily() -> tuple:
    """
    从 daily.csv 获取基金总市值
    
    Returns:
        (基金总市值, 稳利宝市值, 稳利宝收益, 记录详情字典)
    """
    import csv
    daily_path = get_project_root() / "data" / "snapshots" / "daily.csv"
    
    if not daily_path.exists():
        return Decimal('0'), Decimal('0'), Decimal('0'), {}
    
    # 读取最新一天的数据
    records = []
    with open(daily_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            # 跳过中文表头行
            if row.get('fetch_date', '').startswith('采集'):
                continue
            records.append(row)
    
    if not records:
        return Decimal('0'), Decimal('0'), Decimal('0'), {}
    
    # 找到最新的 fetch_date
    latest_date = max(r.get('fetch_date', '') for r in records)
    latest_records = [r for r in records if r.get('fetch_date') == latest_date]
    
    fund_total = Decimal('0')
    wenlibao_value = Decimal('0')
    wenlibao_pnl = Decimal('0')
    details = {}
    
    for r in latest_records:
        product_code = r.get('product_code', '')
        value_str = r.get('value', '0')
        pnl_str = r.get('unrealized_pnl', '0')
        try:
            value = Decimal(value_str)
        except:
            value = Decimal('0')
        try:
            pnl = Decimal(pnl_str)
        except:
            pnl = Decimal('0')
        
        # 稳利宝（民生理财）
        if product_code == 'FBAE41126E':
            wenlibao_value = value
            wenlibao_pnl = pnl
        # 建信嘉薪宝是货币基金，特殊处理（不计入基金总额）
        elif product_code == '000686':
            pass  # 货币基金收益计入小荷包
        else:
            fund_total += value
        
        details[product_code] = {
            'name': r.get('product_name', ''),
            'value': value,
            'shares': r.get('shares', '0'),
            'nav': r.get('nav', '0'),
            'nav_date': r.get('nav_date', ''),
            'pnl': pnl_str,
            'return_rate': r.get('return_rate', '0'),
        }
    
    return fund_total, wenlibao_value, wenlibao_pnl, details


def calc_account_balance(account: dict, ledger_data: list) -> dict:
    """
    计算单个账户的当前余额
    
    余额 = 收入 - 支出 + 转入 - 转出
    （初始余额已作为 income 记录在 ledger.csv 中）
    """
    account_id = account['id']
    # 注：initial_balance 已移至 ledger.csv 作为 income 记录
    initial = Decimal('0')
    
    income = Decimal('0')
    expense = Decimal('0')
    transfer_in = Decimal('0')
    transfer_out = Decimal('0')
    
    for entry in ledger_data:
        entry_type = entry.get('entry_type', '')
        amount_str = entry.get('amount', '0')
        try:
            amount = Decimal(amount_str)
        except:
            amount = Decimal('0')
        
        account_from = entry.get('account_from', '')
        account_to = entry.get('account_to', '')
        
        if entry_type == 'expense' and account_from == account_id:
            expense += amount
        elif entry_type == 'income' and account_to == account_id:
            income += amount
        elif entry_type == 'transfer':
            if account_from == account_id:
                transfer_out += amount
            if account_to == account_id:
                transfer_in += amount
    
    balance = initial + income - expense + transfer_in - transfer_out
    
    return {
        'initial': initial,
        'income': income,
        'expense': expense,
        'transfer_in': transfer_in,
        'transfer_out': transfer_out,
        'balance': balance,
    }


def show_account_balances():
    """显示所有账户余额"""
    print("\n" + "=" * 70)
    print("账户余额总览")
    print("=" * 70)
    
    accounts = load_accounts()
    account_groups = load_account_groups()
    ledger = load_ledger()
    
    # 获取基金和稳利宝市值（包含实际收益）
    fund_total, wenlibao_value, wenlibao_profit, product_details = get_fund_total_from_daily()
    
    # 获取稳利宝子账户
    wenlibao_accounts = get_accounts_by_group('wenlibao')
    wenlibao_initial_total = sum(
        Decimal(str(acc.get('initial_balance', 0))) 
        for acc in wenlibao_accounts
    )
    
    # 找到收益归属账户（项目资金）
    profit_account_id = account_groups.get('wenlibao', {}).get('profit_account', 'wenlibao_project')
    
    grand_total = Decimal('0')
    
    # ==================== 余利宝账户 ====================
    print("\n【余利宝】")
    print("-" * 70)
    print(f"{'账户名称':<20} {'初始余额':>12} {'收入':>10} {'支出':>10} {'转入':>10} {'转出':>10} {'当前余额':>12}")
    print("-" * 70)
    
    ylb_total = Decimal('0')
    for acc in accounts:
        if acc['id'] in ['ylb_life', 'ylb_finance']:
            result = calc_account_balance(acc, ledger)
            name = acc['name']
            print(f"{name:<20} {result['initial']:>12.2f} {result['income']:>10.2f} {result['expense']:>10.2f} {result['transfer_in']:>10.2f} {result['transfer_out']:>10.2f} {result['balance']:>12.2f}")
            ylb_total += result['balance']
    
    print("-" * 70)
    print(f"{'余利宝小计':<20} {'':<12} {'':<10} {'':<10} {'':<10} {'':<10} {ylb_total:>12.2f}")
    grand_total += ylb_total
    
    # ==================== 情侣小荷包 ====================
    print("\n【情侣小荷包】")
    print("-" * 70)
    
    couple_acc = get_account('couple_pocket')
    if couple_acc:
        result = calc_account_balance(couple_acc, ledger)
        # 从 daily.csv 获取 000686 的市值
        xiaohebao_detail = product_details.get('000686', {})
        xiaohebao_value = xiaohebao_detail.get('value', Decimal('0'))
        print(f"{couple_acc['name']:<20} {result['initial']:>12.2f} {result['income']:>10.2f} {result['expense']:>10.2f} {result['transfer_in']:>10.2f} {result['transfer_out']:>10.2f} {result['balance']:>12.2f}")
        if xiaohebao_value > 0:
            diff = xiaohebao_value - result['balance']
            if diff != 0:
                print(f"  (关联货基 000686 市值: {xiaohebao_value:.2f}，差异: {diff:+.2f})")
            else:
                print(f"  (关联货基 000686 市值: {xiaohebao_value:.2f}，一致 ✓)")
        else:
            print(f"  (关联货基 000686，需同步产品快照)")
        grand_total += result['balance']
    
    # ==================== 稳利宝子账户 ====================
    print("\n【稳利宝】")
    print("-" * 70)
    print(f"{'账户名称':<24} {'初始金额':>12} {'当前市值':>12} {'备注':<20}")
    print("-" * 70)
    
    # 用户给的分配已经是当前市值分配，收益已包含在内
    for acc in wenlibao_accounts:
        initial = Decimal(str(acc.get('initial_balance', 0)))
        note = acc.get('note', '')[:18]
        
        # 标记收益归属账户
        profit_mark = ""
        if acc['id'] == profit_account_id:
            profit_mark = " ★"
        
        print(f"{acc['name']:<22}{profit_mark:<2} {initial:>12.2f} {initial:>12.2f} {note:<18}")
    
    print("-" * 70)
    print(f"{'稳利宝小计':<24} {wenlibao_initial_total:>12.2f} {wenlibao_value:>12.2f}")
    if wenlibao_profit != 0:
        print(f"{'(累计收益)':<24} {'':<12} {wenlibao_profit:>+12.2f} (归入 ★ 项目资金)")
    grand_total += wenlibao_value
    
    # ==================== 基金账户 ====================
    print("\n【基金账户】")
    print("-" * 70)
    print(f"基金持仓总市值: {fund_total:>12.2f}")
    grand_total += fund_total
    
    # 显示基金明细
    if product_details:
        print("\n基金持仓明细:")
        print(f"{'代码':<12} {'名称':<30} {'市值':>12} {'盈亏':>10} {'收益率':>10}")
        print("-" * 70)
        for code, detail in sorted(product_details.items()):
            if code in ['FBAE41126E', '000686']:
                continue  # 跳过稳利宝和货基
            value = detail.get('value', Decimal('0'))
            if value > 0:
                name = detail.get('name', '')[:28]
                pnl = detail.get('pnl', '0')
                rate = detail.get('return_rate', '0')
                print(f"{code:<12} {name:<30} {value:>12.2f} {pnl:>10} {rate:>10}")
    
    # ==================== 其他账户 ====================
    other_accounts = [acc for acc in accounts if acc['id'] in ['bank_card', 'wechat', 'alipay', 'cash', 'other']]
    other_with_balance = []
    
    for acc in other_accounts:
        result = calc_account_balance(acc, ledger)
        if result['balance'] != 0 or result['initial'] != 0:
            other_with_balance.append((acc, result))
    
    if other_with_balance:
        print("\n【其他账户】")
        print("-" * 70)
        for acc, result in other_with_balance:
            print(f"{acc['name']:<20} {result['initial']:>12.2f} {result['income']:>10.2f} {result['expense']:>10.2f} {result['transfer_in']:>10.2f} {result['transfer_out']:>10.2f} {result['balance']:>12.2f}")
            grand_total += result['balance']
    
    # ==================== 总计 ====================
    print("\n" + "=" * 70)
    print(f"{'资产总计':<20} {grand_total:>50.2f}")
    print("=" * 70)
    
    # 显示分类汇总
    print(f"\n分类汇总:")
    print(f"  余利宝总额:     {ylb_total:>12.2f}")
    print(f"  稳利宝总额:     {wenlibao_value:>12.2f}")
    print(f"  基金总额:       {fund_total:>12.2f}")
    if couple_acc:
        couple_result = calc_account_balance(couple_acc, ledger)
        print(f"  小荷包:         {couple_result['balance']:>12.2f}")


# ============================================================
# 数据校验
# ============================================================

def check_data():
    """数据校验"""
    print("\n=== 数据校验 ===\n")
    
    errors = []
    warnings = []
    
    # 检查 transactions.csv
    print("检查 transactions.csv...")
    transactions = load_transactions()
    order_ids_tx = {}  # {(order_id, action): row_num}
    
    for i, tx in enumerate(transactions, 1):
        action = tx.get('action', '').lower()
        order_id = tx.get('order_id', '')
        
        # action 必须有效
        if action not in VALID_ACTIONS:
            warnings.append(f"transactions 第{i}行: action '{action}' 不常见")
        
        # buy_debit/buy_confirm/sell_confirm 必须有 order_id
        if action in ['buy_debit', 'buy_confirm', 'sell_confirm'] and not order_id:
            errors.append(f"transactions 第{i}行: {action} 缺少 order_id")
        
        # 检查重复
        if order_id:
            key = (order_id, action)
            if key in order_ids_tx:
                errors.append(f"transactions 第{i}行: order_id+action 重复 ({order_id}, {action})")
            order_ids_tx[key] = i
    
    print(f"  共 {len(transactions)} 条记录")
    
    # 检查 orders.csv
    print("检查 orders.csv...")
    orders = load_orders()
    order_ids_orders = set()
    
    for i, order in enumerate(orders, 1):
        order_id = order.get('order_id', '')
        order_type = order.get('order_type', '')
        status = order.get('status', '')
        
        # order_id 唯一性
        if order_id in order_ids_orders:
            errors.append(f"orders 第{i}行: order_id 重复 ({order_id})")
        order_ids_orders.add(order_id)
        
        # status 有效性
        if status not in VALID_ORDER_STATUS:
            errors.append(f"orders 第{i}行: status '{status}' 无效")
    
    print(f"  共 {len(orders)} 条订单")
    
    # 检查 ledger.csv
    print("检查 ledger.csv...")
    ledger = load_ledger()
    
    for i, entry in enumerate(ledger, 1):
        entry_type = entry.get('entry_type', '')
        
        if entry_type not in VALID_ENTRY_TYPES:
            errors.append(f"ledger 第{i}行: entry_type '{entry_type}' 无效")
        
        if entry_type == 'transfer':
            if entry.get('account_from') == entry.get('account_to'):
                errors.append(f"ledger 第{i}行: transfer 的 account_from 和 account_to 相同")
    
    print(f"  共 {len(ledger)} 条记录")
    
    # 检查 buy_confirm 是否有匹配的 buy_debit
    print("检查 buy_debit/buy_confirm 配对...")
    buy_debits = {tx['order_id'] for tx in transactions 
                  if tx.get('action', '').lower() == 'buy_debit' and tx.get('order_id')}
    
    for tx in transactions:
        action = tx.get('action', '').lower()
        order_id = tx.get('order_id', '')
        
        if action == 'buy_confirm' and order_id:
            if order_id not in buy_debits:
                # 检查是否是兼容的 buy 模式（有 amount）
                if not tx.get('amount'):
                    errors.append(f"buy_confirm ({order_id}) 找不到匹配的 buy_debit")
    
    # 输出结果
    if errors:
        print(f"\n错误 ({len(errors)}):")
        for e in errors[:20]:
            print(f"  ✗ {e}")
        if len(errors) > 20:
            print(f"  ... 还有 {len(errors) - 20} 条")
    
    if warnings:
        print(f"\n警告 ({len(warnings)}):")
        for w in warnings[:10]:
            print(f"  ⚠ {w}")
    
    if not errors and not warnings:
        print("\n✓ 校验通过")
    elif not errors:
        print(f"\n✓ 校验通过，有 {len(warnings)} 条警告")
    else:
        print(f"\n✗ 校验失败，有 {len(errors)} 条错误")


# ============================================================
# 采集与快照
# ============================================================

# ============================================================
# 主入口
# ============================================================

def silent_sync():
    """静默同步：采集净值并更新账户余额（完全无输出）"""
    import logging
    import sys
    from io import StringIO
    
    # 保存原始状态
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    logger = logging.getLogger()
    old_level = logger.level
    
    # 重定向输出
    sys.stdout = StringIO()
    sys.stderr = StringIO()
    logger.setLevel(logging.CRITICAL)  # 只显示严重错误
    
    try:
        collect_and_store()
        from core.daily_balance import create_daily_balance_snapshot
        create_daily_balance_snapshot(get_project_root())
    finally:
        # 恢复原始状态
        sys.stdout = old_stdout
        sys.stderr = old_stderr
        logger.setLevel(old_level)


def auto_collect_nav():
    """启动时自动采集净值并更新账户余额（静默模式，显示简短提示）"""
    print("\n正在同步数据...", end=" ", flush=True)
    
    try:
        silent_sync()
        print("✓ 完成")
    except Exception as e:
        print(f"⚠ 失败: {e}")


def interactive_mode():
    """交互模式"""
    # 启动时自动采集净值并更新账户余额
    auto_collect_nav()
    
    while True:
        print("\n" + "=" * 50)
        print("财富中枢 CLI")
        print("=" * 50)
        print("  [1] 记账 (生活收支)")
        print("  [2] 理财 (买入/赎回)")
        print("  [3] 工具 (查看/校验)")
        print("  [0] 退出")
        
        choice = input("\n请选择: ").strip()
        
        if choice == '0':
            break
        elif choice == '1':
            # 记账模式
            while True:
                print("\n=== 记账 ===")
                print("  [1] 新增记录 (收入/支出/转账)")
                print("  [2] 退款")
                print("  [0] 返回主菜单")
                
                sub = input("\n请选择: ").strip()
                if sub == '0':
                    break
                elif sub == '1':
                    # 普通记账循环
                    while True:
                        add_ledger_entry()
                        silent_sync()
                        print("\n" + "-" * 30)
                        cont = input("继续记账? (回车继续 / 0退出): ").strip()
                        if cont == '0':
                            break
                elif sub == '2':
                    # 退款循环
                    while True:
                        add_refund_entry()
                        silent_sync()
                        print("\n" + "-" * 30)
                        cont = input("继续退款? (回车继续 / 0退出): ").strip()
                        if cont == '0':
                            break
        elif choice == '2':
            # 理财模式：循环操作直到退出
            while True:
                print("\n=== 理财操作 ===")
                print("  [1] 买入扣款 (buy_debit) - 新定投")
                print("  [2] 赎回发起 (redeem_request)")
                print("  [3] 结算确认 (settle) - 处理到期订单")
                print("  [4] 补录历史交易 - 已完成的 buy/sell")
                print("  [0] 返回主菜单")
                
                sub = input("\n请选择: ").strip()
                if sub == '0':
                    break
                elif sub == '1':
                    # 买入扣款循环
                    while True:
                        add_buy_debit()
                        silent_sync()  # 操作后静默同步
                        print("\n" + "-" * 30)
                        cont = input("继续买入扣款? (回车继续 / 0退出): ").strip()
                        if cont == '0':
                            break
                elif sub == '2':
                    # 赎回发起循环
                    while True:
                        add_redeem_request()
                        silent_sync()  # 操作后静默同步
                        print("\n" + "-" * 30)
                        cont = input("继续赎回发起? (回车继续 / 0退出): ").strip()
                        if cont == '0':
                            break
                elif sub == '3':
                    settle_orders()
                    silent_sync()  # 结算后静默同步
                elif sub == '4':
                    add_history_trade()  # 内部已有循环
                    silent_sync()  # 补录后静默同步
        elif choice == '3':
            # 工具菜单：循环操作直到退出
            while True:
                print("\n=== 工具 ===")
                print("  [1] 查看账户余额")
                print("  [2] 查看账本")
                print("  [3] 查看订单")
                print("  [4] 查看交易")
                print("  [5] 数据校验")
                print("  [0] 返回主菜单")
                
                sub = input("\n请选择: ").strip()
                if sub == '0':
                    break
                elif sub == '1':
                    # 显示账户余额（数据已在启动时/操作后同步）
                    from core.daily_balance import display_account_balances
                    display_account_balances(get_project_root())
                elif sub == '2':
                    list_ledger()
                elif sub == '3':
                    list_orders()
                elif sub == '4':
                    list_transactions()
                elif sub == '5':
                    check_data()


def main():
    if len(sys.argv) < 2:
        interactive_mode()
        return
    
    cmd = sys.argv[1].lower()
    
    if cmd == 'add':
        interactive_mode()
    elif cmd == 'settle':
        settle_orders()
    elif cmd == 'list-ledger':
        n = int(sys.argv[2]) if len(sys.argv) > 2 else 20
        list_ledger(n)
    elif cmd == 'list-orders':
        n = int(sys.argv[2]) if len(sys.argv) > 2 else 20
        list_orders(n)
    elif cmd == 'list-tx':
        n = int(sys.argv[2]) if len(sys.argv) > 2 else 20
        list_transactions(n)
    elif cmd == 'check':
        check_data()
    elif cmd == 'collect':
        print("开始采集...\n")
        collect_and_store()
        print("\n✓ 净值采集完成")
        # 自动更新账户余额快照
        print("\n正在更新账户余额快照...")
        from core.daily_balance import create_daily_balance_snapshot
        count = create_daily_balance_snapshot(get_project_root())
        print(f"✓ 账户余额快照已更新: {count} 条")
    else:
        print(f"未知命令: {cmd}")
        print("可用命令: add | settle | list-ledger | list-orders | list-tx | check | collect")
        sys.exit(1)


if __name__ == "__main__":
    main()
