"""
移动平均线（MA）计算
"""
import pymysql
import pandas as pd
from datetime import date
from typing import Optional


class MACalculator:
    """移动平均线计算器"""
    
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
    
    def calculate_ma(self, df: pd.DataFrame, period: int) -> pd.Series:
        """计算移动平均线"""
        return df['close'].rolling(window=period).mean()
    
    def save_indicator(self, product_id: int, indicator_date: date, ma5: float, ma10: float, 
                      ma20: float, ma30: float, ma60: float):
        """保存指标到数据库"""
        with self.conn.cursor() as cursor:
            # 检查是否已存在
            check_sql = """
                SELECT id FROM indicator_daily
                WHERE product_id = %s AND indicator_date = %s
            """
            cursor.execute(check_sql, (product_id, indicator_date))
            existing = cursor.fetchone()
            
            if existing:
                # 更新
                update_sql = """
                    UPDATE indicator_daily
                    SET ma5 = %s, ma10 = %s, ma20 = %s, ma30 = %s, ma60 = %s, updated_at = NOW()
                    WHERE product_id = %s AND indicator_date = %s
                """
                cursor.execute(update_sql, (ma5, ma10, ma20, ma30, ma60, product_id, indicator_date))
            else:
                # 插入
                insert_sql = """
                    INSERT INTO indicator_daily
                    (product_id, indicator_date, ma5, ma10, ma20, ma30, ma60, created_at, updated_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
                """
                cursor.execute(insert_sql, (product_id, indicator_date, ma5, ma10, ma20, ma30, ma60))
            self.conn.commit()
    
    def calculate(self, product_id: int, end_date: date):
        """计算MA指标"""
        # 获取足够的历史数据（至少60天）
        df = self.get_daily_bars(product_id, end_date, days=60)
        
        if df.empty or len(df) < 5:
            print(f"产品 {product_id} 数据不足，无法计算MA")
            return
        
        # 计算各周期MA
        df['ma5'] = self.calculate_ma(df, 5)
        df['ma10'] = self.calculate_ma(df, 10)
        df['ma20'] = self.calculate_ma(df, 20)
        df['ma30'] = self.calculate_ma(df, 30)
        df['ma60'] = self.calculate_ma(df, 60)
        
        # 获取最后一天的数据（end_date或最近一天）
        last_row = df.iloc[-1]
        indicator_date = last_row['trade_date'].date() if hasattr(last_row['trade_date'], 'date') else end_date
        
        # 保存指标
        self.save_indicator(
            product_id,
            indicator_date,
            float(last_row['ma5']) if pd.notna(last_row['ma5']) else None,
            float(last_row['ma10']) if pd.notna(last_row['ma10']) else None,
            float(last_row['ma20']) if pd.notna(last_row['ma20']) else None,
            float(last_row['ma30']) if pd.notna(last_row['ma30']) else None,
            float(last_row['ma60']) if pd.notna(last_row['ma60']) else None,
        )
        
        print(f"  ✓ MA指标计算完成: MA5={last_row['ma5']:.2f}, MA10={last_row['ma10']:.2f}")
