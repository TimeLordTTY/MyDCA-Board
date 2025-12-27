# -*- coding: utf-8 -*-
"""
Context - 策略上下文

每个交易日传递给策略的上下文信息，包含行情、持仓、资金等。
"""

from dataclasses import dataclass, field
from datetime import date
from typing import Dict, Any, Optional

from ..data.daily_bar import DailyBar


@dataclass
class Context:
    """
    策略上下文
    
    引擎在每个交易日构造 Context 并传递给策略的 on_day 方法
    
    Attributes:
        date: 当前日期
        bar: 日K数据（包含 OHLC）
        cash_pool: 可用现金池
        wait_pool: 等待池（因溢价/条件不满足暂存）
        premium_rate: QDII溢价率（如果有）
        holdings: 持仓信息（shares, cost, value等）
        state: 策略自用状态字典，可读写
    """
    date: date
    bar: DailyBar
    cash_pool: float
    wait_pool: float
    premium_rate: Optional[float] = None
    holdings: Dict[str, Any] = field(default_factory=dict)
    state: Dict[str, Any] = field(default_factory=dict)
    
    @property
    def price(self) -> float:
        """价格别名（返回 close）"""
        return self.bar.close
    
    @property
    def nav(self) -> float:
        """净值别名（返回 close，用于兼容）"""
        return self.bar.close
    
    @property
    def close(self) -> float:
        """收盘价"""
        return self.bar.close
    
    @property
    def total_equity(self) -> float:
        """总净资产 = cash_pool + wait_pool + 持仓市值"""
        holdings_value = self.holdings.get('value', 0.0)
        return self.cash_pool + self.wait_pool + holdings_value




