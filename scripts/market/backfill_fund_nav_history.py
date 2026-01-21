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
import re
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

        - ETF / 股票：asset_type in ('ETF', 'STOCK')
        - 期货：asset_type = 'FUTURES'
        - 期权：asset_type = 'OPTIONS'
        """
        sql = """
            SELECT id, product_code, product_name, asset_type, market, data_source
            FROM product_master
            WHERE channel = 'EXCHANGE'
              AND asset_type IN ('ETF', 'STOCK', 'FUTURES', 'OPTIONS')
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
            params.append(
                (
                    product_id,
                    r["trade_date"],
                    r.get("open", 0),
                    r.get("high", 0),
                    r.get("low", 0),
                    r.get("close", 0),
                    r.get("volume", 0),
                    r.get("amount", 0),
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

    # ======== 基金净值采集（使用东方财富HTTP API） ========

    def fetch_history_by_fund_api(self, product_code: str, start_date: date, end_date: date) -> List[Dict]:
        """
        使用东方财富HTTP API获取基金历史净值（参考fund_client.py）。
        
        Args:
            product_code: 基金代码
            start_date: 开始日期
            end_date: 结束日期
            
        Returns:
            历史净值记录列表
        """
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            # fund.eastmoney.com 在部分网络环境可能无法解析/连通；fundf10.eastmoney.com 更稳定
            "Referer": "http://fundf10.eastmoney.com/"
        }
        
        session = _new_session_no_proxy()
        
        all_records: List[Dict] = []
        page = 1
        page_size = 20
        
        while True:
            try:
                # 东方财富历史净值 API，支持日期范围
                url = f"http://fundf10.eastmoney.com/f10/F10DataApi.aspx?type=lsjz&code={product_code}&page={page}&per={page_size}&sdate={start_date.strftime('%Y-%m-%d')}&edate={end_date.strftime('%Y-%m-%d')}"
                response = session.get(url, headers=headers, timeout=10)
                
                if response.status_code != 200:
                    print(f"[fund_api] HTTP {response.status_code}")
                    break
                
                html_text = response.text
                
                # 提取总记录数
                records_match = re.search(r'records:(\d+)', html_text)
                total_records = int(records_match.group(1)) if records_match else 0
                
                if total_records == 0:
                    break
                
                # 提取 content 中的HTML内容
                content_match = re.search(r'content:"(.*?)",records:', html_text, re.DOTALL)
                if not content_match:
                    break
                
                html_content = content_match.group(1)
                html_content = html_content.replace(r'<\/td>', '</td>').replace(r'<\/tr>', '</tr>')
                
                # 解析HTML表格
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

                        # 有些行会出现“暂停申购/开放申购/开放赎回”等非数字文本，直接跳过
                        try:
                            nav = float(dwjz)
                        except Exception:
                            continue
                        
                        ljjz = record.get('ljjz', '').strip()
                        # 处理累计净值：如果包含百分号或不是有效数字，则使用单位净值
                        try:
                            if '%' in str(ljjz):
                                acc_nav = nav
                            else:
                                acc_nav = float(ljjz) if ljjz else nav
                        except (ValueError, TypeError):
                            acc_nav = nav

                        jzzzl = (record.get('jzzzl', '0') or '').strip()
                        daily_return = None
                        if jzzzl and jzzzl != '---' and '%' in jzzzl:
                            try:
                                daily_return = float(jzzzl.replace('%', '')) / 100.0
                            except Exception:
                                daily_return = None
                        
                        all_records.append({
                            "nav_date": nav_date,
                            "nav": nav,
                            "acc_nav": acc_nav,
                            "daily_return": daily_return,
                            "dividend": None,
                        })
                    except Exception as e:  # noqa: BLE001
                        print(f"[fund_api] 解析记录失败: {e}")
                        continue
                
                # 检查是否还有更多页
                if page * page_size >= total_records:
                    break
                
                page += 1
                
            except Exception as e:  # noqa: BLE001
                print(f"[fund_api] 获取历史净值失败 (page={page}): {e}")
                break
        
        # 按日期升序排列
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
        except Exception as e:  # noqa: BLE001
            print(f"[akshare] fund_etf_hist_em({product_code}) 失败，尝试备用接口: {e}")
        
        # 备用方案：使用 stock_zh_a_hist
        try:
            df = ak.stock_zh_a_hist(
                symbol=product_code,
                period="daily",
                start_date=start_date.strftime('%Y%m%d'),
                end_date=end_date.strftime('%Y%m%d'),
                adjust=""
            )
        except Exception as e:  # noqa: BLE001
            print(f"[akshare] stock_zh_a_hist({product_code}) 失败: {e}")
            return []

        if df is None or df.empty:
            print(f"[akshare] 未获取到产品 {product_code} 的历史K线数据")
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
        """
        使用 akshare 获取期货历史日K线数据。

        Args:
            product_code: 期货代码
            start_date: 开始日期
            end_date: 结束日期

        Returns:
            历史日K线记录列表
        """
        try:
            # 期货代码格式可能需要调整，这里使用通用接口
            # 注意：akshare的期货接口可能需要根据实际代码格式调整
            df = ak.futures_main_sina(symbol=product_code)
            # 如果接口不支持，可以尝试其他接口
            # df = ak.futures_zh_daily_sina(symbol=product_code, start_date=start_date.strftime('%Y%m%d'), end_date=end_date.strftime('%Y%m%d'))
        except Exception as e:  # noqa: BLE001
            print(f"[akshare] 期货 {product_code} 历史数据获取失败: {e}")
            # 期货接口可能不稳定，这里先返回空，后续可以根据实际情况调整
            return []

        if df is None or df.empty:
            print(f"[akshare] 未获取到期货 {product_code} 的历史数据")
            return []

        records: List[Dict] = []
        # 根据实际返回的列名调整
        for _, row in df.iterrows():
            try:
                trade_date_raw = row.get("日期") or row.get("date")
                if not trade_date_raw:
                    continue
                trade_date = pd_to_date(trade_date_raw)

                if start_date <= trade_date <= end_date:
                    records.append(
                        {
                            "trade_date": trade_date,
                            "open": float(row.get("开盘", row.get("open", 0))),
                            "high": float(row.get("最高", row.get("high", 0))),
                            "low": float(row.get("最低", row.get("low", 0))),
                            "close": float(row.get("收盘", row.get("close", 0))),
                            "volume": float(row.get("成交量", row.get("volume", 0))),
                            "amount": float(row.get("成交额", row.get("amount", 0))),
                        }
                    )
            except Exception:  # noqa: BLE001
                continue

        return records

    def fetch_history_by_akshare_options(self, product_code: str, start_date: date, end_date: date) -> List[Dict]:
        """
        使用 akshare 获取期权历史日K线数据。

        Args:
            product_code: 期权代码
            start_date: 开始日期
            end_date: 结束日期

        Returns:
            历史日K线记录列表
        """
        try:
            # 期权接口可能需要根据实际代码格式调整
            # akshare的期权接口可能不稳定，这里先返回空，后续可以根据实际情况调整
            print(f"[akshare] 期权 {product_code} 历史数据获取暂未实现，需要根据实际接口调整")
            return []
        except Exception as e:  # noqa: BLE001
            print(f"[akshare] 期权 {product_code} 历史数据获取失败: {e}")
            return []

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
        # 如果数据库已覆盖到今天（或更晚），无需再拉取
        if existing and existing.get("max_date") and existing["max_date"] >= today:
            print(f"  已有数据覆盖到 {existing['max_date']}，无需补齐")
            return
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
        # 货币基金
        elif asset_type == "MMF":
            records = self.fetch_history_by_akshare_mmf(code, start_date, today, force_nav_one=True)
            source_flag = "MMF"
        # 普通基金/LOF（使用东方财富HTTP API）
        else:
            records = self.fetch_history_by_fund_api(code, start_date, today)
            source_flag = "FUND"

        if not records:
            print("  未获取到任何历史净值数据，跳过")
            return

        # 只保留需要补齐区间内的数据
        filtered = [r for r in records if start_date <= r["nav_date"] <= today]
        filtered.sort(key=lambda x: x["nav_date"])

        if not filtered:
            # 检查是否有区间外的数据（可能是回溯到的）
            out_of_range = [r for r in records if r["nav_date"] < start_date]
            if out_of_range:
                latest_out = max(out_of_range, key=lambda x: x["nav_date"])
                print(f"  在补齐区间 {start_date} ~ {today} 内没有可写入的数据")
                print(f"  说明：区间内日期无净值更新（可能是周末或产品未更新）")
                print(f"  回溯到的最新数据日期为 {latest_out['nav_date']}，但该日期已在数据库中或早于补齐区间")
            else:
                print(f"  在补齐区间 {start_date} ~ {today} 内没有可写入的数据")
            return

        print(f"  准备写入 {len(filtered)} 条记录...")
        self.save_nav_records(product_id, filtered, source_flag)
        print(f"  ✓ 补齐完成: 写入 {len(filtered)} 条（含已存在记录的 UPSERT）")

    def backfill_for_exchange_product(self, product: Dict) -> None:
        """为场内产品补齐历史日K线数据。"""
        product_id = product["id"]
        code = product["product_code"]
        name = product["product_name"]
        asset_type = product["asset_type"]
        market = product.get("market", "SH")

        print(f"\n==== 开始补齐: {name} ({code}), 类型={asset_type}, 市场={market} ====")

        # 确定起始日期：已有数据则从最后一天之后开始，否则从 START_DATE
        existing = self.get_existing_bar_range(product_id)
        if existing and existing.get("max_date"):
            start_date = existing["max_date"] + timedelta(days=1)
        else:
            start_date = self.START_DATE

        today = date.today()
        # 如果数据库已覆盖到今天（或更晚），无需再拉取
        if existing and existing.get("max_date") and existing["max_date"] >= today:
            print(f"  已有数据覆盖到 {existing['max_date']}，无需补齐")
            return
        if start_date > today:
            print(f"  已有数据覆盖到 {existing['max_date'] if existing else 'N/A'}，无需补齐")
            return

        print(f"  补齐区间: {start_date} ~ {today}")

        records: List[Dict] = []
        source_flag = "AKSHARE"

        # 根据资产类型选择不同的采集方法
        if asset_type in ("ETF", "STOCK"):
            records = self.fetch_history_by_akshare_stock(code, market, start_date, today)
            source_flag = "ETF" if asset_type == "ETF" else "STOCK"
        elif asset_type == "FUTURES":
            records = self.fetch_history_by_akshare_futures(code, start_date, today)
            source_flag = "FUTURES"
        elif asset_type == "OPTIONS":
            records = self.fetch_history_by_akshare_options(code, start_date, today)
            source_flag = "OPTIONS"

        if not records:
            print("  未获取到任何历史K线数据，跳过")
            return

        # 只保留需要补齐区间内的数据
        filtered = [r for r in records if start_date <= r["trade_date"] <= today]
        filtered.sort(key=lambda x: x["trade_date"])

        if not filtered:
            print("  在补齐区间内没有可写入的数据")
            return

        print(f"  准备写入 {len(filtered)} 条记录...")
        self.save_daily_bar_records(product_id, filtered, source_flag)
        print(f"  ✓ 补齐完成: 写入 {len(filtered)} 条（含已存在记录的 UPSERT）")

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

