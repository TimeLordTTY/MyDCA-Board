# -*- coding: utf-8 -*-
"""
DrawdownAdvice - 回撤策略生产建议版

根据相对高点回撤触发加仓。
"""
import logging
from decimal import Decimal
from typing import Dict, Any, List

from ..strategy_interface import StrategyAdviceInterface, AdviceInput, AdviceOutput

logger = logging.getLogger(__name__)


class DrawdownAdvice(StrategyAdviceInterface):
    """回撤策略生产建议版"""
    
    strategy_type = 'TRIGGER'  # 触发层：判断是否买入
    
    def evaluate(self, input_data: AdviceInput) -> AdviceOutput:
        """
        评估并输出建议
        
        参数来自 param_json，例如：
        {
          "window_days": 750,
          "levels": [0.02, 0.04, 0.08],
          "buy_amounts": [1000, 1500, 2000]
        }
        """
        params = input_data.param_json
        window_days = int(params.get('window_days', 750))
        levels = params.get('levels', [0.02, 0.04, 0.08])
        buy_amounts = params.get('buy_amounts', [1000, 1500, 2000])
        
        # 确保levels和buy_amounts数量一致
        if isinstance(levels, str):
            import json
            levels = json.loads(levels)
        if isinstance(buy_amounts, str):
            import json
            buy_amounts = json.loads(buy_amounts)
        
        levels = [float(l) for l in levels]
        buy_amounts = [float(a) for a in buy_amounts]
        
        min_len = min(len(levels), len(buy_amounts))
        levels = levels[:min_len]
        buy_amounts = buy_amounts[:min_len]
        
        bind_config = input_data.bind_config
        min_trade_amount = float(bind_config.get('min_trade_amount', 1000))
        
        last_price = float(input_data.last_price)
        indicator = input_data.indicator or {}
        budget_amount = float(input_data.budget_amount + input_data.pending_amount)
        
        # 读取指标
        peak_close = indicator.get('peak_close')
        drawdown_from_peak = indicator.get('drawdown_from_peak', 0)
        
        if peak_close is None:
            reason_parts = [
                f"指标未就绪：窗口N={window_days}天，缺少peak_close峰值",
                f"原因：历史数据不足（需要至少{max(int(window_days * 0.6), 300)}个交易日）；或指标计算任务未运行"
            ]
            if budget_amount > 0:
                reason_parts.append(f"实际预算={budget_amount:.2f}（资金池分配={float(input_data.budget_amount):.2f}，等待池={float(input_data.pending_amount):.2f}）")
                if budget_amount < min_trade_amount:
                    reason_parts.append(f"预算不足最小成交额{min_trade_amount:.2f}，已进入等待池")
                else:
                    reason_parts.append(f"预算已进入等待池，等待指标就绪后再买入")
            else:
                reason_parts.append(f"预算=0.00（原因：资金池余额不足或未配置资金池规则）")
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
        
        peak_close = float(peak_close)
        
        # 获取其他指标用于reason说明
        pct_rank = indicator.get('pct_rank')
        ma20 = indicator.get('ma20')
        ma60 = indicator.get('ma60')
        
        # 计算回撤
        if peak_close > 0:
            drawdown = (last_price - peak_close) / peak_close
        else:
            drawdown = 0.0
        
        drawdown_pct = abs(drawdown) * 100
        
        # 构建指标说明部分
        indicator_details = []
        if pct_rank is not None:
            indicator_details.append(f"分位排名={float(pct_rank)*100:.2f}%")
        indicator_details.append(f"峰值={peak_close:.4f}")
        indicator_details.append(f"回撤={drawdown_pct:.2f}%")
        if ma20 is not None:
            ma20_val = float(ma20)
            price_ma20_ratio = (last_price / ma20_val * 100) if ma20_val > 0 else 0
            indicator_details.append(f"当前价/MA20={price_ma20_ratio:.1f}%")
        if ma60 is not None:
            ma60_val = float(ma60)
            price_ma60_ratio = (last_price / ma60_val * 100) if ma60_val > 0 else 0
            indicator_details.append(f"当前价/MA60={price_ma60_ratio:.1f}%")
        
        indicator_str = '，'.join(indicator_details)
        
        # 检查是否触发加仓档位
        triggered_level = None
        for i, level in enumerate(levels):
            if drawdown <= -level:  # drawdown是负数，level是正数
                triggered_level = i
                break
        
        if triggered_level is not None:
            # 触发加仓
            suggest_amount = buy_amounts[triggered_level]
            
            reason_parts = [
                f"步骤1：回撤判断 → 峰值={peak_close:.4f}，当前价={last_price:.4f}，回撤={drawdown_pct:.2f}%"
            ]
            reason_parts.append(
                f"步骤1：回撤判断 → 回撤{drawdown_pct:.2f}% ≥ 档位{levels[triggered_level]*100:.0f}%（档位列表：{', '.join([f'{l*100:.0f}%' for l in levels])}）→ 触发加仓信号"
            )
            reason_parts.append(f"步骤2：技术指标 → {indicator_str}")
            
            # 检查预算是否足够最小成交额
            if budget_amount < min_trade_amount:
                reason_parts.append(
                    f"步骤3：预算检查 → 实际预算={budget_amount:.2f}（资金池分配={float(input_data.budget_amount):.2f}，等待池={float(input_data.pending_amount):.2f}）"
                )
                reason_parts.append(
                    f"步骤3：预算检查 → 预算{budget_amount:.2f} < 最小成交额{min_trade_amount:.2f}，差额={min_trade_amount - budget_amount:.2f} → 进入等待池"
                )
                reason_parts.append(f"最终决策：WAIT（回撤满足但预算不足）")
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
            
            # 限制建议金额不超过预算
            suggest_amount = min(suggest_amount, budget_amount)
            
            reason_parts.append(
                f"步骤3：预算检查 → 实际预算={budget_amount:.2f}（资金池分配={float(input_data.budget_amount):.2f}，等待池={float(input_data.pending_amount):.2f}）≥ 最小成交额{min_trade_amount:.2f} → 通过"
            )
            reason_parts.append(
                f"步骤4：金额计算 → 原计划加仓={buy_amounts[triggered_level]:.2f}，受预算限制 → 建议加仓={suggest_amount:.2f}"
            )
            reason_parts.append(f"最终决策：BUY（回撤满足且预算充足）")
            
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
            # 未触发
            # 计算距离最近档位的距离
            nearest_level = None
            nearest_distance = None
            for i, level in enumerate(levels):
                distance = abs(drawdown + level)  # drawdown是负数，level是正数
                if nearest_distance is None or distance < nearest_distance:
                    nearest_distance = distance
                    nearest_level = level
            
            reason_parts = [
                f"步骤1：回撤判断 → 峰值={peak_close:.4f}，当前价={last_price:.4f}，回撤={drawdown_pct:.2f}%"
            ]
            reason_parts.append(
                f"步骤1：回撤判断 → 回撤{drawdown_pct:.2f}% < 所有档位（档位列表：{', '.join([f'{l*100:.0f}%' for l in levels])}）→ 不触发加仓"
            )
            if nearest_level is not None:
                reason_parts.append(f"步骤1：回撤判断 → 距离最近档位{nearest_level*100:.0f}%还需回撤{nearest_distance*100:.2f}%")
            reason_parts.append(f"步骤2：技术指标 → {indicator_str}")
            reason_parts.append(
                f"步骤3：预算检查 → 实际预算={budget_amount:.2f}（资金池分配={float(input_data.budget_amount):.2f}，等待池={float(input_data.pending_amount):.2f}）"
            )
            if budget_amount >= min_trade_amount:
                reason_parts.append(f"步骤3：预算检查 → 预算充足，等待回撤达到档位时再买入")
            else:
                reason_parts.append(f"步骤3：预算检查 → 预算不足最小成交额{min_trade_amount:.2f}，需等待资金池增加或回撤达到档位")
            reason_parts.append(f"最终决策：HOLD（回撤未达到档位）")
            
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

