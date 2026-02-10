#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
基金净值采集脚本
使用akshare采集基金净值数据，写入nav表
参考 V1 版本的简单实现方式
"""
import sys
import os
import io

# ============================================================
# 【重要】设置 UTF-8 编码，解决 Windows 控制台中文乱码
# ============================================================
if sys.platform == 'win32':
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    else:
        os.environ['PYTHONIOENCODING'] = 'utf-8'

# ============================================================
# 【重要】必须在导入任何网络库之前彻底禁用代理
# 公司网络环境可能配置了系统代理，但代理无法正确转发东方财富 API
# ============================================================

# 1. 清除所有代理相关的环境变量
for key in list(os.environ.keys()):
    if 'proxy' in key.lower():
        del os.environ[key]

# 2. 明确设置不使用代理
os.environ['NO_PROXY'] = '*'
os.environ['no_proxy'] = '*'

# 3. Monkey patch urllib3 禁用代理检测（在导入 requests 之前）
import urllib3
urllib3.util.proxy_from_url = lambda url: None

# 4. Monkey patch requests 禁用代理
import requests

# 保存原始函数
_original_session_init = requests.Session.__init__
_original_get = requests.get
_original_post = requests.post

# 浏览器请求头（东方财富服务器可能检测 User-Agent）
_BROWSER_HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'application/json, text/plain, */*',
    'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
    'Accept-Encoding': 'gzip, deflate, br',
    'Connection': 'keep-alive',
    'Referer': 'https://quote.eastmoney.com/',
}

def _patched_session_init(self, *args, **kwargs):
    _original_session_init(self, *args, **kwargs)
    self.trust_env = False  # 不读取系统代理设置
    self.proxies = {}  # 清空代理
    self.headers.update(_BROWSER_HEADERS)  # 添加浏览器请求头

def _patched_get(url, **kwargs):
    kwargs['proxies'] = {}
    # 合并请求头
    headers = kwargs.get('headers', {})
    merged_headers = {**_BROWSER_HEADERS, **headers}
    kwargs['headers'] = merged_headers
    return _original_get(url, **kwargs)

def _patched_post(url, **kwargs):
    kwargs['proxies'] = {}
    # 合并请求头
    headers = kwargs.get('headers', {})
    merged_headers = {**_BROWSER_HEADERS, **headers}
    kwargs['headers'] = merged_headers
    return _original_post(url, **kwargs)

# 应用补丁
requests.Session.__init__ = _patched_session_init
requests.get = _patched_get
requests.post = _patched_post

# 5. 对已存在的 session 实例也进行处理
if hasattr(requests, 'session'):
    _orig_session_func = requests.session
    def _patched_session_func():
        s = _orig_session_func()
        s.trust_env = False
        s.proxies = {}
        s.headers.update(_BROWSER_HEADERS)
        return s
    requests.session = _patched_session_func

# ============================================================
# 代理禁用完成，现在可以安全导入其他库
# ============================================================

# 设置标准输出编码为UTF-8（Windows兼容）
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

import pymysql
import pandas as pd
from datetime import datetime, date
from typing import List, Optional, Dict
import time
import logging

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# 尝试导入 akshare
try:
    import akshare as ak
    AKSHARE_AVAILABLE = True
except ImportError:
    AKSHARE_AVAILABLE = False
    logger.warning("akshare 未安装，基金净值功能不可用")

# 添加父目录到路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from market.config import DB_CONFIG, COLLECT_CONFIG


class FundCollector:
    """基金净值采集器"""
    
    def __init__(self):
        self.conn = None
        self.connect_db()
    
    def connect_db(self):
        """连接数据库"""
        try:
            self.conn = pymysql.connect(**DB_CONFIG)
            print("数据库连接成功")
        except Exception as e:
            print(f"数据库连接失败: {e}")
            raise
    
    def close_db(self):
        """关闭数据库连接"""
        if self.conn:
            self.conn.close()
    
    def get_active_funds(self) -> List[dict]:
        """获取所有启用的基金产品"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            sql = """
                SELECT id, product_code, product_name, asset_type, data_source
                FROM product_master
                WHERE is_active = 1
                AND asset_type IN ('FUND', 'MMF', 'LOF')
                AND channel = 'OTC'
            """
            cursor.execute(sql)
            return cursor.fetchall()
    
    def fetch_fund_nav(self, product_code: str, asset_type: str, db_latest_date: Optional[date] = None) -> List[dict]:
        """
        采集基金净值（获取最近的数据，而不仅仅是最后一条）
        
        Args:
            product_code: 产品代码
            asset_type: 资产类型
            db_latest_date: 数据库中已有的最新净值日期，用于过滤只获取新数据
            
        Returns:
            包含nav_date和nav的字典列表，如果采集失败返回空列表
        """
        if not AKSHARE_AVAILABLE:
            logger.error("akshare 未安装，无法获取基金净值")
            return []
        
        try:
            # 使用akshare采集基金净值
            if asset_type == 'MMF':
                # 货币基金使用货币基金接口
                # 注意：akshare的货币基金接口返回的是每日收益数据，净值固定为1.0
                # 参数名是 symbol，不是 fund
                try:
                    df = ak.fund_money_fund_info_em(symbol=product_code)
                except (AttributeError, TypeError) as e:
                    # 如果函数不存在或参数错误，尝试使用普通基金接口
                    try:
                        df = ak.fund_open_fund_info_em(symbol=product_code, indicator="单位净值走势")
                    except Exception:
                        raise e
            else:
                # 普通基金使用基金净值接口
                # 参数名是 symbol，不是 fund
                try:
                    df = ak.fund_open_fund_info_em(symbol=product_code, indicator="单位净值走势")
                except (AttributeError, TypeError):
                    # 兼容旧版本akshare（如果有的话）
                    try:
                        df = ak.fund_em_open_fund_info(fund=product_code)
                    except (AttributeError, TypeError):
                        # 最后尝试ETF接口
                        df = ak.fund_etf_fund_info_em(fund=product_code)
            
            if df is None or df.empty:
                print(f"未获取到产品 {product_code} 的净值数据")
                return []
            
            # 解析所有净值数据
            nav_list = []
            date_columns = ['净值日期', '日期', 'date', '交易日期']
            nav_columns = ['单位净值', '累计净值', '净值', 'nav', '单位净值(元)']
            
            for _, row in df.iterrows():
                # 查找日期列
                nav_date = None
                for col in date_columns:
                    if col in row and pd.notna(row[col]):
                        nav_date = pd.to_datetime(row[col])
                        break
                
                if nav_date is None:
                    continue
                
                nav_date_obj = nav_date.date() if hasattr(nav_date, 'date') else nav_date
                
                # 如果数据库已有该日期或更晚的日期，跳过
                if db_latest_date and nav_date_obj <= db_latest_date:
                    continue
                
                # 查找净值列
                nav = None
                for col in nav_columns:
                    if col in row and pd.notna(row[col]):
                        try:
                            nav = float(row[col])
                            break
                        except (ValueError, TypeError):
                            continue
                
                if nav is None or nav <= 0:
                    continue
                
                nav_list.append({
                    'nav_date': nav_date_obj,
                    'nav': nav
                })
            
            # 按日期排序（从旧到新）
            nav_list.sort(key=lambda x: x['nav_date'])
            
            return nav_list
        except Exception as e:
            print(f"采集产品 {product_code} 净值失败: {e}")
            import traceback
            traceback.print_exc()
            return []
    
    def get_latest_nav_date(self, product_id: int) -> Optional[date]:
        """获取数据库中该产品的最新净值日期"""
        with self.conn.cursor() as cursor:
            sql = """
                SELECT MAX(nav_date) as latest_date
                FROM nav
                WHERE product_id = %s
            """
            cursor.execute(sql, (product_id,))
            result = cursor.fetchone()
            if result and result[0]:
                return result[0] if isinstance(result[0], date) else result[0].date()
            return None
    
    def save_nav(self, product_id: int, nav_date: date, nav: float):
        """保存净值到数据库（只有当日期更新时才保存）"""
        # 先检查数据库中已有的最新净值日期
        db_latest_date = self.get_latest_nav_date(product_id)
        
        if db_latest_date:
            # 如果akshare返回的日期不新于数据库中的最新日期，跳过
            if nav_date <= db_latest_date:
                print(f"  跳过：数据库中已有 {db_latest_date} 的净值，akshare返回的是 {nav_date}（未更新）")
                return False
        
        with self.conn.cursor() as cursor:
            # 检查是否已存在
            check_sql = """
                SELECT id FROM nav
                WHERE product_id = %s AND nav_date = %s
            """
            cursor.execute(check_sql, (product_id, nav_date))
            if cursor.fetchone():
                # 更新（与当前 nav 表结构对齐：nav + source，created_at 由默认值控制）
                update_sql = """
                    UPDATE nav
                    SET nav = %s, source = 'AKSHARE'
                    WHERE product_id = %s AND nav_date = %s
                """
                cursor.execute(update_sql, (nav, product_id, nav_date))
                print(f"  更新：{nav_date} = {nav}（数据库中已有该日期记录）")
            else:
                # 插入（nav 表没有 updated_at 字段，created_at 使用默认值）
                insert_sql = """
                    INSERT INTO nav (product_id, nav_date, nav, source)
                    VALUES (%s, %s, %s, 'AKSHARE')
                """
                cursor.execute(insert_sql, (product_id, nav_date, nav))
                print(f"  新增：{nav_date} = {nav}")
            self.conn.commit()
            return True
    
    def collect_all(self):
        """采集所有基金的净值"""
        funds = self.get_active_funds()
        print(f"开始采集 {len(funds)} 个基金的净值...")
        
        success_count = 0
        fail_count = 0
        skip_count = 0  # 跳过的数量（日期未更新）
        
        for fund in funds:
            product_id = fund['id']
            product_code = fund['product_code']
            product_name = fund['product_name']
            asset_type = fund['asset_type']
            
            # 先查询数据库中已有的最新日期
            db_latest_date = self.get_latest_nav_date(product_id)
            db_latest_str = db_latest_date.strftime('%Y-%m-%d') if db_latest_date else "无"
            
            print(f"正在采集: {product_name} ({product_code}) [数据库最新: {db_latest_str}]...")
            
            # 获取净值数据列表（只获取比数据库最新日期更新的数据）
            nav_data_list = self.fetch_fund_nav(product_code, asset_type, db_latest_date)
            if nav_data_list:
                # 保存所有新的净值数据
                saved_count = 0
                for nav_data in nav_data_list:
                    saved = self.save_nav(product_id, nav_data['nav_date'], nav_data['nav'])
                    if saved:
                        saved_count += 1
                
                if saved_count > 0:
                    print(f"  ✓ 成功：保存了 {saved_count} 条新净值数据（最新日期: {nav_data_list[-1]['nav_date']}）")
                    success_count += 1
                else:
                    print(f"  - 跳过：所有数据都已存在")
                    skip_count += 1
            else:
                print(f"  ✗ 失败：无法获取净值数据或没有新数据")
                fail_count += 1
            
            # 避免请求过快
            time.sleep(1)
        
        print(f"\n采集完成: 成功 {success_count}, 跳过 {skip_count}（日期未更新）, 失败 {fail_count}")
    
    def run(self):
        """运行采集任务"""
        try:
            self.collect_all()
        finally:
            self.close_db()


if __name__ == '__main__':
    collector = FundCollector()
    collector.run()
