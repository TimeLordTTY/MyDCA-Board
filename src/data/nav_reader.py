#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
净值读取模块

仅支持 MySQL 数据库存储：nav 表
"""
from pathlib import Path
from datetime import date
from decimal import Decimal
from typing import Optional, Dict, List
import logging

from data.config_loader import get_project_root, load_products
from data.db_connector import execute_query, execute_one, execute_insert, execute_many

logger = logging.getLogger(__name__)


def load_nav_history(product_code: str) -> Dict[str, Decimal]:
    """加载产品的所有历史净值
    
    Returns:
        Dict[str, Decimal]: {nav_date: nav}
    """
    sql = """
        SELECT DATE_FORMAT(nav_date, '%%Y-%%m-%%d') as nav_date, nav
        FROM nav
        WHERE product_code = %s
        ORDER BY nav_date
    """
    rows = execute_query(sql, (product_code,))
    
    nav_map = {}
    for row in rows:
        nav_date = row.get('nav_date')
        nav_str = row.get('nav')
        if nav_date and nav_str:
            try:
                nav_map[nav_date] = Decimal(str(nav_str))
            except:
                pass
    
    return nav_map


def get_nav(product_code: str, nav_date: str) -> Optional[Decimal]:
    """获取指定日期的净值
    
    Args:
        product_code: 产品代码
        nav_date: 净值日期 (YYYY-MM-DD)
    
    Returns:
        Optional[Decimal]: 净值，如果不存在返回 None
    """
    sql = "SELECT nav FROM nav WHERE product_code = %s AND nav_date = %s"
    result = execute_one(sql, (product_code, nav_date))
    
    if result and result.get('nav'):
        try:
            return Decimal(str(result['nav']))
        except:
            pass
    return None


def get_latest_nav(product_code: str) -> Optional[tuple]:
    """获取最新净值
    
    Returns:
        Optional[tuple]: (nav_date, nav) 或 None
    """
    sql = """
        SELECT DATE_FORMAT(nav_date, '%%Y-%%m-%%d') as nav_date, nav
        FROM nav
        WHERE product_code = %s
        ORDER BY nav_date DESC
        LIMIT 1
    """
    result = execute_one(sql, (product_code,))
    
    if result and result.get('nav_date') and result.get('nav'):
        try:
            return (result['nav_date'], Decimal(str(result['nav'])))
        except:
            pass
    return None


def save_nav(product_code: str, nav_date: str, nav: Decimal, 
             acc_nav: Decimal = None, daily_return: Decimal = None) -> None:
    """保存净值数据"""
    sql = """
        INSERT INTO nav (product_code, nav_date, nav, acc_nav, daily_return)
        VALUES (%s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE 
            nav = VALUES(nav),
            acc_nav = VALUES(acc_nav),
            daily_return = VALUES(daily_return),
            fetched_at = CURRENT_TIMESTAMP
    """
    execute_insert(sql, (
        product_code, 
        nav_date, 
        str(nav),
        str(acc_nav) if acc_nav else None,
        str(daily_return) if daily_return else None
    ))


def batch_save_nav(records: List[Dict]) -> int:
    """批量保存净值数据
    
    Args:
        records: [{'product_code': str, 'nav_date': str, 'nav': Decimal, ...}, ...]
    
    Returns:
        int: 保存的记录数
    """
    if not records:
        return 0
    
    sql = """
        INSERT INTO nav (product_code, nav_date, nav, acc_nav, daily_return)
        VALUES (%s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE 
            nav = VALUES(nav),
            acc_nav = VALUES(acc_nav),
            daily_return = VALUES(daily_return),
            fetched_at = CURRENT_TIMESTAMP
    """
    
    params_list = []
    for r in records:
        params_list.append((
            r.get('product_code'),
            r.get('nav_date'),
            str(r.get('nav', 0)),
            str(r.get('acc_nav')) if r.get('acc_nav') else None,
            str(r.get('daily_return')) if r.get('daily_return') else None
        ))
    
    return execute_many(sql, params_list)


if __name__ == "__main__":
    # 简单测试
    logging.basicConfig(level=logging.INFO)
    
    products = load_products()
    for p in products[:3]:
        code = p['product_code']
        name = p['product_name']
        latest = get_latest_nav(code)
        if latest:
            print(f"{code} ({name}): 最新净值 {latest[0]} = {latest[1]}")
        else:
            print(f"{code} ({name}): 无净值数据")
