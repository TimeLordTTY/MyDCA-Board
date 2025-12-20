#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
快照服务层

提供快照生成与读取功能：
- 生成 daily.csv 和 daily_balance.csv
- 采集净值并生成快照
- 读取最新快照数据
- 获取资产汇总信息

UI 和 CLI 都通过此服务操作快照数据，避免业务逻辑重复。
"""
import csv
import logging
from datetime import date
from decimal import Decimal
from pathlib import Path
from typing import List, Dict, Optional
from dataclasses import dataclass, field
from io import StringIO

from data.config_loader import get_project_root, load_products

logger = logging.getLogger(__name__)


@dataclass
class PortfolioSummary:
    """资产汇总"""
    fetch_date: str
    global_value: Decimal = Decimal('0')
    global_pnl: Decimal = Decimal('0')
    global_return: Decimal = Decimal('0')
    fund_total: Decimal = Decimal('0')
    wenlibao_total: Decimal = Decimal('0')
    ylb_total: Decimal = Decimal('0')
    
    def to_dict(self) -> Dict:
        return {
            'fetch_date': self.fetch_date,
            'global_value': str(self.global_value),
            'global_pnl': str(self.global_pnl),
            'global_return': f"{self.global_return:.2%}" if self.global_return else '0%',
            'fund_total': str(self.fund_total),
            'wenlibao_total': str(self.wenlibao_total),
            'ylb_total': str(self.ylb_total)
        }


@dataclass
class SyncResult:
    """同步结果"""
    nav_collected: int = 0       # 采集到的净值数量
    nav_saved: int = 0           # 保存的净值数量
    nav_skipped: int = 0         # 跳过的净值数量（已存在）
    daily_records: int = 0       # daily.csv 记录数
    balance_records: int = 0     # daily_balance.csv 记录数
    errors: List[str] = field(default_factory=list)


def build_all_snapshots(fetch_date: str = None, project_root: Path = None) -> int:
    """
    生成 daily.csv 和 daily_balance.csv
    
    Args:
        fetch_date: 快照日期（默认今天）
        project_root: 项目根目录
    
    Returns:
        生成的 daily_balance 记录数
    """
    if project_root is None:
        project_root = get_project_root()
    
    if fetch_date is None:
        fetch_date = date.today().strftime('%Y-%m-%d')
    
    # 生成 daily.csv（通过 snapshot 模块）
    from core.snapshot import create_daily_snapshot
    
    products = load_products()
    for product in products:
        try:
            create_daily_snapshot(
                project_root=project_root,
                product_code=product['product_code'],
                product_name=product['product_name'],
                fetch_date=fetch_date
            )
        except Exception as e:
            logger.warning(f"生成 {product['product_code']} 快照失败: {e}")
    
    # 生成 daily_balance.csv
    from core.daily_balance import create_daily_balance_snapshot
    count = create_daily_balance_snapshot(project_root, fetch_date)
    
    return count


def collect_nav_and_build_snapshots(fetch_date: str = None, silent: bool = False) -> SyncResult:
    """
    采集净值并生成快照（一键日更）
    
    Args:
        fetch_date: 快照日期（默认今天）
        silent: 是否静默模式
    
    Returns:
        SyncResult 包含采集和生成结果
    """
    result = SyncResult()
    
    if fetch_date is None:
        fetch_date = date.today().strftime('%Y-%m-%d')
    
    project_root = get_project_root()
    
    # 静默模式下抑制输出
    if silent:
        import sys
        from io import StringIO
        old_stdout = sys.stdout
        old_stderr = sys.stderr
        sys.stdout = StringIO()
        sys.stderr = StringIO()
        old_level = logger.level
        logger.setLevel(logging.CRITICAL)
    
    try:
        # 采集净值
        from core.nav_collector import collect_and_store
        collect_and_store()
        
        # 生成快照
        result.balance_records = build_all_snapshots(fetch_date, project_root)
        
    except Exception as e:
        result.errors.append(str(e))
    finally:
        if silent:
            sys.stdout = old_stdout
            sys.stderr = old_stderr
            logger.setLevel(old_level)
    
    return result


def read_latest_daily(project_root: Path = None) -> List[Dict]:
    """
    读取最新 daily.csv 数据
    
    Args:
        project_root: 项目根目录
    
    Returns:
        最新日期的 daily 记录列表
    """
    if project_root is None:
        project_root = get_project_root()
    
    daily_path = project_root / "data" / "snapshots" / "daily.csv"
    
    if not daily_path.exists():
        return []
    
    records = []
    latest_date = None
    
    with open(daily_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        all_records = []
        for row in reader:
            # 跳过中文表头行
            if row.get('fetch_date', '').startswith('采集'):
                continue
            all_records.append(row)
            
            # 找最新日期
            row_date = row.get('fetch_date', '')
            if latest_date is None or row_date > latest_date:
                latest_date = row_date
    
    # 只返回最新日期的记录
    if latest_date:
        records = [r for r in all_records if r.get('fetch_date') == latest_date]
    
    return records


def read_latest_daily_balance(project_root: Path = None) -> List[Dict]:
    """
    读取最新 daily_balance.csv 数据
    
    Args:
        project_root: 项目根目录
    
    Returns:
        最新日期的 daily_balance 记录列表
    """
    if project_root is None:
        project_root = get_project_root()
    
    balance_path = project_root / "data" / "snapshots" / "daily_balance.csv"
    
    if not balance_path.exists():
        return []
    
    records = []
    latest_date = None
    
    with open(balance_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        all_records = []
        for row in reader:
            # 跳过中文表头行
            if row.get('fetch_date', '').startswith('采集'):
                continue
            all_records.append(row)
            
            # 找最新日期
            row_date = row.get('fetch_date', '')
            if latest_date is None or row_date > latest_date:
                latest_date = row_date
    
    # 只返回最新日期的记录
    if latest_date:
        records = [r for r in all_records if r.get('fetch_date') == latest_date]
    
    return records


def get_portfolio_summary(project_root: Path = None) -> PortfolioSummary:
    """
    获取资产汇总信息
    
    Args:
        project_root: 项目根目录
    
    Returns:
        PortfolioSummary 资产汇总
    """
    if project_root is None:
        project_root = get_project_root()
    
    fetch_date = date.today().strftime('%Y-%m-%d')
    summary = PortfolioSummary(fetch_date=fetch_date)
    
    # 从 daily.csv 计算基金总值（使用 value 而不是 total_value，与 daily_balance.csv 一致）
    daily_records = read_latest_daily(project_root)
    
    for record in daily_records:
        product_code = record.get('product_code', '')
        category = record.get('category', '')
        
        try:
            value = Decimal(record.get('value', '0') or '0')
            total_value = Decimal(record.get('total_value', '0') or '0')
        except:
            continue
        
        # 稳利宝
        if product_code == 'FBAE41126E':
            summary.wenlibao_total = total_value
        # 货币基金不计入基金总额（已映射到小荷包）
        elif product_code == '000686':
            pass
        # 其他基金：使用 value（纯市值）而不是 total_value
        elif category == 'fund':
            summary.fund_total += value
    
    # 从 daily_balance.csv 计算账户总值
    balance_records = read_latest_daily_balance(project_root)
    
    for record in balance_records:
        account_id = record.get('account_id', '')
        account_type = record.get('account_type', '')
        
        try:
            balance = Decimal(record.get('balance', '0') or '0')
            product_value = Decimal(record.get('product_value', '0') or '0')
        except:
            continue
        
        # 余利宝
        if account_id in ['ylb_life', 'ylb_finance']:
            summary.ylb_total += balance
        
        # 更新最新日期
        row_date = record.get('fetch_date', '')
        if row_date:
            summary.fetch_date = row_date
    
    # 计算全局汇总
    summary.global_value = summary.fund_total + summary.wenlibao_total + summary.ylb_total
    
    # 计算全局盈亏（从 daily.csv 汇总）
    for record in daily_records:
        try:
            total_pnl = Decimal(record.get('total_pnl', '0') or '0')
            summary.global_pnl += total_pnl
        except:
            pass
    
    # 计算收益率
    if summary.global_value > 0:
        # 简化计算：使用汇总的 principal_total
        total_principal = Decimal('0')
        for record in daily_records:
            try:
                principal = Decimal(record.get('principal_total', '0') or '0')
                total_principal += principal
            except:
                pass
        
        if total_principal > 0:
            summary.global_return = summary.global_pnl / total_principal
    
    return summary


def read_daily_by_category(category: str = None, project_root: Path = None) -> List[Dict]:
    """
    按分类筛选 daily.csv 数据
    
    Args:
        category: 分类筛选（fund/bank）
        project_root: 项目根目录
    
    Returns:
        筛选后的记录列表
    """
    records = read_latest_daily(project_root)
    
    if category:
        records = [r for r in records if r.get('category') == category]
    
    return records


def read_balance_by_type(account_type: str = None, project_root: Path = None) -> List[Dict]:
    """
    按账户类型筛选 daily_balance.csv 数据
    
    Args:
        account_type: 账户类型筛选（cash/fund_mapped/product_sub/summary）
        project_root: 项目根目录
    
    Returns:
        筛选后的记录列表
    """
    records = read_latest_daily_balance(project_root)
    
    if account_type:
        records = [r for r in records if r.get('account_type') == account_type]
    
    return records


def read_balance_by_group(group_keyword: str = None, project_root: Path = None) -> List[Dict]:
    """
    按组关键词筛选 daily_balance.csv 数据
    
    Args:
        group_keyword: 组关键词（ylb/wenlibao/fund）
        project_root: 项目根目录
    
    Returns:
        筛选后的记录列表
    """
    records = read_latest_daily_balance(project_root)
    
    if group_keyword:
        records = [r for r in records 
                   if group_keyword in r.get('account_id', '').lower()]
    
    return records

