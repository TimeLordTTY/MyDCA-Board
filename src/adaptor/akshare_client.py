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
        
        # 调试：检查 DataFrame 的列名和部分数据
        logger.debug(f"fund_etf_spot_em 返回的列名: {df.columns.tolist()}")
        logger.debug(f"fund_etf_spot_em 返回的数据行数: {len(df)}")
        # 检查是否包含目标代码（用于调试）
        if len(df) > 0:
            sample_codes = [str(row.get('代码', '')).strip() for _, row in df.head(10).iterrows()]
            logger.debug(f"fund_etf_spot_em 前10个代码示例: {sample_codes}")
            # 检查是否包含目标代码（不区分大小写）
            all_codes = [str(row.get('代码', '')).strip() for _, row in df.iterrows()]
            if any(product_code in code or code.replace('sh', '').replace('sz', '').replace('SH', '').replace('SZ', '') == product_code for code in all_codes):
                logger.debug(f"数据中包含类似 {product_code} 的代码，但匹配失败")
        
        # 根据代码查找对应的产品
        # 代码字段可能是 "513100" 格式（不包含市场前缀）
        product_row = None
        matched_codes = []  # 用于调试：记录匹配到的代码
        
        for _, row in df.iterrows():
            code = str(row.get('代码', '')).strip()
            # 精确匹配代码（去除可能的空格和前缀）
            # 支持 "513100" 或 "sh513100" 或 "sz513100" 格式
            code_clean = code.replace('sh', '').replace('sz', '').replace('SH', '').replace('SZ', '')
            if code_clean == product_code or code == product_code:
                product_row = row
                break
            # 记录前几个代码用于调试（如果没找到）
            if len(matched_codes) < 5:
                matched_codes.append(f"'{code}' (clean: '{code_clean}')")
        
        if product_row is None:
            logger.warning(f"未找到产品 {product_code} (market={market}) 的实时行情")
            logger.debug(f"尝试匹配的代码格式: product_code='{product_code}', market={market}")
            if matched_codes:
                logger.debug(f"数据中的前几个代码示例: {', '.join(matched_codes)}")
            # 尝试使用其他接口：LOF 基金可能需要使用股票接口
            logger.info(f"尝试使用股票接口获取 {product_code} (market={market}) 的实时行情...")
            return _fetch_realtime_quote_by_stock(product_code, market)
        
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
        
        # 解析更新时间（优先使用响应中的更新时间）
        update_time_val = safe_get('更新时间')
        quote_time = None
        
        if update_time_val:
            try:
                # 如果是 pandas Timestamp 对象
                if hasattr(update_time_val, 'to_pydatetime'):
                    quote_time = update_time_val.to_pydatetime()
                # 如果是 datetime 对象
                elif isinstance(update_time_val, datetime):
                    quote_time = update_time_val
                # 如果是字符串
                elif isinstance(update_time_val, str):
                    # 尝试解析时间字符串，格式可能是 "2025-12-24 16:11:44+08:00"
                    update_time_str = update_time_val
                    if '+' in update_time_str:
                        # 处理时区信息，提取时间部分
                        if '+08:00' in update_time_str or '+0800' in update_time_str:
                            update_time_str = update_time_str.split('+')[0].strip()
                        else:
                            update_time_str = update_time_str.split('+')[0].strip()
                    quote_time = datetime.strptime(update_time_str, '%Y-%m-%d %H:%M:%S')
                else:
                    # 尝试转换为字符串再解析
                    update_time_str = str(update_time_val)
                    if '+' in update_time_str:
                        update_time_str = update_time_str.split('+')[0].strip()
                    quote_time = datetime.strptime(update_time_str, '%Y-%m-%d %H:%M:%S')
            except Exception as e:
                logger.warning(f"解析更新时间失败: {update_time_val}, 错误: {e}")
                quote_time = None
        
        # 如果解析失败，使用当前时间作为默认值
        if quote_time is None:
            quote_time = datetime.now()
            logger.debug(f"使用当前时间作为行情时间: {quote_time}")
        
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
        
        # fund_etf_hist_em 使用纯数字代码，不需要 sh/sz 前缀
        symbol = product_code
        
        # 使用 fund_etf_hist_em 接口（支持日期范围）
        df = ak.fund_etf_hist_em(
            symbol=symbol,
            period='daily',
            start_date=start_date,
            end_date=end_date,
            adjust=''
        )
        
        if df.empty:
            logger.warning(f"未获取到 {product_code} 的日K线数据")
            return []
        
        # 列名映射（fund_etf_hist_em 返回中文列名）
        column_map = {
            '日期': 'date',
            '开盘': 'open',
            '收盘': 'close',
            '最高': 'high',
            '最低': 'low',
            '成交量': 'volume',
            '成交额': 'amount',
            '涨跌额': 'change',
            '涨跌幅': 'pct_chg'
        }
        
        # 重命名列
        df_renamed = df.rename(columns=column_map)
        
        result = []
        for _, row in df_renamed.iterrows():
            try:
                # 解析日期
                date_str = str(row.get('date', ''))
                if not date_str:
                    continue
                
                trade_date = datetime.strptime(date_str, '%Y-%m-%d').date()
                
                bar = {
                    'trade_date': trade_date,
                    'open': Decimal(str(row.get('open', 0))) if row.get('open') is not None else None,
                    'high': Decimal(str(row.get('high', 0))) if row.get('high') is not None else None,
                    'low': Decimal(str(row.get('low', 0))) if row.get('low') is not None else None,
                    'close': Decimal(str(row.get('close', 0))) if row.get('close') is not None else None,
                    'volume': Decimal(str(row.get('volume', 0))) if row.get('volume') is not None else None,
                    'amount': Decimal(str(row.get('amount', 0))) if row.get('amount') is not None else None,
                    'prev_close': None  # fund_etf_hist_em 不提供 prev_close
                }
                
                # 计算 prev_close（使用前一天的 close）
                if result:
                    bar['prev_close'] = result[-1].get('close')
                
                result.append(bar)
            except Exception as e:
                logger.warning(f"解析日K线数据失败: {row}, error={e}")
                continue
        
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


