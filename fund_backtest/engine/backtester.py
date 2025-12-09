"""
回测主循环

Backtester 负责驱动整个回测流程：
1. 遍历行情数据
2. 管理现金流入
3. 调用策略获取交易信号
4. 执行交易
5. 记录结果
"""

from datetime import datetime
from typing import List, Optional, Callable
import csv

from .types import NavBar, DayResult
from .data_feed import DataFeed
from .portfolio import Portfolio
from ..strategies.base import Strategy, Context, Signal


def is_new_month(prev_date: Optional[datetime], curr_date: datetime) -> bool:
    """
    判断是否进入新的一个月
    
    Args:
        prev_date: 前一个交易日日期，首日为 None
        curr_date: 当前交易日日期
    
    Returns:
        True 如果是新月份的第一个交易日
    """
    if prev_date is None:
        return True
    return curr_date.year > prev_date.year or curr_date.month > prev_date.month


def is_new_week(prev_date: Optional[datetime], curr_date: datetime) -> bool:
    """
    判断是否进入新的一周
    
    Args:
        prev_date: 前一个交易日日期
        curr_date: 当前交易日日期
    
    Returns:
        True 如果是新一周的第一个交易日
    """
    if prev_date is None:
        return True
    # ISO 周数比较
    prev_week = prev_date.isocalendar()[1]
    curr_week = curr_date.isocalendar()[1]
    return curr_week != prev_week or curr_date.year != prev_date.year


