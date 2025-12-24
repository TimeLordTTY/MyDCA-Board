"""待买入池服务"""
import logging
from decimal import Decimal
from typing import List, Dict, Optional

from data.db_connector import execute_query, execute_one, execute_update, execute_insert

logger = logging.getLogger(__name__)


def add_pending_amount(product_id: int, from_account_id: int, amount: Decimal, reason: str = None) -> bool:
    """
    增加待买入金额（累加）
    
    Args:
        product_id: 产品ID
        from_account_id: 来源账户ID
        amount: 待买入金额
        reason: 扣留原因
    
    Returns:
        是否成功
    """
    try:
        # 先查询是否存在
        existing = get_pending_pool(product_id, from_account_id)
        
        if existing:
            # 更新：累加金额
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
            # 插入新记录
            sql = """
                INSERT INTO pending_buy_pool (
                    product_id, from_account_id, pending_amount, reason
                ) VALUES (
                    %s, %s, %s, %s
                )
            """
            execute_insert(sql, (product_id, from_account_id, str(amount), reason))
        
        logger.info(f"增加待买入金额: product_id={product_id}, account_id={from_account_id}, amount={amount}")
        return True
        
    except Exception as e:
        logger.error(f"增加待买入金额失败: {e}", exc_info=True)
        return False


def reduce_pending_amount(product_id: int, from_account_id: int, amount: Decimal) -> bool:
    """
    减少待买入金额
    
    Args:
        product_id: 产品ID
        from_account_id: 来源账户ID
        amount: 减少金额
    
    Returns:
        是否成功
    """
    try:
        existing = get_pending_pool(product_id, from_account_id)
        if not existing:
            logger.warning(f"待买入池不存在: product_id={product_id}, account_id={from_account_id}")
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
        
        logger.info(f"减少待买入金额: product_id={product_id}, account_id={from_account_id}, "
                   f"amount={amount}, new_amount={new_amount}")
        return True
        
    except Exception as e:
        logger.error(f"减少待买入金额失败: {e}", exc_info=True)
        return False


def get_pending_pool(product_id: int, from_account_id: int) -> Optional[Dict]:
    """获取待买入池记录"""
    sql = """
        SELECT 
            id, product_id, from_account_id, pending_amount, reason,
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

