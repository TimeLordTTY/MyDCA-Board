"""
ETF行情采集脚本
使用akshare采集ETF实时行情和日K线数据
"""
import sys
import os
import io

# 设置标准输出编码为UTF-8（Windows兼容）
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
import pymysql
import pandas as pd
from datetime import datetime, date, timedelta
from typing import List, Optional
import akshare as ak
import time

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from market.config import DB_CONFIG, COLLECT_CONFIG


class ETFCollector:
    """ETF行情采集器"""
    
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
                AND asset_type IN ('ETF', 'LOF', 'STOCK', 'FUTURES', 'OPTIONS')
                AND channel = 'EXCHANGE'
            """
            cursor.execute(sql)
            return cursor.fetchall()
    
    def fetch_realtime_quote(self, product_code: str, market: str) -> Optional[dict]:
        """
        采集ETF/股票实时行情（参考akshare_client.py）
        
        Args:
            product_code: 产品代码
            market: 市场（SH/SZ）
            
        Returns:
            包含实时行情数据的字典
        """
        try:
            # 优先使用 fund_etf_spot_em（ETF专用接口，效率更高）
            try:
                df = ak.fund_etf_spot_em()
                if df is not None and not df.empty:
                    # 查找对应的ETF（代码字段可能是纯数字或带前缀）
                    product_row = None
                    for _, row in df.iterrows():
                        code = str(row.get('代码', '')).strip()
                        code_clean = code.replace('sh', '').replace('sz', '').replace('SH', '').replace('SZ', '')
                        if code_clean == product_code or code == product_code:
                            product_row = row
                            break
                    
                    if product_row is not None:
                        # 解析ETF实时行情字段
                        def safe_get(key, default=None):
                            val = product_row.get(key, default)
                            if val is None or (isinstance(val, float) and (val != val or val == float('inf') or val == float('-inf'))):
                                return default
                            return val
                        
                        def safe_float(key, default=None):
                            val = safe_get(key, default)
                            if val is None:
                                return default
                            try:
                                return float(val)
                            except (ValueError, TypeError):
                                return default
                        
                        price = safe_float('最新价', 0)
                        prev_close = safe_float('昨收', 0)
                        pct_chg = safe_float('涨跌幅', 0)  # 已经是百分比，如 -0.1 表示 -0.1%
                        if pct_chg is not None:
                            pct_chg = pct_chg / 100.0  # 转换为小数
                        
                        open_price = safe_float('开盘价', 0)
                        high_price = safe_float('最高价', 0)
                        low_price = safe_float('最低价', 0)
                        volume = safe_float('成交量', 0)
                        amount = safe_float('成交额', 0)
                        iopv = safe_float('IOPV实时估值')
                        
                        # 计算溢价率
                        premium_rate = None
                        if price and iopv and iopv > 0:
                            premium_rate = (price - iopv) / iopv
                        
                        return {
                            'price': price,
                            'prev_close': prev_close,
                            'pct_chg': pct_chg,
                            'volume': volume,
                            'amount': amount,
                            'open_price': open_price,
                            'high_price': high_price,
                            'low_price': low_price,
                            'iopv': iopv,
                            'premium_rate': premium_rate,
                            'quote_time': datetime.now(),
                        }
            except Exception as e:
                print(f"[ETF接口] {product_code} 失败，尝试股票接口: {e}")
            
            # 备用方案：使用股票接口
            df = ak.stock_zh_a_spot_em()
            if df is None or df.empty:
                return None
            
            # 查找对应的股票
            stock_data = df[df['代码'] == product_code]
            if stock_data.empty:
                print(f"未找到产品 {product_code} 的行情数据")
                return None
            
            row = stock_data.iloc[0]
            price = float(row.get('最新价', 0))
            prev_close = float(row.get('昨收', 0))
            pct_chg = float(row.get('涨跌幅', 0))
            if pct_chg is not None:
                pct_chg = pct_chg / 100.0  # 转换为小数
            
            return {
                'price': price,
                'prev_close': prev_close,
                'pct_chg': pct_chg,
                'volume': float(row.get('成交量', 0)),
                'amount': float(row.get('成交额', 0)),
                'open_price': float(row.get('今开', 0)),
                'high_price': float(row.get('最高', 0)),
                'low_price': float(row.get('最低', 0)),
                'iopv': None,  # 股票没有IOPV
                'premium_rate': None,
                'quote_time': datetime.now(),
            }
        except Exception as e:
            print(f"采集产品 {product_code} 实时行情失败: {e}")
            return None
    
    def fetch_daily_bar(self, product_code: str, market: str, trade_date: date) -> Optional[dict]:
        """
        采集ETF日K线数据
        
        Args:
            product_code: 产品代码
            market: 市场（SH/SZ）
            trade_date: 交易日期
            
        Returns:
            包含日K线数据的字典
        """
        try:
            # 构建股票代码
            symbol = f"{product_code}.{'SH' if market == 'SH' else 'SZ'}"
            
            # 使用akshare获取历史K线数据
            end_date = trade_date.strftime('%Y%m%d')
            start_date = (trade_date - timedelta(days=5)).strftime('%Y%m%d')  # 获取最近5天数据
            
            df = ak.stock_zh_a_hist(symbol=product_code, period="daily", start_date=start_date, end_date=end_date, adjust="")
            if df is None or df.empty:
                return None
            
            # 查找指定日期的数据
            df['日期'] = pd.to_datetime(df['日期'])
            target_date = pd.to_datetime(trade_date)
            day_data = df[df['日期'].dt.date == trade_date]
            
            if day_data.empty:
                return None
            
            row = day_data.iloc[0]
            return {
                'trade_date': trade_date,
                'open': float(row.get('开盘', 0)),
                'high': float(row.get('最高', 0)),
                'low': float(row.get('最低', 0)),
                'close': float(row.get('收盘', 0)),
                'volume': float(row.get('成交量', 0)),
                'amount': float(row.get('成交额', 0)),
            }
        except Exception as e:
            print(f"采集产品 {product_code} 日K线失败: {e}")
            return None
    
    def save_realtime_quote(self, product_id: int, quote_data: dict):
        """保存实时行情到数据库（字段名与数据库表结构匹配）"""
        with self.conn.cursor() as cursor:
            # 使用 UPSERT（ON DUPLICATE KEY UPDATE）处理唯一约束
            insert_sql = """
                INSERT INTO market_quote_realtime
                (product_id, quote_time, price, prev_close, pct_chg, volume, amount, 
                 iopv, premium_rate, open_price, high_price, low_price, source, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
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
                quote_data.get('quote_time', datetime.now()),
                quote_data.get('price', 0),
                quote_data.get('prev_close'),
                quote_data.get('pct_chg'),
                quote_data.get('volume', 0),
                quote_data.get('amount', 0),
                quote_data.get('iopv'),
                quote_data.get('premium_rate'),
                quote_data.get('open_price', 0),
                quote_data.get('high_price', 0),
                quote_data.get('low_price', 0),
                'AKSHARE'
            ))
            self.conn.commit()
    
    def save_daily_bar(self, product_id: int, bar_data: dict):
        """保存日K线到数据库（字段名与数据库表结构匹配）"""
        with self.conn.cursor() as cursor:
            # 使用 UPSERT（ON DUPLICATE KEY UPDATE）处理唯一约束
            insert_sql = """
                INSERT INTO market_bar_daily
                (product_id, trade_date, open_price, high_price, low_price, close_price, volume, amount, source, created_at)
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
    
    def collect_realtime(self):
        """采集所有场内产品（ETF、股票、期货、期权）的实时行情"""
        products = self.get_active_etfs()
        print(f"开始采集 {len(products)} 个场内产品的实时行情...")
        
        success_count = 0
        fail_count = 0
        
        for product in products:
            product_id = product['id']
            product_code = product['product_code']
            product_name = product['product_name']
            market = product['market']
            asset_type = product['asset_type']
            
            print(f"正在采集: {product_name} ({product_code}, {asset_type})...")
            
            # 目前支持 ETF、LOF 和股票（使用相同的 akshare 接口）
            # 期货和期权需要不同的接口，暂时跳过
            if asset_type in ('ETF', 'LOF', 'STOCK'):
                quote_data = self.fetch_realtime_quote(product_code, market)
                if quote_data:
                    self.save_realtime_quote(product_id, quote_data)
                    print(f"  ✓ 成功: 最新价={quote_data.get('price', 0)}")
                    success_count += 1
                else:
                    print(f"  ✗ 失败")
                    fail_count += 1
            else:
                print(f"  ⚠ 跳过: {asset_type} 类型暂不支持实时行情采集")
                fail_count += 1
            
            time.sleep(0.5)  # 避免请求过快
        
        print(f"\n实时行情采集完成: 成功 {success_count}, 失败 {fail_count}")
    
    def collect_daily_bars(self, trade_date: date = None):
        """采集所有场内产品（ETF、股票、期货、期权）的日K线数据"""
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
            
            # 目前支持 ETF、LOF 和股票（使用相同的 akshare 接口）
            # 期货和期权需要不同的接口，暂时跳过
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
