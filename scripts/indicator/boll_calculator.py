"""
BOLL 指标计算器。

基于 market_bar_daily.close 计算中轨、上轨、下轨和标准差，写入 indicator_daily。
"""
from datetime import date
import math
import pymysql
import pandas as pd


class BOLLCalculator:
    """BOLL 指标计算器。"""

    def __init__(self, conn):
        self.conn = conn

    def get_daily_bars(self, product_id: int, end_date: date, days: int = 120) -> pd.DataFrame:
        """读取指定产品截至 end_date 的日 K 收盘价。"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            sql = """
                SELECT trade_date, close AS close_price
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
        return df.sort_values('trade_date')

    def calculate_boll(self, close: pd.Series, window: int = 20) -> pd.DataFrame:
        """按总体标准差口径计算 BOLL。"""
        middle = close.rolling(window=window).mean()
        std = close.rolling(window=window).std(ddof=0)
        return pd.DataFrame({
            'boll_middle': middle,
            'boll_upper': middle + 2 * std,
            'boll_lower': middle - 2 * std,
            'boll_std': std,
        })

    def save_indicator(self, product_id: int, indicator_date: date, row: pd.Series, window: int):
        """按 product_id + trade_date + window_days 幂等写入 BOLL 结果。"""
        values = tuple(None if pd.isna(row[name]) else float(row[name]) for name in [
            'boll_middle', 'boll_upper', 'boll_lower', 'boll_std'
        ])
        with self.conn.cursor() as cursor:
            sql = """
                INSERT INTO indicator_daily
                (product_id, trade_date, window_days, boll_middle, boll_upper, boll_lower, boll_std, boll_window, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW())
                ON DUPLICATE KEY UPDATE
                    boll_middle = VALUES(boll_middle),
                    boll_upper = VALUES(boll_upper),
                    boll_lower = VALUES(boll_lower),
                    boll_std = VALUES(boll_std),
                    boll_window = VALUES(boll_window)
            """
            cursor.execute(sql, (product_id, indicator_date, window, *values, window))
        self.conn.commit()

    def calculate(self, product_id: int, end_date: date, window: int = 20):
        """计算并保存指定产品最新交易日的 BOLL 指标。"""
        df = self.get_daily_bars(product_id, end_date, days=max(window * 3, 60))
        if df.empty or len(df) < window:
            print(f"产品 {product_id} 数据不足，无法计算 BOLL（需要至少 {window} 天）")
            return
        boll = self.calculate_boll(df['close_price'], window)
        last = boll.iloc[-1]
        indicator_date = df.iloc[-1]['trade_date'].date()
        self.save_indicator(product_id, indicator_date, last, window)
        if not math.isnan(float(last['boll_middle'])):
            print(f"  BOLL计算完成: middle={last['boll_middle']:.4f}, upper={last['boll_upper']:.4f}, lower={last['boll_lower']:.4f}")
