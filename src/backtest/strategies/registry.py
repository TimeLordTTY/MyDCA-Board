# -*- coding: utf-8 -*-
"""
策略注册表

管理策略名称、版本和实现类的映射关系
"""

from typing import Dict, Type, Optional
from .base import Strategy


class StrategyRegistry:
    """
    策略注册表
    
    支持策略名称 + 版本号的二级索引：
    {
        "profit_recycle": {
            "default": ProfitRecycleStrategyV8,
            "v8": ProfitRecycleStrategyV8,
            "v10": ProfitRecycleStrategyV10,
        },
        "pure_sip": {
            "default": PureSipStrategy,
        }
    }
    """
    
    def __init__(self):
        self._registry: Dict[str, Dict[str, Type[Strategy]]] = {}
    
    def register(
        self,
        strategy_name: str,
        strategy_class: Type[Strategy],
        version: str = "default",
        set_as_default: bool = False
    ) -> None:
        """
        注册策略
        
        Args:
            strategy_name: 策略名称（如 "profit_recycle"）
            strategy_class: 策略类
            version: 版本号（如 "v8", "v10"）
            set_as_default: 是否设置为该策略名称的默认版本
        """
        if strategy_name not in self._registry:
            self._registry[strategy_name] = {}
        
        # 注册指定版本
        self._registry[strategy_name][version] = strategy_class
        
        # 如果是第一个注册或明确要求设为默认，则设置为 default
        if set_as_default or "default" not in self._registry[strategy_name]:
            self._registry[strategy_name]["default"] = strategy_class
    
    def get(
        self,
        strategy_name: str,
        version: Optional[str] = None
    ) -> Type[Strategy]:
        """
        获取策略类
        
        Args:
            strategy_name: 策略名称
            version: 版本号，None 则使用 default
        
        Returns:
            策略类
        
        Raises:
            ValueError: 策略名称或版本不存在时抛出
        """
        if strategy_name not in self._registry:
            available = list(self._registry.keys())
            raise ValueError(
                f"未知策略名称: '{strategy_name}'\n"
                f"可用策略: {available}"
            )
        
        version = version or "default"
        
        if version not in self._registry[strategy_name]:
            available_versions = list(self._registry[strategy_name].keys())
            raise ValueError(
                f"策略 '{strategy_name}' 不存在版本: '{version}'\n"
                f"可用版本: {available_versions}"
            )
        
        return self._registry[strategy_name][version]
    
    def list_strategies(self) -> Dict[str, list]:
        """
        列出所有可用的策略及其版本
        
        Returns:
            {strategy_name: [version1, version2, ...]}
        """
        return {
            name: list(versions.keys())
            for name, versions in self._registry.items()
        }
    
    def get_default_version(self, strategy_name: str) -> str:
        """
        获取策略的默认版本号（用于显示）
        
        Args:
            strategy_name: 策略名称
        
        Returns:
            默认版本号，如果是 "default" 则返回实际的版本标识
        """
        if strategy_name not in self._registry:
            raise ValueError(f"未知策略名称: '{strategy_name}'")
        
        default_class = self._registry[strategy_name]["default"]
        
        # 尝试从类属性获取版本号
        if hasattr(default_class, "strategy_version"):
            return default_class.strategy_version
        
        return "default"


# 全局策略注册表实例
STRATEGY_REGISTRY = StrategyRegistry()


def register_strategy(
    strategy_name: str,
    version: str = "default",
    set_as_default: bool = False
):
    """
    策略注册装饰器
    
    用法：
        @register_strategy("profit_recycle", version="v8", set_as_default=True)
        class ProfitRecycleStrategyV8(Strategy):
            ...
    
    Args:
        strategy_name: 策略名称
        version: 版本号
        set_as_default: 是否设置为默认版本
    """
    def decorator(cls: Type[Strategy]):
        STRATEGY_REGISTRY.register(strategy_name, cls, version, set_as_default)
        return cls
    return decorator

