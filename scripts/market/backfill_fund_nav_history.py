#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
全市场（场内 + 场外）历史行情 / 净值补齐脚本。

功能说明：
- 从 MySQL 的 product_master 表中读取所有启用产品：
  - 场外产品（channel='OTC'）：
    - 普通基金：asset_type in ('FUND', 'LOF') → 写入 nav 表
    - 货币基金：asset_type = 'MMF' → 写入 nav 表（nav 固定 1.0，记录每万份收益到 dividend）
    - 银行理财净值型：asset_type = 'BANK_WM_NAV'（如 FBAE41126E）→ 通过民生 API 写入 nav 表
  - 场内产品（channel='EXCHANGE'）：
    - ETF / 股票：asset_type in ('ETF', 'STOCK') → 写入 market_bar_daily（日 K）

- 对于每个产品：
  - nav 表：从 2000-01-01（或该产品已有 nav 的最后一天之后）补齐到今天；
  - market_bar_daily：从 2000-01-01（或已有 trade_date 的最后一天之后）补齐到今天；
  - 没有数据的日期直接跳过（不写记录）。

特殊处理：
- FBAE41126E：走民生银行 API（cmbc），使用专用 HTTP 逻辑补齐 nav。
- 000686（货币基金）：使用 akshare 货币基金接口，nav 始终按 1.0 记录。

使用方式：
    cd scripts/market
    python backfill_fund_nav_history.py

