"""待买入池服务"""
import logging
from decimal import Decimal
from typing import List, Dict, Optional

from data.db_connector import execute_query, execute_one, execute_update, execute_insert

logger = logging.getLogger(__name__)


def add_pending_amount(product_id: int, from_account_id: int, amount, reason: str = None, last_change_reason: str = None) -> bool:
    """
    增加待买入金额（累加）
    
    Args:
        product_id: 产品ID
        from_account_id: 来源账户ID
        amount: 待买入金额（可以是 Decimal 或 float）
        reason: 扣留原因（兼容字段）
        last_change_reason: 最后变更原因（如NON_TRADE_DAY, PREMIUM_BRAKE, MIN_TRADE_LIMIT）
    
    Returns:
        是否成功
    """
    try:
        from datetime import datetime
        
        # 确保 amount 是 Decimal 类型
        if not isinstance(amount, Decimal):
            amount = Decimal(str(amount))
        
        # 先查询是否存在
        existing = get_pending_pool(product_id, from_account_id)
        
        # 使用last_change_reason或reason
        change_reason = last_change_reason or reason
        
        if existing:
            # 更新：累加金额
            new_amount = Decimal(str(existing['pending_amount'])) + amount
            # 检查是否有新字段
            sql = """
                UPDATE pending_buy_pool
                SET pending_amount = %s,
                    reason = COALESCE(%s, reason),
                    last_change_reason = %s,
                    last_change_time = %s,
                    version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE product_id = %s AND from_account_id = %s
            """
            execute_update(sql, (str(new_amount), reason, change_reason, datetime.now(), product_id, from_account_id))
        else:
            # 插入新记录
            sql = """
                INSERT INTO pending_buy_pool (
                    product_id, from_account_id, pending_amount, reason,
                    last_change_reason, last_change_time, version
                ) VALUES (
                    %s, %s, %s, %s, %s, %s, 1
                )
            """
            execute_insert(sql, (product_id, from_account_id, str(amount), reason, change_reason, datetime.now()))
        
        logger.info(f"增加待买入金额: product_id={product_id}, account_id={from_account_id}, amount={amount}, reason={change_reason}")
        return True
        
    except Exception as e:
        logger.error(f"增加待买入金额失败: {e}", exc_info=True)
        # 如果字段不存在，降级到旧逻辑
        try:
            # 确保 amount 是 Decimal 类型
            if not isinstance(amount, Decimal):
                amount = Decimal(str(amount))
            
            existing = get_pending_pool(product_id, from_account_id)
            if existing:
                new_amount = Decimal(str(existing['pending_amount'])) + amount
                sql = """
                    UPDATE pending_buy_pool
                    SET pending_amount = %s,
                        reason = COALESCE(%s, reason),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE product_id = %s AND from_account_id = %s
                """
                execute_update(sql, (str(new_amount), reason, product_id, from_account_id))
            else:
                sql = """
                    INSERT INTO pending_buy_pool (
                        product_id, from_account_id, pending_amount, reason
                    ) VALUES (
                        %s, %s, %s, %s
                    )
                """
                execute_insert(sql, (product_id, from_account_id, str(amount), reason))
            return True
        except Exception as e2:
            logger.error(f"降级逻辑也失败: {e2}", exc_info=True)
            return False


def reduce_pending_amount(product_id: int, from_account_id: int, amount: Decimal, reason: str = None) -> bool:
    """
    减少待买入金额（用于真实成交扣减）
    
    Args:
        product_id: 产品ID
        from_account_id: 来源账户ID
        amount: 减少金额
        reason: 扣减原因（如"真实买入成交"）
    
    Returns:
        是否成功
    """
    try:
        from datetime import datetime
        
        existing = get_pending_pool(product_id, from_account_id)
        if not existing:
            logger.warning(f"待买入池不存在: product_id={product_id}, account_id={from_account_id}")
            return False
        
        current = Decimal(str(existing['pending_amount']))
        new_amount = max(Decimal('0'), current - amount)
        
        # 检查是否有新字段
        sql = """
            UPDATE pending_buy_pool
            SET pending_amount = %s,
                last_change_reason = %s,
                last_change_time = %s,
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE product_id = %s AND from_account_id = %s
        """
        change_reason = reason or "真实买入成交"
        execute_update(sql, (str(new_amount), change_reason, datetime.now(), product_id, from_account_id))
        
        logger.info(f"减少待买入金额: product_id={product_id}, account_id={from_account_id}, "
                   f"amount={amount}, new_amount={new_amount}, reason={change_reason}")
        return True
        
    except Exception as e:
        logger.error(f"减少待买入金额失败: {e}", exc_info=True)
        # 降级到旧逻辑
        try:
            existing = get_pending_pool(product_id, from_account_id)
            if not existing:
                return False
            current = Decimal(str(existing['pending_amount']))
            new_amount = max(Decimal('0'), current - amount)
            sql = """
                UPDATE pending_buy_pool
                SET pending_amount = %s,
                    updated_at = CURRENT_TIMESTAMP
                WHERE product_id = %s AND from_account_id = %s
            """
            execute_update(sql, (str(new_amount), product_id, from_account_id))
            return True
        except Exception as e2:
            logger.error(f"降级逻辑也失败: {e2}", exc_info=True)
            return False


