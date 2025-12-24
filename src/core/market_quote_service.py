"""场内行情服务 - 行情数据入库与管理"""
import logging
from datetime import datetime, date
from typing import List, Dict, Optional
from decimal import Decimal

from data.db_connector import execute_query, execute_one, execute_update, execute_insert
from adaptor.akshare_client import fetch_realtime_quote, fetch_daily_bar, fetch_qdii_premium
from data.product_service import get_product_by_id, get_products

logger = logging.getLogger(__name__)


def save_realtime_quote(product_id: int, quote_data: Dict, source: str = 'AKSHARE') -> bool:
    """
    保存实时行情到数据库（支持 UPSERT）
    
    Args:
        product_id: 产品ID
        quote_data: 行情数据（包含 price, prev_close, pct_chg, volume, amount, quote_time）
        source: 数据源
    
    Returns:
        是否成功
    """
    try:
        quote_time = quote_data.get('quote_time', datetime.now())
        
        # UPSERT: 如果存在相同 (product_id, quote_time, source)，则更新；否则插入
        sql = """
            INSERT INTO market_quote_rt (
                product_id, quote_time, price, prev_close, pct_chg, volume, amount, source
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s, %s
            )
            ON DUPLICATE KEY UPDATE
                price = VALUES(price),
                prev_close = VALUES(prev_close),
                pct_chg = VALUES(pct_chg),
                volume = VALUES(volume),
                amount = VALUES(amount)
        """
        params = (
            product_id,
            quote_time,
            str(quote_data.get('price', 0)),
            str(quote_data.get('prev_close')) if quote_data.get('prev_close') else None,
            float(quote_data.get('pct_chg')) if quote_data.get('pct_chg') is not None else None,
            str(quote_data.get('volume')) if quote_data.get('volume') else None,
            str(quote_data.get('amount')) if quote_data.get('amount') else None,
            source
        )
        
        execute_insert(sql, params)
        logger.debug(f"保存实时行情成功: product_id={product_id}, quote_time={quote_time}")
        return True
        
    except Exception as e:
        logger.error(f"保存实时行情失败: {e}", exc_info=True)
        return False


def save_daily_bar(product_id: int, bar_data: Dict, source: str = 'AKSHARE') -> bool:
    """
    保存日K线到数据库（支持 UPSERT）
    
    Args:
        product_id: 产品ID
        bar_data: K线数据（包含 trade_date, open, high, low, close, volume, amount, prev_close）
        source: 数据源
    
    Returns:
        是否成功
    """
    try:
        sql = """
            INSERT INTO market_bar_d (
                product_id, trade_date, open_price, high_price, low_price, close_price,
                volume, amount, prev_close, source
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
            )
            ON DUPLICATE KEY UPDATE
                open_price = VALUES(open_price),
                high_price = VALUES(high_price),
                low_price = VALUES(low_price),
                close_price = VALUES(close_price),
                volume = VALUES(volume),
                amount = VALUES(amount),
                prev_close = VALUES(prev_close)
        """
        params = (
            product_id,
            bar_data.get('trade_date'),
            str(bar_data.get('open')) if bar_data.get('open') else None,
            str(bar_data.get('high')) if bar_data.get('high') else None,
            str(bar_data.get('low')) if bar_data.get('low') else None,
            str(bar_data.get('close', 0)),
            str(bar_data.get('volume')) if bar_data.get('volume') else None,
            str(bar_data.get('amount')) if bar_data.get('amount') else None,
            str(bar_data.get('prev_close')) if bar_data.get('prev_close') else None,
            source
        )
        
        execute_insert(sql, params)
        logger.debug(f"保存日K线成功: product_id={product_id}, trade_date={bar_data.get('trade_date')}")
        return True
        
    except Exception as e:
        logger.error(f"保存日K线失败: {e}", exc_info=True)
        return False


