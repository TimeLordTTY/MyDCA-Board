# -*- coding: utf-8 -*-
"""
策略管理服务

提供策略的加载、保存、参数编辑等功能。
"""

import os
import json
import importlib
import importlib.util
import sys
from pathlib import Path
from typing import Dict, Any, Optional, List
import logging

from strategy_lab.framework.registry import register_strategy, get_strategy_info, list_strategies
from strategy_lab.framework.base import Strategy

logger = logging.getLogger(__name__)


def get_strategy_dir() -> Path:
    """获取策略目录路径"""
    project_root = Path(__file__).parent.parent.parent
    strategy_dir = project_root / "src" / "strategy_lab" / "strategy"
    return strategy_dir


def list_strategy_files() -> List[Dict[str, Any]]:
    """
    列出策略目录中的所有策略文件
    
    Returns:
        策略文件列表，每个元素包含：filename, strategy_key, strategy_version, display_name
    """
    strategy_dir = get_strategy_dir()
    if not strategy_dir.exists():
        return []
    
    strategies = []
    
    # 获取已注册的策略信息
    registered = list_strategies()
    
    # 扫描策略目录
    for file_path in strategy_dir.glob("*.py"):
        if file_path.name.startswith("_") or file_path.name == "__init__.py":
            continue
        
        try:
            # 尝试加载策略文件获取信息
            module_name = f"strategy_lab.strategy.{file_path.stem}"
            if module_name in sys.modules:
                module = sys.modules[module_name]
            else:
                spec = importlib.util.spec_from_file_location(module_name, file_path)
                if spec and spec.loader:
                    module = importlib.util.module_from_spec(spec)
                    spec.loader.exec_module(module)
                else:
                    continue
            
            # 查找策略类
            for attr_name in dir(module):
                attr = getattr(module, attr_name)
                if (isinstance(attr, type) and 
                    issubclass(attr, Strategy) and 
                    attr != Strategy and
                    hasattr(attr, 'strategy_key')):
                    
                    strategy_key = attr.strategy_key
                    strategy_version = getattr(attr, 'strategy_version', 'default')
                    display_name = getattr(attr, 'display_name', attr_name)
                    
                    strategies.append({
                        'filename': file_path.name,
                        'strategy_key': strategy_key,
                        'strategy_version': strategy_version,
                        'display_name': display_name,
                        'class_name': attr_name
                    })
                    break
        
        except Exception as e:
            logger.warning(f"加载策略文件失败: {file_path.name}, error={e}")
            continue
    
    return strategies


