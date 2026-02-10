#!/usr/bin/env python3
"""检查快速购买货币基金的交易和posting"""
import pymysql
from decimal import Decimal

conn = pymysql.connect(host='124.220.229.91', port=9009, user='dca_v2', password='FW5GxWai5Shyrekb',
                       database='dca_v2', charset='utf8mb4', connect_timeout=30,
                       cursorclass=pymysql.cursors.DictCursor)
cur = conn.cursor()

# 查找最近的SUBSCRIPTION交易
cur.execute("""
    SELECT t.txn_id, t.txn_type, t.product_id, t.requested_at, t.note, t.status
    FROM ledger_txn t
    WHERE t.txn_type = 'SUBSCRIPTION'
    AND t.requested_at >= '2026-02-11 00:00:00'
    ORDER BY t.requested_at DESC
    LIMIT 5
""")
print("=== 最近的SUBSCRIPTION交易 ===")
for r in cur.fetchall():
    print(f"  txn_id={r['txn_id']}, product_id={r['product_id']}, time={r['requested_at']}")
    print(f"    note={r['note']}, status={r['status']}")
    
    # 查找该交易的所有posting
    cur.execute("""
        SELECT p.id, p.posting_type, p.account_id, a.account_name, p.account_type,
               p.amount, p.shares, p.account_balance_after, p.parent_account_balance_after
        FROM ledger_posting p
        JOIN accounts a ON p.account_id = a.id
        WHERE p.txn_id = %s
        ORDER BY p.id
    """, (r['txn_id'],))
    postings = cur.fetchall()
    print(f"    Postings ({len(postings)}条):")
    for p in postings:
        sign = '+' if p['posting_type'] == 'DEBIT' else '-'
        print(f"      pid={p['id']}, {p['account_name']}(id={p['account_id']})")
        print(f"        {p['posting_type']} {sign}{p['amount']}, type={p['account_type']}, shares={p['shares']}")
        print(f"        bal={p['account_balance_after']}, parent_bal={p['parent_account_balance_after']}")
    print()

# 检查小荷包账户余额
cur.execute("SELECT id, account_name, balance, initial_shares FROM accounts WHERE id = 16 OR id = 17")
print("=== 小荷包账户 ===")
for r in cur.fetchall():
    print(f"  id={r['id']}, {r['account_name']}, bal={r['balance']}, shares={r['initial_shares']}")

conn.close()
