#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
ETF行情采集脚本
使用akshare采集ETF实时行情和日K线数据
参考 V1 版本的 akshare_client.py 实现
"""
import sys
import os
import io

# ============================================================
# 【重要】设置 UTF-8 编码，解决 Windows 控制台中文乱码
# ============================================================
if sys.platform == 'win32':
    # 使用 reconfigure 方法设置编码（Python 3.7+）
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    else:
        # 备用方案：设置环境变量
        os.environ['PYTHONIOENCODING'] = 'utf-8'

# ============================================================
# 【重要】必须在导入任何网络库之前彻底禁用代理
# 公司网络环境可能配置了系统代理，但代理无法正确转发东方财富 API
# ============================================================

# 1. 清除所有代理相关的环境变量
for key in list(os.environ.keys()):
    if 'proxy' in key.lower():
        del os.environ[key]

# 2. 明确设置不使用代理
os.environ['NO_PROXY'] = '*'
os.environ['no_proxy'] = '*'

# 3. Monkey patch urllib3 禁用代理检测（在导入 requests 之前）
import urllib3
urllib3.util.proxy_from_url = lambda url: None

# 4. Monkey patch requests 禁用代理
import requests

# 保存原始函数
_original_session_init = requests.Session.__init__
_original_get = requests.get
_original_post = requests.post

# 浏览器请求头（东方财富服务器可能检测 User-Agent）
_BROWSER_HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'application/json, text/plain, */*',
    'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
    'Accept-Encoding': 'gzip, deflate, br',
    'Connection': 'keep-alive',
    'Referer': 'https://quote.eastmoney.com/',
}

def _patched_session_init(self, *args, **kwargs):
    _original_session_init(self, *args, **kwargs)
    self.trust_env = False  # 不读取系统代理设置
    self.proxies = {}  # 清空代理
    self.headers.update(_BROWSER_HEADERS)  # 添加浏览器请求头

def _patched_get(url, **kwargs):
    kwargs['proxies'] = {}
    # 合并请求头
    headers = kwargs.get('headers', {})
    merged_headers = {**_BROWSER_HEADERS, **headers}
    kwargs['headers'] = merged_headers
    return _original_get(url, **kwargs)

def _patched_post(url, **kwargs):
    kwargs['proxies'] = {}
    # 合并请求头
    headers = kwargs.get('headers', {})
    merged_headers = {**_BROWSER_HEADERS, **headers}
    kwargs['headers'] = merged_headers
    return _original_post(url, **kwargs)

# 应用补丁
requests.Session.__init__ = _patched_session_init
requests.get = _patched_get
requests.post = _patched_post

# 5. 对已存在的 session 实例也进行处理
if hasattr(requests, 'session'):
    _orig_session_func = requests.session
    def _patched_session_func():
        s = _orig_session_func()
        s.trust_env = False
        s.proxies = {}
        s.headers.update(_BROWSER_HEADERS)
        return s
    requests.session = _patched_session_func

# ============================================================
# 代理禁用完成，现在可以安全导入其他库
# ============================================================

# 设置标准输出编码为UTF-8（Windows兼容）
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

import pymysql
import pandas as pd
from datetime import datetime, date, timedelta
from typing import List, Optional, Dict
import decimal
from decimal import Decimal
import time
import logging

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# 尝试导入 akshare（作为备用）
try:
    import akshare as ak
    AKSHARE_AVAILABLE = True
except ImportError:
    AKSHARE_AVAILABLE = False
    logger.warning("akshare 未安装，将使用直接 API 调用")


def get_market_by_code(product_code: str) -> str:
    """
    根据产品代码判断所属市场
    
    上海证券交易所代码规则：
    - 5开头：基金（包括ETF）
    - 6开头：A股
    - 9开头：B股
    
    深圳证券交易所代码规则：
    - 0开头：A股主板
    - 1开头：基金（包括LOF/ETF）
    - 2开头：B股
    - 3开头：创业板
    """
    if not product_code:
        return 'SZ'
    
    first_char = product_code[0]
    # 上海：5/6/9 开头
    if first_char in ('5', '6', '9'):
        return 'SH'
    # 深圳：0/1/2/3 开头
    return 'SZ'


def fetch_single_quote_direct(product_code: str, market: str) -> Optional[Dict]:
    """
    直接调用东方财富 API 获取单只产品的实时行情
    这样只获取需要的数据，不获取全市场数据
    
    Args:
        product_code: 产品代码（如 159659, 513100）
        market: 市场（SH/SZ）- 仅作参考，实际根据代码判断
    
    Returns:
        行情字典，如果失败返回 None
    """
    # 根据代码前缀判断真实市场（忽略传入的 market 参数，因为数据库可能有错误）
    actual_market = get_market_by_code(product_code)
    
    if actual_market != market:
        logger.warning(f"产品 {product_code} 市场不一致: 数据库={market}, 实际={actual_market}")
    
    # 东方财富 secid 格式：0.代码（深圳）或 1.代码（上海）
    if actual_market == 'SH':
        secid = f"1.{product_code}"
    else:
        secid = f"0.{product_code}"
    
    url = "https://push2.eastmoney.com/api/qt/stock/get"
    
    params = {
        "secid": secid,
        "ut": "bd1d9ddb04089700cf9c27f6f7426281",
        "fltt": "2",
        "invt": "2",
        "fields": "f43,f44,f45,f46,f47,f48,f49,f50,f51,f52,f55,f57,f58,f60,f71,f92,f152,f154,f161,f297"
        # f43=最新价, f44=最高, f45=最低, f46=今开, f47=成交量, f48=成交额
        # f51=涨停价, f52=跌停价, f55=涨跌额, f57=代码, f58=名称, f60=昨收
        # f71=均价, f92=净资产, f161=流通市值, f297=IOPV
    }
    
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'application/json, text/plain, */*',
        'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
        'Referer': 'https://quote.eastmoney.com/',
    }
    
    try:
        response = _original_get(url, params=params, headers=headers, proxies={}, timeout=10)
        response.raise_for_status()
        
        data = response.json()
        
        if data.get('rc') != 0 or data.get('data') is None:
            logger.debug(f"东方财富单品 API 返回空: {product_code}, rc={data.get('rc')}")
            return None
        
        item = data['data']
        
        # 调试：打印原始 API 返回数据（使用 DEBUG 级别，避免日志过多）
        logger.debug(f"API 返回 {product_code}: f43(价格)={item.get('f43')}, f60(昨收)={item.get('f60')}, f58(名称)={item.get('f58')}")
        
        # 检查是否有有效数据（价格不为 '-'）
        price = item.get('f43')
        if price == '-' or price is None:
            logger.debug(f"产品 {product_code} 无有效价格数据")
            return None
        
        # 构建行情字典
        result = {
            '代码': item.get('f57', product_code),
            '名称': item.get('f58', ''),
            '最新价': price,
            '最高价': item.get('f44'),
            '最低价': item.get('f45'),
            '开盘价': item.get('f46'),
            '成交量': item.get('f47'),
            '成交额': item.get('f48'),
            '昨收': item.get('f60'),
            '涨跌额': item.get('f55'),
            'IOPV实时估值': item.get('f297'),
        }
        
        # 计算涨跌幅
        prev_close = item.get('f60')
        if price and prev_close and prev_close != '-' and float(prev_close) > 0:
            try:
                pct_chg = (float(price) - float(prev_close)) / float(prev_close) * 100
                result['涨跌幅'] = round(pct_chg, 2)
            except:
                result['涨跌幅'] = None
        else:
            result['涨跌幅'] = None
        
        return result
        
    except Exception as e:
        logger.debug(f"获取单品行情失败 {product_code}: {e}")
        return None


def fetch_batch_quotes_direct(products: List[Dict]) -> Dict[str, Dict]:
    """
    批量获取多只产品的实时行情
    
    Args:
        products: 产品列表，每个产品包含 product_code 和 market
    
    Returns:
        代码到行情的映射字典
    """
    quote_map = {}
    
    for product in products:
        product_code = product['product_code']
        market = product.get('market', '')
        
        quote = fetch_single_quote_direct(product_code, market)
        if quote:
            quote_map[product_code] = quote
    
    return quote_map

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from market.config import DB_CONFIG, COLLECT_CONFIG


class ETFCollector:
    """ETF行情采集器 - 参考 V1 版本实现"""
    
    def __init__(self):
        self.conn = None
        self.connect_db()
    
    def connect_db(self):
        """连接数据库"""
        try:
            self.conn = pymysql.connect(**DB_CONFIG)
            print("数据库连接成功")
        except Exception as e:
            print(f"数据库连接失败: {e}")
            raise
    
    def close_db(self):
        """关闭数据库连接"""
        if self.conn:
            self.conn.close()
    
    def get_active_etfs(self) -> List[dict]:
        """获取所有启用的场内产品（ETF/LOF/股票/期货/期权）"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            sql = """
                SELECT id, product_code, product_name, market, asset_type
                FROM product_master
                WHERE is_active = 1
                  AND channel = 'EXCHANGE'
            """
            cursor.execute(sql)
            return cursor.fetchall()
    
    def fetch_realtime_quote(self, product_code: str, market: str) -> Optional[Dict]:
        """
        获取场内实时行情（使用东方财富接口）
        完全按照 V1 版本 akshare_client.py 的实现
        
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
            df = ak.fund_etf_spot_em()
            
            if df.empty:
                logger.warning(f"未获取到 ETF 实时行情数据")
                return None
            
            # 根据代码查找对应的产品
            product_row = None
            
            for _, row in df.iterrows():
                code = str(row.get('代码', '')).strip()
                code_clean = code.replace('sh', '').replace('sz', '').replace('SH', '').replace('SZ', '')
                if code_clean == product_code or code == product_code:
                    product_row = row
                    break
            
            if product_row is None:
                logger.warning(f"未找到产品 {product_code} (market={market}) 的实时行情")
                # 尝试使用其他接口：LOF 基金可能需要使用股票接口
                logger.info(f"尝试使用股票接口获取 {product_code} (market={market}) 的实时行情...")
                return self._fetch_realtime_quote_by_stock(product_code, market)
            
            logger.info(f"[AKShare][ETF][{product_code}] 获取成功")
            
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
            update_time_val = safe_get('更新时间')
            quote_time = None
            
            if update_time_val:
                try:
                    if hasattr(update_time_val, 'to_pydatetime'):
                        quote_time = update_time_val.to_pydatetime()
                    elif isinstance(update_time_val, datetime):
                        quote_time = update_time_val
                    elif isinstance(update_time_val, str):
                        update_time_str = update_time_val
                        if '+' in update_time_str:
                            update_time_str = update_time_str.split('+')[0].strip()
                        quote_time = datetime.strptime(update_time_str, '%Y-%m-%d %H:%M:%S')
                    else:
                        update_time_str = str(update_time_val)
                        if '+' in update_time_str:
                            update_time_str = update_time_str.split('+')[0].strip()
                        quote_time = datetime.strptime(update_time_str, '%Y-%m-%d %H:%M:%S')
                except Exception as e:
                    logger.warning(f"解析更新时间失败: {update_time_val}, 错误: {e}")
                    quote_time = None
            
            if quote_time is None:
                quote_time = datetime.now()
            
            # 核心字段
            price = safe_decimal('最新价', 0)
            iopv = safe_decimal('IOPV实时估值')
            prev_close = safe_decimal('昨收')
            
            # 涨跌幅
            pct_chg = safe_float('涨跌幅')
            if pct_chg is not None:
                pct_chg = pct_chg / 100.0
            
            # 基础价格
            open_price = safe_decimal('开盘价')
            high_price = safe_decimal('最高价')
            low_price = safe_decimal('最低价')
            
            # 流动性指标
            volume = safe_decimal('成交量')
            amount = safe_decimal('成交额')
            turnover_rate = safe_float('换手率')
            if turnover_rate is not None:
                turnover_rate = turnover_rate / 100.0
            
            # 其他指标
            amplitude = safe_float('振幅')
            if amplitude is not None:
                amplitude = amplitude / 100.0
            
            # 计算溢价率
            premium_rate = None
            if price and iopv and iopv > 0:
                premium_rate = float((price - iopv) / iopv)
            elif safe_get('基金折价率') is not None:
                discount_rate = safe_float('基金折价率')
                if discount_rate is not None:
                    premium_rate = -discount_rate / 100.0
            
            result = {
                'price': price,
                'prev_close': prev_close,
                'pct_chg': pct_chg,
                'volume': volume,
                'amount': amount,
                'quote_time': quote_time,
                'iopv': iopv,
                'premium_rate': Decimal(str(premium_rate)) if premium_rate is not None else None,
                'open': open_price,
                'high': high_price,
                'low': low_price,
                'turnover_rate': Decimal(str(turnover_rate)) if turnover_rate is not None else None,
                'amplitude': Decimal(str(amplitude)) if amplitude is not None else None,
            }
            
            return result
            
        except Exception as e:
            logger.error(f"获取 {product_code} 实时行情失败: {e}", exc_info=True)
            return None
    
    def _fetch_realtime_quote_by_stock(self, product_code: str, market: str) -> Optional[Dict]:
        """
        使用股票接口获取 LOF 基金的实时行情（备用方案）
        完全按照 V1 版本 akshare_client.py 的实现
        """
        if not AKSHARE_AVAILABLE:
            return None
        
        try:
            # 构建股票代码
            if market == 'SH':
                symbol = f"sh{product_code}"
            elif market == 'SZ':
                symbol = f"sz{product_code}"
            else:
                logger.error(f"不支持的市场类型: {market}")
                return None
            
            # 尝试使用 fund_lof_spot_em 接口
            try:
                if hasattr(ak, 'fund_lof_spot_em'):
                    df = ak.fund_lof_spot_em()
                    logger.info(f"使用 fund_lof_spot_em 接口获取 {product_code} 的实时行情")
                else:
                    df = ak.stock_zh_a_spot_em()
                    logger.info(f"使用 stock_zh_a_spot_em 接口获取 {product_code} 的实时行情")
            except Exception as e:
                logger.warning(f"尝试使用 LOF 接口失败，使用股票接口: {e}")
                df = ak.stock_zh_a_spot_em()
            
            if df.empty:
                logger.warning(f"未获取到股票实时行情数据")
                return None
            
            # 查找对应的产品
            product_row = None
            for _, row in df.iterrows():
                code = str(row.get('代码', '')).strip()
                code_clean = code.replace('sh', '').replace('sz', '').replace('SH', '').replace('SZ', '')
                if code_clean == product_code or code == product_code or code == symbol:
                    product_row = row
                    break
            
            if product_row is None:
                logger.warning(f"未找到产品 {product_code} (market={market}) 的股票实时行情")
                return None
            
            logger.info(f"[AKShare][Stock][{product_code}] 使用股票接口获取实时行情")
            
            # 解析字段
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
            
            quote_time = datetime.now()
            
            price = safe_decimal('最新价', 0) or safe_decimal('现价', 0)
            prev_close = safe_decimal('昨收', 0) or safe_decimal('前收盘', 0)
            
            pct_chg = safe_float('涨跌幅')
            if pct_chg is not None:
                pct_chg = pct_chg / 100.0
            
            open_price = safe_decimal('开盘', 0) or safe_decimal('今开', 0)
            high_price = safe_decimal('最高', 0)
            low_price = safe_decimal('最低', 0)
            
            volume = safe_decimal('成交量', 0)
            amount = safe_decimal('成交额', 0)
            turnover_rate = safe_float('换手率')
            if turnover_rate is not None:
                turnover_rate = turnover_rate / 100.0
            
            amplitude = safe_float('振幅')
            if amplitude is not None:
                amplitude = amplitude / 100.0
            
            result = {
                'price': price,
                'prev_close': prev_close,
                'pct_chg': pct_chg,
                'volume': volume,
                'amount': amount,
                'quote_time': quote_time,
                'iopv': None,
                'premium_rate': None,
                'open': open_price,
                'high': high_price,
                'low': low_price,
                'turnover_rate': Decimal(str(turnover_rate)) if turnover_rate is not None else None,
                'amplitude': Decimal(str(amplitude)) if amplitude is not None else None,
            }
            
            logger.info(f"获取 {product_code} 股票实时行情成功: price={result['price']}")
            return result
            
        except Exception as e:
            logger.error(f"获取 {product_code} 股票实时行情失败: {e}", exc_info=True)
            return None
    
    def _parse_quote_row(self, row, product_code: str, asset_type: str) -> Optional[Dict]:
        """
        从 DataFrame 行中解析行情数据
        
        Args:
            row: DataFrame 的一行数据
            product_code: 产品代码
            asset_type: 资产类型（ETF/LOF/STOCK）
        
        Returns:
            行情字典
        """
        try:
            def safe_get(key, default=None):
                val = row.get(key, default)
                if val is None or (isinstance(val, float) and (val != val or val == float('inf') or val == float('-inf'))):
                    return default
                return val
            
            def safe_decimal(key, default=None):
                val = safe_get(key, default)
                if val is None:
                    return None
                try:
                    str_val = str(val).strip()
                    # 处理 API 返回的 '-' 或空字符串表示无数据
                    if str_val in ('-', '', 'nan', 'None', 'null'):
                        return default
                    return Decimal(str_val)
                except (ValueError, TypeError, decimal.InvalidOperation):
                    return default
            
            def safe_float(key, default=None):
                val = safe_get(key, default)
                if val is None:
                    return default
                try:
                    str_val = str(val).strip()
                    # 处理 API 返回的 '-' 或空字符串表示无数据
                    if str_val in ('-', '', 'nan', 'None', 'null'):
                        return default
                    return float(str_val)
                except (ValueError, TypeError):
                    return default
            
            # 解析更新时间
            update_time_val = safe_get('更新时间')
            quote_time = None
            
            if update_time_val:
                try:
                    if hasattr(update_time_val, 'to_pydatetime'):
                        quote_time = update_time_val.to_pydatetime()
                    elif isinstance(update_time_val, datetime):
                        quote_time = update_time_val
                    elif isinstance(update_time_val, str):
                        update_time_str = update_time_val
                        if '+' in update_time_str:
                            update_time_str = update_time_str.split('+')[0].strip()
                        quote_time = datetime.strptime(update_time_str, '%Y-%m-%d %H:%M:%S')
                    else:
                        update_time_str = str(update_time_val)
                        if '+' in update_time_str:
                            update_time_str = update_time_str.split('+')[0].strip()
                        quote_time = datetime.strptime(update_time_str, '%Y-%m-%d %H:%M:%S')
                except Exception:
                    quote_time = None
            
            if quote_time is None:
                quote_time = datetime.now()
            
            # 核心字段
            price = safe_decimal('最新价', 0) or safe_decimal('现价', 0)
            prev_close = safe_decimal('昨收') or safe_decimal('前收盘')
            
            # 涨跌幅
            pct_chg = safe_float('涨跌幅')
            if pct_chg is not None:
                pct_chg = pct_chg / 100.0
            
            # 基础价格
            open_price = safe_decimal('开盘价') or safe_decimal('开盘') or safe_decimal('今开')
            high_price = safe_decimal('最高价') or safe_decimal('最高')
            low_price = safe_decimal('最低价') or safe_decimal('最低')
            
            # 流动性指标
            volume = safe_decimal('成交量')
            amount = safe_decimal('成交额')
            turnover_rate = safe_float('换手率')
            if turnover_rate is not None:
                turnover_rate = turnover_rate / 100.0
            
            # 其他指标
            amplitude = safe_float('振幅')
            if amplitude is not None:
                amplitude = amplitude / 100.0
            
            # ETF 特有字段
            iopv = None
            premium_rate = None
            
            if asset_type == 'ETF':
                iopv = safe_decimal('IOPV实时估值')
                if price and iopv and iopv > 0:
                    premium_rate = float((price - iopv) / iopv)
                elif safe_get('基金折价率') is not None:
                    discount_rate = safe_float('基金折价率')
                    if discount_rate is not None:
                        premium_rate = -discount_rate / 100.0
            
            result = {
                'price': price,
                'prev_close': prev_close,
                'pct_chg': pct_chg,
                'volume': volume,
                'amount': amount,
                'quote_time': quote_time,
                'iopv': iopv,
                'premium_rate': Decimal(str(premium_rate)) if premium_rate is not None else None,
                'open': open_price,
                'high': high_price,
                'low': low_price,
                'turnover_rate': Decimal(str(turnover_rate)) if turnover_rate is not None else None,
                'amplitude': Decimal(str(amplitude)) if amplitude is not None else None,
            }
            
            return result
            
        except Exception as e:
            logger.error(f"解析 {product_code} 行情数据失败: {e}")
            return None
    
    def fetch_daily_bar(self, product_code: str, market: str, trade_date: date) -> Optional[dict]:
        """
        获取场内日K线数据
        
        Args:
            product_code: 产品代码
            market: 市场（SH/SZ）
            trade_date: 交易日期
        
        Returns:
            日K线字典，包含：trade_date, open, high, low, close, volume, amount, prev_close
        """
        if not AKSHARE_AVAILABLE:
            logger.error("akshare 未安装，无法获取日K线")
            return None
        
        try:
            end_date = trade_date.strftime('%Y%m%d')
            start_date = (trade_date - timedelta(days=30)).strftime('%Y%m%d')
            
            symbol = product_code
            
            df = ak.fund_etf_hist_em(
                symbol=symbol,
                period='daily',
                start_date=start_date,
                end_date=end_date,
                adjust=''
            )
            
            if df.empty:
                logger.warning(f"未获取到 {product_code} 的日K线数据")
                return None
            
            # 列名映射
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
            
            df_renamed = df.rename(columns=column_map)
            
            # 查找指定日期的数据
            for _, row in df_renamed.iterrows():
                date_str = str(row.get('date', ''))
                if not date_str:
                    continue
                
                row_date = datetime.strptime(date_str, '%Y-%m-%d').date()
                if row_date == trade_date:
                    bar = {
                        'trade_date': row_date,
                        'open': Decimal(str(row.get('open', 0))) if row.get('open') is not None else None,
                        'high': Decimal(str(row.get('high', 0))) if row.get('high') is not None else None,
                        'low': Decimal(str(row.get('low', 0))) if row.get('low') is not None else None,
                        'close': Decimal(str(row.get('close', 0))) if row.get('close') is not None else None,
                        'volume': Decimal(str(row.get('volume', 0))) if row.get('volume') is not None else None,
                        'amount': Decimal(str(row.get('amount', 0))) if row.get('amount') is not None else None,
                        'prev_close': None
                    }
                    logger.info(f"获取 {product_code} 日K线成功: {trade_date}")
                    return bar
            
            logger.warning(f"未找到 {product_code} 在 {trade_date} 的日K线数据")
            return None
            
        except Exception as e:
            logger.error(f"获取 {product_code} 日K线失败: {e}", exc_info=True)
            return None
    
    def save_realtime_quote(self, product_id: int, quote_data: dict):
        """保存实时行情到数据库 market_quote_realtime 表"""
        with self.conn.cursor() as cursor:
            # 获取行情时间，如果没有则使用当前时间
            quote_time = quote_data.get('quote_time')
            if quote_time is None:
                quote_time = datetime.now()
            
            # 检查是否已存在相同时间的行情（避免重复插入）
            # 唯一键：uk_product_time_source (product_id, quote_time, source)
            check_sql = """
                SELECT id FROM market_quote_realtime 
                WHERE product_id = %s AND quote_time = %s AND source = %s
                LIMIT 1
            """
            cursor.execute(check_sql, (product_id, quote_time, 'EASTMONEY'))
            existing = cursor.fetchone()
            
            # 如果已存在相同时间的行情，跳过插入（避免无意义的更新）
            if existing:
                logger.debug(f"产品 {product_id} 在 {quote_time} 的行情已存在，跳过")
                return
            
            # 检查当天是否已有行情记录，如果有且价格相同，则跳过（避免非交易时间段重复插入）
            # 这种情况发生在服务启动时或非交易时间段查询，可能获取到的是当天收盘价
            quote_date = quote_time.date()
            current_price = quote_data.get('price')
            if current_price is not None:
                check_today_sql = """
                    SELECT id, price FROM market_quote_realtime 
                    WHERE product_id = %s 
                      AND DATE(quote_time) = %s 
                      AND source = %s
                    ORDER BY quote_time DESC
                    LIMIT 1
                """
                cursor.execute(check_today_sql, (product_id, quote_date, 'EASTMONEY'))
                today_existing = cursor.fetchone()
                
                if today_existing:
                    existing_price = today_existing[1]
                    # 如果价格相同（允许小的浮点误差），跳过插入
                    if existing_price is not None and abs(float(existing_price) - float(current_price)) < 0.0001:
                        logger.debug(f"产品 {product_id} 在 {quote_date} 已有相同价格的行情记录（价格={current_price}），跳过重复插入")
                        return
            
            # 保存到 market_quote_realtime 表（DDL 定义的表名）
            insert_sql = """
                INSERT INTO market_quote_realtime 
                (product_id, quote_time, price, prev_close, pct_chg, volume, amount, 
                 iopv, premium_rate, open_price, high_price, low_price, source)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
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
                    low_price = VALUES(low_price)
            """
            cursor.execute(insert_sql, (
                product_id,
                quote_time,
                quote_data.get('price', 0),
                quote_data.get('prev_close'),
                quote_data.get('pct_chg'),
                quote_data.get('volume'),
                quote_data.get('amount'),
                quote_data.get('iopv'),
                quote_data.get('premium_rate'),
                quote_data.get('open'),
                quote_data.get('high'),
                quote_data.get('low'),
                'EASTMONEY'
            ))
            self.conn.commit()
    
    def save_daily_bar(self, product_id: int, bar_data: dict):
        """保存日K线到数据库"""
        with self.conn.cursor() as cursor:
            insert_sql = """
                INSERT INTO daily_bar 
                (product_id, trade_date, open_price, high_price, low_price, close_price, volume, amount, data_source, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
                ON DUPLICATE KEY UPDATE
                    open_price = VALUES(open_price),
                    high_price = VALUES(high_price),
                    low_price = VALUES(low_price),
                    close_price = VALUES(close_price),
                    volume = VALUES(volume),
                    amount = VALUES(amount)
            """
            cursor.execute(insert_sql, (
                product_id,
                bar_data['trade_date'],
                bar_data.get('open', 0),
                bar_data.get('high', 0),
                bar_data.get('low', 0),
                bar_data.get('close', 0),
                bar_data.get('volume', 0),
                bar_data.get('amount', 0),
                'AKSHARE'
            ))
            self.conn.commit()
    
    def _fetch_etf_iopv_map(self) -> Dict[str, Decimal]:
        """
        使用 akshare 批量获取 ETF 的 IOPV 数据
        单品 API 不返回 IOPV，需要使用批量接口
        
        Returns:
            代码到 IOPV 的映射字典
        """
        iopv_map = {}
        
        if not AKSHARE_AVAILABLE:
            logger.warning("akshare 不可用，无法获取 IOPV")
            return iopv_map
        
        try:
            # 使用 akshare 的 ETF 实时行情接口获取 IOPV
            df = ak.fund_etf_spot_em()
            if df is not None and not df.empty:
                for _, row in df.iterrows():
                    code = str(row.get('代码', ''))
                    iopv_val = row.get('IOPV实时估值')
                    if code and iopv_val is not None:
                        try:
                            iopv_map[code] = Decimal(str(iopv_val))
                        except:
                            pass
                logger.info(f"获取到 {len(iopv_map)} 个 ETF 的 IOPV 数据")
        except Exception as e:
            logger.warning(f"获取 ETF IOPV 批量数据失败: {e}")
        
        return iopv_map
    
    def collect_realtime(self):
        """
        采集所有场内产品（ETF、股票、期货、期权）的实时行情
        使用精确查询 API，只获取数据库中存在的产品
        """
        products = self.get_active_etfs()
        print(f"开始采集 {len(products)} 个场内产品的实时行情...")
        
        if not products:
            print("没有需要采集的产品")
            return
        
        # 过滤出支持的产品类型
        supported_products = [p for p in products if p['asset_type'] in ('ETF', 'LOF', 'STOCK')]
        print(f"支持的产品: {len(supported_products)} 个")
        
        # 预先批量获取 ETF 的 IOPV 数据（单品 API 不返回 IOPV）
        iopv_map = self._fetch_etf_iopv_map()
        
        success_count = 0
        fail_count = 0
        
        # 逐个产品精确查询（只获取需要的数据）
        for product in supported_products:
            product_id = product['id']
            product_code = product['product_code']
            product_name = product['product_name']
            market = product['market']
            asset_type = product['asset_type']
            
            print(f"采集: {product_name} ({product_code})...", end=" ")
            
            # 直接调用单品 API 获取行情
            quote_dict = fetch_single_quote_direct(product_code, market)
            
            if quote_dict is None:
                print("✗ 失败")
                fail_count += 1
                continue
            
            # 补充 IOPV 数据（如果有）
            if product_code in iopv_map:
                quote_dict['IOPV实时估值'] = iopv_map[product_code]
            
            # 解析并保存行情数据
            quote_data = self._parse_quote_row(quote_dict, product_code, asset_type)
            if quote_data:
                self.save_realtime_quote(product_id, quote_data)
                iopv_str = f", IOPV={quote_data.get('iopv')}" if quote_data.get('iopv') else ""
                premium_str = f", 溢价率={quote_data.get('premium_rate'):.4f}" if quote_data.get('premium_rate') else ""
                print(f"✓ 最新价={quote_data.get('price', 0)}{iopv_str}{premium_str}")
                success_count += 1
            else:
                print("✗ 解析失败")
                fail_count += 1
            
            # 短暂延时避免请求过快
            time.sleep(0.1)
        
        # 处理不支持的产品类型
        unsupported_count = len(products) - len(supported_products)
        if unsupported_count > 0:
            print(f"跳过 {unsupported_count} 个不支持的产品类型")
            fail_count += unsupported_count
        
        print(f"\n实时行情采集完成: 成功 {success_count}, 失败 {fail_count}")
    
    def collect_daily_bars(self, trade_date: date = None):
        """采集所有场内产品的日K线数据"""
        if trade_date is None:
            trade_date = date.today()
        
        products = self.get_active_etfs()
        print(f"开始采集 {len(products)} 个场内产品的日K线数据（日期: {trade_date}）...")
        
        success_count = 0
        fail_count = 0
        
        for product in products:
            product_id = product['id']
            product_code = product['product_code']
            product_name = product['product_name']
            market = product['market']
            asset_type = product['asset_type']
            
            print(f"正在采集: {product_name} ({product_code}, {asset_type})...")
            
            if asset_type in ('ETF', 'LOF', 'STOCK'):
                bar_data = self.fetch_daily_bar(product_code, market, trade_date)
                if bar_data:
                    self.save_daily_bar(product_id, bar_data)
                    print(f"  ✓ 成功: 收盘价={bar_data['close']}")
                    success_count += 1
                else:
                    print(f"  ✗ 失败")
                    fail_count += 1
            else:
                print(f"  ⚠ 跳过: {asset_type} 类型暂不支持日K线采集")
                fail_count += 1
            
            time.sleep(0.5)
        
        print(f"\n日K线采集完成: 成功 {success_count}, 失败 {fail_count}")
    
    def run(self, collect_type: str = 'realtime'):
        """
        运行采集任务
        
        Args:
            collect_type: 'realtime' 或 'daily'
        """
        try:
            if collect_type == 'realtime':
                self.collect_realtime()
            elif collect_type == 'daily':
                self.collect_daily_bars()
            else:
                print(f"未知的采集类型: {collect_type}")
        finally:
            self.close_db()


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description='ETF行情采集')
    parser.add_argument('--type', choices=['realtime', 'daily'], default='realtime', help='采集类型')
    parser.add_argument('--date', help='交易日期（格式：YYYY-MM-DD），仅daily类型需要')
    args = parser.parse_args()
    
    collector = ETFCollector()
    if args.type == 'daily' and args.date:
        trade_date = datetime.strptime(args.date, '%Y-%m-%d').date()
        collector.collect_daily_bars(trade_date)
    else:
        collector.run(args.type)
