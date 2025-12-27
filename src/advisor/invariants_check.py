# -*- coding: utf-8 -*-
"""
硬约束自检模块

在每次生成建议后自动检查并写日志，失败则降级为WAIT。
支持AdviceOutput和AdvisorViewModel两种输入。
"""
import logging
from decimal import Decimal
from typing import Dict, Any, Optional, Union
import math

from .strategy_interface import AdviceInput, AdviceOutput
from .view_model import AdvisorViewModel

logger = logging.getLogger(__name__)


def check_invariants(output: AdviceOutput, input_data: AdviceInput, product: Dict) -> AdviceOutput:
    """
    执行硬约束自检
    
    Args:
        output: 策略输出
        input_data: 策略输入
        product: 产品信息
        
    Returns:
        修正后的输出（如果自检失败则降级为WAIT）
    """
    errors = []
    
    # 1. 检查金额非负
    if output.suggest_amount < 0:
        errors.append(f"suggest_amount < 0: {output.suggest_amount}")
    
    if output.moved_to_wait_pool < 0:
        errors.append(f"moved_to_wait_pool < 0: {output.moved_to_wait_pool}")
    
    # 2. 检查手续费计算
    if output.action == 'BUY' and output.suggest_amount > 0:
        fee_rate = float(input_data.bind_config.get('fee_rate', 0.000845))
        fee_min = float(input_data.bind_config.get('fee_min', 0.20))
        fee = max(float(output.suggest_amount) * fee_rate, fee_min)
        
        if math.isnan(fee) or math.isinf(fee):
            errors.append(f"手续费计算异常: fee={fee}")
    
    # 3. 检查BUY时的最小成交额
    if output.action == 'BUY':
        min_trade_amount = float(input_data.bind_config.get('min_trade_amount', 1000))
        if output.suggest_amount < min_trade_amount:
            errors.append(f"BUY时建议金额{output.suggest_amount} < 最小成交额{min_trade_amount}")
        
        # 检查预算是否足够
        total_budget = float(input_data.budget_amount + input_data.pending_amount)
        if total_budget < min_trade_amount:
            errors.append(f"预算不足: {total_budget} < {min_trade_amount}")
    
    # 4. 检查QDII溢价刹车
    if product.get('is_qdii'):
        premium_rate = output.premium_rate
        if premium_rate is not None:
            premium_float = float(premium_rate)
            if premium_float > 0.02:
                # 溢价>2%时，action必须WAIT
                if output.action != 'WAIT':
                    errors.append(f"QDII溢价{premium_float*100:.2f}%>2%，但action={output.action}，应强制WAIT")
    
    # 5. 检查reason长度
    if not output.reason or len(output.reason) < 30:
        errors.append(f"reason长度不足30字: {len(output.reason) if output.reason else 0}")
    
    # 如果有错误，降级为WAIT
    if errors:
        error_msg = "; ".join(errors)
        logger.warning(f"自检失败: product_id={input_data.product_id}, errors={error_msg}")
        
        return AdviceOutput(
            action='WAIT',
            suggest_amount=Decimal('0'),
            suggest_ratio=None,
            limit_price_hint=output.limit_price_hint,
            premium_rate=output.premium_rate,
            moved_to_wait_pool=output.suggest_amount + output.moved_to_wait_pool,
            reason=f"自检失败：{error_msg}。原建议已降级为WAIT，资金进入等待池。",
            new_state_json=output.new_state_json
        )
    
    return output


