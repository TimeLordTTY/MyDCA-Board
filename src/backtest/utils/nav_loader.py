# -*- coding: utf-8 -*-
"""
净值数据加载器

适配 MyDCA-Board 的 nav 文件格式，从 CSV 文件加载基金净值数据

MyDCA-Board nav 文件格式：
  product_code,product_name,nav_date,nav,total_nav,income,weekly_rate,fetched_at
  产品代码,产品名称,净值日期,单位净值,累计净值,日收益,周收益率,采集时间
  163406,兴全合润混合(LOF)A,2025-12-17,1.0930,4.0930,0.0023,0.17%,2025-12-18 10:30:00
"""

import csv
import json
import os
from datetime import datetime
from typing import List, Optional
from pathlib import Path

from ..engine.types import NavBar


def _find_project_root(start_path: Optional[Path] = None) -> Path:
    """
    向上逐级查找项目根目录
    
    项目根目录的判断条件：同时包含 data 和 config 两个目录
    
    Args:
        start_path: 开始查找的路径，默认从当前文件位置开始
    
    Returns:
        项目根目录的 Path 对象
    
    Raises:
        RuntimeError: 找不到符合条件的项目根目录
    """
    if start_path is None:
        start_path = Path(__file__).resolve().parent
    
    current = start_path
    
    # 向上逐级查找，最多查找 10 层（防止无限循环）
    for _ in range(10):
        data_dir = current / "data"
        config_dir = current / "config"
        
        # 同时包含 data 和 config 目录即为项目根目录
        if data_dir.is_dir() and config_dir.is_dir():
            return current
        
        # 如果到达文件系统根目录，停止查找
        parent = current.parent
        if parent == current:
            break
        current = parent
    
    # fallback 到当前工作目录
    cwd = Path(os.getcwd())
    if (cwd / "data").is_dir() and (cwd / "config").is_dir():
        return cwd
    
    raise RuntimeError(
        "无法定位项目根目录。请确保项目结构包含 data 和 config 两个目录，\n"
        f"或从项目根目录运行脚本。\n"
        f"已尝试从 {start_path} 向上查找，并检查工作目录 {cwd}"
    )


def load_nav_from_file(
    csv_path: str,
    date_col: str = 'nav_date',
    nav_col: str = 'nav',
    encoding: str = 'utf-8-sig'
) -> List[NavBar]:
    """
    从 MyDCA-Board 的 nav CSV 文件加载基金净值序列
    
    Args:
        csv_path: CSV 文件路径
        date_col: 日期列名（默认 'nav_date'）
        nav_col: 净值列名（默认 'nav'）
        encoding: 文件编码（默认 'utf-8-sig'）
    
    Returns:
        NavBar 列表，按日期升序排列
    
    Raises:
        FileNotFoundError: 文件不存在
        ValueError: 数据格式错误
    """
    path = Path(csv_path)
    if not path.exists():
        raise FileNotFoundError(f"找不到净值文件: {csv_path}")
    
    bars: List[NavBar] = []
    
    # 尝试多种编码
    encodings_to_try = [encoding, 'utf-8-sig', 'utf-8', 'gbk', 'gb2312']
    
    for enc in encodings_to_try:
        try:
            with open(path, 'r', encoding=enc) as f:
                reader = csv.DictReader(f)
                
                if reader.fieldnames is None:
                    raise ValueError("CSV 文件为空或格式错误")
                
                # 查找实际的列名（支持中英文）
                actual_date_col = _find_column(reader.fieldnames, date_col, 
                    ['nav_date', '净值日期', 'date', '日期', 'Date', 'FSRQ'])
                actual_nav_col = _find_column(reader.fieldnames, nav_col,
                    ['nav', '单位净值', 'NAV', 'Nav', 'DWJZ', '净值'])
                
                if actual_date_col is None:
                    raise ValueError(f"找不到日期列，尝试过的列名: {date_col}")
                if actual_nav_col is None:
                    raise ValueError(f"找不到净值列，尝试过的列名: {nav_col}")
                
                for row in reader:
                    date_str = row.get(actual_date_col, '').strip()
                    nav_str = row.get(actual_nav_col, '').strip()
                    
                    # 跳过中文表头行和空行
                    if not date_str or not nav_str:
                        continue
                    if date_str.startswith('净值') or date_str.startswith('产品'):
                        continue
                    
                    # 解析日期
                    date = _parse_date(date_str)
                    if date is None:
                        continue
                    
                    # 解析净值
                    try:
                        nav = float(nav_str)
                        if nav > 0:  # 过滤无效净值
                            bars.append(NavBar(date=date, nav=nav))
                    except ValueError:
                        continue
                
                break  # 成功读取，退出编码循环
                
        except UnicodeDecodeError:
            continue  # 尝试下一种编码
    
    if not bars:
        raise ValueError(f"未能从 {csv_path} 中解析出有效数据")
    
    # 按日期升序排列
    bars.sort(key=lambda x: x.date)
    
    return bars


