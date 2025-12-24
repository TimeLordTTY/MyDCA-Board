"""分类服务 - 从数据库读取分类配置"""
import logging
from typing import List, Dict

from data.db_connector import execute_query

logger = logging.getLogger(__name__)


def get_categories(entry_type: str = None) -> Dict:
    """
    从数据库获取分类配置（兼容旧接口格式）
    
    Args:
        entry_type: 记账类型（expense/income），None 表示返回全部
    
    Returns:
        分类字典，格式：{entry_type: {category_l1: [category_l2, ...]}}
    """
    sql = """
        SELECT entry_type, category_l1, category_l2, display_order
        FROM categories
        WHERE is_active = 1
    """
    params = []
    
    if entry_type:
        sql += " AND entry_type = %s"
        params.append(entry_type)
    
    sql += " ORDER BY entry_type, display_order, category_l1, category_l2"
    
    rows = execute_query(sql, tuple(params))
    
    # 转换为旧格式
    result = {}
    for row in rows:
        et = row['entry_type']
        l1 = row['category_l1']
        l2 = row['category_l2']
        
        if et not in result:
            result[et] = {}
        
        if l1 not in result[et]:
            result[et][l1] = []
        
        if l2:
            result[et][l1].append(l2)
    
    return result


def get_category_list(entry_type: str) -> List[Dict]:
    """
    获取分类列表（用于 UI 选择）
    
    Args:
        entry_type: 记账类型（expense/income）
    
    Returns:
        分类列表，每个元素包含 category_l1, category_l2
    """
    sql = """
        SELECT DISTINCT category_l1, category_l2
        FROM categories
        WHERE entry_type = %s AND is_active = 1
        ORDER BY category_l1, category_l2
    """
    return execute_query(sql, (entry_type,))

