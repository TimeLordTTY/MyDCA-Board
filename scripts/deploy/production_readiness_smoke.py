#!/usr/bin/env python3
"""通过隔离测试账号验证 MyDCA HTTPS、认证、草稿与确认闭环。"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import secrets
import stat
import urllib.error
import urllib.request
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--credential-dir", required=True, type=Path)
    parser.add_argument("--allow-write", action="store_true")
    return parser.parse_args()


class ApiClient:
    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")

    def call(
        self,
        method: str,
        path: str,
        body: dict | None = None,
        token: str | None = None,
        expected: tuple[int, ...] = (200,),
    ) -> tuple[int, object]:
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = urllib.request.Request(
            self.base_url + path,
            data=data,
            headers=headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        if status not in expected:
            raise RuntimeError(f"{path}: unexpected HTTP {status}")
        return status, json.loads(raw.decode("utf-8")) if raw else None


def load_or_create_credentials(root: Path) -> tuple[str, str, Path]:
    root.mkdir(parents=True, exist_ok=True)
    os.chmod(root, 0o700)
    env_file = root / "test-account.env"
    if not env_file.exists():
        username = "mydca_e2e_" + secrets.token_hex(6)
        password = "T-" + secrets.token_urlsafe(32)
        env_file.write_text(
            f"TEST_USER={username}\nTEST_PASS={password}\n",
            encoding="utf-8",
        )
        os.chmod(env_file, 0o600)
    values = {
        line.split("=", 1)[0]: line.split("=", 1)[1]
        for line in env_file.read_text(encoding="utf-8").splitlines()
        if "=" in line
    }
    return values["TEST_USER"], values["TEST_PASS"], env_file


def run() -> None:
    args = parse_args()
    if not args.allow_write:
        raise SystemExit("必须显式传入 --allow-write 才能创建隔离测试数据")
    if not args.base_url.startswith("https://"):
        raise SystemExit("生产就绪 smoke 只允许 HTTPS BaseUrl")

    username, password, env_file = load_or_create_credentials(args.credential_dir)
    client = ApiClient(args.base_url)
    status, login = client.call(
        "POST",
        "/api/v2/auth/login",
        {"username": username, "password": password},
        expected=(200, 500),
    )
    created_user = status != 200
    if created_user:
        _, login = client.call(
            "POST",
            "/api/v2/auth/register",
            {
                "username": username,
                "password": password,
                "nickname": "MyDCA E2E Test",
            },
        )

    token = login["token"]
    client.call("GET", "/api/v2/todos/today", token=token)
    _, accounts = client.call("GET", "/api/v2/accounts", token=token)
    parent = next(
        (account for account in accounts if account.get("accountCode") == "MYDCA-E2E-CASH"),
        None,
    )
    if parent is None:
        _, parent = client.call(
            "POST",
            "/api/v2/accounts",
            {
                "accountCode": "MYDCA-E2E-CASH",
                "accountName": "MyDCA 隔离联调账户",
                "accountKind": "REAL",
                "accountType": "CASH",
                "ownerType": "PERSONAL",
                "currency": "CNY",
                "fundUsage": "SPENDABLE",
                "initialBalance": 0,
                "note": "仅用于生产就绪联调，不含真实财富数据",
            },
            token=token,
        )
        _, accounts = client.call("GET", "/api/v2/accounts", token=token)
        parent = next(account for account in accounts if account.get("id") == parent.get("id"))
    child = next(item for item in parent.get("children", []) if item.get("accountName") == "待分配")

    source_ref = "production-readiness-" + secrets.token_hex(8)
    _, intent = client.call(
        "POST",
        "/api/v2/ai/accounting/parse-text",
        {"text": "测试收入0.01，到联调测试账户", "sourceRef": source_ref},
        token=token,
    )
    intent["accountId"] = child["id"]
    intent["accountNameHint"] = "MyDCA 隔离联调账户"
    intent["missingFields"] = [
        field for field in intent.get("missingFields", []) if field != "accountId"
    ]
    # 服务端必须根据人工补齐后的最终 intent 重新生成候选载荷。
    intent["parsedPayloadJson"] = None

    _, result = client.call(
        "POST",
        "/api/v2/ai/accounting/draft-from-intent",
        {"intent": intent},
        token=token,
    )
    draft = result["draft"]
    _, drafts = client.call(
        "GET",
        "/api/v2/drafts?status=DRAFT&page=1&pageSize=20",
        token=token,
    )
    if not any(item.get("id") == draft["id"] for item in drafts):
        raise RuntimeError("新建草稿未出现在草稿列表")

    _, preview = client.call(
        "POST",
        f"/api/v2/drafts/{draft['id']}/preview",
        {},
        token=token,
    )
    if preview.get("confirmSupported") is not True:
        raise RuntimeError("隔离测试草稿未通过 confirmSupported 守门")
    _, confirmed = client.call(
        "POST",
        f"/api/v2/drafts/{draft['id']}/confirm",
        {},
        token=token,
    )
    if confirmed.get("status") != "CONFIRMED" or not confirmed.get("confirmTxnId"):
        raise RuntimeError("隔离测试草稿未生成正式测试流水")

    _, restored = client.call(
        "POST",
        "/api/v2/auth/login",
        {"username": username, "password": password},
    )
    client.call("GET", "/api/v2/drafts", token=restored["token"])

    state_file = args.credential_dir / "test-account-state.json"
    state_file.write_text(
        json.dumps(
            {
                "status": "passed",
                "account_created_this_run": created_user,
                "draft_id": draft["id"],
                "confirm_txn_id": confirmed["confirmTxnId"],
                "source_ref": source_ref,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    os.chmod(state_file, 0o600)

    print("https_login=passed")
    print("token_restore=passed")
    print("today_todo=passed")
    print("draft_create=passed")
    print("draft_list=passed")
    print("preview_confirm_supported=true")
    print("confirm=passed")
    print("ledger_txn_created=true")
    print(f"credential_file_mode={oct(stat.S_IMODE(env_file.stat().st_mode))}")
    print(f"test_user_hash={hashlib.sha256(username.encode()).hexdigest()[:16]}")


if __name__ == "__main__":
    run()
