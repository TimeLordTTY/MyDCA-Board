"""快照生成模块"""
import csv
from pathlib import Path
from datetime import datetime
from decimal import Decimal

def get_last_snapshot_value(snapshot_path, product_code):
    """
    获取上一条快照的value
    :param snapshot_path: 快照文件路径
    :param product_code: 产品代码
    :return: 上一条value或None
    """
    if not Path(snapshot_path).exists():
        return None
    
    last_value = None
    with open(snapshot_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row['product_code'] == product_code:
                last_value = Decimal(row['value'])
    
    return last_value

def get_existing_snapshot_keys(snapshot_path):
    """
    获取已存在的快照键集合 (snapshot_date, product_code)
    """
    if not Path(snapshot_path).exists():
        return set()
    
    keys = set()
    with open(snapshot_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            keys.add((row['snapshot_date'], row['product_code']))
    
    return keys

def create_daily_snapshot(nav_records, holdings_map):
    """
    生成日快照
    :param nav_records: {product_code: [nav_dict]}
    :param holdings_map: {product_code: shares}
    """
    from config_loader import get_project_root
    
    snapshot_path = get_project_root() / "data" / "snapshots" / "daily.csv"
    Path(snapshot_path).parent.mkdir(parents=True, exist_ok=True)
    
    # 获取已存在的快照键
    existing_keys = get_existing_snapshot_keys(snapshot_path)
    
    # 判断文件是否存在
    file_exists = snapshot_path.exists()
    
    fieldnames = ['snapshot_date', 'product_code', 'nav', 'shares', 'value', 'pnl', 'fetched_at']
    
    # 准备新快照记录
    new_snapshots = []
    fetched_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    for product_code, nav_list in nav_records.items():
        shares = Decimal(str(holdings_map.get(product_code, 0)))
        
        for nav_record in nav_list:
            snapshot_date = nav_record['ISS_DATE']
            
            # 检查是否已存在
            if (snapshot_date, product_code) in existing_keys:
                continue
            
            nav = Decimal(nav_record['NAV'])
            value = shares * nav
            
            # 计算pnl (与上一条同产品value差)
            last_value = get_last_snapshot_value(snapshot_path, product_code)
            pnl = value - last_value if last_value is not None else Decimal('0')
            
            new_snapshots.append({
                'snapshot_date': snapshot_date,
                'product_code': product_code,
                'nav': str(nav),
                'shares': str(shares),
                'value': str(value),
                'pnl': str(pnl),
                'fetched_at': fetched_at
            })
    
    if not new_snapshots:
        return 0
    
    # 写入快照
    with open(snapshot_path, 'a', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        
        if not file_exists:
            writer.writeheader()
        
        for snapshot in new_snapshots:
            writer.writerow(snapshot)
    
    return len(new_snapshots)

