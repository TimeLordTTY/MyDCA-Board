# -*- coding: utf-8 -*-
"""
ProfitRecycleAdvice - 利润回收策略生产建议版

生产版只输出建议，不"虚拟卖出成交"，但状态要更新。
"""
import logging
import json
from decimal import Decimal
from typing import Dict, Any, Optional, List
from datetime import date, datetime

from ..strategy_interface import StrategyAdviceInterface, AdviceInput, AdviceOutput

logger = logging.getLogger(__name__)


class ProfitRecycleAdvice(StrategyAdviceInterface):
    """利润回收策略生产建议版"""
    
    strategy_type = 'TRIGGER'  # 触发层：判断是否买入
    
    def evaluate(self, input_data: AdviceInput) -> AdviceOutput:
        """
        评估并输出建议
        
        参数来自 param_json，例如：
        {
          "ma_window": 250,
          "high_bias": 0.20,
          "lock_ratio_low": 0.00,
          "lock_ratio_mid": 0.05,
          "lock_ratio_high": 0.20,
          "deep_dip_levels": [{"threshold": -0.10, "use_ratio": 0.50}, {"threshold": -0.15, "use_ratio": 1.00}],
          "take_profit_enabled": true,
          "take_profit_bias": 0.18,
          "take_profit_sell_ratio": 0.05
        }
        """
        params = input_data.param_json
        state = input_data.state_json or {}
        
        # 参数
        ma_window = int(params.get('ma_window', 250))
        high_bias = float(params.get('high_bias', 0.20))
        lock_ratio_low = float(params.get('lock_ratio_low', 0.00))
        lock_ratio_mid = float(params.get('lock_ratio_mid', 0.05))
        lock_ratio_high = float(params.get('lock_ratio_high', 0.20))
        
        deep_dip_levels = params.get('deep_dip_levels', [])
        if isinstance(deep_dip_levels, str):
            deep_dip_levels = json.loads(deep_dip_levels)
        
        take_profit_enabled = bool(params.get('take_profit_enabled', True))
        take_profit_bias = float(params.get('take_profit_bias', 0.18))
        take_profit_sell_ratio = float(params.get('take_profit_sell_ratio', 0.05))
        take_profit_cooldown_days = int(params.get('take_profit_cooldown_days', 60))
        near_peak_ratio = float(params.get('near_peak_ratio', 0.98))
        
        allow_multi_deep_dip = bool(params.get('allow_multi_deep_dip', True))
        rebound_reset_rate = float(params.get('rebound_reset_rate', 0.05))
        debounce_days = int(params.get('debounce_days', 30))
        
        # 状态初始化
        last_peak_price = float(state.get('last_peak_price', 0) or 0)
        locked_pool = float(state.get('locked_pool', 0) or 0)
        last_action_date = state.get('last_action_date')
        deep_dip_triggered = bool(state.get('deep_dip_triggered', False))
        last_dip_date = state.get('last_dip_date')
        last_dip_price = float(state.get('last_dip_price', 0) or 0)
        last_tp_date = state.get('last_tp_date')
        
        # 当前价格
        last_price = float(input_data.last_price)
        today = input_data.as_of_time.date() if isinstance(input_data.as_of_time, datetime) else date.today()
        
        # 预算
        budget_amount = float(input_data.budget_amount + input_data.pending_amount)
        total_cash_pool = budget_amount
        
        # 持仓
        holding = input_data.holding or {}
        holding_value = float(holding.get('current_value', 0) or 0)
        
        # 指标
        indicator = input_data.indicator or {}
        ma = indicator.get('ma20')  # 使用MA20作为估值参考
        peak_close = indicator.get('peak_close')
        drawdown_from_peak = indicator.get('drawdown_from_peak', 0)
        
        reasons = []
        new_state = state.copy()
        
        # 更新峰值
        if last_peak_price == 0:
            last_peak_price = last_price
            new_state['last_peak_price'] = last_peak_price
            reasons.append(f"初始化峰值水位: {last_peak_price:.4f}")
        
        if last_price > last_peak_price:
            last_peak_price = last_price
            new_state['last_peak_price'] = last_peak_price
            reasons.append(f"更新峰值水位: {last_peak_price:.4f}")
        
        # 计算回撤
        if last_peak_price > 0:
            drawdown = (last_price - last_peak_price) / last_peak_price
        else:
            drawdown = 0.0
        
        drawdown_pct = abs(drawdown) * 100
        
        # 获取其他指标用于reason说明
        indicator = input_data.indicator or {}
        pct_rank = indicator.get('pct_rank')
        ma20 = indicator.get('ma20')
        ma60 = indicator.get('ma60')
        
        # 构建指标说明部分
        indicator_details = []
        if pct_rank is not None:
            indicator_details.append(f"分位排名={float(pct_rank)*100:.2f}%")
        if ma20 is not None:
            ma20_val = float(ma20)
            price_ma20_ratio = (last_price / ma20_val * 100) if ma20_val > 0 else 0
            indicator_details.append(f"当前价/MA20={price_ma20_ratio:.1f}%")
        if ma60 is not None:
            ma60_val = float(ma60)
            price_ma60_ratio = (last_price / ma60_val * 100) if ma60_val > 0 else 0
            indicator_details.append(f"当前价/MA60={price_ma60_ratio:.1f}%")
        
        indicator_str = '，'.join(indicator_details) if indicator_details else '（其他指标未计算）'
        
        # 计算估值偏离
        nav_bias = 0.0
        if ma and ma > 0:
            nav_bias = (last_price - ma) / ma
            reasons.append(f"步骤1：估值判断 → MA{ma_window}={ma:.4f}，当前价={last_price:.4f}，偏离度={nav_bias*100:.2f}%")
        
        # 动态锁定比例
        lock_ratio = lock_ratio_mid
        if ma:
            if last_price < ma:
                lock_ratio = lock_ratio_low
            elif last_price > ma * (1.0 + high_bias):
                lock_ratio = lock_ratio_high
            else:
                lock_ratio = lock_ratio_mid
        
        # 锁定池目标
        target_locked = total_cash_pool * lock_ratio
        
        # 调整锁定池
        unlock_release = 0.0
        if locked_pool > target_locked + 1e-8:
            unlock_release = locked_pool - target_locked
            locked_pool = target_locked
            reasons.append(f"步骤2：锁定池调整 → 估值转弱（锁定池目标={target_locked:.2f}，当前={locked_pool+unlock_release:.2f}），释放 {unlock_release:.2f} 元用于买入")
        elif locked_pool < target_locked - 1e-8:
            inc = target_locked - locked_pool
            locked_pool = target_locked
            reasons.append(f"步骤2：锁定池调整 → 估值偏高（锁定池目标={target_locked:.2f}，当前={locked_pool-inc:.2f}），增加锁定池 {inc:.2f} 元（减少追高投入）")
        
        # 深跌触发
        if allow_multi_deep_dip and deep_dip_triggered:
            can_reset = False
            if last_dip_price > 0 and last_price >= last_dip_price * (1.0 + rebound_reset_rate):
                can_reset = True
            if last_dip_date:
                try:
                    if isinstance(last_dip_date, str):
                        last_dip_date_obj = datetime.strptime(last_dip_date, '%Y-%m-%d').date()
                    else:
                        last_dip_date_obj = last_dip_date
                    if (today - last_dip_date_obj).days >= debounce_days:
                        can_reset = True
                except Exception:
                    pass
            if can_reset:
                deep_dip_triggered = False
                new_state['deep_dip_triggered'] = False
        
        deep_dip_release = 0.0
        if not deep_dip_triggered and locked_pool > 1e-8:
            matched = None
            for lvl in deep_dip_levels:
                threshold = float(lvl.get('threshold', 0))
                if drawdown <= threshold:
                    if matched is None or threshold < matched['threshold']:
                        matched = {'threshold': threshold, 'use_ratio': float(lvl.get('use_ratio', 0))}
            
            if matched:
                deep_dip_release = locked_pool * matched['use_ratio']
                deep_dip_release = max(0.0, min(deep_dip_release, locked_pool))
                locked_pool -= deep_dip_release
                
                new_state['deep_dip_triggered'] = True
                new_state['last_dip_date'] = today.strftime('%Y-%m-%d')
                new_state['last_dip_price'] = last_price
                new_state['deep_dip_count'] = int(state.get('deep_dip_count', 0) or 0) + 1
                
                reasons.append(
                    f"步骤3：深跌触发 → 回撤 {drawdown_pct:.2f}% 触发深跌档位(≤{abs(matched['threshold'])*100:.0f}%)，"
                    f"释放锁定池 {matched['use_ratio']*100:.0f}% = {deep_dip_release:.2f} 元"
                )
        
        # 高估收割（生产版只输出建议，不实际卖出）
        if take_profit_enabled and holding_value > 1e-8 and ma:
            near_peak = last_price >= last_peak_price * near_peak_ratio
            tp_ok = (nav_bias >= take_profit_bias) and near_peak
            
            if tp_ok:
                if last_tp_date:
                    try:
                        if isinstance(last_tp_date, str):
                            last_tp_date_obj = datetime.strptime(last_tp_date, '%Y-%m-%d').date()
                        else:
                            last_tp_date_obj = last_tp_date
                        if (today - last_tp_date_obj).days < take_profit_cooldown_days:
                            tp_ok = False
                    except Exception:
                        tp_ok = False
            
            if tp_ok:
                sell_amount = holding_value * take_profit_sell_ratio
                # 生产版：只建议，不实际卖出，但更新状态
                new_state['last_tp_date'] = today.strftime('%Y-%m-%d')
                new_state['tp_count'] = int(state.get('tp_count', 0) or 0) + 1
                
                reasons.append(
                    f"步骤4：高估收割建议 → nav_bias={nav_bias*100:.2f}% ≥ {take_profit_bias*100:.2f}%，"
                    f"接近峰值（{near_peak_ratio*100:.1f}%，峰值={last_peak_price:.4f}），建议卖出金额 {sell_amount:.2f}（约占持仓市值 {take_profit_sell_ratio*100:.2f}%）。"
                    f"注意：生产版只输出建议，不自动下单。"
                )
                reasons.append(f"步骤5：技术指标 → {indicator_str}")
                reasons.append(f"最终决策：HOLD（高估收割建议，不自动卖出）")
                # 生产版不返回SELL，而是HOLD并提示
                new_state['locked_pool'] = float(locked_pool)
                new_state['last_action_date'] = today.strftime('%Y-%m-%d')
                
                return AdviceOutput(
                    action='HOLD',
                    suggest_amount=Decimal('0'),
                    suggest_ratio=None,
                    limit_price_hint=None,
                    premium_rate=input_data.premium_rate,
                    moved_to_wait_pool=Decimal('0'),
                    reason='；'.join(reasons) if reasons else '高估收割建议（生产版不自动卖出）',
                    new_state_json=new_state
                )
        
        # 计算买入预算
        base_buy_budget = max(0.0, total_cash_pool - locked_pool)
        buy_budget = base_buy_budget + unlock_release + deep_dip_release
        buy_budget = max(0.0, min(buy_budget, total_cash_pool))
        
        # 更新锁定池状态
        new_state['locked_pool'] = float(locked_pool)
        new_state['last_action_date'] = today.strftime('%Y-%m-%d')
        
        # 检查最小成交额
        bind_config = input_data.bind_config
        min_trade_amount = float(bind_config.get('min_trade_amount', 1000))
        
        # 添加技术指标说明
        if indicator_str:
            reasons.append(f"步骤4：技术指标 → {indicator_str}")
        
        # 计算买入预算
        reasons.append(
            f"步骤5：预算计算 → 总现金池={total_cash_pool:.2f}，锁定池={locked_pool:.2f}，"
            f"基础买入预算={max(0.0, total_cash_pool - locked_pool):.2f}，释放金额={unlock_release + deep_dip_release:.2f}，"
            f"建议买入={buy_budget:.2f}"
        )
        
        if buy_budget < min_trade_amount:
            reasons.append(
                f"步骤6：预算检查 → 建议买入{buy_budget:.2f} < 最小成交额{min_trade_amount:.2f}，差额={min_trade_amount - buy_budget:.2f} → 进入等待池"
            )
            reasons.append(f"最终决策：WAIT（预算不足最小成交额）")
            return AdviceOutput(
                action='WAIT',
                suggest_amount=Decimal('0'),
                suggest_ratio=None,
                limit_price_hint=None,
                premium_rate=input_data.premium_rate,
                moved_to_wait_pool=Decimal(str(buy_budget)),
                reason='；'.join(reasons) if reasons else '预算不足最小成交额',
                new_state_json=new_state
            )
        
        if buy_budget > 1e-6:
            reasons.append(
                f"步骤6：预算检查 → 建议买入{buy_budget:.2f} ≥ 最小成交额{min_trade_amount:.2f} → 通过"
            )
            reasons.append(f"最终决策：BUY（满足买入条件，回撤={drawdown_pct:.2f}%，峰值={last_peak_price:.4f}）")
            return AdviceOutput(
                action='BUY',
                suggest_amount=Decimal(str(buy_budget)),
                suggest_ratio=None,
                limit_price_hint=None,
                premium_rate=input_data.premium_rate,
                moved_to_wait_pool=Decimal('0'),
                reason='；'.join(reasons) if reasons else '满足买入条件',
                new_state_json=new_state
            )
        
        # HOLD
        reasons.append(
            f"步骤6：买入判断 → 当前回撤={drawdown_pct:.2f}%，nav_bias={nav_bias*100:.2f}%：无买入触发。"
            f"锁定池余额={locked_pool:.2f}（等待更好时机/深跌释放）。"
        )
        reasons.append(f"最终决策：HOLD（无买入触发条件）")
        return AdviceOutput(
            action='HOLD',
            suggest_amount=Decimal('0'),
            suggest_ratio=None,
            limit_price_hint=None,
            premium_rate=input_data.premium_rate,
            moved_to_wait_pool=Decimal('0'),
            reason='；'.join(reasons) if reasons else '无买入触发',
            new_state_json=new_state
        )

