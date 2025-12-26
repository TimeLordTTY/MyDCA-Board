# -*- coding: utf-8 -*-
"""
ExecutionSimulator - 执行模拟器

将策略输出变成"是否成交 + 成交金额 + 手续费 + 持仓变化"。
"""

from dataclasses import dataclass
from datetime import date
from typing import List, Optional
import logging

from ..framework.decision import Decision
from ..account.cash_model import CashModel
from ..account.fee_model import FeeModel

logger = logging.getLogger(__name__)


@dataclass
class Trade:
    """
    成交记录
    
    Attributes:
        trade_date: 成交日期
        product_id: 产品ID
        side: 买卖方向 (BUY / SELL)
        amount: 成交金额
        price: 成交价格（close）
        shares: 成交份额
        fee: 手续费
        reasons: 成交原因（来自 Decision.reasons）
    """
    trade_date: date
    product_id: int
    side: str  # BUY / SELL
    amount: float
    price: float
    shares: float
    fee: float
    reasons: List[str]


class ExecutionSimulator:
    """
    执行模拟器
    
    输入：Decision + 当前 cash_pool/wait_pool + 当日 close
    输出：Trade（如果成交）或 None（如果不成交）
    """
    
    def __init__(
        self,
        cash_model: CashModel,
        product_id: int,
        is_exchange: bool = True
    ):
        """
        初始化执行模拟器
        
        Args:
            cash_model: 资金模型
            product_id: 产品ID
            is_exchange: 是否为场内交易
        """
        self.cash_model = cash_model
        self.product_id = product_id
        self.is_exchange = is_exchange
    
    def execute(
        self,
        decision: Decision,
        trade_date: date,
        price: float
    ) -> Optional[Trade]:
        """
        执行决策
        
        成交规则：
        1. 如果 cash_pool < target_amount：按可用金额缩减（必须仍满足最小成交额）
        2. 如果成交金额 < min_trade_amount：不成交，金额进入 wait_pool
        3. 计算手续费：fee = FeeModel.calculate(amount)
        4. 计算份额：shares = (amount - fee) / price
        
        Args:
            decision: 策略决策
            trade_date: 交易日期
            price: 成交价格（close）
        
        Returns:
            Trade 如果成交，None 如果不成交
        """
        if decision.action != "BUY":
            return None
        
        if price <= 0:
            logger.warning(f"价格无效: {price}")
            return None
        
        # 获取可用交易金额
        available_amount = self.cash_model.get_available_amount(decision.target_amount)
        
        if available_amount <= 0:
            # 金额不足，进入等待池
            if decision.target_amount > 0:
                self.cash_model.move_to_wait_pool(decision.target_amount)
                logger.debug(f"金额不足，进入等待池: target={decision.target_amount}, available={available_amount}")
            return None
        
        # 计算手续费
        fee = FeeModel.calculate(available_amount, self.is_exchange)
        
        # 计算份额（扣除手续费后的净额）
        net_amount = available_amount - fee
        if net_amount <= 0:
            logger.warning(f"扣除手续费后金额为0: amount={available_amount}, fee={fee}")
            return None
        
        shares = net_amount / price
        
        # 从现金池扣除
        self.cash_model.cash_pool -= available_amount
        
        # 记录成交
        trade = Trade(
            trade_date=trade_date,
            product_id=self.product_id,
            side="BUY",
            amount=available_amount,
            price=price,
            shares=shares,
            fee=fee,
            reasons=decision.reasons.copy()
        )
        
        logger.debug(f"成交: {trade}")
        return trade
    
    def execute_sell(
        self,
        shares: float,
        trade_date: date,
        price: float
    ) -> Optional[Trade]:
        """
        执行卖出（预留接口，当前版本主要关注买入）
        
        Args:
            shares: 卖出份额
            trade_date: 交易日期
            price: 成交价格
        
        Returns:
            Trade 如果成交，None 如果不成交
        """
        if shares <= 0 or price <= 0:
            return None
        
        # 计算卖出金额
        gross_amount = shares * price
        
        # 计算手续费
        fee = FeeModel.calculate(gross_amount, self.is_exchange)
        
        # 计算到手金额
        net_amount = gross_amount - fee
        
        # 增加现金池
        self.cash_model.cash_pool += net_amount
        
        # 记录成交
        trade = Trade(
            trade_date=trade_date,
            product_id=self.product_id,
            side="SELL",
            amount=net_amount,
            price=price,
            shares=shares,
            fee=fee,
            reasons=["卖出"]
        )
        
        logger.debug(f"卖出成交: {trade}")
        return trade

