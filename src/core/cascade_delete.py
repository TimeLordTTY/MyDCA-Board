# -*- coding: utf-8 -*-
"""
级联删除辅助函数
处理订单、理财记录、记账记录之间的级联删除
包含份额和余额的自动恢复逻辑
"""

from typing import List, Optional, Dict
from decimal import Decimal
import logging

logger = logging.getLogger(__name__)


def _parse_decimal(value) -> Decimal:
    """安全解析 Decimal"""
    try:
        return Decimal(str(value)) if value else Decimal('0')
    except:
        return Decimal('0')


def _delete_trade_fills_for_transaction(tx: dict) -> int:
    """
    删除场内交易对应的 trade_fills 记录
    
    根据理财记录的信息（product_id, date, shares, amount, order_id）匹配并删除对应的 trade_fills 记录。
    
    匹配策略（按优先级）：
    1. 通过 order_id 提取时间进行精确匹配
    2. 通过日期+份额+金额完全匹配
    3. 通过日期+份额匹配（允许金额误差）
    
    Args:
        tx: 理财记录（buy_confirm 或 sell_confirm）
    
    Returns:
        删除的 trade_fills 记录数
    """
    action = tx.get('action', '')
    if action not in ('buy_confirm', 'sell_confirm'):
        return 0
    
    product_id = tx.get('product_id')
    product_code = tx.get('product_code', '')
    order_id = tx.get('order_id', '')
    
    # 尝试获取 product_id（如果为None，尝试通过product_code查找）
    product_id_int = None
    if product_id:
        try:
            product_id_int = int(product_id) if not isinstance(product_id, int) else product_id
        except (ValueError, TypeError):
            product_id_int = None
            logger.warning(f"无法转换 product_id: {product_id}")
    
    # 如果 product_id 为 None，尝试通过 product_code 查找
    if not product_id_int and product_code:
        try:
            from data.product_service import get_product_by_code
            product = get_product_by_code(product_code)
            if product and product.get('channel') == 'EXCHANGE':
                product_id_int = product.get('id')
                logger.info(f"通过 product_code 找到 product_id: {product_code} -> {product_id_int}")
        except Exception as e:
            logger.warning(f"通过 product_code 查找 product_id 失败: {product_code}, error={e}")
    
    if not product_id_int:
        return 0
    
    # 检查是否是场内产品
    from data.product_service import get_product_by_id
    product = get_product_by_id(product_id_int)
    if not product or product.get('channel') != 'EXCHANGE':
        return 0
    
    deleted_count = 0
    
    try:
        from data.db_connector import execute_update, execute_query
        from datetime import datetime
        
        # 将 action 转换为 side
        side = 'BUY' if action == 'buy_confirm' else 'SELL'
        
        # 获取交易信息
        tx_date = tx.get('date', '')
        tx_shares = tx.get('shares', '')
        tx_amount = tx.get('amount', '')  # 买入确认时amount可能为空
        
        # 处理日期格式
        if tx_date:
            if isinstance(tx_date, str):
                tx_date_str = tx_date
            else:
                tx_date_str = str(tx_date)
        else:
            tx_date_str = None
        
        # 方法1: 通过 order_id 提取时间进行精确匹配
        # order_id 格式: EXCHANGE_{product_code}_{YYYYMMDD_HHMMSS}
        trade_time_from_order = None
        if order_id and order_id.startswith('EXCHANGE_'):
            try:
                # 从 order_id 中提取时间部分
                parts = order_id.split('_')
                if len(parts) >= 3:
                    time_str = parts[-2] + '_' + parts[-1]  # YYYYMMDD_HHMMSS
                    trade_time_from_order = datetime.strptime(time_str, '%Y%m%d_%H%M%S')
                    logger.debug(f"从 order_id 提取时间: {order_id} -> {trade_time_from_order}")
            except Exception as e:
                logger.warning(f"无法从 order_id 提取时间: {order_id}, error={e}")
        
        # 必须同时有日期和份额才能匹配
        if not tx_date_str or not tx_shares:
            logger.error(f"缺少必要信息来匹配 trade_fills: date={tx_date_str}, shares={tx_shares}, product_id={product_id_int}, order_id={order_id}")
            return 0
        
        shares_decimal = Decimal(str(tx_shares))
        
        # 优先使用时间匹配（最精确）
        if trade_time_from_order:
            delete_sql = """
                DELETE FROM trade_fills
                WHERE product_id = %s
                  AND side = %s
                  AND trade_time = %s
                  AND source = 'MANUAL'
                LIMIT 1
            """
            affected = execute_update(delete_sql, (product_id_int, side, trade_time_from_order))
            if affected > 0:
                deleted_count = affected
                logger.info(f"删除场内成交记录（通过时间精确匹配）: product_id={product_id_int}, side={side}, time={trade_time_from_order}")
        
        # 如果时间匹配失败，使用日期+份额+金额匹配（完全匹配）
        if deleted_count == 0 and tx_amount:
            try:
                amount_decimal = Decimal(str(tx_amount))
                delete_sql = """
                    DELETE FROM trade_fills
                    WHERE product_id = %s
                      AND side = %s
                      AND trade_date = %s
                      AND ABS(qty - %s) < 0.0001
                      AND ABS(amount - %s) < 0.01
                      AND source = 'MANUAL'
                    LIMIT 1
                """
                affected = execute_update(delete_sql, (product_id_int, side, tx_date_str, float(shares_decimal), float(amount_decimal)))
                if affected > 0:
                    deleted_count = affected
                    logger.info(f"删除场内成交记录（通过日期+份额+金额完全匹配）: product_id={product_id_int}, side={side}, date={tx_date_str}, shares={tx_shares}, amount={tx_amount}")
            except Exception as e:
                logger.warning(f"金额匹配失败: {e}")
        
        # 如果还是失败，使用日期+份额匹配（允许金额误差）
        if deleted_count == 0:
            delete_sql = """
                DELETE FROM trade_fills
                WHERE product_id = %s
                  AND side = %s
                  AND trade_date = %s
                  AND ABS(qty - %s) < 0.0001
                  AND source = 'MANUAL'
                LIMIT 1
            """
            affected = execute_update(delete_sql, (product_id_int, side, tx_date_str, float(shares_decimal)))
            if affected > 0:
                deleted_count = affected
                logger.info(f"删除场内成交记录（通过日期+份额匹配）: product_id={product_id_int}, side={side}, date={tx_date_str}, shares={tx_shares}")
        
        # 如果还是匹配不到，记录详细错误信息用于调试
        if deleted_count == 0:
            # 查找所有可能的 trade_fills 记录，用于调试
            debug_sql = """
                SELECT id, trade_date, trade_time, qty, amount, side, source
                FROM trade_fills
                WHERE product_id = %s
                  AND side = %s
                  AND source = 'MANUAL'
            """
            debug_records = execute_query(debug_sql, (product_id_int, side))
            logger.error(f"未找到匹配的 trade_fills 记录: product_id={product_id_int}, side={side}, date={tx_date_str}, shares={tx_shares}, amount={tx_amount}, order_id={order_id}")
            if debug_records:
                logger.error(f"  数据库中存在的 trade_fills 记录: {debug_records}")
                # 尝试找出为什么匹配不上
                for rec in debug_records:
                    rec_date = str(rec.get('trade_date', ''))
                    rec_qty = float(rec.get('qty', 0))
                    rec_amount = float(rec.get('amount', 0))
                    rec_time = rec.get('trade_time')
                    logger.error(f"    记录ID {rec.get('id')}: date={rec_date} (期望: {tx_date_str}), qty={rec_qty} (期望: {float(shares_decimal)}), amount={rec_amount} (期望: {tx_amount}), time={rec_time} (期望: {trade_time_from_order})")
            else:
                logger.error(f"  数据库中不存在任何匹配的 trade_fills 记录")
    
    except Exception as e:
        logger.warning(f"删除场内成交记录失败: product_id={product_id_int}, error={e}", exc_info=True)
    
    return deleted_count


