import json
import os
import sys

import pymysql


def load_db_config():
    """
    从 config/db_config.json 读取数据库配置
    """
    project_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    config_path = os.path.join(project_root, "config", "db_config.json")

    with open(config_path, "r", encoding="utf-8") as f:
        cfg = json.load(f)

    return {
        "host": cfg.get("host", "127.0.0.1"),
        "port": int(cfg.get("port", 3306)),
        "user": cfg["user"],
        "password": cfg["password"],
        "database": cfg["database"],
        "charset": cfg.get("charset", "utf8mb4"),
    }


def main():
    if len(sys.argv) >= 2:
        try:
            account_id = int(sys.argv[1])
        except ValueError:
            print(f"无效的账户ID参数: {sys.argv[1]}")
            return
    else:
        account_id = 75  # 默认检查ID=75

    cfg = load_db_config()
    print("使用配置连接数据库:", cfg)

    conn = pymysql.connect(**cfg)
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cur:
            print(f"\n=== accounts 表中 account_id = {account_id} 的记录 ===")
            cur.execute("SELECT * FROM accounts WHERE id = %s", (account_id,))
            row = cur.fetchone()
            if not row:
                print("未找到该账户")
            else:
                for k, v in row.items():
                    print(f"{k}: {v}")

            print(f"\n=== ledger_posting 表中使用 account_id = {account_id} 的前 10 条分录 ===")
            cur.execute(
                """
                SELECT id, txn_id, posting_type, account_type, amount, shares, note, created_at
                FROM ledger_posting
                WHERE account_id = %s
                ORDER BY created_at DESC
                LIMIT 10
                """,
                (account_id,),
            )
            postings = cur.fetchall()
            if not postings:
                print("未找到相关分录")
            else:
                for p in postings:
                    print("-" * 40)
                    for k, v in p.items():
                        print(f"{k}: {v}")
    finally:
        conn.close()


if __name__ == "__main__":
    main()

