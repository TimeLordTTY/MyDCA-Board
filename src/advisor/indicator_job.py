# -*- coding: utf-8 -*-
"""
日更指标计算任务

从 market_bar_d 计算衍生指标，写入 indicator_daily。
"""
import logging
from datetime import date, timedelta
from typing import List, Dict, Any, Optional
from decimal import Decimal

from data.db_connector import execute_query, execute_one
from data.product_service import get_products
from .repos.indicator_daily_repo import save_indicator
from .repos.product_strategy_bind_repo import get_bind_by_product_id

logger = logging.getLogger(__name__)


def calculate_percentile_indicator(product_id: int, trade_date: date, window_days: int) -> Optional[Dict[str, Any]]:
    """
    计算分位指标（仅计算 pct_rank，不再计算 q_buy_price）
    
    Args:
        product_id: 产品ID
        trade_date: 交易日期
        window_days: 窗口天数
        
    Returns:
        指标字典，包含 pct_rank, q_mid_price, q_high_price（不再包含 q_buy_price）
    """
    # 获取窗口内的收盘价数据
    sql = """
        SELECT close_price
        FROM market_bar_d
        WHERE product_id = %s
          AND trade_date < %s
          AND trade_date >= DATE_SUB(%s, INTERVAL %s DAY)
        ORDER BY trade_date ASC
    """
    rows = execute_query(sql, (product_id, trade_date, trade_date, window_days))
    
    # 计算实际需要的交易日数（750个自然日约等于500-550个交易日）
    # 使用更灵活的要求：至少需要60%的数据，或者至少300个交易日
    min_required = max(int(window_days * 0.6), 300)
    
    if not rows or len(rows) < min_required:
        logger.warning(f"数据不足: product_id={product_id}, trade_date={trade_date}, window_days={window_days}, 需要至少{min_required}条, 实际={len(rows)}")
        return None
    
    # 提取收盘价列表
    closes = [float(row['close_price']) for row in rows if row.get('close_price')]
    if len(closes) < min_required:
        logger.warning(f"有效数据不足: product_id={product_id}, 需要至少{min_required}条, 实际={len(closes)}")
        return None
    
    # 获取最近的交易日收盘价（用于计算分位排名）
    # 不直接使用 trade_date - 1，因为可能不是交易日
    sql_yesterday = """
        SELECT close_price
        FROM market_bar_d
        WHERE product_id = %s AND trade_date < %s
        ORDER BY trade_date DESC
        LIMIT 1
    """
    row_yesterday = execute_one(sql_yesterday, (product_id, trade_date))
    if not row_yesterday:
        logger.warning(f"无法获取最近交易日收盘价: product_id={product_id}, trade_date={trade_date}")
        return None
    
    yesterday_close = float(row_yesterday['close_price'])
    
    # 计算分位排名（pct_rank）
    closes_sorted = sorted(closes)
    below_count = sum(1 for c in closes_sorted if c < yesterday_close)
    pct_rank = below_count / len(closes_sorted) if closes_sorted else 0.0
    
    # 计算50%和80%分位价格（用于展示，不用于买入判断）
    mid_index = int(len(closes_sorted) * 0.5)
    high_index = int(len(closes_sorted) * 0.8)
    q_mid_price = closes_sorted[mid_index] if mid_index < len(closes_sorted) else None
    q_high_price = closes_sorted[high_index] if high_index < len(closes_sorted) else None
    
    return {
        'pct_rank': pct_rank,
        'q_mid_price': q_mid_price,
        'q_high_price': q_high_price
    }


def calculate_drawdown_indicator(product_id: int, trade_date: date, window_days: int) -> Optional[Dict[str, Any]]:
    """
    计算回撤指标
    
    Returns:
        指标字典，包含 peak_close, drawdown_from_peak
    """
    # 获取窗口内的收盘价数据
    sql = """
        SELECT close_price
        FROM market_bar_d
        WHERE product_id = %s
          AND trade_date < %s
          AND trade_date >= DATE_SUB(%s, INTERVAL %s DAY)
        ORDER BY trade_date ASC
    """
    rows = execute_query(sql, (product_id, trade_date, trade_date, window_days))
    
    if not rows:
        return None
    
    closes = [float(row['close_price']) for row in rows if row.get('close_price')]
    if not closes:
        return None
    
    # 计算峰值
    peak_close = max(closes)
    
    # 获取最近的交易日收盘价（用于计算回撤）
    # 不直接使用 trade_date - 1，因为可能不是交易日
    sql_yesterday = """
        SELECT close_price
        FROM market_bar_d
        WHERE product_id = %s AND trade_date < %s
        ORDER BY trade_date DESC
        LIMIT 1
    """
    row_yesterday = execute_one(sql_yesterday, (product_id, trade_date))
    if not row_yesterday:
        logger.warning(f"无法获取最近交易日收盘价: product_id={product_id}, trade_date={trade_date}")
        return None
    
    yesterday_close = float(row_yesterday['close_price'])
    
    # 计算回撤
    if peak_close > 0:
        drawdown_from_peak = (yesterday_close - peak_close) / peak_close
    else:
        drawdown_from_peak = 0.0
    
    return {
        'peak_close': peak_close,
        'drawdown_from_peak': drawdown_from_peak
    }


