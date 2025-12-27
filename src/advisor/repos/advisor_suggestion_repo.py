# -*- coding: utf-8 -*-
"""建议输出 Repository"""
import logging
from typing import Optional, Dict, Any, List
from datetime import datetime
from data.db_connector import execute_one, execute_query, execute_insert

logger = logging.getLogger(__name__)


def _safe_float(value, default=0.0):
    """安全转换为float，处理None、字符串、Decimal等类型"""
    if value is None:
        return default
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(value)
    except (ValueError, TypeError):
        return default


def save_suggestion(suggestion_data: Dict[str, Any]) -> int:
    """保存建议（支持扩展字段）"""
    # 检查是否有新字段，如果没有则使用兼容模式
    has_new_fields = 'new_budget' in suggestion_data or 'planned_amount' in suggestion_data
    
    if has_new_fields:
        sql = """
            INSERT INTO advisor_suggestion (
                product_id, as_of_time, strategy_code, action,
                suggest_amount, suggest_ratio, limit_price_hint, premium_rate,
                moved_to_wait_pool, reason,
                cash_available, wait_pool_balance, plan_budget_today,
                budget_for_execution, budget_to_execute, budget_to_wait_pool,
                execute_ratio, wait_ratio, reason_blocks_json,
                new_budget, wait_pool_before, planned_amount
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s, %s, %s, %s, %s, %s,
                %s, %s, %s
            )
        """
        import json
        reason_blocks_json = None
        if suggestion_data.get('reason_blocks'):
            try:
                reason_blocks_json = json.dumps(suggestion_data['reason_blocks'], ensure_ascii=False)
            except (TypeError, ValueError):
                logger.warning("序列化reason_blocks失败")
        
        params = (
            suggestion_data['product_id'],
            suggestion_data['as_of_time'],
            suggestion_data.get('strategy_code', ''),
            suggestion_data['action'],
            suggestion_data.get('suggest_amount', 0),
            suggestion_data.get('suggest_ratio'),
            suggestion_data.get('limit_price_hint'),
            suggestion_data.get('premium_rate'),
            suggestion_data.get('moved_to_wait_pool', 0),
            suggestion_data.get('reason', ''),
            suggestion_data.get('cash_available'),
            suggestion_data.get('wait_pool_balance'),
            suggestion_data.get('plan_budget_today'),
            suggestion_data.get('budget_for_execution'),
            suggestion_data.get('budget_to_execute'),
            suggestion_data.get('budget_to_wait_pool'),
            suggestion_data.get('execute_ratio'),
            suggestion_data.get('wait_ratio'),
            reason_blocks_json,
            suggestion_data.get('new_budget'),
            suggestion_data.get('wait_pool_before'),
            suggestion_data.get('planned_amount')
        )
    else:
        # 兼容旧模式
        sql = """
            INSERT INTO advisor_suggestion (
                product_id, as_of_time, strategy_code, action,
                suggest_amount, suggest_ratio, limit_price_hint, premium_rate,
                moved_to_wait_pool, reason,
                cash_available, wait_pool_balance, plan_budget_today,
                budget_for_execution, budget_to_execute, budget_to_wait_pool,
                execute_ratio, wait_ratio, reason_blocks_json
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s, %s, %s, %s, %s, %s
            )
        """
        import json
        reason_blocks_json = None
        if suggestion_data.get('reason_blocks'):
            try:
                reason_blocks_json = json.dumps(suggestion_data['reason_blocks'], ensure_ascii=False)
            except (TypeError, ValueError):
                logger.warning("序列化reason_blocks失败")
        
        params = (
            suggestion_data['product_id'],
            suggestion_data['as_of_time'],
            suggestion_data.get('strategy_code', ''),
            suggestion_data['action'],
            suggestion_data.get('suggest_amount', 0),
            suggestion_data.get('suggest_ratio'),
            suggestion_data.get('limit_price_hint'),
            suggestion_data.get('premium_rate'),
            suggestion_data.get('moved_to_wait_pool', 0),
            suggestion_data.get('reason', ''),
            suggestion_data.get('cash_available'),
            suggestion_data.get('wait_pool_balance'),
            suggestion_data.get('plan_budget_today'),
            suggestion_data.get('budget_for_execution'),
            suggestion_data.get('budget_to_execute'),
            suggestion_data.get('budget_to_wait_pool'),
            suggestion_data.get('execute_ratio'),
            suggestion_data.get('wait_ratio'),
            reason_blocks_json
        )
    
    return execute_insert(sql, params)


def get_latest_suggestion(product_id: int) -> Optional[Dict[str, Any]]:
    """获取最新建议（包含扩展字段，兼容字段不存在的情况）"""
    # 先尝试查询包含新字段的SQL
    sql_with_new_fields = """
        SELECT 
            id, product_id, as_of_time, strategy_code, action,
            suggest_amount, suggest_ratio, limit_price_hint, premium_rate,
            moved_to_wait_pool, reason, created_at,
            cash_available, wait_pool_balance, plan_budget_today,
            budget_for_execution, budget_to_execute, budget_to_wait_pool,
            execute_ratio, wait_ratio, reason_blocks_json,
            new_budget, wait_pool_before, planned_amount
        FROM advisor_suggestion
        WHERE product_id = %s
        ORDER BY as_of_time DESC
        LIMIT 1
    """
    
    # 兼容查询（不包含新字段）
    sql_without_new_fields = """
        SELECT 
            id, product_id, as_of_time, strategy_code, action,
            suggest_amount, suggest_ratio, limit_price_hint, premium_rate,
            moved_to_wait_pool, reason, created_at,
            cash_available, wait_pool_balance, plan_budget_today,
            budget_for_execution, budget_to_execute, budget_to_wait_pool,
            execute_ratio, wait_ratio, reason_blocks_json
        FROM advisor_suggestion
        WHERE product_id = %s
        ORDER BY as_of_time DESC
        LIMIT 1
    """
    
    # 先尝试使用包含新字段的查询
    try:
        result = execute_one(sql_with_new_fields, (product_id,))
    except Exception as e:
        # 如果查询失败（可能是字段不存在），使用兼容查询
        error_msg = str(e).lower()
        if 'unknown column' in error_msg and 'new_budget' in error_msg:
            logger.warning(f"检测到数据库表缺少新字段，使用兼容查询: {e}")
            result = execute_one(sql_without_new_fields, (product_id,))
        else:
            # 其他错误，重新抛出
            raise
    
    if result:
        if result.get('reason_blocks_json'):
            import json
            try:
                result['reason_blocks'] = json.loads(result['reason_blocks_json'])
            except (TypeError, ValueError, json.JSONDecodeError):
                result['reason_blocks'] = []
        # 兼容旧数据：如果没有新字段，使用旧字段计算
        if result.get('new_budget') is None:
            result['new_budget'] = _safe_float(result.get('plan_budget_today'), 0)
        if result.get('wait_pool_before') is None:
            # 从wait_pool_balance反推（不准确，但兼容旧数据）
            wait_pool_balance = _safe_float(result.get('wait_pool_balance'), 0)
            moved_to_wait = _safe_float(result.get('budget_to_wait_pool') or result.get('moved_to_wait_pool'), 0)
            result['wait_pool_before'] = wait_pool_balance - moved_to_wait
        if result.get('planned_amount') is None:
            new_budget = _safe_float(result.get('new_budget') or result.get('plan_budget_today'), 0)
            wait_pool_before = _safe_float(result.get('wait_pool_before'), 0)
            result['planned_amount'] = new_budget + wait_pool_before
    return result


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


