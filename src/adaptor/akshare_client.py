"""AKShare 场内行情采集客户端"""
import logging
from datetime import datetime, date
from typing import List, Dict, Optional
from decimal import Decimal

try:
    import akshare as ak
    AKSHARE_AVAILABLE = True
except ImportError:
    AKSHARE_AVAILABLE = False
    logging.warning("akshare 未安装，场内行情功能不可用")

logger = logging.getLogger(__name__)


def fetch_realtime_quote(product_code: str, market: str) -> Optional[Dict]:
    """
    获取场内实时行情
    
    Args:
        product_code: 产品代码（如 513100, 163406）
        market: 市场（SH/SZ）
    
    Returns:
        行情字典，包含：price, prev_close, pct_chg, volume, amount, quote_time
        如果失败返回 None
    """
    if not AKSHARE_AVAILABLE:
        logger.error("akshare 未安装，无法获取实时行情")
        return None
    
    try:
        # 根据市场选择不同的接口
        if market == 'SH':
            # 上海交易所
            df = ak.fund_etf_hist_sina(symbol=f"sh{product_code}")
        elif market == 'SZ':
            # 深圳交易所
            df = ak.fund_etf_hist_sina(symbol=f"sz{product_code}")
        else:
            logger.error(f"不支持的市场类型: {market}")
            return None
        
        if df.empty:
            logger.warning(f"未获取到 {product_code} 的实时行情")
            return None
        
        # 获取最新一行
        latest = df.iloc[-1]
        
        # 解析数据
        quote_time = datetime.now()  # 使用当前时间作为行情时间
        
        result = {
            'price': Decimal(str(latest.get('close', 0))),
            'prev_close': Decimal(str(latest.get('prev_close', 0))) if 'prev_close' in latest else None,
            'pct_chg': float(latest.get('pct_chg', 0)) if 'pct_chg' in latest else None,
            'volume': Decimal(str(latest.get('volume', 0))) if 'volume' in latest else None,
            'amount': Decimal(str(latest.get('amount', 0))) if 'amount' in latest else None,
            'quote_time': quote_time
        }
        
        logger.info(f"获取 {product_code} 实时行情成功: price={result['price']}, pct_chg={result['pct_chg']}")
        return result
        
    except Exception as e:
        logger.error(f"获取 {product_code} 实时行情失败: {e}", exc_info=True)
        return None


def fetch_daily_bar(product_code: str, market: str, start_date: Optional[str] = None, 
                    end_date: Optional[str] = None) -> List[Dict]:
    """
    获取场内日K线数据
    
    Args:
        product_code: 产品代码
        market: 市场（SH/SZ）
        start_date: 开始日期（YYYY-MM-DD），默认最近20日
        end_date: 结束日期（YYYY-MM-DD），默认今天
    
    Returns:
        日K线列表，每个元素包含：trade_date, open, high, low, close, volume, amount, prev_close
    """
    if not AKSHARE_AVAILABLE:
        logger.error("akshare 未安装，无法获取日K线")
        return []
    
    try:
        if end_date is None:
            end_date = date.today().strftime('%Y%m%d')
        else:
            end_date = end_date.replace('-', '')
        
        if start_date is None:
            # 默认最近20个交易日
            from datetime import timedelta
            start_date = (date.today() - timedelta(days=30)).strftime('%Y%m%d')
        else:
            start_date = start_date.replace('-', '')
        
        # 根据市场选择接口
        if market == 'SH':
            symbol = f"sh{product_code}"
        elif market == 'SZ':
            symbol = f"sz{product_code}"
        else:
            logger.error(f"不支持的市场类型: {market}")
            return []
        
        df = ak.fund_etf_hist_sina(symbol=symbol, period="daily", 
                                    start_date=start_date, end_date=end_date)
        
        if df.empty:
            logger.warning(f"未获取到 {product_code} 的日K线数据")
            return []
        
        result = []
        for _, row in df.iterrows():
            bar = {
                'trade_date': datetime.strptime(str(row.get('date', '')), '%Y-%m-%d').date() if 'date' in row else None,
                'open': Decimal(str(row.get('open', 0))) if 'open' in row else None,
                'high': Decimal(str(row.get('high', 0))) if 'high' in row else None,
                'low': Decimal(str(row.get('low', 0))) if 'low' in row else None,
                'close': Decimal(str(row.get('close', 0))) if 'close' in row else None,
                'volume': Decimal(str(row.get('volume', 0))) if 'volume' in row else None,
                'amount': Decimal(str(row.get('amount', 0))) if 'amount' in row else None,
                'prev_close': Decimal(str(row.get('prev_close', 0))) if 'prev_close' in row else None
            }
            if bar['trade_date']:
                result.append(bar)
        
        logger.info(f"获取 {product_code} 日K线成功: {len(result)} 条")
        return result
        
    except Exception as e:
        logger.error(f"获取 {product_code} 日K线失败: {e}", exc_info=True)
        return []


def fetch_qdii_premium(product_code: str, market: str) -> Optional[Dict]:
    """
    获取 QDII ETF 溢价率
    
    Args:
        product_code: 产品代码（如 513100, 513500, 513180）
        market: 市场（SH/SZ）
    
    Returns:
        溢价率字典，包含：iopv, premium_rate, quote_time
        如果失败返回 None
    """
    if not AKSHARE_AVAILABLE:
        logger.error("akshare 未安装，无法获取 QDII 溢价率")
        return None
    
    try:
        # 获取实时行情
        quote = fetch_realtime_quote(product_code, market)
        if not quote:
            return None
        
        # 获取 IOPV（基金份额参考净值）
        # 注意：akshare 可能没有直接的 IOPV 接口，这里先用价格作为近似
        # 实际 IOPV 需要从其他数据源获取
        
        current_price = quote['price']
        
        # 尝试从日K线获取昨收价作为 IOPV 近似值
        # 实际应用中，IOPV 应该从专门的接口获取
        iopv = quote.get('prev_close')
        
        if iopv and iopv > 0:
            premium_rate = float((current_price - iopv) / iopv)
        else:
            # 如果无法获取 IOPV，返回 None
            logger.warning(f"无法获取 {product_code} 的 IOPV，无法计算溢价率")
            return None
        
        result = {
            'iopv': iopv,
            'premium_rate': Decimal(str(premium_rate)),
            'quote_time': quote['quote_time']
        }
        
        logger.info(f"获取 {product_code} QDII 溢价率成功: premium_rate={premium_rate:.4%}")
        return result
        
    except Exception as e:
        logger.error(f"获取 {product_code} QDII 溢价率失败: {e}", exc_info=True)
        return None