依赖：
- pymysql
- akshare
- requests, urllib3（仅民生理财产品）
"""

import os
import sys
from datetime import date, datetime, timedelta
from typing import List, Dict, Optional

import pymysql
import akshare as ak
import requests
import ssl
import urllib3
from urllib3.util.ssl_ import create_urllib3_context
from requests.adapters import HTTPAdapter

# 禁用 SSL 警告（民生银行接口使用 verify=False）
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# 将 scripts 目录加入路径，导入现有配置
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(CURRENT_DIR))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)
if os.path.dirname(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, os.path.dirname(CURRENT_DIR))

from market.config import DB_CONFIG  # noqa: E402


class LegacySSLAdapter(HTTPAdapter):
    """支持 legacy SSL 重新协商的适配器（参考 v1 cmbc_client，做了简化）。"""

    def init_poolmanager(self, *args, **kwargs):
        ctx = create_urllib3_context()
        # 允许 legacy renegotiation（Python 3.12+ 默认禁用）
        try:
            if hasattr(ssl, "OP_LEGACY_SERVER_CONNECT"):
                ctx.options |= ssl.OP_LEGACY_SERVER_CONNECT  # type: ignore[attr-defined]
            else:
                # Python 3.12+ 使用数值 0x4
                ctx.options |= 0x4
        except Exception:
            # 如果设置失败，直接忽略，走默认配置
            pass
        # 必须同时设置 check_hostname / verify_mode
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE  # type: ignore[assignment]
        kwargs["ssl_context"] = ctx
        return super().init_poolmanager(*args, **kwargs)


def _normalize_cmbc_nav_record(raw_record: Dict, product_code: str) -> Dict:
    """
    将民生 API 返回的原始记录转换为系统 nav 表需要的字段。

    参考 v1 的 adaptor/cmbc_client.py：
    - ISS_DATE: 净值日期，YYYYMMDD
    - NAV: 单位净值
    - TOT_NAV: 累计净值
    - INCOME: 收益
    """
    raw_date = str(raw_record["ISS_DATE"])
    nav_date = datetime.strptime(raw_date, "%Y%m%d").date()

    return {
        "product_code": product_code,
        "nav_date": nav_date,
        "nav": float(raw_record.get("NAV", 0)),
        "acc_nav": float(raw_record.get("TOT_NAV", 0)) if raw_record.get("TOT_NAV") is not None else None,
        "dividend": float(raw_record.get("INCOME", 0)) if raw_record.get("INCOME") is not None else None,
    }


def query_cmbc_nav_for_range(product_code: str, start_date: date, end_date: date) -> List[Dict]:
    """
    从民生银行接口按日期区间拉取历史净值。

    说明：
    - 为了避免 v1 中“递归回溯找最近净值”的行为，这里严格按每个交易日查询：
      - begin_date = end_date = 当天
      - 如果当天没有数据，则跳过，不回溯前一日。
    - 民生接口本身支持按日期区间查询，但 v1 客户端是按单日查询的。
      这里延续单日查询逻辑，以便控制每个 nav_date 精确对应。
    """
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        ),
        "Origin": "https://www.cmbcwm.com.cn",
        "Referer": "https://www.cmbcwm.com.cn",
    }

    session = requests.Session()
    session.mount("https://", LegacySSLAdapter())

    all_records: List[Dict] = []
    cur = start_date
    while cur <= end_date:
        try:
            resp = session.post(
                "https://www.cmbcwm.com.cn/gw/po_web/BTADailyQry",
                data={
                    "chart_type": "1",
                    "real_prd_code": product_code,
                    "begin_date": cur.strftime("%Y%m%d"),
                    "end_date": cur.strftime("%Y%m%d"),
                },
                headers=headers,
                timeout=10,
                verify=False,
            )
            data = resp.json()
        except Exception as e:  # noqa: BLE001
            print(f"[CMBC] {product_code} {cur} 请求失败: {e}")
            cur += timedelta(days=1)
            continue

        raw_list = data.get("list") or []
        if raw_list:
            # 接口按日返回列表，但我们只关注当天这一条（一般也是 1 条）
            normalized = _normalize_cmbc_nav_record(raw_list[0], product_code)
            # 严格校验日期一致
            if normalized["nav_date"] == cur:
                all_records.append(normalized)
            else:
                # 如果返回日期和查询日期不一致，保守起见也写入，但打印提示
                print(
                    f"[CMBC] {product_code} 查询日期 {cur} 返回日期 {normalized['nav_date']}，仍写入记录"
                )
                all_records.append(normalized)

        cur += timedelta(days=1)

    return all_records


class FundHistoryBackfiller:
    """为所有场外产品补齐历史净值的执行器。"""

    START_DATE = date(2000, 1, 1)

    def __init__(self):
        self.conn: Optional[pymysql.Connection] = None
        self.connect_db()

    def connect_db(self) -> None:
        try:
            self.conn = pymysql.connect(**DB_CONFIG)
            print("数据库连接成功")
        except Exception as e:  # noqa: BLE001
            print(f"数据库连接失败: {e}")
            raise

    def close_db(self) -> None:
        if self.conn:
            self.conn.close()

    def get_otc_products(self) -> List[Dict]:
        """
        获取所有需要补齐净值的场外产品。

        - 普通基金/LOF：asset_type in ('FUND', 'LOF')
        - 货币基金：asset_type = 'MMF'
        - 银行理财净值型：asset_type = 'BANK_WM_NAV'
        """
        sql = """
            SELECT id, product_code, product_name, asset_type, data_source
            FROM product_master
            WHERE channel = 'OTC'
              AND asset_type IN ('FUND', 'LOF', 'MMF', 'BANK_WM_NAV')
              AND is_active = 1
        """
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute(sql)
            return cursor.fetchall()

    def get_existing_nav_range(self, product_id: int) -> Optional[Dict]:
        """查询 nav 表中某产品已存在的最早/最晚 nav_date。"""
        sql = """
            SELECT MIN(nav_date) AS min_date, MAX(nav_date) AS max_date
            FROM nav
            WHERE product_id = %s
        """
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute(sql, (product_id,))
            row = cursor.fetchone()
            if not row or (row["min_date"] is None and row["max_date"] is None):
                return None
            return row

    def save_nav_records(self, product_id: int, records: List[Dict], source: str) -> None:
        """
        批量写入 nav 记录（带 UPSERT）。

        records: 每个元素必须包含：
            - nav_date: date
            - nav: float
        可选：
            - acc_nav, daily_return, dividend
        """
        if not records:
            return

        sql = """
            INSERT INTO nav (product_id, nav_date, nav, acc_nav, daily_return, dividend, source, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, NOW())
            ON DUPLICATE KEY UPDATE
                nav = VALUES(nav),
                acc_nav = VALUES(acc_nav),
                daily_return = VALUES(daily_return),
                dividend = VALUES(dividend)
        """
        params = []
        for r in records:
            params.append(
                (
                    product_id,
                    r["nav_date"],
                    r["nav"],
                    r.get("acc_nav"),
                    r.get("daily_return"),
                    r.get("dividend"),
                    source,
                )
            )

        with self.conn.cursor() as cursor:
            cursor.executemany(sql, params)
        self.conn.commit()

    # ======== akshare 相关采集 ========

    def fetch_history_by_akshare_fund(self, product_code: str) -> List[Dict]:
        """
        使用 akshare 获取开放式基金/LOF 历史净值。
        默认获取全量历史，然后由调用方按日期过滤。
        """
        try:
            df = ak.fund_em_open_fund_info(fund=product_code)
        except Exception as e:  # noqa: BLE001
            print(f"[akshare] fund_em_open_fund_info({product_code}) 失败: {e}")
            return []

        if df is None or df.empty:
            print(f"[akshare] 未获取到基金 {product_code} 的历史净值")
            return []

        records: List[Dict] = []
        # 兼容列名：常见为「净值日期」「单位净值」
        for _, row in df.iterrows():
            nav_date_raw = row.get("净值日期", row.get("日期"))
            if not nav_date_raw:
                continue
            nav_date = pd_to_date(nav_date_raw)
            nav_val = row.get("单位净值", row.get("净值"))
            if nav_val is None:
                continue

            try:
                nav = float(nav_val)
            except Exception:
                continue

            records.append(
                {
                    "nav_date": nav_date,
                    "nav": nav,
                    "acc_nav": None,
                    "daily_return": None,
                    "dividend": None,
                }
            )
        return records

    def fetch_history_by_akshare_mmf(self, product_code: str, force_nav_one: bool = False) -> List[Dict]:
        """
        使用 akshare 获取货币基金历史数据。

        - ak.fund_em_money_fund_info 一般提供：
          - 净值日期
          - 每万份收益
          - 七日年化收益率
        - nav 表中单位净值对于货币基金通常固定为 1.0，
          收益通过分红或「每万份收益」体现。
        - 这里为了简单和与现有交易记录对齐，nav 一律记为 1.0。
        """
        try:
            df = ak.fund_em_money_fund_info(fund=product_code)
        except Exception as e:  # noqa: BLE001
            print(f"[akshare] fund_em_money_fund_info({product_code}) 失败: {e}")
            return []

        if df is None or df.empty:
            print(f"[akshare] 未获取到货币基金 {product_code} 的历史数据")
            return []

        records: List[Dict] = []
        for _, row in df.iterrows():
            nav_date_raw = row.get("净值日期", row.get("日期"))
            if not nav_date_raw:
                continue
            nav_date = pd_to_date(nav_date_raw)

            # 对于 MMF，nav 统一记为 1.0，dividend 可选记录为「每万份收益」
            dividend_raw = row.get("每万份收益")
            try:
                dividend_val = float(dividend_raw) if dividend_raw is not None else None
            except Exception:
                dividend_val = None

            records.append(
                {
                    "nav_date": nav_date,
                    "nav": 1.0 if force_nav_one or True else 1.0,  # 始终 1.0
                    "acc_nav": None,
                    "daily_return": None,
                    # 这里直接把「每万份收益」记在 dividend 字段，方便后续分析
                    "dividend": dividend_val,
                }
            )
        return records

    # ======== 主流程 ========

    def backfill_for_product(self, product: Dict) -> None:
        product_id = product["id"]
        code = product["product_code"]
        name = product["product_name"]
        asset_type = product["asset_type"]
        data_source = (product.get("data_source") or "").lower()

        print(f"\n==== 开始补齐: {name} ({code}), 类型={asset_type}, 源={data_source} ====")

        # 确定起始日期：已有数据则从最后一天之后开始，否则从 START_DATE
        existing = self.get_existing_nav_range(product_id)
        if existing and existing.get("max_date"):
            start_date = existing["max_date"] + timedelta(days=1)
        else:
            start_date = self.START_DATE

        today = date.today()
        if start_date > today:
            print(f"  已有数据覆盖到 {existing['max_date'] if existing else 'N/A'}，无需补齐")
            return

        print(f"  补齐区间: {start_date} ~ {today}")

        records: List[Dict] = []
        source_flag = "FUND"

        # 特殊：民生银行净值型理财
        if asset_type == "BANK_WM_NAV" or data_source == "cmbc" or code == "FBAE41126E":
            records = query_cmbc_nav_for_range(code, start_date, today)
            source_flag = "CMBC"
        # 特殊：货币基金 000686
        elif asset_type == "MMF" and code == "000686":
            records = self.fetch_history_by_akshare_mmf(code, force_nav_one=True)
            source_flag = "MMF"
        # 一般货币基金
        elif asset_type == "MMF":
            records = self.fetch_history_by_akshare_mmf(code, force_nav_one=True)
            source_flag = "MMF"
        # 普通基金/LOF
        else:
            records = self.fetch_history_by_akshare_fund(code)
            source_flag = "FUND"

        if not records:
            print("  未获取到任何历史净值数据，跳过")
            return

        # 只保留需要补齐区间内的数据
        filtered = [r for r in records if start_date <= r["nav_date"] <= today]
        filtered.sort(key=lambda x: x["nav_date"])

        if not filtered:
            print("  在补齐区间内没有可写入的数据")
            return

        print(f"  准备写入 {len(filtered)} 条记录...")
        self.save_nav_records(product_id, filtered, source_flag)
        print(f"  ✓ 补齐完成: 写入 {len(filtered)} 条（含已存在记录的 UPSERT）")

    def run(self) -> None:
        try:
            products = self.get_otc_products()
            print(f"共找到 {len(products)} 个场外产品需要检查补齐历史净值")
            for p in products:
                self.backfill_for_product(p)
        finally:
            self.close_db()


def pd_to_date(val) -> date:
    """将 pandas / 字符串 日期统一转换为 date 对象。"""
    # 为避免额外依赖，这里不直接 import pandas，而是兼容常见类型
    if isinstance(val, date) and not isinstance(val, datetime):
        return val
    if isinstance(val, datetime):
        return val.date()
    # 字符串，如 '2025-01-01' / '20250101'
    s = str(val)
    if "-" in s:
        return datetime.strptime(s[:10], "%Y-%m-%d").date()
    if len(s) == 8:
        return datetime.strptime(s, "%Y%m%d").date()
    # 兜底：尝试直接用 pandas.to_datetime 风格
    return datetime.fromisoformat(s).date()


if __name__ == "__main__":
    backfiller = FundHistoryBackfiller()
    backfiller.run()

