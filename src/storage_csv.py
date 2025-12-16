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

def save_nav_record(product_code, product_name, nav_record):
    """
    保存净值记录到CSV
    :param product_code: 产品代码
    :param product_name: 产品名称
    :param nav_record: 单条净值记录 {'ISS_DATE': '2023-12-15', 'NAV': '1.0234', ...}
    :return: 是否新增 (1=新增, 0=已存在)
    """
    from config_loader import get_project_root
    
    # 文件名格式：产品代码_产品名称.csv（清理特殊字符）
    safe_name = product_name.replace('/', '_').replace('\\', '_').replace(':', '_')
    csv_path = get_project_root() / "data" / "nav" / f"{product_code}_{safe_name}.csv"
    ensure_dir(csv_path)
    
    # 获取已存在的日期
    existing_dates = get_existing_dates(csv_path)
    
    # 检查是否已存在
    if nav_record['ISS_DATE'] in existing_dates:
        return 0
    
    # 判断是否需要写表头
    file_exists = csv_path.exists()
    
    fieldnames = ['product_code', 'product_name', 'ISS_DATE', 'NAV', 'TOT_NAV', 'INCOME', 'WEEK_CLIENTRATE', 'fetched_at']
    
    with open(csv_path, 'a', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        
        # 如果文件不存在，写入表头
        if not file_exists:
            writer.writeheader()
        
        # 追加新记录
        fetched_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        row = {
            'product_code': product_code,
            'product_name': product_name,
            'ISS_DATE': nav_record['ISS_DATE'],
            'NAV': nav_record['NAV'],
            'TOT_NAV': nav_record['TOT_NAV'],
            'INCOME': nav_record['INCOME'],
            'WEEK_CLIENTRATE': nav_record['WEEK_CLIENTRATE'],
            'fetched_at': fetched_at
        }
        writer.writerow(row)
    
    return 1

