"""
CSV 数据加载工具

从 CSV 文件加载基金净值数据
"""

import csv
from datetime import datetime
from typing import List, Optional
import os

from ..engine.types import NavBar


def load_nav_series(
    csv_path: str,
    date_col: str = 'date',
    nav_col: str = 'nav',
    date_format: str = '%Y-%m-%d',
    encoding: str = 'utf-8'
) -> List[NavBar]:
    """
    从 CSV 文件加载基金净值序列
    
    CSV 文件应至少包含日期列和净值列
    
    Args:
        csv_path: CSV 文件路径
        date_col: 日期列名（如 'date', '净值日期', 'FSRQ' 等）
        nav_col: 净值列名（如 'nav', '单位净值', 'DWJZ' 等）
        date_format: 日期格式字符串，默认 '%Y-%m-%d'
        encoding: 文件编码，默认 'utf-8'
    
    Returns:
        NavBar 列表，按日期升序排列
    
    Raises:
        FileNotFoundError: 文件不存在
        ValueError: 列名不存在或数据格式错误
    """
    if not os.path.exists(csv_path):
        raise FileNotFoundError(f"找不到文件: {csv_path}")
    
    bars: List[NavBar] = []
    
    # 尝试不同编码（优先尝试 utf-8-sig 以正确处理 BOM）
    encodings_to_try = ['utf-8-sig', encoding, 'utf-8', 'gbk', 'gb2312', 'gb18030']
    
    for enc in encodings_to_try:
        try:
            with open(csv_path, 'r', encoding=enc) as f:
                reader = csv.DictReader(f)
                
                # 检查列名是否存在
                if reader.fieldnames is None:
                    raise ValueError("CSV 文件为空或格式错误")
                
                # 尝试找到匹配的列名（支持中英文）
                actual_date_col = _find_column(reader.fieldnames, date_col, 
                    ['date', '净值日期', 'FSRQ', '日期', 'Date'])
                actual_nav_col = _find_column(reader.fieldnames, nav_col,
                    ['nav', '单位净值', 'DWJZ', '净值', 'NAV', 'Nav'])
                
                if actual_date_col is None:
                    raise ValueError(f"找不到日期列，尝试过的列名: {date_col}")
                if actual_nav_col is None:
                    raise ValueError(f"找不到净值列，尝试过的列名: {nav_col}")
                
                for row in reader:
                    date_str = row[actual_date_col].strip()
                    nav_str = row[actual_nav_col].strip()
                    
                    if not date_str or not nav_str:
                        continue
                    
                    # 尝试多种日期格式
                    date = _parse_date(date_str, date_format)
                    if date is None:
                        continue
                    
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


def _find_column(
    fieldnames: List[str], 
    preferred: str, 
    alternatives: List[str]
) -> Optional[str]:
    """
    在列名列表中查找匹配的列
    
    Args:
        fieldnames: CSV 的列名列表
        preferred: 优先查找的列名
        alternatives: 备选列名列表
    
    Returns:
        找到的列名，或 None
    """
    # 清理列名（去除 BOM 和空白字符）
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
        # 清理后再匹配
        if clean_name(field).lower() == preferred_lower:
            return field
    
    # 最后尝试备选列名的清理匹配
    for alt in alternatives:
        alt_lower = alt.lower()
        for field in fieldnames:
            if clean_name(field).lower() == alt_lower:
                return field
    
    return None


def _parse_date(date_str: str, default_format: str) -> Optional[datetime]:
    """
    尝试多种格式解析日期字符串
    
    Args:
        date_str: 日期字符串
        default_format: 默认日期格式
    
    Returns:
        解析后的 datetime，失败返回 None
    """
    formats_to_try = [
        default_format,
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


def create_sample_csv(filepath: str, days: int = 365) -> None:
    """
    创建示例 CSV 文件用于测试
    
    生成一年的模拟净值数据
    
    Args:
        filepath: 输出文件路径
        days: 生成的天数
    """
    import random
    from datetime import timedelta
    
    start_date = datetime(2023, 1, 1)
    nav = 1.0
    
    with open(filepath, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.writer(f)
        writer.writerow(['date', 'nav'])
        
        for i in range(days):
            date = start_date + timedelta(days=i)
            # 跳过周末
            if date.weekday() >= 5:
                continue
            
            # 模拟净值波动（日涨跌幅 -2% ~ +2%）
            daily_return = random.uniform(-0.02, 0.02)
            nav *= (1 + daily_return)
            
            writer.writerow([date.strftime('%Y-%m-%d'), f'{nav:.4f}'])

