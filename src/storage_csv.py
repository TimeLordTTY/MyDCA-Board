"""CSV存储模块"""
import csv
from pathlib import Path
from datetime import datetime

def ensure_dir(file_path):
    """确保目录存在"""
    Path(file_path).parent.mkdir(parents=True, exist_ok=True)

def get_existing_dates(csv_path):
    """读取已存在的日期集合"""
    if not Path(csv_path).exists():
        return set()
    
    dates = set()
    with open(csv_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            dates.add(row['ISS_DATE'])
    return dates

def save_nav_records(product_code, nav_list):
    """
    保存净值记录到CSV
    :param product_code: 产品代码
    :param nav_list: 净值列表 [{'ISS_DATE': '20231215', 'NAV': '1.0234', ...}]
    :return: 新增记录数
    """
    from config_loader import get_project_root
    
    csv_path = get_project_root() / "data" / "nav" / f"{product_code}.csv"
    ensure_dir(csv_path)
    
    # 获取已存在的日期
    existing_dates = get_existing_dates(csv_path)
    
    # 过滤出需要新增的记录
    new_records = [r for r in nav_list if r['ISS_DATE'] not in existing_dates]
    
    if not new_records:
        return 0
    
    # 判断是否需要写表头
    file_exists = csv_path.exists()
    
    fieldnames = ['ISS_DATE', 'NAV', 'TOT_NAV', 'INCOME', 'WEEK_CLIENTRATE', 'fetched_at']
    
    with open(csv_path, 'a', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        
        # 如果文件不存在，写入表头
        if not file_exists:
            writer.writeheader()
        
        # 追加新记录
        fetched_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        for record in new_records:
            row = {
                'ISS_DATE': record['ISS_DATE'],
                'NAV': record['NAV'],
                'TOT_NAV': record['TOT_NAV'],
                'INCOME': record['INCOME'],
                'WEEK_CLIENTRATE': record['WEEK_CLIENTRATE'],
                'fetched_at': fetched_at
            }
            writer.writerow(row)
    
    return len(new_records)

