# -*- coding: utf-8 -*-
"""
查询指定日期范围内的历史净值并更新到 nav 文件

用法：
  python export_nav_history.py <start_date> <end_date>              # 批量获取所有fund类型产品
  python export_nav_history.py <product_code> <start_date> <end_date>  # 获取单个产品

参数：
  product_code: 产品代码（基金代码，如 163406）- 可选
  start_date: 开始日期 (YYYY-MM-DD)
  end_date: 结束日期 (YYYY-MM-DD)

示例：
  python export_nav_history.py 2025-07-01 2025-12-17          # 批量获取所有fund产品
  python export_nav_history.py 163406 2025-01-01 2025-12-17   # 获取单个产品

功能：
  - 查询指定日期范围内的历史净值
  - 更新到 data/nav/{product_code}_{product_name}.csv 文件
  - 同日期记录：净值相同则跳过，不同则覆盖
  - 按日期排序保存
  - 自动统一 fetched_at 格式为 YYYY-MM-DD HH:MM:SS.mmm
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
from nav_range_manager import update_product_nav_range


def load_products():
    """加载产品配置"""
    products_path = get_project_root() / "config" / "products.json"
    if products_path.exists():
        with open(products_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    return []


def load_products_map():
    """加载产品代码到名称的映射"""
    products = load_products()
    return {p['product_code']: p['product_name'] for p in products}


def get_fund_products():
    """获取所有 fund 类型的产品"""
    products = load_products()
    return [p for p in products if p.get('source') == 'fund']


def load_existing_nav(nav_path):
    """
    加载现有的净值记录
    :return: {nav_date: record_dict}
    """
    if not nav_path.exists():
        return {}
    
    existing = {}
    with open(nav_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            date = row.get('nav_date', '')
            # 跳过中文表头行
            if date and not date.startswith('净值'):
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
        date = record['nav_date']
        nav = record['nav']
        
        if date in existing:
            old_nav = existing[date].get('nav', '')
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
        
        # 更新或新增记录（统一 fetched_at 格式）
        existing[date] = {
            'product_code': product_code,
            'product_name': product_name,
            'nav_date': date,
            'nav': nav,
            'total_nav': record.get('total_nav', nav),
            'income': record.get('income', '0'),
            'weekly_rate': record.get('weekly_rate', '0'),
            'fetched_at': normalize_fetched_at(record.get('fetched_at', '')),
        }
    
    # 按日期排序写入文件
    fieldnames = ['product_code', 'product_name', 'nav_date', 'nav', 'total_nav', 'income', 'weekly_rate', 'fetched_at']
    
    # 中文表头映射
    chinese_headers = {
        'product_code': '产品代码',
        'product_name': '产品名称',
        'nav_date': '净值日期',
        'nav': '单位净值',
        'total_nav': '累计净值',
        'income': '日收益',
        'weekly_rate': '周收益率',
        'fetched_at': '采集时间'
    }
    
    sorted_records = sorted(existing.values(), key=lambda x: x['nav_date'])
    
    with open(nav_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        # 写入中文表头行
        f.write(','.join([chinese_headers[field] for field in fieldnames]) + '\n')
        for row in sorted_records:
            # 确保所有字段都存在，并统一 fetched_at 格式
            write_row = {k: row.get(k, '') for k in fieldnames}
            write_row['fetched_at'] = normalize_fetched_at(write_row.get('fetched_at', ''))
            writer.writerow(write_row)
    
    print(f"\n[OK] 已更新: {nav_path}")
    print(f"    新增: {new_count}, 覆盖: {updated_count}, 跳过: {skipped_count}")
    print(f"    文件共 {len(sorted_records)} 条记录")
    
    # 更新净值范围配置
    range_info = update_product_nav_range(product_code, product_name)
    print(f"    净值范围: {range_info['earliest_nav_date']} ~ {range_info['latest_nav_date']}")
    
    return new_count, updated_count, skipped_count


def normalize_fetched_at(fetched_at_str):
    """
    统一 fetched_at 格式为 YYYY-MM-DD HH:MM:SS.mmm
    支持输入格式：
    - YYYY-MM-DD HH:MM:SS
    - YYYY-MM-DD HH:MM:SS.ffffff (微秒)
    - YYYY-MM-DDTHH:MM:SS
    - YYYY-MM-DDTHH:MM:SS.ffffff
    - ISO格式带时区
    """
    if not fetched_at_str:
        return datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:23]
    
    # 已经是目标格式
    if len(fetched_at_str) == 23 and ' ' in fetched_at_str and '.' in fetched_at_str:
        return fetched_at_str
    
    # 替换 T 为空格
    normalized = fetched_at_str.replace('T', ' ')
    
    # 移除时区信息
    if '+' in normalized:
        normalized = normalized.split('+')[0]
    if 'Z' in normalized:
        normalized = normalized.replace('Z', '')
    
    # 处理毫秒/微秒
    if '.' in normalized:
        # 截取到毫秒（3位）
        parts = normalized.split('.')
        base = parts[0]
        ms = parts[1][:3].ljust(3, '0')  # 保留3位，不足补0
        return f"{base}.{ms}"
    else:
        # 没有毫秒，添加 .000
        return f"{normalized}.000"


def update_all_fund_products(start_date, end_date):
    """批量更新所有 fund 类型产品的历史净值"""
    fund_products = get_fund_products()
    
    if not fund_products:
        print("未找到任何 fund 类型的产品")
        return
    
    print(f"=" * 60)
    print(f"批量获取历史净值")
    print(f"日期范围: {start_date} ~ {end_date}")
    print(f"产品数量: {len(fund_products)} 个 fund 类型产品")
    print(f"=" * 60)
    
    total_new = 0
    total_updated = 0
    total_skipped = 0
    success_count = 0
    
    for i, product in enumerate(fund_products, 1):
        product_code = product['product_code']
        product_name = product['product_name']
        
        print(f"\n[{i}/{len(fund_products)}] {product_name} ({product_code})")
        print("-" * 40)
        
        try:
            new_count, updated_count, skipped_count = update_nav_file(product_code, start_date, end_date)
            total_new += new_count
            total_updated += updated_count
            total_skipped += skipped_count
            success_count += 1
        except Exception as e:
            print(f"  ✗ 获取失败: {e}")
    
    # 汇总
    print(f"\n" + "=" * 60)
    print(f"批量获取完成")
    print(f"=" * 60)
    print(f"  成功: {success_count}/{len(fund_products)} 个产品")
    print(f"  新增: {total_new} 条")
    print(f"  覆盖: {total_updated} 条")
    print(f"  跳过: {total_skipped} 条")


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    
    # 判断调用方式
    if len(sys.argv) == 3:
        # 批量模式：python export_nav_history.py <start_date> <end_date>
        start_date = sys.argv[1]
        end_date = sys.argv[2]
        
        # 验证日期格式
        try:
            datetime.strptime(start_date, '%Y-%m-%d')
            datetime.strptime(end_date, '%Y-%m-%d')
        except ValueError:
            print("错误: 日期格式应为 YYYY-MM-DD")
            sys.exit(1)
        
        update_all_fund_products(start_date, end_date)
        
    elif len(sys.argv) >= 4:
        # 单产品模式：python export_nav_history.py <product_code> <start_date> <end_date>
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