def _restore_shares_for_transaction(tx: dict) -> Dict[str, any]:
    """
    根据交易记录恢复账户份额
    
    - 删除买入确认(buy_confirm) → 减少份额
    - 删除赎回确认(sell_confirm) → 恢复份额
    
    **重要：对于组合账户赎回，需要正确恢复每个账户的份额**
    
    订单 note 格式：
    - 新格式（主账户+补充账户）：|redeem_from_account:主账户|redeem_fixed_amount:固定金额|redeem_supplement_accounts:补充账户1:份额1|补充账户2:份额2
    - 旧格式（多账户组合）：|redeem_from_accounts:账户1:份额1|账户2:份额2
    - 单账户：|redeem_from_account:账户ID
    
    Args:
        tx: 交易记录
    
    Returns:
        恢复结果字典，包含所有恢复的账户信息
    """
    from data.account_service import update_account_shares
    from data.data_store import load_orders, load_ledger
    
    result = {
        'shares_restored': False,
        'account_code': None,
        'shares': Decimal('0'),
        'operation': None,
        'error': None,
        'multi_accounts_restored': []  # 组合账户恢复记录
    }
    
    action = tx.get('action', '')
    total_shares = _parse_decimal(tx.get('shares', 0))
    order_id = tx.get('order_id', '')
    
    if total_shares <= 0:
        return result
    
    # 查找订单获取账户信息
    if not order_id:
        return result
    
    orders = load_orders()
    order = None
    for o in orders:
        if o.get('order_id') == order_id:
            order = o
            break
    
    if not order:
        return result
    
    order_note = order.get('note', '') or ''
    
    try:
        if action == 'buy_confirm':
            # 删除买入确认 → 减少份额（撤销买入）
            # 买入通常是单账户，从订单note或产品关联账户获取
            account_code = None
            
            # 尝试从订单note解析
            if '|redeem_from_account:' in order_note:
                try:
                    account_code = order_note.split('|redeem_from_account:')[1].split('|')[0].strip()
                except:
                    pass
            
            # 如果没有，从产品关联的账户查找
            if not account_code:
                product_code = tx.get('product_code', '')
                if product_code:
                    from data.product_service import get_product_by_code
                    from data.account_service import get_accounts
                    
                    product = get_product_by_code(product_code)
                    if product:
                        product_id = product.get('id')
                        accounts = get_accounts(is_active=True)
                        for acc in accounts:
                            if acc.get('product_id') == product_id and acc.get('account_type') == 'PRODUCT_SUB':
                                account_code = acc.get('account_code')
                                break
            
            if account_code:
                update_account_shares(account_code, total_shares, 'decrease')
                result['shares_restored'] = True
                result['account_code'] = account_code
                result['shares'] = total_shares
                result['operation'] = 'decrease'
                logger.info(f"删除买入确认，减少份额: account={account_code}, shares=-{total_shares}")
            else:
                result['error'] = '无法确定买入关联账户'
        
        elif action == 'sell_confirm':
            # 删除赎回确认 → 恢复份额（撤销赎回）
            # **关键：需要从记账记录中获取每个账户的实际赎回份额**
            
            # 方法1：从记账记录（ledger）中获取每个账户的份额
            # 记账记录的备注格式："产品名称 赎回持仓减少 (订单号: xxx, 份额: xxx, 净值: xxx)"
            all_ledgers = load_ledger()
            account_shares_map = {}  # {账户ID: 份额}
            
            for ledger in all_ledgers:
                note = ledger.get('note', '') or ''
                if f'(订单号: {order_id}' not in note and f'(订单号:{order_id}' not in note:
                    continue
                
                # 检查是否是赎回持仓减少记录
                if '赎回持仓减少' not in note:
                    continue
                
                account_from = ledger.get('account_from', '')
                if not account_from:
                    continue
                
                # 从备注中提取份额
                # 格式：... (订单号: xxx, 份额: 3835.090000, 净值: 1.042668)
                shares_in_ledger = Decimal('0')
                if '份额:' in note or '份额: ' in note:
                    try:
                        shares_str = note.split('份额:')[1].split(',')[0].strip()
                        shares_str = shares_str.lstrip(' ')
                        shares_in_ledger = _parse_decimal(shares_str)
                    except Exception as e:
                        logger.warning(f"从记账备注解析份额失败: {e}, note={note}")
                
                if shares_in_ledger > 0:
                    if account_from in account_shares_map:
                        account_shares_map[account_from] += shares_in_ledger
                    else:
                        account_shares_map[account_from] = shares_in_ledger
            
            # 如果从记账记录中找到了账户份额信息，使用它们
            if account_shares_map:
                for acc_code, acc_shares in account_shares_map.items():
                    try:
                        update_account_shares(acc_code, acc_shares, 'increase')
                        result['multi_accounts_restored'].append({
                            'account_code': acc_code,
                            'shares': acc_shares,
                            'operation': 'increase'
                        })
                        logger.info(f"删除赎回确认，恢复份额: account={acc_code}, shares=+{acc_shares}")
                    except Exception as e:
                        logger.error(f"恢复账户 {acc_code} 份额失败: {e}")
                
                if result['multi_accounts_restored']:
                    result['shares_restored'] = True
                    result['operation'] = 'increase'
                    # 设置主要账户信息（第一个）
                    first = result['multi_accounts_restored'][0]
                    result['account_code'] = first['account_code']
                    result['shares'] = first['shares']
            else:
                # 方法2：回退到从订单note解析（可能不准确）
                logger.warning(f"未能从记账记录获取份额信息，尝试从订单note解析: order_id={order_id}")
                
                # 解析新格式：主账户 + 补充账户
                if '|redeem_from_account:' in order_note:
                    main_account = None
                    fixed_amount = None
                    supplement_accounts = []
                    
                    try:
                        main_account = order_note.split('|redeem_from_account:')[1].split('|')[0].strip()
                    except:
                        pass
                    
                    if '|redeem_fixed_amount:' in order_note:
                        try:
                            fixed_amount = _parse_decimal(order_note.split('|redeem_fixed_amount:')[1].split('|')[0].strip())
                        except:
                            pass
                    
                    if '|redeem_supplement_accounts:' in order_note:
                        try:
                            accounts_str = order_note.split('|redeem_supplement_accounts:')[1]
                            # 可能包含 |fee_override: 等后续内容
                            if '|fee_override:' in accounts_str:
                                accounts_str = accounts_str.split('|fee_override:')[0]
                            
                            account_pairs = accounts_str.split('|')
                            for pair in account_pairs:
                                if ':' in pair:
                                    acc_id, shares_str = pair.split(':', 1)
                                    acc_shares = _parse_decimal(shares_str)
                                    if acc_shares > 0:
                                        supplement_accounts.append({
                                            'account_id': acc_id.strip(),
                                            'shares': acc_shares
                                        })
                        except Exception as e:
                            logger.warning(f"解析补充账户失败: {e}")
                    
                    # 恢复补充账户份额
                    supplement_total = Decimal('0')
                    for supp in supplement_accounts:
                        try:
                            update_account_shares(supp['account_id'], supp['shares'], 'increase')
                            supplement_total += supp['shares']
                            result['multi_accounts_restored'].append({
                                'account_code': supp['account_id'],
                                'shares': supp['shares'],
                                'operation': 'increase'
                            })
                            logger.info(f"删除赎回确认，恢复补充账户份额: account={supp['account_id']}, shares=+{supp['shares']}")
                        except Exception as e:
                            logger.error(f"恢复补充账户 {supp['account_id']} 份额失败: {e}")
                    
                    # 恢复主账户份额（总份额 - 补充账户份额）
                    if main_account:
                        main_shares = total_shares - supplement_total
                        if main_shares > 0:
                            try:
                                update_account_shares(main_account, main_shares, 'increase')
                                result['multi_accounts_restored'].append({
                                    'account_code': main_account,
                                    'shares': main_shares,
                                    'operation': 'increase'
                                })
                                logger.info(f"删除赎回确认，恢复主账户份额: account={main_account}, shares=+{main_shares}")
                            except Exception as e:
                                logger.error(f"恢复主账户 {main_account} 份额失败: {e}")
                    
                    if result['multi_accounts_restored']:
                        result['shares_restored'] = True
                        result['operation'] = 'increase'
                        first = result['multi_accounts_restored'][0]
                        result['account_code'] = first['account_code']
                        result['shares'] = first['shares']
                
                # 解析旧格式：多账户组合赎回
                elif '|redeem_from_accounts:' in order_note:
                    try:
                        accounts_str = order_note.split('|redeem_from_accounts:')[1]
                        if '|fee_override:' in accounts_str:
                            accounts_str = accounts_str.split('|fee_override:')[0]
                        if '|redeem_account:' in accounts_str:
                            accounts_str = accounts_str.split('|redeem_account:')[0]
                        
                        account_pairs = accounts_str.split('|')
                        for pair in account_pairs:
                            if ':' in pair:
                                acc_id, shares_str = pair.split(':', 1)
                                acc_shares = _parse_decimal(shares_str)
                                if acc_shares > 0:
                                    try:
                                        update_account_shares(acc_id.strip(), acc_shares, 'increase')
                                        result['multi_accounts_restored'].append({
                                            'account_code': acc_id.strip(),
                                            'shares': acc_shares,
                                            'operation': 'increase'
                                        })
                                        logger.info(f"删除赎回确认，恢复组合账户份额: account={acc_id.strip()}, shares=+{acc_shares}")
                                    except Exception as e:
                                        logger.error(f"恢复账户 {acc_id} 份额失败: {e}")
                        
                        if result['multi_accounts_restored']:
                            result['shares_restored'] = True
                            result['operation'] = 'increase'
                            first = result['multi_accounts_restored'][0]
                            result['account_code'] = first['account_code']
                            result['shares'] = first['shares']
                    except Exception as e:
                        logger.error(f"解析旧格式组合赎回失败: {e}")
                        result['error'] = f'解析组合赎回失败: {e}'
                
                # 单账户赎回（最后回退）
                else:
                    account_code = None
                    product_code = tx.get('product_code', '')
                    if product_code:
                        from data.product_service import get_product_by_code
                        from data.account_service import get_accounts
                        
                        product = get_product_by_code(product_code)
                        if product:
                            product_id = product.get('id')
                            accounts = get_accounts(is_active=True)
                            for acc in accounts:
                                if acc.get('product_id') == product_id and acc.get('account_type') == 'PRODUCT_SUB':
                                    account_code = acc.get('account_code')
                                    break
                    
                    if account_code:
                        update_account_shares(account_code, total_shares, 'increase')
                        result['shares_restored'] = True
                        result['account_code'] = account_code
                        result['shares'] = total_shares
                        result['operation'] = 'increase'
                        logger.info(f"删除赎回确认，恢复单账户份额: account={account_code}, shares=+{total_shares}")
                    else:
                        result['error'] = '无法确定赎回关联账户'
    
    except Exception as e:
        result['error'] = str(e)
        logger.error(f"恢复份额失败: {e}", exc_info=True)
    
    return result


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
        # 检查备注中是否包含订单号（支持多种格式）
        # 格式1: (订单号: {order_id})
        # 格式2: (订单号:{order_id})
        # 格式3: (订单号: {order_id}, ...) - 场内买入确认格式
        # 格式4: 订单号: {order_id}
        if (f'(订单号: {order_id})' in note or 
            f'(订单号:{order_id})' in note or
            f'(订单号: {order_id},' in note or
            f'(订单号:{order_id},' in note or
            f'订单号: {order_id}' in note or
            f'订单号:{order_id}' in note):
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
    同时自动恢复账户份额
    
    Args:
        order_id: 订单号
    
    Returns:
        删除结果字典，包含删除的记录数量和份额恢复信息
    """
    from data.data_store import delete_order, delete_transaction, delete_ledger
    
    result = {
        'order_deleted': False,
        'transactions_deleted': 0,
        'ledgers_deleted': 0,
        'shares_restored': [],  # 份额恢复记录
        'errors': []
    }
    
    try:
        # 1. 查找关联的理财记录
        related_txs = find_related_transactions_by_order_id(order_id)
        
        # 2. 在删除前先恢复份额（针对确认类记录）
        for tx in related_txs:
            action = tx.get('action', '')
            if action in ('buy_confirm', 'sell_confirm'):
                restore_result = _restore_shares_for_transaction(tx)
                if restore_result['shares_restored']:
                    result['shares_restored'].append(restore_result)
                elif restore_result['error']:
                    result['errors'].append(f"恢复份额失败: {restore_result['error']}")
        
        # 3. 删除关联的理财记录
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
        
        # 4. 查找并删除关联的记账记录
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
        
        # 5. 删除订单本身
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
    同时自动恢复账户份额
    
    注意：场外订单对应两笔理财录入（buy_debit 和 buy_confirm）
    赎回订单对应（redeem_request 和 sell_confirm）
    转托管对应（transfer_out 和 transfer_in）
    
    Args:
        tx_id: 理财记录ID
        tx: 理财记录数据
    
    Returns:
        删除结果字典
    """
    from data.data_store import delete_transaction, delete_order, delete_ledger
    
    # 检查是否是转托管记录
    action = tx.get('action', '')
    if action in ('transfer_out', 'transfer_in'):
        # 调用转托管专用删除函数
        return cascade_delete_custody_transfer(tx_id, tx)
    
    result = {
        'transaction_deleted': False,
        'order_deleted': False,
        'related_transactions_deleted': 0,
        'ledgers_deleted': 0,
        'trade_fills_deleted': 0,  # 场内成交记录删除数
        'shares_restored': [],  # 份额恢复记录
        'errors': []
    }
    
    try:
        order_id = tx.get('order_id', '')
        action = tx.get('action', '')
        
        # 0. 先恢复当前记录的份额（如果是确认类记录）
        if action in ('buy_confirm', 'sell_confirm'):
            restore_result = _restore_shares_for_transaction(tx)
            if restore_result['shares_restored']:
                result['shares_restored'].append(restore_result)
            elif restore_result['error']:
                result['errors'].append(f"恢复份额失败: {restore_result['error']}")
        
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
                            related_action = related_tx.get('action', '')
                            
                            # 恢复关联记录的份额
                            if related_action in ('buy_confirm', 'sell_confirm'):
                                restore_result = _restore_shares_for_transaction(related_tx)
                                if restore_result['shares_restored']:
                                    result['shares_restored'].append(restore_result)
                            
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
                            related_action = related_tx.get('action', '')
                            
                            # 恢复关联记录的份额
                            if related_action in ('buy_confirm', 'sell_confirm'):
                                restore_result = _restore_shares_for_transaction(related_tx)
                                if restore_result['shares_restored']:
                                    result['shares_restored'].append(restore_result)
                            
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
        
        # 2. 如果是场内买入/卖出确认，删除对应的 trade_fills 记录
        if action in ('buy_confirm', 'sell_confirm'):
            trade_fills_deleted = _delete_trade_fills_for_transaction(tx)
            result['trade_fills_deleted'] = trade_fills_deleted
        
        # 3. 查找并删除关联的记账记录
        related_ledgers = find_related_ledger_by_order_id(order_id) if order_id else []
        affected_accounts = set()  # 记录受影响的账户
        for ledger in related_ledgers:
            ledger_id = ledger.get('id')
            if ledger_id:
                try:
                    # 记录受影响的账户（用于后续重新计算余额）
                    account_from = ledger.get('account_from', '')
                    account_to = ledger.get('account_to', '')
                    if account_from:
                        affected_accounts.add(account_from)
                    if account_to:
                        affected_accounts.add(account_to)
                    
                    if delete_ledger(ledger_id):
                        result['ledgers_deleted'] += 1
                    else:
                        result['errors'].append(f"删除记账记录失败: ledger_id={ledger_id}")
                except Exception as e:
                    result['errors'].append(f"删除记账记录异常: ledger_id={ledger_id}, error={e}")
        
        # 重新计算受影响的账户余额
        if affected_accounts:
            from core.ledger_service import calc_account_balance
            from data.account_service import update_account_balance
            for account_code in affected_accounts:
                try:
                    new_balance = calc_account_balance(account_code)
                    update_account_balance(account_code, new_balance)
                    logger.info(f"已重新计算账户余额: {account_code} = {new_balance}")
                except Exception as e:
                    result['errors'].append(f"重新计算账户余额失败: account={account_code}, error={e}")
                    logger.warning(f"重新计算账户余额失败: account={account_code}, error={e}")
        
        # 4. 删除理财记录本身
        if delete_transaction(tx_id):
            result['transaction_deleted'] = True
        else:
            result['errors'].append(f"删除理财记录失败: tx_id={tx_id}")
    
    except Exception as e:
        result['errors'].append(f"级联删除理财记录异常: {e}")
        logger.error(f"级联删除理财记录异常: tx_id={tx_id}", exc_info=True)
    
    return result


