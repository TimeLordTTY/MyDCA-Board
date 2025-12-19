"""快照生成模块

设计理念：
- daily.csv 是"日快照"，记录每个采集日的资产状态
- 唯一键：(fetch_date, product_code)，每天每产品一条记录
- 同一天多次运行会覆盖（保持最新状态）

字段说明（按顺序）：
- fetch_date: 采集日期（YYYY-MM-DD），作为快照的"日期维度"
- product_code: 产品代码
- product_name: 产品名称
- category: 分类（fund / bank）
- nav_date: 净值日期（可能滞后 T+1），仅用于标识净值来源
- nav: 单位净值
- shares: 已确认份额（仅确认后才计入）
- value: 持仓市值（shares * nav）
- pnl_day: 日变动（只由净值涨跌贡献，按上一日 shares 计算）
- cost: 持仓成本（平均成本法，卖出按比例扣减）
- unrealized_pnl: 浮动盈亏（value - cost）
- return_rate: 持仓收益率（unrealized_pnl / cost）
- cash: 在途资金（扣款已发生但份额未确认的净申购金额）
- total_value: 产品总资产（value + cash）
- principal_total: 累计投入本金（按扣款净额累计，含在途；不因卖出回笼减少）
- total_pnl: 总盈亏（total_value - principal_total）
- real_return: 真实收益率（total_pnl / principal_total）
- fetched_at: 采集时间（毫秒精度）
"""
import csv
import os
import uuid
from pathlib import Path
from datetime import datetime
from decimal import Decimal
import logging

from core.holdings_calculator import HoldingsCalculator, calc_position_incremental, has_transactions

logger = logging.getLogger(__name__)

# 字段顺序（固定）
FIELDNAMES = [
    'fetch_date', 'product_code', 'product_name', 'category',
    'nav_date', 'nav', 'shares', 'value', 'pnl_day',
    'cost', 'unrealized_pnl', 'return_rate',
    'cash', 'total_value', 'principal_total', 'total_pnl', 'real_return',
    'fetched_at'
]

# 中文表头（与字段一一对应）
CHINESE_HEADERS = {
    'fetch_date': '采集日期',
    'product_code': '产品代码',
    'product_name': '产品名称',
    'category': '分类',
    'nav_date': '净值日期',
    'nav': '净值',
    'shares': '份额',
    'value': '市值',
    'pnl_day': '日变动',
    'cost': '成本',
    'unrealized_pnl': '浮动盈亏',
    'return_rate': '收益率',
    'cash': '在途资金',
    'total_value': '总资产',
    'principal_total': '累计投入本金',
    'total_pnl': '总盈亏',
    'real_return': '真实收益率',
    'fetched_at': '采集时间'
}


def get_last_snapshot_value(snapshot_path, product_code, before_fetch_date=None):
    """
    获取上一个采集日的 value（兼容旧 API，用于 nav_collector.py）
    
    :param snapshot_path: 快照文件路径
    :param product_code: 产品代码
    :param before_fetch_date: 可选，仅获取此日期之前的快照
    :return: 上一条value或None
    """
    prev = get_prev_snapshot(snapshot_path, product_code, before_fetch_date)
    if prev is not None:
        return prev['value']
    return None


def get_prev_snapshot(snapshot_path, product_code, before_fetch_date):
    """
    获取上一个采集日的快照（用于计算 pnl_day）
    
    :param snapshot_path: 快照文件路径
    :param product_code: 产品代码
    :param before_fetch_date: 仅获取此日期之前的快照
    :return: {shares, nav, value} 或 None
    """
    if not Path(snapshot_path).exists():
        return None
    
    prev_snapshot = None
    prev_fetch_date = None
    
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
                
                # 仅取 before_fetch_date 之前的记录
                if row_fetch_date >= before_fetch_date:
                    continue
                
                # 取最新的（按 fetch_date 排序）
                if prev_fetch_date is None or row_fetch_date > prev_fetch_date:
                    prev_fetch_date = row_fetch_date
                    try:
                        prev_snapshot = {
                            'shares': Decimal(row.get('shares', '0').replace(',', '')),
                            'nav': Decimal(row.get('nav', '0').replace(',', '')),
                            'value': Decimal(row.get('value', '0').replace(',', ''))
                        }
                    except:
                        prev_snapshot = None
    
    return prev_snapshot


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
            if first_value and (first_value.startswith('采集') or first_value.startswith('净值')):
                continue
            # 兼容旧格式：如果没有 fetch_date 字段，从 fetched_at 提取
            if 'fetch_date' not in row or not row['fetch_date']:
                fetched_at = row.get('fetched_at', '')
                if fetched_at and len(fetched_at) >= 10:
                    row['fetch_date'] = fetched_at[:10]
            snapshots.append(row)
    return snapshots