def check_viewmodel_invariants(view_model: AdvisorViewModel, product: Dict) -> Optional[AdvisorViewModel]:
    """
    执行ViewModel的硬约束自检
    
    Args:
        view_model: AdvisorViewModel
        product: 产品信息
        
    Returns:
        修正后的ViewModel（如果自检失败则降级为WAIT），None表示通过验证
    """
    errors = []
    
    # 1. 金额非负
    if view_model.budget_to_execute < 0:
        errors.append(f"budget_to_execute < 0: {view_model.budget_to_execute}")
    
    if view_model.budget_to_wait_pool < 0:
        errors.append(f"budget_to_wait_pool < 0: {view_model.budget_to_wait_pool}")
    
    if view_model.budget_for_execution < 0:
        errors.append(f"budget_for_execution < 0: {view_model.budget_for_execution}")
    
    # 2. 比例与金额恒等式
    if view_model.budget_to_execute + view_model.budget_to_wait_pool > view_model.budget_for_execution:
        errors.append(
            f"budget_to_execute({view_model.budget_to_execute}) + budget_to_wait_pool({view_model.budget_to_wait_pool}) "
            f"> budget_for_execution({view_model.budget_for_execution})"
        )
    
    # 3. 比例计算正确性
    if view_model.budget_for_execution > 0:
        expected_execute_ratio = view_model.budget_to_execute / view_model.budget_for_execution
        expected_wait_ratio = view_model.budget_to_wait_pool / view_model.budget_for_execution
        
        if abs(view_model.execute_ratio - expected_execute_ratio) > Decimal('0.0001'):
            errors.append(
                f"execute_ratio不一致: 期望={expected_execute_ratio}, 实际={view_model.execute_ratio}"
            )
        if abs(view_model.wait_ratio - expected_wait_ratio) > Decimal('0.0001'):
            errors.append(
                f"wait_ratio不一致: 期望={expected_wait_ratio}, 实际={view_model.wait_ratio}"
            )
        
        # 比例总和不能超过1（允许<1，因为可能有未分配部分）
        if view_model.execute_ratio + view_model.wait_ratio > Decimal('1.0001'):
            errors.append(
                f"execute_ratio({view_model.execute_ratio}) + wait_ratio({view_model.wait_ratio}) > 1.0"
            )
    else:
        # budget_for_execution=0时，比例应该都是0
        if view_model.execute_ratio != 0 or view_model.wait_ratio != 0:
            errors.append(
                f"budget_for_execution=0但比例不为0: execute_ratio={view_model.execute_ratio}, wait_ratio={view_model.wait_ratio}"
            )
    
    # 4. premium>2%时必须 budget_to_execute=0 且 budget_to_wait_pool=budget_for_execution
    if product.get('is_qdii') and view_model.premium_rate is not None:
        premium_float = float(view_model.premium_rate)
        if premium_float > 0.02:
            if view_model.budget_to_execute != 0:
                errors.append(
                    f"QDII溢价{premium_float*100:.2f}%>2%，但budget_to_execute={view_model.budget_to_execute}，应为0"
                )
            if view_model.budget_to_wait_pool != view_model.budget_for_execution:
                errors.append(
                    f"QDII溢价{premium_float*100:.2f}%>2%，但budget_to_wait_pool({view_model.budget_to_wait_pool}) "
                    f"!= budget_for_execution({view_model.budget_for_execution})"
                )
    
    # 5. action=BUY时必须满足 min_trade_amount & 现金足够 & 一手约束
    if view_model.action == 'BUY' and view_model.budget_to_execute > 0:
        if view_model.budget_to_execute < view_model.min_trade_amount:
            errors.append(
                f"BUY时budget_to_execute({view_model.budget_to_execute}) < min_trade_amount({view_model.min_trade_amount})"
            )
        
        # 检查现金是否足够
        if view_model.budget_to_execute > view_model.cash_available:
            errors.append(
                f"BUY时budget_to_execute({view_model.budget_to_execute}) > cash_available({view_model.cash_available})"
            )
        
        # 检查一手约束（ETF/LOF）
        if view_model.lot_size and view_model.rounded_amount:
            if view_model.budget_to_execute != view_model.rounded_amount:
                errors.append(
                    f"ETF/LOF一手约束：budget_to_execute({view_model.budget_to_execute}) != rounded_amount({view_model.rounded_amount})"
                )
    
    # 6. planned_amount恒等式
    if abs(view_model.planned_amount - (view_model.new_budget + view_model.wait_pool_before)) > Decimal('0.01'):
        errors.append(
            f"planned_amount({view_model.planned_amount}) != new_budget({view_model.new_budget}) + wait_pool_before({view_model.wait_pool_before})"
        )
    
    # 7. executed_amount + moved_to_wait <= planned_amount
    if view_model.budget_to_execute + view_model.budget_to_wait_pool > view_model.planned_amount:
        errors.append(
            f"budget_to_execute({view_model.budget_to_execute}) + budget_to_wait_pool({view_model.budget_to_wait_pool}) "
            f"> planned_amount({view_model.planned_amount})"
        )
    
    # 8. wait_pool_after == wait_pool_before + moved_to_wait（Advisor不扣减）
    expected_wait_pool_after = view_model.wait_pool_before + view_model.budget_to_wait_pool
    if abs(view_model.wait_pool_balance - expected_wait_pool_after) > Decimal('0.01'):
        errors.append(
            f"wait_pool_balance({view_model.wait_pool_balance}) != wait_pool_before({view_model.wait_pool_before}) + moved_to_wait({view_model.budget_to_wait_pool})"
        )
    
    # 9. 若action=BUY，则executed_amount > 0
    if view_model.action == 'BUY' and view_model.budget_to_execute == 0:
        errors.append(f"action=BUY但budget_to_execute=0")
    
    # 10. reason_blocks至少3条且每条含输入值
    if len(view_model.reason_blocks) < 3:
        errors.append(f"reason_blocks数量不足: {len(view_model.reason_blocks)} < 3")
    
    for i, block in enumerate(view_model.reason_blocks):
        if not block.input_values:
            errors.append(f"reason_blocks[{i}]缺少input_values")
    
    # 如果有错误，记录并返回None（表示需要降级）
    if errors:
        error_msg = "; ".join(errors)
        logger.error(f"ViewModel自检失败: product_id={view_model}, errors={error_msg}")
        return None
    
    logger.info(f"ViewModel自检通过")
    return view_model


