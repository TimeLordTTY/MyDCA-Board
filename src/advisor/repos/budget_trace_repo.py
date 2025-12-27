# -*- coding: utf-8 -*-
"""
预算追踪 Repository
用于保存预算分配与延期的审计日志
"""
import logging
from typing import Optional, Dict, Any
from datetime import datetime
from decimal import Decimal
from data.db_connector import execute_insert, execute_one, execute_query

logger = logging.getLogger(__name__)


def save_budget_trace(trace_data: Dict[str, Any]) -> int:
    """
    保存预算追踪记录
    
    Args:
        trace_data: 包含以下字段的字典
            - product_id: 产品ID
            - as_of_time: 建议生成时间
            - new_budget: 本轮新增预算
            - wait_pool_before: 等待池余额（before）
            - planned_amount: 本轮可用于买入
            - executed_amount: 本轮建议执行金额
            - moved_to_wait: 本轮进入等待池金额
            - wait_pool_after: 等待池余额（after）
            - reason_code: 原因代码（如NON_TRADE_DAY, PREMIUM_BRAKE）
            - reason_text: 原因说明（结构化文本）
    
    Returns:
        插入的记录ID
    """
    sql = """
        INSERT INTO budget_trace (
            product_id, as_of_time, new_budget, wait_pool_before,
            planned_amount, executed_amount, moved_to_wait, wait_pool_after,
            reason_code, reason_text
        ) VALUES (
            %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
        )
        ON DUPLICATE KEY UPDATE
            new_budget = VALUES(new_budget),
            wait_pool_before = VALUES(wait_pool_before),
            planned_amount = VALUES(planned_amount),
            executed_amount = VALUES(executed_amount),
            moved_to_wait = VALUES(moved_to_wait),
            wait_pool_after = VALUES(wait_pool_after),
            reason_code = VALUES(reason_code),
            reason_text = VALUES(reason_text)
    """
    
    params = (
        trace_data['product_id'],
        trace_data['as_of_time'],
        float(trace_data['new_budget']),
        float(trace_data['wait_pool_before']),
        float(trace_data['planned_amount']),
        float(trace_data['executed_amount']),
        float(trace_data['moved_to_wait']),
        float(trace_data['wait_pool_after']),
        trace_data['reason_code'],
        trace_data['reason_text']
    )
    
    return execute_insert(sql, params)


def get_latest_trace(product_id: int) -> Optional[Dict[str, Any]]:
    """获取最新的预算追踪记录"""
    sql = """
        SELECT 
            id, product_id, as_of_time, new_budget, wait_pool_before,
            planned_amount, executed_amount, moved_to_wait, wait_pool_after,
            reason_code, reason_text, created_at
        FROM budget_trace
        WHERE product_id = %s
        ORDER BY as_of_time DESC
        LIMIT 1
    """
    return execute_one(sql, (product_id,))


def get_traces_by_time_range(
    product_id: Optional[int] = None,
    start_time: Optional[datetime] = None,
    end_time: Optional[datetime] = None
) -> list:
    """按时间范围获取预算追踪记录"""
    sql = """
        SELECT 
            id, product_id, as_of_time, new_budget, wait_pool_before,
            planned_amount, executed_amount, moved_to_wait, wait_pool_after,
            reason_code, reason_text, created_at
        FROM budget_trace
        WHERE 1=1
    """
    params = []
    
    if product_id:
        sql += " AND product_id = %s"
        params.append(product_id)
    
    if start_time:
        sql += " AND as_of_time >= %s"
        params.append(start_time)
    
    if end_time:
        sql += " AND as_of_time <= %s"
        params.append(end_time)
    
    sql += " ORDER BY as_of_time DESC"
    
    return execute_query(sql, tuple(params) if params else None)



