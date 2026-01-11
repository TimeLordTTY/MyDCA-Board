"""
MACD指标计算
"""
import pymysql
import pandas as pd
from datetime import date
import numpy as np


class MACDCalculator:
    """MACD指标计算器"""
    
    def __init__(self, conn):
        self.conn = conn
    
    def get_daily_bars(self, product_id: int, end_date: date, days: int = 60) -> pd.DataFrame:
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
    
    def calculate_ema(self, series: pd.Series, period: int) -> pd.Series:
        """计算指数移动平均线（EMA）"""
        return series.ewm(span=period, adjust=False).mean()
    
    def calculate_macd(self, df: pd.DataFrame, fast: int = 12, slow: int = 26, signal: int = 9) -> pd.DataFrame:
        """计算MACD指标"""
        close = df['close']
        
        # 计算快线和慢线EMA
        ema_fast = self.calculate_ema(close, fast)
        ema_slow = self.calculate_ema(close, slow)
        
        # DIF = 快线EMA - 慢线EMA
        dif = ema_fast - ema_slow
        
        # DEA = DIF的EMA（信号线）
        dea = self.calculate_ema(dif, signal)
        
        # MACD柱 = (DIF - DEA) * 2
        macd = (dif - dea) * 2
        
        df['dif'] = dif
        df['dea'] = dea
        df['macd'] = macd
        
        return df
    
    def save_indicator(self, product_id: int, indicator_date: date, dif: float, dea: float, macd: float):
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
                    SET dif = %s, dea = %s, macd = %s, updated_at = NOW()
                    WHERE product_id = %s AND indicator_date = %s
                """
                cursor.execute(update_sql, (dif, dea, macd, product_id, indicator_date))
            else:
                insert_sql = """
                    INSERT INTO indicator_daily
                    (product_id, indicator_date, dif, dea, macd, created_at, updated_at)
                    VALUES (%s, %s, %s, %s, %s, NOW(), NOW())
                """
                cursor.execute(insert_sql, (product_id, indicator_date, dif, dea, macd))
            self.conn.commit()
    
    def calculate(self, product_id: int, end_date: date):
        """计算MACD指标"""
        df = self.get_daily_bars(product_id, end_date, days=60)
        
        if df.empty or len(df) < 26:
            print(f"产品 {product_id} 数据不足，无法计算MACD（需要至少26天）")
            return
        
        # 计算MACD
        df = self.calculate_macd(df)
        
        # 获取最后一天的数据
        last_row = df.iloc[-1]
        indicator_date = last_row['trade_date'].date() if hasattr(last_row['trade_date'], 'date') else end_date
        
        # 保存指标
        self.save_indicator(
            product_id,
            indicator_date,
            float(last_row['dif']) if pd.notna(last_row['dif']) else None,
            float(last_row['dea']) if pd.notna(last_row['dea']) else None,
            float(last_row['macd']) if pd.notna(last_row['macd']) else None,
        )
        
        print(f"  ✓ MACD指标计算完成: DIF={last_row['dif']:.4f}, DEA={last_row['dea']:.4f}")
