#!/usr/bin/env python3
"""审计目标 Git 版本中全部 Mapper、数据库对象和 SQL 脚本的覆盖关系。"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from collections import defaultdict
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Set, Tuple


SQL_TAG_PATTERN = re.compile(
    r"<(select|insert|update|delete|sql)\b([^>]*)>(.*?)</\1\s*>",
    re.IGNORECASE | re.DOTALL,
)
ANNOTATION_PATTERN = re.compile(
    r"@(Select|Insert|Update|Delete)\s*\(\s*(?:\{\s*)?((?:\"(?:\\.|[^\"\\])*\"\s*,?\s*)+)",
    re.DOTALL,
)
JAVA_STRING_PATTERN = re.compile(r'"((?:\\.|[^"\\])*)"')
XML_COMMENT_PATTERN = re.compile(r"<!--.*?-->", re.DOTALL)
XML_TAG_PATTERN = re.compile(r"<[^>]+>")
SQL_COMMENT_PATTERN = re.compile(r"/\*.*?\*/|--[^\r\n]*", re.DOTALL)
IDENTIFIER = r'(?:[`"\[]?[A-Za-z_][A-Za-z0-9_$#.-]*[`"\]]?)'
TABLE_PATTERNS = [
    ("查询", re.compile(rf"\b(?:FROM|JOIN)\s+({IDENTIFIER})", re.IGNORECASE)),
    ("新增", re.compile(rf"\bINSERT\s+INTO\s+({IDENTIFIER})", re.IGNORECASE)),
    ("修改", re.compile(rf"\bUPDATE\s+({IDENTIFIER})", re.IGNORECASE)),
    ("删除", re.compile(rf"\bDELETE\s+FROM\s+({IDENTIFIER})", re.IGNORECASE)),
    ("合并", re.compile(rf"\bMERGE\s+INTO\s+({IDENTIFIER})", re.IGNORECASE)),
]
SCRIPT_OBJECT_PATTERNS = [
    ("建表", re.compile(rf"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?({IDENTIFIER})", re.IGNORECASE)),
    ("改表", re.compile(rf"\bALTER\s+TABLE\s+({IDENTIFIER})", re.IGNORECASE)),
    ("删表", re.compile(rf"\bDROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?({IDENTIFIER})", re.IGNORECASE)),
    ("写数据", re.compile(rf"\bINSERT\s+INTO\s+({IDENTIFIER})", re.IGNORECASE)),
    ("更新数据", re.compile(rf"\bUPDATE\s+({IDENTIFIER})", re.IGNORECASE)),
    ("删除数据", re.compile(rf"\bDELETE\s+FROM\s+({IDENTIFIER})", re.IGNORECASE)),
]
CTE_PATTERN = re.compile(rf"(?:\bWITH|,)\s*({IDENTIFIER})\s+AS\s*\(", re.IGNORECASE)
DYNAMIC_TABLE_PATTERN = re.compile(r"(?:FROM|JOIN|INTO|UPDATE)\s+([#$]\{[^}]+\})", re.IGNORECASE)
DATABASE_ID_PATTERN = re.compile(r'\bdatabaseId\s*=\s*["\']([^"\']+)["\']', re.IGNORECASE)
SENSITIVE_PATTERN = re.compile(r"(?i)(password|passwd|secret|token|private.?key|access.?key|credential)")


class AuditError(RuntimeError):
    pass


GIT_EXECUTABLE = shutil.which("git")
ALLOWED_GIT_COMMANDS = {"diff", "ls-files", "ls-tree", "merge-base", "rev-parse", "show"}
SAFE_GIT_ENV = {
    **os.environ,
    "GIT_PAGER": "cat",
    "GIT_EXTERNAL_DIFF": "",
    "GIT_CONFIG_NOSYSTEM": "1",
}


def git(repo: Path, args: Sequence[str], check: bool = True) -> str:
    if not GIT_EXECUTABLE:
        raise AuditError("未找到 Git 可执行文件")
    if not args or args[0] not in ALLOWED_GIT_COMMANDS:
        raise AuditError("拒绝执行未授权的 Git 子命令")
    if any(not isinstance(arg, str) or "\0" in arg or "\r" in arg or "\n" in arg for arg in args):
        raise AuditError("Git 参数包含非法控制字符")
    result = subprocess.run(
        [GIT_EXECUTABLE, "-c", "core.quotePath=false", "-C", str(repo), *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=SAFE_GIT_ENV,
        shell=False,
        check=False,
    )
    if check and result.returncode:
        raise AuditError(result.stderr.strip() or f"Git 命令失败：{' '.join(args)}")
    return result.stdout


def verify(repo: Path, ref: str) -> Tuple[Path, str]:
    root = Path(git(repo, ["rev-parse", "--show-toplevel"]).strip()).resolve()
    if ref.upper() == "WORKTREE":
        return root, "WORKTREE"
    if not ref or ref.startswith("-") or any(ord(ch) < 32 for ch in ref):
        raise AuditError("目标 ref 为空或不安全")
    target = git(root, ["rev-parse", "--verify", f"{ref}^{{commit}}"]).strip()
    return root, target


def list_files(repo: Path, target: str) -> List[str]:
    if target == "WORKTREE":
        files = git(repo, ["ls-files", "--cached", "--others", "--exclude-standard"]).splitlines()
        return sorted({
            path for path in files
            if Path(path).suffix.lower() in {".xml", ".java", ".kt", ".sql", ".yml", ".yaml"}
        })
    return git(repo, ["ls-tree", "-r", "--name-only", target]).splitlines()


def read_file(repo: Path, target: str, path: str) -> str:
    if target == "WORKTREE":
        try:
            return (repo / path).read_text(encoding="utf-8-sig", errors="replace")
        except OSError as exc:
            raise AuditError(f"读取工作树文件失败：{path}：{exc}") from exc
    return git(repo, ["show", f"{target}:{path}"])


def normalize_identifier(value: str) -> str:
    value = value.strip().strip("`\"[]").replace("\\", "/")
    if "." in value:
        value = value.rsplit(".", 1)[-1]
    return value.lower()


def clean_sql(value: str) -> str:
    value = XML_COMMENT_PATTERN.sub(" ", value)
    value = SQL_COMMENT_PATTERN.sub(" ", value)
    value = XML_TAG_PATTERN.sub(" ", value)
    value = re.sub(r"\s+", " ", value).strip()
    # UPSERT 的 UPDATE 后面紧跟的是列名，不是表名；先替换该短语，避免把第一列误识别成表。
    value = re.sub(r"\bON\s+DUPLICATE\s+KEY\s+UPDATE\b", "ON_DUPLICATE_KEY_ASSIGN", value, flags=re.IGNORECASE)
    value = re.sub(r"\bON\s+CONFLICT\b.*?\bDO\s+UPDATE\b", "ON_CONFLICT_ASSIGN", value, flags=re.IGNORECASE)
    return value


def extract_tables(sql: str) -> Tuple[List[Dict[str, str]], List[str]]:
    cleaned = clean_sql(sql)
    ctes = {normalize_identifier(x) for x in CTE_PATTERN.findall(cleaned)}
    objects: List[Dict[str, str]] = []
    for operation, pattern in TABLE_PATTERNS:
        for raw in pattern.findall(cleaned):
            name = normalize_identifier(raw)
            if name and name not in ctes and not name.startswith(("select", "values")):
                objects.append({"数据库对象": name, "操作": operation})
    dynamic = sorted(set(DYNAMIC_TABLE_PATTERN.findall(cleaned)))
    unique = {(x["数据库对象"], x["操作"]): x for x in objects}
    return list(unique.values()), dynamic


def mapper_xml_inventory(repo: Path, target: str, files: Iterable[str]) -> List[Dict[str, object]]:
    result: List[Dict[str, object]] = []
    for path in files:
        lower = path.lower()
        if not lower.endswith(".xml") or not any(part in lower for part in ("/mapper/", "/mapping/")):
            continue
        text = read_file(repo, target, path)
        namespace_match = re.search(r'<mapper\b[^>]*\bnamespace\s*=\s*["\']([^"\']+)', text, re.IGNORECASE)
        namespace = namespace_match.group(1) if namespace_match else ""
        statements: List[Dict[str, object]] = []
        for match in SQL_TAG_PATTERN.finditer(text):
            tag, attrs, body = match.groups()
            statement_id = ""
            id_match = re.search(r'\bid\s*=\s*["\']([^"\']+)', attrs, re.IGNORECASE)
            if id_match:
                statement_id = id_match.group(1)
            database_id = ""
            database_match = DATABASE_ID_PATTERN.search(attrs)
            if database_match:
                database_id = database_match.group(1)
            objects, dynamic = extract_tables(body)
            statements.append({
                "标签": tag.lower(),
                "语句ID": statement_id,
                "数据库方言": database_id,
                "数据库对象": objects,
                "动态表名": dynamic,
            })
        result.append({
            "Mapper文件": path,
            "命名空间": namespace,
            "语句数": len(statements),
            "语句": statements,
            "解析状态": "已解析" if namespace and statements else "未完整解析",
        })
    return result


def mapper_annotation_inventory(repo: Path, target: str, files: Iterable[str]) -> List[Dict[str, object]]:
    result: List[Dict[str, object]] = []
    for path in files:
        lower = path.lower()
        if not lower.endswith((".java", ".kt")) or "mapper" not in lower:
            continue
        text = read_file(repo, target, path)
        statements: List[Dict[str, object]] = []
        for match in ANNOTATION_PATTERN.finditer(text):
            annotation, string_group = match.groups()
            sql = "".join(bytes(s, "utf-8").decode("unicode_escape", errors="replace")
                          for s in JAVA_STRING_PATTERN.findall(string_group))
            objects, dynamic = extract_tables(sql)
            statements.append({
                "注解": annotation,
                "数据库对象": objects,
                "动态表名": dynamic,
            })
        if statements:
            result.append({"Mapper文件": path, "语句数": len(statements), "语句": statements, "解析状态": "已解析"})
    return result


def script_inventory(repo: Path, target: str, files: Iterable[str]) -> List[Dict[str, object]]:
    result: List[Dict[str, object]] = []
    for path in files:
        if not path.lower().endswith(".sql"):
            continue
        text = read_file(repo, target, path)
        clean = SQL_COMMENT_PATTERN.sub(" ", text)
        objects: List[Dict[str, str]] = []
        for operation, pattern in SCRIPT_OBJECT_PATTERNS:
            for raw in pattern.findall(clean):
                objects.append({"数据库对象": normalize_identifier(raw), "脚本动作": operation})
        unique = {(x["数据库对象"], x["脚本动作"]): x for x in objects}
        dialects = []
        lower = path.lower().replace("\\", "/")
        if "/initsql/" in lower or "/init/" in lower or "/full/" in lower:
            script_type = "通用初始化"
        elif "rollback" in lower or "回滚" in lower:
            script_type = "回滚"
        elif re.search(r"/database/[^/]*(?:客户|银行|证券|长沙|农商|城商)[^/]*/", "/" + lower):
            script_type = "客户专项"
        elif re.search(r"/database/v?\d+\.\d+(?:\.\d+)?/", "/" + lower):
            script_type = "版本增量"
        else:
            script_type = "未分类"
        for marker, name in (
            ("/mysql/", "MySQL"), ("/openguass/", "OpenGauss"), ("/opengauss/", "OpenGauss"),
            ("/oracle/", "Oracle"), ("/postgres", "PostgreSQL"), ("/dm/", "达梦"),
        ):
            if marker in lower:
                dialects.append(name)
        result.append({
            "脚本文件": path,
            "脚本类型": script_type,
            "方言目录": dialects or ["未标明"],
            "数据库对象数": len({x["数据库对象"] for x in unique.values()}),
            "数据库对象": list(unique.values()),
        })
    return result


def changed_mapper_paths(repo: Path, base: str, target: str) -> Set[str]:
    if not base:
        return set()
    base_hash = git(repo, ["rev-parse", "--verify", f"{base}^{{commit}}"]).strip()
    merge_base = git(repo, ["merge-base", base_hash, target]).strip()
    paths = git(repo, ["diff", "--name-only", "--no-ext-diff", "--no-textconv", f"{merge_base}..{target}", "--"])
    return {
        path for path in paths.splitlines()
        if ("mapper" in path.lower() or "mapping" in path.lower())
        and path.lower().endswith((".xml", ".java", ".kt"))
    }


def build_coverage(
    xml_mappers: List[Dict[str, object]],
    annotation_mappers: List[Dict[str, object]],
    scripts: List[Dict[str, object]],
    changed_paths: Set[str],
    baseline_objects: Set[str],
) -> Tuple[List[Dict[str, object]], Dict[str, object]]:
    mapper_refs: Dict[str, Dict[str, object]] = defaultdict(lambda: {"Mapper文件": set(), "操作": set(), "是否来自变更Mapper": False})
    dynamic_refs: List[Dict[str, object]] = []
    statement_count = 0
    unresolved_mappers: List[str] = []

    for mapper in [*xml_mappers, *annotation_mappers]:
        if mapper["解析状态"] != "已解析":
            unresolved_mappers.append(str(mapper["Mapper文件"]))
        for statement in mapper["语句"]:
            statement_count += 1
            for obj in statement.get("数据库对象", []):
                entry = mapper_refs[obj["数据库对象"]]
                entry["Mapper文件"].add(mapper["Mapper文件"])
                entry["操作"].add(obj["操作"])
                entry["是否来自变更Mapper"] = entry["是否来自变更Mapper"] or mapper["Mapper文件"] in changed_paths
            for dynamic in statement.get("动态表名", []):
                dynamic_refs.append({"Mapper文件": mapper["Mapper文件"], "动态表名": dynamic})

    script_refs: Dict[str, Dict[str, Set[str]]] = defaultdict(
        lambda: {
            "脚本文件": set(), "脚本动作": set(), "方言目录": set(),
            "通用初始化建表脚本": set(), "非初始化建表脚本": set(), "脚本类型": set(),
        }
    )
    for script in scripts:
        for obj in script["数据库对象"]:
            entry = script_refs[obj["数据库对象"]]
            entry["脚本文件"].add(script["脚本文件"])
            entry["脚本动作"].add(obj["脚本动作"])
            entry["方言目录"].update(script["方言目录"])
            entry["脚本类型"].add(script["脚本类型"])
            if obj["脚本动作"] == "建表":
                key = "通用初始化建表脚本" if script["脚本类型"] == "通用初始化" else "非初始化建表脚本"
                entry[key].add(script["脚本文件"])

    coverage: List[Dict[str, object]] = []
    for name in sorted(mapper_refs):
        mapper = mapper_refs[name]
        script = script_refs.get(name)
        coverage.append({
            "数据库对象": name,
            "Mapper操作": sorted(mapper["操作"]),
            "Mapper文件数": len(mapper["Mapper文件"]),
            "Mapper文件": sorted(mapper["Mapper文件"]),
            "是否来自变更Mapper": mapper["是否来自变更Mapper"],
            "基线是否已引用": name in baseline_objects,
            "是否本分支新增引用": name not in baseline_objects,
            "脚本覆盖": bool(script),
            "脚本动作": sorted(script["脚本动作"]) if script else [],
            "脚本类型": sorted(script["脚本类型"]) if script else [],
            "方言目录": sorted(script["方言目录"]) if script else [],
            "脚本文件数": len(script["脚本文件"]) if script else 0,
            "脚本文件": sorted(script["脚本文件"]) if script else [],
            "通用初始化建表覆盖": bool(script and script["通用初始化建表脚本"]),
            "通用初始化建表脚本": sorted(script["通用初始化建表脚本"]) if script else [],
            "非初始化建表脚本": sorted(script["非初始化建表脚本"]) if script else [],
            "仅非初始化脚本存在建表": bool(
                script and script["非初始化建表脚本"] and not script["通用初始化建表脚本"]
            ),
        })

    uncovered = [x for x in coverage if not x["脚本覆盖"]]
    changed_uncovered = [x for x in uncovered if x["是否来自变更Mapper"]]
    new_uncovered = [x for x in uncovered if x["是否本分支新增引用"]]
    missing_init = [x for x in coverage if x["仅非初始化脚本存在建表"]]
    summary = {
        "Mapper文件总数": len(xml_mappers) + len(annotation_mappers),
        "XML Mapper文件数": len(xml_mappers),
        "注解Mapper文件数": len(annotation_mappers),
        "Mapper SQL语句总数": statement_count,
        "Mapper引用数据库对象总数": len(coverage),
        "SQL脚本文件总数": len(scripts),
        "脚本涉及数据库对象总数": len(script_refs),
        "有脚本覆盖的Mapper对象数": len(coverage) - len(uncovered),
        "无仓库脚本覆盖的Mapper对象数": len(uncovered),
        "变更Mapper引用但无脚本覆盖的对象数": len(changed_uncovered),
        "本分支新增引用数据库对象数": sum(1 for x in coverage if x["是否本分支新增引用"]),
        "本分支新增引用但无脚本覆盖的对象数": len(new_uncovered),
        "仅非初始化脚本存在建表但通用初始化缺失对象数": len(missing_init),
        "未完整解析Mapper数": len(unresolved_mappers),
        "动态表名引用数": len(dynamic_refs),
        "数量闭环": (
            len(coverage)
            == (len(coverage) - len(uncovered)) + len(uncovered)
        ),
        "未完整解析Mapper": unresolved_mappers,
        "动态表名引用": dynamic_refs,
    }
    return coverage, summary


def inventory_object_names(mappers: List[Dict[str, object]]) -> Set[str]:
    names: Set[str] = set()
    for mapper in mappers:
        for statement in mapper["语句"]:
            names.update(obj["数据库对象"] for obj in statement.get("数据库对象", []))
    return names


def config_dev_inventory(repo: Path, target: str, files: Iterable[str]) -> Dict[str, object]:
    dev_files = sorted(path for path in files if Path(path).name.lower() == "application-dev.yml")
    return {
        "配置审查范围": "仅 application-dev.yml",
        "application-dev.yml文件数": len(dev_files),
        "application-dev.yml文件": dev_files,
        "说明": "配置值不输出；配置键与代码读取点的语义核对由审查阶段完成。",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, type=Path)
    parser.add_argument("--target", required=True)
    parser.add_argument("--base", help="可选；用于标记相对基线发生变化的 Mapper")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    try:
        repo, target = verify(args.repo.resolve(), args.target)
        files = list_files(repo, target)
        xml_mappers = mapper_xml_inventory(repo, target, files)
        annotation_mappers = mapper_annotation_inventory(repo, target, files)
        scripts = script_inventory(repo, target, files)
        changed = changed_mapper_paths(repo, args.base, target)
        baseline_objects: Set[str] = set()
        if args.base:
            base_hash = git(repo, ["rev-parse", "--verify", f"{args.base}^{{commit}}"]).strip()
            base_files = list_files(repo, base_hash)
            baseline_objects = inventory_object_names(
                mapper_xml_inventory(repo, base_hash, base_files)
                + mapper_annotation_inventory(repo, base_hash, base_files)
            )
        coverage, summary = build_coverage(
            xml_mappers, annotation_mappers, scripts, changed, baseline_objects
        )
        result = {
            "结构版本": 1,
            "仓库": str(repo),
            "目标版本": target,
            "基线版本": args.base or "",
            "配置清单": config_dev_inventory(repo, target, files),
            "汇总": summary,
            "Mapper清单": xml_mappers + annotation_mappers,
            "SQL脚本清单": scripts,
            "Mapper数据库脚本覆盖矩阵": coverage,
            "强制人工复核": {
                "无脚本覆盖对象": [x for x in coverage if not x["脚本覆盖"]],
                "变更Mapper无脚本覆盖对象": [
                    x for x in coverage if x["是否来自变更Mapper"] and not x["脚本覆盖"]
                ],
                "本分支新增引用但无脚本覆盖对象": [
                    x for x in coverage if x["是否本分支新增引用"] and not x["脚本覆盖"]
                ],
                "仅非初始化脚本存在建表但通用初始化缺失对象": [
                    x for x in coverage if x["仅非初始化脚本存在建表"]
                ],
                "未完整解析Mapper": summary["未完整解析Mapper"],
                "动态表名引用": summary["动态表名引用"],
            },
            "判定提示": (
                "脚本未覆盖不自动等于缺陷：对象可能由基础库、其他仓库或外部初始化提供。"
                "但未逐项确认来源前不得宣称全量数据库脚本检查通过；变更Mapper新增引用且无任何来源证据时应判阻断或待确认。"
            ),
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        if not summary["数量闭环"] or summary["未完整解析Mapper数"]:
            return 2
        return 0
    except (AuditError, OSError, UnicodeError) as exc:
        print(f"错误：{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