def load_nav_from_product(
    product_code: str,
    project_root: Optional[Path] = None
) -> List[NavBar]:
    """
    根据产品代码从 MyDCA-Board 数据目录加载净值
    
    会自动查找 data/nav/{product_code}_*.csv 文件
    
    Args:
        product_code: 产品代码（如 '163406'）
        project_root: 项目根目录，默认自动检测
    
    Returns:
        NavBar 列表，按日期升序排列
    
    Raises:
        FileNotFoundError: 找不到对应的净值文件
    """
    if project_root is None:
        project_root = _find_project_root()
    
    nav_dir = project_root / "data" / "nav"
    
    if not nav_dir.exists():
        raise FileNotFoundError(f"净值目录不存在: {nav_dir}")
    
    # 查找匹配的文件
    matching_files = list(nav_dir.glob(f"{product_code}_*.csv"))
    
    if not matching_files:
        # 尝试精确匹配
        exact_file = nav_dir / f"{product_code}.csv"
        if exact_file.exists():
            matching_files = [exact_file]
    
    if not matching_files:
        raise FileNotFoundError(
            f"找不到产品 {product_code} 的净值文件\n"
            f"查找目录: {nav_dir}\n"
            f"期望文件名格式: {product_code}_*.csv"
        )
    
    # 使用第一个匹配的文件
    nav_file = matching_files[0]
    
    return load_nav_from_file(str(nav_file))


def get_available_products(project_root: Optional[Path] = None) -> List[dict]:
    """
    获取所有可用于回测的产品列表
    
    Args:
        project_root: 项目根目录，默认自动检测
    
    Returns:
        产品列表，每个产品包含 product_code, product_name, nav_file
    """
    if project_root is None:
        project_root = _find_project_root()
    
    # 从 products.json 加载产品信息
    products_path = project_root / "config" / "products.json"
    products_map = {}
    
    if products_path.exists():
        with open(products_path, 'r', encoding='utf-8') as f:
            products = json.load(f)
            for p in products:
                code = p.get("product_code")
                name = p.get("product_name")
                if code:
                    products_map[str(code)] = name or code
    
    # 扫描 nav 目录
    nav_dir = project_root / "data" / "nav"
    available = []
    
    if nav_dir.exists():
        for nav_file in nav_dir.glob("*.csv"):
            filename = nav_file.stem
            if '_' in filename:
                product_code = filename.split('_')[0]
                product_name = filename.split('_', 1)[1]
            else:
                product_code = filename
                product_name = products_map.get(product_code, filename)
            
            available.append({
                'product_code': product_code,
                'product_name': product_name,
                'nav_file': str(nav_file),
            })
    
    return available


def _find_column(
    fieldnames: List[str], 
    preferred: str, 
    alternatives: List[str]
) -> Optional[str]:
    """
    在列名列表中查找匹配的列
    """
    def clean_name(name: str) -> str:
        return name.strip().lstrip('\ufeff')
    
    # 先找指定的列名
    if preferred in fieldnames:
        return preferred
    
    # 再找备选列名
    for alt in alternatives:
        if alt in fieldnames:
            return alt
    
    # 尝试忽略大小写匹配
    preferred_lower = preferred.lower()
    for field in fieldnames:
        if field.lower() == preferred_lower:
            return field
        if clean_name(field).lower() == preferred_lower:
            return field
    
    # 最后尝试备选列名的清理匹配
    for alt in alternatives:
        alt_lower = alt.lower()
        for field in fieldnames:
            if clean_name(field).lower() == alt_lower:
                return field
    
    return None


def _parse_date(date_str: str) -> Optional[datetime]:
    """
    尝试多种格式解析日期字符串
    """
    formats_to_try = [
        '%Y-%m-%d',
        '%Y/%m/%d',
        '%Y%m%d',
        '%d-%m-%Y',
        '%d/%m/%Y',
        '%Y年%m月%d日',
    ]
    
    for fmt in formats_to_try:
        try:
            return datetime.strptime(date_str, fmt)
        except ValueError:
            continue
    
    return None

