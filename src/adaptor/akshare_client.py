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
    获取场内实时行情（使用东方财富接口）
    
    Args:
        product_code: 产品代码（如 513100, 163406）
        market: 市场（SH/SZ）
    
    Returns:
        行情字典，包含：price, prev_close, pct_chg, volume, amount, quote_time, 
        iopv, premium_rate, open, high, low, amplitude, turnover_rate
        如果失败返回 None
    """
    if not AKSHARE_AVAILABLE:
        logger.error("akshare 未安装，无法获取实时行情")
        return None
    
    try:
        # 使用东方财富 ETF 实时行情接口
        # 根据市场构建代码：上海 sh，深圳 sz
        if market == 'SH':
            symbol = f"sh{product_code}"
        elif market == 'SZ':
            symbol = f"sz{product_code}"
        else:
            logger.error(f"不支持的市场类型: {market}")
            return None
        
        # 使用 akshare 的东方财富 ETF 实时行情接口
        # 这个接口返回中文字段名的 DataFrame
        df = ak.fund_etf_spot_em()
        
        if df.empty:
            logger.warning(f"未获取到 ETF 实时行情数据")
            return None
        
        # 根据代码查找对应的产品
        # 代码字段可能是 "513100" 格式（不包含市场前缀）
        product_row = None
        for _, row in df.iterrows():
            code = str(row.get('代码', '')).strip()
            # 精确匹配代码（去除可能的空格和前缀）
            # 支持 "513100" 或 "sh513100" 或 "sz513100" 格式
            code_clean = code.replace('sh', '').replace('sz', '').replace('SH', '').replace('SZ', '')
            if code_clean == product_code or code == product_code:
                product_row = row
                break
        
        if product_row is None:
            logger.warning(f"未找到产品 {product_code} 的实时行情")
            return None
        
        logger.info(f"[AKShare][ETF][{product_code}] 原始行响应={product_row.to_dict()}")
        
        # 解析中文字段名
        def safe_get(key, default=None):
            """安全获取字段值，处理 None 和空值"""
            val = product_row.get(key, default)
            if val is None or (isinstance(val, float) and (val != val or val == float('inf') or val == float('-inf'))):
                return default
            return val
        
        def safe_decimal(key, default=None):
            """安全转换为 Decimal"""
            val = safe_get(key, default)
            if val is None:
                return None
            try:
                return Decimal(str(val))
            except (ValueError, TypeError):
                return default
        
        def safe_float(key, default=None):
            """安全转换为 float"""
            val = safe_get(key, default)
            if val is None:
                return default
            try:
                return float(val)
            except (ValueError, TypeError):
                return default
        
        # 解析更新时间
        update_time_str = safe_get('更新时间', '')
        quote_time = datetime.now()  # 默认使用当前时间
        if update_time_str:
            try:
                # 尝试解析时间字符串，格式可能是 "2025-12-24 16:11:44+08:00"
                if '+' in update_time_str:
                    update_time_str = update_time_str.split('+')[0]
                quote_time = datetime.strptime(update_time_str, '%Y-%m-%d %H:%M:%S')
            except:
                pass
        
        # 核心字段：价格 & 估值
        price = safe_decimal('最新价', 0)
        iopv = safe_decimal('IOPV实时估值')
        prev_close = safe_decimal('昨收')
        
        # 涨跌幅相关
        pct_chg = safe_float('涨跌幅')  # 已经是百分比，如 -0.1 表示 -0.1%
        if pct_chg is not None:
            pct_chg = pct_chg / 100.0  # 转换为小数，如 -0.001
        
        # 基础价格时间序列
        open_price = safe_decimal('开盘价')
        high_price = safe_decimal('最高价')
        low_price = safe_decimal('最低价')
        
        # 流动性指标
        volume = safe_decimal('成交量')
        amount = safe_decimal('成交额')
        turnover_rate = safe_float('换手率')  # 换手率，如 1.88 表示 1.88%
        if turnover_rate is not None:
            turnover_rate = turnover_rate / 100.0  # 转换为小数
        
        # 其他指标
        amplitude = safe_float('振幅')  # 振幅，如 0.72 表示 0.72%
        if amplitude is not None:
            amplitude = amplitude / 100.0  # 转换为小数
        
        # 计算溢价率（根据建议：premium_rate = (最新价 / IOPV - 1)）
        premium_rate = None
        if price and iopv and iopv > 0:
            premium_rate = float((price - iopv) / iopv)
        elif safe_get('基金折价率') is not None:
            # 如果无法从 IOPV 计算，使用基金折价率（注意：折价率是负的溢价率）
            discount_rate = safe_float('基金折价率')  # 如 -7.2 表示折价 7.2%
            if discount_rate is not None:
                premium_rate = -discount_rate / 100.0  # 转换为溢价率（小数）
        
        result = {
            # 核心价格字段
            'price': price,
            'prev_close': prev_close,
            'pct_chg': pct_chg,
            'volume': volume,
            'amount': amount,
            'quote_time': quote_time,
            
            # 新增字段：IOPV 和溢价率（用于 QDII 决策）
            'iopv': iopv,
            'premium_rate': Decimal(str(premium_rate)) if premium_rate is not None else None,
            
            # 基础价格时间序列（用于高低位判断）
            'open': open_price,
            'high': high_price,
            'low': low_price,
            
            # 流动性指标（用于质量监控）
            'turnover_rate': Decimal(str(turnover_rate)) if turnover_rate is not None else None,
            
            # 其他指标
            'amplitude': Decimal(str(amplitude)) if amplitude is not None else None,
        }
        
        premium_rate_str = f"{premium_rate:.4%}" if premium_rate is not None else "None"
        logger.info(f"获取 {product_code} 实时行情成功: price={result['price']}, iopv={result['iopv']}, premium_rate={premium_rate_str}")
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
    获取 QDII ETF 溢价率（从实时行情中提取）
    
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
        # 获取实时行情（已经包含 IOPV 和溢价率）
        quote = fetch_realtime_quote(product_code, market)
        if not quote:
            return None
        
        # 从实时行情中提取 IOPV 和溢价率
        iopv = quote.get('iopv')
        premium_rate = quote.get('premium_rate')
        
        if iopv is None or premium_rate is None:
            logger.warning(f"无法获取 {product_code} 的 IOPV 或溢价率")
            return None
        
        result = {
            'iopv': iopv,
            'premium_rate': premium_rate,
            'quote_time': quote['quote_time']
        }
        
        logger.info(f"获取 {product_code} QDII 溢价率成功: iopv={iopv}, premium_rate={float(premium_rate):.4%}")
        return result
        
    except Exception as e:
        logger.error(f"获取 {product_code} QDII 溢价率失败: {e}", exc_info=True)
        return None

