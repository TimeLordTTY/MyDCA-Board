# -*- coding: utf-8 -*-
"""
净值范围管理模块

用于管理和更新每个产品的净值日期范围配置。
数据存储在数据库 product_nav_range 表中。
"""

from datetime import datetime, date
from typing import Dict, Optional

from data.db_connector import execute_query, execute_one, execute_insert, execute_update


def load_nav_range() -> Dict:
    """
    从数据库加载所有产品的净值范围配置
    
    Returns:
        净值范围字典，格式：{product_code: {product_name, earliest_nav_date, latest_nav_date, record_count, updated_at}}
    """
    sql = """
        SELECT 
            product_code, product_name, 
            earliest_nav_date, latest_nav_date, record_count,
            updated_at
        FROM product_nav_range
        ORDER BY product_code
    """
    rows = execute_query(sql)
    
    result = {}
    for row in rows:
        product_code = row.get('product_code')
        if not product_code:
            continue
        
        # 格式化日期为字符串
        earliest = row.get('earliest_nav_date')
        latest = row.get('latest_nav_date')
        
        result[product_code] = {
            'product_name': row.get('product_name', ''),
            'earliest_nav_date': earliest.strftime('%Y-%m-%d') if earliest else None,
            'latest_nav_date': latest.strftime('%Y-%m-%d') if latest else None,
            'record_count': int(row.get('record_count', 0)),
            'updated_at': row.get('updated_at').strftime('%Y-%m-%d %H:%M:%S') if row.get('updated_at') else None
        }
    
    return result


def save_nav_range_item(product_code: str, nav_range_info: Dict) -> bool:
    """
    保存单个产品的净值范围到数据库
    
    Args:
        product_code: 产品代码
        nav_range_info: 净值范围信息字典，包含：
            - product_name: 产品名称
            - earliest_nav_date: 最早净值日期（YYYY-MM-DD 字符串或 date 对象）
            - latest_nav_date: 最新净值日期（YYYY-MM-DD 字符串或 date 对象）
            - record_count: 记录数
    
    Returns:
        是否成功
    """
    try:
        # 转换日期格式
        earliest_str = nav_range_info.get('earliest_nav_date')
        latest_str = nav_range_info.get('latest_nav_date')
        
        earliest_date = None
        latest_date = None
        
        if earliest_str:
            if isinstance(earliest_str, str):
                earliest_date = datetime.strptime(earliest_str, '%Y-%m-%d').date()
            elif isinstance(earliest_str, date):
                earliest_date = earliest_str
        
        if latest_str:
            if isinstance(latest_str, str):
                latest_date = datetime.strptime(latest_str, '%Y-%m-%d').date()
            elif isinstance(latest_str, date):
                latest_date = latest_str
        
        sql = """
            INSERT INTO product_nav_range (
                product_code, product_name, earliest_nav_date, latest_nav_date, record_count
            ) VALUES (
                %s, %s, %s, %s, %s
            )
            ON DUPLICATE KEY UPDATE
                product_name = VALUES(product_name),
                earliest_nav_date = VALUES(earliest_nav_date),
                latest_nav_date = VALUES(latest_nav_date),
                record_count = VALUES(record_count),
                updated_at = CURRENT_TIMESTAMP
        """
        
        params = (
            product_code,
            nav_range_info.get('product_name', ''),
            earliest_date,
            latest_date,
            int(nav_range_info.get('record_count', 0))
        )
        
        execute_insert(sql, params)
        return True
        
    except Exception as e:
        import logging
        logger = logging.getLogger(__name__)
        logger.error(f"保存净值范围失败: product_code={product_code}, error={e}", exc_info=True)
        return False


def scan_nav_db(product_code: str) -> Dict:
    """
    从数据库扫描产品的净值日期范围
    :param product_code: 产品代码
    :return: {earliest_nav_date, latest_nav_date, record_count}
    """
    sql = """
        SELECT MIN(DATE_FORMAT(nav_date, '%%Y-%%m-%%d')) as earliest,
               MAX(DATE_FORMAT(nav_date, '%%Y-%%m-%%d')) as latest,
               COUNT(*) as cnt
        FROM nav
        WHERE product_code = %s
    """
    result = execute_one(sql, (product_code,))
    
    if result and result.get('cnt', 0) > 0:
        return {
            'earliest_nav_date': result.get('earliest'),
            'latest_nav_date': result.get('latest'),
            'record_count': int(result.get('cnt', 0))
        }
    
    return {
        'earliest_nav_date': None,
        'latest_nav_date': None,
        'record_count': 0
    }


