# -*- coding: utf-8 -*-
"""
净值范围管理模块

用于管理和更新每个产品的净值日期范围配置。
在 export_nav_history.py 和 run_daily.py 执行后自动更新。

已从 CSV 迁移到 MySQL 数据库。
"""

import json
from pathlib import Path
from datetime import datetime
from typing import Dict, Optional

from data.config_loader import get_project_root
from data.db_connector import execute_query, execute_one


def load_nav_range() -> Dict:
    """
    加载净值范围配置
    :return: 净值范围字典
    """
    nav_range_path = get_project_root() / "config" / "nav_range.json"
    
    if not nav_range_path.exists():
        return {}
    
    with open(nav_range_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_nav_range(nav_range: Dict) -> None:
    """
    保存净值范围配置
    :param nav_range: 净值范围字典
    """
    nav_range_path = get_project_root() / "config" / "nav_range.json"
    
    with open(nav_range_path, 'w', encoding='utf-8') as f:
        json.dump(nav_range, f, ensure_ascii=False, indent=4)


def scan_nav_db(product_code: str) -> Dict:
    """
    从数据库扫描产品的净值日期范围
    :param product_code: 产品代码
    :return: {earliest_nav_date, latest_nav_date, record_count}
    """
    sql = """
        SELECT MIN(DATE_FORMAT(nav_date, '%%Y-%%m-%%d')) as earliest,
               MAX(DATE_FORMAT(nav_date, '%%Y-%%m-%%d')) as latest,
               COUNT(*) as cnt
        FROM nav
        WHERE product_code = %s
    """
    result = execute_one(sql, (product_code,))
    
    if result and result.get('cnt', 0) > 0:
        return {
            'earliest_nav_date': result.get('earliest'),
            'latest_nav_date': result.get('latest'),
            'record_count': int(result.get('cnt', 0))
        }
    
    return {
        'earliest_nav_date': None,
        'latest_nav_date': None,
        'record_count': 0
    }


def update_product_nav_range(product_code: str, product_name: str = None) -> Dict:
    """
    更新单个产品的净值范围（从数据库读取）
    :param product_code: 产品代码
    :param product_name: 产品名称（可选）
    :return: 更新后的产品净值范围信息
    """
    nav_range = load_nav_range()
    
    # 从数据库获取日期范围
    range_info = scan_nav_db(product_code)
    
    # 如果没有提供 product_name，从配置获取
    if not product_name:
        from data.config_loader import load_products
        products = load_products()
        for p in products:
            if p.get('product_code') == product_code:
                product_name = p.get('product_name', product_code)
                break
        if not product_name:
            product_name = product_code
    
    # 更新配置
    nav_range[product_code] = {
        'product_name': product_name,
        'earliest_nav_date': range_info['earliest_nav_date'],
        'latest_nav_date': range_info['latest_nav_date'],
        'record_count': range_info['record_count'],
        'updated_at': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    }
    
    save_nav_range(nav_range)
    
    return nav_range[product_code]


def update_all_nav_ranges() -> Dict:
    """
    更新所有产品的净值范围（从数据库读取）
    :return: 完整的净值范围配置
    """
    nav_range = load_nav_range()
    
    # 从数据库获取所有产品代码
    sql = "SELECT DISTINCT product_code FROM nav"
    products = execute_query(sql)
    
    # 获取产品名称映射
    from data.config_loader import load_products
    product_names = {}
    for p in load_products():
        product_names[p.get('product_code')] = p.get('product_name', '')
    
    for row in products:
        product_code = row.get('product_code')
        if not product_code:
            continue
        
        range_info = scan_nav_db(product_code)
        product_name = product_names.get(product_code, product_code)
        
        nav_range[product_code] = {
            'product_name': product_name,
            'earliest_nav_date': range_info['earliest_nav_date'],
            'latest_nav_date': range_info['latest_nav_date'],
            'record_count': range_info['record_count'],
            'updated_at': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        }
    
    save_nav_range(nav_range)
    
    return nav_range


def get_nav_range(product_code: str) -> Optional[Dict]:
    """
    获取单个产品的净值范围
    :param product_code: 产品代码
    :return: 净值范围信息或 None
    """
    nav_range = load_nav_range()
    return nav_range.get(product_code)


def print_nav_range_summary():
    """打印净值范围摘要"""
    nav_range = load_nav_range()
    
    if not nav_range:
        print("暂无净值范围配置")
        return
    
    print(f"\n{'=' * 80}")
    print("净值范围配置摘要")
    print(f"{'=' * 80}")
    print(f"{'产品代码':<15} {'最早日期':<12} {'最新日期':<12} {'记录数':<8} {'产品名称'}")
    print(f"{'-' * 80}")
    
    for code, info in sorted(nav_range.items()):
        earliest = info.get('earliest_nav_date') or '-'
        latest = info.get('latest_nav_date') or '-'
        count = info.get('record_count', 0)
        name = info.get('product_name', '')[:30]
        print(f"{code:<15} {earliest:<12} {latest:<12} {count:<8} {name}")
    
    print(f"{'=' * 80}")


if __name__ == "__main__":
    # 测试：扫描并更新所有净值范围
    print("扫描并更新所有产品的净值范围...")
    update_all_nav_ranges()
    print_nav_range_summary()
