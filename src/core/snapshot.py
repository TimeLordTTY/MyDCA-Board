"""快照生成模块

仅支持 MySQL 数据库存储。

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
- cash_in_transit: 在途资金（扣款已发生但份额未确认的净申购金额）
- total_value: 产品总资产（value + cash_in_transit）
- principal_total: 累计投入本金（按扣款额累计；不因卖出回笼减少）
- total_redemption: 累计赎回金额（卖出到账净额，已扣除赎回费）
- total_pnl: 生命周期总盈亏（total_value + total_redemption - principal_total）
- real_return: 真实收益率（total_pnl / principal_total）
- fetched_at: 采集时间（毫秒精度）

盈亏计算说明：
- unrealized_pnl（浮动盈亏）= 当前市值 - 当前持仓成本（不考虑已卖出部分）
- total_pnl（生命周期总盈亏）= total_value + total_redemption - principal_total
  - 直观理解：我投了 X 元，现在还有 Y 元在里面，已经拿回了 Z 元，总盈亏 = Y + Z - X
  - 全赎回后：total_pnl = 0 + total_redemption - principal_total = 实际利润
"""
import csv
import os
import uuid
from pathlib import Path
from datetime import datetime
from decimal import Decimal
import logging

from core.holdings_calculator import HoldingsCalculator, calc_position_incremental, has_transactions
from data.db_connector import execute_query, execute_one, execute_insert, execute_many

logger = logging.getLogger(__name__)


# 字段顺序（固定）
FIELDNAMES = [
    'fetch_date', 'product_code', 'product_name', 'category',
    'nav_date', 'nav', 'shares', 'value', 'pnl_day',
    'cost', 'unrealized_pnl', 'return_rate',
    'cash_in_transit', 'total_value', 'principal_total', 'total_redemption', 'total_pnl', 'real_return',
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
    'cash_in_transit': '在途资金',
    'total_value': '总资产',
    'principal_total': '累计投入本金',
    'total_redemption': '累计赎回',
    'total_pnl': '总盈亏',
    'real_return': '真实收益率',
    'fetched_at': '采集时间'
}


def get_last_snapshot_value(snapshot_path, product_code, before_fetch_date=None):
    """
    获取上一个采集日的 value（兼容旧 API，用于 nav_collector.py）
    
    :param snapshot_path: 快照文件路径（已忽略，从数据库读取）
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
    
    :param snapshot_path: 快照文件路径（已忽略，从数据库读取）
    :param product_code: 产品代码
    :param before_fetch_date: 仅获取此日期之前的快照
    :return: {shares, nav, value} 或 None
    """
    sql = """
        SELECT shares, nav, `value`
        FROM daily_snapshot
        WHERE product_code = %s AND fetch_date < %s
        ORDER BY fetch_date DESC
        LIMIT 1
    """
    result = execute_one(sql, (product_code, before_fetch_date))
    
    if result:
        try:
            return {
                'shares': Decimal(str(result.get('shares', '0') or '0')),
                'nav': Decimal(str(result.get('nav', '0') or '0')),
                'value': Decimal(str(result.get('value', '0') or '0'))
            }
        except:
            pass
    return None


def read_all_snapshots(snapshot_path=None):
    """读取所有快照记录"""
    sql = """
        SELECT DATE_FORMAT(fetch_date, '%%Y-%%m-%%d') as fetch_date,
               product_code, product_name, category,
               DATE_FORMAT(nav_date, '%%Y-%%m-%%d') as nav_date,
               nav, shares, `value`, pnl_day, cost,
               unrealized_pnl, return_rate, cash_in_transit,
               total_value, principal_total, total_redemption,
               total_pnl, real_return, fetched_at
        FROM daily_snapshot
        ORDER BY fetch_date, product_code
    """
    return execute_query(sql)


