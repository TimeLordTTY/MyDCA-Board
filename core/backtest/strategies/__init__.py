"""
策略模块

包含策略基类、策略注册表和所有策略实现
"""

from .base import Strategy, Context, Signal
from .registry import STRATEGY_REGISTRY, register_strategy
from .profit_recycle import ProfitRecycleStrategy
from .pure_sip import PureSipStrategy
from .ma_enhanced import MovingAverageEnhancedStrategy

# 注册所有策略
STRATEGY_REGISTRY.register("profit_recycle", ProfitRecycleStrategy, version="v10", set_as_default=True)
STRATEGY_REGISTRY.register("pure_sip", PureSipStrategy, version="default", set_as_default=True)
STRATEGY_REGISTRY.register("ma_enhanced", MovingAverageEnhancedStrategy, version="v2", set_as_default=True)

__all__ = [
    'Strategy',
    'Context',
    'Signal',
    'STRATEGY_REGISTRY',
    'register_strategy',
    'ProfitRecycleStrategy',
    'PureSipStrategy',
    'MovingAverageEnhancedStrategy',
]
