#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
数据库连接模块

提供 MySQL 连接池和基础数据库操作
"""
import json
import logging
from pathlib import Path
from typing import Dict, List, Optional, Any
from contextlib import contextmanager
from decimal import Decimal

import pymysql
from pymysql.cursors import DictCursor
from dbutils.pooled_db import PooledDB

logger = logging.getLogger(__name__)

# 全局连接池
_pool: Optional[PooledDB] = None
_config: Optional[Dict] = None


def get_project_root() -> Path:
    """获取项目根目录"""
    current = Path(__file__).resolve()
    # src/data/db_connector.py -> 项目根目录
    return current.parent.parent.parent


def load_db_config() -> Dict:
    """加载数据库配置"""
    global _config
    if _config is not None:
        return _config
    
    config_path = get_project_root() / "config" / "db_config.json"
    
    if not config_path.exists():
        raise FileNotFoundError(f"数据库配置文件不存在: {config_path}")
    
    with open(config_path, 'r', encoding='utf-8') as f:
        _config = json.load(f)
    
    return _config


def is_database_enabled() -> bool:
    """检查是否启用数据库模式"""
    try:
        config = load_db_config()
        return config.get('use_database', False)
    except FileNotFoundError:
        return False


def get_pool() -> PooledDB:
    """获取数据库连接池"""
    global _pool
    
    if _pool is not None:
        return _pool
    
    config = load_db_config()
    
    _pool = PooledDB(
        creator=pymysql,
        maxconnections=config.get('pool_size', 5),
        mincached=1,
        maxcached=3,
        blocking=True,
        host=config['host'],
        port=config['port'],
        user=config['user'],
        password=config['password'],
        database=config['database'],
        charset=config.get('charset', 'utf8mb4'),
        cursorclass=DictCursor,
        autocommit=True
    )
    
    logger.info(f"数据库连接池已创建: {config['host']}:{config['port']}/{config['database']}")
    return _pool


@contextmanager
def get_connection():
    """获取数据库连接（上下文管理器）"""
    pool = get_pool()
    conn = pool.connection()
    try:
        yield conn
    finally:
        conn.close()


@contextmanager
def get_cursor():
    """获取数据库游标（上下文管理器）"""
    with get_connection() as conn:
        cursor = conn.cursor()
        try:
            yield cursor
        finally:
            cursor.close()


def execute_query(sql: str, params: tuple = None) -> List[Dict]:
    """执行查询并返回结果"""
    with get_cursor() as cursor:
        cursor.execute(sql, params or ())
        results = cursor.fetchall()
        # 转换 Decimal 为字符串以保持精度
        return [_convert_row(row) for row in results]


def execute_one(sql: str, params: tuple = None) -> Optional[Dict]:
    """执行查询并返回单条结果"""
    with get_cursor() as cursor:
        cursor.execute(sql, params or ())
        row = cursor.fetchone()
        return _convert_row(row) if row else None


def execute_update(sql: str, params: tuple = None) -> int:
    """执行更新/插入/删除并返回影响行数"""
    with get_connection() as conn:
        with conn.cursor() as cursor:
            cursor.execute(sql, params or ())
            conn.commit()
            return cursor.rowcount


def execute_insert(sql: str, params: tuple = None) -> int:
    """执行插入并返回自增ID"""
    with get_connection() as conn:
        with conn.cursor() as cursor:
            cursor.execute(sql, params or ())
            conn.commit()
            return cursor.lastrowid


def execute_many(sql: str, params_list: List[tuple]) -> int:
    """批量执行"""
    if not params_list:
        return 0
    
    with get_connection() as conn:
        with conn.cursor() as cursor:
            cursor.executemany(sql, params_list)
            conn.commit()
            return cursor.rowcount


def _convert_row(row: Dict) -> Dict:
    """转换行数据，将 Decimal 转为字符串"""
    if row is None:
        return None
    
    result = {}
    for key, value in row.items():
        if isinstance(value, Decimal):
            result[key] = str(value)
        else:
            result[key] = value
    return result


def test_connection() -> bool:
    """测试数据库连接"""
    try:
        result = execute_one("SELECT 1 as test")
        return result is not None and result.get('test') == 1
    except Exception as e:
        logger.error(f"数据库连接测试失败: {e}")
        return False


def close_pool():
    """关闭连接池"""
    global _pool
    if _pool is not None:
        _pool.close()
        _pool = None
        logger.info("数据库连接池已关闭")


# ============================================================
# 表操作工具函数
# ============================================================

def table_exists(table_name: str) -> bool:
    """检查表是否存在"""
    sql = """
        SELECT COUNT(*) as cnt 
        FROM information_schema.tables 
        WHERE table_schema = %s AND table_name = %s
    """
    config = load_db_config()
    result = execute_one(sql, (config['database'], table_name))
    return result and int(result.get('cnt', 0)) > 0


def get_row_count(table_name: str) -> int:
    """获取表行数"""
    sql = f"SELECT COUNT(*) as cnt FROM {table_name}"
    result = execute_one(sql)
    return int(result.get('cnt', 0)) if result else 0


if __name__ == "__main__":
    # 测试连接
    logging.basicConfig(level=logging.INFO)
    
    print("测试数据库连接...")
    if test_connection():
        print("✓ 数据库连接成功")
        
        # 检查表
        tables = ['transactions', 'orders', 'ledger', 'daily_snapshot', 'daily_balance', 'nav']
        for t in tables:
            exists = table_exists(t)
            print(f"  - {t}: {'存在' if exists else '不存在'}")
    else:
        print("✗ 数据库连接失败")


