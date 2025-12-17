#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
查询指定日期范围内的历史净值并更新到 nav 文件

用法：
  python export_nav_history.py <product_code> <start_date> <end_date>

参数：
  product_code: 产品代码（基金代码，如 163406）
  start_date: 开始日期 (YYYY-MM-DD)
  end_date: 结束日期 (YYYY-MM-DD)

示例：
  python export_nav_history.py 163406 2025-01-01 2025-12-17
  python export_nav_history.py 000307 2025-11-01 2025-12-17

功能：
  - 查询指定日期范围内的历史净值
  - 更新到 data/nav/{product_code}_{product_name}.csv 文件
  - 同日期记录：净值相同则跳过，不同则覆盖
  - 按日期排序保存
"""

import sys
import csv
import json
from pathlib import Path
from datetime import datetime
from decimal import Decimal

# 添加项目路径
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))

from adaptor.fund_client import query_nav_history
from config_loader import get_project_root


def load_products_map():
    """加载产品代码到名称的映射"""
    products_path = get_project_root() / "config" / "products.json"
    products_map = {}
    if products_path.exists():
        with open(products_path, 'r', encoding='utf-8') as f:
            products = json.load(f)
            for p in products:
                products_map[p['id']] = p['name']
    return products_map


def load_existing_nav(nav_path):
    """
    加载现有的净值记录
    :return: {ISS_DATE: record_dict}
    """
    if not nav_path.exists():
        return {}
    
    existing = {}
    with open(nav_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            date = row.get('ISS_DATE', '')
            if date:
                existing[date] = row
    return existing


def update_nav_file(product_code, start_date, end_date):
    """
    查询历史净值并更新到 nav 文件
    :param product_code: 产品代码
    :param start_date: 开始日期 (YYYY-MM-DD)
    :param end_date: 结束日期 (YYYY-MM-DD)
    :return: (new_count, updated_count, skipped_count)
    """
    # 获取产品名称
    products_map = load_products_map()
    product_name = products_map.get(product_code, product_code)
    
    print(f"正在查询 {product_name} ({product_code}) 的历史净值...")
    print(f"日期范围: {start_date} ~ {end_date}")
    
    # 查询历史净值
    records = query_nav_history(product_code, start_date, end_date)
    
    if not records:
        print("未查询到任何净值数据")
        return 0, 0, 0
    
    print(f"共查询到 {len(records)} 条记录")
    
    # 确定 nav 文件路径
    nav_dir = get_project_root() / "data" / "nav"
    nav_dir.mkdir(parents=True, exist_ok=True)
    nav_path = nav_dir / f"{product_code}_{product_name}.csv"
    
    # 加载现有记录
    existing = load_existing_nav(nav_path)
    
    # 统计
    new_count = 0
    updated_count = 0
    skipped_count = 0
    
    # 合并记录
    for record in records:
        date = record['ISS_DATE']
        nav = record['NAV']
        
        if date in existing:
            old_nav = existing[date].get('NAV', '')
            # 比较净值是否相同（使用 Decimal 比较避免浮点误差）
            try:
                old_nav_dec = Decimal(str(old_nav).strip())
                new_nav_dec = Decimal(str(nav).strip())
                if abs(old_nav_dec - new_nav_dec) < Decimal('0.0001'):
                    skipped_count += 1
                    continue
                else:
                    # 净值不同，覆盖
                    print(f"  [更新] {date}: {old_nav} -> {nav}")
                    updated_count += 1
            except:
                # 无法比较，直接覆盖
                updated_count += 1
        else:
            new_count += 1
        
        # 更新或新增记录
        existing[date] = {
            'product_code': product_code,
            'product_name': product_name,
            'ISS_DATE': date,
            'NAV': nav,
            'TOT_NAV': record.get('TOT_NAV', nav),
            'INCOME': record.get('INCOME', '0'),
            'WEEK_CLIENTRATE': record.get('WEEK_CLIENTRATE', '0'),
            'fetched_at': record.get('fetched_at', datetime.now().strftime('%Y-%m-%d %H:%M:%S')),
        }
    
    # 按日期排序写入文件
    fieldnames = ['product_code', 'product_name', 'ISS_DATE', 'NAV', 'TOT_NAV', 'INCOME', 'WEEK_CLIENTRATE', 'fetched_at']
    
    sorted_records = sorted(existing.values(), key=lambda x: x['ISS_DATE'])
    
    with open(nav_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in sorted_records:
            # 确保所有字段都存在
            write_row = {k: row.get(k, '') for k in fieldnames}
            writer.writerow(write_row)
    
    print(f"\n[OK] 已更新: {nav_path}")
    print(f"    新增: {new_count}, 覆盖: {updated_count}, 跳过: {skipped_count}")
    print(f"    文件共 {len(sorted_records)} 条记录")
    
    return new_count, updated_count, skipped_count


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    
    product_code = sys.argv[1]
    start_date = sys.argv[2]
    end_date = sys.argv[3]
    
    # 验证日期格式
    try:
        datetime.strptime(start_date, '%Y-%m-%d')
        datetime.strptime(end_date, '%Y-%m-%d')
    except ValueError:
        print("错误: 日期格式应为 YYYY-MM-DD")
        sys.exit(1)
    
    # 执行更新
    new_count, updated_count, skipped_count = update_nav_file(product_code, start_date, end_date)
    
    total = new_count + updated_count + skipped_count
    if total > 0:
        print(f"\n完成!")
    else:
        sys.exit(1)


if __name__ == "__main__":
    main()
