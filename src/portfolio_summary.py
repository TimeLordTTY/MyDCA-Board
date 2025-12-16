"""资产汇总模块 - 按净值日期和采集日期生成投资组合汇总（健壮性加固版）"""
import csv
import logging
from pathlib import Path
from datetime import datetime
from decimal import Decimal, InvalidOperation
from collections import defaultdict

logger = logging.getLogger(__name__)

def safe_decimal(value, default=Decimal("0"), field_name="unknown", row_info=""):
    """
    安全解析为Decimal，防止脏数据导致程序崩溃
    :param value: 原始值（可能是None、空字符串、"-"、带逗号的数字、普通数字等）
    :param default: 解析失败时的默认值
    :param field_name: 字段名（用于日志）
    :param row_info: 行信息（用于日志）
    :return: Decimal对象
    """
    # 如果已经是Decimal，直接返回
    if isinstance(value, Decimal):
        return value
    
    # 如果是None或空字符串或"-"，返回默认值
    if value is None or value == "" or value == "-":
        if value is not None and value != "":
            logger.warning(f"字段 {field_name} 值为 '{value}' (空值/占位符)，{row_info}，使用默认值 {default}")
        return default
    
    # 如果是int或float，转换为Decimal
    if isinstance(value, (int, float)):
        return Decimal(str(value))
    
    # 字符串处理
    try:
        # 去除空格
        value_str = str(value).strip()
        
        # 处理带逗号的数字（如 "12,345.67"）
        if ',' in value_str:
            value_str = value_str.replace(',', '')
        
        # 转换为Decimal
        return Decimal(value_str)
    except (ValueError, InvalidOperation) as e:
        logger.warning(f"字段 {field_name} 无法解析为数字: '{value}' {row_info}，错误: {e}，使用默认值 {default}")
        return default

def parse_date(date_str):
    """
    解析日期字符串为date对象（增强版，支持多种格式）
    :param date_str: 日期字符串
    :return: date对象或None
    """
    if not date_str or date_str.strip() == "":
        return None
    
    date_str = date_str.strip()
    
    # 支持的日期格式
    formats = [
        '%Y-%m-%d',
        '%Y/%m/%d',
        '%Y%m%d',
    ]
    
    for fmt in formats:
        try:
            return datetime.strptime(date_str, fmt).date()
        except ValueError:
            continue
    
    logger.warning(f"无法解析日期字符串: '{date_str}'")
    return None

def parse_datetime(datetime_str):
    """
    解析日期时间字符串，返回日期部分（增强版，支持多种格式和时区）
    :param datetime_str: 日期时间字符串
    :return: date对象或None
    """
    if not datetime_str or datetime_str.strip() == "":
        return None
    
    datetime_str = datetime_str.strip()
    
    # 支持的日期时间格式
    formats = [
        '%Y-%m-%d %H:%M:%S',           # 2025-12-16 22:54:52
        '%Y-%m-%dT%H:%M:%S',           # 2025-12-16T22:54:52
        '%Y-%m-%dT%H:%M:%S.%f',        # 2025-12-16T22:54:52.123
        '%Y-%m-%d %H:%M:%S.%f',        # 2025-12-16 22:54:52.123
    ]
    
    # 处理时区标记（简单处理：直接移除时区部分）
    if '+' in datetime_str or datetime_str.endswith('Z'):
        # 移除时区部分
        if '+' in datetime_str:
            datetime_str = datetime_str.split('+')[0]
        elif datetime_str.endswith('Z'):
            datetime_str = datetime_str[:-1]
    
    for fmt in formats:
        try:
            dt = datetime.strptime(datetime_str, fmt)
            return dt.date()
        except ValueError:
            continue
    
    # 如果都失败，尝试只解析日期部分
    if ' ' in datetime_str or 'T' in datetime_str:
        date_part = datetime_str.split(' ')[0] if ' ' in datetime_str else datetime_str.split('T')[0]
        return parse_date(date_part)
    
    logger.warning(f"无法解析日期时间字符串: '{datetime_str}'")
    return None

