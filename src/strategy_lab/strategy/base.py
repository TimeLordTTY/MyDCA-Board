# -*- coding: utf-8 -*-
"""
Strategy 基类

所有策略都应继承此类并实现 on_day 方法。
"""

from abc import ABC, abstractmethod
from typing import Dict, Any, List

from .context import Context
from .decision import Decision


class Strategy(ABC):
    """
    策略基类
    
    所有策略都应继承此类并实现 on_day 方法
    
    设计原则：
    - 引擎负责构造 Context（含今日行情、组合状态、现金流等）
    - 策略只负责根据 Context 决定本日买卖多少，并返回 Decision
    - 引擎绝对不包含任何策略判断逻辑
    
    Attributes:
        config: 策略配置字典
        state: 策略内部状态字典
        strategy_key: 策略标识（如 "simple", "drawdown"）
        strategy_version: 策略版本（如 "v1", "default"）
        display_name: 策略展示名称（不包含版本号）
    """
    
    # 类属性：子类应该覆盖这些
    strategy_key: str = "unknown"
    strategy_version: str = "default"
    display_name: str = "未命名策略"
    
    def __init__(self, config: Dict[str, Any] = None):
        """
        初始化策略
        
        Args:
            config: 策略配置字典，由具体策略定义其内容
        """
        self.config = config or {}
        self.state: Dict[str, Any] = {}
    
    @abstractmethod
    def on_day(self, ctx: Context) -> Decision:
        """
        处理每日行情
        
        每个交易日调用一次，策略根据当前上下文决定交易操作
        
        Args:
            ctx: 策略上下文，包含当日行情、组合状态、现金流等信息
        
        Returns:
            Decision: 交易决策，指示买入金额和原因
        """
        pass
    
    def on_start(self) -> None:
        """
        回测开始时调用
        
        可选重写，用于初始化策略状态
        """
        pass
    
    def on_end(self) -> None:
        """
        回测结束时调用
        
        可选重写，用于清理或统计
        """
        pass
    
    def get_name(self) -> str:
        """
        获取策略展示名称（不包含版本号）
        
        Returns:
            策略展示名称
        """
        return self.display_name
    
    def get_strategy_key(self) -> str:
        """
        获取策略标识（用于注册表查找）
        
        Returns:
            策略标识
        """
        return self.strategy_key
    
    def get_version(self) -> str:
        """
        获取策略版本号
        
        Returns:
            版本号
        """
        return self.strategy_version
    
    def get_stats(self) -> Dict[str, Any]:
        """
        获取策略统计数据
        
        Returns:
            统计数据字典
        """
        return {}