def save_strategy(
    strategy_key: str,
    strategy_code: str = None,
    display_name: str = None,
    strategy_version: str = "default",
    overwrite: bool = False
) -> Dict[str, Any]:
    """
    保存策略到文件
    
    Args:
        strategy_key: 策略标识
        strategy_code: 策略代码（如果为None，使用模板）
        display_name: 策略显示名称
        strategy_version: 策略版本
        overwrite: 是否覆盖已存在的文件
    
    Returns:
        保存结果字典
    """
    try:
        strategy_dir = get_strategy_dir()
        strategy_dir.mkdir(parents=True, exist_ok=True)
        
        # 生成文件名
        filename = f"{strategy_key}.py"
        file_path = strategy_dir / filename
        
        # 如果文件已存在，检查是否覆盖
        if file_path.exists() and not overwrite:
            return {
                'success': False,
                'error': f'策略文件已存在: {filename}。如需覆盖，请勾选"覆盖已存在文件"选项。'
            }
        
        # 如果提供了自定义代码，使用自定义代码；否则使用模板
        if strategy_code and strategy_code.strip():
            strategy_content = strategy_code
        else:
            # 生成策略代码模板
            if not display_name:
                display_name = strategy_key.replace('_', ' ').title()
            
            strategy_content = f'''# -*- coding: utf-8 -*-
"""
{display_name} - 自定义策略

策略标识: {strategy_key}
策略版本: {strategy_version}
"""

from typing import Dict, Any, Optional
from ..framework.base import Strategy
from ..framework.context import Context
from ..framework.decision import Decision
from ..framework.registry import register_strategy


@register_strategy("{strategy_key}", version="{strategy_version}", set_as_default=True)
class {strategy_key.title().replace('_', '')}Strategy(Strategy):
    """
    {display_name}
    
    自定义策略实现
    """
    
    strategy_key = "{strategy_key}"
    strategy_version = "{strategy_version}"
    display_name = "{display_name}"
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        super().__init__(config)
        # TODO: 初始化策略参数
    
    def get_default_params(self) -> Dict[str, Any]:
        """获取默认参数"""
        return {{
            # TODO: 定义默认参数
        }}
    
    def get_param_schema(self) -> Dict[str, Any]:
        """获取参数 schema"""
        return {{
            # TODO: 定义参数 schema
        }}
    
    def on_day(self, ctx: Context) -> Decision:
        """
        处理每日行情
        
        Args:
            ctx: 策略上下文
        
        Returns:
            Decision: 交易决策
        """
        # TODO: 实现策略逻辑
        return Decision(
            action="HOLD",
            target_amount=0.0,
            reasons=["策略未实现"],
            tags=["custom"]
        )
'''
        
        # 写入文件
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(strategy_content)
        
        # 重新加载模块以注册策略
        try:
            module_name = f"strategy_lab.strategy.{strategy_key}"
            
            # 直接加载策略模块
            spec = importlib.util.spec_from_file_location(module_name, file_path)
            if spec and spec.loader:
                # 如果模块已存在，先删除
                if module_name in sys.modules:
                    del sys.modules[module_name]
                
                module = importlib.util.module_from_spec(spec)
                # 将模块添加到 sys.modules，避免重复加载
                sys.modules[module_name] = module
                spec.loader.exec_module(module)
                
                # 验证策略是否已注册
                from strategy_lab.framework.registry import list_strategies
                strategies = list_strategies()
                if strategy_key in strategies:
                    logger.info(f"策略已保存并注册: {strategy_key}")
                else:
                    # 如果未注册，尝试重新导入 __init__.py
                    init_module = "strategy_lab.strategy"
                    try:
                        if init_module in sys.modules:
                            # 不直接 reload，而是重新执行自动加载逻辑
                            # 因为 reload 可能导致问题
                            pass
                        else:
                            # 如果 __init__.py 未导入，导入它
                            import strategy_lab.strategy
                        
                        # 再次检查
                        strategies = list_strategies()
                        if strategy_key in strategies:
                            logger.info(f"策略已保存并注册: {strategy_key}")
                        else:
                            logger.warning(f"策略已保存但未在注册表中找到: {strategy_key}，可能需要重启应用")
                    except Exception as reload_error:
                        logger.warning(f"重新加载策略模块失败: {reload_error}，策略文件已保存，可能需要重启应用")
            else:
                logger.warning(f"无法创建模块规范: {module_name}, spec={spec}")
        except Exception as e:
            logger.warning(f"策略保存成功但注册失败: {e}", exc_info=True)
        
        return {
            'success': True,
            'filename': filename,
            'file_path': str(file_path)
        }
    
    except Exception as e:
        logger.error(f"保存策略失败: {e}", exc_info=True)
        return {
            'success': False,
            'error': str(e)
        }


def load_strategy_code(strategy_key: str) -> Optional[str]:
    """
    加载策略代码
    
    Args:
        strategy_key: 策略标识
    
    Returns:
        策略代码字符串，如果不存在返回 None
    """
    strategy_dir = get_strategy_dir()
    filename = f"{strategy_key}.py"
    file_path = strategy_dir / filename
    
    if not file_path.exists():
        return None
    
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            return f.read()
    except Exception as e:
        logger.error(f"加载策略代码失败: {e}")
        return None


def delete_strategy(strategy_key: str) -> Dict[str, Any]:
    """
    删除策略文件
    
    Args:
        strategy_key: 策略标识
    
    Returns:
        删除结果字典
    """
    try:
        strategy_dir = get_strategy_dir()
        filename = f"{strategy_key}.py"
        file_path = strategy_dir / filename
        
        if not file_path.exists():
            return {
                'success': False,
                'error': f'策略文件不存在: {filename}'
            }
        
        # 删除文件
        file_path.unlink()
        
        # 从模块缓存中移除
        module_name = f"strategy_lab.strategy.{strategy_key}"
        if module_name in sys.modules:
            del sys.modules[module_name]
        
        return {
            'success': True,
            'message': f'策略已删除: {filename}'
        }
    
    except Exception as e:
        logger.error(f"删除策略失败: {e}", exc_info=True)
        return {
            'success': False,
            'error': str(e)
        }