def read_daily_snapshots(snapshot_path):
    """
    读取日快照数据
    :return: list of dict
    """
    if not Path(snapshot_path).exists():
        return []
    
    records = []
    with open(snapshot_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            records.append(row)
    return records

def aggregate_by_nav_date(records):
    """
    按净值日期（snapshot_date）聚合（健壮性加固版）
    :return: dict {snapshot_date: {total_value, total_pnl, product_codes}}
    """
    aggregated = defaultdict(lambda: {
        'total_value': Decimal('0'),
        'total_pnl': Decimal('0'),
        'product_codes': set()  # 用于去重产品
    })
    
    for idx, record in enumerate(records):
        snapshot_date = record.get('snapshot_date', '')
        product_code = record.get('product_code', '')
        
        row_info = f"(第{idx+1}行, 产品{product_code})"
        
        # 安全解析金额
        value = safe_decimal(record.get('value'), field_name='value', row_info=row_info)
        pnl = safe_decimal(record.get('pnl'), field_name='pnl', row_info=row_info)
        
        if not snapshot_date:
            logger.warning(f"跳过缺少snapshot_date的记录 {row_info}")
            continue
        
        aggregated[snapshot_date]['total_value'] += value
        aggregated[snapshot_date]['total_pnl'] += pnl
        if product_code:
            aggregated[snapshot_date]['product_codes'].add(product_code)
    
    return aggregated

def aggregate_by_fetch_date(records):
    """
    按采集日期（fetched_at的日期部分）聚合（健壮性加固版）
    :return: dict {fetch_date: {total_value, total_pnl, product_codes, stale_products, max_lag_days}}
    """
    # 先按fetch_date分组
    grouped = defaultdict(list)
    for idx, record in enumerate(records):
        fetched_at = record.get('fetched_at', '')
        fetch_date = parse_datetime(fetched_at)
        
        if not fetch_date:
            logger.warning(f"跳过无法解析fetched_at的记录: '{fetched_at}' (第{idx+1}行)")
            continue
        
        grouped[fetch_date].append(record)
    
    # 聚合计算
    aggregated = {}
    for fetch_date, group_records in grouped.items():
        total_value = Decimal('0')
        total_pnl = Decimal('0')
        product_codes = set()  # 用于去重产品
        stale_products = 0
        max_lag_days = 0
        
        for record in group_records:
            product_code = record.get('product_code', '')
            row_info = f"(fetch_date={fetch_date}, 产品{product_code})"
            
            # 安全解析金额
            value = safe_decimal(record.get('value'), field_name='value', row_info=row_info)
            pnl = safe_decimal(record.get('pnl'), field_name='pnl', row_info=row_info)
            
            snapshot_date = parse_date(record.get('snapshot_date', ''))
            
            total_value += value
            total_pnl += pnl
            if product_code:
                product_codes.add(product_code)
            
            # 计算滞后
            if snapshot_date and fetch_date:
                lag_days = (fetch_date - snapshot_date).days
                if lag_days > 0:
                    stale_products += 1
                    max_lag_days = max(max_lag_days, lag_days)
        
        aggregated[fetch_date] = {
            'total_value': total_value,
            'total_pnl': total_pnl,
            'product_codes': product_codes,
            'stale_products': stale_products,
            'max_lag_days': max_lag_days
        }
    
    return aggregated

def write_portfolio_by_nav_date(aggregated, output_path):
    """
    写入按净值日期汇总的文件
    采用全量重算覆盖写入，确保幂等性
    """
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    
    fieldnames = ['snapshot_date', 'total_value', 'total_pnl', 'product_count']
    
    # 按日期排序
    sorted_dates = sorted(aggregated.keys())
    
    with open(output_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        
        for snapshot_date in sorted_dates:
            data = aggregated[snapshot_date]
            writer.writerow({
                'snapshot_date': snapshot_date,
                'total_value': f"{data['total_value']:.2f}",
                'total_pnl': f"{data['total_pnl']:.2f}",
                'product_count': len(data['product_codes'])  # 去重后的产品数
            })

def write_portfolio_by_fetch_date(aggregated, output_path):
    """
    写入按采集日期汇总的文件
    采用全量重算覆盖写入，确保幂等性
    新增 total_pnl_vs_prev_fetch 字段：真实日变动（当天total_value - 前一天total_value）
    """
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    
    fieldnames = ['fetch_date', 'total_value', 'total_pnl', 'total_pnl_vs_prev_fetch', 
                  'product_count', 'stale_products', 'max_lag_days']
    
    # 按日期排序
    sorted_dates = sorted(aggregated.keys())
    
    with open(output_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        
        prev_total_value = None
        for fetch_date in sorted_dates:
            data = aggregated[fetch_date]
            current_total_value = data['total_value']
            
            # 计算相对于上一个采集日的变动
            if prev_total_value is not None:
                total_pnl_vs_prev_fetch = current_total_value - prev_total_value
            else:
                total_pnl_vs_prev_fetch = Decimal('0')
            
            writer.writerow({
                'fetch_date': fetch_date.strftime('%Y-%m-%d'),
                'total_value': f"{current_total_value:.2f}",
                'total_pnl': f"{data['total_pnl']:.2f}",
                'total_pnl_vs_prev_fetch': f"{total_pnl_vs_prev_fetch:.2f}",
                'product_count': len(data['product_codes']),  # 去重后的产品数
                'stale_products': data['stale_products'],
                'max_lag_days': data['max_lag_days']
            })
            
            prev_total_value = current_total_value

def generate_portfolio_summary(snapshot_path, output_dir):
    """
    生成投资组合汇总
    :param snapshot_path: daily.csv 的路径
    :param output_dir: 输出目录
    :return: (nav_date_count, fetch_date_count) 汇总的日期数量
    """
    # 读取快照数据
    records = read_daily_snapshots(snapshot_path)
    
    if not records:
        return 0, 0
    
    # 按净值日期聚合
    by_nav_date = aggregate_by_nav_date(records)
    output_nav_date = output_dir / "portfolio_by_nav_date.csv"
    write_portfolio_by_nav_date(by_nav_date, output_nav_date)
    
    # 按采集日期聚合
    by_fetch_date = aggregate_by_fetch_date(records)
    output_fetch_date = output_dir / "portfolio_by_fetch_date.csv"
    write_portfolio_by_fetch_date(by_fetch_date, output_fetch_date)
    
    return len(by_nav_date), len(by_fetch_date)

if __name__ == "__main__":
    # 配置日志
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(levelname)s - %(message)s'
    )
    
    # 测试
    from config_loader import get_project_root
    
    root = get_project_root()
    snapshot_path = root / "data" / "snapshots" / "daily.csv"
    output_dir = root / "data" / "snapshots"
    
    nav_count, fetch_count = generate_portfolio_summary(snapshot_path, output_dir)
    print(f"✓ 生成投资组合汇总:")
    print(f"  - 按净值日期: {nav_count} 个日期")
    print(f"  - 按采集日期: {fetch_count} 个日期")

