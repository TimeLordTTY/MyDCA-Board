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
        quote_data: 行情数据（包含 price, prev_close, pct_chg, volume, amount, quote_time,
                    iopv, premium_rate, open, high, low, turnover_rate, amplitude）
        source: 数据源
    
    Returns:
        是否成功
    """
    try:
        quote_time = quote_data.get('quote_time', datetime.now())
        
        # 对齐到分钟（秒置00）
        if isinstance(quote_time, datetime):
            quote_time = quote_time.replace(second=0, microsecond=0)
        
        # UPSERT: 如果存在相同 (product_id, quote_time, source)，则更新；否则插入
        # 尝试保存新字段，如果字段不存在会失败，则回退到基础字段
        try:
            sql = """
                INSERT INTO market_quote_rt (
                    product_id, quote_time, price, prev_close, pct_chg, volume, amount,
                    iopv, premium_rate, open_price, high_price, low_price, turnover_rate, amplitude, source
                ) VALUES (
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
                )
                ON DUPLICATE KEY UPDATE
                    price = VALUES(price),
                    prev_close = VALUES(prev_close),
                    pct_chg = VALUES(pct_chg),
                    volume = VALUES(volume),
                    amount = VALUES(amount),
                    iopv = VALUES(iopv),
                    premium_rate = VALUES(premium_rate),
                    open_price = VALUES(open_price),
                    high_price = VALUES(high_price),
                    low_price = VALUES(low_price),
                    turnover_rate = VALUES(turnover_rate),
                    amplitude = VALUES(amplitude)
            """
            params = (
                product_id,
                quote_time,
                str(quote_data.get('price', 0)),
                str(quote_data.get('prev_close')) if quote_data.get('prev_close') else None,
                float(quote_data.get('pct_chg')) if quote_data.get('pct_chg') is not None else None,
                str(quote_data.get('volume')) if quote_data.get('volume') else None,
                str(quote_data.get('amount')) if quote_data.get('amount') else None,
                str(quote_data.get('iopv')) if quote_data.get('iopv') else None,
                str(quote_data.get('premium_rate')) if quote_data.get('premium_rate') else None,
                str(quote_data.get('open')) if quote_data.get('open') else None,
                str(quote_data.get('high')) if quote_data.get('high') else None,
                str(quote_data.get('low')) if quote_data.get('low') else None,
                str(quote_data.get('turnover_rate')) if quote_data.get('turnover_rate') else None,
                str(quote_data.get('amplitude')) if quote_data.get('amplitude') else None,
                source
            )
            execute_insert(sql, params)
        except Exception as e:
            # 如果新字段不存在，回退到基础字段
            logger.warning(f"尝试保存扩展字段失败，回退到基础字段: {e}")
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
        # 验证product_id有效性
        if not product_id or product_id <= 0:
            logger.error(f"无效的product_id: {product_id}")
            return False
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
        # 获取所有场内产品（channel='EXCHANGE' 且 market='SH'或'SZ'）
        products = get_products(channel='EXCHANGE', is_active=True)
        # 进一步筛选：market必须是SH或SZ（排除NA）
        products = [p for p in products if p.get('market') in ['SH', 'SZ']]
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
            
            # 自动触发指标计算（异步，不阻塞）
            if success:
                try:
                    from advisor.indicator_job import calculate_indicators_for_product
                    calculate_indicators_for_product(product_id)
                    # 自动触发建议生成
                    from advisor.advisor_service import run_for_product
                    run_for_product(product_id)
                except Exception as e:
                    logger.warning(f"自动计算指标失败: product_id={product_id}, error={e}")
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


def collect_historical_daily_bars(
    product_ids: Optional[List[int]] = None,
    start_year: int = 2000,
    batch_years: int = 2
) -> Dict[int, Dict]:
    """
    批量采集历史日K线（从指定年份到现在）
    
    由于数据量较大，采用分批获取策略（每批 N 年）
    
    Args:
        product_ids: 产品ID列表，None 表示采集所有场内产品
        start_year: 开始年份（默认2000）
        batch_years: 每批获取的年数（默认2年，避免单次请求数据量过大）
    
    Returns:
        {product_id: {'success': bool, 'total_count': int, 'error': str}} 字典
    """
    import time
    
    if product_ids is None:
        # 获取所有场内产品（channel='EXCHANGE' 且 market != 'NA'）
        products = get_products(channel='EXCHANGE', is_active=True)
        # 进一步筛选：market必须是SH或SZ（排除NA）
        products = [p for p in products if p.get('market') in ['SH', 'SZ']]
        product_ids = [p['id'] for p in products]
    
    result = {}
    today = date.today()
    
    for product_id in product_ids:
        product = get_product_by_id(product_id)
        if not product:
            result[product_id] = {'success': False, 'total_count': 0, 'error': '产品不存在'}
            continue
        
        code = product.get('code')
        market = product.get('market')
        channel = product.get('channel')
        product_name = product.get('product_name', code)
        
        # 严格检查：确保是场内产品
        # 1. channel必须是EXCHANGE
        if channel != 'EXCHANGE':
            logger.warning(f"产品 {code} ({product_name}) 不是场内产品（channel={channel}），跳过")
            result[product_id] = {'success': False, 'total_count': 0, 'error': f'非场内产品（channel={channel}）'}
            continue
        
        # 2. market必须是SH或SZ，不能是NA
        if market == 'NA' or market not in ['SH', 'SZ']:
            logger.warning(f"产品 {code} ({product_name}) 不是场内产品（market={market}, channel={channel}），跳过")
            result[product_id] = {'success': False, 'total_count': 0, 'error': f'非场内产品（market={market}, channel={channel}）'}
            continue
        
        # 记录使用的产品信息（用于排查问题）
        logger.info(f"采集产品: product_id={product_id}, code={code}, market={market}, channel={channel}, name={product_name}")
        
        logger.info(f"开始采集 {code} ({product_name}) 的历史日K数据...")
        
        total_count = 0
        current_year = start_year
        has_error = False
        error_msg = None
        
        # 分批获取（每批 N 年）
        while current_year <= today.year:
            # 计算本批的开始和结束日期
            batch_start = date(current_year, 1, 1)
            batch_end = date(min(current_year + batch_years - 1, today.year), 12, 31)
            
            # 如果结束日期超过今天，则使用今天
            if batch_end > today:
                batch_end = today
            
            logger.info(f"  采集 {code} {batch_start} 至 {batch_end} 的数据...")
            
            try:
                # 获取日K线
                bars = fetch_daily_bar(
                    code, 
                    market, 
                    start_date=batch_start.strftime('%Y-%m-%d'),
                    end_date=batch_end.strftime('%Y-%m-%d')
                )
                
                if bars:
                    batch_count = 0
                    for bar in bars:
                        # 确保使用正确的product_id保存
                        if save_daily_bar(product_id, bar):
                            batch_count += 1
                        else:
                            logger.warning(f"  保存失败: product_id={product_id}, trade_date={bar.get('trade_date')}")
                    
                    total_count += batch_count
                    logger.info(f"  ✓ 保存 {batch_count} 条数据（累计 {total_count} 条，product_id={product_id}）")
                else:
                    logger.warning(f"  ⚠ 未获取到数据（code={code}, market={market}）")
                
                # 避免请求过快，稍作延迟
                time.sleep(0.5)
                
            except Exception as e:
                error_msg = str(e)
                has_error = True
                logger.error(f"  ✗ 采集失败: {e}", exc_info=True)
                # 继续下一批，不中断
            
            # 移动到下一批
            current_year += batch_years
        
        result[product_id] = {
            'success': not has_error or total_count > 0,
            'total_count': total_count,
            'error': error_msg if has_error else None
        }
        
        if total_count > 0:
            logger.info(f"✓ {code} ({product_name}) 完成，共保存 {total_count} 条数据")
        else:
            logger.warning(f"⚠ {code} ({product_name}) 未保存任何数据")
    
    return result


def get_latest_realtime_quote(product_id: int) -> Optional[Dict]:
    """获取最新实时行情（别名）"""
    return get_latest_quote(product_id)

def get_latest_quote(product_id: int) -> Optional[Dict]:
    """获取最新实时行情"""
    # 尝试查询包含新字段，如果字段不存在则回退到基础字段
    try:
        sql = """
            SELECT 
                id, product_id, quote_time, price, prev_close, pct_chg, volume, amount,
                iopv, premium_rate, open_price, high_price, low_price, turnover_rate, amplitude, source
            FROM market_quote_rt
            WHERE product_id = %s
            ORDER BY quote_time DESC
            LIMIT 1
        """
        result = execute_one(sql, (product_id,))
        if result:
            # 将数据库字段名映射到代码中使用的字段名（不删除原字段，保留两者）
            if 'open_price' in result:
                result['open'] = result['open_price']
            if 'high_price' in result:
                result['high'] = result['high_price']
            if 'low_price' in result:
                result['low'] = result['low_price']
        return result
    except Exception as e:
        # 如果新字段不存在，回退到基础字段查询
        logger.debug(f"查询扩展字段失败，回退到基础字段: {e}")
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
            success = save_realtime_quote(product_id, quote)
            # 自动触发指标计算（异步，不阻塞）
            if success:
                try:
                    from advisor.indicator_job import calculate_indicators_for_product
                    calculate_indicators_for_product(product_id)
                    # 自动触发建议生成
                    from advisor.advisor_service import run_for_product
                    run_for_product(product_id)
                except Exception as e:
                    logger.warning(f"自动计算指标失败: product_id={product_id}, error={e}")
            return success
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

