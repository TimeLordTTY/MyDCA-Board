"""快照生成模块"""
import csv
from pathlib import Path
from datetime import datetime
from decimal import Decimal
import logging

logger = logging.getLogger(__name__)

def get_last_snapshot_value(snapshot_path, product_code, before_fetch_date=None):
    """
    获取上一条快照的value
    :param snapshot_path: 快照文件路径
    :param product_code: 产品代码
    :param before_fetch_date: 可选，仅获取此日期之前的快照（用于重建）
    :return: 上一条value或None
    """
    if not Path(snapshot_path).exists():
        return None
    
    last_value = None
    with open(snapshot_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row['product_code'] == product_code:
                # 如果指定了日期过滤
                if before_fetch_date:
                    row_fetch_date = row['fetched_at'][:10]  # 取日期部分 YYYY-MM-DD
                    if row_fetch_date >= before_fetch_date:
                        continue
                last_value = Decimal(row['value'])
    
    return last_value

def get_existing_snapshot_keys(snapshot_path, key_mode='legacy'):
    """
    获取已存在的快照键集合
    :param key_mode: 'legacy' = (snapshot_date, product_code), 'fetch' = (fetch_date, product_code)
    """
    if not Path(snapshot_path).exists():
        return set()
    
    keys = set()
    with open(snapshot_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if key_mode == 'fetch':
                # 使用 fetch_date (fetched_at 的日期部分) + product_code 作为主键
                fetch_date = row['fetched_at'][:10]  # YYYY-MM-DD
                keys.add((fetch_date, row['product_code']))
            else:
                # 默认使用 (snapshot_date, product_code)
                keys.add((row['snapshot_date'], row['product_code']))
    
    return keys

def read_all_snapshots(snapshot_path):
    """读取所有快照记录"""
    if not Path(snapshot_path).exists():
        return []
    
    snapshots = []
    with open(snapshot_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            snapshots.append(row)
    return snapshots

def rebuild_snapshots_from_date(snapshot_path, rebuild_from_date):
    """
    从指定日期重建快照（删除 fetch_date >= rebuild_from_date 的记录）
    :param snapshot_path: 快照文件路径
    :param rebuild_from_date: 重建起始日期 YYYY-MM-DD
    :return: 保留的记录数, 删除的记录数
    """
    if not Path(snapshot_path).exists():
        logger.info(f"快照文件不存在，无需重建")
        return 0, 0
    
    all_snapshots = read_all_snapshots(snapshot_path)
    kept_snapshots = []
    deleted_count = 0
    
    for row in all_snapshots:
        fetch_date = row['fetched_at'][:10]  # YYYY-MM-DD
        if fetch_date >= rebuild_from_date:
            deleted_count += 1
        else:
            kept_snapshots.append(row)
    
    # 重写文件（只保留 fetch_date < rebuild_from_date 的记录）
    fieldnames = ['snapshot_date', 'product_code', 'product_name', 'nav', 'shares', 'value', 'pnl', 'fetched_at']
    
    with open(snapshot_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in kept_snapshots:
            writer.writerow(row)
    
    logger.info(f"重建快照: 保留 {len(kept_snapshots)} 条, 删除 {deleted_count} 条 (fetch_date >= {rebuild_from_date})")
    return len(kept_snapshots), deleted_count

def create_daily_snapshot(nav_records, holdings_map, products_map, force_overwrite=False, snapshot_path=None):
    """
    生成日快照
    :param nav_records: {product_code: nav_dict}
    :param holdings_map: {product_code: shares}
    :param products_map: {product_code: product_name}
    :param force_overwrite: 是否强制覆盖模式（默认False，使用 fetch_date + product_code 为主键）
    :param snapshot_path: 可选，指定快照文件路径（主要用于测试）
    """
    from config_loader import get_project_root
    
    if snapshot_path is None:
        snapshot_path = get_project_root() / "data" / "snapshots" / "daily.csv"
    else:
        snapshot_path = Path(snapshot_path)
    
    Path(snapshot_path).parent.mkdir(parents=True, exist_ok=True)
    
    # 判断文件是否存在
    file_exists = snapshot_path.exists()
    
    fieldnames = ['snapshot_date', 'product_code', 'product_name', 'nav', 'shares', 'value', 'pnl', 'fetched_at']
    
    # 当前采集时间
    fetched_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    fetch_date = fetched_at[:10]  # YYYY-MM-DD
    
    # 根据模式选择去重键
    if force_overwrite:
        # 强制覆盖模式：读取所有快照，按 (fetch_date, product_code) 覆盖
        all_snapshots = read_all_snapshots(snapshot_path)
        existing_snapshots_map = {}
        for row in all_snapshots:
            row_fetch_date = row['fetched_at'][:10]
            key = (row_fetch_date, row['product_code'])
            existing_snapshots_map[key] = row
        
        logger.info(f"[覆盖模式] 当前采集日期: {fetch_date}")
        
        # 准备要覆盖/新增的快照
        updated_count = 0
        new_count = 0
        
        for product_code, nav_record in nav_records.items():
            shares = Decimal(str(holdings_map.get(product_code, 0)))
            product_name = products_map.get(product_code, '')
            snapshot_date = nav_record['ISS_DATE']
            
            key = (fetch_date, product_code)
            
            nav = Decimal(nav_record['NAV'])
            value = shares * nav
            
            # 计算pnl (与上一条同产品value差)
            last_value = get_last_snapshot_value(snapshot_path, product_code, before_fetch_date=fetch_date)
            pnl = value - last_value if last_value is not None else Decimal('0')
            
            new_snapshot = {
                'snapshot_date': snapshot_date,
                'product_code': product_code,
                'product_name': product_name,
                'nav': str(nav),
                'shares': str(shares),
                'value': str(value),
                'pnl': str(pnl),
                'fetched_at': fetched_at
            }
            
            if key in existing_snapshots_map:
                # 覆盖已存在的记录
                logger.info(f"[覆盖] {product_code} @ {fetch_date}: 旧nav={existing_snapshots_map[key]['nav']}, 新nav={nav}")
                existing_snapshots_map[key] = new_snapshot
                updated_count += 1
                logger.debug(f"覆盖快照: {product_code} @ {fetch_date}")
            else:
                # 新增记录
                existing_snapshots_map[key] = new_snapshot
                new_count += 1
                logger.debug(f"新增快照: {product_code} @ {fetch_date}")
        
        # 重写整个文件
        with open(snapshot_path, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            
            # 按 fetch_date 排序写入
            sorted_snapshots = sorted(existing_snapshots_map.values(), 
                                    key=lambda x: (x['fetched_at'], x['product_code']))
            for snapshot in sorted_snapshots:
                writer.writerow(snapshot)
        
        logger.info(f"[覆盖模式] 更新 {updated_count} 条, 新增 {new_count} 条")
        return updated_count + new_count
    
    else:
        # 默认模式：去重跳过 (使用 snapshot_date + product_code)
        existing_keys = get_existing_snapshot_keys(snapshot_path, key_mode='legacy')
        
        # 准备新快照记录
        new_snapshots = []
        
        for product_code, nav_record in nav_records.items():
            shares = Decimal(str(holdings_map.get(product_code, 0)))
            product_name = products_map.get(product_code, '')
            
            # ISS_DATE 已由适配器标准化为 YYYY-MM-DD 格式
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
                'product_name': product_name,
                'nav': str(nav),
                'shares': str(shares),
                'value': str(value),
                'pnl': str(pnl),
                'fetched_at': fetched_at
            })
        
        if not new_snapshots:
            return 0
        
        # 追加写入快照
        with open(snapshot_path, 'a', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            
            if not file_exists:
                writer.writeheader()
            
            for snapshot in new_snapshots:
                writer.writerow(snapshot)
        
        return len(new_snapshots)
