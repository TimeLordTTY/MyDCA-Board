#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
净值读取模块

从 data/nav/{product_code}_{product_name}.csv 读取历史净值数据
"""
import csv
from pathlib import Path
from datetime import date
from decimal import Decimal
from typing import Optional, Dict, List
import logging

from config_loader import get_project_root, load_products

logger = logging.getLogger(__name__)


def get_nav_file_path(product_code: str) -> Optional[Path]:
    """获取产品的净值文件路径"""
    nav_dir = get_project_root() / "data" / "nav"
    
    if not nav_dir.exists():
        return None
    
    # 查找匹配 product_code 的文件
    for nav_file in nav_dir.glob(f"{product_code}_*.csv"):
        return nav_file
    
    return None


def load_nav_history(product_code: str) -> Dict[str, Decimal]:
    """加载产品的所有历史净值
    
    Returns:
        Dict[str, Decimal]: {nav_date: nav}
    """
    nav_file = get_nav_file_path(product_code)
    if nav_file is None or not nav_file.exists():
        return {}
    
    nav_map = {}
    
    try:
        with open(nav_file, 'r', encoding='utf-8-sig') as f:
            reader = csv.DictReader(f)
            
            for row in reader:
                # 跳过中文表头行
                nav_date = row.get('nav_date', '')
                if nav_date.startswith('净值') or not nav_date:
                    continue
                
                nav_str = row.get('nav', '')
                if not nav_str:
                    continue
                
                try:
                    nav = Decimal(str(nav_str).strip())
                    nav_map[nav_date] = nav
                except Exception:
                    continue
    except Exception as e:
        logger.warning(f"读取净值文件失败 {nav_file}: {e}")
    
    return nav_map


def get_nav(product_code: str, nav_date: str) -> Optional[Decimal]:
    """获取指定日期的净值
    
    Args:
        product_code: 产品代码
        nav_date: 净值日期 (YYYY-MM-DD)
    
    Returns:
        Optional[Decimal]: 净值，如果不存在返回 None
    """
    nav_map = load_nav_history(product_code)
    return nav_map.get(nav_date)


def get_latest_nav(product_code: str) -> Optional[tuple]:
    """获取最新净值
    
    Returns:
        Optional[tuple]: (nav_date, nav) 或 None
    """
    nav_map = load_nav_history(product_code)
    
    if not nav_map:
        return None
    
    latest_date = max(nav_map.keys())
    return (latest_date, nav_map[latest_date])


if __name__ == "__main__":
    # 简单测试
    from config_loader import load_products
    
    products = load_products()
    for p in products[:3]:
        code = p['product_code']
        name = p['product_name']
        latest = get_latest_nav(code)
        if latest:
            print(f"{code} ({name}): 最新净值 {latest[0]} = {latest[1]}")
        else:
            print(f"{code} ({name}): 无净值数据")

