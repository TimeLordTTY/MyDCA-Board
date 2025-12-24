"""场内持仓计算器 - 基于 trade_fills 计算持仓、成本、盈亏"""
import logging
from datetime import date
from decimal import Decimal
from typing import Dict, Optional, Tuple

from data.db_connector import execute_query, execute_one
from core.market_quote_service import get_latest_quote

logger = logging.getLogger(__name__)


def calculate_exchange_holdings(product_id: int, asof_date: Optional[str] = None) -> Dict:
    """
    计算场内持仓（基于 trade_fills）
    
    计算公式：
    - 加权成本：avg_cost = Σ(buy_qty × buy_price) / Σ(buy_qty)
    - 当前持仓：current_qty = Σ(buy_qty) - Σ(sell_qty)
    - 已实现盈亏：realized_pnl = Σ((sell_price - avg_cost_at_sell) × sell_qty - sell_fee - sell_tax)
    - 未实现盈亏：unrealized_pnl = (current_price - avg_cost) × current_qty
    - 总费用：total_fees = Σ(fee + tax + other_fee)
    
    Args:
        product_id: 产品ID
        asof_date: 截止日期（YYYY-MM-DD），None 表示今天
    
    Returns:
        持仓字典，包含：
        - current_qty: 当前持仓数量
        - avg_cost: 平均成本
        - total_cost: 总成本（avg_cost × current_qty）
        - realized_pnl: 已实现盈亏
        - unrealized_pnl: 未实现盈亏
        - total_pnl: 总盈亏（realized + unrealized）
        - total_fees: 总费用
        - current_price: 当前价格
    """
    if asof_date is None:
        asof_date = date.today().strftime('%Y-%m-%d')
    
    # 获取所有买入记录（按时间顺序）
    buy_sql = """
        SELECT 
            trade_date, trade_time, qty, price, amount, fee, tax, other_fee
        FROM trade_fills
        WHERE product_id = %s
          AND side = 'BUY'
          AND trade_date <= %s
        ORDER BY trade_date, trade_time
    """
    buy_records = execute_query(buy_sql, (product_id, asof_date))
    
    # 获取所有卖出记录（按时间顺序）
    sell_sql = """
        SELECT 
            trade_date, trade_time, qty, price, amount, fee, tax, other_fee
        FROM trade_fills
        WHERE product_id = %s
          AND side = 'SELL'
          AND trade_date <= %s
        ORDER BY trade_date, trade_time
    """
    sell_records = execute_query(sell_sql, (product_id, asof_date))
    
    # 计算买入累计
    total_buy_qty = Decimal('0')
    total_buy_cost = Decimal('0')  # Σ(qty × price)
    total_buy_fees = Decimal('0')
    
    for record in buy_records:
        qty = Decimal(str(record['qty']))
        price = Decimal(str(record['price']))
        fee = Decimal(str(record.get('fee', 0)))
        tax = Decimal(str(record.get('tax', 0)))
        other_fee = Decimal(str(record.get('other_fee', 0)))
        
        total_buy_qty += qty
        total_buy_cost += qty * price
        total_buy_fees += fee + tax + other_fee
    
    # 计算卖出累计
    total_sell_qty = Decimal('0')
    total_sell_amount = Decimal('0')  # 卖出到账金额
    total_sell_fees = Decimal('0')
    
    # 计算已实现盈亏（使用平均成本法）
    # 每次卖出时，使用卖出时的平均成本计算
    realized_pnl = Decimal('0')
    remaining_qty = total_buy_qty
    remaining_cost = total_buy_cost
    
    for record in sell_records:
        sell_qty = Decimal(str(record['qty']))
        sell_price = Decimal(str(record['price']))
        fee = Decimal(str(record.get('fee', 0)))
        tax = Decimal(str(record.get('tax', 0)))
        other_fee = Decimal(str(record.get('other_fee', 0)))
        
        if remaining_qty <= 0:
            logger.warning(f"卖出数量超过持仓: product_id={product_id}, sell_qty={sell_qty}")
            break
        
        # 计算卖出时的平均成本
        avg_cost_at_sell = remaining_cost / remaining_qty if remaining_qty > 0 else Decimal('0')
        
        # 计算本次卖出的盈亏
        sell_cost = avg_cost_at_sell * sell_qty
        sell_proceeds = sell_price * sell_qty
        sell_fees = fee + tax + other_fee
        pnl = sell_proceeds - sell_cost - sell_fees
        
        realized_pnl += pnl
        
        # 更新剩余持仓
        remaining_qty -= sell_qty
        remaining_cost -= sell_cost
        
        total_sell_qty += sell_qty
        total_sell_amount += sell_proceeds - sell_fees  # 到账净额
        total_sell_fees += sell_fees
    
    # 当前持仓
    current_qty = total_buy_qty - total_sell_qty
    current_cost = remaining_cost  # 剩余成本
    
    # 平均成本
    avg_cost = current_cost / current_qty if current_qty > 0 else Decimal('0')
    
    # 获取当前价格
    latest_quote = get_latest_quote(product_id)
    current_price = Decimal(str(latest_quote['price'])) if latest_quote else Decimal('0')
    
    # 未实现盈亏
    unrealized_pnl = (current_price - avg_cost) * current_qty if current_qty > 0 else Decimal('0')
    
    # 总盈亏
    total_pnl = realized_pnl + unrealized_pnl
    
    # 总费用
    total_fees = total_buy_fees + total_sell_fees
    
    result = {
        'current_qty': current_qty,
        'avg_cost': avg_cost,
        'total_cost': current_cost,
        'realized_pnl': realized_pnl,
        'unrealized_pnl': unrealized_pnl,
        'total_pnl': total_pnl,
        'total_fees': total_fees,
        'current_price': current_price,
        'total_buy_qty': total_buy_qty,
        'total_sell_qty': total_sell_qty,
        'total_sell_amount': total_sell_amount
    }
    
    logger.debug(f"计算场内持仓: product_id={product_id}, qty={current_qty}, "
                f"avg_cost={avg_cost}, total_pnl={total_pnl}")
    
    return result


def get_exchange_holdings_summary(product_ids: Optional[list] = None) -> Dict[int, Dict]:
    """
    批量计算场内持仓汇总
    
    Args:
        product_ids: 产品ID列表，None 表示所有场内产品
    
    Returns:
        {product_id: holdings_dict} 字典
    """
    
    if product_ids is None:
        from data.product_service import get_products
        products = get_products(channel='EXCHANGE', is_active=True)
        product_ids = [p['id'] for p in products]
    
    result = {}
    for product_id in product_ids:
        try:
            holdings = calculate_exchange_holdings(product_id)
            result[product_id] = holdings
        except Exception as e:
            logger.error(f"计算持仓失败: product_id={product_id}, error={e}", exc_info=True)
            result[product_id] = None
    
    return result

