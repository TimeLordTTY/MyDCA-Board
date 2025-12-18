# -*- coding: utf-8 -*-
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
    
    【持仓指标】只反映持仓部分，不包含现金池：
        shares: 持有份额
        value: 持仓市值 = shares × nav
        cost: 当前持仓成本（卖出时按比例减少，与财富中枢一致）
        unrealized_pnl: 持仓浮盈 = value - cost
        return_rate: 持仓收益率 = unrealized_pnl / cost
    
    【总资产指标】反映持仓 + 现金的完整情况：
        cash: 现金池余额
        total_value: 总资产 = value + cash
    
    【统计字段】（只增不减）：
        gross_buy_amount: 累计买入金额
        gross_sell_amount: 累计卖出回笼金额
    
    其他字段：
        date: 日期（交易日）
        nav: 当日净值
        buy_cash: 当日买入金额
        sell_cash: 当日卖出到账金额
        buy_shares: 当日买入份额
        sell_shares: 当日卖出份额
        note: 备注信息
    """
    date: datetime
    nav: float
    shares: float          # 持有份额
    value: float           # 持仓市值（不含现金）
    cash: float            # 现金池余额
    total_value: float     # 总资产 = value + cash
    cost: float            # 当前持仓成本（卖出时按比例减少）
    unrealized_pnl: float  # 持仓浮盈 = value - cost（不含现金）
    return_rate: float     # 持仓收益率 = unrealized_pnl / cost
    buy_cash: float = 0.0
    sell_cash: float = 0.0
    buy_shares: float = 0.0
    sell_shares: float = 0.0
    gross_buy_amount: float = 0.0   # 累计买入金额（只增不减）
    gross_sell_amount: float = 0.0  # 累计卖出回笼金额（只增不减）
    note: str = ""
