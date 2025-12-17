"""快照生成模块"""
import csv
from pathlib import Path
from datetime import datetime
from decimal import Decimal
import logging

from holdings_calculator import calc_position_incremental, has_transactions

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
    fieldnames = ['snapshot_date', 'product_code', 'product_name', 'category', 'nav', 'shares', 'value', 'pnl', 'cost', 'unrealized_pnl', 'fetched_at']
    
    with open(snapshot_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in kept_snapshots:
            writer.writerow(row)
    
    logger.info(f"重建快照: 保留 {len(kept_snapshots)} 条, 删除 {deleted_count} 条 (fetch_date >= {rebuild_from_date})")
    return len(kept_snapshots), deleted_count

def create_daily_snapshot(nav_records, holdings_map, products_map, snapshot_path=None, products_order=None, category_map=None):
    """
    生成日快照（智能去重：value变化则覆盖，否则跳过）
    
    去重策略（按 snapshot_date + product_code）：
    - 不存在 → 新增
    - 存在但 value 变化 → 覆盖更新（份额变化或配置修正）
    - 存在且 value 相同 → 跳过（重复数据）
    
    :param nav_records: {product_code: nav_dict}
    :param holdings_map: {product_code: shares}
    :param products_map: {product_code: product_name}
    :param snapshot_path: 可选，指定快照文件路径（主要用于测试）
    :param products_order: 可选，产品代码列表，用于保持排序顺序（按 products.json 顺序）
    :param category_map: 可选，{product_code: category}，产品分类（fund/bank）
    """
    from config_loader import get_project_root
    
    if snapshot_path is None:
        snapshot_path = get_project_root() / "data" / "snapshots" / "daily.csv"
    else:
        snapshot_path = Path(snapshot_path)
    
    if category_map is None:
        category_map = {}
    
    Path(snapshot_path).parent.mkdir(parents=True, exist_ok=True)
    
    fieldnames = ['snapshot_date', 'product_code', 'product_name', 'category', 'nav', 'shares', 'value', 'pnl', 'cost', 'unrealized_pnl', 'fetched_at']
    
    # 当前采集时间
    fetched_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    fetch_date = fetched_at[:10]  # YYYY-MM-DD
    
    # 读取所有现有快照，按 (snapshot_date, product_code) 索引
    all_snapshots = read_all_snapshots(snapshot_path)
    existing_map = {}
    for row in all_snapshots:
        key = (row['snapshot_date'], row['product_code'])
        existing_map[key] = row
    
    # 统计
    new_count = 0
    updated_count = 0
    skipped_count = 0
    
    # 处理每个产品
    for product_code, nav_record in nav_records.items():
        product_name = products_map.get(product_code, '')
        snapshot_date = nav_record['ISS_DATE']
        key = (snapshot_date, product_code)
        
        # 增量模式：基础份额(holdings.json) + 交易流水累计
        shares, cost = calc_position_incremental(product_code, fetch_date)
        
        # 如果没有基础持仓且没有交易流水，回退到 holdings_map
        if shares == Decimal('0') and product_code in holdings_map:
            shares = Decimal(str(holdings_map.get(product_code, 0)))
        
        nav = Decimal(nav_record['NAV'])
        value = shares * nav
        
        # 计算 unrealized_pnl = value - cost
        unrealized_pnl = value - cost if cost > 0 else Decimal('0')
        
        # 检查是否已存在
        if key in existing_map:
            old_row = existing_map[key]
            old_value = Decimal(old_row['value'])
            
            # 比较 value 是否变化（允许小数点误差）
            value_changed = abs(value - old_value) > Decimal('0.001')
            
            if value_changed:
                # value 变化了（份额或配置变更），覆盖更新
                # 重新计算 pnl（与上一条不同 snapshot_date 的同产品 value 差）
                last_value = get_last_snapshot_value(snapshot_path, product_code)
                # 找到上一个不同 snapshot_date 的 value
                pnl = value - last_value if last_value is not None and last_value != old_value else Decimal(old_row.get('pnl', '0'))
                
                existing_map[key] = {
                    'snapshot_date': snapshot_date,
                    'product_code': product_code,
                    'product_name': product_name,
                    'category': category_map.get(product_code, 'fund'),
                    'nav': str(nav),  # 净值保持原始精度
                    'shares': f"{shares:.2f}",  # 份额保留两位小数
                    'value': f"{value:.2f}",  # 金额保留两位小数
                    'pnl': f"{pnl:.2f}",
                    'cost': f"{cost:.2f}",
                    'unrealized_pnl': f"{unrealized_pnl:.2f}",
                    'fetched_at': fetched_at
                }
                updated_count += 1
                logger.info(f"[覆盖] {product_code} @ {snapshot_date}: value {old_value:.2f} → {value:.2f} (份额/配置变更)")
            else:
                # value 没变，跳过
                skipped_count += 1
                logger.debug(f"[跳过] {product_code} @ {snapshot_date}: value={value:.2f} (重复)")
        else:
            # 新记录
            # 计算 pnl (与上一条同产品 value 差)
            last_value = get_last_snapshot_value(snapshot_path, product_code)
            pnl = value - last_value if last_value is not None else Decimal('0')
            
            existing_map[key] = {
                'snapshot_date': snapshot_date,
                'product_code': product_code,
                'product_name': product_name,
                'category': category_map.get(product_code, 'fund'),
                'nav': str(nav),  # 净值保持原始精度
                'shares': f"{shares:.2f}",  # 份额保留两位小数
                'value': f"{value:.2f}",  # 金额保留两位小数
                'pnl': f"{pnl:.2f}",
                'cost': f"{cost:.2f}",
                'unrealized_pnl': f"{unrealized_pnl:.2f}",
                'fetched_at': fetched_at
            }
            new_count += 1
            logger.debug(f"[新增] {product_code} @ {snapshot_date}: value={value:.2f}")
    
    # 重写整个文件（按 snapshot_date, 产品顺序 排序）
    # 构建产品顺序索引（用于排序）
    if products_order:
        order_index = {code: idx for idx, code in enumerate(products_order)}
    else:
        order_index = {}
    
    def sort_key(x):
        # 先按 snapshot_date 排序，再按 products.json 中的顺序排序
        product_idx = order_index.get(x['product_code'], 9999)  # 未知产品排最后
        return (x['snapshot_date'], product_idx)
    
    with open(snapshot_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        
        sorted_snapshots = sorted(existing_map.values(), key=sort_key)
        for snapshot in sorted_snapshots:
            writer.writerow(snapshot)
    
    # 汇总日志
    if updated_count > 0:
        logger.info(f"✓ 快照更新: 新增 {new_count}, 覆盖 {updated_count}, 跳过 {skipped_count}")
    elif new_count > 0:
        logger.info(f"✓ 快照新增: {new_count} 条")
    else:
        logger.info(f"✓ 快照无变化: 跳过 {skipped_count} 条（数据相同）")
    
    return new_count + updated_count
