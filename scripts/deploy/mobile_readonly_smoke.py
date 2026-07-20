#!/usr/bin/env python3
"""使用服务器本地隔离账号验证 Android v0.2 只读 API，不输出凭据或 Token。"""

from __future__ import annotations

import argparse
import json
import urllib.error
import urllib.request
from pathlib import Path


def call(base: str, method: str, path: str, token: str | None = None, body: dict | None = None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(
        base.rstrip("/") + path,
        data=None if body is None else json.dumps(body).encode(),
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read() or b"null")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--credential-file", required=True, type=Path)
    args = parser.parse_args()
    if not args.base_url.startswith("https://"):
        raise SystemExit("HTTPS is required")
    values = dict(
        line.split("=", 1)
        for line in args.credential_file.read_text(encoding="utf-8").splitlines()
        if "=" in line
    )
    status, login = call(
        args.base_url, "POST", "/api/v2/auth/login", body={"username": values["TEST_USER"], "password": values["TEST_PASS"]}
    )
    if status != 200 or not login.get("token"):
        raise RuntimeError(f"login failed: HTTP {status}")
    token = login["token"]
    checks = [
        ("users_me", "/api/v2/users/me"),
        ("overview", "/api/v2/mobile/overview"),
        ("accounts", "/api/v2/mobile/accounts"),
        ("transactions", "/api/v2/mobile/transactions?page=1&pageSize=20"),
        ("holdings", "/api/v2/mobile/holdings?page=1&pageSize=20"),
    ]
    results: dict[str, object] = {"login": 200}
    payloads: dict[str, object] = {}
    for name, path in checks:
        code, payload = call(args.base_url, "GET", path, token=token)
        if code != 200:
            raise RuntimeError(f"{name} failed: HTTP {code}")
        results[name] = code
        payloads[name] = payload
    accounts = payloads["accounts"]
    account_items = accounts.get("items", []) if isinstance(accounts, dict) else []
    if account_items:
        account_id = account_items[0].get("id")
        if account_id is not None:
            code, _ = call(args.base_url, "GET", f"/api/v2/mobile/accounts/{account_id}", token=token)
            if code != 200:
                raise RuntimeError(f"account_detail failed: HTTP {code}")
            results["account_detail"] = code
    for name in ("accounts", "transactions", "holdings"):
        payload = payloads[name]
        required = {"items", "page", "pageSize", "total", "totalPages", "hasNext"}
        if not isinstance(payload, dict) or not required.issubset(payload):
            raise RuntimeError(f"{name} pagination contract invalid")
    unauthenticated, _ = call(args.base_url, "GET", "/api/v2/mobile/overview")
    if unauthenticated != 401:
        raise RuntimeError(f"authentication guard failed: HTTP {unauthenticated}")
    results["unauthenticated_overview"] = 401
    results["empty_lists_supported"] = all(
        isinstance(payloads[name].get("items"), list) for name in ("accounts", "transactions", "holdings")
    )
    print(json.dumps(results, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
