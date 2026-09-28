import tempfile
import unittest
from pathlib import Path

from core.backtest.engine import InputError, batch, load_nav, run


class BacktestTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.csv = self.root / "nav.csv"
        self.csv.write_text("date,nav\n2024-01-01,1\n2024-02-01,0.8\n2024-03-01,1.2\n")

    def tearDown(self):
        self.temp.cleanup()

    def test_determinism_and_versions(self):
        rows, digest = load_nav(self.csv, self.root)
        for name, version in (("pure_sip", "1"), ("ma_enhanced", "1"), ("profit_recycle", "1"), ("profit_recycle", "2")):
            self.assertEqual(run(rows, digest, name, version), run(rows, digest, name, version))
        self.assertNotEqual(run(rows, digest, "profit_recycle", "1")["run_id"], run(rows, digest, "profit_recycle", "2")["run_id"])

    def test_batch_baseline(self):
        result = batch({"data": "nav.csv", "strategies": [{"name": "ma_enhanced", "parameters": [{"ma_window": 2}, {"ma_window": 3}]}]}, self.root)
        self.assertEqual(len(result["results"]), 2)
        self.assertIn("max_drawdown_delta", result["results"][0]["baseline"])
        self.assertNotIn(str(self.root), str(result))

    def test_reject_bad_csv_and_paths(self):
        for content in ("date,nav\n", "day,nav\n2024-01-01,1\n", "date,nav\n2024-01-01,1\n2024-01-01,2\n", "date,nav\n2024-01-01,0\n"):
            self.csv.write_text(content)
            with self.assertRaises(InputError):
                load_nav(self.csv, self.root)
        with self.assertRaises(InputError):
            load_nav(Path("../outside.csv"), self.root)

    def test_invalid_inputs_and_limit(self):
        rows, digest = load_nav(self.csv, self.root)
        for name, version, params in (("unknown", "1", {}), ("profit_recycle", "3", {}), ("pure_sip", "1", {"contribution": -1}), ("ma_enhanced", "1", {"ma_window": 1})):
            with self.assertRaises(InputError):
                run(rows, digest, name, version, params)
        with self.assertRaises(InputError):
            batch({"data": "nav.csv", "strategies": [{"name": "pure_sip", "parameters": [{}] * 65}]}, self.root)

    def test_future_nav_cannot_change_prior_decisions(self):
        rows, digest = load_nav(self.csv, self.root)
        altered = rows[:-1] + [(rows[-1][0], 99)]
        a = run(rows, digest, "ma_enhanced", params={"ma_window": 2})
        b = run(altered, digest, "ma_enhanced", params={"ma_window": 2})
        self.assertEqual(a["metrics"]["holdings"], b["metrics"]["holdings"])


if __name__ == "__main__":
    unittest.main()
