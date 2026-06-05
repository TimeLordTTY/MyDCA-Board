#!/usr/bin/env python3
"""
债券/国债逆回购行情采集脚本。

首版只覆盖项目内已登记的 BOND_REPO 场内产品，复用现有
market_quote_realtime 与 market_bar_daily 表，不新增数据库结构。
"""
import argparse
import logging
from datetime import datetime, date
from decimal import Decimal, InvalidOperation
from typing import Dict, Iterable, List, Optional

import requests

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")

SOURCE = "EASTMONEY_BOND"


def safe_decimal(value) -> Optional[Decimal]:
    """把行情源返回值安全转换为 Decimal，空值或横线视为缺失。"""
    if value is None or value == "" or value == "-":
        return None
    try:
        return Decimal(str(value))
    except (InvalidOperation, ValueError, TypeError):
        return None


def get_market_by_code(product_code: str) -> str:
    """按 A 股代码前缀推断交易所，避免产品主数据 market 字段误填影响行情查询。"""
    if not product_code:
        return "SZ"
    return "SH" if product_code[0] in ("5", "6", "9") else "SZ"


def eastmoney_secid(product_code: str, market: Optional[str] = None) -> str:
    """生成东方财富单证券查询所需的 secid。"""
    actual_market = get_market_by_code(product_code)
    if market and actual_market != market:
        logger.warning("产品 %s 市场不一致: 主数据=%s, 推断=%s", product_code, market, actual_market)
    prefix = "1" if actual_market == "SH" else "0"
    return f"{prefix}.{product_code}"


def fetch_eastmoney_quote(product_code: str, market: Optional[str] = None) -> Optional[Dict]:
    """调用东方财富单产品行情接口，获取债券/逆回购实时行情。"""
    response = requests.get(
        "https://push2.eastmoney.com/api/qt/stock/get",
        params={
            "secid": eastmoney_secid(product_code, market),
            "ut": "bd1d9ddb04089700cf9c27f6f7426281",
            "fltt": "2",
            "invt": "2",
            "fields": "f43,f44,f45,f46,f47,f48,f55,f57,f58,f60,f170",
        },
        headers={
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            ),
            "Accept": "application/json, text/plain, */*",
            "Referer": "https://quote.eastmoney.com/",
        },
        proxies={},
        timeout=10,
    )
    response.raise_for_status()
    payload = response.json()
    if payload.get("rc") != 0 or not payload.get("data"):
        logger.warning("东方财富债券行情返回空: code=%s rc=%s", product_code, payload.get("rc"))
        return None
    return payload["data"]


def quote_from_eastmoney(product: Dict, raw: Dict, quote_time: Optional[datetime] = None) -> Optional[Dict]:
    """把东方财富字段映射到现有实时行情表字段。"""
    price = safe_decimal(raw.get("f43"))
    prev_close = safe_decimal(raw.get("f60"))
    if price is None:
        price = prev_close
    if price is None:
        return None

    pct_chg = safe_decimal(raw.get("f170"))
    if pct_chg is None and prev_close and prev_close != 0:
        pct_chg = (price - prev_close) / prev_close * Decimal("100")

    return {
        "product_id": product["id"],
        "product_code": raw.get("f57") or product["product_code"],
        "product_name": raw.get("f58") or product.get("product_name") or product["product_code"],
        "quote_time": quote_time or datetime.now().replace(microsecond=0),
        "price": price,
        "prev_close": prev_close,
        "pct_chg": (pct_chg / Decimal("100")) if pct_chg is not None else None,
        "volume": safe_decimal(raw.get("f47")),
        "amount": safe_decimal(raw.get("f48")),
        "open": safe_decimal(raw.get("f46")),
        "high": safe_decimal(raw.get("f44")),
        "low": safe_decimal(raw.get("f45")),
        "source": SOURCE,
    }


def daily_bar_from_quote(quote: Dict, trade_date: Optional[date] = None) -> Dict:
    """首版用当日行情快照生成债券日线，后续可替换为更完整的历史接口。"""
    close_price = quote["price"]
    return {
        "product_id": quote["product_id"],
        "trade_date": trade_date or quote["quote_time"].date(),
        "open": quote.get("open") or close_price,
        "high": quote.get("high") or close_price,
        "low": quote.get("low") or close_price,
        "close": close_price,
        "volume": quote.get("volume"),
        "amount": quote.get("amount"),
        "prev_close": quote.get("prev_close"),
        "source": SOURCE,
    }


