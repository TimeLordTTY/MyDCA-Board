# -*- coding: utf-8 -*-
"""
场内成交录入服务

提供场内ETF/LOF手动录入成交的完整闭环：
- 验证输入
- 保存成交记录
- 扣减等待池/现金池
- 刷新持仓
- 更新快照
- 刷新建议
"""
import logging
from datetime import datetime, date
from decimal import Decimal
from typing import Dict, Optional, Tuple, Any
from dataclasses import dataclass

from data.db_connector import execute_insert, execute_query, execute_one
from data.product_service import get_product_by_id
from data.account_service import get_account_by_id
from core.exchange_holdings_calculator import calculate_exchange_holdings as calc_exchange_holdings
from core.pending_buy_service import reduce_pending_amount, get_pending_pool
from core.ledger_service import calc_account_balance
from utils.trade_calendar import is_trade_day

logger = logging.getLogger(__name__)


@dataclass
class TradeValidationResult:
    """交易验证结果"""
    is_valid: bool
    errors: list
    warnings: list


@dataclass
class TradeDeductionResult:
    """资金扣减结果"""
    wait_pool_deducted: Decimal
    cash_pool_deducted: Decimal
    total_deducted: Decimal
    wait_pool_before: Decimal
    wait_pool_after: Decimal
    cash_pool_before: Decimal
    cash_pool_after: Decimal


def validate_trade_input(
    product_id: int,
    account_id,  # 可以是 int (数据库主键id) 或 str (account_code)
    trade_type: str,
    amount: Decimal,
    shares: Decimal,
    price: Optional[Decimal] = None,
    fee: Optional[Decimal] = None,
    trade_date: Optional[date] = None
) -> TradeValidationResult:
    """
    验证交易输入
    
    Args:
        product_id: 产品ID
        account_id: 账户ID（可以是整数类型的数据库主键id，或字符串类型的account_code）
        trade_type: 交易类型（BUY/SELL/FEE/TAX）
        amount: 成交金额
        shares: 成交份额
        price: 成交价（可选）
        fee: 手续费（可选）
        trade_date: 成交日期（可选）
    
    Returns:
        TradeValidationResult
    """
    errors = []
    warnings = []
    
    # 1. 验证产品
    product = get_product_by_id(product_id)
    if not product:
        errors.append(f"产品不存在: product_id={product_id}")
    elif product.get('channel') != 'EXCHANGE':
        errors.append(f"产品不是场内产品: {product.get('code')}")
    
    # 2. 验证账户（支持 account_id 整数或 account_code 字符串）
    account = None
    if isinstance(account_id, int):
        # 如果是整数，按数据库主键id查找
        account = get_account_by_id(account_id)
    elif isinstance(account_id, str):
        # 如果是字符串，按 account_code 查找
        from data.account_service import get_account_by_code
        account = get_account_by_code(account_id)
    else:
        errors.append(f"账户ID类型无效: account_id={account_id} (类型: {type(account_id).__name__})")
    
    if not account:
        errors.append(f"账户不存在: account_id={account_id}")
    
    # 3. 验证交易类型
    valid_types = ['BUY', 'SELL', 'DIV_CASH', 'DIV_REINV', 'FEE', 'TAX']
    if trade_type not in valid_types:
        errors.append(f"无效的交易类型: {trade_type}，必须是 {valid_types} 之一")
    
    # 4. 验证金额和份额
    if amount <= 0:
        errors.append("成交金额必须大于0")
    
    if shares <= 0:
        errors.append("成交份额必须大于0")
    
    # 5. 验证价格（如果提供）
    if price is not None and price <= 0:
        errors.append("成交价必须大于0")
    
    # 6. 验证手续费（如果提供）
    if fee is not None and fee < 0:
        errors.append("手续费不能为负")
    
    # 7. SELL交易：检查持仓是否足够（卖出时检查）
    if trade_type == 'SELL':
        holdings = calc_exchange_holdings(product_id)
        current_qty = holdings.get('current_qty', Decimal('0'))
        if shares > current_qty:
            errors.append(f"卖出份额({shares})超过当前持仓({current_qty})")
    
    # 8. BUY交易：检查现金是否足够（买入时检查，但实际扣减在apply_fund_deduction中）
    if trade_type == 'BUY':
        # 这里只做警告，实际扣减在apply_fund_deduction中处理
        account_code = account.get('account_code') if account else None
        if account_code:
                try:
                    cash_balance = calc_account_balance(account_code)
                    wait_pool = get_pending_pool(product_id, account_id)
                    wait_pool_sum = Decimal(str(wait_pool['pending_amount'])) if wait_pool else Decimal('0')
                    total_available = cash_balance + wait_pool_sum
                    if amount > total_available:
                        warnings.append(f"成交金额({amount})可能超过可用资金(现金:{cash_balance} + 等待池:{wait_pool_sum} = {total_available})")
                except Exception as e:
                    warnings.append(f"无法计算可用资金: {e}")
    
    # 9. 价格与金额/份额的一致性检查
    if price is not None:
        expected_amount = price * shares
        if abs(amount - expected_amount) > Decimal('0.01'):
            warnings.append(f"成交金额({amount})与价格×份额({expected_amount})不一致")
    
    is_valid = len(errors) == 0
    
    return TradeValidationResult(
        is_valid=is_valid,
        errors=errors,
        warnings=warnings
    )