def _fetch_realtime_quote_by_stock(product_code: str, market: str) -> Optional[Dict]:
    """
    使用股票接口获取 LOF 基金的实时行情（备用方案）
    
    Args:
        product_code: 产品代码（如 163406）
        market: 市场（SH/SZ）
    
    Returns:
        行情字典，格式与 fetch_realtime_quote 相同
        如果失败返回 None
    """
    if not AKSHARE_AVAILABLE:
        return None
    
    try:
        # 构建股票代码：上海 sh，深圳 sz
        if market == 'SH':
            symbol = f"sh{product_code}"
        elif market == 'SZ':
            symbol = f"sz{product_code}"
        else:
            logger.error(f"不支持的市场类型: {market}")
            return None
        
        # 尝试使用 akshare 的 LOF 基金接口（如果存在）
        # 如果不存在，则使用股票接口
        try:
            # 尝试使用 fund_lof_spot_em 接口（如果存在）
            if hasattr(ak, 'fund_lof_spot_em'):
                df = ak.fund_lof_spot_em()
                logger.info(f"使用 fund_lof_spot_em 接口获取 {product_code} 的实时行情")
            else:
                # 使用股票接口作为备用
                df = ak.stock_zh_a_spot_em()
                logger.info(f"使用 stock_zh_a_spot_em 接口获取 {product_code} 的实时行情")
        except Exception as e:
            logger.warning(f"尝试使用 LOF 接口失败，使用股票接口: {e}")
            df = ak.stock_zh_a_spot_em()
        
        if df.empty:
            logger.warning(f"未获取到股票实时行情数据")
            return None
        
        # 查找对应的产品（通过代码匹配）
        product_row = None
        for _, row in df.iterrows():
            code = str(row.get('代码', '')).strip()
            # 匹配代码（去除市场前缀）
            code_clean = code.replace('sh', '').replace('sz', '').replace('SH', '').replace('SZ', '')
            if code_clean == product_code or code == product_code or code == symbol:
                product_row = row
                break
        
        if product_row is None:
            logger.warning(f"未找到产品 {product_code} (market={market}) 的股票实时行情")
            return None
        
        logger.info(f"[AKShare][Stock][{product_code}] 使用股票接口获取实时行情")
        
        # 解析字段（股票接口的字段名可能不同）
        def safe_get(key, default=None):
            val = product_row.get(key, default)
            if val is None or (isinstance(val, float) and (val != val or val == float('inf') or val == float('-inf'))):
                return default
            return val
        
        def safe_decimal(key, default=None):
            val = safe_get(key, default)
            if val is None:
                return None
            try:
                return Decimal(str(val))
            except (ValueError, TypeError):
                return default
        
        def safe_float(key, default=None):
            val = safe_get(key, default)
            if val is None:
                return default
            try:
                return float(val)
            except (ValueError, TypeError):
                return default
        
        # 获取当前时间作为行情时间
        quote_time = datetime.now()
        
        # 核心字段：价格
        price = safe_decimal('最新价', 0) or safe_decimal('现价', 0)
        prev_close = safe_decimal('昨收', 0) or safe_decimal('前收盘', 0)
        
        # 涨跌幅相关
        pct_chg = safe_float('涨跌幅')  # 可能是百分比
        if pct_chg is not None:
            pct_chg = pct_chg / 100.0  # 转换为小数
        
        # 基础价格时间序列
        open_price = safe_decimal('开盘', 0) or safe_decimal('今开', 0)
        high_price = safe_decimal('最高', 0)
        low_price = safe_decimal('最低', 0)
        
        # 流动性指标
        volume = safe_decimal('成交量', 0)
        amount = safe_decimal('成交额', 0)
        turnover_rate = safe_float('换手率')  # 换手率
        if turnover_rate is not None:
            turnover_rate = turnover_rate / 100.0  # 转换为小数
        
        # 其他指标
        amplitude = safe_float('振幅')  # 振幅
        if amplitude is not None:
            amplitude = amplitude / 100.0  # 转换为小数
        
        # LOF 基金没有 IOPV，设置为 None
        iopv = None
        premium_rate = None
        
        result = {
            # 核心价格字段
            'price': price,
            'prev_close': prev_close,
            'pct_chg': pct_chg,
            'volume': volume,
            'amount': amount,
            'quote_time': quote_time,
            
            # LOF 基金没有 IOPV 和溢价率
            'iopv': iopv,
            'premium_rate': premium_rate,
            
            # 基础价格时间序列
            'open': open_price,
            'high': high_price,
            'low': low_price,
            
            # 流动性指标
            'turnover_rate': Decimal(str(turnover_rate)) if turnover_rate is not None else None,
            
            # 其他指标
            'amplitude': Decimal(str(amplitude)) if amplitude is not None else None,
        }
        
        logger.info(f"获取 {product_code} 股票实时行情成功: price={result['price']}")
        return result
        
    except Exception as e:
        logger.error(f"获取 {product_code} 股票实时行情失败: {e}", exc_info=True)
        return None

