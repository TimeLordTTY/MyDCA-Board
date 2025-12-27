# -*- coding: utf-8 -*-
"""日更指标 Repository"""
import logging
from typing import Optional, Dict, Any
from datetime import date
from data.db_connector import execute_one, execute_query, execute_insert

logger = logging.getLogger(__name__)


def get_latest_indicator(product_id: int, window_days: int, max_date: Optional[date] = None) -> Optional[Dict[str, Any]]:
    """
    获取最新指标（<=指定日期）
    
    Args:
        product_id: 产品ID
        window_days: 窗口天数
        max_date: 最大日期（不包含），None表示今天
    """
    if max_date is None:
        from datetime import date
        max_date = date.today()
    
    # 使用 <= 而不是 <，允许获取当天的指标（如果已计算）
    # 但优先获取最新的指标（可能是今天或昨天）
    sql = """
        SELECT 
            id, product_id, trade_date, window_days,
            pct_rank, q_buy_price, q_mid_price, q_high_price,
            peak_close, drawdown_from_peak, ma20, ma60,
            created_at
        FROM indicator_daily
        WHERE product_id = %s 
          AND window_days = %s
          AND trade_date <= %s
        ORDER BY trade_date DESC
        LIMIT 1
    """
    result = execute_one(sql, (product_id, window_days, max_date))
    
    # 如果没找到，尝试不限制日期（获取最新的指标，无论日期）
    if not result:
        sql_fallback = """
            SELECT 
                id, product_id, trade_date, window_days,
                pct_rank, q_buy_price, q_mid_price, q_high_price,
                peak_close, drawdown_from_peak, ma20, ma60,
                created_at
            FROM indicator_daily
            WHERE product_id = %s 
              AND window_days = %s
            ORDER BY trade_date DESC
            LIMIT 1
        """
        result = execute_one(sql_fallback, (product_id, window_days))
    
    return result


def save_indicator(indicator_data: Dict[str, Any]) -> int:
    """保存指标"""
    sql = """
        INSERT INTO indicator_daily (
            product_id, trade_date, window_days,
            pct_rank, q_buy_price, q_mid_price, q_high_price,
            peak_close, drawdown_from_peak, ma20, ma60
        ) VALUES (
            %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
        )
        ON DUPLICATE KEY UPDATE
            pct_rank = VALUES(pct_rank),
            q_buy_price = VALUES(q_buy_price),
            q_mid_price = VALUES(q_mid_price),
            q_high_price = VALUES(q_high_price),
            peak_close = VALUES(peak_close),
            drawdown_from_peak = VALUES(drawdown_from_peak),
            ma20 = VALUES(ma20),
            ma60 = VALUES(ma60)
    """
    params = (
        indicator_data['product_id'],
        indicator_data['trade_date'],
        indicator_data['window_days'],
        indicator_data.get('pct_rank'),
        indicator_data.get('q_buy_price'),
        indicator_data.get('q_mid_price'),
        indicator_data.get('q_high_price'),
        indicator_data.get('peak_close'),
        indicator_data.get('drawdown_from_peak'),
        indicator_data.get('ma20'),
        indicator_data.get('ma60')
    )
    return execute_insert(sql, params)