def calc_default_fee(amount: Decimal, fee_rate: Decimal = Decimal('0.000845'), fee_min: Decimal = Decimal('0.20')) -> Decimal:
    """
    计算默认手续费
    
    Args:
        amount: 成交金额
        fee_rate: 手续费率（默认万0.845）
        fee_min: 最低手续费（默认0.20）
    
    Returns:
        手续费金额
    """
    fee = amount * fee_rate
    return max(fee, fee_min)


def _get_account_by_id_or_code(account_id) -> Optional[Dict]:
    """
    根据 account_id（可以是整数或字符串）获取账户
    
    Args:
        account_id: 账户ID（可以是整数类型的数据库主键id，或字符串类型的account_code）
    
    Returns:
        账户字典，如果不存在则返回 None
    """
    if isinstance(account_id, int):
        return get_account_by_id(account_id)
    elif isinstance(account_id, str):
        from data.account_service import get_account_by_code
        return get_account_by_code(account_id)
    return None


def apply_fund_deduction(
    product_id: int,
    account_id,  # 可以是 int (数据库主键id) 或 str (account_code)
    amount: Decimal,
    trade_type: str,
    order_id: Optional[str] = None,  # 新增：订单号，用于关联记账记录
    event_time: Optional[str] = None  # 新增：记账时间，如果不提供则使用当前时间
) -> TradeDeductionResult:
    """
    应用资金扣减（等待池优先，不足部分从现金池扣除）
    
    Args:
        product_id: 产品ID
        account_id: 账户ID（可以是整数类型的数据库主键id，或字符串类型的account_code）
        amount: 成交金额（含费）
        trade_type: 交易类型（BUY/SELL）
    
    Returns:
        TradeDeductionResult
    """
    wait_pool_deducted = Decimal('0')
    cash_pool_deducted = Decimal('0')
    
    if trade_type == 'BUY':
        # 买入：先扣等待池，再扣现金池
        # 将 account_id（可能是 account_code 字符串）转换为数据库主键 id
        account_db_id = account_id
        if isinstance(account_id, str):
            account = _get_account_by_id_or_code(account_id)
            if account:
                account_db_id = account.get('id')
            else:
                logger.warning(f"无法找到账户: account_id={account_id}，等待池扣减将跳过")
                account_db_id = None
        
        # 获取等待池余额
        wait_pool = None
        wait_pool_before = Decimal('0')
        if account_db_id is not None:
            wait_pool = get_pending_pool(product_id, account_db_id)
            wait_pool_before = Decimal(str(wait_pool['pending_amount'])) if wait_pool else Decimal('0')
        
        # 先扣等待池
        wait_pool_deducted = min(amount, wait_pool_before)
        if wait_pool_deducted > 0 and account_db_id is not None:
            # 使用 reduce_pending_amount 直接扣减指定账户的等待池
            from core.pending_buy_service import reduce_pending_amount
            reduce_pending_amount(product_id, account_db_id, wait_pool_deducted, reason="场内买入成交扣减")
            wait_pool_after = wait_pool_before - wait_pool_deducted
        else:
            wait_pool_after = wait_pool_before
        
        # 剩余部分从现金池扣除（通过ledger记录）
        remaining = amount - wait_pool_deducted
        
        # 获取账户代码
        account = _get_account_by_id_or_code(account_id)
        if account:
            account_code = account.get('account_code')
            cash_pool_before = calc_account_balance(account_code)
            
            # 无论是否有现金扣减，都要创建记账记录（买入确认必须记录）
            # 使用"买入确认"分类，与场外交易保持一致
            from core.ledger_service import add_expense
            from data.product_service import get_product_by_id
            product = get_product_by_id(product_id)
            product_name = product.get('product_name', f'产品ID:{product_id}') if product else f'产品ID:{product_id}'
            
            # 构建备注：说明总金额和等待池扣减情况，并包含订单号用于关联
            note = f"{product_name} 场内买入成交确认"
            if wait_pool_deducted > 0:
                if remaining > 0:
                    note += f"（总金额: {amount}，等待池扣减: {wait_pool_deducted}，现金扣减: {remaining}）"
                else:
                    note += f"（总金额: {amount}，全部从等待池扣减）"
            else:
                note += f"（总金额: {amount}，全部从现金扣减）"
            if order_id:
                note += f" (订单号: {order_id})"
            
            # 记账金额：如果有现金扣减，记录现金扣减金额；如果没有现金扣减（全部从等待池扣减），记录 0
            # 这样账户余额不会错误减少，但记账记录会存在，备注中会说明总金额和等待池扣减情况
            ledger_amount = remaining if remaining > 0 else Decimal('0')
            
            # 使用传入的交易时间，如果没有则使用当前时间
            ledger_event_time = event_time if event_time else datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            
            add_expense(
                account_from=account_code,
                amount=ledger_amount,  # 如果有现金扣减，记录现金扣减金额；否则记录 0
                category_l1="理财投资",
                category_l2="买入确认",
                event_time=ledger_event_time,
                note=note
            )
            
            cash_pool_after = calc_account_balance(account_code)
            cash_pool_deducted = cash_pool_before - cash_pool_after
        else:
            logger.warning(f"无法找到账户: account_id={account_id}")
            cash_pool_before = Decimal('0')
            cash_pool_after = Decimal('0')
    
    elif trade_type == 'SELL':
        # 卖出：增加现金池（通过ledger记录收入）
        account = _get_account_by_id_or_code(account_id)
        if account:
            account_code = account.get('account_code')
            cash_pool_before = calc_account_balance(account_code)
            
            # 在ledger中记录收入（用于增加现金池）
            # 使用"卖出确认"分类，与场外交易保持一致
            from core.ledger_service import add_income
            from data.product_service import get_product_by_id
            product = get_product_by_id(product_id)
            product_name = product.get('product_name', f'产品ID:{product_id}') if product else f'产品ID:{product_id}'
            
            # 构建备注：包含订单号用于关联
            note = f"{product_name} 场内卖出成交确认"
            if order_id:
                note += f" (订单号: {order_id})"
            
            # 使用传入的交易时间，如果没有则使用当前时间
            ledger_event_time = event_time if event_time else datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            
            add_income(
                account_to=account_code,
                amount=amount,
                category_l1="理财投资",
                category_l2="卖出确认",
                event_time=ledger_event_time,
                note=note
            )
            
            cash_pool_after = calc_account_balance(account_code)
            cash_pool_deducted = cash_pool_after - cash_pool_before  # 注意：这里是增加，所以是正数
        else:
            logger.warning(f"无法找到账户: account_id={account_id}")
            cash_pool_before = Decimal('0')
            cash_pool_after = Decimal('0')
        
        wait_pool_before = Decimal('0')
        wait_pool_after = Decimal('0')
    else:
        # 其他类型（DIV_CASH等）：不扣减等待池
        wait_pool_before = Decimal('0')
        wait_pool_after = Decimal('0')
        cash_pool_before = Decimal('0')
        cash_pool_after = Decimal('0')
    
    total_deducted = wait_pool_deducted + (cash_pool_deducted if trade_type == 'BUY' else Decimal('0'))
    
    return TradeDeductionResult(
        wait_pool_deducted=wait_pool_deducted,
        cash_pool_deducted=cash_pool_deducted if trade_type == 'BUY' else -cash_pool_deducted,  # SELL时返回负数表示增加
        total_deducted=total_deducted,
        wait_pool_before=wait_pool_before,
        wait_pool_after=wait_pool_after,
        cash_pool_before=cash_pool_before,
        cash_pool_after=cash_pool_after
    )