def reduce_pending_amount_by_transaction(product_id: int, amount: Decimal, transaction_id: Optional[int] = None) -> bool:
    """
    按成交扣减等待池（优先扣减等待池，不足部分从现金扣除）
    
    Args:
        product_id: 产品ID
        amount: 成交金额（含费前或费后，按现有口径）
        transaction_id: 交易ID（可选，用于关联）
    
    Returns:
        是否成功
    """
    try:
        # 获取该产品的所有等待池记录（按账户）
        from data.db_connector import execute_query
        
        # 获取所有相关账户的等待池
        sql = """
            SELECT from_account_id, pending_amount
            FROM pending_buy_pool
            WHERE product_id = %s AND pending_amount > 0
            ORDER BY updated_at ASC
        """
        pools = execute_query(sql, (product_id,))
        
        remaining = Decimal(str(amount))
        
        # 先扣等待池
        for pool in pools:
            if remaining <= 0:
                break
            
            from_account_id = pool['from_account_id']
            pool_amount = Decimal(str(pool['pending_amount']))
            
            if pool_amount > 0:
                deduct_amount = min(remaining, pool_amount)
                reduce_pending_amount(
                    product_id, from_account_id, deduct_amount,
                    reason=f"真实买入成交扣减（交易ID: {transaction_id or 'N/A'}）"
                )
                remaining -= deduct_amount
                logger.info(f"从等待池扣减: product_id={product_id}, account_id={from_account_id}, "
                           f"deduct={deduct_amount}, remaining={remaining}")
        
        # 如果还有剩余，需要从现金扣除（这部分在invest_service中处理）
        if remaining > 0:
            logger.info(f"等待池不足，剩余金额需从现金扣除: product_id={product_id}, remaining={remaining}")
        
        return True
        
    except Exception as e:
        logger.error(f"按成交扣减等待池失败: {e}", exc_info=True)
        return False


def get_pending_pool(product_id: int, from_account_id: int) -> Optional[Dict]:
    """获取待买入池记录"""
    sql = """
        SELECT 
            id, product_id, from_account_id, pending_amount, reason,
            last_change_reason, last_change_time, version,
            created_at, updated_at
        FROM pending_buy_pool
        WHERE product_id = %s AND from_account_id = %s
    """
    return execute_one(sql, (product_id, from_account_id))


def get_all_pending_pools(from_account_id: Optional[int] = None) -> List[Dict]:
    """
    获取所有待买入池记录
    
    Args:
        from_account_id: 可选，筛选特定账户
    
    Returns:
        待买入池列表
    """
    sql = """
        SELECT 
            pbp.id, pbp.product_id, pbp.from_account_id, pbp.pending_amount, pbp.reason,
            p.code, p.product_name,
            a.account_code, a.account_name,
            pbp.created_at, pbp.updated_at
        FROM pending_buy_pool pbp
        INNER JOIN products p ON pbp.product_id = p.id
        INNER JOIN accounts a ON pbp.from_account_id = a.id
        WHERE pbp.pending_amount > 0
    """
    params = []
    
    if from_account_id:
        sql += " AND pbp.from_account_id = %s"
        params.append(from_account_id)
    
    sql += " ORDER BY pbp.updated_at DESC"
    
    return execute_query(sql, tuple(params))


def clear_pending_pool(product_id: int, from_account_id: int) -> bool:
    """清空待买入池（设置为0）"""
    sql = """
        UPDATE pending_buy_pool
        SET pending_amount = 0,
            updated_at = CURRENT_TIMESTAMP
        WHERE product_id = %s AND from_account_id = %s
    """
    execute_update(sql, (product_id, from_account_id))
    return True


