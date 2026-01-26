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
    - ETF / LOF / 股票：asset_type in ('ETF', 'LOF', 'STOCK') → 写入 market_bar_daily（日 K）
    - 期货：asset_type = 'FUTURES' → 写入 market_bar_daily（日 K）
    - 期权：asset_type = 'OPTIONS' → 写入 market_bar_daily（日 K）

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
import os
import io
import re
from datetime import date, datetime, timedelta
from typing import List, Dict, Optional

# ============================================================
# 【重要】设置 UTF-8 编码，解决 Windows 控制台中文乱码
# ============================================================
if sys.platform == 'win32':
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    else:
        os.environ['PYTHONIOENCODING'] = 'utf-8'

import pymysql
import akshare as ak
import requests
import ssl
import urllib3
import math
from urllib3.util.ssl_ import create_urllib3_context
from requests.adapters import HTTPAdapter

# 禁用 SSL 警告（民生银行接口使用 verify=False）
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# 重要：禁用环境变量代理（例如 http(s)_proxy 指向 127.0.0.1:7890），
# 否则在没有本地代理服务时会导致所有 requests 连接失败。
def _new_session_no_proxy() -> requests.Session:
    s = requests.Session()
    s.trust_env = False
    return s

# 将 scripts 目录加入路径，导入现有配置
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(CURRENT_DIR))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)
if os.path.dirname(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, os.path.dirname(CURRENT_DIR))

from market.config import DB_CONFIG  # noqa: E402

# 交易日历缓存（避免重复查询）
_TRADING_DAYS_CACHE: Dict[str, List[date]] = {}


def get_trading_days_from_akshare(start_date: date, end_date: date) -> List[date]:
    """使用 akshare 获取指定日期范围内的所有交易日（排除周末和节假日）。"""
    cache_key = f"{start_date}_{end_date}"
    if cache_key in _TRADING_DAYS_CACHE:
        return _TRADING_DAYS_CACHE[cache_key]
    
    try:
        df = ak.tool_trade_date_hist_sina()
        if df is None or df.empty:
            return []
        
        trading_days = []
        date_column = None
        
        for col in df.columns:
            if 'date' in col.lower() or '日期' in col or 'time' in col.lower():
                date_column = col
                break
        
        if date_column is None:
            date_column = df.columns[0]
        
        for _, row in df.iterrows():
            trade_date_val = row.get(date_column)
            if trade_date_val is None:
                continue
            
            try:
                trade_date = None
                if hasattr(trade_date_val, 'date'):
                    trade_date = trade_date_val.date()
                elif isinstance(trade_date_val, str):
                    trade_date_str = trade_date_val.strip()
                    if len(trade_date_str) == 8:
                        trade_date = datetime.strptime(trade_date_str, '%Y%m%d').date()
                    elif len(trade_date_str) >= 10:
                        trade_date = datetime.strptime(trade_date_str[:10], '%Y-%m-%d').date()
                elif isinstance(trade_date_val, date):
                    trade_date = trade_date_val
                
                if trade_date and start_date <= trade_date <= end_date:
                    trading_days.append(trade_date)
            except (ValueError, TypeError, AttributeError):
                continue
        
        trading_days = sorted(list(set(trading_days)))
        _TRADING_DAYS_CACHE[cache_key] = trading_days
        return trading_days
    except Exception:
        return []


def is_trading_day(d: date, trading_days_cache: List[date] = None) -> bool:
    """
    判断指定日期是否为交易日（基于 akshare 交易日历）。
    
    Args:
        d: 日期对象
        trading_days_cache: 交易日列表缓存（可选，如果提供则直接使用，否则查询 akshare）
        
    Returns:
        True 如果是交易日，False 如果不是
    """
    if trading_days_cache is not None:
        return d in trading_days_cache
    
    # 如果没有提供缓存，查询 akshare（但这样效率较低，建议先获取交易日列表）
    trading_days = get_trading_days_from_akshare(d, d)
    return len(trading_days) > 0 and trading_days[0] == d


