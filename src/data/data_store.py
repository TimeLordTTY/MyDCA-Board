#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
数据存储模块

管理三个核心 CSV 文件：
- transactions.csv: 交易流水（买入/卖出/分红）
- orders.csv: 理财任务队列（扣款/赎回发起 -> 自动结算确认）
- ledger.csv: 生活账本（日常收支记录）

所有 CSV 使用 UTF-8 编码，列顺序固定。
"""
import csv
import os
from pathlib import Path
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
from typing import List, Dict, Optional, Tuple
import logging

from data.config_loader import get_project_root

logger = logging.getLogger(__name__)

# ============================================================
# transactions.csv - 交易流水
# ============================================================

TRANSACTIONS_FIELDNAMES = [
    'date', 'product_code', 'action', 'amount', 'shares', 
    'fee', 'nav', 'nav_date', 'order_id', 'note'
]

VALID_ACTIONS = ['buy_debit', 'buy_confirm', 'buy', 'sell', 'sell_confirm', 'dividend']


def get_transactions_path() -> Path:
    """获取 transactions.csv 路径"""
    return get_project_root() / "data" / "transactions.csv"


def load_transactions() -> List[Dict]:
    """加载所有交易记录"""
    tx_path = get_transactions_path()
    if not tx_path.exists():
        return []
    
    rows = []
    with open(tx_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)
    return rows


def append_transaction(record: Dict) -> None:
    """追加一条交易记录"""
    tx_path = get_transactions_path()
    file_exists = tx_path.exists()
    
    # 确保目录存在
    tx_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(tx_path, 'a', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=TRANSACTIONS_FIELDNAMES)
        if not file_exists:
            writer.writeheader()
        writer.writerow(record)


def transaction_exists(order_id: str, action: str) -> bool:
    """检查是否已存在指定 order_id 和 action 的交易记录（用于幂等性检查）"""
    if not order_id:
        return False
    
    transactions = load_transactions()
    for tx in transactions:
        if tx.get('order_id') == order_id and tx.get('action', '').lower() == action.lower():
            return True
    return False


# ============================================================
# orders.csv - 理财任务队列
# ============================================================

ORDERS_FIELDNAMES = [
    'order_id', 'product_code', 'order_type', 'amount', 'fee', 'shares',
    'requested_at', 'trade_date', 'nav_date', 'confirm_date', 
    'holding_days', 'sell_fee_rate',  # 赎回时保存持有天数和费率
    'status', 'note'
]

VALID_ORDER_TYPES = ['buy_debit', 'redeem_request']
VALID_ORDER_STATUS = ['pending', 'done', 'cancelled']


def get_orders_path() -> Path:
    """获取 orders.csv 路径"""
    return get_project_root() / "data" / "orders.csv"


def load_orders() -> List[Dict]:
    """加载所有订单"""
    orders_path = get_orders_path()
    if not orders_path.exists():
        return []
    
    rows = []
    with open(orders_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)
    return rows


def save_orders(orders: List[Dict]) -> None:
    """保存所有订单（覆盖写入）"""
    orders_path = get_orders_path()
    orders_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(orders_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=ORDERS_FIELDNAMES)
        writer.writeheader()
        for order in orders:
            writer.writerow(order)


def append_order(record: Dict) -> None:
    """追加一条订单"""
    orders_path = get_orders_path()
    file_exists = orders_path.exists()
    
    orders_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(orders_path, 'a', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=ORDERS_FIELDNAMES)
        if not file_exists:
            writer.writeheader()
        writer.writerow(record)


def get_pending_orders(before_date: Optional[str] = None) -> List[Dict]:
    """获取待处理订单
    
    Args:
        before_date: 可选，只返回 confirm_date <= before_date 的订单
    
    Returns:
        List[Dict]: 待处理订单列表
    """
    orders = load_orders()
    pending = []
    
    for order in orders:
        if order.get('status', '').lower() != 'pending':
            continue
        
        if before_date:
            confirm_date = order.get('confirm_date', '')
            if confirm_date and confirm_date > before_date:
                continue
        
        pending.append(order)
    
    return pending


def update_order_status(order_id: str, new_status: str) -> bool:
    """更新订单状态
    
    Returns:
        bool: 是否找到并更新
    """
    orders = load_orders()
    updated = False
    
    for order in orders:
        if order.get('order_id') == order_id:
            order['status'] = new_status
            updated = True
            break
    
    if updated:
        save_orders(orders)
    
    return updated


def order_exists(order_id: str) -> bool:
    """检查订单是否存在"""
    orders = load_orders()
    return any(o.get('order_id') == order_id for o in orders)


# ============================================================
# ledger.csv - 生活账本
# ============================================================

LEDGER_FIELDNAMES = [
    'event_time', 'entry_type', 'amount', 'category_l1', 'category_l2',
    'account_from', 'account_to', 'discount', 'reimbursable', 'note'
]

VALID_ENTRY_TYPES = ['expense', 'income', 'transfer']


def get_ledger_path() -> Path:
    """获取 ledger.csv 路径"""
    return get_project_root() / "data" / "ledger.csv"


def load_ledger() -> List[Dict]:
    """加载所有账本记录"""
    ledger_path = get_ledger_path()
    if not ledger_path.exists():
        return []
    
    rows = []
    with open(ledger_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)
    return rows


def append_ledger(record: Dict) -> None:
    """追加一条账本记录"""
    ledger_path = get_ledger_path()
    file_exists = ledger_path.exists()
    
    ledger_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(ledger_path, 'a', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=LEDGER_FIELDNAMES)
        if not file_exists:
            writer.writeheader()
        writer.writerow(record)


# ============================================================
# 工具函数
# ============================================================

def generate_order_id(product_code: str) -> str:
    """生成订单号
    
    格式：YYYYMMDDHHMMSS_{product_code}_{seq}
    
    seq 规则：从现有 orders/transactions 中同秒同产品的最大 seq + 1
    """
    timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
    prefix = f"{timestamp}_{product_code}_"
    
    # 查找现有最大 seq
    max_seq = 0
    
    # 从 orders.csv 查找
    orders = load_orders()
    for order in orders:
        order_id = order.get('order_id', '')
        if order_id.startswith(prefix):
            try:
                seq = int(order_id.split('_')[-1])
                max_seq = max(max_seq, seq)
            except ValueError:
                pass
    
    # 从 transactions.csv 查找
    transactions = load_transactions()
    for tx in transactions:
        order_id = tx.get('order_id', '')
        if order_id.startswith(prefix):
            try:
                seq = int(order_id.split('_')[-1])
                max_seq = max(max_seq, seq)
            except ValueError:
                pass
    
    new_seq = max_seq + 1
    return f"{prefix}{new_seq:03d}"


def format_decimal(d: Decimal, places: int = 2) -> str:
    """格式化 Decimal，避免科学计数法"""
    if d == 0:
        return ''
    
    # 四舍五入到指定位数
    quantize_str = '0.' + '0' * places
    rounded = d.quantize(Decimal(quantize_str), rounding=ROUND_HALF_UP)
    
    # 转为字符串，去除尾部多余的0
    s = str(rounded)
    if '.' in s:
        s = s.rstrip('0').rstrip('.')
    
    return s


def parse_decimal(value, default=Decimal('0')) -> Decimal:
    """安全解析 Decimal"""
    if value is None:
        return default
    if isinstance(value, Decimal):
        return value
    
    s = str(value).strip()
    if s == '' or s == '-':
        return default
    
    s = s.replace(',', '')
    
    try:
        return Decimal(s)
    except Exception:
        return default

