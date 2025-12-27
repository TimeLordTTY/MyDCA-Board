# -*- coding: utf-8 -*-
"""
PercentileAdvice - 分位策略生产建议版

基于滚动N日close分位判断买入时机。
"""
import logging
from decimal import Decimal
from typing import Dict, Any

from ..strategy_interface import StrategyAdviceInterface, AdviceInput, AdviceOutput

logger = logging.getLogger(__name__)


class PercentileAdvice(StrategyAdviceInterface):
    """分位策略生产建议版"""
    
    strategy_type = 'TRIGGER'  # 触发层：判断是否买入
    
    def evaluate(self, input_data: AdviceInput) -> AdviceOutput:
        """
        评估并输出建议
        
        参数来自 param_json，例如：
        {
          "window_days": 750,
          "buy_percentile": 0.20,
          "max_buy_per_day": 2000
        }
        """
        params = input_data.param_json
        window_days = int(params.get('window_days', 750))
        buy_percentile = float(params.get('buy_percentile', 0.20))  # 0.20 = 20%
        max_buy_per_day = float(params.get('max_buy_per_day', 2000))
        
        bind_config = input_data.bind_config
        min_trade_amount = float(bind_config.get('min_trade_amount', 1000))
        ideal_trade_amount = float(bind_config.get('ideal_trade_amount', 2000))
        
        last_price = float(input_data.last_price)
        indicator = input_data.indicator or {}
        budget_amount = float(input_data.budget_amount + input_data.pending_amount)
        
        logger.debug(f"PercentileAdvice: product_id={input_data.product_id}, budget_amount={input_data.budget_amount:.2f}, pending_amount={input_data.pending_amount:.2f}, total={budget_amount:.2f}")
        
        # 读取指标
        q_buy_price = indicator.get('q_buy_price')
        if q_buy_price is None:
            # 详细说明为什么指标未就绪
            reason_parts = [
                f"指标未就绪：窗口N={window_days}天，买入分位={buy_percentile*100:.0f}%",
                f"缺少q_buy_price阈值（原因：历史数据不足，需要至少{max(int(window_days * 0.6), 300)}个交易日；或指标计算任务未运行）"
            ]
            
            # 添加预算详情
            if budget_amount > 0:
                reason_parts.append(f"实际预算={budget_amount:.2f}（资金池分配={float(input_data.budget_amount):.2f}，等待池={float(input_data.pending_amount):.2f}）")
                if budget_amount < min_trade_amount:
                    reason_parts.append(f"预算不足最小成交额{min_trade_amount:.2f}，已进入等待池")
                else:
                    reason_parts.append(f"预算已进入等待池，等待指标就绪后再买入")
            else:
                reason_parts.append(f"预算=0.00（原因：资金池余额不足或未配置资金池规则）")
            
            logger.warning(f"PercentileAdvice: 指标未就绪，q_buy_price=None, budget_amount={budget_amount:.2f}")
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
        
        q_buy_price = float(q_buy_price)
        
        # 获取其他指标用于reason说明
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
        
        # 判断买入信号
        if last_price <= q_buy_price:
            # 触发买入信号
            price_diff_pct = ((q_buy_price - last_price) / q_buy_price * 100) if q_buy_price > 0 else 0
            
            # 步骤1：价格判断
            reason_parts = [
                f"步骤1：价格判断 → 当前价={last_price:.4f} ≤ 阈值价={q_buy_price:.4f}（低于阈值{price_diff_pct:.2f}%）→ 触发买入信号"
            ]
            
            # 步骤2：指标展示
            reason_parts.append(f"步骤2：技术指标 → {indicator_str}")
            
            # 步骤3：预算检查
            if budget_amount < min_trade_amount:
                reason_parts.append(
                    f"步骤3：预算检查 → 实际预算={budget_amount:.2f}（资金池分配={float(input_data.budget_amount):.2f}，等待池={float(input_data.pending_amount):.2f}）"
                )
                reason_parts.append(
                    f"步骤3：预算检查 → 预算{budget_amount:.2f} < 最小成交额{min_trade_amount:.2f}，差额={min_trade_amount - budget_amount:.2f} → 进入等待池"
                )
                reason_parts.append(f"最终决策：WAIT（价格满足但预算不足）")
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
            
            # 步骤4：计算建议金额
            suggest_amount = min(budget_amount, ideal_trade_amount, max_buy_per_day)
            
            # 计算手续费
            fee = max(suggest_amount * 0.000845, 0.20)
            
            reason_parts.append(
                f"步骤3：预算检查 → 实际预算={budget_amount:.2f}（资金池分配={float(input_data.budget_amount):.2f}，等待池={float(input_data.pending_amount):.2f}）≥ 最小成交额{min_trade_amount:.2f} → 通过"
            )
            reason_parts.append(
                f"步骤4：金额计算 → min(预算={budget_amount:.2f}，理想成交={ideal_trade_amount:.2f}，每日最大={max_buy_per_day:.2f}) = {suggest_amount:.2f}"
            )
            reason_parts.append(f"步骤5：费用估算 → 预计手续费={fee:.2f}（费率0.0845%，最低0.20元）")
            reason_parts.append(f"最终决策：BUY（价格满足且预算充足）")
            
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
        else:
            # 不触发买入
            price_diff_pct = ((last_price - q_buy_price) / q_buy_price * 100) if q_buy_price > 0 else 0
            
            reason_parts = [
                f"步骤1：价格判断 → 当前价={last_price:.4f} > 阈值价={q_buy_price:.4f}（高于阈值{price_diff_pct:.2f}%）→ 不触发买入"
            ]
            reason_parts.append(f"步骤2：技术指标 → {indicator_str}")
            reason_parts.append(
                f"步骤3：预算检查 → 实际预算={budget_amount:.2f}（资金池分配={float(input_data.budget_amount):.2f}，等待池={float(input_data.pending_amount):.2f}）"
            )
            if budget_amount >= min_trade_amount:
                reason_parts.append(f"步骤3：预算检查 → 预算充足，等待价格回落至阈值价{q_buy_price:.4f}以下时再买入")
            else:
                reason_parts.append(f"步骤3：预算检查 → 预算不足最小成交额{min_trade_amount:.2f}，需等待资金池增加或价格回落")
            reason_parts.append(f"最终决策：HOLD（价格未满足买入条件）")
            
            reason = '；'.join(reason_parts) + '。'
            
            return AdviceOutput(
                action='HOLD',
                suggest_amount=Decimal('0'),
                suggest_ratio=None,
                limit_price_hint=None,
                premium_rate=input_data.premium_rate,
                moved_to_wait_pool=Decimal('0'),
                reason=reason,
                new_state_json=None
            )

