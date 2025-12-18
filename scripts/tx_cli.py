#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
交易记录交互录入器

用法：
  python tx_cli.py add    # 交互新增一条交易记录
  python tx_cli.py list   # 列表展示最近 N 条（默认20）
  python tx_cli.py check  # 校验（列齐全、数值可解析、debit/confirm成对）

支持的 action 类型（扣款/确认分离模式）：
  - buy_debit:   扣款事件（钱已扣，份额未到）
  - buy_confirm: 份额确认事件（份额正式到账）
  - buy:         兼容模式（当天既扣款又确认）
  - sell:        卖出确认
  - dividend:    分红（份额增加，成本不变）

成本口径（统一规则）：
  cost = amount - fee  （净申购额入账）

transactions.csv 固定 10 列：
  date,product_code,action,amount,shares,fee,nav,nav_date,order_id,note
"""

import sys
import csv
import io
import uuid
from pathlib import Path
from datetime import datetime
from decimal import Decimal, InvalidOperation

# 设置 stdout 编码为 utf-8，避免 Windows 控制台编码问题
if sys.stdout.encoding != 'utf-8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

# 添加项目路径
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))

from config_loader import get_project_root, load_products

# CSV 列定义（固定 10 列，顺序不可变）
FIELDNAMES = ['date', 'product_code', 'action', 'amount', 'shares', 'fee', 'nav', 'nav_date', 'order_id', 'note']

# 支持的 action 类型
VALID_ACTIONS = ['buy_debit', 'buy_confirm', 'buy', 'sell', 'dividend']


def get_transactions_path():
    """获取 transactions.csv 路径"""
    return get_project_root() / "data" / "transactions.csv"


def load_products_list():
    """加载产品列表，返回 [(product_code, product_name), ...]"""
    products = load_products()
    return [(p['product_code'], p['product_name']) for p in products]


def generate_order_id():
    """生成唯一的 order_id（格式：ORD + 日期 + 6位随机）"""
    date_str = datetime.now().strftime('%Y%m%d')
    random_suffix = uuid.uuid4().hex[:6].upper()
    return f"ORD{date_str}{random_suffix}"


def parse_decimal(value, allow_empty=False, default=Decimal('0')):
    """解析 Decimal，支持空值和带空格的输入"""
    if value is None:
        return default if allow_empty else None
    
    s = str(value).strip()
    if s == '' or s == '-':
        return default if allow_empty else None
    
    # 移除千分位逗号
    s = s.replace(',', '')
    
    try:
        return Decimal(s)
    except InvalidOperation:
        return None


def validate_date(date_str):
    """验证日期格式 YYYY-MM-DD"""
    try:
        datetime.strptime(date_str, '%Y-%m-%d')
        return True
    except ValueError:
        return False


def select_product():
    """交互选择产品"""
    products = load_products_list()
    
    print("\n=== 选择产品 ===")
    for i, (code, name) in enumerate(products, 1):
        print(f"  [{i}] {code} - {name}")
    
    while True:
        choice = input("\n请输入序号或产品代码: ").strip()
        
        # 尝试按序号选择
        try:
            idx = int(choice) - 1
            if 0 <= idx < len(products):
                return products[idx]
        except ValueError:
            pass
        
        # 尝试按产品代码选择
        for code, name in products:
            if code == choice:
                return (code, name)
        
        print("✗ 无效选择，请重试")


def select_action():
    """交互选择 action"""
    print("\n=== 选择操作类型 ===")
    print("  [1] buy_debit   - 扣款事件（钱已扣，份额未到）")
    print("  [2] buy_confirm - 份额确认（份额正式到账）")
    print("  [3] buy         - 买入确认（兼容模式，当天扣款+确认）")
    print("  [4] sell        - 卖出确认")
    print("  [5] dividend    - 分红")
    
    action_map = {
        '1': 'buy_debit', '2': 'buy_confirm', '3': 'buy',
        '4': 'sell', '5': 'dividend'
    }
    
    while True:
        choice = input("\n请输入序号或操作名: ").strip().lower()
        
        # 按序号选择
        if choice in action_map:
            return action_map[choice]
        
        # 按名称选择
        if choice in VALID_ACTIONS:
            return choice
        
        print("✗ 无效选择，请重试")


def input_date(prompt, default=None):
    """输入日期，支持默认值"""
    if default is None:
        default = datetime.now().strftime('%Y-%m-%d')
    
    while True:
        value = input(f"{prompt} (默认 {default}): ").strip()
        if value == '':
            return default
        
        if validate_date(value):
            return value
        
        print("✗ 日期格式错误，请使用 YYYY-MM-DD")


def input_decimal(prompt, required=True, allow_zero=True, must_positive=False, default=None):
    """输入 Decimal 数值"""
    default_hint = f" (默认 {default})" if default is not None else ""
    required_hint = " [必填]" if required else " [可选]"
    
    while True:
        value = input(f"{prompt}{default_hint}{required_hint}: ").strip()
        
        if value == '':
            if default is not None:
                return Decimal(str(default))
            if not required:
                return Decimal('0')
            print("✗ 此字段必填")
            continue
        
        d = parse_decimal(value)
        if d is None:
            print("✗ 数值格式错误")
            continue
        
        if not allow_zero and d == 0:
            print("✗ 不能为 0")
            continue
        
        if must_positive and d <= 0:
            print("✗ 必须为正数")
            continue
        
        if d < 0:
            print("✗ 不能为负数")
            continue
        
        return d


def input_string(prompt, required=False, default=''):
    """输入字符串，支持默认值"""
    hint = " [必填]" if required else " [可选]"
    while True:
        value = input(f"{prompt}{hint}: ").strip()
        if value:
            return value
        if not required:
            return default
        print("✗ 此字段必填")


def format_decimal(d, places=2):
    """格式化 Decimal，保留指定小数位，不使用科学计数法"""
    if d == 0:
        return ''
    fmt = f"{{:.{places}f}}"
    s = fmt.format(d)
    if '.' in s:
        s = s.rstrip('0').rstrip('.')
    return s


def add_transaction():
    """交互新增一条交易记录"""
    print("\n" + "=" * 60)
    print("新增交易记录")
    print("=" * 60)
    
    # 1. 选择产品
    product_code, product_name = select_product()
    print(f"✓ 产品: {product_code} - {product_name}")
    
    # 2. 选择 action
    action = select_action()
    print(f"✓ 操作: {action}")
    
    # 3. 输入 event_date
    event_date = input_date("\n交易日期 (date)")
    print(f"✓ 交易日期: {event_date}")
    
    # 4. 根据 action 输入不同字段
    amount = Decimal('0')
    shares = Decimal('0')
    fee = Decimal('0')
    nav = Decimal('0')
    nav_date = ''
    order_id = ''
    
    if action == 'buy_debit':
        # 扣款事件：amount 必填，fee 可选，自动生成 order_id
        print("\n--- 扣款信息 ---")
        amount = input_decimal("扣款金额 (amount)", required=True, must_positive=True)
        fee = input_decimal("手续费 (fee)", required=False, default=0)
        
        # 自动生成 order_id
        order_id = generate_order_id()
        print(f"✓ 自动生成 order_id: {order_id}")
        
        net_amount = amount - fee
        print(f"\n  → 在途净额 (amount - fee): {format_decimal(net_amount, 2)}")
        
    elif action == 'buy_confirm':
        # 份额确认：order_id 必填，shares、nav、nav_date 必填
        print("\n--- 份额确认信息 ---")
        order_id = input_string("订单号 (order_id) - 必须与扣款记录匹配", required=True)
        shares = input_decimal("确认份额 (shares)", required=True, must_positive=True)
        nav = input_decimal("确认净值 (nav)", required=True, must_positive=True)
        nav_date = input_date("净值日期 (nav_date)")
        
        # 可选：提供 amount 用于降级兼容（但会打印警告）
        print("\n如果没有匹配的 buy_debit 记录，可以提供 amount 用于降级计算：")
        amount_input = input("降级金额 (amount) [可选，正常流程无需填写]: ").strip()
        if amount_input:
            amount = parse_decimal(amount_input) or Decimal('0')
            fee_input = input("手续费 (fee) [可选]: ").strip()
            fee = parse_decimal(fee_input) or Decimal('0')
            print("⚠ 注意：提供了降级 amount，如果找不到匹配的 debit 将使用此值计算成本")
        
        # 校验：nav_date 不晚于 event_date
        if nav_date > event_date:
            print(f"⚠ 警告: 净值日期 ({nav_date}) 晚于交易日期 ({event_date})")
            confirm = input("是否继续? (y/n): ").strip().lower()
            if confirm != 'y':
                print("已取消")
                return
        
    elif action == 'buy':
        # 兼容模式：amount, shares, nav, nav_date 必填，fee 可选
        print("\n--- 买入信息（兼容模式）---")
        amount = input_decimal("买入金额 (amount)", required=True, must_positive=True)
        fee = input_decimal("手续费 (fee)", required=False, default=0)
        shares = input_decimal("确认份额 (shares)", required=True, must_positive=True)
        nav = input_decimal("成交净值 (nav)", required=True, must_positive=True)
        nav_date = input_date("净值日期 (nav_date)")
        
        # buy 模式也可以提供 order_id（可选）
        order_id_input = input("订单号 (order_id) [可选]: ").strip()
        order_id = order_id_input if order_id_input else ''
        
        # 校验：nav_date 不晚于 event_date
        if nav_date > event_date:
            print(f"⚠ 警告: 净值日期 ({nav_date}) 晚于交易日期 ({event_date})")
            confirm = input("是否继续? (y/n): ").strip().lower()
            if confirm != 'y':
                print("已取消")
                return
        
        net_amount = amount - fee
        print(f"\n  → 成本入账 (amount - fee): {format_decimal(net_amount, 2)}")
        
    elif action == 'sell':
        # 卖出：shares, nav, nav_date 必填；amount, fee 可选
        print("\n--- 卖出信息 ---")
        shares = input_decimal("卖出份额 (shares)", required=True, must_positive=True)
        nav = input_decimal("成交净值 (nav)", required=True, must_positive=True)
        nav_date = input_date("净值日期 (nav_date)")
        amount = input_decimal("到账金额 (amount)", required=False, default=0)
        fee = input_decimal("手续费 (fee)", required=False, default=0)
        
        # 校验：nav_date 不晚于 event_date
        if nav_date > event_date:
            print(f"⚠ 警告: 净值日期 ({nav_date}) 晚于交易日期 ({event_date})")
            confirm = input("是否继续? (y/n): ").strip().lower()
            if confirm != 'y':
                print("已取消")
                return
        
    elif action == 'dividend':
        # 分红：shares 必填；nav, nav_date 可选
        print("\n--- 分红信息 ---")
        shares = input_decimal("分红份额 (shares)", required=True, must_positive=True)
        nav_input = input("分红净值 (nav) [可选]: ").strip()
        if nav_input:
            nav = parse_decimal(nav_input) or Decimal('0')
            nav_date = input_date("净值日期 (nav_date)")
    
    # 5. 输入备注（自动补产品名称）
    note = input_string("\n备注 (note)", default=product_name)
    
    # 6. 确认
    print("\n" + "-" * 60)
    print("请确认以下信息：")
    print(f"  日期: {event_date}")
    print(f"  产品: {product_code} - {product_name}")
    print(f"  操作: {action}")
    if amount > 0:
        print(f"  金额: {format_decimal(amount, 2)}")
    if shares > 0:
        print(f"  份额: {format_decimal(shares, 2)}")
    if fee > 0:
        print(f"  手续费: {format_decimal(fee, 2)}")
    if nav > 0:
        print(f"  净值: {format_decimal(nav, 4)}")
    if nav_date:
        print(f"  净值日期: {nav_date}")
    if order_id:
        print(f"  订单号: {order_id}")
    if note:
        print(f"  备注: {note}")
    
    confirm = input("\n确认写入? (y/n): ").strip().lower()
    if confirm != 'y':
        print("已取消")
        return
    
    # 7. 写入 CSV（固定 10 列）
    tx_path = get_transactions_path()
    file_exists = tx_path.exists()
    
    # 构建行数据（空值写空字符串）
    row = {
        'date': event_date,
        'product_code': product_code,
        'action': action,
        'amount': format_decimal(amount, 2) if amount > 0 else '',
        'shares': format_decimal(shares, 2) if shares > 0 else '',
        'fee': format_decimal(fee, 2) if fee > 0 else '',
        'nav': format_decimal(nav, 4) if nav > 0 else '',
        'nav_date': nav_date,
        'order_id': order_id,
        'note': note
    }
    
    with open(tx_path, 'a', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=FIELDNAMES)
        if not file_exists:
            writer.writeheader()
        writer.writerow(row)
    
    print(f"\n✓ 已写入: {tx_path}")


def list_transactions(n=20):
    """列表展示最近 N 条交易记录"""
    tx_path = get_transactions_path()
    
    if not tx_path.exists():
        print(f"✗ 文件不存在: {tx_path}")
        return
    
    rows = []
    with open(tx_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)
    
    if not rows:
        print("暂无交易记录")
        return
    
    # 取最后 N 条
    recent = rows[-n:]
    
    print(f"\n=== 最近 {len(recent)} 条交易记录 ===\n")
    header = f"{'#':<4} {'日期':<12} {'产品':<12} {'操作':<12} {'金额':<10} {'份额':<10} {'fee':<6} {'nav':<8} {'order_id':<18}"
    print(header)
    print("-" * len(header))
    
    for i, row in enumerate(recent, len(rows) - len(recent) + 1):
        date = row.get('date', '')
        product_code = row.get('product_code', '')
        action = row.get('action', '')
        amount = row.get('amount', '')
        shares = row.get('shares', '')
        fee = row.get('fee', '')
        nav = row.get('nav', '')
        order_id = row.get('order_id', '')[:16] if row.get('order_id') else ''
        
        print(f"{i:<4} {date:<12} {product_code:<12} {action:<12} {amount:<10} {shares:<10} {fee:<6} {nav:<8} {order_id:<18}")
    
    print(f"\n共 {len(rows)} 条记录")


def check_transactions():
    """校验：列齐全、数值可解析、debit/confirm 成对检查"""
    tx_path = get_transactions_path()
    
    if not tx_path.exists():
        print(f"✗ 文件不存在: {tx_path}")
        return
    
    print(f"\n=== 校验 {tx_path} ===\n")
    
    errors = []
    warnings = []
    row_count = 0
    
    # 收集 debit/confirm 记录用于成对检查
    debits = {}   # {(product_code, order_id): row_info}
    confirms = {} # {(product_code, order_id): row_info}
    
    with open(tx_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        
        # 检查列名
        missing_cols = set(FIELDNAMES) - set(reader.fieldnames or [])
        if missing_cols:
            errors.append(f"缺少列: {missing_cols}")
        
        extra_cols = set(reader.fieldnames or []) - set(FIELDNAMES)
        if extra_cols:
            warnings.append(f"多余列: {extra_cols}")
        
        for i, row in enumerate(reader, 2):
            row_count += 1
            
            # 检查必填字段
            if not row.get('date'):
                errors.append(f"第 {i} 行: 缺少 date")
            elif not validate_date(row['date']):
                errors.append(f"第 {i} 行: date 格式错误 ({row['date']})")
            
            if not row.get('product_code'):
                errors.append(f"第 {i} 行: 缺少 product_code")
            
            action = row.get('action', '').lower()
            if not action:
                errors.append(f"第 {i} 行: 缺少 action")
            elif action not in VALID_ACTIONS:
                warnings.append(f"第 {i} 行: action 值不常见 ({action})")
            
            # 检查数值字段可解析
            for field in ['amount', 'shares', 'fee', 'nav']:
                value = row.get(field, '').strip()
                if value and value != '-':
                    d = parse_decimal(value)
                    if d is None:
                        errors.append(f"第 {i} 行: {field} 无法解析为数值 ({value})")
            
            # 检查 nav_date
            nav_date = row.get('nav_date', '').strip()
            if nav_date and not validate_date(nav_date):
                errors.append(f"第 {i} 行: nav_date 格式错误 ({nav_date})")
            
            # 收集 debit/confirm 记录
            product_code = row.get('product_code', '')
            order_id = row.get('order_id', '').strip()
            
            if action == 'buy_debit':
                if not order_id:
                    warnings.append(f"第 {i} 行: buy_debit 缺少 order_id（可能导致永久在途）")
                else:
                    key = (product_code, order_id)
                    if key in debits:
                        errors.append(f"第 {i} 行: 重复的 buy_debit order_id ({order_id})")
                    else:
                        debits[key] = {'row': i, 'date': row.get('date')}
            
            elif action == 'buy_confirm':
                if not order_id:
                    errors.append(f"第 {i} 行: buy_confirm 必须提供 order_id")
                else:
                    key = (product_code, order_id)
                    if key in confirms:
                        errors.append(f"第 {i} 行: 重复的 buy_confirm order_id ({order_id})")
                    else:
                        confirms[key] = {'row': i, 'date': row.get('date')}
    
    # 检查 debit/confirm 成对
    print(f"共 {row_count} 条记录\n")
    
    # 检查未确认的 debit
    unconfirmed_debits = set(debits.keys()) - set(confirms.keys())
    if unconfirmed_debits:
        print(f"⚠ 未确认的扣款（在途资金）: {len(unconfirmed_debits)} 笔")
        for key in sorted(unconfirmed_debits):
            product_code, order_id = key
            info = debits[key]
            print(f"    - {product_code} / {order_id} (第{info['row']}行, {info['date']})")
    
    # 检查无匹配 debit 的 confirm
    orphan_confirms = set(confirms.keys()) - set(debits.keys())
    if orphan_confirms:
        for key in orphan_confirms:
            product_code, order_id = key
            info = confirms[key]
            errors.append(f"第 {info['row']} 行: buy_confirm 找不到匹配的 buy_debit (order_id={order_id})")
    
    # 检查跨产品的 order_id
    all_order_ids = {}
    for (product_code, order_id), info in {**debits, **confirms}.items():
        if order_id:
            if order_id in all_order_ids and all_order_ids[order_id] != product_code:
                errors.append(f"order_id '{order_id}' 跨产品使用 ({all_order_ids[order_id]} vs {product_code})")
            all_order_ids[order_id] = product_code
    
    # 输出结果
    if errors:
        print(f"\n错误 ({len(errors)}):")
        for e in errors[:20]:
            print(f"  ✗ {e}")
        if len(errors) > 20:
            print(f"  ... 还有 {len(errors) - 20} 条错误")
    
    if warnings:
        print(f"\n警告 ({len(warnings)}):")
        for w in warnings[:10]:
            print(f"  ⚠ {w}")
        if len(warnings) > 10:
            print(f"  ... 还有 {len(warnings) - 10} 条警告")
    
    if not errors and not warnings and not unconfirmed_debits:
        print("✓ 校验通过，无错误")
    elif not errors:
        print(f"\n✓ 校验通过，有 {len(warnings)} 条警告")
    else:
        print(f"\n✗ 校验失败，有 {len(errors)} 条错误")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        print("\n请指定命令: add | list | check")
        sys.exit(1)
    
    cmd = sys.argv[1].lower()
    
    if cmd == 'add':
        add_transaction()
    elif cmd == 'list':
        n = int(sys.argv[2]) if len(sys.argv) > 2 else 20
        list_transactions(n)
    elif cmd == 'check':
        check_transactions()
    else:
        print(f"未知命令: {cmd}")
        print("可用命令: add | list | check")
        sys.exit(1)


if __name__ == "__main__":
    main()
