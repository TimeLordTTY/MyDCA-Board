# -*- coding: utf-8 -*-
"""策略状态 Repository"""
import json
import logging
from typing import Optional, Dict, Any
from data.db_connector import execute_one, execute_insert, execute_update

logger = logging.getLogger(__name__)


def get_state(product_id: int, strategy_code: str) -> Optional[Dict[str, Any]]:
    """获取策略状态"""
    sql = """
        SELECT 
            id, product_id, strategy_code, state_json,
            updated_at, created_at
        FROM strategy_state
        WHERE product_id = %s AND strategy_code = %s
    """
    row = execute_one(sql, (product_id, strategy_code))
    if row and row.get('state_json'):
        try:
            state_dict = json.loads(row['state_json'])
            row['state_json'] = state_dict
        except (json.JSONDecodeError, TypeError):
            logger.warning(f"解析state_json失败: product_id={product_id}, strategy_code={strategy_code}")
            row['state_json'] = {}
    return row


def save_state(product_id: int, strategy_code: str, state_dict: Dict[str, Any]) -> bool:
    """保存策略状态"""
    try:
        state_json = json.dumps(state_dict, ensure_ascii=False)
        sql = """
            INSERT INTO strategy_state (
                product_id, strategy_code, state_json
            ) VALUES (%s, %s, %s)
            ON DUPLICATE KEY UPDATE
                state_json = VALUES(state_json),
                updated_at = CURRENT_TIMESTAMP
        """
        execute_update(sql, (product_id, strategy_code, state_json))
        return True
    except Exception as e:
        logger.error(f"保存策略状态失败: {e}", exc_info=True)
        return False