class Backtester:
    """
    回测引擎
    
    负责驱动回测流程，但不包含任何策略逻辑
    
    Attributes:
        data_feed: 行情数据源
        portfolio: 投资组合
        strategy: 策略实例
        initial_invest: 初始一次性投入金额
        periodic_invest: 定期投入金额
        invest_day_rule: 定投周期规则 ("month_change" 或 "week_change")
    """
    
    def __init__(
        self,
        data_feed: DataFeed,
        portfolio: Portfolio,
        strategy: Strategy,
        initial_invest: float = 0.0,
        periodic_invest: float = 0.0,
        invest_day_rule: str = "month_change"
    ):
        """
        初始化回测引擎
        
        Args:
            data_feed: 行情数据源
            portfolio: 投资组合对象
            strategy: 策略实例
            initial_invest: 首次投入金额，在第一个交易日买入
            periodic_invest: 定期投入金额
            invest_day_rule: 定投触发规则
                - "month_change": 每月第一个交易日
                - "week_change": 每周第一个交易日
        """
        self.data_feed = data_feed
        self.portfolio = portfolio
        self.strategy = strategy
        self.initial_invest = initial_invest
        self.periodic_invest = periodic_invest
        self.invest_day_rule = invest_day_rule
        
        # 内部状态
        self.results: List[DayResult] = []
        self.cash_pool = 0.0  # 待投资现金池
    
    def _check_invest_day(
        self, 
        prev_date: Optional[datetime], 
        curr_date: datetime
    ) -> bool:
        """
        检查是否是定投日
        
        Args:
            prev_date: 前一个交易日
            curr_date: 当前交易日
        
        Returns:
            True 如果是定投日
        """
        if self.invest_day_rule == "month_change":
            return is_new_month(prev_date, curr_date)
        elif self.invest_day_rule == "week_change":
            return is_new_week(prev_date, curr_date)
        else:
            return False
    
    def run(self) -> List[DayResult]:
        """
        执行回测
        
        回测流程：
        1. 首日进行初始投资
        2. 遍历每个交易日：
           a. 检查是否有定投资金流入
           b. 更新组合估值
           c. 调用策略获取信号
           d. 执行交易
           e. 记录结果
        
        Returns:
            每日回测结果列表
        """
        self.results = []
        self.cash_pool = 0.0
        prev_date: Optional[datetime] = None
        is_first_day = True
        
        # 调用策略的 on_start
        self.strategy.on_start()
        
        for bar in self.data_feed:
            date, nav = bar.date, bar.nav
            
            # ===== 1. 处理资金流入 =====
            cash_inflow = 0.0
            
            # 首日处理初始投资
            if is_first_day and self.initial_invest > 0:
                cash_inflow += self.initial_invest
                self.cash_pool += self.initial_invest
                is_first_day = False
            else:
                is_first_day = False
            
            # 检查定投日
            if self._check_invest_day(prev_date, date) and self.periodic_invest > 0:
                # 首日的定投资金不重复计算
                if prev_date is not None:
                    cash_inflow += self.periodic_invest
                    self.cash_pool += self.periodic_invest
            
            # ===== 2. 更新组合估值 =====
            self.portfolio.update_valuation(nav)
            
            # ===== 3. 构造上下文，调用策略 =====
            ctx = Context(
                date=date,
                nav=nav,
                portfolio=self.portfolio,
                cash_inflow=cash_inflow,
                cash_pool=self.cash_pool,
                state=self.strategy.state,
            )
            signal = self.strategy.on_bar(ctx)
            
            # ===== 4. 执行交易 =====
            buy_cash = 0.0
            sell_cash = 0.0
            buy_units = 0.0
            sell_units = 0.0
            
            # 处理买入
            actual_buy_cash = min(signal.buy_cash, self.cash_pool)
            if actual_buy_cash > 0:
                buy_units = self.portfolio.buy(nav, actual_buy_cash)
                buy_cash = actual_buy_cash
                self.cash_pool -= actual_buy_cash
            
            # 处理卖出
            if signal.sell_units > 0:
                sell_cash = self.portfolio.sell(nav, signal.sell_units)
                sell_units = min(signal.sell_units, self.portfolio.units + signal.sell_units)
                # 卖出所得加入现金池
                self.cash_pool += sell_cash
            
            # ===== 5. 再次更新估值 =====
            self.portfolio.update_valuation(nav)
            
            # ===== 6. 记录结果 =====
            result = DayResult(
                date=date,
                nav=nav,
                units=self.portfolio.units,
                fund_value=self.portfolio.market_value,
                cash=self.cash_pool,
                total_value=self.portfolio.market_value + self.cash_pool,
                total_cost=self.portfolio.total_cost,
                unrealized_pnl=self.portfolio.market_value + self.cash_pool - self.portfolio.total_cost,
                unrealized_pnl_pct=(
                    (self.portfolio.market_value + self.cash_pool - self.portfolio.total_cost) 
                    / self.portfolio.total_cost
                ) if self.portfolio.total_cost > 0 else 0.0,
                buy_cash=buy_cash,
                sell_cash=sell_cash,
                buy_units=buy_units,
                sell_units=sell_units,
                note=signal.note,
            )
            self.results.append(result)
            
            prev_date = date
        
        # 调用策略的 on_end
        self.strategy.on_end()
        
        return self.results
    
    def get_summary(self) -> dict:
        """
        获取回测摘要统计
        
        Returns:
            包含关键指标的字典
        """
        if not self.results:
            return {}
        
        first = self.results[0]
        last = self.results[-1]
        
        # 计算年化收益率
        days = (last.date - first.date).days
        years = days / 365.0 if days > 0 else 1
        total_return = last.unrealized_pnl_pct
        annual_return = (1 + total_return) ** (1 / years) - 1 if years > 0 else 0
        
        # 统计交易次数
        buy_count = sum(1 for r in self.results if r.buy_cash > 0)
        sell_count = sum(1 for r in self.results if r.sell_cash > 0)
        total_buy = sum(r.buy_cash for r in self.results)
        total_sell = sum(r.sell_cash for r in self.results)
        
        # 从策略中获取额外统计
        strategy_stats = {}
        if hasattr(self.strategy, 'get_stats'):
            strategy_stats = self.strategy.get_stats()
        
        return {
            'start_date': first.date.strftime('%Y-%m-%d'),
            'end_date': last.date.strftime('%Y-%m-%d'),
            'days': days,
            'total_cost': last.total_cost,
            'final_value': last.total_value,
            'final_fund_value': last.fund_value,
            'final_cash': last.cash,
            'total_return': total_return,
            'annual_return': annual_return,
            'buy_count': buy_count,
            'sell_count': sell_count,
            'total_buy': total_buy,
            'total_sell': total_sell,
            **strategy_stats,
        }


def write_results_to_csv(results: List[DayResult], filepath: str) -> None:
    """
    将回测结果写入CSV文件
    
    Args:
        results: DayResult 列表
        filepath: 输出文件路径
    """
    if not results:
        return
    
    fieldnames = [
        'date', 'nav', 'units', 'fund_value', 'cash', 
        'total_value', 'total_cost', 'unrealized_pnl', 'unrealized_pnl_pct',
        'buy_cash', 'sell_cash', 'buy_units', 'sell_units', 'note'
    ]
    
    with open(filepath, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        
        for r in results:
            writer.writerow({
                'date': r.date.strftime('%Y-%m-%d'),
                'nav': f'{r.nav:.4f}',
                'units': f'{r.units:.4f}',
                'fund_value': f'{r.fund_value:.2f}',
                'cash': f'{r.cash:.2f}',
                'total_value': f'{r.total_value:.2f}',
                'total_cost': f'{r.total_cost:.2f}',
                'unrealized_pnl': f'{r.unrealized_pnl:.2f}',
                'unrealized_pnl_pct': f'{r.unrealized_pnl_pct:.4f}',
                'buy_cash': f'{r.buy_cash:.2f}',
                'sell_cash': f'{r.sell_cash:.2f}',
                'buy_units': f'{r.buy_units:.4f}',
                'sell_units': f'{r.sell_units:.4f}',
                'note': r.note,
            })

