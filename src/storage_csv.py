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
    with open(csv_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            # 跳过中文表头行
            nav_date = row.get('nav_date', '')
            if nav_date and not nav_date.startswith('净值'):
                dates.add(nav_date)
    return dates

def save_nav_record(product_code, product_name, nav_record):
    """
    保存净值记录到CSV
    :param product_code: 产品代码
    :param product_name: 产品名称
    :param nav_record: 单条净值记录 {'nav_date': '2023-12-15', 'nav': '1.0234', ...}
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
    if nav_record['nav_date'] in existing_dates:
        return 0
    
    # 判断是否需要写表头
    file_exists = csv_path.exists()
    
    # 统一字段名（全小写）
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
    
    with open(csv_path, 'a', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        
        # 如果文件不存在，写入英文表头和中文表头
        if not file_exists:
            writer.writeheader()
            # 写入中文表头行
            f.write(','.join([chinese_headers[field] for field in fieldnames]) + '\n')
        
        # 追加新记录
        fetched_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:23]  # 毫秒精度
        row = {
            'product_code': product_code,
            'product_name': product_name,
            'nav_date': nav_record['nav_date'],
            'nav': nav_record['nav'],
            'total_nav': nav_record['total_nav'],
            'income': nav_record['income'],
            'weekly_rate': nav_record['weekly_rate'],
            'fetched_at': fetched_at
        }
        writer.writerow(row)
    
    return 1

