#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
数据存储模块

仅支持 MySQL 数据库存储。

管理三个核心数据：
- transactions: 交易流水（买入/卖出/分红）
- orders: 理财任务队列（扣款/赎回发起 -> 自动结算确认）
- ledger: 生活账本（日常收支记录）
"""
import os
from pathlib import Path
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
from typing import List, Dict, Optional, Tuple
import logging

from data.config_loader import get_project_root
from data.db_connector import (
    execute_query, execute_one, 
    execute_update, execute_insert, execute_many
)

logger = logging.getLogger(__name__)


# ============================================================
# transactions - 交易流水
# ============================================================

TRANSACTIONS_FIELDNAMES = [
    'date', 'product_code', 'action', 'amount', 'shares', 
    'fee', 'nav', 'nav_date', 'order_id', 'note'
]

VALID_ACTIONS = ['buy_debit', 'buy_confirm', 'buy', 'sell', 'sell_confirm', 'dividend']


def load_transactions() -> List[Dict]:
    """加载所有交易记录"""
    sql = """
        SELECT id,
               DATE_FORMAT(`date`, '%%Y-%%m-%%d') as `date`,
               product_code, action, amount, shares, fee, nav,
               DATE_FORMAT(nav_date, '%%Y-%%m-%%d') as nav_date,
               order_id, note,
               DATE_FORMAT(created_at, '%%Y-%%m-%%d %%H:%%i:%%s') as created_at
        FROM transactions
        ORDER BY created_at, id
    """
    return execute_query(sql)


def load_recent_transactions(n: int = 30) -> List[Dict]:
    """
    加载最近 N 条交易记录
    
    Args:
        n: 返回条数
    
    Returns:
        交易记录列表（按时间倒序）
    """
    sql = """
        SELECT id,
               DATE_FORMAT(`date`, '%%Y-%%m-%%d') as `date`,
               product_code, action, amount, shares, fee, nav,
               DATE_FORMAT(nav_date, '%%Y-%%m-%%d') as nav_date,
               order_id, note,
               DATE_FORMAT(created_at, '%%Y-%%m-%%d %%H:%%i:%%s') as created_at
        FROM transactions
        ORDER BY created_at DESC, id DESC
        LIMIT %s
    """
    return execute_query(sql, (n,))


def append_transaction(record: Dict) -> None:
    """追加一条交易记录"""
    # 如果有 created_at 字段，使用它；否则数据库自动设置
    created_at = record.get('created_at')
    
    if created_at:
        sql = """
            INSERT INTO transactions 
            (`date`, product_code, action, amount, shares, fee, nav, nav_date, order_id, note, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        params = (
            record.get('date'),
            record.get('product_code'),
            record.get('action'),
            record.get('amount') or None,
            record.get('shares') or None,
            record.get('fee') or None,
            record.get('nav') or None,
            record.get('nav_date') or None,
            record.get('order_id') or None,
            record.get('note') or None,
            created_at
        )
    else:
        sql = """
            INSERT INTO transactions 
            (`date`, product_code, action, amount, shares, fee, nav, nav_date, order_id, note)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        params = (
            record.get('date'),
            record.get('product_code'),
            record.get('action'),
            record.get('amount') or None,
            record.get('shares') or None,
            record.get('fee') or None,
            record.get('nav') or None,
            record.get('nav_date') or None,
            record.get('order_id') or None,
            record.get('note') or None
        )
    execute_insert(sql, params)


def transaction_exists(order_id: str, action: str) -> bool:
    """检查是否已存在指定 order_id 和 action 的交易记录（用于幂等性检查）"""
    if not order_id:
        return False
    
    sql = "SELECT COUNT(*) as cnt FROM transactions WHERE order_id = %s AND action = %s"
    result = execute_one(sql, (order_id, action.lower()))
    return result and int(result.get('cnt', 0)) > 0


def update_transaction(record_id: int, record: Dict) -> bool:
    """更新交易记录"""
    sql = """
        UPDATE transactions SET
            `date` = %s, product_code = %s, action = %s,
            amount = %s, shares = %s, fee = %s,
            nav = %s, nav_date = %s, note = %s
        WHERE id = %s
    """
    params = (
        record.get('date'),
        record.get('product_code'),
        record.get('action'),
        record.get('amount') or None,
        record.get('shares') or None,
        record.get('fee') or None,
        record.get('nav') or None,
        record.get('nav_date') or None,
        record.get('note') or None,
        record_id
    )
    affected = execute_update(sql, params)
    return affected > 0


def delete_transaction(record_id: int) -> bool:
    """删除交易记录"""
    sql = "DELETE FROM transactions WHERE id = %s"
    affected = execute_update(sql, (record_id,))
    return affected > 0


# ============================================================
# orders - 理财任务队列
# ============================================================

ORDERS_FIELDNAMES = [
    'order_id', 'product_code', 'order_type', 'amount', 'fee', 'shares',
    'requested_at', 'trade_date', 'nav_date', 'confirm_date', 
    'holding_days', 'sell_fee_rate',
    'status', 'note'
]

VALID_ORDER_TYPES = ['buy_debit', 'redeem_request']
VALID_ORDER_STATUS = ['pending', 'done', 'cancelled']


def load_orders() -> List[Dict]:
    """加载所有订单"""
    sql = """
        SELECT order_id, product_code, order_type, amount, fee, shares,
               DATE_FORMAT(requested_at, '%%Y-%%m-%%d %%H:%%i:%%s') as requested_at,
               DATE_FORMAT(trade_date, '%%Y-%%m-%%d') as trade_date,
               DATE_FORMAT(nav_date, '%%Y-%%m-%%d') as nav_date,
               DATE_FORMAT(confirm_date, '%%Y-%%m-%%d') as confirm_date,
               holding_days, sell_fee_rate, status, note
        FROM orders
        ORDER BY requested_at, id
    """
    return execute_query(sql)


def save_orders(orders: List[Dict]) -> None:
    """保存所有订单（更新模式）"""
    for order in orders:
        sql = """
            UPDATE orders SET 
                status = %s, amount = %s, fee = %s, shares = %s,
                holding_days = %s, sell_fee_rate = %s, note = %s
            WHERE order_id = %s
        """
        execute_update(sql, (
            order.get('status'),
            order.get('amount') or None,
            order.get('fee') or None,
            order.get('shares') or None,
            order.get('holding_days') or None,
            order.get('sell_fee_rate') or None,
            order.get('note') or None,
            order.get('order_id')
        ))


def append_order(record: Dict) -> None:
    """追加一条订单"""
    sql = """
        INSERT INTO orders 
        (order_id, product_code, order_type, amount, fee, shares,
         requested_at, trade_date, nav_date, confirm_date,
         holding_days, sell_fee_rate, status, note)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    params = (
        record.get('order_id'),
        record.get('product_code'),
        record.get('order_type'),
        record.get('amount') or None,
        record.get('fee') or None,
        record.get('shares') or None,
        record.get('requested_at'),
        record.get('trade_date') or None,
        record.get('nav_date') or None,
        record.get('confirm_date') or None,
        record.get('holding_days') or None,
        record.get('sell_fee_rate') or None,
        record.get('status', 'pending'),
        record.get('note') or None
    )
    execute_insert(sql, params)


def get_pending_orders(before_date: Optional[str] = None) -> List[Dict]:
    """获取待处理订单"""
    if before_date:
        sql = """
            SELECT order_id, product_code, order_type, amount, fee, shares,
                   DATE_FORMAT(requested_at, '%%Y-%%m-%%d %%H:%%i:%%s') as requested_at,
                   DATE_FORMAT(trade_date, '%%Y-%%m-%%d') as trade_date,
                   DATE_FORMAT(nav_date, '%%Y-%%m-%%d') as nav_date,
                   DATE_FORMAT(confirm_date, '%%Y-%%m-%%d') as confirm_date,
                   holding_days, sell_fee_rate, status, note
            FROM orders
            WHERE status = 'pending' AND (confirm_date IS NULL OR confirm_date <= %s)
            ORDER BY requested_at
        """
        return execute_query(sql, (before_date,))
    else:
        sql = """
            SELECT order_id, product_code, order_type, amount, fee, shares,
                   DATE_FORMAT(requested_at, '%%Y-%%m-%%d %%H:%%i:%%s') as requested_at,
                   DATE_FORMAT(trade_date, '%%Y-%%m-%%d') as trade_date,
                   DATE_FORMAT(nav_date, '%%Y-%%m-%%d') as nav_date,
                   DATE_FORMAT(confirm_date, '%%Y-%%m-%%d') as confirm_date,
                   holding_days, sell_fee_rate, status, note
            FROM orders
            WHERE status = 'pending'
            ORDER BY requested_at
        """
        return execute_query(sql)


def update_order_status(order_id: str, new_status: str) -> bool:
    """更新订单状态"""
    sql = "UPDATE orders SET status = %s WHERE order_id = %s"
    affected = execute_update(sql, (new_status, order_id))
    return affected > 0


def update_order(order_id: str, updates: Dict) -> bool:
    """
    更新订单字段
    
    Args:
        order_id: 订单ID
        updates: 要更新的字段字典，如 {'shares': '10.0000', 'status': 'done'}
    
    Returns:
        是否更新成功
    """
    if not updates:
        return False
    
    # 构建 SET 子句
    set_parts = []
    values = []
    for key, value in updates.items():
        set_parts.append(f"{key} = %s")
        values.append(value)
    
    values.append(order_id)
    sql = f"UPDATE orders SET {', '.join(set_parts)} WHERE order_id = %s"
    affected = execute_update(sql, tuple(values))
    return affected > 0


def order_exists(order_id: str) -> bool:
    """检查订单是否存在"""
    sql = "SELECT COUNT(*) as cnt FROM orders WHERE order_id = %s"
    result = execute_one(sql, (order_id,))
    return result and int(result.get('cnt', 0)) > 0


# ============================================================
# ledger - 生活账本
# ============================================================

LEDGER_FIELDNAMES = [
    'event_time', 'entry_type', 'amount', 'category_l1', 'category_l2',
    'account_from', 'account_to', 'discount', 'reimbursable', 'note'
]

VALID_ENTRY_TYPES = ['expense', 'income', 'transfer', 'refund']


def load_ledger() -> List[Dict]:
    """加载所有账本记录"""
    sql = """
        SELECT id,
               DATE_FORMAT(event_time, '%%Y-%%m-%%d %%H:%%i:%%s') as event_time,
               entry_type, amount, category_l1, category_l2,
               account_from, account_to, discount,
               CASE WHEN reimbursable = 1 THEN 'y' ELSE '' END as reimbursable,
               note
        FROM ledger
        ORDER BY event_time, id
    """
    return execute_query(sql)


def load_recent_ledger(n: int = 30, entry_type: str = None) -> List[Dict]:
    """
    加载最近 N 条账本记录
    
    Args:
        n: 返回条数
        entry_type: 可选，筛选类型
    
    Returns:
        账本记录列表（按时间倒序）
    """
    if entry_type:
        sql = """
            SELECT id,
                   DATE_FORMAT(event_time, '%%Y-%%m-%%d %%H:%%i:%%s') as event_time,
                   entry_type, amount, category_l1, category_l2,
                   account_from, account_to, discount,
                   CASE WHEN reimbursable = 1 THEN 'y' ELSE '' END as reimbursable,
                   note
            FROM ledger
            WHERE entry_type = %s
            ORDER BY event_time DESC, id DESC
            LIMIT %s
        """
        return execute_query(sql, (entry_type, n))
    else:
        sql = """
            SELECT id,
                   DATE_FORMAT(event_time, '%%Y-%%m-%%d %%H:%%i:%%s') as event_time,
                   entry_type, amount, category_l1, category_l2,
                   account_from, account_to, discount,
                   CASE WHEN reimbursable = 1 THEN 'y' ELSE '' END as reimbursable,
                   note
            FROM ledger
            ORDER BY event_time DESC, id DESC
            LIMIT %s
        """
        return execute_query(sql, (n,))


def append_ledger(record: Dict) -> None:
    """追加一条账本记录"""
    sql = """
        INSERT INTO ledger 
        (event_time, entry_type, amount, category_l1, category_l2,
         account_from, account_to, discount, reimbursable, note)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    # 处理 reimbursable 字段
    reimbursable = record.get('reimbursable', '')
    reimbursable_bool = 1 if reimbursable and reimbursable.lower() == 'y' else 0
    
    params = (
        record.get('event_time'),
        record.get('entry_type'),
        record.get('amount'),
        record.get('category_l1') or None,
        record.get('category_l2') or None,
        record.get('account_from') or None,
        record.get('account_to') or None,
        record.get('discount') or None,
        reimbursable_bool,
        record.get('note') or None
    )
    execute_insert(sql, params)


def update_ledger(record_id: int, record: Dict) -> bool:
    """更新账本记录"""
    sql = """
        UPDATE ledger SET
            event_time = %s, entry_type = %s, amount = %s,
            category_l1 = %s, category_l2 = %s,
            account_from = %s, account_to = %s,
            discount = %s, reimbursable = %s, note = %s
        WHERE id = %s
    """
    reimbursable = record.get('reimbursable', '')
    reimbursable_bool = 1 if reimbursable and str(reimbursable).lower() == 'y' else 0
    
    params = (
        record.get('event_time'),
        record.get('entry_type'),
        record.get('amount'),
        record.get('category_l1') or None,
        record.get('category_l2') or None,
        record.get('account_from') or None,
        record.get('account_to') or None,
        record.get('discount') or None,
        reimbursable_bool,
        record.get('note') or None,
        record_id
    )
    affected = execute_update(sql, params)
    return affected > 0


def delete_ledger(record_id: int) -> bool:
    """删除账本记录"""
    sql = "DELETE FROM ledger WHERE id = %s"
    affected = execute_update(sql, (record_id,))
    return affected > 0


# ============================================================
# 工具函数
# ============================================================

def generate_order_id(product_code: str) -> str:
    """生成订单号
    
    格式：YYYYMMDDHHMMSS_{product_code}_{seq}
    """
    timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
    prefix = f"{timestamp}_{product_code}_"
    
    max_seq = 0
    
    # 从 orders 查找
    orders = load_orders()
    for order in orders:
        order_id = order.get('order_id') or ''
        if order_id.startswith(prefix):
            try:
                seq = int(order_id.split('_')[-1])
                max_seq = max(max_seq, seq)
            except ValueError:
                pass
    
    # 从 transactions 查找
    transactions = load_transactions()
    for tx in transactions:
        order_id = tx.get('order_id') or ''
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
    
    quantize_str = '0.' + '0' * places
    rounded = d.quantize(Decimal(quantize_str), rounding=ROUND_HALF_UP)
    
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
