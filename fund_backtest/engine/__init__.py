"""
回测引擎核心模块

包含:
- types: 通用数据结构定义
- data_feed: 行情数据读取与迭代
- portfolio: 组合状态与交易撮合
- backtester: 回测主循环
"""

from .types import NavBar, DayResult, Order, Trade
from .data_feed import DataFeed
from .portfolio import Portfolio
from .backtester import Backtester

__all__ = [
    'NavBar',
    'DayResult',
    'Order',
    'Trade',
    'DataFeed',
    'Portfolio',
    'Backtester',
]

