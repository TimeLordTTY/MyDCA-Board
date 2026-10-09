import ast
import json
from pathlib import Path
import socket
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import mysql57_compat_preflight as preflight

ROOT = Path(__file__).resolve().parent.parent


class Mysql57Tests(unittest.TestCase):
    def test_exact_gap_coverage_and_evidence_boundaries(self):
        result = preflight.review()
        self.assertEqual({"missing_tables": 9, "missing_columns": 2, "missing_unique_indexes": 2}, result["counts"])
        self.assertEqual({"draft_lifecycle_event", "research_plan", "risk_watch_rule", "risk_watch_snapshot",
                          "risk_watch_event", "risk_watch_mute", "allocation_policy", "goal_tracking", "monthly_budget"},
                         {name for g in result["groups"] for name in g["missing_tables"]})
        self.assertEqual(set(preflight.COLUMNS + preflight.INDEXES), {o["object"] for o in result["supplement_objects"]})
        self.assertTrue(all(o["line"] and o["manual_checks"] and o["execution_blocker"] for o in result["supplement_objects"]))
        self.assertTrue(result["static_coverage_pass"])
        self.assertFalse(result["production_ready"])
        self.assertEqual("NOT_RUN", result["engine_test"])
        self.assertEqual(["STATIC_REVIEW", "ENGINE_NOT_TESTED", "PRODUCTION_SCHEMA_BLOCKED"], result["status"])

    def test_route_exclusivity(self):
        for route in ["init", "migration"]:
            result = preflight.review(route=route)
            self.assertEqual([], result["errors"])
            for group in result["groups"]:
                self.assertEqual(group[route], group["selected_for_review_only"])
        both = [p for _, init, migration in preflight.paths() for p in (init, migration)]
        self.assertTrue(preflight.review(selected=both)["errors"])
        self.assertTrue(preflight.review(selected=[])["errors"])

    def test_missing_object_and_path_drift_fail_closed(self):
        with tempfile.TemporaryDirectory(dir=ROOT / "scripts/deploy") as directory:
            repo = Path(directory)
            files = {p for _, init, migration in preflight.paths() for p in (init, migration)} | set(preflight.SUPPLEMENTS)
            for path in files:
                destination = repo / path
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / path, destination)
            changed = repo / preflight.SUPPLEMENTS[2]
            changed.write_text(changed.read_text(encoding="utf-8").replace("uk_draft_ledger_user_source", "other_key"), encoding="utf-8")
            result = preflight.review(repo)
            self.assertFalse(result["static_coverage_pass"])
            self.assertTrue(any("uk_draft_ledger_user_source" in error for error in result["errors"]))
            changed = repo / "backend/migrations/20261002_goal_tracking.sql"
            changed.write_text(changed.read_text().replace("goal_tracking", "other_table"), encoding="utf-8")
            result = preflight.review(repo)
            self.assertTrue(any("双路径结构漂移" in error for error in result["errors"]))
            self.assertTrue(any("缺失表脚本覆盖" in error for error in result["errors"]))

    def test_check_parse_and_enforcement_are_separate(self):
        found = preflight.classify("CONSTRAINT chk_payload CHECK (JSON_VALID(payload));")
        by_feature = {f["feature"]: f for f in found}
        self.assertEqual("MYSQL8_FEATURE", by_feature["NAMED_CHECK"]["category"])
        self.assertEqual("INCOMPATIBLE_SEMANTICS", by_feature["CHECK_NOT_ENFORCED"]["category"])
        self.assertIn("NOT_RUN", by_feature["NAMED_CHECK"]["note"])
        self.assertIn("忽略", by_feature["CHECK_NOT_ENFORCED"]["note"])
        report = preflight.review()
        for name in ["goal_tracking", "monthly_budget", "research_plan", "allocation_policy", "risk_watch", "risk_alert_history"]:
            path = next(g["migration"] for g in report["groups"] if g["group"] == name)
            self.assertIn("NAMED_CHECK", {f["feature"] for f in report["files"][path]["findings"]})

    def test_classifier_semantics_and_comments(self):
        sql = "-- CHECK(JSON_VALID(payload))\nCREATE TABLE x (p JSON, ts TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6), v VARCHAR(255), KEY k(v));\nUPDATE x SET v='secret CHECK(1)';"
        found = preflight.classify(sql)
        features = {f["feature"] for f in found}
        self.assertNotIn("CHECK_NOT_ENFORCED", features)
        self.assertTrue({"JSON_TYPE", "TIME_DEFAULT", "CHARSET_INDEX", "DDL_IMPLICIT_COMMIT", "MANUAL_DATA_AUTHORIZATION"} <= features)
        self.assertEqual(3, next(f["line"] for f in found if f["feature"] == "MANUAL_DATA_AUTHORIZATION"))
        self.assertIn("INCOMPATIBLE_SYNTAX", {f["category"] for f in preflight.classify("COLLATE utf8mb4_0900_ai_ci")})
        self.assertEqual("UNCLASSIFIED", preflight.classify("/*!80000 SELECT 1 */")[0]["category"])

    def test_normalization_is_separate_manual_data_approval(self):
        result = preflight.review()
        findings = result["files"][preflight.SUPPLEMENTS[1]]["findings"]
        self.assertIn("MANUAL_SEPARATE_APPROVAL", {f["category"] for f in findings})
        order = result["approval_order_only"]
        self.assertIn("只读预检", order[0])
        self.assertIn("人工审查", order[1])
        self.assertIn("UPDATE", order[2])
        self.assertIn("独立批准", order[3])

    def test_no_network_database_or_process_execution(self):
        with patch.object(socket, "socket", side_effect=AssertionError("network forbidden")), \
                patch.object(subprocess, "Popen", side_effect=AssertionError("process forbidden")):
            self.assertTrue(preflight.review()["static_coverage_pass"])
        tree = ast.parse((ROOT / "scripts/mysql57_compat_preflight.py").read_text(encoding="utf-8"))
        imports = {n.names[0].name for n in ast.walk(tree) if isinstance(n, ast.Import)}
        imports |= {n.module for n in ast.walk(tree) if isinstance(n, ast.ImportFrom)}
        self.assertEqual({"argparse", "hashlib", "json", "pathlib", "re", "sys", "migration_preflight"}, imports)
        calls = {n.func.id for n in ast.walk(tree) if isinstance(n, ast.Call) and isinstance(n.func, ast.Name)}
        self.assertFalse({"exec", "eval", "compile", "__import__"} & calls)

    def test_cli_help_output_and_unsafe_options(self):
        script = ROOT / "scripts/mysql57_compat_preflight.py"
        def run(*args):
            return subprocess.run([sys.executable, str(script), *map(str, args)], capture_output=True, text=True)
        self.assertEqual(0, run("--help").returncode)
        self.assertEqual(0, run("--route", "init").returncode)
        self.assertEqual(2, run("--execute-sql").returncode)
        with tempfile.TemporaryDirectory(dir=ROOT / "scripts/deploy") as directory:
            output = Path(directory) / "review.json"
            process = run("--output", output)
            self.assertEqual(0, process.returncode, process.stderr)
            data = json.loads(output.read_text(encoding="utf-8"))
            self.assertFalse(data["production_ready"])
            original = output.read_bytes()
            self.assertEqual(1, run("--output", output).returncode)
            self.assertEqual(original, output.read_bytes())
            self.assertEqual(1, run("--output", Path(directory) / "report.sql").returncode)
        self.assertEqual(1, run("--output", ROOT / "scripts/not-allowed.json").returncode)
        self.assertFalse((ROOT / "scripts/not-allowed.json").exists())


if __name__ == "__main__":
    unittest.main()
