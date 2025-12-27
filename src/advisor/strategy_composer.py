# -*- coding: utf-8 -*-
"""
策略组合器 - 三层结构（VETO/TRIGGER/SCORE）

执行顺序：
1. VETO层：任一命中 → 直接返回WAIT/HOLD/SKIP
2. TRIGGER层：任一命中 → 进入SCORE层
3. SCORE层：计算建议金额档位
"""
import logging
from typing import List, Dict, Any, Optional
from decimal import Decimal

from .strategy_interface import AdviceInput, AdviceOutput

logger = logging.getLogger(__name__)


class StrategyComposer:
    """策略组合器"""
    
    @staticmethod
    def compose(
        binds: List[Dict[str, Any]],
        input_data: AdviceInput,
        product: Dict[str, Any]
    ) -> AdviceOutput:
        """
        组合多个策略生成建议
        
        Args:
            binds: 产品绑定的所有策略配置列表（已按strategy_type和priority排序）
            input_data: 策略输入
            product: 产品信息
            
        Returns:
            AdviceOutput: 组合后的建议输出
        """
        if not binds:
            logger.warning(f"产品 {input_data.product_id} 未绑定任何策略")
            return AdviceOutput(
                action='WAIT',
                suggest_amount=Decimal('0'),
                suggest_ratio=None,
                limit_price_hint=None,
                premium_rate=input_data.premium_rate,
                moved_to_wait_pool=input_data.budget_amount + input_data.pending_amount,
                reason="未绑定任何策略，资金进入等待池。",
                new_state_json=None
            )
        
        # 按策略类型分组
        veto_strategies = [b for b in binds if b.get('strategy_type') == 'VETO']
        trigger_strategies = [b for b in binds if b.get('strategy_type') == 'TRIGGER']
        score_strategies = [b for b in binds if b.get('strategy_type') == 'SCORE']
        
        logger.debug(
            f"产品 {input_data.product_id} 策略分组: "
            f"VETO={len(veto_strategies)}, TRIGGER={len(trigger_strategies)}, SCORE={len(score_strategies)}"
        )
        
        # ========== 第1层：VETO层 ==========
        # 遍历所有VETO策略，任一命中 → 直接返回WAIT
        for bind in veto_strategies:
            strategy_code = bind['strategy_code']
            try:
                # 延迟导入避免循环依赖
                from .advisor_service import get_strategy_advice_instance
                strategy_instance = get_strategy_advice_instance(strategy_code)
                
                # 读取该策略的参数配置
                from .advisor_service import get_strategy_config
                param_json = get_strategy_config(bind['strategy_code'], bind['param_set_id'])
                if not param_json:
                    logger.warning(f"策略参数未找到: strategy_code={bind['strategy_code']}, param_set_id={bind['param_set_id']}")
                    continue
                
                # 读取该策略的状态
                from .repos.strategy_state_repo import get_state
                state_row = get_state(input_data.product_id, bind['strategy_code'])
                state_json = None
                if state_row and state_row.get('state_json'):
                    state_json = state_row['state_json']
                
                # 构建该策略的输入（使用该策略的bind_config）
                strategy_input = AdviceInput(
                    product_id=input_data.product_id,
                    as_of_time=input_data.as_of_time,
                    last_price=input_data.last_price,
                    prev_close=input_data.prev_close,
                    premium_rate=input_data.premium_rate,
                    indicator=input_data.indicator,
                    holding=input_data.holding,
                    budget_amount=input_data.budget_amount,
                    pending_amount=input_data.pending_amount,
                    bind_config=bind,  # 使用该策略的bind_config
                    param_json=param_json,  # 从strategy_config读取
                    state_json=state_json  # 从strategy_state读取
                )
                
                output = strategy_instance.evaluate(strategy_input)
                
                # VETO层命中：如果action是WAIT/HOLD/SKIP，直接返回
                if output.action in ['WAIT', 'HOLD', 'SKIP']:
                    logger.info(
                        f"VETO层策略 {strategy_code} 命中: action={output.action}, "
                        f"reason={output.reason[:50]}..."
                    )
                    return output
                    
            except Exception as e:
                logger.error(f"执行VETO策略 {strategy_code} 失败: {e}", exc_info=True)
                continue
        
        # ========== 第2层：TRIGGER层 ==========
        # 遍历所有TRIGGER策略，任一命中 → 进入SCORE层
        trigger_hit = False
        trigger_outputs = []
        
        for bind in trigger_strategies:
            strategy_code = bind['strategy_code']
            try:
                # 延迟导入避免循环依赖
                from .advisor_service import get_strategy_advice_instance
                strategy_instance = get_strategy_advice_instance(strategy_code)
                
                # 读取该策略的参数配置
                from .advisor_service import get_strategy_config
                param_json = get_strategy_config(bind['strategy_code'], bind['param_set_id'])
                if not param_json:
                    logger.warning(f"策略参数未找到: strategy_code={bind['strategy_code']}, param_set_id={bind['param_set_id']}")
                    continue
                
                # 读取该策略的状态
                from .repos.strategy_state_repo import get_state
                state_row = get_state(input_data.product_id, bind['strategy_code'])
                state_json = None
                if state_row and state_row.get('state_json'):
                    state_json = state_row['state_json']
                
                # 构建该策略的输入
                strategy_input = AdviceInput(
                    product_id=input_data.product_id,
                    as_of_time=input_data.as_of_time,
                    last_price=input_data.last_price,
                    prev_close=input_data.prev_close,
                    premium_rate=input_data.premium_rate,
                    indicator=input_data.indicator,
                    holding=input_data.holding,
                    budget_amount=input_data.budget_amount,
                    pending_amount=input_data.pending_amount,
                    bind_config=bind,
                    param_json=param_json,
                    state_json=state_json
                )
                
                output = strategy_instance.evaluate(strategy_input)
                trigger_outputs.append((strategy_code, output))
                
                # TRIGGER层命中：如果action是BUY，标记为命中
                if output.action == 'BUY':
                    trigger_hit = True
                    logger.debug(f"TRIGGER层策略 {strategy_code} 命中: action=BUY")
                    
            except Exception as e:
                logger.error(f"执行TRIGGER策略 {strategy_code} 失败: {e}", exc_info=True)
                continue
        
        # 如果TRIGGER层未命中，返回HOLD
        if not trigger_hit:
            logger.debug(f"TRIGGER层未命中，返回HOLD")
            # 合并所有TRIGGER策略的reason
            reasons = [f"{code}: {out.reason}" for code, out in trigger_outputs]
            combined_reason = "；".join(reasons) if reasons else "TRIGGER层未命中任何买入条件。"
            
            return AdviceOutput(
                action='HOLD',
                suggest_amount=Decimal('0'),
                suggest_ratio=None,
                limit_price_hint=None,
                premium_rate=input_data.premium_rate,
                moved_to_wait_pool=Decimal('0'),
                reason=combined_reason,
                new_state_json=None
            )
        
        # ========== 第3层：SCORE层 ==========
        # 遍历所有SCORE策略，计算建议金额档位，取最大建议金额
        max_suggest_amount = Decimal('0')
        best_output: Optional[AdviceOutput] = None
        score_reasons = []
        
        for bind in score_strategies:
            strategy_code = bind['strategy_code']
            try:
                # 延迟导入避免循环依赖
                from .advisor_service import get_strategy_advice_instance
                strategy_instance = get_strategy_advice_instance(strategy_code)
                
                # 读取该策略的参数配置
                from .advisor_service import get_strategy_config
                param_json = get_strategy_config(bind['strategy_code'], bind['param_set_id'])
                if not param_json:
                    logger.warning(f"策略参数未找到: strategy_code={bind['strategy_code']}, param_set_id={bind['param_set_id']}")
                    continue
                
                # 读取该策略的状态
                from .repos.strategy_state_repo import get_state
                state_row = get_state(input_data.product_id, bind['strategy_code'])
                state_json = None
                if state_row and state_row.get('state_json'):
                    state_json = state_row['state_json']
                
                # 构建该策略的输入
                strategy_input = AdviceInput(
                    product_id=input_data.product_id,
                    as_of_time=input_data.as_of_time,
                    last_price=input_data.last_price,
                    prev_close=input_data.prev_close,
                    premium_rate=input_data.premium_rate,
                    indicator=input_data.indicator,
                    holding=input_data.holding,
                    budget_amount=input_data.budget_amount,
                    pending_amount=input_data.pending_amount,
                    bind_config=bind,
                    param_json=param_json,
                    state_json=state_json
                )
                
                output = strategy_instance.evaluate(strategy_input)
                
                # SCORE层：取最大建议金额
                if output.action == 'BUY' and output.suggest_amount > max_suggest_amount:
                    max_suggest_amount = output.suggest_amount
                    best_output = output
                    score_reasons.append(f"{strategy_code}: {output.reason}")
                    
            except Exception as e:
                logger.error(f"执行SCORE策略 {strategy_code} 失败: {e}", exc_info=True)
                continue
        
        # 如果SCORE层有输出，返回最佳输出
        if best_output:
            logger.debug(f"SCORE层最佳建议: amount={max_suggest_amount}, strategy={best_output}")
            # 合并reason
            if score_reasons:
                best_output.reason = "；".join(score_reasons)
            return best_output
        
        # 如果SCORE层没有输出，使用TRIGGER层的第一个BUY输出
        for strategy_code, output in trigger_outputs:
            if output.action == 'BUY':
                logger.debug(f"使用TRIGGER层策略 {strategy_code} 的输出作为最终建议")
                return output
        
        # 兜底：返回HOLD
        logger.warning(f"所有策略层都未产生有效建议，返回HOLD")
        return AdviceOutput(
            action='HOLD',
            suggest_amount=Decimal('0'),
            suggest_ratio=None,
            limit_price_hint=None,
            premium_rate=input_data.premium_rate,
            moved_to_wait_pool=Decimal('0'),
            reason="所有策略层都未产生有效建议。",
            new_state_json=None
        )

