"""
KDJ 指标计算器。

基于 market_bar_daily 的 high/low/close 计算 RSV、K、D、J，写入 indicator_daily。
"""
from datetime import date
import pymysql
import pandas as pd


class KDJCalculator:
    """KDJ 指标计算器。"""

    def __init__(self, conn):
        self.conn = conn

    def get_daily_bars(self, product_id: int, end_date: date, days: int = 120) -> pd.DataFrame:
        """读取指定产品截至 end_date 的日 K 高低收数据。"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            sql = """
                SELECT trade_date, high AS high_price, low AS low_price, close AS close_price
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

    def calculate_kdj(self, df: pd.DataFrame, window: int = 9) -> pd.DataFrame:
        """按 9 日 RSV 和默认 K/D=50 的递推口径计算 KDJ。"""
        low_min = df['low_price'].rolling(window=window).min()
        high_max = df['high_price'].rolling(window=window).max()
        rsv = (df['close_price'] - low_min) * 100 / (high_max - low_min)
        rsv = rsv.fillna(50)
        rsv = rsv.mask((high_max - low_min) == 0, 50)

        k_values = []
        d_values = []
        prev_k = 50.0
        prev_d = 50.0
        for value in rsv:
            k = prev_k * 2 / 3 + float(value) / 3
            d = prev_d * 2 / 3 + k / 3
            k_values.append(k)
            d_values.append(d)
            prev_k = k
            prev_d = d

        result = pd.DataFrame({'kdj_rsv': rsv, 'kdj_k': k_values, 'kdj_d': d_values})
        result['kdj_j'] = 3 * result['kdj_k'] - 2 * result['kdj_d']
        return result

    def save_indicator(self, product_id: int, indicator_date: date, row: pd.Series, kdj_window: int, storage_window: int):
        """按 product_id + trade_date + window_days 幂等写入 KDJ 结果。"""
        values = tuple(None if pd.isna(row[name]) else float(row[name]) for name in [
            'kdj_k', 'kdj_d', 'kdj_j', 'kdj_rsv'
        ])
        with self.conn.cursor() as cursor:
            sql = """
                INSERT INTO indicator_daily
                (product_id, trade_date, window_days, kdj_k, kdj_d, kdj_j, kdj_rsv, kdj_window, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW())
                ON DUPLICATE KEY UPDATE
                    kdj_k = VALUES(kdj_k),
                    kdj_d = VALUES(kdj_d),
                    kdj_j = VALUES(kdj_j),
                    kdj_rsv = VALUES(kdj_rsv),
                    kdj_window = VALUES(kdj_window)
            """
            cursor.execute(sql, (product_id, indicator_date, storage_window, *values, kdj_window))
        self.conn.commit()

    def calculate(self, product_id: int, end_date: date, window: int = 9, storage_windows=None):
        """计算并保存指定产品最新交易日的 KDJ 指标。"""
        if storage_windows is None:
            storage_windows = [20]
        df = self.get_daily_bars(product_id, end_date, days=max(window * 6, 60))
        if df.empty or len(df) < window:
            print(f"产品 {product_id} 数据不足，无法计算 KDJ（需要至少 {window} 天）")
            return
        kdj = self.calculate_kdj(df, window)
        last = kdj.iloc[-1]
        indicator_date = df.iloc[-1]['trade_date'].date()
        for storage_window in storage_windows:
            self.save_indicator(product_id, indicator_date, last, window, storage_window)
        print(f"  KDJ计算完成: K={last['kdj_k']:.4f}, D={last['kdj_d']:.4f}, J={last['kdj_j']:.4f}")