def calculate_ma_indicator(product_id: int, trade_date: date, ma_days: int) -> Optional[float]:
    """
    计算移动平均线
    
    Args:
        product_id: 产品ID
        trade_date: 交易日期
        ma_days: 均线天数（如20、60）
        
    Returns:
        均线值，None表示数据不足
    """
    sql = """
        SELECT close_price
        FROM market_bar_d
        WHERE product_id = %s
          AND trade_date < %s
        ORDER BY trade_date DESC
        LIMIT %s
    """
    rows = execute_query(sql, (product_id, trade_date, ma_days))
    
    if not rows or len(rows) < ma_days:
        return None
    
    closes = [float(row['close_price']) for row in rows if row.get('close_price')]
    if len(closes) < ma_days:
        return None
    
    return sum(closes) / len(closes)


def calculate_indicators_for_product(product_id: int, trade_date: Optional[date] = None) -> int:
    """
    为单个产品计算指标
    
    Args:
        product_id: 产品ID
        trade_date: 交易日期，None表示昨天
        
    Returns:
        成功计算的指标数量
    """
    if trade_date is None:
        trade_date = date.today() - timedelta(days=1)
    
    # 获取策略绑定
    bind = get_bind_by_product_id(product_id)
    if not bind:
        logger.debug(f"产品未绑定策略: product_id={product_id}")
        return 0
    
    strategy_code = bind['strategy_code']
    param_set_id = bind['param_set_id']
    
    # 获取策略参数
    sql = """
        SELECT param_json
        FROM strategy_config
        WHERE strategy_key = %s AND param_set_id = %s AND is_active = 1
        LIMIT 1
    """
    row = execute_one(sql, (strategy_code, param_set_id))
    if not row or not row.get('param_json'):
        logger.warning(f"策略参数未找到: strategy_code={strategy_code}, param_set_id={param_set_id}")
        return 0
    
    import json
    try:
        params = json.loads(row['param_json'])
    except (json.JSONDecodeError, TypeError):
        logger.warning(f"解析参数失败: strategy_code={strategy_code}, param_set_id={param_set_id}")
        return 0
    
    window_days = int(params.get('window_days', 750))
    
    # 计算指标
    indicator_data = {
        'product_id': product_id,
        'trade_date': trade_date,
        'window_days': window_days,
        'pct_rank': None,
        'q_buy_price': None,  # 保留字段以兼容旧数据，但不再计算
        'q_mid_price': None,
        'q_high_price': None,
        'peak_close': None,
        'drawdown_from_peak': None,
        'ma20': None,
        'ma60': None
    }
    
    # 根据策略类型计算对应指标
    if strategy_code == 'percentile':
        # 验证参数：必须包含 tiers
        tiers = params.get('tiers')
        if not tiers or not isinstance(tiers, list) or len(tiers) == 0:
            logger.error(f"percentile策略参数配置缺失: strategy_code={strategy_code}, param_set_id={param_set_id}, 必须提供 tiers 参数")
            return 0
        
        # 只计算 pct_rank（不依赖 buy_percentile，不再计算 q_buy_price）
        percentile_ind = calculate_percentile_indicator(product_id, trade_date, window_days)
        if percentile_ind:
            indicator_data.update(percentile_ind)
    
    # 计算回撤指标（所有策略都需要，用于展示）
    drawdown_ind = calculate_drawdown_indicator(product_id, trade_date, window_days)
    if drawdown_ind:
        indicator_data.update(drawdown_ind)
    
    # 计算均线（所有策略都需要）
    ma20 = calculate_ma_indicator(product_id, trade_date, 20)
    ma60 = calculate_ma_indicator(product_id, trade_date, 60)
    if ma20:
        indicator_data['ma20'] = ma20
    if ma60:
        indicator_data['ma60'] = ma60
    
    # 保存指标
    try:
        save_indicator(indicator_data)
        logger.info(f"计算指标成功: product_id={product_id}, trade_date={trade_date}, window_days={window_days}")
        return 1
    except Exception as e:
        logger.error(f"保存指标失败: product_id={product_id}, error={e}", exc_info=True)
        return 0


def calculate_indicators_for_all_products(trade_date: Optional[date] = None) -> Dict[str, int]:
    """
    为所有场内产品计算指标
    
    Returns:
        {success_count, fail_count}
    """
    if trade_date is None:
        trade_date = date.today() - timedelta(days=1)
    
    products = get_products(channel='EXCHANGE', is_active=True)
    
    success_count = 0
    fail_count = 0
    
    for product in products:
        product_id = product['id']
        try:
            result = calculate_indicators_for_product(product_id, trade_date)
            if result > 0:
                success_count += 1
            else:
                fail_count += 1
        except Exception as e:
            logger.error(f"计算指标失败: product_id={product_id}, error={e}", exc_info=True)
            fail_count += 1
    
    logger.info(f"计算指标完成: 成功={success_count}, 失败={fail_count}, trade_date={trade_date}")
    
    return {
        'success_count': success_count,
        'fail_count': fail_count
    }

