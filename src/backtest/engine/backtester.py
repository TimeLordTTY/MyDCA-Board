# -*- coding: utf-8 -*-
"""
回测主循环

Backtester 负责驱动整个回测流程：
1. 遍历行情数据
2. 管理现金流入（直接加入 portfolio.cash）
3. 调用策略获取交易信号
4. 执行交易（由 Portfolio 处理现金变更）
5. 记录结果

【现金单一来源设计】
- 现金只有一个来源：portfolio.cash
- 初始投入、定投流入 -> portfolio.cash += inflow
- 买入/卖出时的现金变更由 Portfolio.buy/sell 内部处理
- 不再有 Backtester.cash_pool
"""

from datetime import datetime
from typing import List, Optional, Callable, Dict, Any
import csv
import os

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
    
    【现金单一来源】
    - 所有现金统一由 portfolio.cash 管理
    - 不再有 self.cash_pool
    
    Attributes:
        data_feed: 行情数据源
        portfolio: 投资组合（包含现金管理）
        strategy: 策略实例
        initial_invest: 初始一次性投入金额
        periodic_invest: 定期投入金额
        invest_day_rule: 定投周期规则 ("month_change" 或 "week_change")
        start_date: 回测起始日期（格式：YYYY-MM-DD），为 None 则从数据起始开始
        end_date: 回测结束日期（格式：YYYY-MM-DD），为 None 则到数据结束
    """
    
    def __init__(
        self,
        data_feed: DataFeed,
        portfolio: Portfolio,
        strategy: Strategy,
        initial_invest: float = 0.0,
        periodic_invest: float = 0.0,
        invest_day_rule: str = "month_change",
        start_date: Optional[str] = None,
        end_date: Optional[str] = None,
        fund_code: str = "未知"
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
            start_date: 回测起始日期（格式：YYYY-MM-DD），为 None 则从数据起始开始
            end_date: 回测结束日期（格式：YYYY-MM-DD），为 None 则到数据结束
            fund_code: 基金代码（用于标识和展示）
        """
        # 保存原始数据信息
        self.original_start_date = data_feed.start_date
        self.original_end_date = data_feed.end_date
        self.config_start_date = start_date
        self.config_end_date = end_date
        self.fund_code = fund_code
        
        # 对数据进行日期切片
        self.data_feed = self._slice_data_by_date(data_feed, start_date, end_date)
        
        self.portfolio = portfolio
        self.strategy = strategy
        self.initial_invest = initial_invest
        self.periodic_invest = periodic_invest
        self.invest_day_rule = invest_day_rule
        
        # 内部状态
        self.results: List[DayResult] = []
        self.principal_total = 0.0  # 真实打入本金总额（包括初始投资和定期投资）
    
    def _slice_data_by_date(
        self,
        data_feed: DataFeed,
        start_date: Optional[str],
        end_date: Optional[str]
    ) -> DataFeed:
        """
        根据日期区间对数据进行切片
        
        Args:
            data_feed: 原始数据源
            start_date: 起始日期字符串（格式：YYYY-MM-DD），为 None 则不限制起始
            end_date: 结束日期字符串（格式：YYYY-MM-DD），为 None 则不限制结束
        
        Returns:
            切片后的新 DataFeed 对象
        
        Raises:
            ValueError: 如果日期格式错误或切片后数据为空
        """
        bars = data_feed.bars
        
        # 如果没有指定任何日期限制，直接返回原数据源
        if start_date is None and end_date is None:
            return data_feed
        
        # 解析日期字符串
        start_dt = None
        end_dt = None
        
        try:
            if start_date is not None:
                start_dt = datetime.strptime(start_date, "%Y-%m-%d")
            if end_date is not None:
                end_dt = datetime.strptime(end_date, "%Y-%m-%d")
        except ValueError as e:
            raise ValueError(f"日期格式错误，应为 YYYY-MM-DD 格式: {e}")
        
        # 过滤数据
        filtered_bars = []
        for bar in bars:
            # 检查起始日期
            if start_dt is not None and bar.date < start_dt:
                continue
            # 检查结束日期
            if end_dt is not None and bar.date > end_dt:
                continue
            filtered_bars.append(bar)
        
        # 验证切片后数据非空
        if not filtered_bars:
            date_range_str = ""
            if start_date and end_date:
                date_range_str = f"区间 [{start_date}, {end_date}]"
            elif start_date:
                date_range_str = f"起始日期 {start_date} 之后"
            elif end_date:
                date_range_str = f"结束日期 {end_date} 之前"
            
            raise ValueError(
                f"指定的日期{date_range_str}内没有数据。"
                f"原始数据区间: [{self.original_start_date.strftime('%Y-%m-%d')}, "
                f"{self.original_end_date.strftime('%Y-%m-%d')}]"
            )
        
        # 返回新的 DataFeed
        return DataFeed(filtered_bars)
    
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
        1. 首日进行初始投资（加入 portfolio.cash）
        2. 遍历每个交易日：
           a. 检查是否有定投资金流入（加入 portfolio.cash）
           b. 更新组合估值
           c. 调用策略获取信号
           d. 执行交易（Portfolio 内部处理现金变更）
           e. 记录结果
        
        Returns:
            每日回测结果列表
        """
        self.results = []
        self.principal_total = 0.0  # 重置真实打入本金总额
        prev_date: Optional[datetime] = None
        is_first_day = True
        
        # 调用策略的 on_start
        self.strategy.on_start()
        
        for bar in self.data_feed:
            date, nav = bar.date, bar.nav
            
            # ===== 1. 处理资金流入（直接加入 portfolio.cash） =====
            cash_inflow = 0.0
            
            # 首日处理初始投资
            if is_first_day and self.initial_invest > 0:
                cash_inflow += self.initial_invest
                self.portfolio.cash += self.initial_invest  # 现金单一来源
                self.principal_total += self.initial_invest  # 记录真实打入本金
                is_first_day = False
            else:
                is_first_day = False
            
            # 检查定投日
            if self._check_invest_day(prev_date, date) and self.periodic_invest > 0:
                # 首日的定投资金不重复计算
                if prev_date is not None:
                    cash_inflow += self.periodic_invest
                    self.portfolio.cash += self.periodic_invest  # 现金单一来源
                    self.principal_total += self.periodic_invest  # 记录真实打入本金
            
            # ===== 2. 更新组合估值 =====
            self.portfolio.update_valuation(nav)
            
            # ===== 3. 构造上下文，调用策略 =====
            ctx = Context(
                date=date,
                nav=nav,
                portfolio=self.portfolio,
                cash_inflow=cash_inflow,
                cash=self.portfolio.cash,  # 使用 cash，不是 cash_pool
                state=self.strategy.state,
            )
            signal = self.strategy.on_bar(ctx)
            
            # ===== 4. 执行交易（Portfolio 内部处理现金变更） =====
            buy_cash = 0.0
            sell_cash = 0.0
            buy_shares = 0.0
            sell_shares = 0.0
            
            # 处理买入（Portfolio.buy 内部会扣减 cash）
            if signal.buy_cash > 0:
                # Portfolio.buy 会限制买入金额不超过可用现金
                buy_shares = self.portfolio.buy(nav, signal.buy_cash)
                # 计算实际买入金额（从 gross_buy_amount 变化推断，或用 min）
                buy_cash = min(signal.buy_cash, self.portfolio.cash + signal.buy_cash)
                # 更精确的方式：根据份额反推
                if buy_shares > 0:
                    # 实际买入金额 = 份额 * 净值 * (1 + 费率)
                    actual_invest = buy_shares * nav
                    buy_cash = actual_invest * (1 + self.portfolio.buy_fee_rate)
            
            # 处理卖出（Portfolio.sell 内部会增加 cash）
            if signal.sell_units > 0:
                sell_cash, sell_shares = self.portfolio.sell(nav, signal.sell_units)
            
            # ===== 5. 再次更新估值 =====
            self.portfolio.update_valuation(nav)
            
            # ===== 6. 记录结果 =====
            # 【持仓指标】只反映持仓部分，不包含现金
            holdings_value = self.portfolio.value
            holdings_cost = self.portfolio.cost  # 卖出时已按比例减少
            holdings_unrealized_pnl = holdings_value - holdings_cost
            holdings_return_rate = (
                holdings_unrealized_pnl / holdings_cost
            ) if holdings_cost > 0 else 0.0
            
            # 【总资产指标】= 持仓 + 现金（现金单一来源）
            total_value = self.portfolio.value + self.portfolio.cash
            
            result = DayResult(
                date=date,
                nav=nav,
                shares=self.portfolio.shares,
                value=holdings_value,
                cash=self.portfolio.cash,  # 现金单一来源
                total_value=total_value,
                cost=holdings_cost,  # 当前持仓成本（卖出时按比例减少）
                unrealized_pnl=holdings_unrealized_pnl,
                return_rate=holdings_return_rate,
                buy_cash=buy_cash,
                sell_cash=sell_cash,
                buy_shares=buy_shares,
                sell_shares=sell_shares,
                gross_buy_amount=self.portfolio.gross_buy_amount,    # 累计买入金额
                gross_sell_amount=self.portfolio.gross_sell_amount,  # 累计卖出金额
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
        
        【持仓指标】只反映持仓部分：
            - cost: 当前持仓成本（卖出时按比例减少）
            - value: 持仓市值
            - unrealized_pnl: 持仓浮盈 = value - cost
            - return_rate: 持仓收益率 = unrealized_pnl / cost
        
        【总资产指标】反映完整情况：
            - principal_total: 累计投入本金
            - final_assets: 期末总资产 = value + cash
            - real_return: 总资产收益率 = (final_assets - principal_total) / principal_total
            - annual_return: 基于 real_return 的年化收益率
        
        【统计字段】：
            - gross_buy_amount: 累计买入金额（只增不减）
            - gross_sell_amount: 累计卖出回笼金额（只增不减）
            - net_invested: 净投入 = gross_buy_amount - gross_sell_amount
        
        Returns:
            包含关键指标的字典
        """
        if not self.results:
            return {}
        
        first = self.results[0]
        last = self.results[-1]
        
        days = (last.date - first.date).days
        years = days / 365.0 if days > 0 else 1
        
        # 【持仓指标】
        holdings_return_rate = last.return_rate
        holdings_unrealized_pnl = last.unrealized_pnl
        
        # 【总资产指标】
        real_return = (
            (last.total_value - self.principal_total) / self.principal_total
        ) if self.principal_total > 0 else 0.0
        
        # 年化收益率基于真实收益率（总资产口径）
        annual_return = (1 + real_return) ** (1 / years) - 1 if years > 0 else 0
        
        # 统计交易次数
        buy_count = sum(1 for r in self.results if r.buy_cash > 0)
        sell_count = sum(1 for r in self.results if r.sell_cash > 0)
        total_buy = sum(r.buy_cash for r in self.results)
        total_sell = sum(r.sell_cash for r in self.results)
        
        # 【统计字段】
        gross_buy_amount = last.gross_buy_amount
        gross_sell_amount = last.gross_sell_amount
        net_invested = gross_buy_amount - gross_sell_amount
        
        # 从策略中获取额外统计
        strategy_stats = {}
        if hasattr(self.strategy, 'get_stats'):
            strategy_stats = self.strategy.get_stats()
        
        return {
            # 基础信息
            'strategy_name': self.strategy.get_name(),
            'strategy_key': self.strategy.get_strategy_key(),
            'strategy_version': self.strategy.get_version(),
            'fund_code': self.fund_code,
            'start_date': first.date.strftime('%Y-%m-%d'),
            'end_date': last.date.strftime('%Y-%m-%d'),
            'days': days,
            
            # 日期区间信息
            'data_start_date': self.original_start_date.strftime('%Y-%m-%d') if self.original_start_date else None,
            'data_end_date': self.original_end_date.strftime('%Y-%m-%d') if self.original_end_date else None,
            'backtest_start_date': first.date.strftime('%Y-%m-%d'),
            'backtest_end_date': last.date.strftime('%Y-%m-%d'),
            
            # 【资金情况】
            'principal_total': self.principal_total,
            'cost': last.cost,  # 当前持仓成本
            'value': last.value,
            'final_cash': last.cash,
            'final_assets': last.total_value,
            
            # 【持仓收益】
            'unrealized_pnl': holdings_unrealized_pnl,
            'return_rate': holdings_return_rate,
            
            # 【总资产收益】
            'real_return': real_return,
            'annual_return': annual_return,
            
            # 【统计字段】（只增不减）
            'gross_buy_amount': gross_buy_amount,
            'gross_sell_amount': gross_sell_amount,
            'net_invested': net_invested,
            
            # 兼容旧字段
            'total_cost': last.cost,
            'final_fund_value': last.value,
            'nominal_pnl': holdings_unrealized_pnl,
            'nominal_return': holdings_return_rate,
            'total_return': real_return,
            'final_value': last.total_value,
            
            # 交易统计
            'buy_count': buy_count,
            'sell_count': sell_count,
            'total_buy_amount': total_buy,
            'total_sell_amount': total_sell,
            'total_buy': total_buy,
            'total_sell': total_sell,
            
            # 策略自定义统计
            **strategy_stats,
        }
    
    def print_summary(self) -> None:
        """
        打印回测摘要
        
        分开展示持仓指标和总资产指标，便于理解收益口径
        """
        summary = self.get_summary()
        if not summary:
            print("无回测结果可显示")
            return
        
        print("\n" + "=" * 70)
        print("                         📊 回测结果摘要")
        print("=" * 70)
        
        print(f"\n【基础信息】")
        print(f"   策略名称: {summary.get('strategy_name', '未知')}")
        print(f"   基金代码: {summary.get('fund_code', '未知')}")
        print(f"   回测区间: {summary.get('start_date')} ~ {summary.get('end_date')}")
        print(f"   回测天数: {summary.get('days', 0)} 天")
        
        print(f"\n【资金概览】")
        print(f"   累计投入本金:  {summary.get('principal_total', 0):>12,.2f} 元")
        print(f"   累计买入金额:  {summary.get('gross_buy_amount', 0):>12,.2f} 元")
        print(f"   累计卖出回笼:  {summary.get('gross_sell_amount', 0):>12,.2f} 元")
        print(f"   净投入金额:    {summary.get('net_invested', 0):>12,.2f} 元")
        print(f"   期末现金余额:  {summary.get('final_cash', 0):>12,.2f} 元")
        print(f"   期末总资产:    {summary.get('final_assets', 0):>12,.2f} 元")
        
        print(f"\n【持仓指标】（只反映持仓，不含现金）")
        print(f"   cost(持仓成本):     {summary.get('cost', 0):>12,.2f} 元")
        print(f"   value(持仓市值):    {summary.get('value', 0):>12,.2f} 元")
        print(f"   unrealized_pnl:     {summary.get('unrealized_pnl', 0):>+12,.2f} 元")
        print(f"   return_rate:        {summary.get('return_rate', 0) * 100:>12.2f}%")
        
        print(f"\n【总资产收益】（持仓 + 现金）")
        print(f"   真实收益率:         {summary.get('real_return', 0) * 100:>12.2f}%")
        print(f"   年化收益率:         {summary.get('annual_return', 0) * 100:>12.2f}%")
        
        print(f"\n【交易统计】")
        print(f"   买入次数: {summary.get('buy_count', 0)}")
        print(f"   卖出次数: {summary.get('sell_count', 0)}")
        print(f"   总买入金额:  {summary.get('total_buy_amount', 0):>12,.2f} 元")
        print(f"   总卖出金额:  {summary.get('total_sell_amount', 0):>12,.2f} 元")
        
        print("\n" + "=" * 70)