def save_qdii_premium(product_id: int, premium_data: Dict, source: str = 'AKSHARE') -> bool:
    """
    保存 QDII 溢价率到数据库（支持 UPSERT）
    
    Args:
        product_id: 产品ID
        premium_data: 溢价率数据（包含 iopv, premium_rate, quote_time）
        source: 数据源
    
    Returns:
        是否成功
    """
    try:
        quote_time = premium_data.get('quote_time', datetime.now())
        
        sql = """
            INSERT INTO qdii_premium_rt (
                product_id, quote_time, iopv, premium_rate, source
            ) VALUES (
                %s, %s, %s, %s, %s
            )
            ON DUPLICATE KEY UPDATE
                iopv = VALUES(iopv),
                premium_rate = VALUES(premium_rate)
        """
        params = (
            product_id,
            quote_time,
            str(premium_data.get('iopv')) if premium_data.get('iopv') else None,
            str(premium_data.get('premium_rate', 0)),
            source
        )
        
        execute_insert(sql, params)
        logger.debug(f"保存 QDII 溢价率成功: product_id={product_id}, premium_rate={premium_data.get('premium_rate')}")
        return True
        
    except Exception as e:
        logger.error(f"保存 QDII 溢价率失败: {e}", exc_info=True)
        return False


def collect_realtime_quotes(product_ids: Optional[List[int]] = None) -> Dict[int, bool]:
    """
    批量采集实时行情
    
    Args:
        product_ids: 产品ID列表，None 表示采集所有场内产品
    
    Returns:
        {product_id: success} 字典
    """
    from data.product_service import get_products
    
    if product_ids is None:
        # 获取所有场内产品
        products = get_products(channel='EXCHANGE', is_active=True)
        product_ids = [p['id'] for p in products]
    
    result = {}
    for product_id in product_ids:
        product = get_product_by_id(product_id)
        if not product:
            logger.warning(f"产品不存在: product_id={product_id}")
            result[product_id] = False
            continue
        
        code = product.get('code')
        market = product.get('market')
        
        if market == 'NA':
            logger.warning(f"产品 {code} 不是场内产品，跳过")
            result[product_id] = False
            continue
        
        # 获取实时行情
        quote = fetch_realtime_quote(code, market)
        if quote:
            success = save_realtime_quote(product_id, quote)
            result[product_id] = success
            
            # 如果是 QDII，同时采集溢价率
            if product.get('is_qdii'):
                premium = fetch_qdii_premium(code, market)
                if premium:
                    save_qdii_premium(product_id, premium)
        else:
            result[product_id] = False
    
    return result


def collect_daily_bars(product_ids: Optional[List[int]] = None, days: int = 20) -> Dict[int, bool]:
    """
    批量采集日K线
    
    Args:
        product_ids: 产品ID列表，None 表示采集所有场内产品
        days: 采集最近 N 日
    
    Returns:
        {product_id: success} 字典
    """
    from data.product_service import get_products
    from datetime import timedelta
    
    if product_ids is None:
        products = get_products(channel='EXCHANGE', is_active=True)
        product_ids = [p['id'] for p in products]
    
    start_date = (date.today() - timedelta(days=days)).strftime('%Y-%m-%d')
    
    result = {}
    for product_id in product_ids:
        product = get_product_by_id(product_id)
        if not product:
            result[product_id] = False
            continue
        
        code = product.get('code')
        market = product.get('market')
        
        if market == 'NA':
            result[product_id] = False
            continue
        
        # 获取日K线
        bars = fetch_daily_bar(code, market, start_date=start_date)
        if bars:
            success_count = 0
            for bar in bars:
                if save_daily_bar(product_id, bar):
                    success_count += 1
            result[product_id] = success_count > 0
        else:
            result[product_id] = False
    
    return result


def get_latest_realtime_quote(product_id: int) -> Optional[Dict]:
    """获取最新实时行情（别名）"""
    return get_latest_quote(product_id)

def get_latest_quote(product_id: int) -> Optional[Dict]:
    """获取最新实时行情"""
    sql = """
        SELECT 
            id, product_id, quote_time, price, prev_close, pct_chg, volume, amount, source
        FROM market_quote_rt
        WHERE product_id = %s
        ORDER BY quote_time DESC
        LIMIT 1
    """
    return execute_one(sql, (product_id,))


def get_latest_qdii_premium(product_id: int) -> Optional[Dict]:
    """获取最新QDII溢价率（别名）"""
    return get_latest_premium(product_id)

