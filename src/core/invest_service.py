#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
理财业务服务层

提供理财操作（transactions.csv + orders.csv）：
- 买入扣款、赎回发起、历史补录
- 订单结算
- 校验交易和订单数据

UI 和 CLI 都通过此服务操作理财数据，避免业务逻辑重复。
"""
from datetime import datetime, date
from decimal import Decimal, ROUND_HALF_UP
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass, field

from data.data_store import (
    load_transactions, append_transaction, transaction_exists,
    load_orders, append_order, get_pending_orders, update_order_status, update_order,
    generate_order_id, format_decimal, parse_decimal,
    VALID_ACTIONS
)
from data.config_loader import load_products, get_product, get_sell_fee_rate
from data.product_service import get_product_by_code
from data.nav_reader import get_nav
from utils.trade_calendar import (
    is_trade_day, next_trade_day, add_trade_days, subtract_trade_days
)


@dataclass
class SettleResult:
    """结算结果"""
    settled: List[Dict] = field(default_factory=list)   # 成功结算的订单
    skipped: List[Dict] = field(default_factory=list)   # 跳过的订单（已存在确认记录）
    errors: List[Dict] = field(default_factory=list)    # 失败的订单（缺净值等）


@dataclass
class ValidationResult:
    """校验结果"""
    success: bool
    errors: List[str]
    warnings: List[str]


def calc_trade_date(requested_at: datetime, cutoff_time: str = '15:00') -> date:
    """
    计算交易日期
    
    规则：
    - 如果 requested_at 在交易日且时间 <= cutoff_time，则 trade_date = 当天
    - 否则 trade_date = next_trade_day(当天)
    """
    request_date = requested_at.date()
    cutoff = datetime.strptime(cutoff_time, '%H:%M').time()
    request_time = requested_at.time()
    
    if is_trade_day(request_date) and request_time <= cutoff:
        return request_date
    else:
        return next_trade_day(request_date)


def calc_confirm_date(trade_date: date, confirm_offset: int) -> date:
    """计算确认日期"""
    return add_trade_days(trade_date, confirm_offset)


def add_buy_debit(
    product_code: str,
    amount: Decimal,
    fee: Decimal = None,
    requested_at: datetime = None,
    note: str = None,
    channel: str = None
) -> str:
    """
    添加买入扣款（支持场内/场外）
    
    Args:
        product_code: 产品代码
        amount: 扣款金额（含手续费）
        fee: 手续费（可选，默认自动计算）
        requested_at: 请求时间（默认当前时间）
        note: 备注（默认产品名称）
        channel: 渠道（EXCHANGE/OTC），None 表示优先场外
    
    Returns:
        order_id: 生成的订单号
    
    Raises:
        ValueError: 产品不存在
    """
    from data.product_service import get_product_by_code
    product = get_product_by_code(product_code, channel=channel)
    if product is None:
        raise ValueError(f"产品不存在: product_code={product_code}, channel={channel}")
    
    product_name = product['product_name']
    buy_fee_rate = Decimal(str(product.get('buy_fee_rate', 0)))
    buy_confirm_offset = product.get('buy_confirm_offset', 1)
    cutoff_time = product.get('cutoff_time', '15:00')
    
    if requested_at is None:
        requested_at = datetime.now()
    
    # 计算手续费
    if fee is None:
        fee = (amount * buy_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
    
    # 计算日期
    trade_date = calc_trade_date(requested_at, cutoff_time)
    nav_date = trade_date
    confirm_date = calc_confirm_date(trade_date, buy_confirm_offset)
    
    # 生成订单号（包含请求时间）
    requested_at_str = requested_at.strftime('%Y-%m-%d %H:%M:%S')
    order_id = generate_order_id(product_code) + '_' + requested_at_str
    
    if note is None:
        note = product_name
    
    # 写入 transactions (buy_debit)，使用请求时间作为 created_at
    tx_record = {
        'date': str(trade_date),
        'product_id': product.get('id'),  # 添加 product_id
        'product_code': product_code,
        'action': 'buy_debit',
        'amount': format_decimal(amount, 2),
        'shares': '',
        'fee': format_decimal(fee, 2),
        'nav': '',
        'nav_date': '',
        'order_id': order_id,
        'note': note,
        'created_at': requested_at_str  # 使用请求时间作为记录时间
    }
    append_transaction(tx_record)
    
    # 写入 orders
    order_record = {
        'order_id': order_id,
        'product_id': product.get('id'),  # 添加 product_id
        'product_code': product_code,
        'order_type': 'buy_debit',
        'amount': format_decimal(amount, 2),
        'fee': format_decimal(fee, 2),
        'shares': '',
        'requested_at': requested_at.strftime('%Y-%m-%d %H:%M:%S'),
        'trade_date': str(trade_date),
        'nav_date': str(nav_date),
        'confirm_date': str(confirm_date),
        'holding_days': '',
        'sell_fee_rate': '',
        'status': 'pending',
        'note': note
    }
    append_order(order_record)
    
    return order_id


def add_redeem_request(
    product_code: str,
    shares: Decimal,
    holding_days: int,
    requested_at: datetime = None,
    note: str = None
) -> str:
    """
    添加赎回发起
    
    Args:
        product_code: 产品代码
        shares: 赎回份额
        holding_days: 持有天数（用于确定赎回费率）
        requested_at: 请求时间（默认当前时间）
        note: 备注（默认产品名称）
    
    Returns:
        order_id: 生成的订单号
    
    Raises:
        ValueError: 产品不存在
    """
    from data.product_service import get_product_by_code
    product = get_product_by_code(product_code, channel=channel)
    if product is None:
        raise ValueError(f"产品不存在: product_code={product_code}, channel={channel}")
    
    product_name = product['product_name']
    sell_confirm_offset = product.get('sell_confirm_offset', 1)
    cutoff_time = product.get('cutoff_time', '15:00')
    
    if requested_at is None:
        requested_at = datetime.now()
    
    # 获取赎回费率
    sell_fee_rate = Decimal(str(get_sell_fee_rate(product, holding_days)))
    
    # 计算日期
    trade_date = calc_trade_date(requested_at, cutoff_time)
    nav_date = trade_date
    confirm_date = calc_confirm_date(trade_date, sell_confirm_offset)
    
    # 生成订单号
    order_id = generate_order_id(product_code)
    
    if note is None:
        note = product_name
    
    # 只写入 orders（不写 transactions）
    order_record = {
        'order_id': order_id,
        'product_id': product.get('id'),  # 添加 product_id
        'product_code': product_code,
        'order_type': 'redeem_request',
        'amount': '',
        'fee': '',
        'shares': format_decimal(shares, 4),
        'requested_at': requested_at.strftime('%Y-%m-%d %H:%M:%S'),
        'trade_date': str(trade_date),
        'nav_date': str(nav_date),
        'confirm_date': str(confirm_date),
        'holding_days': str(holding_days),
        'sell_fee_rate': str(sell_fee_rate),
        'status': 'pending',
        'note': note
    }
    append_order(order_record)
    
    return order_id


def add_history_trade(
    product_code: str,
    action: str,
    confirm_date: str,
    shares: Decimal,
    amount: Decimal = None,
    fee: Decimal = None,
    nav: Decimal = None,
    nav_date: str = None,
    note: str = None,
    confirm_time: str = None  # 新增：确认时间 HH:MM:SS
) -> Dict:
    """
    补录历史交易（已完成的 buy/sell/dividend）
    
    Args:
        product_code: 产品代码
        action: 交易类型 (buy/sell/dividend)
        confirm_date: 确认日期 (YYYY-MM-DD)
        shares: 份额
        amount: 金额（buy=扣款金额，sell=到账净额，dividend不需要）
        fee: 手续费
        nav: 净值
        nav_date: 净值日期
        note: 备注
        confirm_time: 确认时间 (HH:MM:SS)，可选，默认 09:30:00
    
    Returns:
        写入的交易记录
    
    Raises:
        ValueError: 产品不存在或 action 无效
    """
    if action not in ['buy', 'sell', 'dividend']:
        raise ValueError(f"无效的 action: {action}")
    
    from data.product_service import get_product_by_code
    product = get_product_by_code(product_code, channel=channel)
    if product is None:
        raise ValueError(f"产品不存在: product_code={product_code}, channel={channel}")
    
    if note is None:
        note = product['product_name']
    
    # 确认时间默认为 09:30:00
    if confirm_time is None:
        confirm_time = '09:30:00'
    
    # 构造完整的 created_at
    created_at = f"{confirm_date} {confirm_time}"
    
    # 获取 product_id
    product_id = product.get('id')
    
    tx_record = {
        'date': confirm_date,
        'product_id': product_id,
        'product_code': product_code,
        'action': action,
        'amount': format_decimal(amount, 2) if amount and amount > 0 else '',
        'shares': format_decimal(shares, 4),
        'fee': format_decimal(fee, 2) if fee and fee > 0 else '',
        'nav': format_decimal(nav, 4) if nav and nav > 0 else '',
        'nav_date': nav_date if nav and nav > 0 else '',
        'order_id': '',  # 补录不需要 order_id
        'note': note,
        'created_at': created_at  # 使用确认日期+时间作为记录时间
    }
    
    append_transaction(tx_record)
    return tx_record


def list_pending_orders(before_date: str = None) -> List[Dict]:
    """
    获取待结算订单
    
    Args:
        before_date: 只返回 confirm_date <= before_date 的订单（可选）
    
    Returns:
        待结算订单列表
    """
    return get_pending_orders(before_date)


def list_all_orders() -> List[Dict]:
    """获取所有订单"""
    from data.data_store import load_orders
    return load_orders()


def list_recent_transactions(n: int = 20) -> List[Dict]:
    """
    获取最近的交易记录（优化版，直接在数据库排序和限制）
    
    Args:
        n: 返回条数
    
    Returns:
        交易记录列表（按时间倒序）
    """
    from data.data_store import load_recent_transactions
    return load_recent_transactions(n)


def update_transaction_entry(record_id: int, record: Dict) -> bool:
    """
    更新交易记录
    
    Args:
        record_id: 记录 ID
        record: 更新后的记录数据
    
    Returns:
        是否更新成功
    """
    from data.data_store import update_transaction
    return update_transaction(record_id, record)


def settle_orders(target_date: str = None) -> SettleResult:
    """
    结算待处理订单
    
    Args:
        target_date: 目标日期，只结算 confirm_date <= target_date 的订单
                     默认为今天
    
    Returns:
        SettleResult 包含成功、跳过、失败的订单列表
    
    特性：
    - 幂等：已存在确认记录的订单会跳过
    - 缺净值安全：缺少净值的订单保持 pending，不崩溃
    """
    if target_date is None:
        target_date = date.today().strftime('%Y-%m-%d')
    
    result = SettleResult()
    pending = get_pending_orders(before_date=target_date)
    
    for order in pending:
        order_id = order['order_id']
        order_type = order['order_type']
        product_code = order['product_code']
        nav_date = order.get('nav_date', '')
        
        # 幂等性检查
        confirm_action = 'buy_confirm' if order_type == 'buy_debit' else 'sell_confirm'
        if transaction_exists(order_id, confirm_action):
            update_order_status(order_id, 'done')
            result.skipped.append({
                'order': order,
                'reason': '已存在确认记录'
            })
            continue
        
        # 获取净值
        nav = get_nav(product_code, nav_date)
        if nav is None:
            result.errors.append({
                'order': order,
                'reason': f'缺少净值: {product_code} @ {nav_date}'
            })
            continue
        
        # 获取产品配置
        product = get_product_by_code(product_code)
        if product is None:
            result.errors.append({
                'order': order,
                'reason': f'找不到产品配置: {product_code}'
            })
            continue
        
        try:
            if order_type == 'buy_debit':
                # 买入确认
                amount = parse_decimal(order.get('amount', 0))
                fee = parse_decimal(order.get('fee', 0))
                
                # 如果手续费为空或0，尝试从产品配置重新计算
                if fee == 0:
                    product_obj = get_product_by_code(product_code)
                    if product_obj:
                        buy_fee_rate = Decimal(str(product_obj.get('buy_fee_rate', 0)))
                        if buy_fee_rate > 0:
                            fee = (amount * buy_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
                
                # 计算净申购金额（金额 - 手续费）
                net_amount = amount - fee
                
                # 计算份额：净申购金额 / 净值（保持6位小数精度，与数据库字段匹配）
                shares = (net_amount / nav).quantize(Decimal('0.000001'), rounding=ROUND_HALF_UP)
                
                # 写入 buy_confirm
                # 使用订单的确认日期，时间默认 12:00:00
                confirm_date_str = order.get('confirm_date', target_date)
                created_at = f"{confirm_date_str} 12:00:00"
                
                # 获取 product_id
                from data.product_service import get_product_by_code
                product = get_product_by_code(product_code)
                product_id = product.get('id') if product else None
                
                tx_record = {
                    'date': confirm_date_str,
                    'product_id': product_id,  # 添加 product_id
                    'product_code': product_code,
                    'action': 'buy_confirm',
                    'amount': '',
                    'shares': format_decimal(shares, 6),
                    'fee': '0',
                    'nav': str(nav),
                    'nav_date': nav_date,
                    'order_id': order_id,
                    'note': order.get('note', ''),
                    'created_at': created_at
                }
                append_transaction(tx_record)
                
            elif order_type == 'redeem_request':
                # 赎回确认
                shares = parse_decimal(order.get('shares', 0))
                sell_fee_rate_str = order.get('sell_fee_rate', '0')
                sell_fee_rate = Decimal(str(sell_fee_rate_str)) if sell_fee_rate_str else Decimal('0')
                
                # 计算到账金额
                gross = shares * nav
                fee = (gross * sell_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
                amount = gross - fee
                
                # 获取 product_id
                product_obj = get_product_by_code(product_code)
                product_id = product_obj.get('id') if product_obj else None
                
                # 写入 sell_confirm
                # 使用订单的确认日期，时间默认 12:00:00
                confirm_date_str = order.get('confirm_date', target_date)
                created_at = f"{confirm_date_str} 12:00:00"
                
                tx_record = {
                    'date': confirm_date_str,
                    'product_id': product_id,
                    'product_code': product_code,
                    'action': 'sell_confirm',
                    'amount': format_decimal(amount, 2),
                    'shares': format_decimal(shares, 4),
                    'fee': format_decimal(fee, 2),
                    'nav': str(nav),
                    'nav_date': nav_date,
                    'order_id': order_id,
                    'note': order.get('note', ''),
                    'created_at': created_at
                }
                append_transaction(tx_record)
            
            # 标记订单完成
            update_order_status(order_id, 'done')
            result.settled.append({
                'order': order,
                'nav': nav,
                'confirm_action': confirm_action
            })
            
        except Exception as e:
            result.errors.append({
                'order': order,
                'reason': str(e)
            })
    
    return result


@dataclass
class SingleSettleResult:
    """单订单结算结果"""
    success: bool
    order_id: str
    nav: Optional[Decimal] = None
    shares: Optional[Decimal] = None
    amount: Optional[Decimal] = None
    message: str = ''


def settle_single_order(
    order_id: str, 
    nav_override: Decimal = None,
    confirm_datetime: str = None
) -> SingleSettleResult:
    """
    结算单个订单
    
    Args:
        order_id: 订单ID
        nav_override: 可选，手动指定净值（用于测试或特殊场景）
        confirm_datetime: 可选，确认时间（格式：YYYY-MM-DD HH:MM:SS），默认使用订单的确认日期
    
    Returns:
        SingleSettleResult 包含结算结果
    """
    # 查找订单
    orders = load_orders()
    order = None
    for o in orders:
        if o.get('order_id') == order_id:
            order = o
            break
    
    if order is None:
        return SingleSettleResult(
            success=False,
            order_id=order_id,
            message=f'找不到订单: {order_id}'
        )
    
    if order.get('status') != 'pending':
        return SingleSettleResult(
            success=False,
            order_id=order_id,
            message=f'订单状态不是 pending: {order.get("status")}'
        )
    
    order_type = order['order_type']
    product_code = order['product_code']
    nav_date = order.get('nav_date', '')
    
    # 幂等性检查
    confirm_action = 'buy_confirm' if order_type == 'buy_debit' else 'sell_confirm'
    if transaction_exists(order_id, confirm_action):
        update_order_status(order_id, 'done')
        return SingleSettleResult(
            success=False,
            order_id=order_id,
            message='已存在确认记录'
        )
    
    # 获取净值
    nav = nav_override
    if nav is None:
        nav = get_nav(product_code, nav_date)
    
    if nav is None:
        return SingleSettleResult(
            success=False,
            order_id=order_id,
            message=f'缺少净值: {product_code} @ {nav_date}'
        )
    
    # 获取产品配置
    product = get_product_by_code(product_code)
    if product is None:
        return SingleSettleResult(
            success=False,
            order_id=order_id,
            message=f'找不到产品配置: {product_code}'
        )
    
    # 处理确认时间
    if confirm_datetime:
        # 从 confirm_datetime 提取日期和完整时间
        confirm_date_str = confirm_datetime[:10]  # YYYY-MM-DD
        created_at = confirm_datetime
    else:
        # 使用订单的确认日期，时间默认 12:00:00
        confirm_date_str = order.get('confirm_date', date.today().strftime('%Y-%m-%d'))
        created_at = f"{confirm_date_str} 12:00:00"
    
    try:
        if order_type == 'buy_debit':
            # 买入确认
            amount = parse_decimal(order.get('amount', 0))
            fee = parse_decimal(order.get('fee', 0))
            
            # 如果手续费为空或0，尝试从产品配置重新计算
            if fee == 0:
                product_obj = get_product_by_code(product_code)
                if product_obj:
                    buy_fee_rate = Decimal(str(product_obj.get('buy_fee_rate', 0)))
                    if buy_fee_rate > 0:
                        fee = (amount * buy_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
            
            # 计算净申购金额（金额 - 手续费）
            net_amount = amount - fee
            
            # 计算份额：净申购金额 / 净值（保持6位小数精度，与数据库字段匹配）
            shares = (net_amount / nav).quantize(Decimal('0.000001'), rounding=ROUND_HALF_UP)
            
            # 获取 product_id
            product_obj = get_product_by_code(product_code)
            product_id = product_obj.get('id') if product_obj else None
            
            # 写入 buy_confirm
            tx_record = {
                'date': confirm_date_str,
                'product_id': product_id,
                'product_code': product_code,
                'action': 'buy_confirm',
                'amount': '',
                'shares': format_decimal(shares, 6),
                'fee': '0',
                'nav': str(nav),
                'nav_date': nav_date,
                'order_id': order_id,
                'note': order.get('note', ''),
                'created_at': created_at
            }
            append_transaction(tx_record)
            
            # 更新订单（份额和状态）
            update_order(order_id, {
                'shares': format_decimal(shares, 4),
                'status': 'done'
            })
            
            return SingleSettleResult(
                success=True,
                order_id=order_id,
                nav=nav,
                shares=shares,
                amount=net_amount,
                message=f'买入确认成功: {shares:.4f} 份 @ {nav}'
            )
            
        elif order_type == 'redeem_request':
            # 赎回确认
            shares = parse_decimal(order.get('shares', 0))
            sell_fee_rate_str = order.get('sell_fee_rate', '0')
            sell_fee_rate = Decimal(str(sell_fee_rate_str)) if sell_fee_rate_str else Decimal('0')
            
            # 计算到账金额
            gross = shares * nav
            fee = (gross * sell_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
            amount = gross - fee
            
            # 获取 product_id
            product_obj = get_product_by_code(product_code)
            product_id = product_obj.get('id') if product_obj else None
            
            # 写入 sell_confirm
            tx_record = {
                'date': confirm_date_str,
                'product_id': product_id,
                'product_code': product_code,
                'action': 'sell_confirm',
                'amount': format_decimal(amount, 2),
                'shares': format_decimal(shares, 4),
                'fee': format_decimal(fee, 2),
                'nav': str(nav),
                'nav_date': nav_date,
                'order_id': order_id,
                'note': order.get('note', ''),
                'created_at': created_at
            }
            append_transaction(tx_record)
            
            # 标记订单完成
            update_order_status(order_id, 'done')
            
            return SingleSettleResult(
                success=True,
                order_id=order_id,
                nav=nav,
                shares=shares,
                amount=amount,
                message=f'赎回确认成功: {shares:.4f} 份 @ {nav}，到账 {amount:.2f}'
            )
        
        else:
            return SingleSettleResult(
                success=False,
                order_id=order_id,
                message=f'不支持的订单类型: {order_type}'
            )
            
    except Exception as e:
        return SingleSettleResult(
            success=False,
            order_id=order_id,
            message=str(e)
        )


def get_order_by_id(order_id: str) -> Optional[Dict]:
    """根据订单ID获取订单"""
    orders = load_orders()
    for o in orders:
        if o.get('order_id') == order_id:
            return o
    return None


def preview_settle(order_id: str) -> Dict:
    """
    预览订单结算结果（不实际执行）
    
    Returns:
        包含计算结果的字典: {nav, shares, amount, message}
    """
    order = get_order_by_id(order_id)
    if order is None:
        return {'success': False, 'message': f'找不到订单: {order_id}'}
    
    product_code = order['product_code']
    nav_date = order.get('nav_date', '')
    order_type = order['order_type']
    
    # 获取净值
    nav = get_nav(product_code, nav_date)
    if nav is None:
        return {'success': False, 'message': f'缺少净值: {product_code} @ {nav_date}', 'nav': None}
    
    if order_type == 'buy_debit':
        amount = parse_decimal(order.get('amount', 0))
        fee = parse_decimal(order.get('fee', 0))
        
        # 如果手续费为空或0，尝试从产品配置重新计算
        if fee == 0:
            product_obj = get_product_by_code(product_code)
            if product_obj:
                buy_fee_rate = Decimal(str(product_obj.get('buy_fee_rate', 0)))
                if buy_fee_rate > 0:
                    fee = (amount * buy_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
        
        # 计算净申购金额（金额 - 手续费）
        net_amount = amount - fee
        
        # 计算份额：净申购金额 / 净值
        shares = (net_amount / nav).quantize(Decimal('0.000001'), rounding=ROUND_HALF_UP)
        
        return {
            'success': True,
            'nav': float(nav),
            'shares': float(shares),
            'net_amount': float(net_amount),
            'message': f'预计获得 {shares:.4f} 份（净值 {nav}）'
        }
    
    elif order_type == 'redeem_request':
        shares = parse_decimal(order.get('shares', 0))
        sell_fee_rate = Decimal(order.get('sell_fee_rate', '0') or '0')
        gross = shares * nav
        fee = (gross * sell_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
        amount = gross - fee
        
        return {
            'success': True,
            'nav': float(nav),
            'shares': float(shares),
            'amount': float(amount),
            'fee': float(fee),
            'message': f'预计到账 {amount:.2f}（扣费 {fee:.2f}）'
        }
    
    return {'success': False, 'message': f'不支持的订单类型: {order_type}'}


def validate_transactions_orders() -> ValidationResult:
    """
    校验交易和订单数据
    
    Returns:
        ValidationResult 包含错误和警告信息
    """
    errors = []
    warnings = []
    
    # 检查 transactions.csv
    transactions = load_transactions()
    order_ids_tx = {}  # {(order_id, action): row_num}
    
    for i, tx in enumerate(transactions, 1):
        action = (tx.get('action') or '').lower()
        order_id = tx.get('order_id') or ''
        
        # action 必须有效
        if action not in [a.lower() for a in VALID_ACTIONS]:
            warnings.append(f"transactions 第{i}行: action '{action}' 不常见")
        
        # buy_debit/buy_confirm/sell_confirm 必须有 order_id
        if action in ['buy_debit', 'buy_confirm', 'sell_confirm'] and not order_id:
            errors.append(f"transactions 第{i}行: {action} 缺少 order_id")
        
        # 检查重复
        if order_id:
            key = (order_id, action)
            if key in order_ids_tx:
                errors.append(f"transactions 第{i}行: order_id+action 重复 ({order_id}, {action})")
            order_ids_tx[key] = i
    
    # 检查 orders.csv
    orders = list_all_orders()
    order_ids_orders = set()
    
    for i, order in enumerate(orders, 1):
        order_id = order.get('order_id') or ''
        status = order.get('status') or ''
        
        # order_id 唯一性
        if order_id in order_ids_orders:
            errors.append(f"orders 第{i}行: order_id 重复 ({order_id})")
        order_ids_orders.add(order_id)
        
        # status 有效性
        if status not in ['pending', 'done', 'cancelled']:
            errors.append(f"orders 第{i}行: status '{status}' 无效")
    
    # 检查 buy_confirm 是否有匹配的 buy_debit
    buy_debits = {tx['order_id'] for tx in transactions 
                  if (tx.get('action') or '').lower() == 'buy_debit' and tx.get('order_id')}
    
    for tx in transactions:
        action = (tx.get('action') or '').lower()
        order_id = tx.get('order_id') or ''
        
        if action == 'buy_confirm' and order_id:
            if order_id not in buy_debits:
                if not tx.get('amount'):
                    errors.append(f"buy_confirm ({order_id}) 找不到匹配的 buy_debit")
    
    return ValidationResult(
        success=len(errors) == 0,
        errors=errors,
        warnings=warnings
    )


def get_product_options() -> List[Dict]:
    """
    获取产品选项列表（用于 UI 下拉框）
    
    Returns:
        产品列表 [{'code': '000307', 'name': '...', 'buy_fee_rate': 0.015, ...}, ...]
    """
    products = load_products()
    return [{
        'code': p['product_code'],
        'name': p['product_name'],
        'buy_fee_rate': p.get('buy_fee_rate') or 0,  # 确保 None 转换为 0
        'buy_confirm_offset': p.get('buy_confirm_offset') or 1,
        'sell_confirm_offset': p.get('sell_confirm_offset') or 1,
        'cutoff_time': p.get('cutoff_time') or '15:00',
        'market': p.get('market') or 'cn'
    } for p in products]


def calc_buy_fee(product_code: str, amount: Decimal) -> Decimal:
    """
    计算买入手续费
    
    Args:
        product_code: 产品代码
        amount: 扣款金额
    
    Returns:
        计算的手续费
    """
    product = get_product_by_code(product_code)
    if product is None:
        return Decimal('0')
    
    buy_fee_rate = Decimal(str(product.get('buy_fee_rate', 0)))
    return (amount * buy_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)


def calc_trade_dates(product_code: str, requested_at: datetime = None) -> Dict:
    """
    计算交易日期信息
    
    Args:
        product_code: 产品代码
        requested_at: 请求时间
    
    Returns:
        {trade_date, nav_date, confirm_date}
    """
    product = get_product_by_code(product_code)
    if product is None:
        return {}
    
    if requested_at is None:
        requested_at = datetime.now()
    
    cutoff_time = product.get('cutoff_time', '15:00')
    buy_confirm_offset = product.get('buy_confirm_offset', 1)
    
    trade_date = calc_trade_date(requested_at, cutoff_time)
    nav_date = trade_date
    confirm_date = calc_confirm_date(trade_date, buy_confirm_offset)
    
    return {
        'trade_date': trade_date,
        'nav_date': nav_date,
        'confirm_date': confirm_date
    }

