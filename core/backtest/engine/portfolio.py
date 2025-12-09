"""
组合状态与交易撮合

Portfolio 负责管理基金持仓、现金，以及买卖交易的撮合
"""

from dataclasses import dataclass, field
from typing import List
from .types import Trade


class Portfolio:
    """
    投资组合管理器
    
    管理基金持仓、现金余额，处理买卖交易
    
    设计说明：
    - total_cost 记录累计投入本金，买入时增加，卖出时不减少
    - 这样设计是为了计算"相对于总投入本金的收益率"
    - 如果需要计算"相对于当前持仓成本的收益率"，可以另外维护一个变量
    
    Attributes:
        cash: 现金余额（注意：这是组合内部现金，与回测的 cash_pool 不同）
        units: 基金持有份额
        total_cost: 累计投入本金
        buy_fee_rate: 买入手续费率
        sell_fee_rate: 卖出手续费率
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
        self.total_cost = 0.0
        self.buy_fee_rate = buy_fee_rate
        self.sell_fee_rate = sell_fee_rate
        
        # 内部状态：当前净值、市值等
        self._current_nav = 0.0
        self._market_value = 0.0
        
        # 交易记录
        self.trades: List[Trade] = []
    
    def buy(self, nav: float, amount_cash: float) -> float:
        """
        买入基金
        
        用指定金额买入基金，扣除买入手续费后计算实际买入份额
        
        计算方式：
        - 实际用于购买的金额 = amount_cash / (1 + buy_fee_rate)
        - 手续费 = amount_cash - 实际用于购买的金额
        - 买入份额 = 实际用于购买的金额 / nav
        
        Args:
            nav: 当前基金净值
            amount_cash: 打算用于买入的总金额（含手续费）
        
        Returns:
            实际买入的份额数
        """
        if amount_cash <= 0 or nav <= 0:
            return 0.0
        
        # 计算手续费和实际购买金额
        # amount_cash = 实际购买金额 + 手续费 = 实际购买金额 * (1 + fee_rate)
        actual_invest = amount_cash / (1 + self.buy_fee_rate)
        fee = amount_cash - actual_invest
        
        # 计算买入份额
        units_bought = actual_invest / nav
        
        # 更新组合状态
        self.units += units_bought
        self.total_cost += amount_cash  # 总成本包含手续费
        
        # 更新市值
        self._current_nav = nav
        self._market_value = self.units * nav
        
        return units_bought
    
    def sell(self, nav: float, sell_units: float) -> float:
        """
        卖出基金
        
        卖出指定份额的基金，扣除卖出手续费后返回实际到手金额
        
        计算方式：
        - 卖出总金额 = sell_units * nav
        - 手续费 = 卖出总金额 * sell_fee_rate
        - 到手金额 = 卖出总金额 - 手续费
        
        注意：卖出不会减少 total_cost
        
        Args:
            nav: 当前基金净值
            sell_units: 打算卖出的份额
        
        Returns:
            实际到手的现金金额
        """
        if sell_units <= 0 or nav <= 0:
            return 0.0
        
        # 限制卖出份额不超过持有份额
        actual_sell_units = min(sell_units, self.units)
        if actual_sell_units <= 0:
            return 0.0
        
        # 计算卖出金额和手续费
        gross_amount = actual_sell_units * nav
        fee = gross_amount * self.sell_fee_rate
        net_amount = gross_amount - fee
        
        # 更新组合状态
        self.units -= actual_sell_units
        self.cash += net_amount
        
        # 更新市值
        self._current_nav = nav
        self._market_value = self.units * nav
        
        return net_amount
    
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
    def total_value(self) -> float:
        """
        总资产
        
        注意：这里的 cash 是组合内部现金（卖出后的现金）
        回测时的 cash_pool（待投资现金）不包含在这里
        """
        return self._market_value + self.cash
    
    @property
    def unrealized_pnl(self) -> float:
        """
        浮动盈亏（绝对值）
        
        计算方式：当前市值 + 组合内现金 - 累计投入本金
        
        说明：
        - 如果有卖出，卖出所得在 self.cash 中
        - total_cost 不会因卖出而减少
        - 所以这个公式能正确反映"总盈亏"
        """
        return self.total_value - self.total_cost
    
    @property
    def unrealized_pnl_pct(self) -> float:
        """
        浮动盈亏比例
        
        计算方式：浮动盈亏 / 累计投入本金
        
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
            包含组合所有关键指标的字典
        """
        return {
            'units': self.units,
            'cash': self.cash,
            'total_cost': self.total_cost,
            'market_value': self.market_value,
            'total_value': self.total_value,
            'unrealized_pnl': self.unrealized_pnl,
            'unrealized_pnl_pct': self.unrealized_pnl_pct,
            'current_nav': self._current_nav,
        }

