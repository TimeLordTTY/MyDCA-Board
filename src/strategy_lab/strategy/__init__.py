# -*- coding: utf-8 -*-
"""策略实现模块

包含所有策略实现，框架文件已移至 framework/ 目录
"""

import importlib.util
import sys
from pathlib import Path

# 导入所有策略类以确保它们被注册
from .simple import SimpleStrategy
from .drawdown import DrawdownStrategy
from .percentile import PercentileStrategy

# 自动加载策略目录中的所有策略文件
_strategy_dir = Path(__file__).parent
for file_path in _strategy_dir.glob("*.py"):
    if file_path.name.startswith("_") or file_path.name == "__init__.py":
        continue
    
    # 跳过已导入的模块
    module_name = f"strategy_lab.strategy.{file_path.stem}"
    if module_name in sys.modules:
        continue
    
    try:
        spec = importlib.util.spec_from_file_location(module_name, file_path)
        if spec and spec.loader:
            module = importlib.util.module_from_spec(spec)
            # 将模块添加到 sys.modules，避免重复加载
            sys.modules[module_name] = module
            spec.loader.exec_module(module)
    except Exception as e:
        # 忽略加载失败的文件（可能是语法错误等）
        import logging
        logging.getLogger(__name__).warning(f"加载策略文件失败: {file_path.name}, error={e}")
        pass

# 从框架模块导出注册表函数
from ..framework.registry import list_strategies, get_strategy, register_strategy, get_strategy_info

__all__ = [
    'SimpleStrategy',
    'DrawdownStrategy',
    'PercentileStrategy',
    'list_strategies',
    'get_strategy',
    'register_strategy',
    'get_strategy_info'
]

