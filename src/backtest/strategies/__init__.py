# -*- coding: utf-8 -*-
"""
策略模块

包含所有可用的定投策略
"""

from .base import Strategy, Context, Signal
from .registry import STRATEGY_REGISTRY, register_strategy
from .pure_sip import PureSipStrategy
from .profit_recycle_v11 import ProfitRecycleStrategy
from .profit_recycle import ProfitRecycleStrategyV10
from .ma_enhanced import MovingAverageEnhancedStrategy

# 导入策略以触发注册
from . import pure_sip
from . import profit_recycle_v11
from . import profit_recycle
from . import ma_enhanced

__all__ = [
    'Strategy',
    'Context', 
    'Signal',
    'STRATEGY_REGISTRY',
    'register_strategy',
    'PureSipStrategy',
    'ProfitRecycleStrategy',
    'ProfitRecycleStrategyV10',
    'MovingAverageEnhancedStrategy',
]

