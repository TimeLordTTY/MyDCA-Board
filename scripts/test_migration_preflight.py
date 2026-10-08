"""离线回归：修改仅发生在一次性源码副本，不启动数据库。"""
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import migration_preflight as preflight


ROOT = Path(__file__).resolve().parent.parent


class PreflightTests(unittest.TestCase):
    def test_both_routes_and_evidence(self):
        for route in ("init", "migration"):
            result = preflight.check(ROOT, route)
            self.assertEqual([], result["errors"])
            self.assertTrue(result["summary"]["数量闭环"])
            self.assertEqual("NOT_RUN", result["evidence"]["disposable_database_rehearsal"])
            self.assertEqual("待部署方验证", result["evidence"]["target_manual_deployment"])
            self.assertGreater(len(result["indexes_constraints"]), 0)

    def test_duplicate_path_repeat_and_order(self):
        plan = preflight.check(ROOT)["review_order_only_not_executable"]
        cases = [plan + [preflight.paths()[0][2]], plan + [plan[0]],
                 list(reversed(plan)), [p for p in plan if p != preflight.paths()[0][1]],
                 [p for p in plan if p != preflight.SUPPLEMENTS[2]]]
        for selected in cases:
            self.assertTrue(preflight.check(ROOT, selected=selected)["errors"])

    def test_cross_group_dependencies_reject_individual_reordering(self):
        for route in ("init", "migration"):
            plan = preflight.check(ROOT, route)["review_order_only_not_executable"]
            pairs = preflight.paths()
            for before, after in [(pairs[0][1 if route == "init" else 2], pairs[1][1 if route == "init" else 2]),
                                  (preflight.SUPPLEMENTS[2], pairs[1][1 if route == "init" else 2]),
                                  (pairs[3][1 if route == "init" else 2], pairs[4][1 if route == "init" else 2])]:
                changed = plan.copy()
                changed.remove(after)
                changed.insert(changed.index(before), after)
                self.assertTrue(preflight.check(ROOT, route, changed)["errors"])
            if route == "init":
                changed = plan[1:] + plan[:1]
                self.assertTrue(preflight.check(ROOT, route, changed)["errors"])

    def test_db001_exact_copy(self):
        _, init, migration = preflight.paths()[0]
        self.assertEqual((ROOT / init).read_bytes(), (ROOT / migration).read_bytes())

    def test_missing_object_encoding_and_drift(self):
        # 临时文件也限制在 scripts 内；不复制配置、凭据或业务数据。
        with tempfile.TemporaryDirectory(dir=ROOT / "scripts") as directory:
            repo = Path(directory)
            tracked = preflight.audit.list_files(ROOT, "WORKTREE")
            files = [p for p in tracked if p.endswith((".sql", "Mapper.xml", "Mapper.java"))]
            files += ["docs/v020_database_coverage.json"]
            for p in files:
                destination = repo / p
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / p, destination)
            with patch.object(preflight.audit, "list_files", side_effect=lambda *args: [p for p in files if (repo / p).is_file()]):
                (repo / preflight.paths()[0][1]).unlink()
                result = preflight.check(repo)
                self.assertTrue(any("draft_ledger_entry" in x for x in result["errors"]))
                shutil.copyfile(ROOT / preflight.paths()[0][1], repo / preflight.paths()[0][1])
                drift = repo / preflight.paths()[2][1]
                drift.write_text(drift.read_text(encoding="utf-8") .replace("LONGTEXT", "TEXT"), encoding="utf-8")
                self.assertTrue(preflight.check(repo)["errors"])
                drift.write_bytes(b"\xff")
                # 无效编码必须失败，不能生成通过证据。
                self.assertTrue(any("UTF-8" in x for x in preflight.check(repo)["errors"]))

    def test_cli_exit_codes(self):
        for expected, extra in [(0, []), (1, ["--plan", "scripts/nonexistent-plan.json"])]:
            process = subprocess.run([sys.executable, str(ROOT / "scripts/migration_preflight.py"), *extra],
                                     capture_output=True)
            self.assertEqual(expected, process.returncode)
        with tempfile.TemporaryDirectory(dir=ROOT / "scripts") as directory:
            plan = Path(directory) / "plan.json"
            plan.write_text(json.dumps([]), encoding="utf-8")
            process = subprocess.run([sys.executable, str(ROOT / "scripts/migration_preflight.py"), "--plan", str(plan)], capture_output=True)
            self.assertEqual(2, process.returncode)


if __name__ == "__main__":
    unittest.main()
