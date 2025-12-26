# -*- coding: utf-8 -*-
"""
策略注册表

管理策略名称、版本和实现类的映射关系。
"""

from typing import Dict, Type, Optional, List
from .base import Strategy


class StrategyRegistry:
    """
    策略注册表
    
    支持策略名称 + 版本号的二级索引：
    {
        "simple": {
            "default": SimpleStrategy,
            "v1": SimpleStrategyV1,
        },
        "drawdown": {
            "default": DrawdownStrategy,
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
            strategy_name: 策略名称（如 "simple"）
            strategy_class: 策略类
            version: 版本号（如 "v1", "default"）
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
            version: 版本号，None 则使用默认版本
        
        Returns:
            策略类
        
        Raises:
            ValueError: 如果策略不存在
        """
        if strategy_name not in self._registry:
            raise ValueError(f"策略不存在: {strategy_name}")
        
        versions = self._registry[strategy_name]
        version_key = version or "default"
        
        if version_key not in versions:
            raise ValueError(f"策略版本不存在: {strategy_name}@{version_key}")
        
        return versions[version_key]
    
    def list_strategies(self) -> Dict[str, List[str]]:
        """
        列出所有已注册的策略
        
        Returns:
            {策略名称: [版本列表]}
        """
        return {
            name: list(versions.keys())
            for name, versions in self._registry.items()
        }


# 全局注册表实例
_registry = StrategyRegistry()


def register_strategy(
    strategy_name: str,
    version: str = "default",
    set_as_default: bool = False
):
    """
    策略注册装饰器
    
    Usage:
        @register_strategy("simple", version="v1")
        class SimpleStrategy(Strategy):
            ...
    """
    def decorator(cls: Type[Strategy]):
        _registry.register(strategy_name, cls, version, set_as_default)
        return cls
    return decorator


def get_strategy(strategy_name: str, version: Optional[str] = None) -> Type[Strategy]:
    """获取策略类（便捷函数）"""
    return _registry.get(strategy_name, version)


def list_strategies() -> Dict[str, List[str]]:
    """列出所有策略（便捷函数）"""
    return _registry.list_strategies()