def persist_trade_record(
    product_id: int,
    account_id,  # 可以是 int (数据库主键id) 或 str (account_code)
    trade_date: date,
    trade_time: datetime,
    trade_type: str,
    amount: Decimal,
    shares: Decimal,
    price: Optional[Decimal] = None,
    fee: Optional[Decimal] = None,
    tax: Decimal = Decimal('0'),
    other_fee: Decimal = Decimal('0'),
    remark: Optional[str] = None
) -> int:
    """
    保存成交记录到 trade_fills 表
    
    Args:
        product_id: 产品ID
        account_id: 账户ID（可以是整数类型的数据库主键id，或字符串类型的account_code）
        trade_date: 成交日期
        trade_time: 成交时间
        trade_type: 交易类型（BUY/SELL）
        amount: 成交金额
        shares: 成交份额
        price: 成交价（可选，若不填则用amount/shares计算）
        fee: 手续费（可选）
        tax: 印花税（默认0）
        other_fee: 其他费用（默认0）
        remark: 备注
    
    Returns:
        插入的记录ID
    """
    # 如果没有提供价格，从金额和份额计算
    if price is None:
        price = amount / shares if shares > 0 else Decimal('0')
    
    # 如果没有提供手续费，使用默认计算
    if fee is None:
        fee = calc_default_fee(amount)
    
    # 转换trade_type为side（BUY/SELL）
    side = 'BUY' if trade_type == 'BUY' else 'SELL'
    
    # 将 account_id（可能是 account_code 字符串）转换为数据库主键 id
    account_db_id = account_id
    if isinstance(account_id, str):
        account = _get_account_by_id_or_code(account_id)
        if account:
            account_db_id = account.get('id')
        else:
            logger.error(f"无法找到账户: account_id={account_id}")
            raise ValueError(f"账户不存在: account_id={account_id}")
    
    # 检查是否已存在相同的记录（防止重复创建）
    # 匹配条件：product_id, side, trade_date, trade_time, qty, amount（允许小误差）
    check_sql = """
        SELECT id FROM trade_fills
        WHERE product_id = %s
          AND side = %s
          AND trade_date = %s
          AND trade_time = %s
          AND ABS(qty - %s) < 0.0001
          AND ABS(amount - %s) < 0.01
          AND source = 'MANUAL'
        LIMIT 1
    """
    existing = execute_one(check_sql, (
        product_id, side, trade_date, trade_time, float(shares), float(amount)
    ))
    
    if existing:
        existing_id = existing.get('id')
        logger.warning(f"已存在相同的成交记录: trade_fills id={existing_id}, "
                      f"product_id={product_id}, date={trade_date}, time={trade_time}, "
                      f"qty={shares}, amount={amount}")
        return existing_id  # 返回已存在的记录ID，不重复创建
    
    sql = """
        INSERT INTO trade_fills (
            trade_date, trade_time, product_id, account_id, side,
            qty, price, amount, fee, tax, other_fee,
            remark, source
        ) VALUES (
            %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'MANUAL'
        )
    """
    
    params = (
        trade_date,
        trade_time,
        product_id,
        account_db_id,  # 使用转换后的数据库主键 id
        side,
        float(shares),
        float(price),
        float(amount),
        float(fee),
        float(tax),
        float(other_fee),
        remark,
    )
    
    record_id = execute_insert(sql, params)
    logger.info(f"保存场内成交记录: product_id={product_id}, account_id={account_id}, "
                f"type={trade_type}, amount={amount}, shares={shares}, record_id={record_id}")
    
    return record_id




