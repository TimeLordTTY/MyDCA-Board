# -*- coding: utf-8 -*-
"""
策略接口定义 - 生产建议版

统一输入输出格式，与回测引擎分离。
"""
from dataclasses import dataclass
from typing import Optional, Dict, Any
from datetime import datetime
from decimal import Decimal


@dataclass
class AdviceInput:
    """策略输入"""
    product_id: int
    as_of_time: datetime
    last_price: Decimal  # market_quote_rt
    prev_close: Decimal  # 从 market_bar_d 昨日close读
    premium_rate: Optional[Decimal]  # qdii_premium_rt 或 quote里
    indicator: Optional[Dict[str, Any]]  # indicator_daily 最新一条（<=昨日）
    holding: Optional[Dict[str, Any]]  # 份额/成本/市值（复用现有持仓服务读）
    budget_amount: Decimal  # 今日预算（来自：task_dca 或 account_pool_rules 计算）
    pending_amount: Decimal  # pending_buy_pool 里该产品累计的待买入
    bind_config: Dict[str, Any]  # product_strategy_bind（含 min_trade_amount/fee规则）
    param_json: Dict[str, Any]  # strategy_config.params_json
    state_json: Optional[Dict[str, Any]]  # strategy_state.state_json（profit_recycle需要）


@dataclass
class AdviceOutput:
    """策略输出"""
    action: str  # BUY/HOLD/WAIT
    suggest_amount: Decimal
    suggest_ratio: Optional[Decimal]  # 0.5=半买
    limit_price_hint: Optional[Decimal]
    premium_rate: Optional[Decimal]
    moved_to_wait_pool: Decimal
    reason: str  # 必须中文可读，>=30字
    new_state_json: Optional[Dict[str, Any]]  # 若状态变化则返回


class StrategyAdviceInterface:
    """策略建议接口（生产版）"""
    
    # 策略类型：VETO=否决层，TRIGGER=触发层，SCORE=强度层
    strategy_type: str = 'TRIGGER'
    
    def evaluate(self, input_data: AdviceInput) -> AdviceOutput:
        """
        评估并输出建议
        
        Args:
            input_data: 输入数据
            
        Returns:
            AdviceOutput: 建议输出
        """
        raise NotImplementedError("子类必须实现 evaluate 方法")


