# -*- coding: utf-8 -*-
"""
CashModel - 资金模型

管理 cash_pool（可用现金）和 wait_pool（等待池）
"""

from datetime import date
from typing import Optional
import logging

import sys
from pathlib import Path
project_root = Path(__file__).parent.parent.parent.parent
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))
from utils.trade_calendar import is_trade_day, next_trade_day

logger = logging.getLogger(__name__)


class CashModel:
    """
    资金模型
    
    资金结构：
    - cash_pool: 可用现金（可立即买入）
    - wait_pool: 等待池（因溢价/条件不满足暂存，仍算净资产）
    
    约束：
    - 不允许负现金
    - 买入必须满足最小成交金额
    """
    
    def __init__(
        self,
        initial_cash: float = 0.0,
        min_trade_amount: float = 1000.0,
        monthly_deposit: Optional[float] = None,
        deposit_day: int = 10
    ):
        """
        初始化资金模型
        
        Args:
            initial_cash: 初始现金
            min_trade_amount: 最小成交金额（默认1000，理想2000）
            monthly_deposit: 每月入金金额（None表示不自动入金）
            deposit_day: 每月入金日期（如10号）
        """
        self.cash_pool = max(0.0, initial_cash)  # 不允许负现金
        self.wait_pool = 0.0
        self.min_trade_amount = min_trade_amount
        self.monthly_deposit = monthly_deposit
        self.deposit_day = deposit_day
        
        # 记录上次入金日期
        self._last_deposit_date: Optional[date] = None
        
        # 记录累计入金（用于计算年化收益）
        self.total_deposits = 0.0
    
    def deposit(self, amount: float, trade_date: date) -> bool:
        """
        入金
        
        Args:
            amount: 入金金额
            trade_date: 交易日期
        
        Returns:
            是否成功
        """
        if amount <= 0:
            return False
        
        self.cash_pool += amount
        self.total_deposits += amount  # 累计入金
        logger.debug(f"入金成功: amount={amount}, cash_pool={self.cash_pool}, date={trade_date}")
        return True
    
    def check_monthly_deposit(self, trade_date: date) -> bool:
        """
        检查是否需要每月入金
        
        规则：
        - 每月固定日期入金（如每月10号）
        - 若非交易日则顺延到下一个交易日
        
        Args:
            trade_date: 当前交易日期
        
        Returns:
            是否执行了入金
        """
        if self.monthly_deposit is None or self.monthly_deposit <= 0:
            return False
        
        # 检查是否到了入金日期
        if trade_date.day == self.deposit_day:
            # 检查是否已经入过金（避免同一天重复入金）
            if self._last_deposit_date is None or self._last_deposit_date < trade_date:
                # 检查是否为交易日（如果不是，则顺延）
                if is_trade_day(trade_date):
                    self.deposit(self.monthly_deposit, trade_date)
                    self._last_deposit_date = trade_date
                    return True
                else:
                    # 顺延到下一个交易日
                    next_day = next_trade_day(trade_date)
                    if next_day == trade_date:  # 如果已经是下一个交易日了
                        self.deposit(self.monthly_deposit, trade_date)
                        self._last_deposit_date = trade_date
                        return True
        elif trade_date.day > self.deposit_day:
            # 如果已经过了入金日期，检查是否需要顺延入金
            # 找到本月应该入金的日期
            deposit_date = date(trade_date.year, trade_date.month, self.deposit_day)
            if is_trade_day(deposit_date):
                actual_deposit_date = deposit_date
            else:
                actual_deposit_date = next_trade_day(deposit_date)
            
            # 如果实际入金日期是今天，且还没入过金
            if actual_deposit_date == trade_date:
                if self._last_deposit_date is None or self._last_deposit_date < trade_date:
                    self.deposit(self.monthly_deposit, trade_date)
                    self._last_deposit_date = trade_date
                    return True
        
        return False
    
    def move_to_wait_pool(self, amount: float) -> float:
        """
        将现金移动到等待池
        
        Args:
            amount: 移动金额
        
        Returns:
            实际移动的金额（不超过 cash_pool）
        """
        if amount <= 0:
            return 0.0
        
        actual_amount = min(amount, self.cash_pool)
        self.cash_pool -= actual_amount
        self.wait_pool += actual_amount
        
        logger.debug(f"移动到等待池: amount={actual_amount}, cash_pool={self.cash_pool}, wait_pool={self.wait_pool}")
        return actual_amount
    
    def move_from_wait_pool(self, amount: float) -> float:
        """
        从等待池移回现金池
        
        Args:
            amount: 移动金额
        
        Returns:
            实际移动的金额（不超过 wait_pool）
        """
        if amount <= 0:
            return 0.0
        
        actual_amount = min(amount, self.wait_pool)
        self.wait_pool -= actual_amount
        self.cash_pool += actual_amount
        
        logger.debug(f"从等待池移回: amount={actual_amount}, cash_pool={self.cash_pool}, wait_pool={self.wait_pool}")
        return actual_amount
    
    def can_trade(self, amount: float) -> bool:
        """
        检查是否可以交易
        
        Args:
            amount: 交易金额
        
        Returns:
            是否可以交易（满足最小成交额且现金足够）
        """
        if amount < self.min_trade_amount:
            return False
        
        if amount > self.cash_pool:
            return False
        
        return True
    
    def get_available_amount(self, target_amount: float) -> float:
        """
        获取可用交易金额
        
        如果 target_amount > cash_pool，则返回 cash_pool
        如果 target_amount < min_trade_amount，则返回 0
        
        Args:
            target_amount: 目标交易金额
        
        Returns:
            可用交易金额
        """
        if target_amount < self.min_trade_amount:
            return 0.0
        
        return min(target_amount, self.cash_pool)
    
    @property
    def total_equity(self) -> float:
        """总净资产 = cash_pool + wait_pool"""
        return self.cash_pool + self.wait_pool
    
    def get_state(self) -> dict:
        """获取资金状态"""
        return {
            'cash_pool': self.cash_pool,
            'wait_pool': self.wait_pool,
            'total_equity': self.total_equity,
            'min_trade_amount': self.min_trade_amount
        }

