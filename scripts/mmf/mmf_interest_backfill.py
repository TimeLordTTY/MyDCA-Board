#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
MMF（日收益）全量回算 + 回填脚本（基于 nav.dividend）

目标：
- 先复算并对比：给出指定日期区间的“应发收益” vs “已入账收益”
- 确认无误后，回填缺失的日收益（ledger_txn + ledger_posting），并同步更新 MMF 平台账户 initial_shares

本脚本假设：
- 货币基金净值通常为 1.0，收益按 nav.dividend（每份收益）发放
- quick-buy-mmf 已将申购份额写入：ledger_posting.shares（在入金子账户的 CASH DEBIT 分录上）
- 生效份额以 ledger_txn.confirm_date 为准（T+N 确认/跨周末节假日由后端推导）

用法：
  python scripts/mmf/mmf_interest_backfill.py --product-id 15 --platform-account-id 17 --child-account-id 16 --start 2026-02-11 --dry
  python scripts/mmf/mmf_interest_backfill.py --product-id 15 --platform-account-id 17 --child-account-id 16 --start 2026-02-11 --apply
"""

import argparse
import uuid
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP
from typing import Dict, List, Optional, Tuple

import pymysql

import os
import sys
import akshare as ak

# 让 scripts/ 目录可被当作包根目录导入 market/config.py
_CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
_SCRIPTS_DIR = os.path.dirname(_CURRENT_DIR)
if _SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, _SCRIPTS_DIR)

from market.config import DB_CONFIG  # noqa: E402


@dataclass
class AccountRow:
    id: int
    account_name: str
    account_type: str
    account_kind: str
    parent_account_id: Optional[int]
    owner_user_id: Optional[int]
    owner_family_id: Optional[int]
    currency: str
    linked_product_id: Optional[int]
    initial_shares: Optional[Decimal]


def get_conn():
    return pymysql.connect(cursorclass=pymysql.cursors.DictCursor, **DB_CONFIG)


def d(s: str) -> date:
    return datetime.strptime(s, "%Y-%m-%d").date()


def dec(v) -> Decimal:
    if v is None:
        return Decimal("0")
    if isinstance(v, Decimal):
        return v
    return Decimal(str(v))


def round2(x: Decimal) -> Decimal:
    # 按常规理财产品口径采用四舍五入到分
    return x.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def get_account(conn, account_id: int) -> AccountRow:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, account_name, account_type, account_kind, parent_account_id,
                   owner_user_id, owner_family_id, currency, linked_product_id, initial_shares
            FROM accounts
            WHERE id = %s
            """,
            (account_id,),
        )
        row = cur.fetchone()
        if not row:
            raise RuntimeError(f"accounts 找不到 account_id={account_id}")
        return AccountRow(
            id=int(row["id"]),
            account_name=row["account_name"],
            account_type=row["account_type"],
            account_kind=row["account_kind"],
            parent_account_id=row["parent_account_id"],
            owner_user_id=row.get("owner_user_id"),
            owner_family_id=row.get("owner_family_id"),
            currency=row.get("currency") or "CNY",
            linked_product_id=row.get("linked_product_id"),
            initial_shares=dec(row.get("initial_shares")) if row.get("initial_shares") is not None else None,
        )


def find_income_virtual_account(conn, owner_user_id: Optional[int], owner_family_id: Optional[int]) -> int:
    """
    找一个可用的 INCOME 虚拟科目账户作为收益对手方。
    优先使用同 owner 的 VIRTUAL + virtual_subtype=INCOME（如果存在），否则回退到任意一个 INCOME 虚拟科目。
    """
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT id
            FROM accounts
            WHERE account_kind='VIRTUAL'
              AND virtual_subtype='INCOME'
              AND (
                (owner_user_id <=> %s AND owner_family_id <=> %s)
                OR (%s IS NULL AND %s IS NULL)
              )
            ORDER BY id
            LIMIT 1
            """,
            (owner_user_id, owner_family_id, owner_user_id, owner_family_id),
        )
        row = cur.fetchone()
        if row:
            return int(row["id"])

        cur.execute(
            """
            SELECT id
            FROM accounts
            WHERE account_kind='VIRTUAL'
              AND virtual_subtype='INCOME'
            ORDER BY id
            LIMIT 1
            """
        )
        row2 = cur.fetchone()
        if not row2:
            raise RuntimeError("未找到 virtual_subtype=INCOME 的虚拟科目账户，无法回填收益分录")
        return int(row2["id"])


def get_nav_mmf_per10k_yields(conn, product_id: int, start: date, end: date) -> List[Tuple[date, Decimal]]:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT nav_date, acc_nav
            FROM nav
            WHERE product_id=%s
              AND nav_date >= %s
              AND nav_date <= %s
            ORDER BY nav_date ASC
            """,
            (product_id, start, end),
        )
        rows = cur.fetchall()

    out: List[Tuple[date, Decimal]] = []
    for r in rows:
        nav_date = r["nav_date"]
        per10k = dec(r.get("acc_nav"))
        # per10k 为空或 0 视为无收益日，跳过
        if per10k is None or per10k == 0:
            continue
        out.append((nav_date, per10k))
    return out


