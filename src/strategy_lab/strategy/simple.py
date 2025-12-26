# -*- coding: utf-8 -*-
"""
SimpleStrategy - 简单策略

固定周频/月频买入策略，用于验证框架。
"""

from datetime import date
from typing import Dict, Any, Optional

from ..framework.base import Strategy
from ..framework.context import Context
from ..framework.decision import Decision
from ..framework.registry import register_strategy
import sys
from pathlib import Path
project_root = Path(__file__).parent.parent.parent.parent
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))
from utils.trade_calendar import is_trade_day


@register_strategy("simple", version="default", set_as_default=True)
class SimpleStrategy(Strategy):
    """
    简单策略
    
    支持：
    - 固定周频买入（如每周五）
    - 固定月频买入（如每月10号）
    - 非固定日期买入（用于验证框架）
    """
    
    strategy_key = "simple"
    strategy_version = "default"
    display_name = "简单策略"
    
    # 默认参数
    DEFAULT_BASE_AMOUNT = 1000.0
    DEFAULT_FREQUENCY = "monthly"  # monthly / weekly / daily
    DEFAULT_DAY = 10  # 每月几号（monthly）或星期几（weekly，0=周一，6=周日）
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        super().__init__(config)
        
        # 基础买入金额
        self.base_amount = float(self.config.get("base_amount", self.DEFAULT_BASE_AMOUNT))
        
        # 买入频率
        self.frequency = self.config.get("frequency", self.DEFAULT_FREQUENCY)  # monthly / weekly / daily
        
        # 买入日期
        self.day = int(self.config.get("day", self.DEFAULT_DAY))
        
        # 状态：记录上次买入日期
        self.state.setdefault("last_buy_date", None)
    
    def on_start(self) -> None:
        """初始化状态"""
        self.state["last_buy_date"] = None
    
    def on_day(self, ctx: Context) -> Decision:
        """
        处理每日行情
        
        Args:
            ctx: 策略上下文
        
        Returns:
            Decision: 买入决策或持有
        """
        should_buy = False
        reason = ""
        
        if self.frequency == "daily":
            # 每日买入
            should_buy = True
            reason = "每日买入策略"
        
        elif self.frequency == "weekly":
            # 每周固定日期买入
            weekday = ctx.date.weekday()  # 0=周一，6=周日
            if weekday == self.day:
                should_buy = True
                reason = f"每周{self._weekday_name(self.day)}买入策略"
        
        elif self.frequency == "monthly":
            # 每月固定日期买入
            if ctx.date.day == self.day:
                # 检查是否为交易日（如果不是，则顺延）
                if is_trade_day(ctx.date):
                    should_buy = True
                    reason = f"每月{self.day}号买入策略"
                else:
                    # 非交易日，检查是否应该顺延到今天
                    # 这里简化处理：如果是交易日且是入金日之后，则买入
                    pass
        
        if should_buy:
            # 检查现金是否足够
            if ctx.cash_pool >= self.base_amount:
                return Decision(
                    action="BUY",
                    target_amount=self.base_amount,
                    reasons=[reason],
                    tags=["simple_strategy"]
                )
            else:
                return Decision(
                    action="HOLD",
                    target_amount=0.0,
                    reasons=[f"{reason}，但现金不足（需要{self.base_amount}，可用{ctx.cash_pool}）"],
                    tags=["simple_strategy", "insufficient_cash"]
                )
        else:
            return Decision(
                action="HOLD",
                target_amount=0.0,
                reasons=[f"非买入日（频率={self.frequency}，日期={ctx.date}）"],
                tags=["simple_strategy"]
            )
    
    def _weekday_name(self, weekday: int) -> str:
        """星期几名称"""
        names = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
        return names[weekday] if 0 <= weekday < 7 else f"星期{weekday}"
    
    def get_default_params(self) -> Dict[str, Any]:
        """获取默认参数"""
        return {
            "base_amount": self.DEFAULT_BASE_AMOUNT,
            "frequency": self.DEFAULT_FREQUENCY,
            "day": self.DEFAULT_DAY
        }
    
    def get_param_schema(self) -> Dict[str, Any]:
        """获取参数 schema"""
        return {
            "base_amount": {
                "type": "float",
                "default": self.DEFAULT_BASE_AMOUNT,
                "description": "基础买入金额",
                "min": 0.0,
                "max": 100000.0
            },
            "frequency": {
                "type": "list",
                "default": self.DEFAULT_FREQUENCY,
                "description": "买入频率",
                "options": ["daily", "weekly", "monthly"]
            },
            "day": {
                "type": "int",
                "default": self.DEFAULT_DAY,
                "description": "买入日期（每月几号或星期几）",
                "min": 0,
                "max": 31
            }
        }