def rebuild_snapshots_from_date(snapshot_path, rebuild_from_date):
    """
    重建快照（保护历史数据版本）
    
    设计原则：
    - 永远不删除历史数据（任何 fetch_date < today 的记录都会保留）
    - 只允许覆盖当天的数据，由 create_daily_snapshot 自动处理
    - rebuild 操作仅用于触发重新计算，不会删除任何已有快照
    
    :param snapshot_path: 快照文件路径（已忽略）
    :param rebuild_from_date: 重建起始日期（已忽略，不再用于删除）
    :return: (保留数量, 0)  # 不再删除任何记录
    """
    all_snapshots = read_all_snapshots()
    today = datetime.now().strftime('%Y-%m-%d')
    
    # 检查是否有历史数据（早于今天的数据）
    history_count = sum(1 for row in all_snapshots 
                       if row.get('fetch_date', '')[:10] < today)
    
    logger.info(f"保护历史快照: 共 {len(all_snapshots)} 条记录, 其中历史数据 {history_count} 条（不会被删除）")
    logger.info(f"提示: rebuild 操作现在只会覆盖当天数据，不会删除任何历史记录")
    
    # 不删除任何数据，直接返回
    return len(all_snapshots), 0


def create_daily_snapshot(nav_records, holdings_map, products_map, snapshot_path=None, 
                          products_order=None, category_map=None, market_map=None):
    """
    生成日快照
    
    设计原则：
    - 唯一键：(fetch_date, product_code)
    - 同一采集日，同一产品只有一条记录
    - 同一天多次运行会覆盖（保持最新状态）
    - pnl_day = prev_shares * (nav_today - nav_prev)（只由净值变化贡献）
    - total_pnl = total_value + total_redemption - principal_total（生命周期口径）
    
    :param nav_records: {product_code: nav_dict}
    :param holdings_map: {product_code: shares} (备用，优先用 HoldingsCalculator)
    :param products_map: {product_code: product_name}
    :param snapshot_path: 可选，指定快照文件路径（主要用于 CSV 兼容）
    :param products_order: 可选，产品代码列表，用于保持排序顺序
    :param category_map: 可选，{product_code: category}，产品分类
    :param market_map: 可选，{product_code: market}，产品市场类型（用于 cash_like 特殊处理）
    """
    from data.config_loader import get_project_root
    
    if snapshot_path is None:
        snapshot_path = get_project_root() / "data" / "snapshots" / "daily.csv"
    else:
        snapshot_path = Path(snapshot_path)
    
    if category_map is None:
        category_map = {}
    
    if market_map is None:
        market_map = {}
    
    Path(snapshot_path).parent.mkdir(parents=True, exist_ok=True)
    
    # 当前采集时间
    fetched_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:23]  # 毫秒精度
    fetch_date = fetched_at[:10]  # YYYY-MM-DD
    
    # 创建持仓计算器，获取所有持仓数据
    calc = HoldingsCalculator()
    all_holdings_data = calc.get_all_holdings_data_as_of(fetch_date)
    
    # 读取所有现有快照，按 (fetch_date, product_code) 索引
    all_snapshots = read_all_snapshots()
    existing_map = {}
    for row in all_snapshots:
        row_fetch_date = row.get('fetch_date', '')[:10]
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
        
        # 获取持仓数据
        if product_code in all_holdings_data:
            h = all_holdings_data[product_code]
            shares = h["shares"]
            cost = h["cost"]
            cash_in_transit = h["cash_in_transit"]
            principal_total = h["principal_total"]
            total_redemption = h["total_redemption"]
        else:
            # 回退到 holdings_map
            shares = Decimal(str(holdings_map.get(product_code, 0)))
            cost = Decimal('0')
            cash_in_transit = Decimal('0')
            principal_total = Decimal('0')
            total_redemption = Decimal('0')
        
        # 检查是否为 cash_like 产品（如货币基金）
        # 对于 cash_like 产品，NAV 显示的是万份收益，计算市值时使用 NAV=1
        market = market_map.get(product_code, 'cn')
        if market == 'cash_like':
            nav = Decimal('1')  # 货币基金按 1:1 计算市值
        else:
            nav = Decimal(str(nav_record['nav']))
        value = shares * nav
        
        # 计算总资产
        total_value = value + cash_in_transit
        
        # 计算生命周期总盈亏（核心公式变更）
        # total_pnl = total_value + total_redemption - principal_total
        # 直观：投了 principal_total，现在还有 total_value 在里面，已拿回 total_redemption
        if principal_total > 0:
            total_pnl = total_value + total_redemption - principal_total
        else:
            total_pnl = Decimal('0')
        
        # 计算真实收益率（生命周期口径）
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
            'shares': f"{shares:.4f}",  # 份额精度至少4位
            'value': f"{value:.2f}",
            'pnl_day': f"{pnl_day:.2f}",
            'cost': f"{cost:.2f}",
            'unrealized_pnl': f"{unrealized_pnl:.2f}",
            'return_rate': f"{return_rate:.2f}%",
            'cash_in_transit': f"{cash_in_transit:.2f}",
            'total_value': f"{total_value:.2f}",
            'principal_total': f"{principal_total:.2f}",
            'total_redemption': f"{total_redemption:.2f}",
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
    
    # 同步到数据库
    _db_sync_daily_snapshots(existing_map, fetch_date)
    
    # 同时写入 CSV 文件（保持兼容）
    _write_csv_snapshots(existing_map, snapshot_path, products_order)
    
    # 汇总日志
    if updated_count > 0:
        logger.info(f"✓ 快照更新: 新增 {new_count}, 覆盖 {updated_count}")
    elif new_count > 0:
        logger.info(f"✓ 快照新增: {new_count} 条")
    else:
        logger.info(f"✓ 快照无变化")
    
    return new_count + updated_count