def upsert_nav_mmf_history(conn, product_id: int, product_code: str, start: date, end: date) -> int:
    """
    使用 akshare 拉取货币基金历史「每万份收益」，写入 nav 表：
    - nav = 1.0
    - acc_nav = 每万份收益（DECIMAL(18,6) 足够精度）
    - dividend 列在当前库结构是 DECIMAL(18,2)，不足以存每份收益（会变 0.00）
      因此这里将 dividend 也写入 “每万份收益（两位小数）”，便于 SQL 快速查看。
    """
    df = ak.fund_money_fund_info_em(product_code)
    if df is None or df.empty:
        return 0

    df = df.copy()
    df["净值日期"] = df["净值日期"].astype(str)
    df = df[(df["净值日期"] >= start.isoformat()) & (df["净值日期"] <= end.isoformat())]
    if df.empty:
        return 0

    rows = []
    for _, r in df.iterrows():
        nav_date = r["净值日期"]
        per10k = r["每万份收益"]
        try:
            per10k_d = Decimal(str(per10k))
        except Exception:
            continue
        rows.append((
            product_id,
            nav_date,
            Decimal("1.000000"),
            per10k_d,
            per10k_d.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP),
            "MMF_AKSHARE"
        ))

    with conn.cursor() as cur:
        cur.executemany(
            """
            INSERT INTO nav (product_id, nav_date, nav, acc_nav, dividend, source, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, NOW())
            ON DUPLICATE KEY UPDATE
              nav = VALUES(nav),
              acc_nav = VALUES(acc_nav),
              dividend = VALUES(dividend),
              source = VALUES(source)
            """,
            rows,
        )
        return cur.rowcount


def get_confirmed_share_events(conn, child_account_id: int, product_id: int, start: date, end: date) -> List[Tuple[date, Decimal]]:
    """
    取 child_account 上的“份额变动事件”，以 ledger_txn.confirm_date 归档：
    - CASH DEBIT 且 shares 不为空：增加份额
    - CASH CREDIT 且 shares 不为空：减少份额

    仅统计 ledger_txn.product_id=product_id 的交易。
    """
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT t.confirm_date, p.posting_type, p.shares
            FROM ledger_posting p
            JOIN ledger_txn t ON t.txn_id = p.txn_id
            WHERE p.account_id = %s
              AND p.shares IS NOT NULL
              AND p.shares > 0
              AND t.product_id = %s
              AND t.status = 'CONFIRMED'
              AND t.confirm_date IS NOT NULL
              AND t.confirm_date >= %s
              AND t.confirm_date <= %s
            ORDER BY t.confirm_date ASC, p.id ASC
            """,
            (child_account_id, product_id, start, end),
        )
        rows = cur.fetchall()

    events: List[Tuple[date, Decimal]] = []
    for r in rows:
        cd: date = r["confirm_date"]
        shares = dec(r["shares"])
        if r["posting_type"] == "CREDIT":
            shares = -shares
        events.append((cd, shares))
    return events


def build_share_asof_map(nav_dates: List[date], events: List[Tuple[date, Decimal]]) -> Dict[date, Decimal]:
    """
    给定一组 nav_date（有 dividend 的交易日），构建每个 nav_date 当天可用的“已确认份额”：
    shares_asof(d) = 累加所有 confirm_date <= d 的 shares 事件
    """
    events_sorted = sorted(events, key=lambda x: x[0])
    idx = 0
    cum = Decimal("0")
    out: Dict[date, Decimal] = {}
    for nd in nav_dates:
        while idx < len(events_sorted) and events_sorted[idx][0] <= nd:
            cum += events_sorted[idx][1]
            idx += 1
        out[nd] = cum
    return out


def build_balance_asof_map(conn, account_id: int, start: date, end: date, nav_dates: List[date]) -> Dict[date, Decimal]:
    """
    从 ledger_posting 复原账户余额轨迹（从 start 之前最近一笔开始累计），并取每个 nav_date 的 end-of-day 余额作为“有效份额”近似。
    """
    # 为避免区间内只有转出导致余额为负，这里从很早开始复原余额轨迹（单账户数据量通常可控）
    start_dt = datetime(2000, 1, 1)
    end_dt = datetime.combine(end + timedelta(days=1), datetime.min.time())

    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT t.requested_at, p.id, p.posting_type, p.amount
            FROM ledger_posting p
            JOIN ledger_txn t ON t.txn_id = p.txn_id
            WHERE p.account_id = %s
              AND p.account_type = 'CASH'
              AND t.requested_at >= %s
              AND t.requested_at < %s
              AND t.status = 'CONFIRMED'
            ORDER BY t.requested_at ASC, p.id ASC
            """,
            (account_id, start_dt, end_dt),
        )
        postings = cur.fetchall()

    bal = Decimal("0")
    timeline: List[Tuple[datetime, Decimal]] = []
    for r in postings:
        amt = dec(r["amount"])
        if r["posting_type"] == "DEBIT":
            bal += amt
        else:
            bal -= amt
        timeline.append((r["requested_at"], bal))

    out: Dict[date, Decimal] = {}
    idx = 0
    current = Decimal("0")
    for nd in nav_dates:
        cutoff = datetime.combine(nd, datetime.max.time())
        while idx < len(timeline) and timeline[idx][0] <= cutoff:
            current = timeline[idx][1]
            idx += 1
        out[nd] = current
    return out


