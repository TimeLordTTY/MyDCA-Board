# -*- coding: utf-8 -*-
"""
Decision - 策略决策数据结构

策略输出决策，包含买入/持有建议、金额、原因等。
"""

from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class Decision:
    """
    策略决策
    
    Attributes:
        action: 操作类型 (BUY / HOLD)
        target_amount: 本次希望投入的金额（建议值，不保证成交）
        reasons: 决策原因列表（必须写清楚为什么）
        tags: 可选标签（如 "premium_brake", "drawdown_4%"）
    """
    action: str  # BUY / HOLD
    target_amount: float
    reasons: List[str]
    tags: Optional[List[str]] = None
    
    def __post_init__(self):
        """初始化后处理"""
        if self.tags is None:
            self.tags = []
        if not isinstance(self.reasons, list):
            self.reasons = [str(self.reasons)] if self.reasons else []

