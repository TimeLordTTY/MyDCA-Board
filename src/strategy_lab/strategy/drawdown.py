# -*- coding: utf-8 -*-
"""
DrawdownStrategy - 回撤触发加仓策略

根据相对高点回撤触发加仓，支持多档位。
"""

from typing import Dict, Any, Optional, List

from ..framework.base import Strategy
from ..framework.context import Context
from ..framework.decision import Decision
from ..framework.registry import register_strategy


@register_strategy("drawdown", version="default", set_as_default=True)
class DrawdownStrategy(Strategy):
    """
    回撤触发加仓策略
    
    参数：
    - base_amount: 基础买入金额
    - drawdown_thresholds: 回撤阈值列表（如 [0.02, 0.04, 0.08] 表示 2%, 4%, 8%）
    - use_ratios: 对应档位的使用比例（如 [0.3, 0.5, 1.0] 表示使用30%, 50%, 100%的可用资金）
    - reset_on_new_high: 净值新高时重置（默认 True）
    """
    
    strategy_key = "drawdown"
    strategy_version = "default"
    display_name = "回撤加仓策略"
    
    # 默认参数
    DEFAULT_BASE_AMOUNT = 1000.0
    DEFAULT_DRAWDOWN_THRESHOLDS = [0.02, 0.04, 0.08]  # 2%, 4%, 8%
    DEFAULT_USE_RATIOS = [0.3, 0.5, 1.0]  # 30%, 50%, 100%
    DEFAULT_RESET_ON_NEW_HIGH = True
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        super().__init__(config)
        
        # 基础买入金额
        self.base_amount = float(self.config.get("base_amount", self.DEFAULT_BASE_AMOUNT))
        
        # 回撤阈值（负数，如 -0.02 表示 2% 回撤）
        thresholds = self.config.get("drawdown_thresholds", self.DEFAULT_DRAWDOWN_THRESHOLDS)
        self.drawdown_thresholds = [float(t) for t in thresholds]
        
        # 使用比例
        ratios = self.config.get("use_ratios", self.DEFAULT_USE_RATIOS)
        self.use_ratios = [float(r) for r in ratios]
        
        # 确保阈值和比例数量一致
        if len(self.drawdown_thresholds) != len(self.use_ratios):
            # 使用较短的列表长度
            min_len = min(len(self.drawdown_thresholds), len(self.use_ratios))
            self.drawdown_thresholds = self.drawdown_thresholds[:min_len]
            self.use_ratios = self.use_ratios[:min_len]
        
        # 重置选项
        self.reset_on_new_high = bool(self.config.get("reset_on_new_high", self.DEFAULT_RESET_ON_NEW_HIGH))
        
        # 状态：记录历史最高净值
        self.state.setdefault("peak_nav", None)
        self.state.setdefault("triggered_levels", set())  # 已触发的档位索引
    
    def on_start(self) -> None:
        """初始化状态"""
        self.state["peak_nav"] = None
        self.state["triggered_levels"] = set()
    
    def on_day(self, ctx: Context) -> Decision:
        """
        处理每日行情
        
        Args:
            ctx: 策略上下文
        
        Returns:
            Decision: 买入决策或持有
        """
        current_nav = ctx.close
        
        # 初始化峰值
        if self.state["peak_nav"] is None:
            self.state["peak_nav"] = current_nav
            return Decision(
                action="HOLD",
                target_amount=0.0,
                reasons=["初始化峰值"],
                tags=["drawdown_strategy"]
            )
        
        # 检查是否创新高
        if current_nav > self.state["peak_nav"]:
            if self.reset_on_new_high:
                # 重置已触发的档位
                self.state["triggered_levels"] = set()
            self.state["peak_nav"] = current_nav
            return Decision(
                action="HOLD",
                target_amount=0.0,
                reasons=[f"净值创新高 {current_nav:.4f} > {self.state['peak_nav']:.4f}，重置触发档位"],
                tags=["drawdown_strategy", "new_high"]
            )
        
        # 计算回撤
        drawdown = (current_nav - self.state["peak_nav"]) / self.state["peak_nav"]
        
        # 检查是否触发加仓档位
        triggered_level = None
        for i, threshold in enumerate(self.drawdown_thresholds):
            if i in self.state["triggered_levels"]:
                continue  # 已触发过，跳过
            
            if drawdown <= threshold:  # 注意：drawdown 是负数，threshold 也是负数
                triggered_level = i
                break
        
        if triggered_level is not None:
            # 触发加仓
            use_ratio = self.use_ratios[triggered_level]
            threshold_pct = abs(self.drawdown_thresholds[triggered_level]) * 100
            
            # 计算买入金额（基于可用资金）
            available_cash = ctx.cash_pool + ctx.wait_pool
            target_amount = max(self.base_amount, available_cash * use_ratio)
            
            # 标记已触发
            self.state["triggered_levels"].add(triggered_level)
            
            return Decision(
                action="BUY",
                target_amount=target_amount,
                reasons=[
                    f"回撤 {abs(drawdown)*100:.2f}% 触发第 {triggered_level+1} 档加仓（阈值 {threshold_pct:.1f}%）",
                    f"使用比例 {use_ratio*100:.0f}%，目标金额 {target_amount:.2f}"
                ],
                tags=[f"drawdown_{threshold_pct:.0f}%", "drawdown_strategy"]
            )
        else:
            # 未触发
            drawdown_pct = abs(drawdown) * 100
            return Decision(
                action="HOLD",
                target_amount=0.0,
                reasons=[
                    f"当前回撤 {drawdown_pct:.2f}%，未触发加仓档位",
                    f"峰值 {self.state['peak_nav']:.4f}，当前 {current_nav:.4f}"
                ],
                tags=["drawdown_strategy"]
            )
    
    def get_default_params(self) -> Dict[str, Any]:
        """获取默认参数"""
        return {
            "base_amount": self.DEFAULT_BASE_AMOUNT,
            "drawdown_thresholds": self.DEFAULT_DRAWDOWN_THRESHOLDS,
            "use_ratios": self.DEFAULT_USE_RATIOS,
            "reset_on_new_high": self.DEFAULT_RESET_ON_NEW_HIGH
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
            "drawdown_thresholds": {
                "type": "str",
                "default": str(self.DEFAULT_DRAWDOWN_THRESHOLDS),
                "description": "回撤阈值列表（JSON格式，如 [0.02, 0.04, 0.08]）"
            },
            "use_ratios": {
                "type": "str",
                "default": str(self.DEFAULT_USE_RATIOS),
                "description": "使用比例列表（JSON格式，如 [0.3, 0.5, 1.0]）"
            },
            "reset_on_new_high": {
                "type": "bool",
                "default": self.DEFAULT_RESET_ON_NEW_HIGH,
                "description": "净值新高时重置"
            }
        }