def update_snapshot_if_needed(trade_date: date) -> None:
    """
    如果需要，更新快照（暂时不实现，因为快照是定时任务）
    
    Args:
        trade_date: 成交日期
    """
    # 快照更新由定时任务处理，这里只记录日志
    logger.debug(f"快照将在下次定时任务时更新: trade_date={trade_date}")


def refresh_advisor_suggestion(product_id: int) -> Optional[Dict]:
    """
    刷新Advisor建议
    
    Args:
        product_id: 产品ID
    
    Returns:
        最新建议字典
    """
    try:
        from advisor.advisor_service import run_for_product
        suggestion = run_for_product(product_id)
        logger.info(f"刷新Advisor建议: product_id={product_id}")
        return suggestion
    except Exception as e:
        logger.error(f"刷新Advisor建议失败: product_id={product_id}, error={e}", exc_info=True)
        return None


def save_exchange_trade(
    product_id: int,
    account_id: int,
    trade_date: date,
    trade_time: datetime,
    trade_type: str,
    amount: Decimal,
    shares: Decimal,
    price: Optional[Decimal] = None,
    fee: Optional[Decimal] = None,
    tax: Decimal = Decimal('0'),
    other_fee: Decimal = Decimal('0'),
    remark: Optional[str] = None
) -> Tuple[bool, str, Dict]:
    """
    保存场内成交（完整闭环）
    
    Args:
        product_id: 产品ID
        account_id: 账户ID
        trade_date: 成交日期
        trade_time: 成交时间
        trade_type: 交易类型（BUY/SELL）
        amount: 成交金额
        shares: 成交份额
        price: 成交价（可选）
        fee: 手续费（可选）
        tax: 印花税（默认0）
        other_fee: 其他费用（默认0）
        remark: 备注
    
    Returns:
        (success, message, result_dict)
        result_dict包含：
        - record_id: 记录ID
        - holdings_before: 持仓前
        - holdings_after: 持仓后
        - deduction_result: 扣减结果
        - suggestion: 最新建议
    """
    try:
        # 1. 验证输入
        validation = validate_trade_input(
            product_id, account_id, trade_type, amount, shares, price, fee, trade_date
        )
        
        if not validation.is_valid:
            error_msg = "; ".join(validation.errors)
            return False, f"验证失败: {error_msg}", {}
        
        # 2. 获取持仓前状态
        holdings_before = calc_exchange_holdings(product_id)
        
        # 3. 计算总金额（含手续费）
        # 如果没有提供手续费，使用默认计算
        if fee is None:
            fee = calc_default_fee(amount)
        total_amount = amount + fee + tax + other_fee  # 买入时，总金额 = 成交金额 + 所有费用
        
        # 4. 生成订单号（用于关联记账记录和交易记录）
        from data.product_service import get_product_by_id
        product = get_product_by_id(product_id)
        product_code = product.get('code') if product else f'PRODUCT_{product_id}'
        order_id = f"EXCHANGE_{product_code}_{trade_time.strftime('%Y%m%d_%H%M%S')}"
        
        # 5. 应用资金扣减（传入含手续费的总金额、订单号和交易时间）
        # 将 trade_time 转换为字符串格式用于记账记录
        event_time_str = trade_time.strftime('%Y-%m-%d %H:%M:%S') if isinstance(trade_time, datetime) else str(trade_time)
        deduction_result = apply_fund_deduction(product_id, account_id, total_amount, trade_type, order_id, event_time_str)
        
        # 6. 保存成交记录
        record_id = persist_trade_record(
            product_id, account_id, trade_date, trade_time, trade_type,
            amount, shares, price, fee, tax, other_fee, remark
        )
        
        # 7. 重新计算持仓
        holdings_after = calc_exchange_holdings(product_id)
        
        # 8. 对于买入/卖出交易，自动创建 buy_confirm/sell_confirm 记录（场内交易直接确认）
        buy_confirm_order_id = None
        sell_confirm_order_id = None
        if trade_type == 'BUY':
            try:
                from data.data_store import append_transaction, format_decimal
                
                # 使用已生成的订单号
                buy_confirm_order_id = order_id
                
                # 计算成交价（作为确认净值）
                confirm_nav = price if price else (amount / shares if shares > 0 else Decimal('0'))
                
                # 创建 buy_confirm 记录
                confirm_date_str = trade_date.strftime('%Y-%m-%d')
                confirm_time_str = trade_time.strftime('%H:%M:%S')
                created_at = f"{confirm_date_str} {confirm_time_str}"
                
                tx_record = {
                    'date': confirm_date_str,
                    'product_id': product_id,
                    'product_code': product_code,
                    'action': 'buy_confirm',
                    'amount': '',  # 买入确认不记录金额
                    'shares': format_decimal(shares, 6),
                    'fee': format_decimal(fee, 2) if fee else '0',
                    'nav': str(confirm_nav),
                    'nav_date': confirm_date_str,
                    'order_id': buy_confirm_order_id,
                    'note': remark or f'场内买入成交确认',
                    'created_at': created_at
                }
                append_transaction(tx_record)
                logger.info(f"场内买入自动确认: product_id={product_id}, shares={shares}, nav={confirm_nav}, order_id={buy_confirm_order_id}")
                # 注意：记账记录已在 apply_fund_deduction 中创建（买入确认支出）
            except Exception as e:
                logger.warning(f"创建买入确认记录失败（不影响成交记录）: {e}", exc_info=True)
        
        elif trade_type == 'SELL':
            # 对于卖出交易，创建 sell_confirm 记录
            try:
                from data.data_store import append_transaction, format_decimal
                
                # 使用已生成的订单号
                sell_confirm_order_id = order_id
                
                # 计算成交价（作为确认净值）
                confirm_nav = price if price else (amount / shares if shares > 0 else Decimal('0'))
                
                # 创建 sell_confirm 记录
                confirm_date_str = trade_date.strftime('%Y-%m-%d')
                confirm_time_str = trade_time.strftime('%H:%M:%S')
                created_at = f"{confirm_date_str} {confirm_time_str}"
                
                # 计算手续费（如果未提供）
                total_fee = fee + tax + other_fee if fee else calc_default_fee(amount)
                gross_amount = amount + total_fee  # 卖出总金额（含费）
                
                tx_record = {
                    'date': confirm_date_str,
                    'product_id': product_id,
                    'product_code': product_code,
                    'action': 'sell_confirm',
                    'amount': format_decimal(amount, 2),  # 卖出到账净额
                    'shares': format_decimal(shares, 6),
                    'fee': format_decimal(total_fee, 2),
                    'nav': str(confirm_nav),
                    'nav_date': confirm_date_str,
                    'order_id': sell_confirm_order_id,
                    'note': remark or f'场内卖出成交确认',
                    'created_at': created_at
                }
                append_transaction(tx_record)
                logger.info(f"场内卖出自动确认: product_id={product_id}, shares={shares}, nav={confirm_nav}, order_id={sell_confirm_order_id}")
                # 注意：记账记录已在 apply_fund_deduction 中创建（卖出确认收入）
            except Exception as e:
                logger.warning(f"创建卖出确认记录失败（不影响成交记录）: {e}", exc_info=True)
        
        # 8. 更新快照（如果需要）
        update_snapshot_if_needed(trade_date)
        
        # 9. 刷新Advisor建议
        suggestion = refresh_advisor_suggestion(product_id)
        
        result = {
            'record_id': record_id,
            'holdings_before': holdings_before,
            'holdings_after': holdings_after,
            'deduction_result': deduction_result,
            'suggestion': suggestion,
            'warnings': validation.warnings,
            'buy_confirm_order_id': buy_confirm_order_id  # 新增：买入确认订单号
        }
        
        return True, "保存成功", result
        
    except Exception as e:
        logger.error(f"保存场内成交失败: {e}", exc_info=True)
        return False, f"保存失败: {str(e)}", {}


def get_pending_amount_sum(product_id: int, account_id: Optional[int] = None) -> Decimal:
    """
    获取等待池余额总和
    
    Args:
        product_id: 产品ID
        account_id: 账户ID（可选）
    
    Returns:
        等待池余额总和
    """
    from core.pending_buy_service import get_pending_pool, get_all_pending_pools
    
    if account_id:
        pool = get_pending_pool(product_id, account_id)
        return Decimal(str(pool['pending_amount'])) if pool else Decimal('0')
    else:
        pools = get_all_pending_pools()
        total = Decimal('0')
        for pool in pools:
            if pool['product_id'] == product_id:
                total += Decimal(str(pool['pending_amount']))
        return total