class LegacySSLAdapter(HTTPAdapter):
    """
    支持 legacy SSL 重新协商的适配器（严格参考 v1 的 adaptor/cmbc_client.py）。

    说明：
    - 使用 urllib3 的 create_urllib3_context 创建自定义 SSLContext
    - 通过 OP_LEGACY_SERVER_CONNECT（或常量值 0x4）开启不安全的 legacy renegotiation
    - 同时关闭主机名校验，并将 verify_mode 设为 CERT_NONE
    """

    def init_poolmanager(self, *args, **kwargs):
        ctx = create_urllib3_context()
        # 允许 legacy renegotiation（Python 3.12+ 默认禁用）
        try:
            if hasattr(ssl, "OP_LEGACY_SERVER_CONNECT"):
                ctx.options |= ssl.OP_LEGACY_SERVER_CONNECT  # 与 v1 保持一致
            else:
                # Python 3.12+ 使用数值 0x4
                ctx.options |= 0x4
        except Exception:
            # 如果设置失败，尝试继续使用默认配置
            pass

        # 必须同时设置 check_hostname / verify_mode（顺序同 v1）
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


def _query_latest_cmbc_nav(product_code: str, query_date: date, retry_num: int) -> List[Dict]:
    """
    严格参考 v1 的 cmbc_client.query_latest_nav 实现单日查询 + 回溯逻辑。

    与 v1 的差异：
    - 使用当前脚本的 _normalize_cmbc_nav_record，直接返回 nav_date 为 date 类型，便于后续写入 DB。
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

    try:
        print(f"[CMBC] 开始获取基金 {product_code} {query_date} 的净值 (重试={retry_num})")
        # 创建支持 legacy SSL 的 session（用法与 v1 完全一致）
        session = _new_session_no_proxy()
        session.mount("https://", LegacySSLAdapter())

        resp = session.post(
            "https://www.cmbcwm.com.cn/gw/po_web/BTADailyQry",
            data={
                "chart_type": "1",
                "real_prd_code": product_code,
                "begin_date": query_date.strftime("%Y%m%d"),
                "end_date": query_date.strftime("%Y%m%d"),
            },
            headers=headers,
            timeout=10,
            verify=False,  # 与 v1 完全一致
        )
        data = resp.json()
    except Exception as e:  # noqa: BLE001
        print(f"[CMBC] 获取最新净值失败: {e}")
        raise

    raw_list = data.get("list") or []

    # 无数据时，向前回溯最多 15 天（与 v1 行为一致）
    if not raw_list and retry_num < 15:
        retry_num += 1
        prev_date = query_date - timedelta(days=1)
        print(f"[CMBC] 当前日期 {query_date} 无净值数据，回溯到前一天 {prev_date}（重试 {retry_num} 次）")
        return _query_latest_cmbc_nav(product_code, prev_date, retry_num)

    if not raw_list:
        print(f"[CMBC] 已重试 {retry_num} 次，仍无数据")
        return []

    # 只取第一条记录，做标准化（与 v1 的“只用一条”保持一致）
    try:
        normalized = _normalize_cmbc_nav_record(raw_list[0], product_code)
        print(f"[CMBC] 产品 {product_code} 净值获取成功，日期 {normalized['nav_date']}, nav={normalized['nav']}")
        return [normalized]
    except Exception as e:  # noqa: BLE001
        print(f"[CMBC] 解析净值记录失败: {e}")
        return []


def query_cmbc_nav_for_range(product_code: str, start_date: date, end_date: date) -> List[Dict]:
    """
    从民生银行接口按日期区间拉取历史净值。

    实现方式：
    - **严格参考 v1 的单日查询逻辑**（_query_latest_cmbc_nav），逐日调用并回溯最多15天；
    - 只在 Python 侧做日期循环和去重，HTTP 部分完全与 v1 保持一致。
    """
    all_records: List[Dict] = []
    cur = start_date
    success_count = 0
    fail_count = 0

    while cur <= end_date:
        # 跳过周末
        if cur.weekday() >= 5:
            cur += timedelta(days=1)
            continue

        try:
            daily_records = _query_latest_cmbc_nav(product_code, cur, 0)
            if daily_records:
                all_records.extend(daily_records)
                success_count += 1
        except Exception as e:  # noqa: BLE001
            print(f"[CMBC] {product_code} {cur} 单日查询失败: {e}")
            fail_count += 1

        cur += timedelta(days=1)

        if (success_count + fail_count) % 50 == 0:
            print(f"[CMBC] {product_code} 进度: 成功 {success_count}, 失败 {fail_count}, 当前日期 {cur}")

    # 去重：不同日期可能因为回溯指向同一 nav_date，这里按 nav_date 去重
    dedup: Dict[date, Dict] = {}
    for r in all_records:
        dedup[r["nav_date"]] = r
    out = list(dedup.values())
    out.sort(key=lambda x: x["nav_date"])

    print(f"[CMBC] {product_code} 完成: 成功 {success_count}, 失败 {fail_count}, 去重后 {len(out)} 条")
    return out


class FundHistoryBackfiller:
    """为所有场外产品补齐历史净值的执行器。"""

    START_DATE = date(2000, 1, 1)
    # 民生银行理财产品的起始日期（这类产品 2020 年之前不存在）
    CMBC_START_DATE = date(2023, 1, 1)

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

    def get_exchange_products(self) -> List[Dict]:
        """
        获取所有需要补齐历史行情的场内产品。

        - ETF / LOF / 股票：asset_type in ('ETF', 'LOF', 'STOCK')
        - 期货：asset_type = 'FUTURES'
        - 期权：asset_type = 'OPTIONS'
        """
        sql = """
            SELECT id, product_code, product_name, asset_type, market, data_source
            FROM product_master
            WHERE channel = 'EXCHANGE'
              AND asset_type IN ('ETF', 'LOF', 'STOCK', 'FUTURES', 'OPTIONS')
              AND is_active = 1
        """
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute(sql)
            return cursor.fetchall()

    def get_existing_bar_range(self, product_id: int) -> Optional[Dict]:
        """查询 market_bar_daily 表中某产品已存在的最早/最晚 trade_date。"""
        sql = """
            SELECT MIN(trade_date) AS min_date, MAX(trade_date) AS max_date
            FROM market_bar_daily
            WHERE product_id = %s
        """
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute(sql, (product_id,))
            row = cursor.fetchone()
            if not row or (row["min_date"] is None and row["max_date"] is None):
                return None
            return row

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

    def save_daily_bar_records(self, product_id: int, records: List[Dict], source: str) -> None:
        """
        批量写入 market_bar_daily 记录（带 UPSERT）。

        records: 每个元素必须包含：
            - trade_date: date
            - open, high, low, close: float (对应数据库字段 open_price, high_price, low_price, close_price)
            - volume, amount: float (可选)
        """
        if not records:
            return

        sql = """
            INSERT INTO market_bar_daily (product_id, trade_date, open_price, high_price, low_price, close_price, volume, amount, source, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
            ON DUPLICATE KEY UPDATE
                open_price = VALUES(open_price),
                high_price = VALUES(high_price),
                low_price = VALUES(low_price),
                close_price = VALUES(close_price),
                volume = VALUES(volume),
                amount = VALUES(amount)
        """
        params = []
        for r in records:
            # 确保所有数值字段都不是 NaN，如果是则转换为 0 或 None
            def safe_float(val, default=0):
                if val is None:
                    return default
                if isinstance(val, float) and math.isnan(val):
                    return default
                try:
                    return float(val)
                except (ValueError, TypeError):
                    return default
            
            params.append(
                (
                    product_id,
                    r["trade_date"],
                    safe_float(r.get("open"), 0),
                    safe_float(r.get("high"), 0),
                    safe_float(r.get("low"), 0),
                    safe_float(r.get("close"), 0),
                    safe_float(r.get("volume"), 0),
                    safe_float(r.get("amount"), 0),
                    source,
                )
            )

        with self.conn.cursor() as cursor:
            cursor.executemany(sql, params)
        self.conn.commit()

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
            # 确保所有数值字段都不是 NaN，如果是则转换为 None
            nav = r["nav"]
            if isinstance(nav, float) and math.isnan(nav):
                nav = None
            
            acc_nav = r.get("acc_nav")
            if acc_nav is not None and isinstance(acc_nav, float) and math.isnan(acc_nav):
                acc_nav = None
            
            daily_return = r.get("daily_return")
            if daily_return is not None and isinstance(daily_return, float) and math.isnan(daily_return):
                daily_return = None
            
            dividend = r.get("dividend")
            if dividend is not None and isinstance(dividend, float) and math.isnan(dividend):
                dividend = None
            
            params.append(
                (
                    product_id,
                    r["nav_date"],
                    nav,
                    acc_nav,
                    daily_return,
                    dividend,
                    source,
                )
            )

        with self.conn.cursor() as cursor:
            cursor.executemany(sql, params)
        self.conn.commit()

    # ======== 基金净值采集（使用东方财富HTTP API） ========

    def fetch_history_by_fund_api(self, product_code: str, start_date: date, end_date: date) -> List[Dict]:
        """使用东方财富HTTP API获取基金历史净值。"""
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer": "http://fundf10.eastmoney.com/"
        }
        
        session = _new_session_no_proxy()
        all_records: List[Dict] = []
        page = 1
        page_size = 20
        
        while True:
            try:
                url = f"http://fundf10.eastmoney.com/f10/F10DataApi.aspx?type=lsjz&code={product_code}&page={page}&per={page_size}&sdate={start_date.strftime('%Y-%m-%d')}&edate={end_date.strftime('%Y-%m-%d')}"
                response = session.get(url, headers=headers, timeout=10)
                
                if response.status_code != 200:
                    break
                
                html_text = response.text
                records_match = re.search(r'records:(\d+)', html_text)
                total_records = int(records_match.group(1)) if records_match else 0
                
                if total_records == 0:
                    break
                
                content_match = re.search(r'content:"(.*?)",records:', html_text, re.DOTALL)
                if not content_match:
                    break
                
                html_content = content_match.group(1)
                html_content = html_content.replace(r'<\/td>', '</td>').replace(r'<\/tr>', '</tr>')
                
                records = self._parse_fund_html_table(html_content)
                if not records:
                    break
                
                for record in records:
                    try:
                        nav_date_str = record.get('jzrq', '').strip()
                        if not nav_date_str:
                            continue
                        nav_date = datetime.strptime(nav_date_str, '%Y-%m-%d').date()
                        
                        dwjz = record.get('dwjz', '').strip()
                        if not dwjz or dwjz == '---':
                            continue

                        try:
                            nav = float(dwjz)
                        except Exception:
                            continue
                        
                        ljjz = record.get('ljjz', '').strip()
                        try:
                            acc_nav = nav if '%' in str(ljjz) else (float(ljjz) if ljjz else nav)
                        except (ValueError, TypeError):
                            acc_nav = nav

                        jzzzl = (record.get('jzzzl', '0') or '').strip()
                        daily_return = None
                        if jzzzl and jzzzl != '---' and '%' in jzzzl:
                            try:
                                daily_return = float(jzzzl.replace('%', '')) / 100.0
                            except Exception:
                                pass
                        
                        all_records.append({
                            "nav_date": nav_date,
                            "nav": nav,
                            "acc_nav": acc_nav,
                            "daily_return": daily_return,
                            "dividend": None,
                        })
                    except Exception:
                        continue
                
                if page * page_size >= total_records:
                    break
                
                page += 1
                
            except Exception:
                break
        
        all_records.sort(key=lambda x: x["nav_date"])
        return all_records
    
    def _parse_fund_html_table(self, html_content: str) -> List[Dict]:
        """解析东方财富返回的HTML表格，返回所有数据行"""
        # 提取tbody中的所有数据行
        tbody_match = re.search(r'<tbody>(.*?)</tbody>', html_content, re.DOTALL)
        if not tbody_match:
            # 没有 tbody，直接解析所有 tr
            tr_pattern = r'<tr>(.*?)</tr>'
            rows = re.findall(tr_pattern, html_content, re.DOTALL)
            rows = rows[1:] if rows else []  # 跳过表头
        else:
            tbody_content = tbody_match.group(1)
            tr_pattern = r'<tr>(.*?)</tr>'
            rows = re.findall(tr_pattern, tbody_content, re.DOTALL)
        
        records = []
        for row in rows:
            # 提取单元格 <td>...</td>
            td_pattern = r'<td[^>]*>(.*?)</td>'
            cells = re.findall(td_pattern, row)
            
            if len(cells) >= 3:
                records.append({
                    'jzrq': cells[0].strip(),
                    'dwjz': cells[1].strip(),
                    'ljjz': cells[2].strip(),
                    'jzzzl': cells[3].strip() if len(cells) > 3 else '0',
                })
        
        return records

    def fetch_history_by_akshare_mmf(self, product_code: str, start_date: date, end_date: date, force_nav_one: bool = False) -> List[Dict]:
        """
        使用东方财富HTTP API获取货币基金历史数据（货币基金也使用fund API）。
        
        货币基金特点：
        - nav 表中单位净值固定为 1.0
        - 收益通过分红或「每万份收益」体现
        """
        # 货币基金也使用fund API，但nav固定为1.0
        records = self.fetch_history_by_fund_api(product_code, start_date, end_date)
        
        # 将所有记录的nav改为1.0
        for record in records:
            record["nav"] = 1.0
            # 如果有daily_return，可以记录到dividend字段
            if record.get("daily_return") is not None:
                # 将日增长率转换为每万份收益（近似）
                record["dividend"] = record["daily_return"] * 10000
        
        return records

    def fetch_history_by_akshare_fund(self, product_code: str, start_date: date, end_date: date) -> List[Dict]:
        """
        使用 akshare 获取基金历史净值数据（主要方案）。
        
        优先尝试 fund_etf_fund_info_em，如果失败则尝试 fund_open_fund_info_em。
        
        Args:
            product_code: 基金代码
            start_date: 开始日期
            end_date: 结束日期
            
        Returns:
            历史净值记录列表
        """
        # 如果补齐区间很小（只有1-2天），放宽查询范围以避免因为单日无数据导致全部被过滤
        # 查询最近30天的数据，然后再根据实际日期范围过滤
        # 注意：akshare 的接口通常返回所有历史数据，所以不需要特别放宽查询范围
        # 但如果日期范围很大（比如从2000年开始），akshare 可能只返回最近的数据
        # 所以我们需要使用较宽的查询范围，但最终过滤时使用实际需要的范围
        query_start_date = start_date
        query_end_date = end_date
        
        # 放宽查询范围以确保能获取到数据
        if (end_date - start_date).days > 365:
            query_start_date = self.START_DATE
        elif (end_date - start_date).days <= 2:
            query_start_date = max(start_date - timedelta(days=30), self.START_DATE)
        
        # 方法1: 尝试 fund_etf_fund_info_em（适用于ETF和部分LOF）
        try:
            df = ak.fund_etf_fund_info_em(fund=product_code)
            if df is not None and not df.empty:
                records = self._parse_akshare_fund_dataframe(df, product_code, query_start_date, end_date, start_date, end_date, "fund_etf_fund_info_em")
                if records:
                    return records
        except Exception:
            pass
        
        # 方法2: 尝试 fund_open_fund_info_em（适用于开放式基金）
        try:
            df = ak.fund_open_fund_info_em(symbol=product_code, indicator="单位净值走势")
            if df is not None and not df.empty:
                records = self._parse_akshare_fund_dataframe(df, product_code, query_start_date, end_date, start_date, end_date, "fund_open_fund_info_em")
                if records:
                    return records
        except Exception:
            pass
        
        return []
    
    def _parse_akshare_fund_dataframe(self, df, product_code: str, query_start_date: date, query_end_date: date, filter_start_date: date, filter_end_date: date, source: str) -> List[Dict]:
        """
        解析 akshare 返回的 DataFrame，提取历史净值数据
        """
        records: List[Dict] = []
        
        # 列名映射
        date_columns = ['净值日期', '日期', 'date', '交易日期', '净值日期时间']
        nav_columns = ['单位净值', '累计净值', '净值', 'nav', '单位净值(元)', '累计净值(元)']
        return_columns = ['日增长率', '涨跌幅', '日涨跌幅', 'return', '日收益率']
        
        for _, row in df.iterrows():
            try:
                # 获取日期
                date_val = None
                for col in date_columns:
                    if col in df.columns:
                        date_val = row.get(col)
                        if date_val is not None:
                            break
                
                if date_val is None:
                    date_val = row.name if hasattr(row, 'name') else None
                
                if date_val is None:
                    continue
                
                # 解析日期
                try:
                    import pandas as pd
                    if pd.isna(date_val):
                        continue
                except (ImportError, AttributeError, TypeError):
                    pass
                
                nav_date = pd_to_date(date_val)
                if nav_date is None:
                    continue
                
                # 日期范围过滤
                if not (query_start_date <= nav_date <= query_end_date):
                    continue
                
                # 获取净值
                nav = None
                for col in nav_columns:
                    if col in df.columns:
                        nav = row.get(col)
                        if nav is not None:
                            break
                
                if nav is None:
                    continue
                
                try:
                    nav_float = float(nav)
                except (ValueError, TypeError):
                    continue
                
                if math.isnan(nav_float) or nav_float <= 0:
                    continue
                
                # 累计净值
                acc_nav = nav_float
                for col in ['累计净值', '累计净值(元)', 'acc_nav']:
                    if col in df.columns:
                        acc_val = row.get(col)
                        if acc_val is not None:
                            try:
                                acc_nav = float(acc_val)
                                if math.isnan(acc_nav):
                                    acc_nav = nav_float
                            except (ValueError, TypeError):
                                pass
                        break
                
                # 日增长率
                daily_return_float = None
                for col in return_columns:
                    if col in df.columns:
                        daily_return = row.get(col)
                        if daily_return is not None:
                            try:
                                if isinstance(daily_return, str):
                                    daily_return = daily_return.replace('%', '').strip()
                                daily_return_val = float(daily_return)
                                if not math.isnan(daily_return_val):
                                    daily_return_float = daily_return_val / 100.0 if abs(daily_return_val) > 1 else daily_return_val
                            except (ValueError, TypeError):
                                pass
                        break
                
                records.append({
                    "nav_date": nav_date,
                    "nav": nav_float,
                    "acc_nav": acc_nav,
                    "daily_return": daily_return_float
                })
                
            except Exception:
                continue
        
        # 最终日期范围过滤
        return [r for r in records if filter_start_date <= r["nav_date"] <= filter_end_date]

    def fetch_history_by_akshare_stock(self, product_code: str, market: str, start_date: date, end_date: date) -> List[Dict]:
        """
        使用 akshare 获取ETF/股票历史日K线数据（参考akshare_client.py）。
        
        优先使用 fund_etf_hist_em（ETF专用接口），如果失败则使用 stock_zh_a_hist。

        Args:
            product_code: 产品代码（6位代码）
            market: 市场（SH/SZ）
            start_date: 开始日期
            end_date: 结束日期

        Returns:
            历史日K线记录列表
        """
        # 优先尝试使用 fund_etf_hist_em（ETF专用接口，参考akshare_client.py）
        try:
            df = ak.fund_etf_hist_em(
                symbol=product_code,
                period='daily',
                start_date=start_date.strftime('%Y%m%d'),
                end_date=end_date.strftime('%Y%m%d'),
                adjust=''
            )
            
            if df is not None and not df.empty:
                records: List[Dict] = []
                for _, row in df.iterrows():
                    try:
                        # fund_etf_hist_em 返回中文列名
                        date_str = str(row.get('日期', ''))
                        if not date_str:
                            continue
                        trade_date = pd_to_date(date_str)
                        
                        records.append({
                            "trade_date": trade_date,
                            "open": float(row.get('开盘', 0)) if row.get('开盘') is not None else 0,
                            "high": float(row.get('最高', 0)) if row.get('最高') is not None else 0,
                            "low": float(row.get('最低', 0)) if row.get('最低') is not None else 0,
                            "close": float(row.get('收盘', 0)) if row.get('收盘') is not None else 0,
                            "volume": float(row.get('成交量', 0)) if row.get('成交量') is not None else 0,
                            "amount": float(row.get('成交额', 0)) if row.get('成交额') is not None else 0,
                        })
                    except Exception:  # noqa: BLE001
                        continue
                
                if records:
                    return records
        except Exception:
            pass
        
        # 备用方案：使用 stock_zh_a_hist
        try:
            df = ak.stock_zh_a_hist(
                symbol=product_code,
                period="daily",
                start_date=start_date.strftime('%Y%m%d'),
                end_date=end_date.strftime('%Y%m%d'),
                adjust=""
            )
        except Exception:
            return []

        if df is None or df.empty:
            return []

        records: List[Dict] = []
        for _, row in df.iterrows():
            try:
                trade_date_raw = row.get("日期")
                if not trade_date_raw:
                    continue
                trade_date = pd_to_date(trade_date_raw)

                records.append(
                    {
                        "trade_date": trade_date,
                        "open": float(row.get("开盘", 0)) if row.get("开盘") is not None else 0,
                        "high": float(row.get("最高", 0)) if row.get("最高") is not None else 0,
                        "low": float(row.get("最低", 0)) if row.get("最低") is not None else 0,
                        "close": float(row.get("收盘", 0)) if row.get("收盘") is not None else 0,
                        "volume": float(row.get("成交量", 0)) if row.get("成交量") is not None else 0,
                        "amount": float(row.get("成交额", 0)) if row.get("成交额") is not None else 0,
                    }
                )
            except Exception:  # noqa: BLE001
                continue

        return records

    def fetch_history_by_akshare_futures(self, product_code: str, start_date: date, end_date: date) -> List[Dict]:
        """使用 akshare 获取期货历史日K线数据。"""
        try:
            df = ak.futures_main_sina(symbol=product_code)
        except Exception:
            return []

        if df is None or df.empty:
            return []

        records: List[Dict] = []
        for _, row in df.iterrows():
            try:
                trade_date_raw = row.get("日期") or row.get("date")
                if not trade_date_raw:
                    continue
                trade_date = pd_to_date(trade_date_raw)

                if start_date <= trade_date <= end_date:
                    records.append({
                        "trade_date": trade_date,
                        "open": float(row.get("开盘", row.get("open", 0))),
                        "high": float(row.get("最高", row.get("high", 0))),
                        "low": float(row.get("最低", row.get("low", 0))),
                        "close": float(row.get("收盘", row.get("close", 0))),
                        "volume": float(row.get("成交量", row.get("volume", 0))),
                        "amount": float(row.get("成交额", row.get("amount", 0))),
                    })
            except Exception:
                continue

        return records

    def fetch_history_by_akshare_options(self, product_code: str, start_date: date, end_date: date) -> List[Dict]:
        """使用 akshare 获取期权历史日K线数据。"""
        # 暂未实现
        return []

    # ======== 主流程 ========

    def backfill_for_product(self, product: Dict) -> None:
        product_id = product["id"]
        code = product["product_code"]
        name = product["product_name"]
        asset_type = product["asset_type"]
        data_source = (product.get("data_source") or "").lower()

        print(f"\n==== {name} ({code}) [{asset_type}] ====")

        existing = self.get_existing_nav_range(product_id)
        today = date.today()
        
        is_cmbc = (asset_type == "BANK_WM_NAV" or data_source == "cmbc" or code == "FBAE41126E")
        
        if is_cmbc:
            if existing and existing.get("max_date"):
                start_date = existing["max_date"] + timedelta(days=1)
            else:
                start_date = self.CMBC_START_DATE
        else:
            if existing and existing.get("max_date"):
                start_date = existing["max_date"] + timedelta(days=1)
            else:
                start_date = self.START_DATE
        
        if start_date > today:
            print(f"  已是最新")
            return

        # 获取交易日列表
        trading_days = get_trading_days_from_akshare(start_date, today)
        if not trading_days:
            print(f"  无交易日需要补齐")
            return
        
        actual_start_date = trading_days[0]
        actual_end_date = trading_days[-1]
        print(f"  交易日范围: {actual_start_date} ~ {actual_end_date} ({len(trading_days)} 天)")

        records: List[Dict] = []
        source_flag = "FUND"

        # 民生银行净值型理财
        if asset_type == "BANK_WM_NAV" or data_source == "cmbc" or code == "FBAE41126E":
            records = query_cmbc_nav_for_range(code, start_date, today)
            source_flag = "CMBC"
        # 货币基金
        elif asset_type == "MMF":
            records = self.fetch_history_by_akshare_mmf(code, start_date, today, force_nav_one=True)
            source_flag = "MMF"
        # 普通基金/LOF
        else:
            records = self.fetch_history_by_akshare_fund(code, actual_start_date, actual_end_date)
            source_flag = "AKSHARE"
            # 后备方案：东方财富 HTTP API
            if not records:
                try:
                    records = self.fetch_history_by_fund_api(code, actual_start_date, actual_end_date)
                    if records:
                        source_flag = "FUND"
                except Exception:
                    pass
            
            # 过滤交易日数据
            if records and trading_days:
                trading_days_set = set(trading_days)
                records = [r for r in records if r.get("nav_date") in trading_days_set]

        if not records:
            print(f"  未获取到数据（数据源可能未更新）")
            return

        # 过滤并写入
        filtered = [r for r in records if start_date <= r["nav_date"] <= today]
        if trading_days:
            trading_days_set = set(trading_days)
            filtered = [r for r in filtered if r["nav_date"] in trading_days_set]
        filtered.sort(key=lambda x: x["nav_date"])

        if not filtered:
            print(f"  无新数据需要写入")
            return

        self.save_nav_records(product_id, filtered, source_flag)
        print(f"  ✓ 写入 {len(filtered)} 条记录")

    def backfill_for_exchange_product(self, product: Dict) -> None:
        """为场内产品补齐历史日K线数据。"""
        product_id = product["id"]
        code = product["product_code"]
        name = product["product_name"]
        asset_type = product["asset_type"]
        market = product.get("market", "SH")

        print(f"\n==== {name} ({code}) [{asset_type}] ====")

        existing = self.get_existing_bar_range(product_id)
        today = date.today()
        
        if existing and existing.get("max_date"):
            start_date = existing["max_date"] + timedelta(days=1)
        else:
            start_date = self.START_DATE
        
        if start_date > today:
            print(f"  已是最新")
            return

        records: List[Dict] = []
        source_flag = "AKSHARE"

        if asset_type in ("ETF", "LOF", "STOCK"):
            records = self.fetch_history_by_akshare_stock(code, market, start_date, today)
            source_flag = asset_type
        elif asset_type == "FUTURES":
            records = self.fetch_history_by_akshare_futures(code, start_date, today)
            source_flag = "FUTURES"
        elif asset_type == "OPTIONS":
            records = self.fetch_history_by_akshare_options(code, start_date, today)
            source_flag = "OPTIONS"

        if not records:
            print(f"  未获取到数据")
            return

        # 过滤交易日
        trading_days = get_trading_days_from_akshare(start_date, today)
        filtered = [r for r in records if start_date <= r["trade_date"] <= today]
        if trading_days:
            trading_days_set = set(trading_days)
            filtered = [r for r in filtered if r["trade_date"] in trading_days_set]
        filtered.sort(key=lambda x: x["trade_date"])

        if not filtered:
            print(f"  无新数据")
            return

        self.save_daily_bar_records(product_id, filtered, source_flag)
        print(f"  ✓ 写入 {len(filtered)} 条记录")

    def run(self) -> None:
        try:
            # 补齐场外产品净值
            otc_products = self.get_otc_products()
            print(f"共找到 {len(otc_products)} 个场外产品需要检查补齐历史净值")
            for p in otc_products:
                self.backfill_for_product(p)

            # 补齐场内产品日K线
            exchange_products = self.get_exchange_products()
            print(f"\n共找到 {len(exchange_products)} 个场内产品需要检查补齐历史日K线")
            for p in exchange_products:
                self.backfill_for_exchange_product(p)
        finally:
            self.close_db()


def pd_to_date(val) -> Optional[date]:
    """将 pandas / 字符串 日期统一转换为 date 对象。"""
    if val is None:
        return None
    
    # 检查是否为 pandas NaT (Not a Time)
    try:
        import pandas as pd
        if pd.isna(val) or val is pd.NaT:
            return None
    except (ImportError, AttributeError, TypeError):
        pass
    
    # 处理 pandas Timestamp 类型（通过检查是否有 date() 方法）
    if hasattr(val, 'date') and callable(getattr(val, 'date')):
        try:
            # 再次检查是否为 NaT
            if str(val) == 'NaT' or str(val) == 'nat':
                return None
            return val.date()
        except (ValueError, TypeError, AttributeError):
            return None
    
    # 处理 date 类型
    if isinstance(val, date) and not isinstance(val, datetime):
        return val
    
    # 处理 datetime 类型
    if isinstance(val, datetime):
        return val.date()
    
    # 处理字符串
    s = str(val).strip()
    if not s or s == 'NaT' or s == 'nan' or s == 'nat' or s.lower() == 'nat':
        return None
    
    # 尝试多种日期格式
    date_formats = [
        "%Y-%m-%d",
        "%Y/%m/%d",
        "%Y%m%d",
        "%Y-%m-%d %H:%M:%S",
        "%Y/%m/%d %H:%M:%S",
    ]
    
    for fmt in date_formats:
        try:
            if len(s) >= len(fmt.replace('%', '').replace(':', '').replace(' ', '').replace('-', '').replace('/', '')):
                return datetime.strptime(s[:len(fmt)], fmt).date()
        except (ValueError, TypeError):
            continue
    
    # 兜底：尝试 fromisoformat
    try:
        return datetime.fromisoformat(s.replace('Z', '+00:00')[:19]).date()
    except Exception:
        pass
    
    return None


if __name__ == "__main__":
    backfiller = FundHistoryBackfiller()
    backfiller.run()

