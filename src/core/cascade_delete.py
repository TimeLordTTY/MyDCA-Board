# -*- coding: utf-8 -*-
"""
级联删除辅助函数
处理订单、理财记录、记账记录之间的级联删除
"""

from typing import List, Optional
from decimal import Decimal
import logging

logger = logging.getLogger(__name__)


def find_related_ledger_by_order_id(order_id: str) -> List[dict]:
    """
    根据订单号查找关联的记账记录
    
    记账记录的备注中可能包含订单号，格式：
    - "产品名称 (订单号: {order_id})" - 买入扣款
    - "产品名称 赎回 (订单号: {order_id})" - 赎回确认
    
    Args:
        order_id: 订单号
    
    Returns:
        关联的记账记录列表
    """
    from data.data_store import load_ledger
    
    related_ledgers = []
    all_ledgers = load_ledger()
    
    for ledger in all_ledgers:
        note = ledger.get('note', '') or ''
        # 检查备注中是否包含订单号
        if f'(订单号: {order_id})' in note or f'(订单号:{order_id})' in note:
            related_ledgers.append(ledger)
    
    return related_ledgers


def find_related_transactions_by_order_id(order_id: str) -> List[dict]:
    """
    根据订单号查找关联的理财记录
    
    Args:
        order_id: 订单号
    
    Returns:
        关联的理财记录列表
    """
    from data.data_store import load_recent_transactions
    
    related_txs = []
    all_txs = load_recent_transactions(10000)  # 获取足够多的记录
    
    for tx in all_txs:
        if tx.get('order_id') == order_id:
            related_txs.append(tx)
    
    return related_txs


def find_related_order_by_transaction(tx: dict) -> Optional[dict]:
    """
    根据理财记录查找关联的订单
    
    Args:
        tx: 理财记录
    
    Returns:
        关联的订单，如果不存在则返回None
    """
    order_id = tx.get('order_id', '')
    if not order_id:
        return None
    
    from data.data_store import load_orders
    
    all_orders = load_orders()
    for order in all_orders:
        if order.get('order_id') == order_id:
            return order
    
    return None


def find_related_transactions_by_ledger(ledger: dict) -> List[dict]:
    """
    根据记账记录查找关联的理财记录
    
    通过备注中的订单号来查找
    
    Args:
        ledger: 记账记录
    
    Returns:
        关联的理财记录列表
    """
    note = ledger.get('note', '') or ''
    
    # 从备注中提取订单号
    order_id = None
    if '(订单号: ' in note:
        try:
            order_id = note.split('(订单号: ')[1].split(')')[0].strip()
        except:
            pass
    elif '(订单号:' in note:
        try:
            order_id = note.split('(订单号:')[1].split(')')[0].strip()
        except:
            pass
    
    if not order_id:
        return []
    
    return find_related_transactions_by_order_id(order_id)


def cascade_delete_order(order_id: str) -> dict:
    """
    级联删除订单及其关联的理财记录和记账记录
    
    Args:
        order_id: 订单号
    
    Returns:
        删除结果字典，包含删除的记录数量
    """
    from data.data_store import delete_order, delete_transaction, delete_ledger
    
    result = {
        'order_deleted': False,
        'transactions_deleted': 0,
        'ledgers_deleted': 0,
        'errors': []
    }
    
    try:
        # 1. 查找并删除关联的理财记录
        related_txs = find_related_transactions_by_order_id(order_id)
        for tx in related_txs:
            tx_id = tx.get('id')
            if tx_id:
                try:
                    if delete_transaction(tx_id):
                        result['transactions_deleted'] += 1
                    else:
                        result['errors'].append(f"删除理财记录失败: tx_id={tx_id}")
                except Exception as e:
                    result['errors'].append(f"删除理财记录异常: tx_id={tx_id}, error={e}")
        
        # 2. 查找并删除关联的记账记录
        related_ledgers = find_related_ledger_by_order_id(order_id)
        for ledger in related_ledgers:
            ledger_id = ledger.get('id')
            if ledger_id:
                try:
                    if delete_ledger(ledger_id):
                        result['ledgers_deleted'] += 1
                    else:
                        result['errors'].append(f"删除记账记录失败: ledger_id={ledger_id}")
                except Exception as e:
                    result['errors'].append(f"删除记账记录异常: ledger_id={ledger_id}, error={e}")
        
        # 3. 删除订单本身
        if delete_order(order_id):
            result['order_deleted'] = True
        else:
            result['errors'].append(f"删除订单失败: order_id={order_id}")
    
    except Exception as e:
        result['errors'].append(f"级联删除订单异常: {e}")
        logger.error(f"级联删除订单异常: order_id={order_id}", exc_info=True)
    
    return result


