"""
ETF行情采集脚本
使用akshare采集ETF实时行情和日K线数据
"""
import sys
import os
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
        """获取所有启用的ETF产品"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            sql = """
                SELECT id, product_code, product_name, market, asset_type
                FROM product_master
                WHERE is_active = 1
                AND asset_type = 'ETF'
                AND channel = 'EXCHANGE'
            """
            cursor.execute(sql)
            return cursor.fetchall()
    
    def fetch_realtime_quote(self, product_code: str, market: str) -> Optional[dict]:
        """
        采集ETF实时行情
        
        Args:
            product_code: 产品代码
            market: 市场（SH/SZ）
            
        Returns:
            包含实时行情数据的字典
        """
        try:
            # 构建股票代码（上海6位，深圳6位）
            symbol = f"{product_code}.{'SH' if market == 'SH' else 'SZ'}"
            
            # 使用akshare获取实时行情
            df = ak.stock_zh_a_spot_em()
            if df is None or df.empty:
                return None
            
            # 查找对应的股票
            stock_data = df[df['代码'] == product_code]
            if stock_data.empty:
                print(f"未找到产品 {product_code} 的行情数据")
                return None
            
            row = stock_data.iloc[0]
            return {
                'last_price': float(row.get('最新价', 0)),
                'change_pct': float(row.get('涨跌幅', 0)),
                'volume': float(row.get('成交量', 0)),
                'amount': float(row.get('成交额', 0)),
                'high': float(row.get('最高', 0)),
                'low': float(row.get('最低', 0)),
                'open': float(row.get('今开', 0)),
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
        """保存实时行情到数据库"""
        with self.conn.cursor() as cursor:
            # 检查是否已存在
            check_sql = """
                SELECT id FROM market_quote_realtime
                WHERE product_id = %s
            """
            cursor.execute(check_sql, (product_id,))
            existing = cursor.fetchone()
            
            if existing:
                # 更新
                update_sql = """
                    UPDATE market_quote_realtime
                    SET last_price = %s, change_pct = %s, volume = %s, amount = %s,
                        high = %s, low = %s, open = %s, updated_at = NOW()
                    WHERE product_id = %s
                """
                cursor.execute(update_sql, (
                    quote_data['last_price'], quote_data['change_pct'],
                    quote_data['volume'], quote_data['amount'],
                    quote_data['high'], quote_data['low'], quote_data['open'],
                    product_id
                ))
            else:
                # 插入
                insert_sql = """
                    INSERT INTO market_quote_realtime
                    (product_id, last_price, change_pct, volume, amount, high, low, open, created_at, updated_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
                """
                cursor.execute(insert_sql, (
                    product_id, quote_data['last_price'], quote_data['change_pct'],
                    quote_data['volume'], quote_data['amount'],
                    quote_data['high'], quote_data['low'], quote_data['open']
                ))
            self.conn.commit()
    
    def save_daily_bar(self, product_id: int, bar_data: dict):
        """保存日K线到数据库"""
        with self.conn.cursor() as cursor:
            # 检查是否已存在
            check_sql = """
                SELECT id FROM market_bar_daily
                WHERE product_id = %s AND trade_date = %s
            """
            cursor.execute(check_sql, (product_id, bar_data['trade_date']))
            if cursor.fetchone():
                # 更新
                update_sql = """
                    UPDATE market_bar_daily
                    SET open = %s, high = %s, low = %s, close = %s,
                        volume = %s, amount = %s, updated_at = NOW()
                    WHERE product_id = %s AND trade_date = %s
                """
                cursor.execute(update_sql, (
                    bar_data['open'], bar_data['high'], bar_data['low'], bar_data['close'],
                    bar_data['volume'], bar_data['amount'],
                    product_id, bar_data['trade_date']
                ))
            else:
                # 插入
                insert_sql = """
                    INSERT INTO market_bar_daily
                    (product_id, trade_date, open, high, low, close, volume, amount, created_at, updated_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
                """
                cursor.execute(insert_sql, (
                    product_id, bar_data['trade_date'],
                    bar_data['open'], bar_data['high'], bar_data['low'], bar_data['close'],
                    bar_data['volume'], bar_data['amount']
                ))
            self.conn.commit()
    
    def collect_realtime(self):
        """采集所有ETF的实时行情"""
        etfs = self.get_active_etfs()
        print(f"开始采集 {len(etfs)} 个ETF的实时行情...")
        
        success_count = 0
        fail_count = 0
        
        for etf in etfs:
            product_id = etf['id']
            product_code = etf['product_code']
            product_name = etf['product_name']
            market = etf['market']
            
            print(f"正在采集: {product_name} ({product_code})...")
            
            quote_data = self.fetch_realtime_quote(product_code, market)
            if quote_data:
                self.save_realtime_quote(product_id, quote_data)
                print(f"  ✓ 成功: 最新价={quote_data['last_price']}")
                success_count += 1
            else:
                print(f"  ✗ 失败")
                fail_count += 1
            
            time.sleep(0.5)  # 避免请求过快
        
        print(f"\n实时行情采集完成: 成功 {success_count}, 失败 {fail_count}")
    
    def collect_daily_bars(self, trade_date: date = None):
        """采集所有ETF的日K线数据"""
        if trade_date is None:
            trade_date = date.today()
        
        etfs = self.get_active_etfs()
        print(f"开始采集 {len(etfs)} 个ETF的日K线数据（日期: {trade_date}）...")
        
        success_count = 0
        fail_count = 0
        
        for etf in etfs:
            product_id = etf['id']
            product_code = etf['product_code']
            product_name = etf['product_name']
            market = etf['market']
            
            print(f"正在采集: {product_name} ({product_code})...")
            
            bar_data = self.fetch_daily_bar(product_code, market, trade_date)
            if bar_data:
                self.save_daily_bar(product_id, bar_data)
                print(f"  ✓ 成功: 收盘价={bar_data['close']}")
                success_count += 1
            else:
                print(f"  ✗ 失败")
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
