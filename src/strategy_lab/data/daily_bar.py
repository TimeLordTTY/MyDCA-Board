# -*- coding: utf-8 -*-
"""
DailyBar 数据结构

日K线标准数据结构，用于回测引擎统一处理。
"""

from dataclasses import dataclass
from datetime import date
from typing import Optional


@dataclass
class DailyBar:
    """
    日K线数据条目
    
    Attributes:
        date: 交易日期 (YYYY-MM-DD)
        open: 开盘价
        high: 最高价
        low: 最低价
        close: 收盘价（回测主要用此价格）
        volume: 成交量（可选）
    """
    date: date
    open: float
    high: float
    low: float
    close: float
    volume: Optional[float] = None
    
    @property
    def price(self) -> float:
        """价格别名（用于兼容，返回 close）"""
        return self.close
    
    @property
    def nav(self) -> float:
        """净值别名（用于兼容场外基金，返回 close）"""
        return self.close




