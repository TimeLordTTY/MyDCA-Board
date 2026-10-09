#!/usr/bin/env python3
"""MySQL 5.7 源码文本审查；不连接数据库、不执行 SQL、不生成迁移批处理。"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

from migration_preflight import SUPPLEMENTS, normalized, paths

ROOT = Path(__file__).resolve().parent.parent
GROUPS = {
    "draft_lifecycle_event": ["draft_lifecycle_event"],
    "research_plan": ["research_plan"],
    "risk_watch": ["risk_watch_rule", "risk_watch_snapshot"],
    "risk_alert_history": ["risk_watch_event", "risk_watch_mute"],
    "allocation_policy": ["allocation_policy"],
    "goal_tracking": ["goal_tracking"],
    "monthly_budget": ["monthly_budget"],
}
COLUMNS = ["preview_digest", "ledger_txn_id"]
INDEXES = ["uk_draft_ledger_user_source", "uk_draft_ledger_family_source"]
DEPENDENCIES = {
    "draft_lifecycle_event": "既有 draft_ledger_entry；v0.8 独立审批链先完成；逻辑依赖无 FK",
    "risk_alert_history": "risk_watch_rule/snapshot → event/mute；逻辑依赖无 FK",
}
MANUAL_SCHEMA = [
    "脱敏 SHOW CREATE TABLE 与逐列类型、空值、默认值、主键/唯一键列顺序；脚本执行记录",
    "owner/family 隔离语义、实际默认引擎/字符集/排序规则；JSON_VALID 与应用层非法 payload 拒绝证据",
    "sql_mode、explicit_defaults_for_timestamp、时区、TIMESTAMP(6) 精度/默认值",
    "innodb_large_prefix、row_format、file_format、page_size 与实际索引字节长度",
    "DDL 权限、锁表窗口、备份校验和独立恢复演练；事务 ROLLBACK 无法撤销 DDL",
]


def uncomment(text):
    # Limited lexical scanner: preserve line positions, skip strings/backticks and
    # ordinary comments. Versioned executable comments are explicitly unclassified.
    pattern = r"'(?:''|\\.|[^'\\])*'|\"(?:\"\"|\\.|[^\"\\])*\"|`[^`]*`|/\*.*?\*/|--[^\r\n]*|#[^\r\n]*"
    def replace(match):
        value = match[0]
        if value.startswith("`"):
            return value[1:-1]
        return re.sub(r"[^\r\n]", " ", value)
    return re.sub(pattern, replace, text, flags=re.S)


RULES = [
    ("NAMED_CHECK", r"\bCONSTRAINT\s+\w+\s+CHECK\s*\(", "MYSQL8_FEATURE",
     "5.7 文档的有限 CHECK 语法不含 CONSTRAINT 名称；不能保证此原文可解析；具体脚本引擎实测 NOT_RUN"),
    ("CHECK_NOT_ENFORCED", r"\bCHECK\s*\(", "INCOMPATIBLE_SEMANTICS",
     "5.7 即使接受有限 CHECK 语法也忽略约束；不能保证 JSON_VALID/status 强制执行。8.0.16+ 才支持 CHECK 强制语义"),
    ("JSON_VALID", r"\bJSON_VALID\s*\(", "POSSIBLY_COMPATIBLE",
     "5.7 有 JSON_VALID 函数；函数存在不意味着 LONGTEXT + CHECK 得到自动 JSON 验证"),
    ("JSON_TYPE", r"\bJSON\b", "POSSIBLY_COMPATIBLE",
     "原生 JSON 自 5.7.8 支持并验证输入；不得与 LONGTEXT 等同；JSON 规范化/重复 key 与 8 不同"),
    ("TIME_DEFAULT", r"\b(?:TIMESTAMP|DATETIME|CURRENT_TIMESTAMP)\b", "POSSIBLY_COMPATIBLE",
     "5.7 支持 0..6 位精度；核查 sql_mode、默认值/NULL、explicit_defaults_for_timestamp、时区与范围"),
    ("CHARSET_INDEX", r"\b(?:CHARACTER\s+SET|COLLATE|INDEX|KEY)\b", "UNCLASSIFIED",
     "既有/继承字符集与排序规则、索引长度及 row_format 未核实；767/3072 字节受 5.7 设置影响"),
    ("DDL_IMPLICIT_COMMIT", r"\b(?:CREATE|ALTER|DROP)\s+TABLE\b", "INCOMPATIBLE_RECOVERY_ASSUMPTION",
     "DDL 隐式提交；多个 ALTER 可部分成功；不能用自动事务 rollback 保证恢复"),
    ("MANUAL_DATA_AUTHORIZATION", r"\b(?:UPDATE\s+\w+\s+SET|INSERT\s+INTO|DELETE\s+FROM)\b", "MANUAL_SEPARATE_APPROVAL",
     "真实数据修改必须独立人工授权；本工具只读文件文本，绝不执行"),
    ("MYSQL8_ONLY", r"\butf8mb4_0900_\w+\b|\bJSON_TABLE\s*\(|\b(?:DENSE_RANK|ROW_NUMBER)\s*\(|\b(?:NOT\s+)?ENFORCED\b", "INCOMPATIBLE_SYNTAX",
     "检测到已知 8.x 专属排序规则/功能；5.7 不支持；静态扫描并非完整语法判定"),
]


def classify(text):
    code = uncomment(text)
    findings = []
    for feature, pattern, category, note in RULES:
        for match in re.finditer(pattern, code, flags=re.I):
            findings.append({"feature": feature, "category": category,
                             "line": code.count("\n", 0, match.start()) + 1, "note": note})
    if "/*!" in text:
        findings.append({"feature": "EXECUTABLE_COMMENT", "category": "UNCLASSIFIED", "line": 1,
                         "note": "版本条件注释可能含可执行 SQL；需要人工解析"})
    return findings


def review(repo=ROOT, route="migration", selected=None):
    if route not in ("init", "migration"):
        raise ValueError("invalid route")
    repo = Path(repo)
    pairs = paths()
    chosen = selected if selected is not None else [p[1 if route == "init" else 2] for p in pairs]
    errors, groups, files = [], [], {}
    permitted = {p for _, init, migration in pairs for p in (init, migration)}
    if len(chosen) != len(set(chosen)) or any(p not in permitted for p in chosen):
        errors.append("重复或不在固定双路径清单内的文件")
    for group, init, migration in pairs:
        if sum(p in chosen for p in (init, migration)) != 1:
            errors.append(f"同组必须且只能选择一路: {group}")
        texts = {}
        for path in (init, migration):
            text = (repo / path).read_text(encoding="utf-8-sig")
            texts[path] = text
            files[path] = {"sha256": hashlib.sha256((repo / path).read_bytes()).hexdigest(),
                           "findings": classify(text)}
        if normalized(texts[init]) != normalized(texts[migration]):
            errors.append(f"双路径结构漂移: {group}")
        missing = GROUPS.get(group, [])
        table_evidence = {}
        for table in missing:
            table_evidence[table] = {}
            for path, text in texts.items():
                code = uncomment(text)
                match = re.search(rf"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?{table}\b", code, re.I)
                table_evidence[table][path] = code.count("\n", 0, match.start()) + 1 if match else None
            if not all(re.search(rf"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?{table}\b", uncomment(text), re.I)
                       for text in texts.values()):
                errors.append(f"缺失表脚本覆盖: {table}")
        groups.append({"group": group, "missing_tables": missing, "table_evidence": table_evidence, "init": init, "migration": migration,
                       "selected_for_review_only": next((p for p in (init, migration) if p in chosen), None),
                       "risk": "HIGH", "depends_on": DEPENDENCIES.get(group, "既有 owner/family 与基础 schema 人工核验"),
                       "repeatability": "IF NOT EXISTS 仅跳过，不验证结构" if group == "draft_ledger_entry" else
                           ("init 可跳过；migration 重跑报错" if group == "draft_lifecycle_event" else "CREATE TABLE 重跑报错"),
                       "manual_checks": MANUAL_SCHEMA,
                       "execution_blocker": "生产缺口/其余 schema 未完整核验；5.7 引擎 NOT_RUN；缺迁移授权和恢复证据"})
    for path in SUPPLEMENTS:
        data = (repo / path).read_bytes()
        files[path] = {"sha256": hashlib.sha256(data).hexdigest(),
                       "findings": classify(data.decode("utf-8-sig"))}
    coverage = []
    for kind, names, path, prefix in [("column", COLUMNS, SUPPLEMENTS[3], r"ADD\s+COLUMN"),
                                      ("unique_index", INDEXES, SUPPLEMENTS[2], r"ADD\s+UNIQUE\s+KEY")]:
        code = uncomment((repo / path).read_text(encoding="utf-8-sig"))
        for name in names:
            match = re.search(rf"\b{prefix}\s+{name}\b", code, re.I)
            if not match:
                errors.append(f"缺失对象脚本覆盖: {name}")
            coverage.append({"kind": kind, "table": "settlement_confirm" if kind == "column" else "draft_ledger_entry",
                             "object": name, "file": path, "line": code.count("\n", 0, match.start()) + 1 if match else None,
                             "risk": "HIGH", "repeatability": "重复 ADD 报错；错误不能当已应用，需核对实际定义",
                             "depends_on": "既有 settlement_confirm 逐列核验" if kind == "column" else
                                 "draft 已存在 → v0.8 01 只读预检 → 人工决定 → 02 独立数据授权 → 03 独立 DDL 授权",
                             "manual_checks": MANUAL_SCHEMA + (["user/family 非 NULL 来源重复、空 source_ref、排序规则等价与并发写入；不可静默删行"] if kind == "unique_index" else []),
                             "execution_blocker": "缺目标环境升级/迁移授权；实际字段/索引与数据未核实"})
    return {"status": ["STATIC_REVIEW", "ENGINE_NOT_TESTED", "PRODUCTION_SCHEMA_BLOCKED"],
            "static_coverage_pass": not errors, "engine_test": "NOT_RUN", "production_ready": False,
            "source_of_gap_counts": "owner-approved task 20261009；本工具未独立访问目标环境",
            "counts": {"missing_tables": sum(len(g["missing_tables"]) for g in groups),
                       "missing_columns": len(COLUMNS), "missing_unique_indexes": len(INDEXES)},
            "route": route, "groups": groups, "supplement_objects": coverage, "files": files,
            "approval_order_only": ["v0.8 01 只读预检（本任务不执行）", "人工审查重复/空来源与处理方案",
                                    "独立批准 02 normalization（UPDATE 同时改 updated_at）",
                                    "复核预检/并发窗口后独立批准 03 两个唯一索引"],
            "limitations": ["有限规则扫描非 SQL parser；未分类部分不能推断兼容；不全量验证其余生产对象",
                            "无 SQL 原文/凭据/业务数据输出，无数据库/网络/远端执行入口",
                            "核对清单不可执行；不批量选择历史 updatesql；DDL 不具备事务回退保障"],
            "errors": errors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--route", choices=["init", "migration"], default="migration")
    parser.add_argument("--output", type=Path, help="仅新建 scripts/deploy 内 .json 本地脱敏报告；不覆盖文件")
    args = parser.parse_args()
    try:
        output = args.output.resolve() if args.output else None
        if output:
            output.relative_to((ROOT / "scripts/deploy").resolve())
            if output.suffix != ".json":
                raise ValueError("output must be .json")
        result = review(route=args.route)
        if output:
            with output.open("x", encoding="utf-8", newline="\n") as report:
                json.dump(result, report, ensure_ascii=False, indent=2)
                report.write("\n")
        print(json.dumps({key: result[key] for key in ("status", "counts", "static_coverage_pass", "production_ready")}, ensure_ascii=True))
        return 2 if result["errors"] else 0
    except (OSError, ValueError) as exc:
        # Avoid reflecting arbitrary paths or SQL text from input errors.
        print(f"Local report failed: {type(exc).__name__}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