def find_transfer_pair_ledger(ledger: dict) -> Optional[dict]:
    """
    查找转账记录的配对记录
    
    转账会生成两条记录：
    - expense: category_l1='转账', category_l2='转出', account_from=源账户
    - income: category_l1='转账', category_l2='转入', account_to=目标账户
    
    这两条记录有相同的时间和金额
    
    Args:
        ledger: 记账记录
    
    Returns:
        配对的记账记录，如果不存在则返回None
    """
    from data.data_store import load_ledger
    
    category_l1 = ledger.get('category_l1', '')
    if category_l1 != '转账':
        return None
    
    category_l2 = ledger.get('category_l2', '')
    event_time = ledger.get('event_time', '')
    amount = ledger.get('amount', '')
    ledger_id = ledger.get('id')
    entry_type = ledger.get('entry_type', '')
    
    # 确定配对记录的类型
    if entry_type == 'expense' or category_l2 == '转出':
        pair_entry_type = 'income'
        pair_category_l2 = '转入'
    elif entry_type == 'income' or category_l2 == '转入':
        pair_entry_type = 'expense'
        pair_category_l2 = '转出'
    else:
        return None
    
    # 在所有记账记录中查找配对记录
    all_ledgers = load_ledger()
    
    for l in all_ledgers:
        if l.get('id') == ledger_id:
            continue  # 跳过自己
        
        # 匹配条件：相同时间、相同金额、转账类型、配对的分类
        if (l.get('category_l1') == '转账' and 
            l.get('event_time') == event_time and
            str(l.get('amount', '')) == str(amount) and
            (l.get('entry_type') == pair_entry_type or l.get('category_l2') == pair_category_l2)):
            return l
    
    return None


