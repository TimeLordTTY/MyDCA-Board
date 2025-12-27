# -*- coding: utf-8 -*-
"""建议输出 Repository"""
import logging
from typing import Optional, Dict, Any, List
from datetime import datetime
from data.db_connector import execute_one, execute_query, execute_insert

logger = logging.getLogger(__name__)


def save_suggestion(suggestion_data: Dict[str, Any]) -> int:
    """保存建议"""
    sql = """
        INSERT INTO advisor_suggestion (
            product_id, as_of_time, strategy_code, action,
            suggest_amount, suggest_ratio, limit_price_hint, premium_rate,
            moved_to_wait_pool, reason
        ) VALUES (
            %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
        )
    """
    params = (
        suggestion_data['product_id'],
        suggestion_data['as_of_time'],
        suggestion_data['strategy_code'],
        suggestion_data['action'],
        suggestion_data['suggest_amount'],
        suggestion_data.get('suggest_ratio'),
        suggestion_data.get('limit_price_hint'),
        suggestion_data.get('premium_rate'),
        suggestion_data['moved_to_wait_pool'],
        suggestion_data['reason']
    )
    return execute_insert(sql, params)


def get_latest_suggestion(product_id: int) -> Optional[Dict[str, Any]]:
    """获取最新建议"""
    sql = """
        SELECT 
            id, product_id, as_of_time, strategy_code, action,
            suggest_amount, suggest_ratio, limit_price_hint, premium_rate,
            moved_to_wait_pool, reason, created_at
        FROM advisor_suggestion
        WHERE product_id = %s
        ORDER BY as_of_time DESC
        LIMIT 1
    """
    return execute_one(sql, (product_id,))


def get_suggestions_by_time_range(
    product_id: Optional[int] = None,
    start_time: Optional[datetime] = None,
    end_time: Optional[datetime] = None
) -> List[Dict[str, Any]]:
    """按时间范围获取建议"""
    sql = """
        SELECT 
            s.id, s.product_id, s.as_of_time, s.strategy_code, s.action,
            s.suggest_amount, s.suggest_ratio, s.limit_price_hint, s.premium_rate,
            s.moved_to_wait_pool, s.reason, s.created_at,
            p.code, p.product_name
        FROM advisor_suggestion s
        INNER JOIN products p ON s.product_id = p.id
        WHERE 1=1
    """
    params = []
    
    if product_id:
        sql += " AND s.product_id = %s"
        params.append(product_id)
    
    if start_time:
        sql += " AND s.as_of_time >= %s"
        params.append(start_time)
    
    if end_time:
        sql += " AND s.as_of_time <= %s"
        params.append(end_time)
    
    sql += " ORDER BY s.as_of_time DESC"
    
    return execute_query(sql, tuple(params) if params else None)

