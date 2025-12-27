# -*- coding: utf-8 -*-
"""
AdvisorViewModel - 统一输出模型

用于UI展示，确保所有数字自洽。
"""
from dataclasses import dataclass, field
from typing import Optional, List, Dict, Any
from datetime import datetime
from decimal import Decimal


@dataclass
class ReasonBlock:
    """结构化原因块"""
    rule_name: str  # 规则名称
    input_values: Dict[str, Any]  # 输入值
    decision: str  # 决策结论


@dataclass
class AdvisorViewModel:
    """Advisor统一输出模型（用于UI展示）"""
    
    # ========== 行情状态 ==========
    is_trade_day: bool  # 是否为交易日
    is_trade_time: bool  # 是否在交易时段
    quote_time: Optional[datetime]  # 上次行情时间
    last_price: Decimal  # 最新价
    prev_close: Decimal  # 昨收价
    pct_change: Optional[Decimal]  # 涨跌幅（百分比，如0.05表示5%）
    iopv: Optional[Decimal]  # IOPV实时估值（QDII可用时）
    premium_rate: Optional[Decimal]  # 溢价率（QDII可用时）
    
    # ========== 慢指标（昨日） ==========
    pct_rank: Optional[Decimal]  # 分位排名（0-1，如0.72表示72%分位）
    peak_close: Optional[Decimal]  # 峰值价格
    drawdown_from_peak: Optional[Decimal]  # 回撤幅度（0-1，如0.08表示8%）
    ma20: Optional[Decimal]  # 20日均线
    ma60: Optional[Decimal]  # 60日均线
    price_over_ma20: Optional[bool]  # 当前价是否超过MA20
    price_over_ma60: Optional[bool]  # 当前价是否超过MA60
    
    # ========== 资金与预算 ==========
    cash_available: Decimal  # 可用现金池余额（可用于今天执行）
    wait_pool_balance: Decimal  # 等待池累计金额（之前因为溢价/门槛未买进去的）
    new_budget: Decimal  # 本轮新增预算（根据资金规则计算出的"新可投入金额"）
    wait_pool_before: Decimal  # 等待池余额（before，历史累计）
    planned_amount: Decimal  # 本轮可用于买入（=new_budget + wait_pool_before）
    plan_budget_today: Decimal  # 今天"计划预算"（兼容字段，等于new_budget）
    budget_for_execution: Decimal  # 本次允许用于执行的预算（=min(planned_amount, cash_available)）
    budget_to_wait_pool: Decimal  # 本次应转入等待池的预算金额（由溢价刹车/门槛导致）
    budget_to_execute: Decimal  # 本次建议实际执行金额（最终BUY的金额，可能为0）
    
    # ========== 交易成本与门槛 ==========
    fee_rate: Decimal  # 手续费率（0.000845）
    fee_min: Decimal  # 最低手续费（0.20）
    min_trade_amount: Decimal  # 最小成交金额（>=1000）
    ideal_trade_amount: Decimal  # 理想成交金额（>=2000）
    estimated_fee: Decimal  # 预计手续费（按budget_to_execute计算）
    lot_size: Optional[int]  # 一手股数（ETF/LOF为100，其他为None）
    suggest_shares: Optional[int]  # 建议股数（ETF/LOF）
    rounded_amount: Optional[Decimal]  # 按一手取整后的金额（ETF/LOF）
    
    # ========== 最终建议 ==========
    action: str  # BUY/HOLD/WAIT/SKIP
    execute_ratio: Decimal  # 本次预算中用于执行的比例（0~1）
    wait_ratio: Decimal  # 本次预算中进入等待池的比例（0~1）
    limit_price_hint: Optional[Decimal]  # 限价建议
    time_window_hint: Optional[str]  # 建议交易窗口（如"10:30-11:15/13:30-14:30"）
    reason_blocks: List[ReasonBlock] = field(default_factory=list)  # 结构化原因列表
    
    # ========== 策略组合信息 ==========
    strategy_codes: List[str] = field(default_factory=list)  # 绑定的策略代码列表
    
    def validate(self) -> List[str]:
        """
        验证ViewModel的自洽性
        
        Returns:
            List[str]: 错误列表（空列表表示通过验证）
        """
        errors = []
        
        # 1. 金额非负
        if self.budget_to_execute < 0:
            errors.append(f"budget_to_execute < 0: {self.budget_to_execute}")
        if self.budget_to_wait_pool < 0:
            errors.append(f"budget_to_wait_pool < 0: {self.budget_to_wait_pool}")
        if self.budget_for_execution < 0:
            errors.append(f"budget_for_execution < 0: {self.budget_for_execution}")
        
        # 2. planned_amount 恒等式
        if abs(self.planned_amount - (self.new_budget + self.wait_pool_before)) > Decimal('0.01'):
            errors.append(
                f"planned_amount({self.planned_amount}) != new_budget({self.new_budget}) + wait_pool_before({self.wait_pool_before})"
            )
        
        # 3. 比例与金额恒等式
        if self.budget_to_execute + self.budget_to_wait_pool > self.budget_for_execution:
            errors.append(
                f"budget_to_execute({self.budget_to_execute}) + budget_to_wait_pool({self.budget_to_wait_pool}) "
                f"> budget_for_execution({self.budget_for_execution})"
            )
        
        # 4. executed_amount + moved_to_wait <= planned_amount
        if self.budget_to_execute + self.budget_to_wait_pool > self.planned_amount:
            errors.append(
                f"budget_to_execute({self.budget_to_execute}) + budget_to_wait_pool({self.budget_to_wait_pool}) "
                f"> planned_amount({self.planned_amount})"
            )
        
        # 5. 比例计算正确性
        if self.budget_for_execution > 0:
            expected_execute_ratio = self.budget_to_execute / self.budget_for_execution
            expected_wait_ratio = self.budget_to_wait_pool / self.budget_for_execution
            
            if abs(self.execute_ratio - expected_execute_ratio) > Decimal('0.0001'):
                errors.append(
                    f"execute_ratio不一致: 期望={expected_execute_ratio}, 实际={self.execute_ratio}"
                )
            if abs(self.wait_ratio - expected_wait_ratio) > Decimal('0.0001'):
                errors.append(
                    f"wait_ratio不一致: 期望={expected_wait_ratio}, 实际={self.wait_ratio}"
                )
            
            # 比例总和不能超过1（允许<1，因为可能有未分配部分）
            if self.execute_ratio + self.wait_ratio > Decimal('1.0001'):
                errors.append(
                    f"execute_ratio({self.execute_ratio}) + wait_ratio({self.wait_ratio}) > 1.0"
                )
        else:
            # budget_for_execution=0时，比例应该都是0
            if self.execute_ratio != 0 or self.wait_ratio != 0:
                errors.append(
                    f"budget_for_execution=0但比例不为0: execute_ratio={self.execute_ratio}, wait_ratio={self.wait_ratio}"
                )
        
        # 6. BUY时必须满足最小成交额
        if self.action == 'BUY' and self.budget_to_execute > 0:
            if self.budget_to_execute < self.min_trade_amount:
                errors.append(
                    f"BUY时budget_to_execute({self.budget_to_execute}) < min_trade_amount({self.min_trade_amount})"
                )
        
        return errors