def update_transfer_pair_ledger(ledger_id: int, ledger: dict, updated_record: dict) -> dict:
    """
    更新转账记录时同时更新配对记录
    
    更新逻辑：
    - 时间：两条记录同步更新为新时间
    - 金额：两条记录同步更新为新金额
    - 备注：两条记录同步更新为新备注
    - 账户：根据记录类型更新对应的账户
      - expense（转出）: 更新 account_from
      - income（转入）: 更新 account_to
    
    Args:
        ledger_id: 主记录ID
        ledger: 原记录数据
        updated_record: 更新后的记录数据
    
    Returns:
        更新结果字典
    """
    from data.data_store import update_ledger
    
    result = {
        'main_updated': False,
        'pair_updated': False,
        'pair_id': None,
        'errors': []
    }
    
    try:
        # 1. 更新主记录
        if update_ledger(ledger_id, updated_record):
            result['main_updated'] = True
        else:
            result['errors'].append(f"更新主记录失败: ledger_id={ledger_id}")
            return result
        
        # 2. 检查是否是转账记录
        category_l1 = ledger.get('category_l1', '')
        if category_l1 != '转账':
            return result  # 非转账记录，只更新主记录即可
        
        # 3. 查找配对记录
        pair_ledger = find_transfer_pair_ledger(ledger)
        if not pair_ledger:
            return result  # 没有配对记录
        
        pair_id = pair_ledger.get('id')
        result['pair_id'] = pair_id
        
        # 4. 构建配对记录的更新数据
        pair_entry_type = pair_ledger.get('entry_type', '')
        pair_category_l2 = pair_ledger.get('category_l2', '')
        
        pair_updated_record = {
            'event_time': updated_record.get('event_time'),
            'entry_type': pair_entry_type,
            'amount': updated_record.get('amount'),
            'category_l1': '转账',
            'category_l2': pair_category_l2,
            'note': updated_record.get('note', '')
        }
        
        # 根据类型设置账户
        if pair_entry_type == 'expense' or pair_category_l2 == '转出':
            # 配对记录是转出，从主记录获取转出账户
            # 如果主记录是转入，则主记录的 account_to 对应配对记录的转出来源
            # 但实际上转账的 expense 的 account_from 应该由用户指定
            # 这里保持配对记录的原账户不变，只更新时间、金额、备注
            pair_updated_record['account_from'] = pair_ledger.get('account_from', '')
            pair_updated_record['account_to'] = pair_ledger.get('account_to', '')
        else:
            # 配对记录是转入
            pair_updated_record['account_from'] = pair_ledger.get('account_from', '')
            pair_updated_record['account_to'] = pair_ledger.get('account_to', '')
        
        # 5. 更新配对记录
        if update_ledger(pair_id, pair_updated_record):
            result['pair_updated'] = True
            logger.info(f"已同步更新转账配对记录: id={pair_id}")
        else:
            result['errors'].append(f"更新配对记录失败: ledger_id={pair_id}")
    
    except Exception as e:
        result['errors'].append(f"更新转账记录异常: {e}")
        logger.error(f"更新转账记录异常: ledger_id={ledger_id}", exc_info=True)
    
    return result