def query_existing_interest(conn, child_account_id: int, product_id: int, start: date, end: date) -> Dict[date, Decimal]:
    """
    取已入账的利息（INTEREST/INCOME），按 confirm_date 汇总到 payout_date：
    payout_date = ledger_txn.confirm_date
    只统计：DEBIT 到 child_account 的分录金额
    """
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT t.confirm_date AS payout_date, SUM(p.amount) AS amt
            FROM ledger_posting p
            JOIN ledger_txn t ON t.txn_id = p.txn_id
            WHERE p.account_id = %s
              AND p.posting_type = 'DEBIT'
              AND p.account_type = 'CASH'
              AND t.product_id = %s
              AND t.status = 'CONFIRMED'
              AND t.confirm_date IS NOT NULL
              AND t.confirm_date >= %s
              AND t.confirm_date <= %s
              AND t.txn_type IN ('INTEREST','INCOME')
            GROUP BY t.confirm_date
            ORDER BY t.confirm_date ASC
            """,
            (child_account_id, product_id, start, end),
        )
        rows = cur.fetchall()

    out: Dict[date, Decimal] = {}
    for r in rows:
        out[r["payout_date"]] = dec(r["amt"])
    return out


def update_platform_initial_shares(conn, platform_account_id: int, delta_shares: Decimal):
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE accounts
            SET initial_shares = IFNULL(initial_shares, 0) + %s,
                updated_at = NOW()
            WHERE id = %s
            """,
            (delta_shares, platform_account_id),
        )


