"""数据校验模块 - 确保数据质量和配置正确性"""
import re
from datetime import datetime

# 必需字段定义
REQUIRED_NAV_FIELDS = ['PRODUCT_CODE', 'ISS_DATE', 'NAV', 'fetched_at']

def validate_date_format(date_str):
    """
    校验日期格式是否为 YYYY-MM-DD
    :param date_str: 日期字符串
    :return: True/False
    """
    pattern = r'^\d{4}-\d{2}-\d{2}$'
    if not re.match(pattern, date_str):
        return False
    try:
        datetime.strptime(date_str, '%Y-%m-%d')
        return True
    except ValueError:
        return False

def validate_nav_record(nav_record, product_code):
    """
    校验单条净值记录是否完整
    :param nav_record: 净值记录字典
    :param product_code: 产品代码（用于错误信息）
    :return: (is_valid, error_message)
    """
    if not isinstance(nav_record, dict):
        return False, f"记录类型错误: 期望dict，实际{type(nav_record)}"
    
    # 检查必需字段
    missing_fields = [f for f in REQUIRED_NAV_FIELDS if f not in nav_record]
    if missing_fields:
        return False, f"缺少必需字段: {', '.join(missing_fields)}"
    
    # 校验日期格式
    iss_date = nav_record.get('ISS_DATE')
    if not validate_date_format(iss_date):
        return False, f"ISS_DATE格式错误: {iss_date}，应为YYYY-MM-DD"
    
    # 校验NAV是否为数字
    try:
        float(nav_record['NAV'])
    except (ValueError, TypeError):
        return False, f"NAV不是有效数字: {nav_record['NAV']}"
    
    # 校验PRODUCT_CODE一致性
    if nav_record['PRODUCT_CODE'] != product_code:
        return False, f"PRODUCT_CODE不匹配: 期望{product_code}，实际{nav_record['PRODUCT_CODE']}"
    
    return True, None

def validate_product_config(product):
    """
    校验产品配置是否完整
    :param product: 产品配置字典
    :return: (is_valid, error_message)
    """
    required_fields = ['id', 'name', 'source']
    missing_fields = [f for f in required_fields if f not in product]
    if missing_fields:
        return False, f"缺少必需字段: {', '.join(missing_fields)}"
    return True, None

def validate_holdings_config(holdings, products):
    """
    校验持仓配置的产品ID是否都存在于产品列表中
    :param holdings: 持仓配置列表
    :param products: 产品配置列表
    :return: (is_valid, error_message)
    """
    product_ids = {p['id'] for p in products}
    invalid_ids = []
    
    for holding in holdings:
        holding_id = holding.get('products_id')
        if holding_id not in product_ids:
            invalid_ids.append(holding_id)
    
    if invalid_ids:
        return False, f"holdings.json中包含不存在的产品ID: {', '.join(invalid_ids)}"
    
    return True, None

def validate_adaptor_exists(source, adaptor_map):
    """
    校验数据源是否有对应的适配器
    :param source: 数据源名称
    :param adaptor_map: 适配器映射表
    :return: (is_valid, error_message)
    """
    if source not in adaptor_map:
        available = ', '.join(adaptor_map.keys())
        return False, f"不支持的数据源: {source}，可用: {available}"
    return True, None