def get_latest_premium(product_id: int) -> Optional[Dict]:
    """获取最新 QDII 溢价率"""
    sql = """
        SELECT 
            id, product_id, quote_time, iopv, premium_rate, source
        FROM qdii_premium_rt
        WHERE product_id = %s
        ORDER BY quote_time DESC
        LIMIT 1
    """
    return execute_one(sql, (product_id,))


def get_position_20d(product_id: int) -> Optional[float]:
    """
    计算近20日区间位置
    
    公式：pos_20d = (close - low_20d) / (high_20d - low_20d)
    
    Returns:
        位置值（0-1），None 表示数据不足
    """
    sql = """
        SELECT 
            close_price, 
            MIN(low_price) AS low_20d,
            MAX(high_price) AS high_20d
        FROM market_bar_d
        WHERE product_id = %s
          AND trade_date >= DATE_SUB(CURDATE(), INTERVAL 20 DAY)
        GROUP BY product_id
    """
    result = execute_one(sql, (product_id,))
    
    if not result:
        return None
    
    close = float(result.get('close_price', 0))
    low_20d = float(result.get('low_20d', 0))
    high_20d = float(result.get('high_20d', 0))
    
    if high_20d <= low_20d:
        return None
    
    pos = (close - low_20d) / (high_20d - low_20d)
    return max(0.0, min(1.0, pos))  # 限制在 0-1 之间


def fetch_and_save_realtime_quote(product_id: int, symbol: str) -> bool:
    """
    从AKShare获取实时行情并保存
    
    Args:
        product_id: 产品ID
        symbol: 产品代码
    
    Returns:
        是否成功
    """
    try:
        product = get_product_by_id(product_id)
        if not product:
            return False
        market = product.get('market', 'SH')
        quote = fetch_realtime_quote(symbol, market)
        if quote:
            return save_realtime_quote(product_id, quote)
        return False
    except Exception as e:
        logger.error(f"获取实时行情失败: {e}", exc_info=True)
        return False


def fetch_and_save_daily_bar(product_id: int, symbol: str, days: int = 20) -> bool:
    """
    从AKShare获取日K线并保存
    
    Args:
        product_id: 产品ID
        symbol: 产品代码
        days: 获取最近N日
    
    Returns:
        是否成功
    """
    try:
        from datetime import timedelta
        start_date = (date.today() - timedelta(days=days * 2)).strftime('%Y-%m-%d')
        product = get_product_by_id(product_id)
        if not product:
            return False
        market = product.get('market', 'SH')
        bars = fetch_daily_bar(symbol, market, start_date=start_date)
        if bars:
            success_count = 0
            for bar in bars:
                if save_daily_bar(product_id, bar):
                    success_count += 1
            return success_count > 0
        return False
    except Exception as e:
        logger.error(f"获取日K线失败: {e}", exc_info=True)
        return False


def fetch_and_save_qdii_premium(product_id: int, symbol: str) -> bool:
    """
    从AKShare获取QDII溢价率并保存
    
    Args:
        product_id: 产品ID
        symbol: 产品代码
    
    Returns:
        是否成功
    """
    try:
        product = get_product_by_id(product_id)
        if not product:
            return False
        market = product.get('market', 'SH')
        premium = fetch_qdii_premium(symbol, market)
        if premium:
            return save_qdii_premium(product_id, premium)
        return False
    except Exception as e:
        logger.error(f"获取QDII溢价率失败: {e}", exc_info=True)
        return False


def get_daily_bars(product_id: int, start_date: str, end_date: str) -> List[Dict]:
    """
    获取日K线数据
    
    Args:
        product_id: 产品ID
        start_date: 开始日期 (YYYY-MM-DD)
        end_date: 结束日期 (YYYY-MM-DD)
    
    Returns:
        K线数据列表
    """
    sql = """
        SELECT 
            id, product_id, trade_date, open_price as `open`, high_price as high,
            low_price as low, close_price as `close`, volume, amount, prev_close, source
        FROM market_bar_d
        WHERE product_id = %s AND trade_date BETWEEN %s AND %s
        ORDER BY trade_date ASC
    """
    return execute_query(sql, (product_id, start_date, end_date))

