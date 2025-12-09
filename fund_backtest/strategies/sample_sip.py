"""
示例策略1：普通定投（Simple Investment Plan）

最朴素的"定期定额"策略，用作基准对比
"""

from typing import Dict, Any
from .base import Strategy, Context, Signal


class SipStrategy(Strategy):
    """
    普通定投策略
    
    规则：
    - 每当有新增定投资金（cash_inflow > 0）时，全部用来买入
    - 不管市场高低，不止盈不补仓
    - 这是最简单的定投基准策略
    
    配置参数（可选）：
    - immediate_invest: bool, 是否立即投资新增资金，默认 True
                        如果为 False，则等待策略决定何时使用
    """
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)
        # 是否立即投资新增资金
        self.immediate_invest = self.config.get('immediate_invest', True)
    
    def on_bar(self, ctx: Context) -> Signal:
        """
        每日处理逻辑
        
        如果本日有新增资金流入，立即全部买入
        """
        buy_cash = 0.0
        note = ""
        
        if self.immediate_invest:
            # 立即投资模式：将所有可用现金都用于买入
            if ctx.cash_pool > 0:
                buy_cash = ctx.cash_pool
                if ctx.cash_inflow > 0:
                    note = f"定投买入: {buy_cash:.2f}"
                else:
                    note = f"买入: {buy_cash:.2f}"
        else:
            # 仅投资新增资金
            if ctx.cash_inflow > 0:
                buy_cash = ctx.cash_inflow
                note = f"定投买入: {buy_cash:.2f}"
        
        return Signal(buy_cash=buy_cash, note=note)
    
    def on_start(self) -> None:
        """初始化策略状态"""
        self.state['total_sip_count'] = 0  # 定投次数
    
    def get_name(self) -> str:
        return "普通定投策略"

