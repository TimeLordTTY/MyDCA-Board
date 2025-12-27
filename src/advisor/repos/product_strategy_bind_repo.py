# -*- coding: utf-8 -*-
"""产品策略绑定 Repository"""
import logging
from typing import Optional, Dict, Any, List
from data.db_connector import execute_one, execute_query, execute_update, execute_insert

logger = logging.getLogger(__name__)


def get_bind_by_product_id(product_id: int) -> Optional[Dict[str, Any]]:
    """根据产品ID获取策略绑定（向后兼容，返回第一个策略）"""
    sql = """
        SELECT 
            id, product_id, strategy_code, param_set_id, enabled,
            strategy_type, priority,
            min_trade_amount, ideal_trade_amount, fee_rate, fee_min,
            updated_at, created_at
        FROM product_strategy_bind
        WHERE product_id = %s AND enabled = 1
        ORDER BY 
            CASE strategy_type
                WHEN 'VETO' THEN 1
                WHEN 'TRIGGER' THEN 2
                WHEN 'SCORE' THEN 3
            END,
            priority ASC
        LIMIT 1
    """
    return execute_one(sql, (product_id,))


def get_binds_by_product_id(product_id: int) -> List[Dict[str, Any]]:
    """根据产品ID获取所有策略绑定（按策略类型和优先级排序）"""
    sql = """
        SELECT 
            id, product_id, strategy_code, param_set_id, enabled,
            strategy_type, priority,
            min_trade_amount, ideal_trade_amount, fee_rate, fee_min,
            updated_at, created_at
        FROM product_strategy_bind
        WHERE product_id = %s AND enabled = 1
        ORDER BY 
            CASE strategy_type
                WHEN 'VETO' THEN 1
                WHEN 'TRIGGER' THEN 2
                WHEN 'SCORE' THEN 3
            END,
            priority ASC
    """
    return execute_query(sql, (product_id,))


def create_or_update_bind(bind_data: Dict[str, Any]) -> int:
    """创建或更新策略绑定"""
    sql = """
        INSERT INTO product_strategy_bind (
            product_id, strategy_code, param_set_id, enabled,
            strategy_type, priority,
            min_trade_amount, ideal_trade_amount, fee_rate, fee_min
        ) VALUES (
            %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
        )
        ON DUPLICATE KEY UPDATE
            param_set_id = VALUES(param_set_id),
            enabled = VALUES(enabled),
            strategy_type = VALUES(strategy_type),
            priority = VALUES(priority),
            min_trade_amount = VALUES(min_trade_amount),
            ideal_trade_amount = VALUES(ideal_trade_amount),
            fee_rate = VALUES(fee_rate),
            fee_min = VALUES(fee_min),
            updated_at = CURRENT_TIMESTAMP
    """
    params = (
        bind_data['product_id'],
        bind_data['strategy_code'],
        bind_data['param_set_id'],
        bind_data.get('enabled', 1),
        bind_data.get('strategy_type', 'TRIGGER'),
        bind_data.get('priority', 0),
        bind_data.get('min_trade_amount', 1000.00),
        bind_data.get('ideal_trade_amount', 2000.00),
        bind_data.get('fee_rate', 0.000845),
        bind_data.get('fee_min', 0.20)
    )
    return execute_insert(sql, params)


def get_all_binds() -> list:
    """获取所有策略绑定"""
    sql = """
        SELECT 
            psb.id, psb.product_id, psb.strategy_code, psb.param_set_id, psb.enabled,
            psb.strategy_type, psb.priority,
            psb.min_trade_amount, psb.ideal_trade_amount, psb.fee_rate, psb.fee_min,
            p.code, p.product_name, p.channel,
            psb.updated_at, psb.created_at
        FROM product_strategy_bind psb
        INNER JOIN products p ON psb.product_id = p.id
        WHERE psb.enabled = 1
        ORDER BY p.code, 
            CASE psb.strategy_type
                WHEN 'VETO' THEN 1
                WHEN 'TRIGGER' THEN 2
                WHEN 'SCORE' THEN 3
            END,
            psb.priority ASC
    """
    return execute_query(sql)


