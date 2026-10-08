#!/usr/bin/env python3
"""离线数据库预检：只读源码，不连接数据库、不执行 SQL。"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

import audit_mapper_database_coverage as audit


def normalized(text):
    ddl = re.sub(r"\bIF\s+NOT\s+EXISTS\b", "", audit.clean_sql(text), flags=re.I)
    return re.sub(r"\s+", "", ddl).lower()


def paths():
    pairs = [("draft_ledger_entry", "sql/initsql/20260610_draft_ledger_entry.sql",
              "sql/updatesql/20260610/01_create_draft_ledger_entry.sql"),
             ("draft_lifecycle_event", "sql/initsql/20260928_draft_lifecycle_event.sql",
              "sql/updatesql/20260928/01_create_draft_lifecycle_event.sql")]
    for name, date in [("research_plan", "20261001"), ("risk_watch", "20261002"),
                       ("risk_alert_history", "20261002"), ("allocation_policy", "20261002"),
                       ("goal_tracking", "20261002"), ("monthly_budget", "20261002")]:
        pairs.append((name, f"backend/sql/initsql/{name}.sql", f"backend/migrations/{date}_{name}.sql"))
    return pairs


SUPPLEMENTS = [
    "sql/updatesql/20260927/01_precheck_draft_ledger_source_duplicates.sql",
    "sql/updatesql/20260927/02_normalize_blank_draft_ledger_source_ref.sql",
    "sql/updatesql/20260927/03_add_draft_ledger_source_unique_keys.sql",
    "sql/updatesql/20260929/01_settlement_audit_link.sql",
]


def check(repo, route="init", selected=None):
    repo = Path(repo).resolve()
    files = audit.list_files(repo, "WORKTREE")
    xml = audit.mapper_xml_inventory(repo, "WORKTREE", files)
    annotations = audit.mapper_annotation_inventory(repo, "WORKTREE", files)
    sql = audit.script_inventory(repo, "WORKTREE", files)
    matrix, summary = audit.build_coverage(xml, annotations, sql, set(),
                                         audit.inventory_object_names(xml + annotations))
    errors, warnings, pairs = [], [], []
    # 所有 SQL 都检查 UTF-8，且索引/约束引用保留文件与行号；不输出 SQL 正文或凭据。
    indexes = []
    for item in sql:
        path = item["脚本文件"]
        try:
            text = (repo / path).read_bytes().decode("utf-8-sig")
            if "\ufffd" in text:
                errors.append(f"编码含替换字符: {path}")
            for number, line in enumerate(text.splitlines(), 1):
                clean = audit.clean_sql(line)
                for match in re.finditer(r"\b(?:INDEX|KEY|CONSTRAINT)\s+`?([a-zA-Z_]\w*)", clean, re.I):
                    indexes.append({"file": path, "line": number, "name": match[1]})
        except UnicodeError:
            errors.append(f"非 UTF-8 SQL: {path}")
    for name, init, migration in paths():
        exists = all((repo / p).is_file() for p in (init, migration))
        try:
            equal = exists and normalized((repo / init).read_text(encoding="utf-8-sig")) == normalized(
                (repo / migration).read_text(encoding="utf-8-sig"))
        except UnicodeError:
            equal = False
        pairs.append({"object_group": name, "init": init, "migration": migration,
                      "scripts_exist": exists, "structure_equal": equal,
                      "repeat_risk": "IF NOT EXISTS 仅跳过建表，不验证既有结构；生命周期增量无保护，重跑报错" if name.startswith("draft_") else "CREATE TABLE 重跑报错；必须择一路径"})
        if not exists or not equal:
            errors.append(f"双路径缺失或结构不一致: {name}")
    plan = selected if selected is not None else (["sql/initsql/DDL.sql"] if route == "init" else []) + [p[1 if route == "init" else 2] for p in paths()] + SUPPLEMENTS
    if len(plan) != len(set(plan)):
        errors.append("计划包含重复脚本")
    for pair in pairs:
        if pair["init"] in plan and pair["migration"] in plan:
            errors.append(f"双路径重复建表: {pair['object_group']}")
        if not any(p in plan for p in (pair["init"], pair["migration"])):
            errors.append(f"计划缺少对象路径: {pair['object_group']}")
    for p in SUPPLEMENTS:
        if p not in plan:
            errors.append(f"计划缺少必要补充步骤: {p}")
    for p in plan:
        if Path(p).is_absolute() or ".." in Path(p).parts or not p.endswith(".sql"):
            errors.append(f"非法计划路径: {p}")
        if not (repo / p).is_file():
            errors.append(f"计划脚本不存在: {p}")
    if all(p in plan for p in SUPPLEMENTS):
        positions = [plan.index(p) for p in SUPPLEMENTS[:3]]
        if positions != sorted(positions):
            errors.append("强幂等步骤顺序错误")
    # ALTER 依赖：草稿先创建，结算字段依赖主 DDL 或经部署方核验的既有 schema。
    for name, init, migration in paths()[:2]:
        chosen = next((p for p in (init, migration) if p in plan), None)
        if name == "draft_ledger_entry" and chosen and SUPPLEMENTS[0] in plan and plan.index(chosen) > plan.index(SUPPLEMENTS[0]):
            errors.append("草稿建表必须早于强幂等预检")
    for row in matrix:
        if not row["脚本覆盖"] or not row["通用初始化建表覆盖"]:
            errors.append(f"缺失对象/通用初始化: {row['数据库对象']}")
    if summary["未完整解析Mapper数"] or summary["动态表名引用数"] or not summary["数量闭环"]:
        errors.append("Mapper 存在未解析或动态对象，必须人工复核")
    warnings.extend([
        "v0.15 回测历史没有 SQL；持久化目录权限、损坏记录及备份待部署方验证",
        "v0.14 settlement_confirm 新字段依赖 20260929 ALTER；重复执行报重复字段错误，不可把错误当成功",
        "v0.8 唯一索引重复执行报错；01 必须无重复，02 改写数据仅由部署方另行授权执行",
        "旧版更新含历史财务修复/示例 DML：完整清单只用于核对，不是可批量执行计划",
        "主 DDL 与全部历史 ALTER 的字段/约束兼容性未做数据库语义验证",
        "MySQL DDL 隐式提交，不能以事务 ROLLBACK 保证撤销；无一次性测试库演练证据",
    ])
    historical = repo / "docs/v020_database_coverage.json"
    return {"evidence": {"scripts_exist": not any(not p["scripts_exist"] for p in pairs),
                         "static_coverage_pass": not errors, "disposable_database_rehearsal": "NOT_RUN",
                         "target_manual_deployment": "待部署方验证"},
            "historical_v020_sha256": hashlib.sha256(historical.read_bytes()).hexdigest(),
            "summary": summary, "mappers": xml + annotations, "sql_scripts": sql,
            "object_matrix": matrix, "indexes_constraints": indexes, "path_pairs": pairs,
            "review_order_only_not_executable": plan, "errors": errors, "unknowns_warnings": warnings,
            "route": route,
            "dependencies": ["基础 DDL/既有 schema → draft → 强幂等 01/02/03 → lifecycle",
                             "基础 settlement_confirm → 20260929 字段 ALTER",
                             "risk_watch_rule/snapshot → risk_watch_event/mute（逻辑依赖，无 FK）",
                             "research/allocation/goal/budget 依赖既有 owner/family 隔离数据语义"],
            "manual_checks": ["核验 MySQL 8 版本、CHECK/JSON、权限、备份和恢复方案",
                              "用脱敏 SHOW CREATE TABLE/索引证据逐项比对字段类型、空值、约束和唯一键",
                              "记录每个脚本版本/hash、执行结果；确认未知项后由人工批准部署"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--route", choices=["init", "migration"], default="init")
    parser.add_argument("--plan", type=Path, help="JSON 字符串数组；仅验证，不执行")
    parser.add_argument("--output", type=Path, help="仅允许输出到仓库 scripts 目录")
    args = parser.parse_args()
    try:
        plan = json.loads(args.plan.read_text(encoding="utf-8-sig")) if args.plan else None
        if plan is not None and (not isinstance(plan, list) or not all(isinstance(p, str) for p in plan)):
            raise ValueError("plan 必须为字符串数组")
        result = check(args.repo, args.route, plan)
        data = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
        if args.output:
            output = args.output.resolve()
            output.relative_to((args.repo / "scripts").resolve())
            output.write_text(data, encoding="utf-8")
        print(json.dumps(result["evidence"], ensure_ascii=True))
        for error in result["errors"]:
            print(error)
        return 2 if result["errors"] else 0
    except (OSError, ValueError, audit.AuditError) as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
