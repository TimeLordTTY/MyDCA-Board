# -*- coding: utf-8 -*-
"""
SimpleAdvice - 简单策略生产建议版

按计划买/预算够就买，不做指标判断（仍受溢价刹车/最小成交额/手续费约束）。
"""
import logging
from decimal import Decimal
from typing import Dict, Any

from ..strategy_interface import StrategyAdviceInterface, AdviceInput, AdviceOutput

logger = logging.getLogger(__name__)


class SimpleAdvice(StrategyAdviceInterface):
    """简单策略生产建议版"""
    
    strategy_type = 'TRIGGER'  # 触发层：判断是否买入
    
    def evaluate(self, input_data: AdviceInput) -> AdviceOutput:
        """
        评估并输出建议
        
        参数来自 param_json，例如：
        {
          "max_buy_per_day": 2000
        }
        """
        params = input_data.param_json
        max_buy_per_day = float(params.get('max_buy_per_day', 2000))
        
        bind_config = input_data.bind_config
        min_trade_amount = float(bind_config.get('min_trade_amount', 1000))
        ideal_trade_amount = float(bind_config.get('ideal_trade_amount', 2000))
        
        budget_amount = float(input_data.budget_amount + input_data.pending_amount)
        last_price = float(input_data.last_price)
        
        # 获取其他指标用于reason说明
        indicator = input_data.indicator or {}
        pct_rank = indicator.get('pct_rank')
        peak_close = indicator.get('peak_close')
        drawdown_from_peak = indicator.get('drawdown_from_peak')
        ma20 = indicator.get('ma20')
        ma60 = indicator.get('ma60')
        
        # 构建指标说明部分
        indicator_details = []
        if pct_rank is not None:
            indicator_details.append(f"分位排名={float(pct_rank)*100:.2f}%")
        if peak_close is not None:
            indicator_details.append(f"峰值={float(peak_close):.4f}")
        if drawdown_from_peak is not None:
            indicator_details.append(f"回撤={abs(float(drawdown_from_peak))*100:.2f}%")
        if ma20 is not None:
            ma20_val = float(ma20)
            price_ma20_ratio = (last_price / ma20_val * 100) if ma20_val > 0 else 0
            indicator_details.append(f"当前价/MA20={price_ma20_ratio:.1f}%")
        if ma60 is not None:
            ma60_val = float(ma60)
            price_ma60_ratio = (last_price / ma60_val * 100) if ma60_val > 0 else 0
            indicator_details.append(f"当前价/MA60={price_ma60_ratio:.1f}%")
        
        indicator_str = '，'.join(indicator_details) if indicator_details else '（其他指标未计算）'
        
        # 检查预算是否足够最小成交额
        if budget_amount < min_trade_amount:
            reason_parts = [
                f"步骤1：策略判断 → 简单策略（不做指标判断，预算够就买）"
            ]
            if indicator_str:
                reason_parts.append(f"步骤2：技术指标 → {indicator_str}（仅供参考）")
            reason_parts.append(
                f"步骤3：预算检查 → 实际预算={budget_amount:.2f}（资金池分配={float(input_data.budget_amount):.2f}，等待池={float(input_data.pending_amount):.2f}）"
            )
            reason_parts.append(
                f"步骤3：预算检查 → 预算{budget_amount:.2f} < 最小成交额{min_trade_amount:.2f}，差额={min_trade_amount - budget_amount:.2f} → 进入等待池"
            )
            reason_parts.append(f"最终决策：WAIT（预算不足最小成交额）")
            return AdviceOutput(
                action='WAIT',
                suggest_amount=Decimal('0'),
                suggest_ratio=None,
                limit_price_hint=None,
                premium_rate=input_data.premium_rate,
                moved_to_wait_pool=Decimal(str(budget_amount)),
                reason='；'.join(reason_parts) + '。',
                new_state_json=None
            )
        
        # 计算建议金额
        suggest_amount = min(budget_amount, ideal_trade_amount, max_buy_per_day)
        
        # 计算手续费
        fee = max(suggest_amount * 0.000845, 0.20)
        
        reason_parts = [
            f"步骤1：策略判断 → 简单策略（不做指标判断，预算够就买）"
        ]
        if indicator_str:
            reason_parts.append(f"步骤2：技术指标 → {indicator_str}（仅供参考）")
        reason_parts.append(
            f"步骤3：预算检查 → 实际预算={budget_amount:.2f}（资金池分配={float(input_data.budget_amount):.2f}，等待池={float(input_data.pending_amount):.2f}）≥ 最小成交额{min_trade_amount:.2f} → 通过"
        )
        reason_parts.append(
            f"步骤4：金额计算 → min(预算={budget_amount:.2f}，理想成交={ideal_trade_amount:.2f}，每日最大={max_buy_per_day:.2f}) = {suggest_amount:.2f}"
        )
        reason_parts.append(f"步骤5：费用估算 → 预计手续费={fee:.2f}（费率0.0845%，最低0.20元）")
        reason_parts.append(f"最终决策：BUY（预算充足）")
        
        reason = '；'.join(reason_parts) + '。'
        
        return AdviceOutput(
            action='BUY',
            suggest_amount=Decimal(str(suggest_amount)),
            suggest_ratio=None,
            limit_price_hint=None,
            premium_rate=input_data.premium_rate,
            moved_to_wait_pool=Decimal('0'),
            reason=reason,
            new_state_json=None
        )