def cascade_delete_ledger(ledger_id: int, ledger: dict) -> dict:
    """
    级联删除记账记录及其关联的理财记录和订单
    同时自动恢复账户份额
    
    对于转账记录，会自动删除配对的另一条记录
    
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
        'transfer_pair_deleted': False,  # 新增：是否删除了转账配对记录
        'trade_fills_deleted': 0,  # 场内成交记录删除数
        'shares_restored': [],  # 份额恢复记录
        'errors': []
    }
    
    try:
        # 0. 检查是否是转账记录，如果是则查找配对记录
        transfer_pair = find_transfer_pair_ledger(ledger)
        
        # 1. 查找关联的理财记录
        related_txs = find_related_transactions_by_ledger(ledger)
        
        # 收集所有相关的订单ID
        order_ids = set()
        for tx in related_txs:
            order_id = tx.get('order_id', '')
            if order_id:
                order_ids.add(order_id)
        
        # 2. 先恢复份额，删除场内成交记录，再删除关联的理财记录
        for tx in related_txs:
            tx_id = tx.get('id')
            action = tx.get('action', '')
            
            # 恢复份额
            if action in ('buy_confirm', 'sell_confirm'):
                restore_result = _restore_shares_for_transaction(tx)
                if restore_result['shares_restored']:
                    result['shares_restored'].append(restore_result)
                elif restore_result['error']:
                    result['errors'].append(f"恢复份额失败: {restore_result['error']}")
                
                # 删除场内成交记录（如果是场内产品）
                trade_fills_deleted = _delete_trade_fills_for_transaction(tx)
                result['trade_fills_deleted'] += trade_fills_deleted
            
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
        
        # 5. 删除转账配对记录
        if transfer_pair:
            pair_id = transfer_pair.get('id')
            if pair_id:
                try:
                    if delete_ledger(pair_id):
                        result['transfer_pair_deleted'] = True
                        logger.info(f"已删除转账配对记录: id={pair_id}")
                    else:
                        result['errors'].append(f"删除转账配对记录失败: ledger_id={pair_id}")
                except Exception as e:
                    result['errors'].append(f"删除转账配对记录异常: ledger_id={pair_id}, error={e}")
        
        # 6. 重新计算受影响的账户余额
        affected_accounts = set()
        # 收集当前记账记录影响的账户
        account_from = ledger.get('account_from', '')
        account_to = ledger.get('account_to', '')
        if account_from:
            affected_accounts.add(account_from)
        if account_to:
            affected_accounts.add(account_to)
        # 如果删除了转账配对记录，也要收集配对记录影响的账户
        if transfer_pair:
            pair_account_from = transfer_pair.get('account_from', '')
            pair_account_to = transfer_pair.get('account_to', '')
            if pair_account_from:
                affected_accounts.add(pair_account_from)
            if pair_account_to:
                affected_accounts.add(pair_account_to)
        
        # 重新计算所有受影响的账户余额
        if affected_accounts:
            from core.ledger_service import calc_account_balance
            from data.account_service import update_account_balance
            for account_code in affected_accounts:
                try:
                    new_balance = calc_account_balance(account_code)
                    update_account_balance(account_code, new_balance)
                    logger.info(f"已重新计算账户余额: {account_code} = {new_balance}")
                except Exception as e:
                    result['errors'].append(f"重新计算账户余额失败: account={account_code}, error={e}")
                    logger.warning(f"重新计算账户余额失败: account={account_code}, error={e}")
    
    except Exception as e:
        result['errors'].append(f"级联删除记账记录异常: {e}")
        logger.error(f"级联删除记账记录异常: ledger_id={ledger_id}", exc_info=True)
    
    return result


def restore_shares_for_order_reset(order_id: str) -> Dict[str, any]:
    """
    订单重置时恢复份额
    
    当订单从 done 状态重置为 pending 时，需要恢复被确认操作影响的份额：
    - 买入订单重置：减少份额（撤销买入）
    - 赎回订单重置：恢复份额（撤销赎回）
    
    Args:
        order_id: 订单号
    
    Returns:
        恢复结果字典
    """
    result = {
        'success': False,
        'shares_restored': [],
        'errors': []
    }
    
    try:
        # 查找订单关联的确认记录
        related_txs = find_related_transactions_by_order_id(order_id)
        
        for tx in related_txs:
            action = tx.get('action', '')
            if action in ('buy_confirm', 'sell_confirm'):
                restore_result = _restore_shares_for_transaction(tx)
                if restore_result['shares_restored']:
                    result['shares_restored'].append(restore_result)
                    result['success'] = True
                elif restore_result['error']:
                    result['errors'].append(restore_result['error'])
    
    except Exception as e:
        result['errors'].append(str(e))
        logger.error(f"订单重置恢复份额异常: order_id={order_id}", exc_info=True)
    
    return result


# ============================================================
# 联动更新函数
# ============================================================

def cascade_update_transaction(tx_id: int, tx: dict, updated_record: dict) -> dict:
    """
    联动更新理财记录及其关联的记账记录
    
    更新逻辑：
    - 理财记录更新：同步更新关联的记账记录（金额、日期、备注）
    - 记账记录通过备注中的订单号关联
    - 如果是转托管记录，调用专门的转托管更新函数
    
    Args:
        tx_id: 理财记录ID
        tx: 原理财记录数据
        updated_record: 更新后的记录数据
    
    Returns:
        更新结果字典
    """
    from data.data_store import update_transaction, update_ledger, load_ledger
    
    # 检查是否是转托管记录
    action = tx.get('action', '')
    if action in ('transfer_out', 'transfer_in'):
        # 调用转托管专用更新函数
        return cascade_update_custody_transfer(tx_id, tx, updated_record)
    
    result = {
        'transaction_updated': False,
        'ledgers_updated': 0,
        'errors': []
    }
    
    try:
        # 1. 更新理财记录本身
        if update_transaction(tx_id, updated_record):
            result['transaction_updated'] = True
        else:
            result['errors'].append(f"更新理财记录失败: tx_id={tx_id}")
            return result
        
        # 2. 查找并更新关联的记账记录
        order_id = tx.get('order_id', '')
        if not order_id:
            return result  # 没有订单号，无法关联记账记录
        
        action = tx.get('action', '')
        all_ledgers = load_ledger()
        
        # 根据订单号查找关联的记账记录
        for ledger in all_ledgers:
            note = ledger.get('note', '') or ''
            if f'(订单号: {order_id})' not in note and f'(订单号:{order_id})' not in note:
                continue
            
            ledger_id = ledger.get('id')
            if not ledger_id:
                continue
            
            # 构建记账记录更新数据
            ledger_update = {}
            
            # 更新日期（如果提供了新日期）
            new_date = updated_record.get('date')
            if new_date:
                # 记账记录的时间格式：YYYY-MM-DD HH:MM:SS
                old_event_time = ledger.get('event_time', '')
                if old_event_time and len(old_event_time) > 10:
                    # 保留原时间部分
                    time_part = old_event_time[10:]
                    ledger_update['event_time'] = f"{new_date}{time_part}"
                else:
                    ledger_update['event_time'] = f"{new_date} 12:00:00"
            
            # 根据记账类型和理财记录类型决定是否更新金额
            ledger_entry_type = ledger.get('entry_type', '')
            ledger_category_l2 = ledger.get('category_l2', '')
            
            # buy_debit 对应的记账是 expense（买入扣款）
            if action == 'buy_debit' and ledger_entry_type == 'expense':
                new_amount = updated_record.get('amount')
                if new_amount:
                    ledger_update['amount'] = str(new_amount)
            
            # sell_confirm 对应的记账有两种：
            # - income（赎回确认，资金到账）
            # - expense（赎回持仓减少）
            elif action == 'sell_confirm':
                new_amount = updated_record.get('amount')
                if new_amount:
                    if ledger_entry_type == 'income' and ledger_category_l2 == '赎回确认':
                        # 资金到账金额
                        ledger_update['amount'] = str(new_amount)
                    # 赎回持仓减少的金额需要根据份额和净值重新计算，暂不自动更新
            
            # 更新备注（保留订单号部分）
            new_note = updated_record.get('note')
            if new_note is not None:
                # 从原备注中提取订单号部分
                if '(订单号:' in note:
                    order_part = note[note.find('(订单号:'):]
                    # 重新构建备注
                    if new_note:
                        ledger_update['note'] = f"{new_note} {order_part}"
                    else:
                        ledger_update['note'] = order_part
            
            # 执行更新
            if ledger_update:
                # 保留其他字段
                full_update = {
                    'event_time': ledger_update.get('event_time', ledger.get('event_time')),
                    'entry_type': ledger.get('entry_type'),
                    'amount': ledger_update.get('amount', str(ledger.get('amount', '0'))),
                    'category_l1': ledger.get('category_l1'),
                    'category_l2': ledger.get('category_l2'),
                    'account_from': ledger.get('account_from'),
                    'account_to': ledger.get('account_to'),
                    'note': ledger_update.get('note', ledger.get('note'))
                }
                
                try:
                    if update_ledger(ledger_id, full_update):
                        result['ledgers_updated'] += 1
                        logger.info(f"已更新关联记账记录: ledger_id={ledger_id}")
                except Exception as e:
                    result['errors'].append(f"更新记账记录失败: ledger_id={ledger_id}, error={e}")
    
    except Exception as e:
        result['errors'].append(f"联动更新理财记录异常: {e}")
        logger.error(f"联动更新理财记录异常: tx_id={tx_id}", exc_info=True)
    
    return result


def cascade_update_order(order_id: str, order: dict, updated_fields: dict) -> dict:
    """
    联动更新订单及其关联的理财记录和记账记录
    
    更新逻辑：
    - 订单更新：同步更新关联的理财记录和记账记录
    - 可更新字段：日期、金额、份额、净值、备注等
    
    Args:
        order_id: 订单号
        order: 原订单数据
        updated_fields: 需要更新的字段
    
    Returns:
        更新结果字典
    """
    from data.data_store import update_order, update_transaction, update_ledger, load_ledger
    
    result = {
        'order_updated': False,
        'transactions_updated': 0,
        'ledgers_updated': 0,
        'errors': []
    }
    
    try:
        # 1. 更新订单本身
        if update_order(order_id, updated_fields):
            result['order_updated'] = True
        else:
            result['errors'].append(f"更新订单失败: order_id={order_id}")
            return result
        
        # 2. 查找并更新关联的理财记录
        related_txs = find_related_transactions_by_order_id(order_id)
        
        for tx in related_txs:
            tx_id = tx.get('id')
            if not tx_id:
                continue
            
            # 构建理财记录更新数据
            tx_update = {}
            
            # 更新日期
            if 'nav_date' in updated_fields:
                tx_update['date'] = updated_fields['nav_date']
                tx_update['nav_date'] = updated_fields['nav_date']
            
            # 更新金额（买入订单）
            if 'amount' in updated_fields:
                tx_update['amount'] = str(updated_fields['amount'])
            
            # 更新份额（赎回订单）
            if 'shares' in updated_fields:
                tx_update['shares'] = str(updated_fields['shares'])
            
            # 更新净值
            if 'nav' in updated_fields:
                tx_update['nav'] = str(updated_fields['nav'])
            
            # 更新备注
            if 'note' in updated_fields:
                tx_update['note'] = updated_fields['note']
            
            # 执行更新
            if tx_update:
                try:
                    if update_transaction(tx_id, tx_update):
                        result['transactions_updated'] += 1
                        logger.info(f"已更新关联理财记录: tx_id={tx_id}")
                except Exception as e:
                    result['errors'].append(f"更新理财记录失败: tx_id={tx_id}, error={e}")
        
        # 3. 查找并更新关联的记账记录
        related_ledgers = find_related_ledger_by_order_id(order_id)
        
        for ledger in related_ledgers:
            ledger_id = ledger.get('id')
            if not ledger_id:
                continue
            
            # 构建记账记录更新数据
            ledger_update = {}
            
            # 更新日期
            if 'nav_date' in updated_fields or 'confirm_date' in updated_fields:
                new_date = updated_fields.get('confirm_date') or updated_fields.get('nav_date')
                if new_date:
                    old_event_time = ledger.get('event_time', '')
                    if old_event_time and len(old_event_time) > 10:
                        time_part = old_event_time[10:]
                        ledger_update['event_time'] = f"{new_date}{time_part}"
                    else:
                        ledger_update['event_time'] = f"{new_date} 12:00:00"
            
            # 更新金额（只更新买入扣款和赎回确认的金额）
            ledger_entry_type = ledger.get('entry_type', '')
            ledger_category_l2 = ledger.get('category_l2', '')
            
            if 'amount' in updated_fields:
                order_type = order.get('order_type', '')
                if order_type == 'buy_debit' and ledger_entry_type == 'expense':
                    ledger_update['amount'] = str(updated_fields['amount'])
                elif order_type == 'redeem_request' and ledger_entry_type == 'income' and ledger_category_l2 == '赎回确认':
                    ledger_update['amount'] = str(updated_fields['amount'])
            
            # 执行更新
            if ledger_update:
                # 保留其他字段
                full_update = {
                    'event_time': ledger_update.get('event_time', ledger.get('event_time')),
                    'entry_type': ledger.get('entry_type'),
                    'amount': ledger_update.get('amount', str(ledger.get('amount', '0'))),
                    'category_l1': ledger.get('category_l1'),
                    'category_l2': ledger.get('category_l2'),
                    'account_from': ledger.get('account_from'),
                    'account_to': ledger.get('account_to'),
                    'note': ledger.get('note')
                }
                
                try:
                    if update_ledger(ledger_id, full_update):
                        result['ledgers_updated'] += 1
                        logger.info(f"已更新关联记账记录: ledger_id={ledger_id}")
                except Exception as e:
                    result['errors'].append(f"更新记账记录失败: ledger_id={ledger_id}, error={e}")
    
    except Exception as e:
        result['errors'].append(f"联动更新订单异常: {e}")
        logger.error(f"联动更新订单异常: order_id={order_id}", exc_info=True)
    
    return result


# ============================================================
# 转托管联动删除/更新函数
# ============================================================

def cascade_delete_custody_transfer(tx_id: int, tx: dict) -> dict:
    """
    级联删除转托管记录及其关联记录
    
    转托管会产生两笔记录：
    - transfer_out: 场外转出
    - transfer_in: 场内转入
    
    删除逻辑：
    1. 通过 order_id 找到配对记录
    2. 恢复账户份额
    3. 删除 trade_fills 记录
    4. 删除关联的记账记录
    5. 删除两条 transactions 记录
    
    Args:
        tx_id: 理财记录ID
        tx: 理财记录数据
    
    Returns:
        删除结果字典
    """
    from data.data_store import delete_transaction, delete_ledger, load_ledger
    from core.custody_transfer_service import find_custody_transfer_pair
    
    result = {
        'transaction_deleted': False,
        'pair_transaction_deleted': False,
        'trade_fills_deleted': 0,
        'ledgers_deleted': 0,
        'shares_restored': [],
        'errors': []
    }
    
    try:
        action = tx.get('action', '')
        order_id = tx.get('order_id', '')
        
        if action not in ('transfer_out', 'transfer_in'):
            result['errors'].append(f"不是转托管记录: action={action}")
            return result
        
        if not order_id:
            result['errors'].append(f"转托管记录缺少 order_id")
            return result
        
        # 1. 查找配对记录
        transfer_out, transfer_in = find_custody_transfer_pair(order_id)
        
        if not transfer_out or not transfer_in:
            result['errors'].append(f"找不到转托管配对记录: order_id={order_id}")
            return result
        
        # 确定当前记录和配对记录
        current_record = transfer_out if action == 'transfer_out' else transfer_in
        pair_record = transfer_in if action == 'transfer_out' else transfer_out
        
        # 2. 恢复账户份额
        transfer_shares = _parse_decimal(current_record.get('shares', 0))
        
        # 恢复场外账户份额（增加）
        if action == 'transfer_out':
            # 删除转出，恢复场外份额
            otc_product_code = transfer_out.get('product_code', '')
            if otc_product_code:
                from data.product_service import get_product_by_code
                from data.account_service import get_accounts, update_account_shares
                
                otc_product = get_product_by_code(otc_product_code, channel='OTC')
                if otc_product:
                    otc_accounts = get_accounts(is_active=True)
                    for acc in otc_accounts:
                        if acc.get('product_id') == otc_product.get('id') and acc.get('account_type') == 'PRODUCT_SUB':
                            update_account_shares(acc.get('account_code'), transfer_shares, 'increase')
                            result['shares_restored'].append({
                                'account_code': acc.get('account_code'),
                                'shares': transfer_shares,
                                'operation': 'increase'
                            })
        
        # 恢复场内账户份额（减少）
        if action == 'transfer_in':
            # 删除转入，恢复场内份额
            exchange_product_code = transfer_in.get('product_code', '')
            if exchange_product_code:
                from data.product_service import get_product_by_code
                from data.account_service import get_accounts, update_account_shares
                
                exchange_product = get_product_by_code(exchange_product_code, channel='EXCHANGE')
                if exchange_product:
                    exchange_accounts = get_accounts(is_active=True)
                    for acc in exchange_accounts:
                        if acc.get('product_id') == exchange_product.get('id') and acc.get('account_type') == 'PRODUCT_SUB':
                            update_account_shares(acc.get('account_code'), transfer_shares, 'decrease')
                            result['shares_restored'].append({
                                'account_code': acc.get('account_code'),
                                'shares': transfer_shares,
                                'operation': 'decrease'
                            })
        
        # 3. 删除 trade_fills 记录（如果是场内记录）
        if action == 'transfer_in':
            exchange_product_code = transfer_in.get('product_code', '')
            if exchange_product_code:
                from data.product_service import get_product_by_code
                from data.db_connector import execute_update
                
                exchange_product = get_product_by_code(exchange_product_code, channel='EXCHANGE')
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
                    affected = execute_update(delete_sql, (
                        exchange_product_id,
                        transfer_in.get('date', ''),
                        float(transfer_shares)
                    ))
                    result['trade_fills_deleted'] = affected
        
        # 4. 删除关联的记账记录
        all_ledgers = load_ledger()
        for ledger in all_ledgers:
            note = ledger.get('note', '') or ''
            if (f'(订单号: {order_id})' in note or f'(订单号:{order_id})' in note) and '转托管' in note:
                ledger_id = ledger.get('id')
                if ledger_id:
                    try:
                        if delete_ledger(ledger_id):
                            result['ledgers_deleted'] += 1
                    except Exception as e:
                        result['errors'].append(f"删除记账记录失败: ledger_id={ledger_id}, error={e}")
        
        # 5. 删除配对记录
        pair_id = pair_record.get('id')
        if pair_id:
            try:
                if delete_transaction(pair_id):
                    result['pair_transaction_deleted'] = True
            except Exception as e:
                result['errors'].append(f"删除配对记录失败: tx_id={pair_id}, error={e}")
        
        # 6. 删除当前记录
        if delete_transaction(tx_id):
            result['transaction_deleted'] = True
        else:
            result['errors'].append(f"删除理财记录失败: tx_id={tx_id}")
    
    except Exception as e:
        result['errors'].append(f"级联删除转托管记录异常: {e}")
        logger.error(f"级联删除转托管记录异常: tx_id={tx_id}", exc_info=True)
    
    return result


def cascade_update_custody_transfer(tx_id: int, tx: dict, updated_record: dict) -> dict:
    """
    联动更新转托管记录及其关联记录
    
    更新逻辑：
    1. 通过 order_id 找到配对记录
    2. 联动更新两条 transactions 记录（金额、价格、份额、费用、日期、备注）
    3. 更新 trade_fills 记录（如果份额或价格变化）
    4. 更新关联的记账记录
    5. 重新计算账户份额（如果份额变化）
    
    Args:
        tx_id: 理财记录ID
        tx: 原理财记录数据
        updated_record: 更新后的记录数据
    
    Returns:
        更新结果字典
    """
    from data.data_store import update_transaction, update_ledger, load_ledger, parse_decimal
    from core.custody_transfer_service import find_custody_transfer_pair, update_custody_transfer
    from decimal import Decimal, ROUND_HALF_UP
    
    result = {
        'transaction_updated': False,
        'pair_transaction_updated': False,
        'trade_fills_updated': False,
        'ledgers_updated': 0,
        'errors': []
    }
    
    try:
        action = tx.get('action', '')
        order_id = tx.get('order_id', '')
        
        if action not in ('transfer_out', 'transfer_in'):
            result['errors'].append(f"不是转托管记录: action={action}")
            return result
        
        if not order_id:
            result['errors'].append(f"转托管记录缺少 order_id")
            return result
        
        # 1. 查找配对记录
        transfer_out, transfer_in = find_custody_transfer_pair(order_id)
        
        if not transfer_out or not transfer_in:
            result['errors'].append(f"找不到转托管配对记录: order_id={order_id}")
            return result
        
        # 2. 提取更新字段
        new_shares = updated_record.get('shares')
        new_price = updated_record.get('nav')  # 价格存储在 nav 字段
        new_fee = updated_record.get('fee')
        new_date = updated_record.get('date')
        new_note = updated_record.get('note')
        
        # 转换类型
        if new_shares:
            new_shares = parse_decimal(new_shares) if isinstance(new_shares, str) else Decimal(str(new_shares))
        if new_price:
            new_price = parse_decimal(new_price) if isinstance(new_price, str) else Decimal(str(new_price))
        if new_fee:
            new_fee = parse_decimal(new_fee) if isinstance(new_fee, str) else Decimal(str(new_fee))
        if new_date and isinstance(new_date, str):
            from datetime import datetime
            new_date = datetime.strptime(new_date, '%Y-%m-%d').date()
        
        # 3. 使用 custody_transfer_service 的更新函数
        update_result = update_custody_transfer(
            order_id=order_id,
            transfer_shares=new_shares,
            price=new_price,
            fee=new_fee,
            transfer_date=new_date,
            note=new_note
        )
        
        if update_result.get('success'):
            result['transaction_updated'] = True
            result['pair_transaction_updated'] = True
            result['trade_fills_updated'] = True
        else:
            result['errors'].append(update_result.get('message', '更新失败'))
        
        # 4. 更新关联的记账记录
        all_ledgers = load_ledger()
        for ledger in all_ledgers:
            note = ledger.get('note', '') or ''
            if (f'(订单号: {order_id})' in note or f'(订单号:{order_id})' in note) and '转托管' in note:
                ledger_id = ledger.get('id')
                if not ledger_id:
                    continue
                
                # 构建记账记录更新数据
                ledger_update = {}
                
                # 更新日期
                if new_date:
                    old_event_time = ledger.get('event_time', '')
                    if old_event_time and len(old_event_time) > 10:
                        time_part = old_event_time[10:]
                        ledger_update['event_time'] = f"{new_date.strftime('%Y-%m-%d')}{time_part}"
                    else:
                        ledger_update['event_time'] = f"{new_date.strftime('%Y-%m-%d')} 10:00:00"
                
                # 更新金额（如果份额或价格变化）
                if new_shares and new_price:
                    new_amount = (new_price * new_shares).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
                    ledger_update['amount'] = str(new_amount)
                
                # 更新备注
                if new_note is not None:
                    # 从原备注中提取订单号部分
                    if '(订单号:' in note:
                        order_part = note[note.find('(订单号:'):]
                        # 重新构建备注
                        if new_note:
                            ledger_update['note'] = f"{new_note} {order_part}"
                        else:
                            ledger_update['note'] = order_part
                
                # 执行更新
                if ledger_update:
                    full_update = {
                        'event_time': ledger_update.get('event_time', ledger.get('event_time')),
                        'entry_type': ledger.get('entry_type'),
                        'amount': ledger_update.get('amount', str(ledger.get('amount', '0'))),
                        'category_l1': ledger.get('category_l1'),
                        'category_l2': ledger.get('category_l2'),
                        'account_from': ledger.get('account_from'),
                        'account_to': ledger.get('account_to'),
                        'note': ledger_update.get('note', ledger.get('note'))
                    }
                    
                    try:
                        if update_ledger(ledger_id, full_update):
                            result['ledgers_updated'] += 1
                            logger.info(f"已更新关联记账记录: ledger_id={ledger_id}")
                    except Exception as e:
                        result['errors'].append(f"更新记账记录失败: ledger_id={ledger_id}, error={e}")
    
    except Exception as e:
        result['errors'].append(f"联动更新转托管记录异常: {e}")
        logger.error(f"联动更新转托管记录异常: tx_id={tx_id}", exc_info=True)
    
    return result