def insert_interest_txn(conn,
                        user_id: int,
                        family_id: Optional[int],
                        product_id: int,
                        nav_date: date,
                        payout_date: date,
                        child_account_id: int,
                        income_account_id: int,
                        amount: Decimal,
                        currency: str):
    """
    插入一笔 INTEREST 交易：借入金账户（小荷包子账户）+ 贷收益虚拟科目。
    同时在借方分录写 shares=amount（nav≈1.0），便于后续份额推导/复算。
    """
    txn_id = f"MMF_INT_{payout_date.strftime('%Y%m%d')}_{uuid.uuid4().hex[:8]}"
    requested_at = datetime.combine(payout_date, datetime.min.time()) + timedelta(hours=9)
    note = f"货币基金日收益（nav_date={nav_date.isoformat()}）"

    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO ledger_txn (
              txn_id, user_id, family_id, txn_type,
              product_id, requested_at, trade_date, nav_date, confirm_date, fetch_date,
              status, note, is_reversed, created_at, updated_at
            ) VALUES (
              %s, %s, %s, 'INTEREST',
              %s, %s, %s, %s, %s, %s,
              'CONFIRMED', %s, 0, NOW(), NOW()
            )
            """,
            (
                txn_id,
                user_id,
                family_id,
                product_id,
                requested_at,
                nav_date,
                nav_date,
                payout_date,
                payout_date,
                note,
            ),
        )

        # 借：小荷包子账户（现金增加）
        cur.execute(
            """
            INSERT INTO ledger_posting (
              txn_id, posting_type, account_id, account_type, amount, shares, currency, note, created_at
            ) VALUES (
              %s, 'DEBIT', %s, 'CASH', %s, %s, %s, %s, NOW()
            )
            """,
            (txn_id, child_account_id, amount, amount, currency, "MMF日收益入账"),
        )

        # 贷：收益虚拟科目
        cur.execute(
            """
            INSERT INTO ledger_posting (
              txn_id, posting_type, account_id, account_type, amount, shares, currency, note, created_at
            ) VALUES (
              %s, 'CREDIT', %s, 'INCOME', %s, NULL, %s, %s, NOW()
            )
            """,
            (txn_id, income_account_id, amount, currency, "MMF日收益对冲"),
        )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--product-id", type=int, required=True)
    parser.add_argument("--platform-account-id", type=int, required=True)
    parser.add_argument("--child-account-id", type=int, required=True)
    parser.add_argument("--start", type=str, required=True)
    parser.add_argument("--end", type=str, default=None)
    parser.add_argument("--dry", action="store_true", help="只计算/对比，不写入")
    parser.add_argument(
        "--apply",
        action="store_true",
        help="回填收益（会写入 ledger + 更新 initial_shares）",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="强制重算：先删除指定区间内已存在的 INTEREST 收益分录，再重算写入",
    )
    args = parser.parse_args()

    if args.apply and args.dry:
        raise RuntimeError("不能同时指定 --dry 和 --apply")
    if not args.apply and not args.dry:
        args.dry = True

    start = d(args.start)
    end = d(args.end) if args.end else date.today()

    conn = get_conn()
    try:
        platform = get_account(conn, args.platform_account_id)
        child = get_account(conn, args.child_account_id)

        if platform.linked_product_id is not None and int(platform.linked_product_id) != args.product_id:
            print(f"[WARN] 平台账户 linked_product_id={platform.linked_product_id} != product_id={args.product_id}")

        if child.parent_account_id != platform.id:
            print(f"[WARN] 子账户 parent_account_id={child.parent_account_id} != platform_account_id={platform.id}")

        user_id = platform.owner_user_id or child.owner_user_id
        if not user_id:
            raise RuntimeError("无法从 accounts.owner_user_id 推断 user_id")
        family_id = platform.owner_family_id or child.owner_family_id
        currency = child.currency or "CNY"

        income_acc_id = find_income_virtual_account(conn, user_id, family_id)

        nav_yields = get_nav_mmf_per10k_yields(conn, args.product_id, start, end)
        if not nav_yields:
            with conn.cursor() as cur:
                cur.execute("SELECT product_code FROM product_master WHERE id=%s", (args.product_id,))
                pr = cur.fetchone()
            if not pr or not pr.get("product_code"):
                raise RuntimeError("无法读取 product_master.product_code，无法回补 MMF 历史收益")
            product_code = pr["product_code"]
            print(f"[INFO] nav.dividend 缺失，尝试用 akshare 回补：product_code={product_code}, range={start}~{end}")
            affected = upsert_nav_mmf_history(conn, args.product_id, product_code, start, end)
            conn.commit()
            print(f"[INFO] nav 回补完成：affected_rows={affected}")
            nav_yields = get_nav_mmf_per10k_yields(conn, args.product_id, start, end)

        nav_dates = [nd for nd, _ in nav_yields]
        if not nav_yields:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT nav_date, nav, acc_nav, dividend, source
                    FROM nav
                    WHERE product_id=%s
                      AND nav_date >= %s
                      AND nav_date <= %s
                    ORDER BY nav_date DESC
                    LIMIT 5
                    """,
                    (args.product_id, start, end),
                )
                sample = cur.fetchall()
            print("[DEBUG] nav sample rows (latest 5 in range):")
            for r in sample:
                print(r)
            raise RuntimeError("nav 表中没有可用的 acc_nav（每万份收益）数据，无法计算（请检查 akshare/网络是否可用）")

        # 份额事件：从 start~end+5 取宽一点，避免边界
        events = get_confirmed_share_events(conn, args.child_account_id, args.product_id, start - timedelta(days=10), end + timedelta(days=10))
        shares_asof = build_share_asof_map(nav_dates, events)
        if len(events) == 0:
            print("[INFO] 未找到 shares 事件（confirm_date 为空或无 shares），回退为用账户余额轨迹近似份额。")
            shares_asof = build_balance_asof_map(conn, args.child_account_id, start, end, nav_dates)

        if args.force and not args.dry:
            # 删除指定区间内已有的 INTEREST 收益分录（只删我们这类日收益，避免影响其他类型）
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT DISTINCT t.txn_id
                    FROM ledger_txn t
                    JOIN ledger_posting p ON p.txn_id = t.txn_id
                    WHERE t.product_id=%s
                      AND t.txn_type='INTEREST'
                      AND p.account_id=%s
                      AND t.nav_date >= %s
                      AND t.nav_date <= %s
                    """,
                    (args.product_id, args.child_account_id, start, end),
                )
                rows = cur.fetchall()
                old_txn_ids = [r["txn_id"] for r in rows]
            if old_txn_ids:
                print(f"[INFO] --force 模式：删除旧 INTEREST 交易 {len(old_txn_ids)} 条")
                with conn.cursor() as cur:
                    cur.execute(
                        f"DELETE FROM ledger_posting WHERE txn_id IN ({','.join(['%s']*len(old_txn_ids))})",
                        old_txn_ids,
                    )
                    cur.execute(
                        f"DELETE FROM ledger_txn WHERE txn_id IN ({','.join(['%s']*len(old_txn_ids))})",
                        old_txn_ids,
                    )
                # 注意：平台 initial_shares 在之前 run 中已增加过，这里不回滚，
                # 新一轮 run 时仍然会在其基础上继续增加（等价于把历史收益都当作已再投资）。

        existing = {} if args.force else query_existing_interest(
            conn, args.child_account_id, args.product_id, start, end + timedelta(days=2)
        )

        # 计算应发收益（payout_date = nav_date+1）
        rows = []
        missing = []
        total_should = Decimal("0")
        total_exist = Decimal("0")

        for nav_date, per10k in nav_yields:
            payout_date = nav_date + timedelta(days=1)
            sh = shares_asof.get(nav_date, Decimal("0"))
            amt = round2(sh * per10k / Decimal("10000"))
            total_should += amt
            exist_amt = existing.get(payout_date, Decimal("0"))
            total_exist += exist_amt

            diff = amt - exist_amt
            rows.append((nav_date, payout_date, sh, per10k, amt, exist_amt, diff))
            if amt != exist_amt and amt != 0:
                missing.append((nav_date, payout_date, sh, per10k, amt, exist_amt, diff))

        print("=== MMF 日收益复算（按 nav.acc_nav=每万份收益） ===")
        print(f"product_id={args.product_id}, platform_account_id={platform.id}({platform.account_name}), child_account_id={child.id}({child.account_name})")
        print(f"range: {start} ~ {end}, yield_days={len(nav_yields)}, share_events={len(events)}")
        print(f"should_total={total_should} | existing_total={total_exist} | diff_total={total_should - total_exist}")
        print("")

        # 打印最近 10 条便于肉眼核对
        tail = rows[-10:] if len(rows) > 10 else rows
        print("最近收益（nav_date -> payout_date | shares | dividend | should | existing | diff）")
        for r in tail:
            print(f"{r[0]} -> {r[1]} | sh={r[2]} | div={r[3]} | should={r[4]} | exist={r[5]} | diff={r[6]}")

        print("")
        print(f"不一致条数: {len(missing)}（仅列出 diff!=0 且 should!=0）")
        if len(missing) > 0:
            print("前 20 条不一致：")
            for r in missing[:20]:
                print(f"{r[0]} -> {r[1]} | sh={r[2]} | div={r[3]} | should={r[4]} | exist={r[5]} | diff={r[6]}")

        if args.dry:
            print("")
            print("dry-run 结束：未写入数据库。")
            return

        # apply：只回填“缺失收益”（exist=0），避免覆盖手工修正
        apply_rows = [r for r in rows if r[4] != 0 and existing.get(r[1], Decimal("0")) == 0]
        print("")
        print(f"准备回填缺失收益条数: {len(apply_rows)}（仅 exist=0）")
        if not apply_rows:
            print("无需回填。")
            return

        for nav_date, payout_date, sh, dividend, amt, exist_amt, diff in apply_rows:
            insert_interest_txn(
                conn=conn,
                user_id=int(user_id),
                family_id=int(family_id) if family_id else None,
                product_id=args.product_id,
                nav_date=nav_date,
                payout_date=payout_date,
                child_account_id=args.child_account_id,
                income_account_id=income_acc_id,
                amount=amt,
                currency=currency,
            )
            # 同步份额：MMF净值≈1.0，收益默认再投资 -> initial_shares 增加 amt/nav（=amt）
            update_platform_initial_shares(conn, args.platform_account_id, amt)

        conn.commit()
        print("回填完成：已写入 ledger_txn/ledger_posting，并更新平台 initial_shares。")

    finally:
        conn.close()


if __name__ == "__main__":
    main()

