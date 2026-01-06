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
import logging
from datetime import datetime, date
from decimal import Decimal, ROUND_HALF_UP
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)

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
    trade_date: date = None,
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
        trade_date: 交易日期（可选，默认根据requested_at计算）
        note: 备注（默认产品名称）
        channel: 渠道（EXCHANGE/OTC），None 表示优先场外
    
    Returns:
        order_id: 生成的订单号
    
    Raises:
        ValueError: 产品不存在
    """
    from data.product_service import get_product_by_code
    product = get_product_by_code(product_code)
    if product is None:
        raise ValueError(f"产品不存在: product_code={product_code}")
    
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
    if trade_date is None:
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
    trade_date: date = None,
    redeem_account: str = None,
    redeem_from_account: str = None,
    redeem_from_accounts: Dict[str, Decimal] = None,
    redeem_fixed_amount: Decimal = None,
    redeem_supplement_accounts: List[Dict] = None,
    note: str = None,
    fee_override: Decimal = None
) -> str:
    """
    添加赎回发起
    
    Args:
        product_code: 产品代码
        shares: 赎回份额（总份额，固定不变）
        holding_days: 持有天数（用于确定赎回费率）
        requested_at: 请求时间（默认当前时间）
        trade_date: 交易日期（可选，默认根据requested_at计算）
        redeem_account: 资金到账账户（可选，默认余利宝理财金）
        redeem_from_account: 赎回来源账户（可选，主账户，新格式）
        redeem_from_accounts: 赎回来源账户字典（可选，多账户组合赎回，格式：{账户ID: 份额}，兼容旧格式）
        redeem_fixed_amount: 固定赎回金额（可选，如果提供则主账户按此金额赎回）
        redeem_supplement_accounts: 补充账户列表（可选，格式：[{'account_id': 'acc_id', 'shares': Decimal}], 新格式）
        note: 备注（默认产品名称）
        fee_override: 手续费覆盖值（可选，如果提供则使用此值，否则按费率计算）
    
    Returns:
        order_id: 生成的订单号
    
    Raises:
        ValueError: 产品不存在
    """
    from data.product_service import get_product_by_code
    product = get_product_by_code(product_code)
    if product is None:
        raise ValueError(f"产品不存在: product_code={product_code}")
    
    product_name = product['product_name']
    sell_confirm_offset = product.get('sell_confirm_offset', 1)
    cutoff_time = product.get('cutoff_time', '15:00')
    
    if requested_at is None:
        requested_at = datetime.now()
    
    # 获取赎回费率
    sell_fee_rate = Decimal(str(get_sell_fee_rate(product, holding_days)))
    
    # 计算日期
    if trade_date is None:
        trade_date = calc_trade_date(requested_at, cutoff_time)
    nav_date = trade_date
    confirm_date = calc_confirm_date(trade_date, sell_confirm_offset)
    
    # 生成订单号（包含请求时间，格式：YYYYMMDDHHMMSS_product_code_seq_R{requested_at_timestamp}）
    # 使用请求时间生成基础订单号，而不是当前时间
    requested_at_str = requested_at.strftime('%Y%m%d%H%M%S')
    prefix = f"{requested_at_str}_{product_code}_"
    
    # 查找同秒内的最大序号
    from data.data_store import load_orders, load_transactions
    max_seq = 0
    orders = load_orders()
    for order in orders:
        order_id = order.get('order_id') or ''
        if order_id.startswith(prefix):
            try:
                # 格式：YYYYMMDDHHMMSS_product_code_seq 或 YYYYMMDDHHMMSS_product_code_seq_R...
                parts = order_id.split('_')
                if len(parts) >= 3:
                    seq_str = parts[2].split('R')[0]  # 提取序号部分（去掉R后面的内容）
                    seq = int(seq_str)
                    max_seq = max(max_seq, seq)
            except (ValueError, IndexError):
                pass
    
    transactions = load_transactions()
    for tx in transactions:
        order_id = tx.get('order_id') or ''
        if order_id.startswith(prefix):
            try:
                parts = order_id.split('_')
                if len(parts) >= 3:
                    seq_str = parts[2].split('R')[0]
                    seq = int(seq_str)
                    max_seq = max(max_seq, seq)
            except (ValueError, IndexError):
                pass
    
    new_seq = max_seq + 1
    order_id = f"{prefix}{new_seq:03d}R{requested_at_str}"
    
    if note is None:
        note = product_name
    
    # 将赎回账户信息存储在note中
    # 新格式：原备注|redeem_account:资金到账账户ID|redeem_from_account:主账户ID|redeem_fixed_amount:固定金额|redeem_supplement_accounts:账户1:份额1|账户2:份额2
    # 旧格式（兼容）：原备注|redeem_account:资金到账账户ID|redeem_from_accounts:账户1:份额1|账户2:份额2
    note_with_account = note
    if redeem_account:
        note_with_account = f"{note_with_account}|redeem_account:{redeem_account}"
    
    # 新格式：主账户 + 固定金额 + 补充账户
    if redeem_from_account:
        note_with_account = f"{note_with_account}|redeem_from_account:{redeem_from_account}"
        
        if redeem_fixed_amount is not None and redeem_fixed_amount > 0:
            note_with_account = f"{note_with_account}|redeem_fixed_amount:{format_decimal(redeem_fixed_amount, 2)}"
        
        if redeem_supplement_accounts:
            supp_list = [f"{supp['account_id']}:{format_decimal(supp['shares'], 6)}" for supp in redeem_supplement_accounts]
            supp_str = '|'.join(supp_list)
            note_with_account = f"{note_with_account}|redeem_supplement_accounts:{supp_str}"
    # 兼容旧格式：多账户组合赎回
    elif redeem_from_accounts:
        account_shares_list = [f"{acc_id}:{shares}" for acc_id, shares in redeem_from_accounts.items()]
        account_shares_str = '|'.join(account_shares_list)
        note_with_account = f"{note_with_account}|redeem_from_accounts:{account_shares_str}"
    
    # 如果提供了手续费覆盖值，将其存储在note中（格式：...|fee_override:手续费金额）
    if fee_override is not None and fee_override > 0:
        fee_str = format_decimal(fee_override, 2)
        note_with_account = f"{note_with_account}|fee_override:{fee_str}"
        logger.info(f"add_redeem_request: 设置手续费覆盖值: {fee_override} (格式化后: {fee_str}), order_id={order_id}")
    
    # 写入 orders
    order_record = {
        'order_id': order_id,
        'product_id': product.get('id'),  # 添加 product_id
        'product_code': product_code,
        'order_type': 'redeem_request',
        'amount': '',
        'fee': format_decimal(fee_override, 2) if fee_override is not None and fee_override > 0 else '',
        'shares': format_decimal(shares, 4),
        'requested_at': requested_at.strftime('%Y-%m-%d %H:%M:%S'),
        'trade_date': str(trade_date),
        'nav_date': str(nav_date),
        'confirm_date': str(confirm_date),
        'holding_days': str(holding_days),
        'sell_fee_rate': str(sell_fee_rate),
        'status': 'pending',
        'note': note_with_account
    }
    append_order(order_record)
    
    # 同时写入 transactions（redeem_request），以便在理财记录中可查询
    from data.data_store import append_transaction
    tx_record = {
        'date': str(trade_date),
        'product_id': product.get('id'),
        'product_code': product_code,
        'action': 'redeem_request',
        'amount': '',
        'shares': format_decimal(shares, 4),
        'fee': '',
        'nav': '',
        'nav_date': str(nav_date),
        'order_id': order_id,
        'note': note,
        'created_at': requested_at.strftime('%Y-%m-%d %H:%M:%S')
    }
    append_transaction(tx_record)
    
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
    product = get_product_by_code(product_code)
    if product is None:
        raise ValueError(f"产品不存在: product_code={product_code}")
    
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
        
        # 获取净值（赎回订单使用交易日期nav_date的净值）
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
                
                # 获取 product_id（使用之前已获取的 product）
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
                
                # 解析 account_amounts 并分配份额到各子账户
                order_note = order.get('note', '')
                account_amounts = {}
                if '|account_amounts:' in order_note:
                    try:
                        accounts_str = order_note.split('|account_amounts:')[1].split('|')[0].strip()
                        # 格式：acc1:amount1|acc2:amount2
                        account_pairs = accounts_str.split('|')
                        for pair in account_pairs:
                            if ':' in pair:
                                acc_id, amount_str = pair.split(':', 1)
                                account_amounts[acc_id.strip()] = parse_decimal(amount_str)
                        logger.info(f"解析到子账户购买金额: {account_amounts}, order_id={order_id}")
                    except Exception as e:
                        logger.warning(f"解析 account_amounts 失败: {e}, order_id={order_id}, note={order_note}")
                
                # 如果有子账户分配，为每个子账户增加余额和份额
                if account_amounts:
                    from core.ledger_service import add_income
                    from data.account_service import update_account_shares
                    
                    total_amount = sum(account_amounts.values())
                    for acc_id, acc_amount in account_amounts.items():
                        # 按比例分摊手续费
                        acc_fee = (acc_amount / total_amount * fee).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
                        acc_net = acc_amount - acc_fee
                        # 计算该子账户的份额
                        acc_shares = (acc_net / nav).quantize(Decimal('0.000001'), rounding=ROUND_HALF_UP)
                        
                        # 在 ledger 中增加子账户余额
                        add_income(
                            account_to=acc_id,
                            amount=acc_net,
                            category_l1="理财投资",
                            category_l2="买入确认",
                            event_time=created_at,
                            note=f"{product.get('product_name', product_code)} 买入确认 (订单号: {order_id}, 份额: {acc_shares:.6f}, 净值: {nav})"
                        )
                        
                        # 更新子账户份额
                        update_account_shares(acc_id, acc_shares, 'increase')
                        logger.info(f"子账户 {acc_id} 增加份额: {acc_shares:.6f}, 金额: {acc_net:.2f}, order_id={order_id}")
                
                # 扣减等待池（优先扣减等待池，不足部分从现金扣除）
                # 注意：这里使用amount（含费前），因为等待池存储的是含费前的金额
                if product_id:
                    try:
                        from core.pending_buy_service import reduce_pending_amount_by_transaction
                        # 获取交易ID（从transactions表查询）
                        from data.data_store import load_transactions
                        transactions = load_transactions()
                        tx_id = None
                        for tx in transactions:
                            if tx.get('order_id') == order_id and tx.get('action') == 'buy_confirm':
                                # 尝试从数据库获取ID（如果使用数据库存储）
                                tx_id = tx.get('id')
                                break
                        reduce_pending_amount_by_transaction(product_id, amount, tx_id)
                        logger.info(f"买入确认时扣减等待池: product_id={product_id}, amount={amount}, order_id={order_id}")
                    except Exception as e:
                        logger.warning(f"扣减等待池失败（不影响结算）: {e}", exc_info=True)
                
            elif order_type == 'redeem_request':
                # 赎回确认
                shares = parse_decimal(order.get('shares', 0))
                sell_fee_rate_str = order.get('sell_fee_rate', '0')
                sell_fee_rate = Decimal(str(sell_fee_rate_str)) if sell_fee_rate_str else Decimal('0')
                
                # 计算到账金额
                gross = shares * nav
                
                # 检查是否有手续费覆盖值（从订单的note中解析，或从fee字段读取）
                fee_override = None
                order_note = order.get('note', '')
                if '|fee_override:' in order_note:
                    try:
                        fee_override_str = order_note.split('|fee_override:')[1].split('|')[0].strip()
                        if fee_override_str:  # 确保不是空字符串
                            fee_override = parse_decimal(fee_override_str)
                            logger.info(f"settle_orders: 从note中解析到手续费覆盖值: {fee_override}, order_id={order_id}")
                    except Exception as e:
                        logger.warning(f"settle_orders: 解析note中的手续费覆盖值失败: {e}, order_id={order_id}")
                
                # 如果订单的fee字段有值，也尝试使用（优先级低于note中的值）
                if fee_override is None or fee_override == 0:
                    order_fee_str = order.get('fee', '')
                    if order_fee_str and order_fee_str.strip():
                        try:
                            fee_override = parse_decimal(order_fee_str)
                            if fee_override > 0:
                                logger.info(f"settle_orders: 从订单fee字段读取到手续费覆盖值: {fee_override}, order_id={order_id}")
                        except Exception as e:
                            logger.warning(f"settle_orders: 解析订单fee字段失败: {e}, order_id={order_id}")
                
                # 如果提供了手续费覆盖值，使用它；否则按费率计算
                if fee_override is not None and fee_override > 0:
                    fee = fee_override
                else:
                    fee = (gross * sell_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
                
                amount = gross - fee
                
                # 获取 product_id（使用之前已获取的 product）
                product_id = product.get('id') if product else None
                
                # 获取赎回账户（从订单的note中解析）
                # 格式：原备注|redeem_account:资金到账账户ID|redeem_from_accounts:账户1:份额1|账户2:份额2
                # 或：原备注|redeem_account:资金到账账户ID|redeem_from_account:赎回来源账户ID（兼容旧格式）
                redeem_account = None  # 资金到账账户
                redeem_from_account = None  # 单个账户赎回（兼容旧格式）
                redeem_from_accounts = {}  # 多账户组合赎回 {账户ID: 份额}
                order_note = order.get('note', '')
                
                if '|redeem_account:' in order_note:
                    try:
                        # 解析格式：...|redeem_account:账户代码|... 或 ...|redeem_account:账户代码
                        # 需要确保只取账户代码，不包含后续的 |fee_override: 等
                        parts = order_note.split('|redeem_account:')
                        if len(parts) > 1:
                            # 取 |redeem_account: 后面的部分
                            redeem_account_part = parts[1]
                            # 按 | 分割，取第一部分（账户代码）
                            redeem_account = redeem_account_part.split('|')[0].strip()
                            # 如果账户代码中包含 fee_override（说明解析错误），需要进一步处理
                            if 'fee_override' in redeem_account or ':' in redeem_account:
                                # 这种情况不应该发生，但为了兼容旧数据，尝试修复
                                redeem_account = redeem_account.split(':')[0].strip()
                    except Exception as e:
                        logger.warning(f"解析redeem_account失败: {e}, order_id={order_id}, note={order_note}")
                        redeem_account = None
                
                # 解析新的赎回格式：支持固定金额和补充账户
                # 格式：|redeem_from_account:主账户ID|redeem_fixed_amount:固定金额|redeem_supplement_accounts:补充账户1:份额1|补充账户2:份额2
                main_account = None
                fixed_amount = None
                supplement_accounts_list = []  # [{'account_id': 'acc_id', 'shares': Decimal}]
                
                if '|redeem_from_account:' in order_note:
                    try:
                        main_account = order_note.split('|redeem_from_account:')[1].split('|')[0].strip()
                    except Exception as e:
                        logger.warning(f"解析主账户失败: {e}, order_id={order_id}")
                
                if '|redeem_fixed_amount:' in order_note:
                    try:
                        fixed_amount_str = order_note.split('|redeem_fixed_amount:')[1].split('|')[0].strip()
                        if fixed_amount_str:
                            fixed_amount = parse_decimal(fixed_amount_str)
                    except Exception as e:
                        logger.warning(f"解析固定金额失败: {e}, order_id={order_id}")
                
                if '|redeem_supplement_accounts:' in order_note:
                    try:
                        accounts_str = order_note.split('|redeem_supplement_accounts:')[1].split('|')[0].strip()
                        # 格式：账户1:份额1|账户2:份额2
                        account_pairs = accounts_str.split('|')
                        for pair in account_pairs:
                            if ':' in pair:
                                acc_id, shares_str = pair.split(':', 1)
                                supplement_accounts_list.append({
                                    'account_id': acc_id.strip(),
                                    'shares': parse_decimal(shares_str)
                                })
                    except Exception as e:
                        logger.warning(f"解析补充账户失败: {e}, order_id={order_id}")
                
                # 兼容旧格式：多账户组合赎回
                if '|redeem_from_accounts:' in order_note and not main_account:
                    try:
                        accounts_str = order_note.split('|redeem_from_accounts:')[1].split('|')[0].strip()
                        account_pairs = accounts_str.split('|')
                        for pair in account_pairs:
                            if ':' in pair:
                                acc_id, shares_str = pair.split(':', 1)
                                redeem_from_accounts[acc_id.strip()] = parse_decimal(shares_str)
                    except Exception as e:
                        logger.warning(f"解析多账户组合赎回信息失败: {e}, order_id={order_id}, note={order_note}")
                elif '|redeem_from_account:' in order_note and not main_account:
                    # 兼容旧格式：单个账户赎回
                    try:
                        redeem_from_account = order_note.split('|redeem_from_account:')[1].split('|')[0].strip()
                    except:
                        pass
                
                # 如果没有找到资金到账账户，使用默认账户（余利宝理财金）
                if not redeem_account:
                    redeem_account = 'ylb_finance'
                
                # 写入 sell_confirm
                # 使用订单的确认日期，时间默认 12:00:00
                confirm_date_str = order.get('confirm_date', target_date)
                created_at = f"{confirm_date_str} 12:00:00"
                
                # 清理 note，移除账户信息
                clean_note = order_note
                if '|redeem_account:' in clean_note:
                    clean_note = clean_note.split('|redeem_account:')[0]
                if '|redeem_from_account:' in clean_note:
                    clean_note = clean_note.split('|redeem_from_account:')[0]
                if '|redeem_fixed_amount:' in clean_note:
                    clean_note = clean_note.split('|redeem_fixed_amount:')[0]
                if '|redeem_supplement_accounts:' in clean_note:
                    clean_note = clean_note.split('|redeem_supplement_accounts:')[0]
                if '|redeem_from_accounts:' in clean_note:
                    clean_note = clean_note.split('|redeem_from_accounts:')[0]
                if '|fee_override:' in clean_note:
                    clean_note = clean_note.split('|fee_override:')[0]
                
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
                    'note': clean_note,
                    'created_at': created_at
                }
                append_transaction(tx_record)
                
                # 添加记账记录（资金到账账户金额增加）
                from core.ledger_service import add_income, add_expense
                from data.account_service import get_account_shares, update_account_shares
                
                add_income(
                    account_to=redeem_account,
                    amount=amount,
                    category_l1="理财投资",
                    category_l2="赎回确认",
                    event_time=created_at,
                    note=f"{product.get('product_name', product_code)} 赎回 (订单号: {order_id})"
                )
                
                # 处理新的赎回格式：固定金额 + 补充账户
                if main_account:
                    # 计算主账户需要的份额
                    if fixed_amount:
                        main_shares_needed = (fixed_amount / nav).quantize(Decimal('0.000001'), rounding=ROUND_HALF_UP)
                    else:
                        # 如果没有固定金额，主账户承担总份额减去补充账户份额
                        supplement_total_shares = sum(supp['shares'] for supp in supplement_accounts_list)
                        main_shares_needed = shares - supplement_total_shares
                    
                    # 获取主账户当前份额
                    main_account_shares = get_account_shares(main_account)
                    
                    # 如果主账户份额不足，全部清空，不足部分由补充账户承担
                    if main_account_shares < main_shares_needed:
                        actual_main_shares = main_account_shares
                        remaining_shares = main_shares_needed - actual_main_shares
                        
                        # 将剩余份额分配给补充账户（如果补充账户份额不足，按比例分配）
                        if supplement_accounts_list:
                            # 如果补充账户已经指定了份额，检查是否足够
                            supplement_total = sum(supp['shares'] for supp in supplement_accounts_list)
                            if supplement_total < remaining_shares:
                                # 补充账户份额不足，按比例增加
                                for supp in supplement_accounts_list:
                                    supp['shares'] = (supp['shares'] / supplement_total * remaining_shares).quantize(Decimal('0.000001'), rounding=ROUND_HALF_UP)
                        else:
                            # 如果没有指定补充账户份额，将剩余份额分配给第一个补充账户（这种情况不应该发生，但为了安全）
                            logger.warning(f"主账户份额不足但未指定补充账户份额，order_id={order_id}")
                    else:
                        actual_main_shares = main_shares_needed
                    
                    # 记账：主账户
                    if fixed_amount:
                        # 固定金额，按固定金额记账
                        main_amount = fixed_amount
                    else:
                        # 非固定金额，按实际份额×净值记账
                        main_amount = actual_main_shares * nav
                    
                    add_expense(
                        account_from=main_account,
                        amount=main_amount.quantize(Decimal('0.01'), rounding=ROUND_HALF_UP),
                        category_l1="理财投资",
                        category_l2="赎回持仓减少",
                        event_time=created_at,
                        note=f"{product.get('product_name', product_code)} 赎回持仓减少 (订单号: {order_id}, 份额: {actual_main_shares:.6f}, 净值: {nav})"
                    )
                    
                    # 更新主账户份额
                    update_account_shares(main_account, actual_main_shares, 'decrease')
                    
                    # 记账：补充账户
                    for supp in supplement_accounts_list:
                        supp_acc_id = supp['account_id']
                        supp_shares = supp['shares']
                        supp_amount = (supp_shares * nav).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
                        add_expense(
                            account_from=supp_acc_id,
                            amount=supp_amount,
                            category_l1="理财投资",
                            category_l2="赎回持仓减少",
                            event_time=created_at,
                            note=f"{product.get('product_name', product_code)} 赎回持仓减少 (订单号: {order_id}, 份额: {supp_shares:.6f}, 净值: {nav})"
                        )
                        
                        # 更新补充账户份额
                        update_account_shares(supp_acc_id, supp_shares, 'decrease')
                
                # 兼容旧格式：多账户组合赎回
                elif redeem_from_accounts:
                    # 多账户组合赎回：为每个账户分别记录份额减少
                    for acc_id, acc_shares in redeem_from_accounts.items():
                        # 该账户的份额减少金额 = 该账户份额 × 净值
                        acc_gross = acc_shares * nav
                        add_expense(
                            account_from=acc_id,
                            amount=acc_gross.quantize(Decimal('0.01'), rounding=ROUND_HALF_UP),
                            category_l1="理财投资",
                            category_l2="赎回持仓减少",
                            event_time=created_at,
                            note=f"{product.get('product_name', product_code)} 赎回持仓减少 (订单号: {order_id}, 份额: {acc_shares:.4f}, 净值: {nav})"
                        )
                        
                        # 更新账户份额
                        update_account_shares(acc_id, acc_shares, 'decrease')
                else:
                    # 单个账户赎回（兼容旧格式）
                    if not redeem_from_account:
                        redeem_from_account = redeem_account
                    
                    add_expense(
                        account_from=redeem_from_account,
                        amount=gross.quantize(Decimal('0.01'), rounding=ROUND_HALF_UP),
                        category_l1="理财投资",
                        category_l2="赎回持仓减少",
                        event_time=created_at,
                        note=f"{product.get('product_name', product_code)} 赎回持仓减少 (订单号: {order_id}, 份额: {shares:.4f}, 净值: {nav})"
                    )
                    
                    # 更新账户份额
                    update_account_shares(redeem_from_account, shares, 'decrease')
            
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
    
    # 获取净值（赎回订单使用交易日期nav_date的净值）
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
                if product:
                    buy_fee_rate = Decimal(str(product.get('buy_fee_rate', 0)))
                    if buy_fee_rate > 0:
                        fee = (amount * buy_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
            
            # 计算净申购金额（金额 - 手续费）
            net_amount = amount - fee
            
            # 计算份额：净申购金额 / 净值（保持6位小数精度，与数据库字段匹配）
            shares = (net_amount / nav).quantize(Decimal('0.000001'), rounding=ROUND_HALF_UP)
            
            # 获取 product_id（使用之前已获取的 product）
            product_id = product.get('id') if product else None
            
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
            
            # 扣减等待池（优先扣减等待池，不足部分从现金扣除）
            # 注意：这里使用amount（含费前），因为等待池存储的是含费前的金额
            if product_id:
                try:
                    from core.pending_buy_service import reduce_pending_amount_by_transaction
                    # 获取交易ID（从transactions表查询）
                    from data.data_store import load_transactions
                    transactions = load_transactions()
                    tx_id = None
                    for tx in transactions:
                        if tx.get('order_id') == order_id and tx.get('action') == 'buy_confirm':
                            # 尝试从数据库获取ID（如果使用数据库存储）
                            tx_id = tx.get('id')
                            break
                    reduce_pending_amount_by_transaction(product_id, amount, tx_id)
                    logger.info(f"买入确认时扣减等待池: product_id={product_id}, amount={amount}, order_id={order_id}")
                except Exception as e:
                    logger.warning(f"扣减等待池失败（不影响结算）: {e}", exc_info=True)
            
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
            
            # 检查是否有手续费覆盖值（从订单的note中解析，或从fee字段读取）
            fee_override = None
            order_note = order.get('note', '')
            if '|fee_override:' in order_note:
                try:
                    fee_override_str = order_note.split('|fee_override:')[1].split('|')[0].strip()
                    if fee_override_str:  # 确保不是空字符串
                        fee_override = parse_decimal(fee_override_str)
                        logger.info(f"settle_single_order: 从note中解析到手续费覆盖值: {fee_override}, order_id={order_id}")
                except Exception as e:
                    logger.warning(f"settle_single_order: 解析note中的手续费覆盖值失败: {e}, order_id={order_id}")
            
            # 如果订单的fee字段有值，也尝试使用（优先级低于note中的值）
            if fee_override is None or fee_override == 0:
                order_fee_str = order.get('fee', '')
                if order_fee_str and order_fee_str.strip():
                    try:
                        fee_override = parse_decimal(order_fee_str)
                        if fee_override > 0:
                            logger.info(f"settle_single_order: 从订单fee字段读取到手续费覆盖值: {fee_override}, order_id={order_id}")
                    except Exception as e:
                        logger.warning(f"settle_single_order: 解析订单fee字段失败: {e}, order_id={order_id}")
            
            # 如果提供了手续费覆盖值，使用它；否则按费率计算
            if fee_override is not None and fee_override > 0:
                fee = fee_override
                logger.info(f"settle_single_order: 使用手续费覆盖值: {fee}, order_id={order_id}")
            else:
                fee = (gross * sell_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
                logger.info(f"settle_single_order: 按费率计算手续费: {fee} (费率={sell_fee_rate}, 总金额={gross}), order_id={order_id}")
            
            amount = gross - fee
            
            # 获取 product_id（使用之前已获取的 product）
            product_id = product.get('id') if product else None
            
            # 获取赎回账户（从订单的note中解析）
            # 格式：原备注|redeem_account:资金到账账户ID|redeem_from_accounts:账户1:份额1|账户2:份额2
            # 或：原备注|redeem_account:资金到账账户ID|redeem_from_account:赎回来源账户ID（兼容旧格式）
            redeem_account = None  # 资金到账账户
            redeem_from_account = None  # 单个账户赎回（兼容旧格式）
            redeem_from_accounts = {}  # 多账户组合赎回 {账户ID: 份额}
            order_note = order.get('note', '')
            
            if '|redeem_account:' in order_note:
                try:
                    # 解析格式：...|redeem_account:账户代码|... 或 ...|redeem_account:账户代码
                    # 需要确保只取账户代码，不包含后续的 |fee_override: 等
                    parts = order_note.split('|redeem_account:')
                    if len(parts) > 1:
                        # 取 |redeem_account: 后面的部分
                        redeem_account_part = parts[1]
                        # 按 | 分割，取第一部分（账户代码）
                        redeem_account = redeem_account_part.split('|')[0].strip()
                        # 如果账户代码中包含 fee_override（说明解析错误），需要进一步处理
                        if 'fee_override' in redeem_account or ':' in redeem_account:
                            # 这种情况不应该发生，但为了兼容旧数据，尝试修复
                            redeem_account = redeem_account.split(':')[0].strip()
                except Exception as e:
                    logger.warning(f"解析redeem_account失败: {e}, order_id={order_id}, note={order_note}")
                    redeem_account = None
            
            # 优先解析多账户组合赎回
            if '|redeem_from_accounts:' in order_note:
                try:
                    accounts_str = order_note.split('|redeem_from_accounts:')[1].split('|')[0].strip()
                    # 格式：账户1:份额1|账户2:份额2
                    account_pairs = accounts_str.split('|')
                    for pair in account_pairs:
                        if ':' in pair:
                            acc_id, shares_str = pair.split(':', 1)
                            redeem_from_accounts[acc_id.strip()] = parse_decimal(shares_str)
                except Exception as e:
                    logger.warning(f"解析多账户组合赎回信息失败: {e}, order_id={order_id}, note={order_note}")
            elif '|redeem_from_account:' in order_note:
                # 兼容旧格式：单个账户赎回
                try:
                    redeem_from_account = order_note.split('|redeem_from_account:')[1].split('|')[0].strip()
                except:
                    pass
            
            # 如果没有找到资金到账账户，使用默认账户（余利宝理财金）
            if not redeem_account:
                redeem_account = 'ylb_finance'
            
            # 如果没有找到赎回来源账户，使用资金到账账户（兼容旧数据）
            if not redeem_from_accounts and not redeem_from_account:
                redeem_from_account = redeem_account
            
            # 写入 sell_confirm
            # 清理 note，移除账户信息
            clean_note = order_note
            if '|redeem_account:' in clean_note:
                clean_note = clean_note.split('|redeem_account:')[0]
            if '|redeem_from_accounts:' in clean_note:
                clean_note = clean_note.split('|redeem_from_accounts:')[0]
            if '|redeem_from_account:' in clean_note:
                clean_note = clean_note.split('|redeem_from_account:')[0]
            if '|fee_override:' in clean_note:
                clean_note = clean_note.split('|fee_override:')[0]
            
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
                'note': clean_note,
                'created_at': created_at
            }
            logger.info(f"settle_single_order: 准备写入赎回确认记录 order_id={order_id}, product_code={product_code}, product_id={product_id}, shares={shares}, amount={amount}")
            append_transaction(tx_record)
            logger.info(f"settle_single_order: 成功写入赎回确认记录 order_id={order_id}")
            
            # 添加记账记录（资金到账账户金额增加）
            from core.ledger_service import add_income, add_expense
            add_income(
                account_to=redeem_account,
                amount=amount,
                category_l1="理财投资",
                category_l2="赎回确认",
                event_time=created_at,
                note=f"{product.get('product_name', product_code)} 赎回 (订单号: {order_id})"
            )
            logger.info(f"settle_single_order: 成功添加记账记录（金额增加），账户={redeem_account}，金额={amount}")
            
            # 添加记账记录（赎回来源账户份额减少，金额减少）
            if redeem_from_accounts:
                # 多账户组合赎回：为每个账户分别记录份额减少
                for acc_id, acc_shares in redeem_from_accounts.items():
                    # 该账户的份额减少金额 = 该账户份额 × 净值
                    acc_gross = acc_shares * nav
                    add_expense(
                        account_from=acc_id,
                        amount=acc_gross,
                        category_l1="理财投资",
                        category_l2="赎回持仓减少",
                        event_time=created_at,
                        note=f"{product.get('product_name', product_code)} 赎回持仓减少 (订单号: {order_id}, 份额: {acc_shares:.4f}, 净值: {nav})"
                    )
                    logger.info(f"settle_single_order: 成功添加记账记录（份额减少），账户={acc_id}，份额={acc_shares:.4f}，金额={acc_gross}")
            else:
                # 单个账户赎回（兼容旧格式）
                add_expense(
                    account_from=redeem_from_account,
                    amount=gross,
                    category_l1="理财投资",
                    category_l2="赎回持仓减少",
                    event_time=created_at,
                    note=f"{product.get('product_name', product_code)} 赎回持仓减少 (订单号: {order_id}, 份额: {shares:.4f}, 净值: {nav})"
                )
                logger.info(f"settle_single_order: 成功添加记账记录（份额减少），账户={redeem_from_account}，金额={gross}")
            
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
    
    # 获取净值（赎回订单使用交易日期nav_date的净值）
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
        
        # 赎回订单使用交易日期（nav_date）的净值计算金额
        gross = shares * nav
        
        # 检查是否有手续费覆盖值（从订单的note中解析，或从fee字段读取）
        fee_override = None
        order_note = order.get('note', '')
        if '|fee_override:' in order_note:
            try:
                # 解析格式：...|fee_override:0.99|... 或 ...|fee_override:0.99
                parts = order_note.split('|fee_override:')
                if len(parts) > 1:
                    fee_override_str = parts[1].split('|')[0].strip()
                    if fee_override_str:  # 确保不是空字符串
                        fee_override = parse_decimal(fee_override_str)
                        logger.debug(f"preview_settle: 从note中解析到手续费覆盖值: {fee_override}, order_id={order_id}, note={order_note}")
            except Exception as e:
                logger.warning(f"preview_settle: 解析note中的手续费覆盖值失败: {e}, order_id={order_id}, note={order_note}")
        
        # 如果订单的fee字段有值，也尝试使用（优先级低于note中的值）
        if fee_override is None or fee_override == 0:
            order_fee_str = order.get('fee', '')
            if order_fee_str and order_fee_str.strip():
                try:
                    fee_override = parse_decimal(order_fee_str)
                    if fee_override > 0:
                        logger.debug(f"preview_settle: 从订单fee字段读取到手续费覆盖值: {fee_override}, order_id={order_id}")
                except Exception as e:
                    logger.warning(f"preview_settle: 解析订单fee字段失败: {e}, order_id={order_id}")
        
        # 如果提供了手续费覆盖值，使用它；否则按费率计算
        if fee_override is not None and fee_override > 0:
            fee = fee_override
            logger.debug(f"preview_settle: 使用手续费覆盖值: {fee}, order_id={order_id}")
        else:
            fee = (gross * sell_fee_rate).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
            logger.debug(f"preview_settle: 按费率计算手续费: {fee} (费率={sell_fee_rate}, 总金额={gross}), order_id={order_id}")
        
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

