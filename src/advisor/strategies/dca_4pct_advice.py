# -*- coding: utf-8 -*-
"""
Dca4PctAdvice - 4%定投策略生产建议版

基于回撤档位触发买入，支持分位门控（VETO）。
"""
import logging
from decimal import Decimal
from typing import Dict, Any, Optional, List
from datetime import date, timedelta

from ..strategy_interface import StrategyAdviceInterface, AdviceInput, AdviceOutput
from data.db_connector import execute_query, execute_one

logger = logging.getLogger(__name__)


class Dca4PctAdvice(StrategyAdviceInterface):
    """4%定投策略生产建议版"""
    
    strategy_type = 'TRIGGER'  # 默认是TRIGGER层，如果设置了percentile_gate则可以作为VETO
    
    def evaluate(self, input_data: AdviceInput) -> AdviceOutput:
        """
        评估并输出建议
        
        参数来自 param_json，例如：
        {
          "anchor_window_days": 60,
          "drawdown_levels": [0.04, 0.08, 0.12],
          "amount_levels": [1000, 2000, 3000],
          "percentile_gate": 0.7,  # 可选，pct_rank > 0.7 则 veto
          "max_buy_per_day": 5000,  # 可选
          "use_wait_pool_first": true  # 可选，是否优先消耗等待池
        }
        """
        params = input_data.param_json
        anchor_window_days = int(params.get('anchor_window_days', 60))
        drawdown_levels = params.get('drawdown_levels', [0.04, 0.08, 0.12])
        amount_levels = params.get('amount_levels', [1000, 2000, 3000])
        percentile_gate = params.get('percentile_gate')  # 可选
        max_buy_per_day = float(params.get('max_buy_per_day', 10000))
        use_wait_pool_first = params.get('use_wait_pool_first', False)
        
        bind_config = input_data.bind_config
        min_trade_amount = float(bind_config.get('min_trade_amount', 1000))
        ideal_trade_amount = float(bind_config.get('ideal_trade_amount', 2000))
        
        last_price = float(input_data.last_price)
        indicator = input_data.indicator or {}
        budget_amount = float(input_data.budget_amount + input_data.pending_amount)
        
        logger.debug(
            f"Dca4PctAdvice: product_id={input_data.product_id}, "
            f"budget_amount={input_data.budget_amount:.2f}, "
            f"pending_amount={input_data.pending_amount:.2f}, "
            f"total={budget_amount:.2f}"
        )
        
        # ========== VETO检查：分位门控 ==========
        if percentile_gate is not None:
            pct_rank = indicator.get('pct_rank')
            if pct_rank is not None:
                pct_rank_float = float(pct_rank)
                if pct_rank_float > percentile_gate:
                    reason = (
                        f"分位门控触发：当前分位排名={pct_rank_float*100:.1f}%，"
                        f"超过阈值{percentile_gate*100:.0f}%，拒绝买入。"
                        f"预算={budget_amount:.2f}已进入等待池。"
                    )
                    logger.info(f"Dca4PctAdvice: 分位门控触发，拒绝买入")
                    return AdviceOutput(
                        action='WAIT',
                        suggest_amount=Decimal('0'),
                        suggest_ratio=None,
                        limit_price_hint=None,
                        premium_rate=input_data.premium_rate,
                        moved_to_wait_pool=Decimal(str(budget_amount)),
                        reason=reason,
                        new_state_json=None
                    )
        
        # ========== 计算回撤 ==========
        # 1. 获取锚点窗口内的峰值价格（使用昨日日K）
        yesterday = date.today() - timedelta(days=1)
        peak_close = self._get_peak_close(input_data.product_id, yesterday, anchor_window_days)
        
        if peak_close is None:
            reason = (
                f"数据不足：无法计算峰值价格（需要至少{anchor_window_days}个交易日的历史数据）。"
                f"预算={budget_amount:.2f}已进入等待池。"
            )
            logger.warning(f"Dca4PctAdvice: 无法计算峰值价格")
            return AdviceOutput(
                action='WAIT',
                suggest_amount=Decimal('0'),
                suggest_ratio=None,
                limit_price_hint=None,
                premium_rate=input_data.premium_rate,
                moved_to_wait_pool=Decimal(str(budget_amount)),
                reason=reason,
                new_state_json=None
            )
        
        # 2. 计算当前回撤（使用实时价格）
        if peak_close <= 0:
            reason = f"峰值价格异常：peak_close={peak_close}，无法计算回撤。"
            logger.warning(f"Dca4PctAdvice: 峰值价格异常")
            return AdviceOutput(
                action='WAIT',
                suggest_amount=Decimal('0'),
                suggest_ratio=None,
                limit_price_hint=None,
                premium_rate=input_data.premium_rate,
                moved_to_wait_pool=Decimal(str(budget_amount)),
                reason=reason,
                new_state_json=None
            )
        
        drawdown = 1.0 - (last_price / peak_close)
        
        logger.debug(
            f"Dca4PctAdvice: peak_close={peak_close:.4f}, last_price={last_price:.4f}, "
            f"drawdown={drawdown*100:.2f}%"
        )
        
        # ========== 匹配回撤档位 ==========
        suggested_amount = 0.0
        matched_level = None
        
        # 从高到低匹配回撤档位（回撤越大，买入金额越大）
        for i in range(len(drawdown_levels) - 1, -1, -1):
            level_dd = float(drawdown_levels[i])
            if drawdown >= level_dd:
                suggested_amount = float(amount_levels[i]) if i < len(amount_levels) else float(amount_levels[-1])
                matched_level = i
                break
        
        if suggested_amount == 0:
            # 未达到任何回撤档位
            reason = (
                f"回撤未达标：当前回撤={drawdown*100:.2f}%，"
                f"最低档位={drawdown_levels[0]*100:.0f}%，未触发买入条件。"
            )
            logger.debug(f"Dca4PctAdvice: 回撤未达标")
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
        
        # ========== 应用约束 ==========
        # 1. 不超过max_buy_per_day
        if suggested_amount > max_buy_per_day:
            suggested_amount = max_buy_per_day
            logger.debug(f"Dca4PctAdvice: 限制为max_buy_per_day={max_buy_per_day}")
        
        # 2. 不超过可用预算
        if suggested_amount > budget_amount:
            suggested_amount = budget_amount
            logger.debug(f"Dca4PctAdvice: 限制为可用预算={budget_amount}")
        
        # 3. 必须满足最小成交额
        if suggested_amount < min_trade_amount:
            reason = (
                f"预算不足最小成交额：建议金额={suggested_amount:.2f}，"
                f"最小成交额={min_trade_amount:.2f}。"
                f"预算={budget_amount:.2f}已进入等待池。"
            )
            logger.debug(f"Dca4PctAdvice: 预算不足最小成交额")
            return AdviceOutput(
                action='WAIT',
                suggest_amount=Decimal('0'),
                suggest_ratio=None,
                limit_price_hint=None,
                premium_rate=input_data.premium_rate,
                moved_to_wait_pool=Decimal(str(budget_amount)),
                reason=reason,
                new_state_json=None
            )
        
        # ========== 构建原因说明 ==========
        reason_parts = [
            f"4%定投策略触发：回撤={drawdown*100:.2f}%，达到档位{matched_level+1}（阈值={drawdown_levels[matched_level]*100:.0f}%），"
            f"建议买入金额={suggested_amount:.2f}。",
            f"峰值价格={peak_close:.4f}，当前价格={last_price:.4f}。",
            f"预算={budget_amount:.2f}，可用={suggested_amount:.2f}。"
        ]
        
        if percentile_gate is not None:
            pct_rank = indicator.get('pct_rank')
            if pct_rank is not None:
                reason_parts.append(f"分位排名={float(pct_rank)*100:.1f}%，未超过门控阈值{percentile_gate*100:.0f}%。")
        
        reason = "；".join(reason_parts)
        
        # ========== 计算转入等待池的金额 ==========
        moved_to_wait = Decimal(str(max(0.0, budget_amount - suggested_amount)))
        
        return AdviceOutput(
            action='BUY',
            suggest_amount=Decimal(str(suggested_amount)),
            suggest_ratio=Decimal(str(suggested_amount / budget_amount)) if budget_amount > 0 else None,
            limit_price_hint=None,
            premium_rate=input_data.premium_rate,
            moved_to_wait_pool=moved_to_wait,
            reason=reason,
            new_state_json=None
        )
    
    def _get_peak_close(self, product_id: int, trade_date: date, window_days: int) -> Optional[float]:
        """
        获取锚点窗口内的峰值收盘价
        
        Args:
            product_id: 产品ID
            trade_date: 交易日期（通常是昨天）
            window_days: 窗口天数
            
        Returns:
            峰值收盘价，None表示数据不足
        """
        sql = """
            SELECT close_price
            FROM market_bar_d
            WHERE product_id = %s
              AND trade_date < %s
              AND trade_date >= DATE_SUB(%s, INTERVAL %s DAY)
            ORDER BY trade_date ASC
        """
        rows = execute_query(sql, (product_id, trade_date, trade_date, window_days))
        
        if not rows:
            return None
        
        closes = [float(row['close_price']) for row in rows if row.get('close_price')]
        if not closes:
            return None
        
        return max(closes)