def cascade_delete_transaction(tx_id: int, tx: dict) -> dict:
    """
    级联删除理财记录及其关联的订单和记账记录
    
    注意：场外订单对应两笔理财录入（buy_debit 和 buy_confirm）
    赎回订单对应（redeem_request 和 sell_confirm）
    
    Args:
        tx_id: 理财记录ID
        tx: 理财记录数据
    
    Returns:
        删除结果字典
    """
    from data.data_store import delete_transaction, delete_order, delete_ledger
    
    result = {
        'transaction_deleted': False,
        'order_deleted': False,
        'related_transactions_deleted': 0,
        'ledgers_deleted': 0,
        'errors': []
    }
    
    try:
        order_id = tx.get('order_id', '')
        action = tx.get('action', '')
        
        # 1. 如果有关联订单，查找并删除订单
        if order_id:
            related_order = find_related_order_by_transaction(tx)
            if related_order:
                # 检查订单类型，决定需要删除哪些关联的理财记录
                order_type = related_order.get('order_type', '')
                
                if order_type == 'buy_debit':
                    # 场外买入订单：需要删除 buy_debit 和 buy_confirm
                    related_txs = find_related_transactions_by_order_id(order_id)
                    for related_tx in related_txs:
                        if related_tx.get('id') != tx_id:  # 不重复删除当前记录
                            related_tx_id = related_tx.get('id')
                            if related_tx_id:
                                try:
                                    if delete_transaction(related_tx_id):
                                        result['related_transactions_deleted'] += 1
                                except Exception as e:
                                    result['errors'].append(f"删除关联理财记录异常: tx_id={related_tx_id}, error={e}")
                
                elif order_type == 'redeem_request':
                    # 赎回订单：需要删除 redeem_request 和 sell_confirm
                    related_txs = find_related_transactions_by_order_id(order_id)
                    for related_tx in related_txs:
                        if related_tx.get('id') != tx_id:  # 不重复删除当前记录
                            related_tx_id = related_tx.get('id')
                            if related_tx_id:
                                try:
                                    if delete_transaction(related_tx_id):
                                        result['related_transactions_deleted'] += 1
                                except Exception as e:
                                    result['errors'].append(f"删除关联理财记录异常: tx_id={related_tx_id}, error={e}")
                
                # 删除订单
                try:
                    if delete_order(order_id):
                        result['order_deleted'] = True
                    else:
                        result['errors'].append(f"删除订单失败: order_id={order_id}")
                except Exception as e:
                    result['errors'].append(f"删除订单异常: order_id={order_id}, error={e}")
        
        # 2. 查找并删除关联的记账记录
        related_ledgers = find_related_ledger_by_order_id(order_id) if order_id else []
        for ledger in related_ledgers:
            ledger_id = ledger.get('id')
            if ledger_id:
                try:
                    if delete_ledger(ledger_id):
                        result['ledgers_deleted'] += 1
                    else:
                        result['errors'].append(f"删除记账记录失败: ledger_id={ledger_id}")
                except Exception as e:
                    result['errors'].append(f"删除记账记录异常: ledger_id={ledger_id}, error={e}")
        
        # 3. 删除理财记录本身
        if delete_transaction(tx_id):
            result['transaction_deleted'] = True
        else:
            result['errors'].append(f"删除理财记录失败: tx_id={tx_id}")
    
    except Exception as e:
        result['errors'].append(f"级联删除理财记录异常: {e}")
        logger.error(f"级联删除理财记录异常: tx_id={tx_id}", exc_info=True)
    
    return result


def cascade_delete_ledger(ledger_id: int, ledger: dict) -> dict:
    """
    级联删除记账记录及其关联的理财记录和订单
    
    Args:
        ledger_id: 记账记录ID
        ledger: 记账记录数据
    
    Returns:
        删除结果字典
    """
    from data.data_store import delete_ledger, delete_transaction, delete_order
    
    result = {
        'ledger_deleted': False,
        'transactions_deleted': 0,
        'order_deleted': False,
        'errors': []
    }
    
    try:
        # 1. 查找关联的理财记录
        related_txs = find_related_transactions_by_ledger(ledger)
        
        # 收集所有相关的订单ID
        order_ids = set()
        for tx in related_txs:
            order_id = tx.get('order_id', '')
            if order_id:
                order_ids.add(order_id)
        
        # 2. 删除关联的理财记录
        for tx in related_txs:
            tx_id = tx.get('id')
            if tx_id:
                try:
                    if delete_transaction(tx_id):
                        result['transactions_deleted'] += 1
                    else:
                        result['errors'].append(f"删除理财记录失败: tx_id={tx_id}")
                except Exception as e:
                    result['errors'].append(f"删除理财记录异常: tx_id={tx_id}, error={e}")
        
        # 3. 删除关联的订单
        for order_id in order_ids:
            try:
                if delete_order(order_id):
                    result['order_deleted'] = True
                else:
                    result['errors'].append(f"删除订单失败: order_id={order_id}")
            except Exception as e:
                result['errors'].append(f"删除订单异常: order_id={order_id}, error={e}")
        
        # 4. 删除记账记录本身
        if delete_ledger(ledger_id):
            result['ledger_deleted'] = True
        else:
            result['errors'].append(f"删除记账记录失败: ledger_id={ledger_id}")
    
    except Exception as e:
        result['errors'].append(f"级联删除记账记录异常: {e}")
        logger.error(f"级联删除记账记录异常: ledger_id={ledger_id}", exc_info=True)
    
    return result

