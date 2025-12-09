"""
通用数据结构定义

包含回测引擎所需的所有核心数据类型
"""

from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional


@dataclass
class NavBar:
    """
    基金净值数据条目
    
    Attributes:
        date: 净值日期
        nav: 单位净值
    """
    date: datetime
    nav: float


@dataclass
class Order:
    """
    订单结构
    
    Attributes:
        date: 订单日期
        direction: 方向 ('BUY' 或 'SELL')
        nav: 成交净值
        amount: 买入金额(BUY) 或 卖出份额(SELL)
        fee: 手续费
    """
    date: datetime
    direction: str  # 'BUY' or 'SELL'
    nav: float
    amount: float
    fee: float


@dataclass
class Trade:
    """
    成交记录
    
    Attributes:
        date: 成交日期
        direction: 方向 ('BUY' 或 'SELL')
        nav: 成交净值
        units: 成交份额
        cash: 成交金额
        fee: 手续费
    """
    date: datetime
    direction: str
    nav: float
    units: float
    cash: float
    fee: float


@dataclass
class DayResult:
    """
    每日回测结果
    
    记录每个交易日结束后的组合状态和交易情况
    
    Attributes:
        date: 日期
        nav: 当日净值
        units: 持有份额
        fund_value: 基金市值
        cash: 现金池余额
        total_value: 总资产(市值+现金)
        total_cost: 累计投入本金
        unrealized_pnl: 浮动盈亏
        unrealized_pnl_pct: 浮动盈亏比例
        buy_cash: 当日买入金额
        sell_cash: 当日卖出到账金额
        buy_units: 当日买入份额
        sell_units: 当日卖出份额
        note: 备注信息
    """
    date: datetime
    nav: float
    units: float
    fund_value: float
    cash: float
    total_value: float
    total_cost: float
    unrealized_pnl: float
    unrealized_pnl_pct: float
    buy_cash: float = 0.0
    sell_cash: float = 0.0
    buy_units: float = 0.0
    sell_units: float = 0.0
    note: str = ""

