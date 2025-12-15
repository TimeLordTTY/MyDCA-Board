"""
适配器模块
支持多种理财产品数据源
"""

# 导出适配器模块，方便导入
from . import cmbc_client

__all__ = ['cmbc_client']