def _write_csv_snapshots(snapshots_map, snapshot_path, products_order=None):
    """写入 CSV 快照文件（保持兼容）"""
    if products_order:
        order_index = {code: idx for idx, code in enumerate(products_order)}
    else:
        order_index = {}
    
    def sort_key(x):
        product_idx = order_index.get(x['product_code'], 9999)
        return (x['fetch_date'], product_idx)
    
    tmp_path = snapshot_path.parent / f"daily.csv.tmp.{uuid.uuid4().hex[:8]}"
    
    try:
        with open(tmp_path, 'w', newline='', encoding='utf-8-sig') as f:
            writer = csv.DictWriter(f, fieldnames=FIELDNAMES, extrasaction='ignore')
            writer.writeheader()
            # 写入中文表头行
            f.write(','.join([CHINESE_HEADERS.get(field, field) for field in FIELDNAMES]) + '\n')
            
            sorted_snapshots = sorted(snapshots_map.values(), key=sort_key)
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


def _db_sync_daily_snapshots(snapshots_map, fetch_date):
    """同步快照到数据库"""
    # 获取当天的快照
    today_snapshots = [
        row for row in snapshots_map.values() 
        if row.get('fetch_date') == fetch_date
    ]
    
    if not today_snapshots:
        return
    
    # 使用 INSERT ... ON DUPLICATE KEY UPDATE
    sql = """
        INSERT INTO daily_snapshot 
        (fetch_date, product_code, product_name, category, nav_date, nav,
         shares, `value`, pnl_day, cost, unrealized_pnl, return_rate,
         cash_in_transit, total_value, principal_total, total_redemption,
         total_pnl, real_return, fetched_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            product_name = VALUES(product_name),
            category = VALUES(category),
            nav_date = VALUES(nav_date),
            nav = VALUES(nav),
            shares = VALUES(shares),
            `value` = VALUES(`value`),
            pnl_day = VALUES(pnl_day),
            cost = VALUES(cost),
            unrealized_pnl = VALUES(unrealized_pnl),
            return_rate = VALUES(return_rate),
            cash_in_transit = VALUES(cash_in_transit),
            total_value = VALUES(total_value),
            principal_total = VALUES(principal_total),
            total_redemption = VALUES(total_redemption),
            total_pnl = VALUES(total_pnl),
            real_return = VALUES(real_return),
            fetched_at = VALUES(fetched_at)
    """
    
    params_list = []
    for row in today_snapshots:
        # 清理百分号
        return_rate = row.get('return_rate', '0').replace('%', '')
        real_return = row.get('real_return', '0').replace('%', '')
        
        params_list.append((
            row.get('fetch_date'),
            row.get('product_code'),
            row.get('product_name'),
            row.get('category'),
            row.get('nav_date'),
            row.get('nav'),
            row.get('shares'),
            row.get('value'),
            row.get('pnl_day'),
            row.get('cost'),
            row.get('unrealized_pnl'),
            return_rate or None,
            row.get('cash_in_transit'),
            row.get('total_value'),
            row.get('principal_total'),
            row.get('total_redemption'),
            row.get('total_pnl'),
            real_return or None,
            row.get('fetched_at')
        ))
    
    if params_list:
        affected = execute_many(sql, params_list)
        logger.info(f"✓ 数据库同步: {affected} 条快照")
