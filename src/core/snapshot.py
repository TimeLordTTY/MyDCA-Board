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
from pathlib import Path
from datetime import datetime
from decimal import Decimal
import logging

from core.holdings_calculator import HoldingsCalculator, calc_position_incremental, has_transactions
from data.db_connector import execute_query, execute_one, execute_insert, execute_many
from utils.decimal_utils import to_dec, q_money, q_shares, q_nav, format_money, format_shares, format_nav

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
                'shares': to_dec(result.get('shares', '0')),
                'nav': to_dec(result.get('nav', '0')),
                'value': to_dec(result.get('value', '0'))
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
    
    # 获取场内产品持仓数据（基于 trade_fills）
    from data.product_service import get_products
    from core.exchange_holdings_calculator import calculate_exchange_holdings
    exchange_products = get_products(channel='EXCHANGE', is_active=True)
    exchange_holdings_map = {}  # {product_code: holdings_dict}
    for product in exchange_products:
        product_code = product.get('code', '')
        product_id = product.get('id')
        if product_id:
            try:
                holdings = calculate_exchange_holdings(product_id, asof_date=fetch_date)
                # 即使持仓为0也记录，确保快照中有该产品
                if holdings:
                    # 将场内持仓数据转换为快照格式
                    exchange_holdings_map[product_code] = {
                        'shares': Decimal(str(holdings.get('current_qty', 0))),
                        'cost': Decimal(str(holdings.get('total_cost', 0))),
                        'cash_in_transit': Decimal('0'),  # 场内无在途资金
                        'principal_total': Decimal(str(holdings.get('total_cost', 0))),  # 使用总成本作为本金
                        'total_redemption': Decimal('0'),  # 场内暂不支持赎回
                        'realized_pnl': Decimal(str(holdings.get('realized_pnl', 0))),
                        'unrealized_pnl': Decimal(str(holdings.get('unrealized_pnl', 0))),
                        'total_pnl': Decimal(str(holdings.get('total_pnl', 0)))
                    }
            except Exception as e:
                logger.warning(f"计算场内产品 {product_code} 持仓失败: {e}")
    
    # 读取所有现有快照，按 (fetch_date, product_code) 索引
    all_snapshots = read_all_snapshots()
    existing_map = {}
    for row in all_snapshots:
        row_fetch_date = row.get('fetch_date', '')[:10]
        key = (row_fetch_date, row['product_code'])
        existing_map[key] = row
    
    # 处理有持仓但没有 nav_records 的场内产品（确保所有有持仓的场内产品都生成快照）
    for product_code, holdings_data in exchange_holdings_map.items():
        if product_code not in nav_records:
            # 有持仓但没有价格数据，尝试获取实时行情
            from data.product_service import get_product_by_code
            from core.market_quote_service import get_latest_quote
            product_info = get_product_by_code(product_code)
            if product_info and product_info.get('id'):
                quote = get_latest_quote(product_info['id'])
                if quote and quote.get('price'):
                    nav_records[product_code] = {
                        'nav_date': fetch_date,
                        'nav': quote.get('price')
                    }
    
    # 统计
    new_count = 0
    updated_count = 0
    skipped_count = 0
    
    # 处理每个产品
    for product_code, nav_record in nav_records.items():
        product_name = products_map.get(product_code, '')
        nav_date = nav_record['nav_date']  # 净值日期
        key = (fetch_date, product_code)  # 唯一键：采集日期 + 产品
        
        # 获取持仓数据：优先使用场内持仓，其次使用场外持仓
        if product_code in exchange_holdings_map:
            # 场内产品：使用 trade_fills 计算的持仓
            h = exchange_holdings_map[product_code]
            shares = h["shares"]
            cost = h["cost"]
            cash_in_transit = h["cash_in_transit"]
            principal_total = h["principal_total"]
            total_redemption = h["total_redemption"]
        elif product_code in all_holdings_data:
            # 场外产品：使用 transactions 计算的持仓
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
            
            # 调试：如果场外产品没有匹配到持仓数据，记录警告
            # 检查是否有相似的产品代码在 all_holdings_data 中
            if product_code not in exchange_holdings_map:
                # 这是场外产品，但没有匹配到持仓数据
                available_codes = list(all_holdings_data.keys())
                if available_codes:
                    logger.warning(
                        f"场外产品 {product_code} ({product_name}) 没有匹配到持仓数据。"
                        f"可用的 product_code: {available_codes[:10]}"  # 只显示前10个
                    )
                else:
                    logger.debug(
                        f"场外产品 {product_code} ({product_name}) 没有匹配到持仓数据，"
                        f"且 all_holdings_data 为空（可能没有交易记录）"
                    )
        
        # 确保所有输入都是 Decimal
        shares = to_dec(shares)
        cost = to_dec(cost)
        cash_in_transit = to_dec(cash_in_transit)
        principal_total = to_dec(principal_total)
        total_redemption = to_dec(total_redemption)
        
        # 检查是否为 cash_like 产品（如货币基金）
        # 对于 cash_like 产品，NAV 显示的是万份收益，计算市值时使用 NAV=1
        market = market_map.get(product_code, 'cn')
        if market == 'cash_like':
            nav = Decimal('1')  # 货币基金按 1:1 计算市值
        else:
            nav = q_nav(to_dec(nav_record['nav']))  # 净值保留4位
        
        # 计算市值（内部保留高精度，输出时再舍入）
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
        # 必须剔除资金流影响，只反映市场波动
        prev_snapshot = get_prev_snapshot(snapshot_path, product_code, fetch_date)
        data_status = 'ok'  # 默认状态正常
        if prev_snapshot is not None:
            prev_shares = to_dec(prev_snapshot['shares'])
            prev_nav = to_dec(prev_snapshot['nav'])
            pnl_day = prev_shares * (nav - prev_nav)
        else:
            # 没有上一交易日快照，pnl_day = 0
            pnl_day = Decimal('0')
            # 检查是否是因为节假日/周末（这里简化处理，实际可以结合交易日历）
            data_status = 'missing'  # 标记为缺数据
        
        # 构建新记录（输出时使用舍入函数）
        new_row = {
            'fetch_date': fetch_date,
            'product_code': product_code,
            'product_name': product_name,
            'category': category_map.get(product_code, 'fund'),
            'nav_date': nav_date,
            'nav': format_nav(nav),  # 净值保留4位
            'shares': format_shares(shares),  # 份额保留6位
            'value': format_money(value),  # 金额保留2位
            'pnl_day': format_money(pnl_day),  # 金额保留2位
            'cost': format_money(cost),  # 金额保留2位
            'unrealized_pnl': format_money(unrealized_pnl),  # 金额保留2位
            'return_rate': f"{q_money(return_rate):.2f}%",  # 收益率保留2位
            'cash_in_transit': format_money(cash_in_transit),  # 金额保留2位
            'total_value': format_money(total_value),  # 金额保留2位
            'principal_total': format_money(principal_total),  # 金额保留2位
            'total_redemption': format_money(total_redemption),  # 金额保留2位
            'total_pnl': format_money(total_pnl),  # 金额保留2位
            'real_return': f"{q_money(real_return):.2f}%",  # 收益率保留2位
            'fetched_at': fetched_at,
            'data_status': data_status  # 数据状态（用于缺数据兜底）
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
    
    # 汇总日志
    if updated_count > 0:
        logger.info(f"✓ 快照更新: 新增 {new_count}, 覆盖 {updated_count}")
    elif new_count > 0:
        logger.info(f"✓ 快照新增: {new_count} 条")
    else:
        logger.info(f"✓ 快照无变化")
    
    return new_count + updated_count


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
         total_pnl, real_return, data_status, fetched_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
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
            data_status = VALUES(data_status),
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
            row.get('data_status', 'ok'),  # 默认 ok
            row.get('fetched_at')
        ))
    
    if params_list:
        affected = execute_many(sql, params_list)
        logger.info(f"✓ 数据库同步: {affected} 条快照")
