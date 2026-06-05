import sys
import unittest
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from bond_collector import daily_bar_from_quote, quote_from_eastmoney


class BondCollectorMappingTest(unittest.TestCase):
    """债券行情字段映射的无数据库单元测试。"""

    def test_quote_mapping_uses_existing_pct_ratio_convention(self):
        product = {"id": 12, "product_code": "204001", "product_name": "GC001"}
        raw = {
            "f43": "1.895",
            "f44": "2.120",
            "f45": "1.720",
            "f46": "1.880",
            "f47": "128300",
            "f48": "128300000",
            "f57": "204001",
            "f58": "GC001",
            "f60": "1.780",
            "f170": "6.46",
        }

        quote = quote_from_eastmoney(product, raw, datetime(2026, 6, 5, 10, 0, 0))

        self.assertIsNotNone(quote)
        self.assertEqual(Decimal("1.895"), quote["price"])
        self.assertEqual(Decimal("1.780"), quote["prev_close"])
        self.assertEqual(Decimal("0.0646"), quote["pct_chg"])
        self.assertEqual(Decimal("128300"), quote["volume"])
        self.assertEqual(Decimal("128300000"), quote["amount"])
        self.assertEqual(Decimal("1.880"), quote["open"])
        self.assertEqual(Decimal("2.120"), quote["high"])
        self.assertEqual(Decimal("1.720"), quote["low"])

    def test_quote_mapping_falls_back_to_prev_close_when_latest_missing(self):
        product = {"id": 12, "product_code": "204001", "product_name": "GC001"}
        raw = {
            "f43": "-",
            "f57": "204001",
            "f58": "GC001",
            "f60": "1.780",
            "f170": "-",
        }

        quote = quote_from_eastmoney(product, raw, datetime(2026, 6, 5, 15, 30, 0))

        self.assertIsNotNone(quote)
        self.assertEqual(Decimal("1.780"), quote["price"])
        self.assertEqual(Decimal("0"), quote["pct_chg"])

    def test_daily_bar_uses_quote_snapshot_and_fills_missing_ohlc(self):
        quote = {
            "product_id": 12,
            "quote_time": datetime(2026, 6, 5, 15, 30, 0),
            "price": Decimal("1.895"),
            "open": None,
            "high": None,
            "low": None,
            "volume": Decimal("128300"),
            "amount": Decimal("128300000"),
            "prev_close": Decimal("1.780"),
        }

        bar = daily_bar_from_quote(quote, date(2026, 6, 5))

        self.assertEqual(date(2026, 6, 5), bar["trade_date"])
        self.assertEqual(Decimal("1.895"), bar["open"])
        self.assertEqual(Decimal("1.895"), bar["high"])
        self.assertEqual(Decimal("1.895"), bar["low"])
        self.assertEqual(Decimal("1.895"), bar["close"])
        self.assertEqual(Decimal("1.780"), bar["prev_close"])


if __name__ == "__main__":
    unittest.main()
