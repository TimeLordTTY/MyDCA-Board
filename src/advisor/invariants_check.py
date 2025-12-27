# -*- coding: utf-8 -*-
"""
硬约束自检模块

在每次生成建议后自动检查并写日志，失败则降级为WAIT。
"""
import logging
from decimal import Decimal
from typing import Dict, Any
import math

from .strategy_interface import AdviceInput, AdviceOutput

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

