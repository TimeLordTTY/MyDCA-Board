# -*- coding: utf-8 -*-
"""
Backtester - 回测主循环

驱动整个回测流程：
1. 遍历日K数据
2. 管理现金流入（每月入金）
3. 调用策略获取交易信号
4. 执行交易（由 ExecutionSimulator 处理）
5. 记录结果
"""

from datetime import date
from typing import List, Dict, Any, Optional
import logging

from ..data.provider import DataProvider
from ..data.daily_bar import DailyBar
from ..account.cash_model import CashModel
from ..account.fee_model import FeeModel
from ..framework.base import Strategy
from ..framework.context import Context
from ..simulator.executor import ExecutionSimulator, Trade
from ..metrics.calculator import MetricsCalculator
from ..metrics.reporter import Reporter

logger = logging.getLogger(__name__)


class Backtester:
    """
    回测引擎
    
    负责驱动回测流程，但不包含任何策略逻辑
    """
    
    def __init__(
        self,
        data_provider: DataProvider,
        cash_model: CashModel,
        strategy: Strategy,
        product_id: int,
        is_exchange: bool = True,
        start_date: Optional[date] = None,
        end_date: Optional[date] = None
    ):
        """
        初始化回测引擎
        
        Args:
            data_provider: 数据提供者
            cash_model: 资金模型
            strategy: 策略实例
            product_id: 产品ID
            is_exchange: 是否为场内交易
            start_date: 回测起始日期（None 则从数据起始开始）
            end_date: 回测结束日期（None 则到数据结束）
        """
        self.data_provider = data_provider
        self.cash_model = cash_model
        self.strategy = strategy
        self.product_id = product_id
        self.is_exchange = is_exchange
        
        # 保存初始现金（用于计算累计投入）
        self.initial_cash = cash_model.cash_pool + cash_model.wait_pool
        
        # 持仓状态
        self.holdings = {
            'shares': 0.0,
            'cost': 0.0,
            'value': 0.0
        }
        
        # 交易记录
        self.trades: List[Trade] = []
        
        # 每日记录
        self.daily_records: List[Dict[str, Any]] = []
        
        # 日期范围
        self.start_date = start_date
        self.end_date = end_date
        
        # 累计手续费
        self.fee_cum = 0.0
        
        # 峰值（用于计算回撤）
        self.peak_value = 0.0
    
    def run(self) -> Dict[str, Any]:
        """
        执行回测
        
        Returns:
            回测结果字典，包含：
            - summary_id: 汇总ID
            - metrics: 指标字典
            - daily_records: 每日记录
            - trades: 成交记录
        """
        logger.info(f"开始回测: product_id={self.product_id}, start={self.start_date}, end={self.end_date}")
        
        # 获取日K数据
        bars = self.data_provider.get_bars(self.product_id, self.start_date or date(2020, 1, 1), self.end_date or date.today())
        
        if not bars:
            logger.error("没有可用的日K数据")
            return {}
        
        # 过滤日期范围
        if self.start_date:
            bars = [b for b in bars if b.date >= self.start_date]
        if self.end_date:
            bars = [b for b in bars if b.date <= self.end_date]
        
        if not bars:
            logger.error("过滤后没有可用的日K数据")
            return {}
        
        logger.info(f"共 {len(bars)} 个交易日")
        
        # 初始化策略
        self.strategy.on_start()
        
        # 创建执行模拟器
        executor = ExecutionSimulator(self.cash_model, self.product_id, self.is_exchange)
        
        # 遍历每个交易日
        for bar in bars:
            self._process_day(bar, executor)
        
        # 策略结束
        self.strategy.on_end()
        
        # 计算指标
        # 计算累计投入：初始现金 + 累计入金
        # 从 cash_model 获取累计入金
        total_deposits = getattr(self.cash_model, 'total_deposits', 0.0)
        
        # 累计投入 = 初始现金 + 累计入金
        total_invested = self.initial_cash + total_deposits
        
        # 如果 total_invested 仍然为 0，使用第一条记录的总资产作为基准
        if total_invested <= 0.01 and self.daily_records:
            total_invested = self.daily_records[0].get('total_value', 1.0)
        
        metrics = MetricsCalculator.calculate(
            self.daily_records,
            [self._trade_to_dict(t) for t in self.trades],
            total_invested,  # 使用累计投入而不是初始现金
            bars[0].date,
            bars[-1].date
        )
        
        # 保存到数据库
        summary_id = Reporter.save_summary(
            self.product_id,
            self.strategy.get_strategy_key(),
            self.strategy.get_version(),
            self._get_param_set_id(),
            bars[0].date,
            bars[-1].date,
            metrics,
            strategy_config=self.strategy.config  # 传递策略参数
        )
        
        if summary_id:
            Reporter.save_daily_records(summary_id, self.daily_records)
            Reporter.save_trades(summary_id, [self._trade_to_dict(t) for t in self.trades])
        
        logger.info(f"回测完成: summary_id={summary_id}")
        
        return {
            'summary_id': summary_id,
            'metrics': metrics,
            'daily_records': self.daily_records,
            'trades': [self._trade_to_dict(t) for t in self.trades]
        }
    
    def _process_day(self, bar: DailyBar, executor: ExecutionSimulator) -> None:
        """处理单个交易日"""
        # 检查每月入金
        self.cash_model.check_monthly_deposit(bar.date)
        
        # 更新持仓市值
        self.holdings['value'] = self.holdings['shares'] * bar.close
        
        # 构造上下文
        ctx = Context(
            date=bar.date,
            bar=bar,
            cash_pool=self.cash_model.cash_pool,
            wait_pool=self.cash_model.wait_pool,
            premium_rate=None,  # TODO: 从数据库获取溢价率
            holdings=self.holdings.copy(),
            state=self.strategy.state
        )
        
        # 调用策略
        decision = self.strategy.on_day(ctx)
        
        # 执行决策
        trade = None
        if decision.action == "BUY":
            trade = executor.execute(decision, bar.date, bar.close)
            if trade:
                # 更新持仓
                self.holdings['shares'] += trade.shares
                self.holdings['cost'] += trade.amount  # 成本包含手续费
                self.fee_cum += trade.fee
                self.trades.append(trade)
        
        # 更新持仓市值
        self.holdings['value'] = self.holdings['shares'] * bar.close
        
        # 计算总资产
        total_value = self.holdings['value'] + self.cash_model.cash_pool + self.cash_model.wait_pool
        
        # 更新峰值
        if total_value > self.peak_value:
            self.peak_value = total_value
        
        # 计算回撤
        drawdown = (self.peak_value - total_value) / self.peak_value if self.peak_value > 0 else 0.0
        
        # 记录每日数据
        self.daily_records.append({
            'trade_date': bar.date,
            'nav': bar.close,
            'cash_pool': self.cash_model.cash_pool,
            'wait_pool': self.cash_model.wait_pool,
            'holdings_value': self.holdings['value'],
            'total_value': total_value,
            'drawdown': drawdown,
            'fee_cum': self.fee_cum
        })
    
    def _get_param_set_id(self) -> str:
        """获取参数组合ID（简化版，使用配置的哈希）"""
        import hashlib
        param_str = str(sorted(self.strategy.config.items()))
        return hashlib.md5(param_str.encode()).hexdigest()[:16]
    
    def _trade_to_dict(self, trade: Trade) -> Dict[str, Any]:
        """将 Trade 转换为字典"""
        return {
            'trade_date': trade.trade_date,
            'side': trade.side,
            'amount': trade.amount,
            'price': trade.price,
            'shares': trade.shares,
            'fee': trade.fee,
            'reasons': trade.reasons
        }