def write_results_to_csv(results: List[DayResult], filepath: str) -> None:
    """
    将回测结果写入CSV文件
    
    使用统一字段名，与 MyDCA-Board 现有系统保持一致
    
    Args:
        results: DayResult 列表
        filepath: 输出文件路径
    """
    if not results:
        return
    
    # 使用统一字段名，添加统计字段
    fieldnames = [
        'date', 'nav', 'shares', 'value', 'cash', 
        'total_value', 'cost', 'unrealized_pnl', 'return_rate',
        'buy_cash', 'sell_cash', 'buy_shares', 'sell_shares',
        'gross_buy_amount', 'gross_sell_amount', 'note'
    ]
    
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    
    with open(filepath, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        
        for r in results:
            writer.writerow({
                'date': r.date.strftime('%Y-%m-%d'),
                'nav': f'{r.nav:.4f}',
                'shares': f'{r.shares:.2f}',
                'value': f'{r.value:.2f}',
                'cash': f'{r.cash:.2f}',
                'total_value': f'{r.total_value:.2f}',
                'cost': f'{r.cost:.2f}',
                'unrealized_pnl': f'{r.unrealized_pnl:.2f}',
                'return_rate': f'{r.return_rate:.4f}',
                'buy_cash': f'{r.buy_cash:.2f}',
                'sell_cash': f'{r.sell_cash:.2f}',
                'buy_shares': f'{r.buy_shares:.2f}',
                'sell_shares': f'{r.sell_shares:.2f}',
                'gross_buy_amount': f'{r.gross_buy_amount:.2f}',
                'gross_sell_amount': f'{r.gross_sell_amount:.2f}',
                'note': r.note,
            })


def write_summary_to_csv(summary: Dict[str, Any], filepath: str) -> None:
    """
    将回测摘要写入CSV文件
    
    【持仓指标】只反映持仓部分：cost/value/unrealized_pnl/return_rate
    【总资产指标】反映完整情况：real_return/annual_return
    【统计字段】：gross_buy_amount/gross_sell_amount/net_invested
    
    Args:
        summary: 回测摘要字典
        filepath: 输出文件路径
    """
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    
    with open(filepath, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.writer(f)
        
        # 表头清晰区分各类指标口径
        headers = [
            "基金代码", "策略名称", "回测起始", "回测结束", "回测天数",
            "累计投入本金", "累计买入金额", "累计卖出回笼", "净投入金额",
            "cost(持仓成本)", "value(持仓市值)", "现金余额", "总资产",
            "unrealized_pnl(持仓浮盈)", "return_rate(持仓收益率)", 
            "real_return(总资产收益率)", "annual_return(年化收益率)",
            "买入次数", "卖出次数", "总买入金额", "总卖出金额",
        ]
        
        row = [
            summary.get("fund_code", "未知"),
            summary.get("strategy_name", "未知"),
            summary.get("start_date", "N/A"),
            summary.get("end_date", "N/A"),
            summary.get("days", 0),
            f"{summary.get('principal_total', 0.0):.2f}",
            f"{summary.get('gross_buy_amount', 0.0):.2f}",
            f"{summary.get('gross_sell_amount', 0.0):.2f}",
            f"{summary.get('net_invested', 0.0):.2f}",
            f"{summary.get('cost', 0.0):.2f}",
            f"{summary.get('value', 0.0):.2f}",
            f"{summary.get('final_cash', 0.0):.2f}",
            f"{summary.get('final_assets', 0.0):.2f}",
            f"{summary.get('unrealized_pnl', 0.0):.2f}",
            f"{summary.get('return_rate', 0.0) * 100:.2f}%",
            f"{summary.get('real_return', 0.0) * 100:.2f}%",
            f"{summary.get('annual_return', 0.0) * 100:.2f}%",
            summary.get("buy_count", 0),
            summary.get("sell_count", 0),
            f"{summary.get('total_buy_amount', 0.0):.2f}",
            f"{summary.get('total_sell_amount', 0.0):.2f}",
        ]
        
        writer.writerow(headers)
        writer.writerow(row)
