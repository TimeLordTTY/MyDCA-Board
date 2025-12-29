# -*- coding: utf-8 -*-
"""
PercentileAdvice - 分位策略生产建议版

基于滚动N日close分位判断买入时机。
仅支持 tiers 模式（阶梯强度），不再支持 buy_percentile 模式。
"""
import logging
import hashlib
import json
from decimal import Decimal
from typing import Dict, Any

from ..strategy_interface import StrategyAdviceInterface, AdviceInput, AdviceOutput

logger = logging.getLogger(__name__)


class PercentileAdvice(StrategyAdviceInterface):
    """分位策略生产建议版（仅支持 tiers 模式）"""
    
    strategy_type = 'TRIGGER'  # 触发层：判断是否买入
    
    def evaluate(self, input_data: AdviceInput) -> AdviceOutput:
        """
        评估并输出建议
        
        参数来自 param_json，仅支持 tiers 模式：
        {
          "window_days": 750,
          "tiers": [
            {"max_rank": 0.20, "suggest_ratio": 1.00, "label":"极低估"},
            {"max_rank": 0.40, "suggest_ratio": 1.00, "label":"偏低估"},
            {"max_rank": 0.60, "suggest_ratio": 0.50, "label":"中性轻买"},
            {"max_rank": 1.01, "suggest_ratio": 0.00, "label":"偏高不买"}
          ],
          "max_buy_per_day": 2000
        }
        """
        params = input_data.param_json
        strategy_code = input_data.bind_config.get('strategy_code', 'percentile')
        param_set_id = input_data.bind_config.get('param_set_id', 'default')
        product_id = input_data.product_id
        
        # 计算参数摘要（MD5 hash）
        params_str = json.dumps(params, sort_keys=True, ensure_ascii=False)
        params_hash = hashlib.md5(params_str.encode('utf-8')).hexdigest()[:8]
        
        window_days = int(params.get('window_days', 750))
        max_buy_per_day = float(params.get('max_buy_per_day', 2000))
        
        # 验证必须包含 tiers 参数
        tiers = params.get('tiers')
        if not tiers or not isinstance(tiers, list) or len(tiers) == 0:
            error_msg = f"参数配置缺失：必须提供 tiers 参数。strategy_code={strategy_code}, param_set_id={param_set_id}, params_hash={params_hash}"
            logger.error(f"PercentileAdvice: {error_msg}")
            return AdviceOutput(
                action='HOLD',
                suggest_amount=Decimal('0'),
                suggest_ratio=None,
                limit_price_hint=None,
                premium_rate=input_data.premium_rate,
                moved_to_wait_pool=Decimal('0'),
                reason=error_msg,
                new_state_json=None
            )
        
        bind_config = input_data.bind_config
        min_trade_amount = float(bind_config.get('min_trade_amount', 1000))
        ideal_trade_amount = float(bind_config.get('ideal_trade_amount', 2000))
        
        last_price = float(input_data.last_price)
        indicator = input_data.indicator or {}
        budget_amount = float(input_data.budget_amount + input_data.pending_amount)
        
        # 读取指标（只使用 pct_rank，不再依赖 q_buy_price）
        pct_rank = indicator.get('pct_rank')
        
        # 获取其他指标用于reason说明
        peak_close = indicator.get('peak_close')
        drawdown_from_peak = indicator.get('drawdown_from_peak')
        ma20 = indicator.get('ma20')
        ma60 = indicator.get('ma60')
        
        # 构建指标说明部分
        indicator_details = []
        if pct_rank is not None:
            try:
                pct_rank_val = float(pct_rank) * 100
                indicator_details.append(f"分位排名={pct_rank_val:.2f}%")
            except (ValueError, TypeError):
                indicator_details.append(f"分位排名=N/A")
        if peak_close is not None:
            try:
                peak_close_val = float(peak_close)
                indicator_details.append(f"峰值={peak_close_val:.4f}")
            except (ValueError, TypeError):
                indicator_details.append(f"峰值=N/A")
        if drawdown_from_peak is not None:
            try:
                drawdown_val = abs(float(drawdown_from_peak)) * 100
                indicator_details.append(f"回撤={drawdown_val:.2f}%")
            except (ValueError, TypeError):
                indicator_details.append(f"回撤=N/A")
        if ma20 is not None:
            try:
                ma20_val = float(ma20)
                price_ma20_ratio = (last_price / ma20_val * 100) if ma20_val > 0 else 0
                indicator_details.append(f"当前价/MA20={price_ma20_ratio:.1f}%")
            except (ValueError, TypeError):
                indicator_details.append(f"当前价/MA20=N/A")
        if ma60 is not None:
            try:
                ma60_val = float(ma60)
                price_ma60_ratio = (last_price / ma60_val * 100) if ma60_val > 0 else 0
                indicator_details.append(f"当前价/MA60={price_ma60_ratio:.1f}%")
            except (ValueError, TypeError):
                indicator_details.append(f"当前价/MA60=N/A")
        
        indicator_str = '，'.join(indicator_details) if indicator_details else '（其他指标未计算）'
        
        # ========== 阶梯强度模式（唯一支持的模式） ==========
        pct_rank_float = float(pct_rank) if pct_rank is not None else None
        
        if pct_rank_float is None:
            # 分位排名未就绪
            reason_parts = [
                f"分位排名未就绪：窗口N={window_days}天",
                f"缺少pct_rank指标（原因：历史数据不足，需要至少{max(int(window_days * 0.6), 300)}个交易日；或指标计算任务未运行）"
            ]
            if budget_amount > 0:
                reason_parts.append(f"实际预算={budget_amount:.2f}（资金池分配={float(input_data.budget_amount):.2f}，等待池={float(input_data.pending_amount):.2f}）")
                if budget_amount < min_trade_amount:
                    reason_parts.append(f"预算不足最小成交额{min_trade_amount:.2f}，已进入等待池")
                else:
                    reason_parts.append(f"预算已进入等待池，等待指标就绪后再买入")
            else:
                reason_parts.append(f"预算=0.00（原因：资金池余额不足或未配置资金池规则）")
            
            logger.warning(f"PercentileAdvice: 分位排名未就绪，pct_rank=None, budget_amount={budget_amount:.2f}")
            return AdviceOutput(
                action='WAIT',
                suggest_amount=Decimal('0'),
                suggest_ratio=None,
                limit_price_hint=None,
                premium_rate=input_data.premium_rate,
                moved_to_wait_pool=Decimal(str(budget_amount)) if budget_amount >= min_trade_amount else Decimal('0'),
                reason='；'.join(reason_parts) + '。',
                new_state_json=None
            )
        
        # 根据pct_rank命中对应tier
        matched_tier = None
        for tier in tiers:
            max_rank = float(tier.get('max_rank', 1.01))
            if pct_rank_float < max_rank:
                matched_tier = tier
                break
        
        if not matched_tier:
            # 未命中任何tier（理论上不应该发生，因为最后一个tier的max_rank应该是1.01）
            matched_tier = tiers[-1]
        
        # 确保所有值都是正确的数字类型
        suggest_ratio = float(matched_tier.get('suggest_ratio', 0.0))
        tier_label = str(matched_tier.get('label', '未知档位'))
        tier_max_rank = float(matched_tier.get('max_rank', 1.01))
        
        # 计算建议金额：suggest_amount = budget_for_execution * suggest_ratio
        budget_for_execution = budget_amount  # 策略层暂时使用budget_amount
        suggest_amount_raw = budget_for_execution * suggest_ratio
        suggest_amount = min(suggest_amount_raw, ideal_trade_amount, max_buy_per_day)
        
        # 构建reason（必须包含：pct_rank、命中档位、档位比例、预算、建议买入金额）
        # 确保所有数值都转换为 float 后再格式化
        pct_rank_pct = float(pct_rank_float) * 100
        tier_max_rank_pct = float(tier_max_rank) * 100
        suggest_ratio_pct = float(suggest_ratio) * 100
        
        reason_parts = [
            f"【1. 分位排名判断】分位排名={pct_rank_pct:.2f}%，命中档位：{tier_label}（max_rank<{tier_max_rank_pct:.0f}%）",
            f"【2. 档位建议比例】档位建议比例={suggest_ratio_pct:.0f}%",
            f"【3. 预算计算】实际预算={budget_amount:.2f}（资金池分配={float(input_data.budget_amount):.2f}，等待池={float(input_data.pending_amount):.2f}）",
            f"【4. 建议金额计算】建议买入金额={suggest_amount:.2f}（计算过程：预算{budget_amount:.2f} × 比例{suggest_ratio_pct:.0f}% = {suggest_amount_raw:.2f}，受理想成交额{ideal_trade_amount:.2f}和每日最大{max_buy_per_day:.2f}限制后为{suggest_amount:.2f}）"
        ]
        reason_parts.append(f"【5. 技术指标】{indicator_str}")
        
        # 预算检查
        if suggest_amount > 0:
            if suggest_amount < min_trade_amount:
                reason_parts.append(f"【6. 最小成交额检查】建议金额{suggest_amount:.2f} < 最小成交额{min_trade_amount:.2f}，差额={min_trade_amount - suggest_amount:.2f} → 全部预算进入等待池")
                reason_parts.append(f"【7. 最终决策】WAIT（档位满足但金额不足最小成交额，等待资金累积）")
                
                # 记录日志
                logger.info(
                    f"PercentileAdvice决策: product_id={product_id}, strategy_code={strategy_code}, "
                    f"param_set_id={param_set_id}, params_hash={params_hash}, "
                    f"pct_rank={pct_rank_float:.4f}, tier={tier_label}, suggest_ratio={suggest_ratio:.2f}, "
                    f"decision=WAIT, reason=金额不足"
                )
                
                return AdviceOutput(
                    action='WAIT',
                    suggest_amount=Decimal('0'),
                    suggest_ratio=Decimal(str(suggest_ratio)),
                    limit_price_hint=None,
                    premium_rate=input_data.premium_rate,
                    moved_to_wait_pool=Decimal(str(budget_amount)),
                    reason='；'.join(reason_parts) + '。',
                    new_state_json=None
                )
            else:
                reason_parts.append(f"【6. 最小成交额检查】建议金额{suggest_amount:.2f} ≥ 最小成交额{min_trade_amount:.2f} → 通过")
                fee = max(suggest_amount * 0.000845, 0.20)
                reason_parts.append(f"【7. 费用估算】预计手续费={fee:.2f}（费率0.0845%，最低0.20元）")
                reason_parts.append(f"【8. 最终决策】BUY（档位满足且金额充足）")
                
                # 记录日志
                logger.info(
                    f"PercentileAdvice决策: product_id={product_id}, strategy_code={strategy_code}, "
                    f"param_set_id={param_set_id}, params_hash={params_hash}, "
                    f"pct_rank={pct_rank_float:.4f}, tier={tier_label}, suggest_ratio={suggest_ratio:.2f}, "
                    f"decision=BUY, suggest_amount={suggest_amount:.2f}"
                )
                
                return AdviceOutput(
                    action='BUY',
                    suggest_amount=Decimal(str(suggest_amount)),
                    suggest_ratio=Decimal(str(suggest_ratio)),
                    limit_price_hint=None,
                    premium_rate=input_data.premium_rate,
                    moved_to_wait_pool=Decimal('0'),
                    reason='；'.join(reason_parts) + '。',
                    new_state_json=None
                )
        else:
            # suggest_ratio=0，不买入
            reason_parts.append(f"【6. 档位检查】档位建议比例=0%，不买入")
            pct_rank_pct_hold = float(pct_rank_float) * 100
            reason_parts.append(f"【7. 最终决策】HOLD（分位排名{pct_rank_pct_hold:.2f}%偏高，等待回落）")
            
            # 记录日志
            logger.info(
                f"PercentileAdvice决策: product_id={product_id}, strategy_code={strategy_code}, "
                f"param_set_id={param_set_id}, params_hash={params_hash}, "
                f"pct_rank={pct_rank_float:.4f}, tier={tier_label}, suggest_ratio={suggest_ratio:.2f}, "
                f"decision=HOLD, reason=分位偏高"
            )
            
            return AdviceOutput(
                action='HOLD',
                suggest_amount=Decimal('0'),
                suggest_ratio=Decimal(str(suggest_ratio)),
                limit_price_hint=None,
                premium_rate=input_data.premium_rate,
                moved_to_wait_pool=Decimal('0'),
                reason='；'.join(reason_parts) + '。',
                new_state_json=None
            )

