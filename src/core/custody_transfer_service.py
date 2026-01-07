#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
场外基金转托管到场内（LOF）服务

功能：
- 完成转托管操作：场外份额转入场内
- 自动生成关联的理财记录（transfer_out 和 transfer_in）
- 自动更新持仓和账户份额
- 支持联动更新和删除
"""

import logging
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal, ROUND_HALF_UP
from typing import List, Dict, Optional, Tuple

from data.db_connector import execute_insert, execute_query, execute_update, execute_one
from data.data_store import append_transaction, format_decimal, parse_decimal
from data.product_service import get_product_by_code
from data.account_service import get_accounts, get_account_shares, update_account_shares
from core.ledger_service import add_expense, add_income
from core.exchange_trade_service import persist_trade_record

logger = logging.getLogger(__name__)


@dataclass
class FundCustodyTransfer:
    id: Optional[int]
    product_code: str
    from_channel: str
    to_channel: str
    transfer_date: str
    transfer_shares: Decimal
    note: str = ""


def _generate_custody_order_id(product_code: str, transfer_date: date, transfer_time: str = "10:00:00") -> str:
    """
    生成转托管关联的 order_id
    
    格式：CUSTODY_{product_code}_{YYYYMMDD_HHMMSS}
    """
    from datetime import datetime
    
    # 将日期和时间组合
    datetime_str = f"{transfer_date.strftime('%Y%m%d')}_{transfer_time.replace(':', '')}"
    order_id = f"CUSTODY_{product_code}_{datetime_str}"
    
    # 检查是否已存在，如果存在则添加序号
    from data.data_store import load_transactions
    transactions = load_transactions()
    existing_ids = {tx.get('order_id') or '' for tx in transactions if (tx.get('order_id') or '').startswith(order_id)}
    
    if existing_ids:
        # 查找最大序号
        max_seq = 0
        for existing_id in existing_ids:
            if '_' in existing_id:
                parts = existing_id.split('_')
                if len(parts) >= 4:
                    try:
                        seq = int(parts[-1])
                        max_seq = max(max_seq, seq)
                    except ValueError:
                        pass
        order_id = f"{order_id}_{max_seq + 1:03d}"
    
    return order_id


def add_fund_custody_transfer(
    product_code: str,
    transfer_shares: Decimal,
    price: Decimal,
    transfer_date: date,
    fee: Decimal = Decimal('0'),
    transfer_time: str = "10:00:00",
    note: str = "",
) -> Dict:
    """
    完成转托管操作（场外 -> 场内）
    
    功能：
    1. 计算金额 = price × shares
    2. 生成关联 order_id
    3. 写入场外 transfer_out 记录（份额减少）
    4. 写入场内 transfer_in 记录（份额增加）
    5. 写入场内 trade_fills 记录（BUY 类型）
    6. 更新场外账户份额（减少）
    7. 更新场内账户份额（增加）
    8. 在 ledger 中记录两笔记录（转出和转入）
    
    Args:
        product_code: 产品代码（场外和场内使用相同代码）
        transfer_shares: 转托管份额
        price: 成交价格（场内价格）
        transfer_date: 转托管日期
        fee: 费用（默认0）
        transfer_time: 转托管时间（默认 10:00:00）
        note: 备注
    
    Returns:
        包含操作结果的字典：
        {
            'success': bool,
            'order_id': str,
            'transfer_out_id': int,  # 场外记录ID
            'transfer_in_id': int,  # 场内记录ID
            'trade_fill_id': int,   # 场内成交记录ID
            'message': str
        }
    """
    try:
        # 1. 验证产品存在（场外和场内版本）
        otc_product = get_product_by_code(product_code, channel='OTC')
        exchange_product = get_product_by_code(product_code, channel='EXCHANGE')
        
        if not otc_product:
            raise ValueError(f"场外产品不存在: {product_code}")
        if not exchange_product:
            raise ValueError(f"场内产品不存在: {product_code}")
        
        otc_product_id = otc_product.get('id')
        exchange_product_id = exchange_product.get('id')
        
        # 2. 计算金额
        amount = (price * transfer_shares).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
        
        # 3. 生成关联 order_id
        order_id = _generate_custody_order_id(product_code, transfer_date, transfer_time)
        
        # 4. 构建完整时间
        event_datetime = f"{transfer_date.strftime('%Y-%m-%d')} {transfer_time}"
        
        # 5. 写入场外 transfer_out 记录（份额减少）
        transfer_out_record = {
            'date': transfer_date.strftime('%Y-%m-%d'),
            'product_id': otc_product_id,
            'product_code': product_code,
            'action': 'transfer_out',
            'amount': '',  # 转出不记录金额
            'shares': format_decimal(transfer_shares, 6),
            'fee': format_decimal(fee, 2) if fee > 0 else '',
            'nav': '',  # 场外不记录净值
            'nav_date': '',
            'order_id': order_id,
            'note': note or f'转托管转出（场外→场内）',
            'created_at': event_datetime
        }
        append_transaction(transfer_out_record)
        
        # 获取刚插入的记录ID（通过查询）
        from data.data_store import load_recent_transactions
        recent_txs = load_recent_transactions(10)
        transfer_out_id = None
        for tx in recent_txs:
            if tx.get('order_id') == order_id and tx.get('action') == 'transfer_out':
                transfer_out_id = tx.get('id')
                break
        
        # 6. 写入场内 transfer_in 记录（份额增加）
        transfer_in_record = {
            'date': transfer_date.strftime('%Y-%m-%d'),
            'product_id': exchange_product_id,
            'product_code': product_code,
            'action': 'transfer_in',
            'amount': format_decimal(amount, 2),
            'shares': format_decimal(transfer_shares, 6),
            'fee': format_decimal(fee, 2) if fee > 0 else '',
            'nav': str(price),  # 使用成交价格作为净值
            'nav_date': transfer_date.strftime('%Y-%m-%d'),
            'order_id': order_id,
            'note': note or f'转托管转入（场外→场内）',
            'created_at': event_datetime
        }
        append_transaction(transfer_in_record)
        
        # 获取刚插入的记录ID
        recent_txs = load_recent_transactions(10)
        transfer_in_id = None
        for tx in recent_txs:
            if tx.get('order_id') == order_id and tx.get('action') == 'transfer_in':
                transfer_in_id = tx.get('id')
                break
        
        # 7. 写入场内 trade_fills 记录（BUY 类型）
        trade_datetime = datetime.strptime(event_datetime, '%Y-%m-%d %H:%M:%S')
        trade_fill_id = persist_trade_record(
            product_id=exchange_product_id,
            account_id=None,  # 转托管不涉及账户资金变动
            trade_date=transfer_date,
            trade_time=trade_datetime,
            trade_type='BUY',
            amount=amount,
            shares=transfer_shares,
            price=price,
            fee=fee,
            tax=Decimal('0'),
            other_fee=Decimal('0'),
            remark=f'转托管转入 {note or ""}'
        )
        
        # 8. 更新场外账户份额（减少）
        # 查找场外产品关联的账户
        otc_accounts = get_accounts(is_active=True)
        otc_account = None
        for acc in otc_accounts:
            if acc.get('product_id') == otc_product_id and acc.get('account_type') == 'PRODUCT_SUB':
                otc_account = acc
                break
        
        if otc_account:
            otc_account_code = otc_account.get('account_code')
            # 减少场外账户份额
            update_account_shares(otc_account_code, transfer_shares, 'decrease')
            logger.info(f"场外账户份额减少: {otc_account_code}, -{transfer_shares}")
            
            # 在 ledger 中记录场外转出（持仓减少）
            add_expense(
                account_from=otc_account_code,
                amount=amount,
                category_l1="理财投资",
                category_l2="转托管转出",
                event_time=event_datetime,
                note=f"{otc_product.get('product_name', product_code)} 转托管转出 (订单号: {order_id}, 份额: {transfer_shares:.6f}, 价格: {price})"
            )
        
        # 9. 更新场内账户份额（增加）
        # 查找场内产品关联的账户
        exchange_accounts = get_accounts(is_active=True)
        exchange_account = None
        for acc in exchange_accounts:
            if acc.get('product_id') == exchange_product_id and acc.get('account_type') == 'PRODUCT_SUB':
                exchange_account = acc
                break
        
        if exchange_account:
            exchange_account_code = exchange_account.get('account_code')
            # 增加场内账户份额
            update_account_shares(exchange_account_code, transfer_shares, 'increase')
            logger.info(f"场内账户份额增加: {exchange_account_code}, +{transfer_shares}")
            
            # 在 ledger 中记录场内转入（持仓增加）
            add_income(
                account_to=exchange_account_code,
                amount=amount,
                category_l1="理财投资",
                category_l2="转托管转入",
                event_time=event_datetime,
                note=f"{exchange_product.get('product_name', product_code)} 转托管转入 (订单号: {order_id}, 份额: {transfer_shares:.6f}, 价格: {price})"
            )
        
        # 10. 记录到 fund_custody_transfer 表（历史记录）
        sql = """
            INSERT INTO fund_custody_transfer
            (product_code, from_channel, to_channel, transfer_date, transfer_shares, note)
            VALUES (%s, %s, %s, %s, %s, %s)
        """
        params = (
            product_code,
            'OTC',
            'EXCHANGE',
            transfer_date.strftime("%Y-%m-%d"),
            str(transfer_shares),
            note or None,
        )
        execute_insert(sql, params)
        
        return {
            'success': True,
            'order_id': order_id,
            'transfer_out_id': transfer_out_id,
            'transfer_in_id': transfer_in_id,
            'trade_fill_id': trade_fill_id,
            'message': f'转托管成功: {transfer_shares:.4f} 份 @ {price}'
        }
        
    except Exception as e:
        logger.error(f"转托管失败: {e}", exc_info=True)
        return {
            'success': False,
            'order_id': '',
            'transfer_out_id': None,
            'transfer_in_id': None,
            'trade_fill_id': None,
            'message': f'转托管失败: {str(e)}'
        }


def find_custody_transfer_pair(order_id: str) -> Tuple[Optional[Dict], Optional[Dict]]:
    """
    查找转托管配对的两笔记录
    
    Args:
        order_id: 转托管订单号
    
    Returns:
        (transfer_out_record, transfer_in_record)
    """
    from data.data_store import load_transactions
    
    transactions = load_transactions()
    transfer_out = None
    transfer_in = None
    
    for tx in transactions:
        if tx.get('order_id') == order_id:
            action = tx.get('action', '')
            if action == 'transfer_out':
                transfer_out = tx
            elif action == 'transfer_in':
                transfer_in = tx
    
    return transfer_out, transfer_in


def update_custody_transfer(
    order_id: str,
    transfer_shares: Optional[Decimal] = None,
    price: Optional[Decimal] = None,
    fee: Optional[Decimal] = None,
    transfer_date: Optional[date] = None,
    note: Optional[str] = None
) -> Dict:
    """
    更新转托管记录（联动更新配对的两笔记录）
    
    Args:
        order_id: 转托管订单号
        transfer_shares: 新份额（可选）
        price: 新价格（可选）
        fee: 新费用（可选）
        transfer_date: 新日期（可选）
        note: 新备注（可选）
    
    Returns:
        更新结果字典
    """
    try:
        # 1. 查找配对记录
        transfer_out, transfer_in = find_custody_transfer_pair(order_id)
        
        if not transfer_out or not transfer_in:
            raise ValueError(f"找不到转托管配对记录: order_id={order_id}")
        
        # 2. 确定要更新的字段
        # 如果提供了新值，使用新值；否则使用原值
        new_shares = transfer_shares if transfer_shares is not None else parse_decimal(transfer_out.get('shares', 0))
        new_price = price if price is not None else parse_decimal(transfer_in.get('nav', 0))
        new_fee = fee if fee is not None else parse_decimal(transfer_out.get('fee', 0))
        new_date = transfer_date if transfer_date is not None else datetime.strptime(transfer_out.get('date', ''), '%Y-%m-%d').date()
        new_note = note if note is not None else transfer_out.get('note', '')
        
        # 3. 重新计算金额
        new_amount = (new_price * new_shares).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
        
        # 4. 更新场外 transfer_out 记录
        from data.data_store import update_transaction
        transfer_out_update = {
            'date': new_date.strftime('%Y-%m-%d'),
            'product_code': transfer_out.get('product_code'),
            'action': 'transfer_out',
            'amount': '',
            'shares': format_decimal(new_shares, 6),
            'fee': format_decimal(new_fee, 2) if new_fee > 0 else '',
            'nav': '',
            'nav_date': '',
            'note': new_note
        }
        update_transaction(transfer_out.get('id'), transfer_out_update)
        
        # 5. 更新场内 transfer_in 记录
        transfer_in_update = {
            'date': new_date.strftime('%Y-%m-%d'),
            'product_code': transfer_in.get('product_code'),
            'action': 'transfer_in',
            'amount': format_decimal(new_amount, 2),
            'shares': format_decimal(new_shares, 6),
            'fee': format_decimal(new_fee, 2) if new_fee > 0 else '',
            'nav': str(new_price),
            'nav_date': new_date.strftime('%Y-%m-%d'),
            'note': new_note
        }
        update_transaction(transfer_in.get('id'), transfer_in_update)
        
        # 6. 更新 trade_fills 记录（如果份额或价格变化）
        if transfer_shares is not None or price is not None:
            # 查找 trade_fills 记录
            from data.db_connector import execute_query, execute_update
            from data.product_service import get_product_by_code
            
            exchange_product = get_product_by_code(transfer_in.get('product_code'), channel='EXCHANGE')
            if exchange_product:
                exchange_product_id = exchange_product.get('id')
                
                # 查找匹配的 trade_fills 记录
                sql = """
                    SELECT id FROM trade_fills
                    WHERE product_id = %s
                      AND side = 'BUY'
                      AND trade_date = %s
                      AND ABS(qty - %s) < 0.0001
                      AND source = 'MANUAL'
                    LIMIT 1
                """
                old_shares = parse_decimal(transfer_in.get('shares', 0))
                result = execute_query(sql, (exchange_product_id, transfer_out.get('date'), float(old_shares)))
                
                if result:
                    trade_fill_id = result[0].get('id')
                    update_sql = """
                        UPDATE trade_fills SET
                            qty = %s,
                            price = %s,
                            amount = %s,
                            fee = %s,
                            trade_date = %s
                        WHERE id = %s
                    """
                    execute_update(update_sql, (
                        float(new_shares),
                        float(new_price),
                        float(new_amount),
                        float(new_fee),
                        new_date,
                        trade_fill_id
                    ))
        
        # 7. 重新计算账户份额（如果份额变化）
        if transfer_shares is not None:
            old_shares = parse_decimal(transfer_out.get('shares', 0))
            shares_diff = new_shares - old_shares
            
            if shares_diff != 0:
                # 更新场外账户份额
                otc_product = get_product_by_code(transfer_out.get('product_code'), channel='OTC')
                if otc_product:
                    otc_accounts = get_accounts(is_active=True)
                    for acc in otc_accounts:
                        if acc.get('product_id') == otc_product.get('id') and acc.get('account_type') == 'PRODUCT_SUB':
                            update_account_shares(acc.get('account_code'), shares_diff, 'decrease' if shares_diff < 0 else 'increase')
                
                # 更新场内账户份额
                exchange_product = get_product_by_code(transfer_in.get('product_code'), channel='EXCHANGE')
                if exchange_product:
                    exchange_accounts = get_accounts(is_active=True)
                    for acc in exchange_accounts:
                        if acc.get('product_id') == exchange_product.get('id') and acc.get('account_type') == 'PRODUCT_SUB':
                            update_account_shares(acc.get('account_code'), abs(shares_diff), 'increase' if shares_diff > 0 else 'decrease')
        
        return {
            'success': True,
            'message': f'转托管记录更新成功'
        }
        
    except Exception as e:
        logger.error(f"更新转托管记录失败: {e}", exc_info=True)
        return {
            'success': False,
            'message': f'更新失败: {str(e)}'
        }


def delete_custody_transfer(order_id: str) -> Dict:
    """
    删除转托管记录（联动删除配对的两笔记录）
    
    Args:
        order_id: 转托管订单号
    
    Returns:
        删除结果字典
    """
    try:
        # 1. 查找配对记录
        transfer_out, transfer_in = find_custody_transfer_pair(order_id)
        
        if not transfer_out or not transfer_in:
            raise ValueError(f"找不到转托管配对记录: order_id={order_id}")
        
        # 2. 恢复账户份额
        transfer_shares = parse_decimal(transfer_out.get('shares', 0))
        
        # 恢复场外账户份额（增加）
        otc_product = get_product_by_code(transfer_out.get('product_code'), channel='OTC')
        if otc_product:
            otc_accounts = get_accounts(is_active=True)
            for acc in otc_accounts:
                if acc.get('product_id') == otc_product.get('id') and acc.get('account_type') == 'PRODUCT_SUB':
                    update_account_shares(acc.get('account_code'), transfer_shares, 'increase')
        
        # 恢复场内账户份额（减少）
        exchange_product = get_product_by_code(transfer_in.get('product_code'), channel='EXCHANGE')
        if exchange_product:
            exchange_accounts = get_accounts(is_active=True)
            for acc in exchange_accounts:
                if acc.get('product_id') == exchange_product.get('id') and acc.get('account_type') == 'PRODUCT_SUB':
                    update_account_shares(acc.get('account_code'), transfer_shares, 'decrease')
        
        # 3. 删除 trade_fills 记录
        from data.db_connector import execute_update
        if exchange_product:
            exchange_product_id = exchange_product.get('id')
            delete_sql = """
                DELETE FROM trade_fills
                WHERE product_id = %s
                  AND side = 'BUY'
                  AND trade_date = %s
                  AND ABS(qty - %s) < 0.0001
                  AND source = 'MANUAL'
                LIMIT 1
            """
            execute_update(delete_sql, (exchange_product_id, transfer_out.get('date'), float(transfer_shares)))
        
        # 4. 删除 transactions 记录
        from data.data_store import delete_transaction
        delete_transaction(transfer_out.get('id'))
        delete_transaction(transfer_in.get('id'))
        
        # 5. 删除关联的 ledger 记录（通过订单号查找）
        from data.data_store import load_ledger, delete_ledger
        ledgers = load_ledger()
        for ledger in ledgers:
            note = ledger.get('note', '') or ''
            if f'(订单号: {order_id})' in note or f'(订单号:{order_id})' in note:
                if '转托管' in note:
                    delete_ledger(ledger.get('id'))
        
        return {
            'success': True,
            'message': f'转托管记录删除成功'
        }
        
    except Exception as e:
        logger.error(f"删除转托管记录失败: {e}", exc_info=True)
        return {
            'success': False,
            'message': f'删除失败: {str(e)}'
        }


def list_fund_custody_transfers(product_code: Optional[str] = None) -> List[Dict]:
    """
    按产品代码查询转托管记录（按日期正序）。
    """
    if product_code:
        sql = """
            SELECT id, product_code, from_channel, to_channel,
                   DATE_FORMAT(transfer_date, '%%Y-%%m-%%d') AS transfer_date,
                   transfer_shares, note, created_at, updated_at
            FROM fund_custody_transfer
            WHERE product_code = %s
            ORDER BY transfer_date, id
        """
        return execute_query(sql, (product_code,))
    else:
        sql = """
            SELECT id, product_code, from_channel, to_channel,
                   DATE_FORMAT(transfer_date, '%%Y-%%m-%%d') AS transfer_date,
                   transfer_shares, note, created_at, updated_at
            FROM fund_custody_transfer
            ORDER BY product_code, transfer_date, id
        """
        return execute_query(sql)
