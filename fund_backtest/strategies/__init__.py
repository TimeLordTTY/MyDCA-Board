"""
策略模块

包含策略基类和示例策略实现
"""

from .base import Strategy, Context, Signal
from .sample_sip import SipStrategy
from .sample_tp_dip import TpDipStrategy

__all__ = [
    'Strategy',
    'Context',
    'Signal',
    'SipStrategy',
    'TpDipStrategy',
]

