"""
示例策略2：多档止盈 + 多档逢低加仓

一个更复杂的定投策略，包含：
- 多档止盈：收益率达到阈值时分批卖出
- 多档补仓：回撤达到阈值时加大买入
"""

from typing import Dict, Any, List
from .base import Strategy, Context, Signal


class TpDipStrategy(Strategy):
    """
    止盈补仓策略
    
    策略逻辑：
    1. 更新历史最高净值
    2. 计算当前收益率（相对于总投入本金）
    3. 检查止盈条件：收益率达到阈值时卖出一定比例
    4. 如果没有止盈，检查补仓条件：回撤达到阈值时额外买入
    5. 每天最多触发一个止盈档位或一个补仓档位
    
    配置参数：
    - tp_levels: List[Dict], 止盈档位列表
        每档包含: threshold (收益率阈值), sell_ratio (卖出比例)
        例如: [{"threshold": 0.10, "sell_ratio": 0.25}]
    
    - dip_levels: List[Dict], 补仓档位列表
        每档包含: drawdown (回撤阈值), extra_amount (补仓金额)
        例如: [{"drawdown": 0.05, "extra_amount": 500}]
    
    - max_dip_buy_ratio_of_cash: float, 单次补仓最多用现金池的比例，默认 1.0
    
    - tp_reference: str, 止盈收益率的计算基准
        "cost" - 相对于总投入本金（默认）
        "peak" - 相对于历史最高市值
    
    - auto_invest_inflow: bool, 是否自动投资新增资金，默认 False
        如果为 True，每月新增资金会立即买入
        如果为 False，新增资金仅在触发补仓条件时使用
    """
    
    # 默认止盈档位
    DEFAULT_TP_LEVELS = [
        {"threshold": 0.10, "sell_ratio": 0.25},  # 收益10%，卖出25%
        {"threshold": 0.20, "sell_ratio": 0.25},  # 收益20%，卖出25%
        {"threshold": 0.30, "sell_ratio": 0.50},  # 收益30%，卖出50%
    ]
    
    # 默认补仓档位
    DEFAULT_DIP_LEVELS = [
        {"drawdown": 0.05, "extra_amount": 500},   # 回撤5%，补500
        {"drawdown": 0.10, "extra_amount": 1000},  # 回撤10%，补1000
        {"drawdown": 0.15, "extra_amount": 1500},  # 回撤15%，补1500
    ]
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)
        
        # 加载配置，使用默认值
        self.tp_levels: List[Dict] = self.config.get('tp_levels', self.DEFAULT_TP_LEVELS)
        self.dip_levels: List[Dict] = self.config.get('dip_levels', self.DEFAULT_DIP_LEVELS)
        self.max_dip_buy_ratio = self.config.get('max_dip_buy_ratio_of_cash', 1.0)
        self.tp_reference = self.config.get('tp_reference', 'cost')
        self.auto_invest_inflow = self.config.get('auto_invest_inflow', False)
        
        # 按阈值降序排列，优先检查高档位
        self.tp_levels = sorted(self.tp_levels, key=lambda x: x['threshold'], reverse=True)
        self.dip_levels = sorted(self.dip_levels, key=lambda x: x['drawdown'], reverse=True)
    
    def on_start(self) -> None:
        """初始化策略状态"""
        self.state['peak_nav'] = 0.0           # 历史最高净值
        self.state['tp_count'] = 0             # 止盈次数
        self.state['dip_count'] = 0            # 补仓次数
        self.state['last_tp_level'] = None     # 上次触发的止盈档位
        self.state['last_dip_level'] = None    # 上次触发的补仓档位
        self.state['triggered_tp_levels'] = set()  # 已触发过的止盈档位
    
    def on_bar(self, ctx: Context) -> Signal:
        """
        每日处理逻辑
        
        1. 更新峰值净值
        2. 检查止盈条件
        3. 如果没有止盈，检查补仓条件
        4. 处理自动投资
        """
        nav = ctx.nav
        portfolio = ctx.portfolio
        
        # 更新历史最高净值
        if nav > self.state.get('peak_nav', 0):
            self.state['peak_nav'] = nav
        peak_nav = self.state['peak_nav']
        
        # 初始化信号
        buy_cash = 0.0
        sell_units = 0.0
        note_parts = []
        
        # ===== 1. 检查止盈条件 =====
        if portfolio.units > 0 and portfolio.total_cost > 0:
            # 计算收益率
            if self.tp_reference == 'cost':
                # 相对于总投入本金的收益率
                pnl_pct = portfolio.unrealized_pnl_pct
            else:
                # 相对于历史最高市值的收益率（可扩展）
                pnl_pct = portfolio.unrealized_pnl_pct
            
            # 从高档位到低档位检查
            for level in self.tp_levels:
                threshold = level['threshold']
                sell_ratio = level['sell_ratio']
                
                if pnl_pct >= threshold:
                    # 触发止盈
                    sell_units = portfolio.units * sell_ratio
                    self.state['tp_count'] += 1
                    self.state['last_tp_level'] = threshold
                    note_parts.append(
                        f"止盈触发: 收益率{pnl_pct:.2%}>={threshold:.0%}, "
                        f"卖出{sell_ratio:.0%}份额({sell_units:.2f}份)"
                    )
                    break  # 每天只触发一档
        
        # ===== 2. 如果没有止盈，检查补仓条件 =====
        if sell_units == 0 and peak_nav > 0:
            # 计算回撤
            drawdown = (peak_nav - nav) / peak_nav
            
            # 从高档位到低档位检查
            for level in self.dip_levels:
                dd_threshold = level['drawdown']
                extra_amount = level['extra_amount']
                
                if drawdown >= dd_threshold:
                    # 计算可用于补仓的金额
                    max_buy = ctx.cash_pool * self.max_dip_buy_ratio
                    actual_buy = min(extra_amount, max_buy)
                    
                    if actual_buy > 0:
                        buy_cash = actual_buy
                        self.state['dip_count'] += 1
                        self.state['last_dip_level'] = dd_threshold
                        note_parts.append(
                            f"补仓触发: 回撤{drawdown:.2%}>={dd_threshold:.0%}, "
                            f"补仓{actual_buy:.2f}元"
                        )
                    break  # 每天只触发一档
        
        # ===== 3. 自动投资新增资金（如果配置了） =====
        if self.auto_invest_inflow and ctx.cash_inflow > 0 and sell_units == 0:
            # 如果没有触发止盈，且配置了自动投资
            if buy_cash == 0:
                # 如果也没有触发补仓，则用新增资金买入
                buy_cash = ctx.cash_inflow
                note_parts.append(f"定投买入: {buy_cash:.2f}元")
            else:
                # 如果已经触发补仓，补仓金额已经包含在 cash_pool 中
                pass
        
        note = "; ".join(note_parts) if note_parts else ""
        
        return Signal(buy_cash=buy_cash, sell_units=sell_units, note=note)
    
    def get_stats(self) -> Dict[str, Any]:
        """
        获取策略统计数据
        
        Returns:
            包含止盈次数、补仓次数等统计的字典
        """
        return {
            'tp_count': self.state.get('tp_count', 0),
            'dip_count': self.state.get('dip_count', 0),
            'peak_nav': self.state.get('peak_nav', 0),
        }
    
    def get_name(self) -> str:
        return "止盈补仓策略"