def update_product_nav_range(product_code: str, product_name: str = None) -> Dict:
    """
    更新单个产品的净值范围（从数据库读取）
    
    Args:
        product_code: 产品代码
        product_name: 产品名称（可选）
    
    Returns:
        更新后的产品净值范围信息
    """
    # 从数据库获取日期范围
    range_info = scan_nav_db(product_code)
    
    # 如果没有提供 product_name，从数据库 products 表获取
    if not product_name:
        from data.product_service import get_product_by_code
        product = get_product_by_code(product_code)
        if product:
            product_name = product.get('product_name', product_code)
        else:
            product_name = product_code
    
    # 构建净值范围信息
    nav_range_info = {
        'product_name': product_name,
        'earliest_nav_date': range_info['earliest_nav_date'],
        'latest_nav_date': range_info['latest_nav_date'],
        'record_count': range_info['record_count']
    }
    
    # 保存到数据库
    save_nav_range_item(product_code, nav_range_info)
    
    # 返回格式化的信息（兼容旧接口）
    return {
        'product_name': product_name,
        'earliest_nav_date': range_info['earliest_nav_date'],
        'latest_nav_date': range_info['latest_nav_date'],
        'record_count': range_info['record_count'],
        'updated_at': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    }


def update_all_nav_ranges() -> Dict:
    """
    更新所有产品的净值范围（从数据库读取）
    
    Returns:
        完整的净值范围配置字典
    """
    # 从数据库获取所有产品代码
    sql = "SELECT DISTINCT product_code FROM nav"
    products = execute_query(sql)
    
    # 获取产品名称映射（从 products 表）
    from data.product_service import get_products
    product_names = {}
    for p in get_products():
        product_names[p.get('code', '')] = p.get('product_name', '')
    
    # 更新每个产品的净值范围
    for row in products:
        product_code = row.get('product_code')
        if not product_code:
            continue
        
        range_info = scan_nav_db(product_code)
        product_name = product_names.get(product_code, product_code)
        
        nav_range_info = {
            'product_name': product_name,
            'earliest_nav_date': range_info['earliest_nav_date'],
            'latest_nav_date': range_info['latest_nav_date'],
            'record_count': range_info['record_count']
        }
        
        save_nav_range_item(product_code, nav_range_info)
    
    # 返回所有净值范围（从数据库加载）
    return load_nav_range()


def get_nav_range(product_code: str) -> Optional[Dict]:
    """
    从数据库获取单个产品的净值范围
    
    Args:
        product_code: 产品代码
    
    Returns:
        净值范围信息或 None，格式：{product_name, earliest_nav_date, latest_nav_date, record_count, updated_at}
    """
    sql = """
        SELECT 
            product_code, product_name,
            earliest_nav_date, latest_nav_date, record_count,
            updated_at
        FROM product_nav_range
        WHERE product_code = %s
    """
    row = execute_one(sql, (product_code,))
    
    if not row:
        return None
    
    # 格式化日期为字符串
    earliest = row.get('earliest_nav_date')
    latest = row.get('latest_nav_date')
    
    return {
        'product_name': row.get('product_name', ''),
        'earliest_nav_date': earliest.strftime('%Y-%m-%d') if earliest else None,
        'latest_nav_date': latest.strftime('%Y-%m-%d') if latest else None,
        'record_count': int(row.get('record_count', 0)),
        'updated_at': row.get('updated_at').strftime('%Y-%m-%d %H:%M:%S') if row.get('updated_at') else None
    }


def print_nav_range_summary():
    """打印净值范围摘要"""
    nav_range = load_nav_range()
    
    if not nav_range:
        print("暂无净值范围配置")
        return
    
    print(f"\n{'=' * 80}")
    print("净值范围配置摘要")
    print(f"{'=' * 80}")
    print(f"{'产品代码':<15} {'最早日期':<12} {'最新日期':<12} {'记录数':<8} {'产品名称'}")
    print(f"{'-' * 80}")
    
    for code, info in sorted(nav_range.items()):
        earliest = info.get('earliest_nav_date') or '-'
        latest = info.get('latest_nav_date') or '-'
        count = info.get('record_count', 0)
        name = info.get('product_name', '')[:30]
        print(f"{code:<15} {earliest:<12} {latest:<12} {count:<8} {name}")
    
    print(f"{'=' * 80}")


if __name__ == "__main__":
    # 测试：扫描并更新所有净值范围
    print("扫描并更新所有产品的净值范围...")
    update_all_nav_ranges()
    print_nav_range_summary()
