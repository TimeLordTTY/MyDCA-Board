"""快照生成模块

设计理念：
- daily.csv 是"日快照"，记录每个采集日的资产状态
- 唯一键：(fetch_date, product_code)，每天每产品一条记录
- 同一天多次运行会覆盖（保持最新状态）
- nav_date 只记录净值日期（可能滞后），不参与去重

字段说明：
- fetch_date: 采集日期（YYYY-MM-DD），作为快照的"日期维度"
- fetched_at: 采集时间（YYYY-MM-DD HH:MM:SS.mmm），精确到毫秒
- nav_date: 净值日期（可能滞后 T+1），仅用于标识净值来源
- nav: 净值
- shares: 份额
- value: 市值
- cost: 成本
- unrealized_pnl: 浮动盈亏
- return_rate: 收益率 = unrealized_pnl / cost × 100%
- pnl: 相比上一个采集日的市值变化
"""
import csv
from pathlib import Path
from datetime import datetime
from decimal import Decimal
import logging

from holdings_calculator import calc_position_incremental, has_transactions

logger = logging.getLogger(__name__)

def get_last_snapshot_value(snapshot_path, product_code, before_fetch_date=None):
    """
    获取上一个采集日的 value（用于计算 pnl）
    :param snapshot_path: 快照文件路径
    :param product_code: 产品代码
    :param before_fetch_date: 可选，仅获取此日期之前的快照
    :return: 上一条value或None
    """
    if not Path(snapshot_path).exists():
        return None
    
    last_value = None
    last_fetch_date = None
    
    with open(snapshot_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            # 跳过中文表头行
            row_fetch_date = row.get('fetch_date', '')
            if row_fetch_date and row_fetch_date.startswith('采集'):
                continue
            
            if row['product_code'] == product_code:
                if not row_fetch_date:
                    row_fetch_date = row['fetched_at'][:10] if row.get('fetched_at') else ''
                
                # 如果指定了日期过滤，跳过该日期及之后的记录
                if before_fetch_date and row_fetch_date >= before_fetch_date:
                    continue
                
                # 记录最新的（按 fetched_date 排序）
                if last_fetch_date is None or row_fetch_date > last_fetch_date:
                    last_fetch_date = row_fetch_date
                    last_value = Decimal(row['value'])
    
    return last_value

def read_all_snapshots(snapshot_path):
    """读取所有快照记录（跳过中文表头行）"""
    if not Path(snapshot_path).exists():
        return []
    
    snapshots = []
    with open(snapshot_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            # 跳过中文表头行（第一个字段值是中文"采集日期"）
            first_value = row.get('fetch_date', row.get('nav_date', ''))
            if first_value and first_value.startswith('采集') or first_value.startswith('净值'):
                continue
            # 兼容旧格式：如果没有 fetched_date 字段，从 fetched_at 提取
            if 'fetch_date' not in row or not row['fetch_date']:
                fetched_at = row.get('fetched_at', '')
                if fetched_at and len(fetched_at) >= 10:
                    row['fetch_date'] = fetched_at[:10]
            snapshots.append(row)
    return snapshots

def rebuild_snapshots_from_date(snapshot_path, rebuild_from_date):
    """
    从指定日期重建快照（删除 fetched_date >= rebuild_from_date 的记录）
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
        fetch_date = row.get('fetch_date', row['fetched_at'][:10])
        if fetch_date >= rebuild_from_date:
            deleted_count += 1
        else:
            kept_snapshots.append(row)
    
    # 重写文件
    fieldnames = ['fetch_date', 'product_code', 'product_name', 'category', 'nav_date', 'nav', 'shares', 'value', 'pnl', 'cost', 'unrealized_pnl', 'return_rate', 'fetched_at']
    
    # 中文表头映射
    chinese_headers = {
        'fetch_date': '采集日期',
        'product_code': '产品代码',
        'product_name': '产品名称',
        'category': '分类',
        'nav_date': '净值日期',
        'nav': '净值',
        'shares': '份额',
        'value': '市值',
        'pnl': '日变动',
        'cost': '成本',
        'unrealized_pnl': '浮动盈亏',
        'return_rate': '收益率',
        'fetched_at': '采集时间'
    }
    
    with open(snapshot_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        # 写入中文表头行
        f.write(','.join([chinese_headers.get(field, field) for field in fieldnames]) + '\n')
        for row in kept_snapshots:
            writer.writerow(row)
    
    logger.info(f"重建快照: 保留 {len(kept_snapshots)} 条, 删除 {deleted_count} 条 (fetched_date >= {rebuild_from_date})")
    return len(kept_snapshots), deleted_count

def create_daily_snapshot(nav_records, holdings_map, products_map, snapshot_path=None, products_order=None, category_map=None):
    """
    生成日快照
    
    设计原则：
    - 唯一键：(fetched_date, product_code)
    - 同一采集日，同一产品只有一条记录
    - 同一天多次运行会覆盖（保持最新状态）
    - pnl = 当前value - 上一个采集日的value
    
    :param nav_records: {product_code: nav_dict}
    :param holdings_map: {product_code: shares}
    :param products_map: {product_code: product_name}
    :param snapshot_path: 可选，指定快照文件路径（主要用于测试）
    :param products_order: 可选，产品代码列表，用于保持排序顺序
    :param category_map: 可选，{product_code: category}，产品分类
    """
    from config_loader import get_project_root
    
    if snapshot_path is None:
        snapshot_path = get_project_root() / "data" / "snapshots" / "daily.csv"
    else:
        snapshot_path = Path(snapshot_path)
    
    if category_map is None:
        category_map = {}
    
    Path(snapshot_path).parent.mkdir(parents=True, exist_ok=True)
    
    # 新字段顺序：fetched_date 放在最前面，作为主要维度
    fieldnames = ['fetch_date', 'product_code', 'product_name', 'category', 'nav_date', 'nav', 'shares', 'value', 'pnl', 'cost', 'unrealized_pnl', 'return_rate', 'fetched_at']
    
    # 当前采集时间
    fetched_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:23]  # 毫秒精度
    fetched_date = fetched_at[:10]  # YYYY-MM-DD
    
    # 读取所有现有快照，按 (fetched_date, product_code) 索引
    all_snapshots = read_all_snapshots(snapshot_path)
    existing_map = {}
    for row in all_snapshots:
        row_fetch_date = row.get('fetch_date', row['fetched_at'][:10])
        key = (row_fetch_date, row['product_code'])
        existing_map[key] = row
    
    # 统计
    new_count = 0
    updated_count = 0
    skipped_count = 0
    
    # 处理每个产品
    for product_code, nav_record in nav_records.items():
        product_name = products_map.get(product_code, '')
        snapshot_date = nav_record['nav_date']  # 净值日期
        key = (fetched_date, product_code)  # 唯一键：采集日期 + 产品
        
        # 计算份额和成本
        shares, cost = calc_position_incremental(product_code, fetched_date)
        
        # 如果没有交易流水，回退到 holdings_map
        if shares == Decimal('0') and product_code in holdings_map:
            shares = Decimal(str(holdings_map.get(product_code, 0)))
        
        nav = Decimal(nav_record['nav'])
        value = shares * nav
        
        # 计算 unrealized_pnl = value - cost
        unrealized_pnl = value - cost if cost > 0 else Decimal('0')
        
        # 计算 return_rate = unrealized_pnl / cost × 100%
        if cost > 0:
            return_rate = (unrealized_pnl / cost * 100)
        else:
            return_rate = Decimal('0')
        
        # 计算 pnl（与上一个采集日的 value 差）
        last_value = get_last_snapshot_value(snapshot_path, product_code, before_fetch_date=fetched_date)
        pnl = value - last_value if last_value is not None else Decimal('0')
        
        # 检查是否已存在（同一采集日同一产品）
        if key in existing_map:
            old_row = existing_map[key]
            old_value = Decimal(old_row['value'])
            old_cost = Decimal(old_row.get('cost', '0'))
            old_snapshot_date = old_row.get('nav_date', '')
            
            # 比较是否有变化
            value_changed = abs(value - old_value) > Decimal('0.001')
            cost_changed = abs(cost - old_cost) > Decimal('0.001')
            nav_date_changed = snapshot_date != old_snapshot_date
            
            if value_changed or cost_changed or nav_date_changed:
                # 有变化，覆盖更新
                existing_map[key] = {
                    'fetch_date': fetched_date,
                    'product_code': product_code,
                    'product_name': product_name,
                    'category': category_map.get(product_code, 'fund'),
                    'nav_date': snapshot_date,
                    'nav': str(nav),
                    'shares': f"{shares:.2f}",
                    'value': f"{value:.2f}",
                    'pnl': f"{pnl:.2f}",
                    'cost': f"{cost:.2f}",
                    'unrealized_pnl': f"{unrealized_pnl:.2f}",
                    'return_rate': f"{return_rate:.2f}%",
                    'fetched_at': fetched_at
                }
                updated_count += 1
                logger.info(f"[覆盖] {product_code} @ {fetched_date}: value {old_value:.2f} → {value:.2f}")
            else:
                # 无变化，跳过
                skipped_count += 1
                logger.debug(f"[跳过] {product_code} @ {fetched_date}: 无变化")
        else:
            # 新记录
            existing_map[key] = {
                'fetch_date': fetched_date,
                'product_code': product_code,
                'product_name': product_name,
                'category': category_map.get(product_code, 'fund'),
                'nav_date': snapshot_date,
                'nav': str(nav),
                'shares': f"{shares:.2f}",
                'value': f"{value:.2f}",
                'pnl': f"{pnl:.2f}",
                'cost': f"{cost:.2f}",
                'unrealized_pnl': f"{unrealized_pnl:.2f}",
                'return_rate': f"{return_rate:.2f}%",
                'fetched_at': fetched_at
            }
            new_count += 1
            logger.debug(f"[新增] {product_code} @ {fetched_date}: value={value:.2f}")
    
    # 重写整个文件（按 fetched_date, 产品顺序 排序）
    if products_order:
        order_index = {code: idx for idx, code in enumerate(products_order)}
    else:
        order_index = {}
    
    def sort_key(x):
        # 先按 fetched_date 排序，再按 products.json 中的顺序排序
        product_idx = order_index.get(x['product_code'], 9999)
        return (x['fetch_date'], product_idx)
    
    # 中文表头映射
    chinese_headers = {
        'fetch_date': '采集日期',
        'product_code': '产品代码',
        'product_name': '产品名称',
        'category': '分类',
        'nav_date': '净值日期',
        'nav': '净值',
        'shares': '份额',
        'value': '市值',
        'pnl': '日变动',
        'cost': '成本',
        'unrealized_pnl': '浮动盈亏',
        'return_rate': '收益率',
        'fetched_at': '采集时间'
    }
    
    with open(snapshot_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        # 写入中文表头行
        f.write(','.join([chinese_headers.get(field, field) for field in fieldnames]) + '\n')
        
        sorted_snapshots = sorted(existing_map.values(), key=sort_key)
        for snapshot in sorted_snapshots:
            writer.writerow(snapshot)
    
    # 汇总日志
    if updated_count > 0:
        logger.info(f"✓ 快照更新: 新增 {new_count}, 覆盖 {updated_count}, 跳过 {skipped_count}")
    elif new_count > 0:
        logger.info(f"✓ 快照新增: {new_count} 条")
    else:
        logger.info(f"✓ 快照无变化: 跳过 {skipped_count} 条")
    
    return new_count + updated_count
