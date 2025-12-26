# -*- coding: utf-8 -*-
"""
PercentileStrategy - 分位策略

基于滚动N日close分位判断买入时机。
"""

from typing import Dict, Any, Optional

from ..framework.base import Strategy
from ..framework.context import Context
from ..framework.decision import Decision
from ..framework.registry import register_strategy


@register_strategy("percentile", version="default", set_as_default=True)
class PercentileStrategy(Strategy):
    """
    分位策略
    
    参数：
    - base_amount: 基础买入金额
    - window: 滚动窗口大小（如 250 表示250个交易日）
    - buy_percentile: 买入分位（如 20，低于p20买）
    - hold_percentile: 持有分位（如 80，高于p80不买）
    """
    
    strategy_key = "percentile"
    strategy_version = "default"
    display_name = "分位策略"
    
    # 默认参数
    DEFAULT_BASE_AMOUNT = 1000.0
    DEFAULT_WINDOW = 250
    DEFAULT_BUY_PERCENTILE = 20
    DEFAULT_HOLD_PERCENTILE = 80
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        super().__init__(config)
        
        # 基础买入金额
        self.base_amount = float(self.config.get("base_amount", self.DEFAULT_BASE_AMOUNT))
        
        # 滚动窗口
        self.window = int(self.config.get("window", self.DEFAULT_WINDOW))
        
        # 买入分位
        self.buy_percentile = float(self.config.get("buy_percentile", self.DEFAULT_BUY_PERCENTILE))
        
        # 持有分位
        self.hold_percentile = float(self.config.get("hold_percentile", self.DEFAULT_HOLD_PERCENTILE))
        
        # 状态：历史价格列表
        self.state.setdefault("price_history", [])
    
    def on_start(self) -> None:
        """初始化状态"""
        self.state["price_history"] = []
    
    def on_day(self, ctx: Context) -> Decision:
        """
        处理每日行情
        
        Args:
            ctx: 策略上下文
        
        Returns:
            Decision: 买入决策或持有
        """
        current_price = ctx.close
        
        # 添加到历史价格
        self.state["price_history"].append(current_price)
        
        # 保持窗口大小
        if len(self.state["price_history"]) > self.window:
            self.state["price_history"] = self.state["price_history"][-self.window:]
        
        # 如果历史数据不足，不买入
        if len(self.state["price_history"]) < self.window:
            return Decision(
                action="HOLD",
                target_amount=0.0,
                reasons=[f"历史数据不足（{len(self.state['price_history'])}/{self.window}）"],
                tags=["percentile_strategy"]
            )
        
        # 计算分位
        history_prices = self.state["price_history"][:-1]  # 排除当前价格
        below_count = sum(1 for p in history_prices if p < current_price)
        percentile = (below_count / len(history_prices)) * 100 if history_prices else 0.0
        
        # 判断买入
        if percentile <= self.buy_percentile:
            # 低于买入分位，买入
            return Decision(
                action="BUY",
                target_amount=self.base_amount,
                reasons=[
                    f"当前价格分位 {percentile:.1f}% <= {self.buy_percentile}%（买入阈值）",
                    f"历史价格范围: min={min(history_prices):.4f}, max={max(history_prices):.4f}, current={current_price:.4f}"
                ],
                tags=["percentile_strategy", f"percentile_{percentile:.0f}%"]
            )
        elif percentile >= self.hold_percentile:
            # 高于持有分位，不买
            return Decision(
                action="HOLD",
                target_amount=0.0,
                reasons=[
                    f"当前价格分位 {percentile:.1f}% >= {self.hold_percentile}%（持有阈值）",
                    f"历史价格范围: min={min(history_prices):.4f}, max={max(history_prices):.4f}, current={current_price:.4f}"
                ],
                tags=["percentile_strategy", f"percentile_{percentile:.0f}%"]
            )
        else:
            # 中间区域，持有
            return Decision(
                action="HOLD",
                target_amount=0.0,
                reasons=[
                    f"当前价格分位 {percentile:.1f}% 在中间区域（{self.buy_percentile}% ~ {self.hold_percentile}%）",
                    f"历史价格范围: min={min(history_prices):.4f}, max={max(history_prices):.4f}, current={current_price:.4f}"
                ],
                tags=["percentile_strategy"]
            )
    
    def get_default_params(self) -> Dict[str, Any]:
        """获取默认参数"""
        return {
            "base_amount": self.DEFAULT_BASE_AMOUNT,
            "window": self.DEFAULT_WINDOW,
            "buy_percentile": self.DEFAULT_BUY_PERCENTILE,
            "hold_percentile": self.DEFAULT_HOLD_PERCENTILE
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
            "window": {
                "type": "int",
                "default": self.DEFAULT_WINDOW,
                "description": "滚动窗口大小（交易日数）",
                "min": 1,
                "max": 1000
            },
            "buy_percentile": {
                "type": "float",
                "default": self.DEFAULT_BUY_PERCENTILE,
                "description": "买入分位（低于此分位买入）",
                "min": 0.0,
                "max": 100.0
            },
            "hold_percentile": {
                "type": "float",
                "default": self.DEFAULT_HOLD_PERCENTILE,
                "description": "持有分位（高于此分位不买）",
                "min": 0.0,
                "max": 100.0
            }
        }

