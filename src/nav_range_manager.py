# -*- coding: utf-8 -*-
"""
净值范围管理模块

用于管理和更新每个产品的净值日期范围配置。
在 export_nav_history.py 和 run_daily.py 执行后自动更新。
"""

import json
import csv
from pathlib import Path
from datetime import datetime
from typing import Dict, Optional

from config_loader import get_project_root


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


def scan_nav_file(nav_path: Path) -> Dict:
    """
    扫描单个净值文件，获取日期范围和记录数
    :param nav_path: 净值文件路径
    :return: {earliest_nav_date, latest_nav_date, record_count}
    """
    if not nav_path.exists():
        return {
            'earliest_nav_date': None,
            'latest_nav_date': None,
            'record_count': 0
        }
    
    dates = []
    with open(nav_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            nav_date = row.get('nav_date', '')
            # 跳过中文表头行和空行
            if nav_date and not nav_date.startswith('净值') and nav_date.strip():
                dates.append(nav_date)
    
    if not dates:
        return {
            'earliest_nav_date': None,
            'latest_nav_date': None,
            'record_count': 0
        }
    
    dates.sort()
    return {
        'earliest_nav_date': dates[0],
        'latest_nav_date': dates[-1],
        'record_count': len(dates)
    }


def update_product_nav_range(product_code: str, product_name: str = None) -> Dict:
    """
    更新单个产品的净值范围
    :param product_code: 产品代码
    :param product_name: 产品名称（可选）
    :return: 更新后的产品净值范围信息
    """
    nav_range = load_nav_range()
    
    # 查找对应的净值文件
    nav_dir = get_project_root() / "data" / "nav"
    nav_files = list(nav_dir.glob(f"{product_code}_*.csv"))
    
    if nav_files:
        nav_path = nav_files[0]
        range_info = scan_nav_file(nav_path)
        
        # 如果没有提供 product_name，尝试从文件名提取
        if not product_name:
            # 文件名格式: {product_code}_{product_name}.csv
            filename = nav_path.stem
            if '_' in filename:
                product_name = filename.split('_', 1)[1]
            else:
                product_name = product_code
    else:
        range_info = {
            'earliest_nav_date': None,
            'latest_nav_date': None,
            'record_count': 0
        }
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
    更新所有产品的净值范围（基于实际的净值文件）
    :return: 完整的净值范围配置
    """
    nav_range = load_nav_range()
    nav_dir = get_project_root() / "data" / "nav"
    
    if not nav_dir.exists():
        return nav_range
    
    # 扫描所有净值文件
    for nav_file in nav_dir.glob("*.csv"):
        filename = nav_file.stem
        if '_' in filename:
            product_code = filename.split('_')[0]
            product_name = filename.split('_', 1)[1]
        else:
            product_code = filename
            product_name = filename
        
        range_info = scan_nav_file(nav_file)
        
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