class BondCollector:
    """债券/逆回购行情采集器。"""

    def __init__(self, dry_run: bool = False):
        self.dry_run = dry_run
        self.conn = None

    def __enter__(self):
        if not self.dry_run:
            import pymysql
            from config import DB_CONFIG

            self.conn = pymysql.connect(**DB_CONFIG)
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        if self.conn:
            self.conn.close()

    def get_active_bond_products(self) -> List[Dict]:
        """读取产品主数据中的 BOND_REPO 场内产品。"""
        if self.dry_run:
            return [
                {
                    "id": 0,
                    "product_code": "204001",
                    "product_name": "GC001 dry-run",
                    "market": "SH",
                }
            ]

        sql = """
            SELECT id, product_code, product_name, market
            FROM product_master
            WHERE is_active = 1
              AND channel = 'EXCHANGE'
              AND asset_type = 'BOND_REPO'
            ORDER BY sort_order, id
        """
        with self.conn.cursor() as cursor:
            cursor.execute(sql)
            rows = cursor.fetchall()
            return [
                {
                    "id": row[0],
                    "product_code": row[1],
                    "product_name": row[2],
                    "market": row[3],
                }
                for row in rows
            ]

    def save_realtime_quote(self, quote: Dict) -> None:
        """幂等写入实时行情表。"""
        if self.dry_run:
            logger.info("[dry-run] 实时行情: %s %s price=%s", quote["product_code"], quote["product_name"], quote["price"])
            return

        sql = """
            INSERT INTO market_quote_realtime
            (product_id, quote_time, price, prev_close, pct_chg, volume, amount,
             iopv, premium_rate, open_price, high_price, low_price, source)
            VALUES (%s, %s, %s, %s, %s, %s, %s, NULL, NULL, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
                price = VALUES(price),
                prev_close = VALUES(prev_close),
                pct_chg = VALUES(pct_chg),
                volume = VALUES(volume),
                amount = VALUES(amount),
                open_price = VALUES(open_price),
                high_price = VALUES(high_price),
                low_price = VALUES(low_price)
        """
        with self.conn.cursor() as cursor:
            cursor.execute(
                sql,
                (
                    quote["product_id"],
                    quote["quote_time"],
                    quote["price"],
                    quote.get("prev_close"),
                    quote.get("pct_chg"),
                    quote.get("volume"),
                    quote.get("amount"),
                    quote.get("open"),
                    quote.get("high"),
                    quote.get("low"),
                    SOURCE,
                ),
            )
        self.conn.commit()

    def save_daily_bar(self, bar: Dict) -> None:
        """幂等写入日线行情表。"""
        if self.dry_run:
            logger.info("[dry-run] 日线行情: product_id=%s trade_date=%s close=%s", bar["product_id"], bar["trade_date"], bar["close"])
            return

        sql = """
            INSERT INTO market_bar_daily
            (product_id, trade_date, open_price, high_price, low_price, close_price,
             volume, amount, prev_close, source, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
            ON DUPLICATE KEY UPDATE
                open_price = VALUES(open_price),
                high_price = VALUES(high_price),
                low_price = VALUES(low_price),
                close_price = VALUES(close_price),
                volume = VALUES(volume),
                amount = VALUES(amount),
                prev_close = VALUES(prev_close)
        """
        with self.conn.cursor() as cursor:
            cursor.execute(
                sql,
                (
                    bar["product_id"],
                    bar["trade_date"],
                    bar.get("open"),
                    bar.get("high"),
                    bar.get("low"),
                    bar["close"],
                    bar.get("volume"),
                    bar.get("amount"),
                    bar.get("prev_close"),
                    SOURCE,
                ),
            )
        self.conn.commit()

    def fetch_quote_for_product(self, product: Dict) -> Optional[Dict]:
        """采集单只 BOND_REPO 产品的实时行情。"""
        if self.dry_run:
            raw = {
                "f43": "1.895",
                "f44": "2.120",
                "f45": "1.720",
                "f46": "1.880",
                "f47": "128300",
                "f48": "128300000",
                "f57": product["product_code"],
                "f58": product["product_name"],
                "f60": "1.780",
                "f170": "6.46",
            }
            return quote_from_eastmoney(product, raw, datetime(2026, 6, 5, 10, 0, 0))

        raw = fetch_eastmoney_quote(product["product_code"], product.get("market"))
        if not raw:
            return None
        return quote_from_eastmoney(product, raw)

    def collect_realtime(self) -> int:
        """采集并写入 BOND_REPO 实时行情。"""
        return self._collect_products(save_daily=False)

    def collect_daily(self) -> int:
        """采集并写入 BOND_REPO 当日日线。"""
        return self._collect_products(save_daily=True)

    def _collect_products(self, save_daily: bool) -> int:
        products = self.get_active_bond_products()
        if not products:
            logger.info("没有需要采集的 BOND_REPO 产品")
            return 0

        saved_count = 0
        for product in products:
            quote = self.fetch_quote_for_product(product)
            if not quote:
                logger.warning("跳过无有效行情的 BOND_REPO 产品: %s", product["product_code"])
                continue

            if save_daily:
                self.save_daily_bar(daily_bar_from_quote(quote))
            else:
                self.save_realtime_quote(quote)
            saved_count += 1

        logger.info("BOND_REPO %s采集完成，处理 %s 条", "日线" if save_daily else "实时行情", saved_count)
        return saved_count


def main(argv: Optional[Iterable[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="采集 BOND_REPO 债券/逆回购行情")
    parser.add_argument("--type", choices=("realtime", "daily"), default="realtime", help="采集类型")
    parser.add_argument("--dry-run", action="store_true", help="只验证解析流程，不连接数据库、不写入数据")
    args = parser.parse_args(argv)

    with BondCollector(dry_run=args.dry_run) as collector:
        count = collector.collect_daily() if args.type == "daily" else collector.collect_realtime()
    print(f"BOND_REPO {args.type} collection finished: {count} item(s), dry_run={args.dry_run}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
