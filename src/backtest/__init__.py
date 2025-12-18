# -*- coding: utf-8 -*-
"""
回测引擎模块

从 fund 项目移植的策略回测引擎，适配 MyDCA-Board 的数据格式。
"""

from .engine import DataFeed, Portfolio, Backtester
from .engine.types import NavBar, DayResult, Order, Trade

__all__ = [
    'DataFeed',
    'Portfolio', 
    'Backtester',
    'NavBar',
    'DayResult',
    'Order',
    'Trade',
]

