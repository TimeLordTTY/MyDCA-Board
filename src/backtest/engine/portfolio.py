# -*- coding: utf-8 -*-
"""
组合状态与交易撮合

Portfolio 负责管理基金持仓、现金，以及买卖交易的撮合

【现金单一来源设计】
所有现金统一由 portfolio.cash 管理：
- 初始投入、定投流入 -> portfolio.cash += inflow
- 买入时 -> portfolio.cash -= actual_buy_amount
- 卖出时 -> portfolio.cash += net_sell_amount

不再有 Backtester.cash_pool，避免现金双计。
"""

from dataclasses import dataclass, field
from typing import List
from .types import Trade


class Portfolio:
    """
    投资组合管理器
    
    管理基金持仓、现金余额，处理买卖交易
    
    设计说明（与财富中枢一致）：
    - cash 是唯一的现金来源（初始投入、定投、卖出所得）
    - cost 表示"当前持仓成本"（平均成本法），卖出时按比例减少
    - gross_buy_amount 表示"累计买入金额"（只增不减），用于统计视角
    - gross_sell_amount 表示"累计卖出金额"（只增不减），用于统计视角
    
    字段名与 MyDCA-Board 现有系统保持一致：
        shares: 基金持有份额（对应现有系统 shares）
        cost: 当前持仓成本（对应现有系统 cost，卖出时按比例减少）
        value: 基金持仓市值（对应现有系统 value）
        return_rate: 收益率（对应现有系统 return_rate）
        cash: 现金余额（唯一来源）
    """
    
    def __init__(
        self, 
        cash: float = 0.0, 
        buy_fee_rate: float = 0.0, 
        sell_fee_rate: float = 0.0
    ):
        """
        初始化组合
        
        Args:
            cash: 初始现金
            buy_fee_rate: 买入手续费率（如 0.0015 表示 0.15%）
            sell_fee_rate: 卖出手续费率（如 0.005 表示 0.5%）
        """
        self.cash = cash
        self.units = 0.0
        self.total_cost = 0.0  # 当前持仓成本（卖出时按比例减少）
        self.buy_fee_rate = buy_fee_rate
        self.sell_fee_rate = sell_fee_rate
        
        # 统计字段（只增不减）
        self.gross_buy_amount = 0.0   # 累计买入金额
        self.gross_sell_amount = 0.0  # 累计卖出回笼金额
        
        # 内部状态：当前净值、市值等
        self._current_nav = 0.0
        self._market_value = 0.0
        
        # 交易记录
        self.trades: List[Trade] = []
    
    def buy(self, nav: float, amount_cash: float) -> float:
        """
        买入基金
        
        用指定金额买入基金，扣除买入手续费后计算实际买入份额
        
        【现金扣减逻辑】
        - 若 amount_cash > self.cash，则实际买入金额 = self.cash
        - 买入成功后：self.cash -= actual_amount
        
        计算方式：
        - 实际用于购买的金额 = amount_cash / (1 + buy_fee_rate)
        - 手续费 = amount_cash - 实际用于购买的金额
        - 买入份额 = 实际用于购买的金额 / nav
        
        更新：
        - self.cash -= amount_cash（扣减现金）
        - total_cost += amount_cash（含手续费）
        - gross_buy_amount += amount_cash（只增不减）
        
        Args:
            nav: 当前基金净值
            amount_cash: 打算用于买入的总金额（含手续费）
        
        Returns:
            实际买入的份额数
        """
        if amount_cash <= 0 or nav <= 0:
            return 0.0
        
        # 限制买入金额不超过可用现金
        actual_buy_cash = min(amount_cash, self.cash)
        if actual_buy_cash <= 0:
            return 0.0
        
        # 计算手续费和实际购买金额
        # actual_buy_cash = 实际购买金额 + 手续费 = 实际购买金额 * (1 + fee_rate)
        actual_invest = actual_buy_cash / (1 + self.buy_fee_rate)
        fee = actual_buy_cash - actual_invest
        
        # 计算买入份额
        units_bought = actual_invest / nav
        
        # 更新组合状态
        self.cash -= actual_buy_cash  # 扣减现金
        self.units += units_bought
        self.total_cost += actual_buy_cash  # 持仓成本增加（含手续费）
        self.gross_buy_amount += actual_buy_cash  # 累计买入金额（只增不减）
        
        # 更新市值
        self._current_nav = nav
        self._market_value = self.units * nav
        
        return units_bought
    
    def sell(self, nav: float, sell_units: float) -> tuple:
        """
        卖出基金
        
        卖出指定份额的基金，扣除卖出手续费后返回实际到手金额和实际卖出份额
        
        【现金增加逻辑】
        - self.cash += net_amount（卖出到手金额）
        
        计算方式：
        - 卖出总金额 = sell_units * nav
        - 手续费 = 卖出总金额 * sell_fee_rate
        - 到手金额 = 卖出总金额 - 手续费
        
        成本扣减（与财富中枢一致）：
        - cost_reduce = pre_cost * (sold_units / pre_units)
        - cost = pre_cost - cost_reduce
        
        Args:
            nav: 当前基金净值
            sell_units: 打算卖出的份额
        
        Returns:
            tuple: (实际到手的现金金额, 实际卖出的份额)
        """
        if sell_units <= 0 or nav <= 0:
            return 0.0, 0.0
        
        # 限制卖出份额不超过持有份额
        actual_sell_units = min(sell_units, self.units)
        if actual_sell_units <= 0:
            return 0.0, 0.0
        
        # 保存卖出前状态，用于按比例计算成本扣减
        pre_units = self.units
        pre_cost = self.total_cost
        
        # 计算卖出金额和手续费
        gross_amount = actual_sell_units * nav
        fee = gross_amount * self.sell_fee_rate
        net_amount = gross_amount - fee
        
        # 按比例减少成本（与财富中枢一致）
        if pre_units > 0:
            cost_reduce = pre_cost * (actual_sell_units / pre_units)
            self.total_cost = pre_cost - cost_reduce
        else:
            cost_reduce = 0.0
        
        # 更新组合状态
        self.units -= actual_sell_units
        self.cash += net_amount  # 卖出所得加入现金
        
        # 更新累计卖出金额（只增不减，使用到手金额）
        self.gross_sell_amount += net_amount
        
        # 更新市值
        self._current_nav = nav
        self._market_value = self.units * nav
        
        return net_amount, actual_sell_units
    
    def update_valuation(self, nav: float) -> None:
        """
        更新组合估值
        
        根据当前净值更新市值等估值数据，不进行任何交易
        
        Args:
            nav: 当前基金净值
        """
        self._current_nav = nav
        self._market_value = self.units * nav
    
    @property
    def market_value(self) -> float:
        """基金持仓市值"""
        return self._market_value
    
    @property
    def shares(self) -> float:
        """持有份额（统一字段名，与现有系统一致）"""
        return self.units
    
    @property
    def value(self) -> float:
        """市值（统一字段名，与现有系统一致）"""
        return self._market_value
    
    @property
    def cost(self) -> float:
        """当前持仓成本（统一字段名，与现有系统一致，卖出时按比例减少）"""
        return self.total_cost
    
    @property
    def return_rate(self) -> float:
        """收益率（统一字段名，与现有系统一致）"""
        return self.unrealized_pnl_pct
    
    @property
    def net_invested(self) -> float:
        """净投入金额 = 累计买入金额 - 累计卖出金额"""
        return self.gross_buy_amount - self.gross_sell_amount
    
    @property
    def total_value(self) -> float:
        """
        总资产 = 持仓市值 + 现金
        
        现金单一来源：portfolio.cash 是唯一的现金字段
        """
        return self._market_value + self.cash
    
    @property
    def unrealized_pnl(self) -> float:
        """
        浮动盈亏（绝对值）
        
        计算方式：当前市值 - 当前持仓成本
        
        说明：
        - cost 会随卖出按比例减少
        - 所以这个公式反映的是"当前持仓的浮盈"
        """
        return self._market_value - self.total_cost
    
    @property
    def unrealized_pnl_pct(self) -> float:
        """
        浮动盈亏比例
        
        计算方式：浮动盈亏 / 当前持仓成本
        
        Returns:
            盈亏比例，如 0.1 表示 10% 收益
        """
        if self.total_cost <= 0:
            return 0.0
        return self.unrealized_pnl / self.total_cost
    
    def get_state_snapshot(self) -> dict:
        """
        获取当前组合状态快照
        
        Returns:
            包含组合所有关键指标的字典（使用统一字段名）
        """
        return {
            'shares': self.shares,
            'cash': self.cash,
            'cost': self.cost,
            'value': self.value,
            'total_value': self.total_value,
            'unrealized_pnl': self.unrealized_pnl,
            'return_rate': self.return_rate,
            'nav': self._current_nav,
            'gross_buy_amount': self.gross_buy_amount,
            'gross_sell_amount': self.gross_sell_amount,
            'net_invested': self.net_invested,
        }
    
    def get(self, key: str, default=None):
        """
        字典式获取属性值（用于兼容旧策略）
        
        支持旧策略使用 portfolio.get("units", 0.0) 这种访问方式
        """
        if hasattr(self, key):
            return getattr(self, key)
        return default