def rebuild_snapshots_from_date(snapshot_path, rebuild_from_date):
    """
    从指定日期重建快照（删除 fetch_date >= rebuild_from_date 的记录）
    使用原子写入：先写临时文件，再 os.replace 替换
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
    
    # 原子写入：先写临时文件
    snapshot_path = Path(snapshot_path)
    tmp_path = snapshot_path.parent / f"daily.csv.tmp.{uuid.uuid4().hex[:8]}"
    
    try:
        with open(tmp_path, 'w', newline='', encoding='utf-8-sig') as f:
            writer = csv.DictWriter(f, fieldnames=FIELDNAMES, extrasaction='ignore')
            writer.writeheader()
            # 写入中文表头行
            f.write(','.join([CHINESE_HEADERS.get(field, field) for field in FIELDNAMES]) + '\n')
            for row in kept_snapshots:
                writer.writerow(row)
            f.flush()
        
        # 原子替换
        os.replace(str(tmp_path), str(snapshot_path))
        logger.info(f"重建快照: 保留 {len(kept_snapshots)} 条, 删除 {deleted_count} 条")
    except Exception as e:
        # 清理临时文件
        if tmp_path.exists():
            tmp_path.unlink()
        raise e
    
    return len(kept_snapshots), deleted_count


def create_daily_snapshot(nav_records, holdings_map, products_map, snapshot_path=None, 
                          products_order=None, category_map=None):
    """
    生成日快照
    
    设计原则：
    - 唯一键：(fetch_date, product_code)
    - 同一采集日，同一产品只有一条记录
    - 同一天多次运行会覆盖（保持最新状态）
    - pnl_day = prev_shares * (nav_today - nav_prev)（只由净值变化贡献）
    
    :param nav_records: {product_code: nav_dict}
    :param holdings_map: {product_code: shares} (备用，优先用 HoldingsCalculator)
    :param products_map: {product_code: product_name}
    :param snapshot_path: 可选，指定快照文件路径（主要用于测试）
    :param products_order: 可选，产品代码列表，用于保持排序顺序
    :param category_map: 可选，{product_code: category}，产品分类
    """
    from data.config_loader import get_project_root
    
    if snapshot_path is None:
        snapshot_path = get_project_root() / "data" / "snapshots" / "daily.csv"
    else:
        snapshot_path = Path(snapshot_path)
    
    if category_map is None:
        category_map = {}
    
    Path(snapshot_path).parent.mkdir(parents=True, exist_ok=True)
    
    # 当前采集时间
    fetched_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:23]  # 毫秒精度
    fetch_date = fetched_at[:10]  # YYYY-MM-DD
    
    # 创建持仓计算器
    calc = HoldingsCalculator()
    all_holdings = calc.get_holdings_as_of(fetch_date)
    all_cash_in_transit = calc.get_cash_in_transit_as_of(fetch_date)
    all_principal = calc.get_principal_total_as_of(fetch_date)
    
    # 读取所有现有快照，按 (fetch_date, product_code) 索引
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
        nav_date = nav_record['nav_date']  # 净值日期
        key = (fetch_date, product_code)  # 唯一键：采集日期 + 产品
        
        # 获取份额和成本
        if product_code in all_holdings:
            shares = all_holdings[product_code]["shares"]
            cost = all_holdings[product_code]["cost"]
        else:
            # 回退到 holdings_map
            shares = Decimal(str(holdings_map.get(product_code, 0)))
            cost = Decimal('0')
        
        nav = Decimal(str(nav_record['nav']))
        value = shares * nav
        
        # 获取在途资金
        cash = all_cash_in_transit.get(product_code, Decimal('0'))
        
        # 获取累计投入本金
        principal_total = all_principal.get(product_code, Decimal('0'))
        
        # 计算总资产
        total_value = value + cash
        
        # 计算总盈亏
        total_pnl = total_value - principal_total if principal_total > 0 else Decimal('0')
        
        # 计算真实收益率
        if principal_total > 0:
            real_return = (total_pnl / principal_total * 100)
        else:
            real_return = Decimal('0')
        
        # 计算 unrealized_pnl = value - cost
        unrealized_pnl = value - cost if cost > 0 else Decimal('0')
        
        # 计算 return_rate = unrealized_pnl / cost × 100%
        if cost > 0:
            return_rate = (unrealized_pnl / cost * 100)
        else:
            return_rate = Decimal('0')
        
        # 计算 pnl_day（核心：只由净值变化贡献）
        # pnl_day = prev_shares * (nav_today - nav_prev)
        prev_snapshot = get_prev_snapshot(snapshot_path, product_code, fetch_date)
        if prev_snapshot is not None:
            prev_shares = prev_snapshot['shares']
            prev_nav = prev_snapshot['nav']
            pnl_day = prev_shares * (nav - prev_nav)
        else:
            pnl_day = Decimal('0')
        
        # 构建新记录
        new_row = {
            'fetch_date': fetch_date,
            'product_code': product_code,
            'product_name': product_name,
            'category': category_map.get(product_code, 'fund'),
            'nav_date': nav_date,
            'nav': str(nav),
            'shares': f"{shares:.2f}",
            'value': f"{value:.2f}",
            'pnl_day': f"{pnl_day:.2f}",
            'cost': f"{cost:.2f}",
            'unrealized_pnl': f"{unrealized_pnl:.2f}",
            'return_rate': f"{return_rate:.2f}%",
            'cash': f"{cash:.2f}",
            'total_value': f"{total_value:.2f}",
            'principal_total': f"{principal_total:.2f}",
            'total_pnl': f"{total_pnl:.2f}",
            'real_return': f"{real_return:.2f}%",
            'fetched_at': fetched_at
        }
        
        # 检查是否已存在（同一采集日同一产品）
        if key in existing_map:
            old_row = existing_map[key]
            # 直接覆盖（同日多次运行保持最新状态）
            existing_map[key] = new_row
            updated_count += 1
            logger.debug(f"[覆盖] {product_code} @ {fetch_date}")
        else:
            # 新记录
            existing_map[key] = new_row
            new_count += 1
            logger.debug(f"[新增] {product_code} @ {fetch_date}")
    
    # 重写整个文件（按 fetch_date, 产品顺序 排序）
    # 使用原子写入：先写临时文件，再 os.replace 替换
    if products_order:
        order_index = {code: idx for idx, code in enumerate(products_order)}
    else:
        order_index = {}
    
    def sort_key(x):
        # 先按 fetch_date 排序，再按 products.json 中的顺序排序
        product_idx = order_index.get(x['product_code'], 9999)
        return (x['fetch_date'], product_idx)
    
    tmp_path = snapshot_path.parent / f"daily.csv.tmp.{uuid.uuid4().hex[:8]}"
    
    try:
        with open(tmp_path, 'w', newline='', encoding='utf-8-sig') as f:
            writer = csv.DictWriter(f, fieldnames=FIELDNAMES, extrasaction='ignore')
            writer.writeheader()
            # 写入中文表头行
            f.write(','.join([CHINESE_HEADERS.get(field, field) for field in FIELDNAMES]) + '\n')
            
            sorted_snapshots = sorted(existing_map.values(), key=sort_key)
            for snapshot in sorted_snapshots:
                writer.writerow(snapshot)
            f.flush()
        
        # 原子替换
        os.replace(str(tmp_path), str(snapshot_path))
    except Exception as e:
        # 清理临时文件
        if tmp_path.exists():
            tmp_path.unlink()
        raise e
    
    # 汇总日志
    if updated_count > 0:
        logger.info(f"✓ 快照更新: 新增 {new_count}, 覆盖 {updated_count}")
    elif new_count > 0:
        logger.info(f"✓ 快照新增: {new_count} 条")
    else:
        logger.info(f"✓ 快照无变化")
    
    return new_count + updated_count
