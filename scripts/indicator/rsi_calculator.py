"""
RSI指标计算
"""
import pymysql
import pandas as pd
from datetime import date


class RSICalculator:
    """RSI指标计算器"""
    
    def __init__(self, conn):
        self.conn = conn
    
    def get_daily_bars(self, product_id: int, end_date: date, days: int = 30) -> pd.DataFrame:
        """获取日K线数据"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            sql = """
                SELECT trade_date, close
                FROM market_bar_daily
                WHERE product_id = %s AND trade_date <= %s
                ORDER BY trade_date DESC
                LIMIT %s
            """
            cursor.execute(sql, (product_id, end_date, days))
            data = cursor.fetchall()
            
            if not data:
                return pd.DataFrame()
            
            df = pd.DataFrame(data)
            df['trade_date'] = pd.to_datetime(df['trade_date'])
            df = df.sort_values('trade_date')
            return df
    
    def calculate_rsi(self, series: pd.Series, period: int = 14) -> pd.Series:
        """计算RSI指标"""
        delta = series.diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
        
        rs = gain / loss
        rsi = 100 - (100 / (1 + rs))
        
        return rsi
    
    def save_indicator(self, product_id: int, indicator_date: date, rsi6: float, rsi12: float, rsi24: float):
        """保存指标到数据库"""
        with self.conn.cursor() as cursor:
            check_sql = """
                SELECT id FROM indicator_daily
                WHERE product_id = %s AND indicator_date = %s
            """
            cursor.execute(check_sql, (product_id, indicator_date))
            existing = cursor.fetchone()
            
            if existing:
                update_sql = """
                    UPDATE indicator_daily
                    SET rsi6 = %s, rsi12 = %s, rsi24 = %s, updated_at = NOW()
                    WHERE product_id = %s AND indicator_date = %s
                """
                cursor.execute(update_sql, (rsi6, rsi12, rsi24, product_id, indicator_date))
            else:
                insert_sql = """
                    INSERT INTO indicator_daily
                    (product_id, indicator_date, rsi6, rsi12, rsi24, created_at, updated_at)
                    VALUES (%s, %s, %s, %s, %s, NOW(), NOW())
                """
                cursor.execute(insert_sql, (product_id, indicator_date, rsi6, rsi12, rsi24))
            self.conn.commit()
    
    def calculate(self, product_id: int, end_date: date):
        """计算RSI指标"""
        df = self.get_daily_bars(product_id, end_date, days=30)
        
        if df.empty or len(df) < 24:
            print(f"产品 {product_id} 数据不足，无法计算RSI（需要至少24天）")
            return
        
        # 计算各周期RSI
        df['rsi6'] = self.calculate_rsi(df['close'], 6)
        df['rsi12'] = self.calculate_rsi(df['close'], 12)
        df['rsi24'] = self.calculate_rsi(df['close'], 24)
        
        # 获取最后一天的数据
        last_row = df.iloc[-1]
        indicator_date = last_row['trade_date'].date() if hasattr(last_row['trade_date'], 'date') else end_date
        
        # 保存指标
        self.save_indicator(
            product_id,
            indicator_date,
            float(last_row['rsi6']) if pd.notna(last_row['rsi6']) else None,
            float(last_row['rsi12']) if pd.notna(last_row['rsi12']) else None,
            float(last_row['rsi24']) if pd.notna(last_row['rsi24']) else None,
        )
        
        print(f"  ✓ RSI指标计算完成: RSI6={last_row['rsi6']:.2f}, RSI12={last_row['rsi12']:.2f}")
