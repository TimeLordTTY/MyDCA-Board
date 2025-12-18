"""资产汇总模块 - 按净值日期和采集日期生成投资组合汇总

字段说明（与 daily.csv 统一）：
- total_value: 总资产（含在途资金）
- total_pnl: 总盈亏（total_value - principal_total）
- cost: 持仓成本
- unrealized_pnl: 浮动盈亏（value - cost）
- pnl_day: 日变动（只由净值涨跌贡献）
"""
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
    """
    if isinstance(value, Decimal):
        return value
    
    if value is None or value == "" or value == "-":
        return default
    
    if isinstance(value, (int, float)):
        return Decimal(str(value))
    
    try:
        value_str = str(value).strip()
        # 处理百分号
        if value_str.endswith('%'):
            value_str = value_str[:-1]
        # 处理带逗号的数字
        if ',' in value_str:
            value_str = value_str.replace(',', '')
        return Decimal(value_str)
    except (ValueError, InvalidOperation) as e:
        logger.warning(f"字段 {field_name} 无法解析: '{value}' {row_info}")
        return default


def parse_date(date_str):
    """解析日期字符串为date对象"""
    if not date_str or date_str.strip() == "":
        return None
    
    date_str = date_str.strip()
    formats = ['%Y-%m-%d', '%Y/%m/%d', '%Y%m%d']
    
    for fmt in formats:
        try:
            return datetime.strptime(date_str, fmt).date()
        except ValueError:
            continue
    
    logger.warning(f"无法解析日期字符串: '{date_str}'")
    return None


def parse_datetime(datetime_str):
    """解析日期时间字符串，返回日期部分"""
    if not datetime_str or datetime_str.strip() == "":
        return None
    
    datetime_str = datetime_str.strip()
    formats = [
        '%Y-%m-%d %H:%M:%S',
        '%Y-%m-%dT%H:%M:%S',
        '%Y-%m-%dT%H:%M:%S.%f',
        '%Y-%m-%d %H:%M:%S.%f',
    ]
    
    # 处理时区
    if '+' in datetime_str:
        datetime_str = datetime_str.split('+')[0]
    elif datetime_str.endswith('Z'):
        datetime_str = datetime_str[:-1]
    
    for fmt in formats:
        try:
            return datetime.strptime(datetime_str, fmt).date()
        except ValueError:
            continue
    
    # 尝试只解析日期部分
    if ' ' in datetime_str or 'T' in datetime_str:
        date_part = datetime_str.split(' ')[0] if ' ' in datetime_str else datetime_str.split('T')[0]
        return parse_date(date_part)
    
    return None


def read_daily_snapshots(snapshot_path):
    """读取日快照数据（跳过中文表头行）"""
    if not Path(snapshot_path).exists():
        return []
    
    records = []
    with open(snapshot_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            # 跳过中文表头行
            first_value = row.get('fetch_date', row.get('nav_date', ''))
            if first_value and (first_value.startswith('采集') or first_value.startswith('净值')):
                continue
            records.append(row)
    return records


def aggregate_by_nav_date(records):
    """
    按净值日期（nav_date）聚合
    :return: dict {nav_date: {...}}
    """
    aggregated = defaultdict(lambda: {
        'total_value': Decimal('0'),
        'total_pnl': Decimal('0'),
        'pnl_day': Decimal('0'),
        'cost': Decimal('0'),
        'unrealized_pnl': Decimal('0'),
        'principal_total': Decimal('0'),
        'product_codes': set()
    })
    
    for idx, record in enumerate(records):
        nav_date = record.get('nav_date', '')
        product_code = record.get('product_code', '')
        row_info = f"(第{idx+1}行, 产品{product_code})"
        
        if not nav_date:
            continue
        
        # 使用新字段（优先）或旧字段（兼容）
        total_value = safe_decimal(record.get('total_value') or record.get('value', '0'), 
                                   field_name='total_value', row_info=row_info)
        total_pnl = safe_decimal(record.get('total_pnl', '0'), 
                                 field_name='total_pnl', row_info=row_info)
        pnl_day = safe_decimal(record.get('pnl_day') or record.get('pnl', '0'), 
                               field_name='pnl_day', row_info=row_info)
        cost = safe_decimal(record.get('cost', '0'), field_name='cost', row_info=row_info)
        unrealized_pnl = safe_decimal(record.get('unrealized_pnl', '0'), 
                                      field_name='unrealized_pnl', row_info=row_info)
        principal_total = safe_decimal(record.get('principal_total', '0'), 
                                       field_name='principal_total', row_info=row_info)
        
        aggregated[nav_date]['total_value'] += total_value
        aggregated[nav_date]['total_pnl'] += total_pnl
        aggregated[nav_date]['pnl_day'] += pnl_day
        aggregated[nav_date]['cost'] += cost
        aggregated[nav_date]['unrealized_pnl'] += unrealized_pnl
        aggregated[nav_date]['principal_total'] += principal_total
        if product_code:
            aggregated[nav_date]['product_codes'].add(product_code)
    
    return aggregated


def aggregate_by_fetch_date(records):
    """
    按采集日期聚合 - 每个 fetch_date 汇总"截至该日的所有产品最新快照"
    """
    all_fetch_dates = set()
    product_records = defaultdict(list)
    
    for idx, record in enumerate(records):
        if 'fetch_date' in record and record['fetch_date']:
            fetch_date = parse_date(record['fetch_date'])
        else:
            fetched_at = record.get('fetched_at', '')
            fetch_date = parse_datetime(fetched_at)
        
        product_code = record.get('product_code', '')
        
        if not fetch_date:
            continue
        
        all_fetch_dates.add(fetch_date)
        product_records[product_code].append({
            'record': record,
            'fetch_date': fetch_date
        })
    
    # 对每个产品的记录按 fetch_date 排序
    for product_code in product_records:
        product_records[product_code].sort(key=lambda x: x['fetch_date'])
    
    # 聚合
    aggregated = {}
    sorted_fetch_dates = sorted(all_fetch_dates)
    
    for fetch_date in sorted_fetch_dates:
        agg = {
            'total_value': Decimal('0'),
            'total_pnl': Decimal('0'),
            'pnl_day': Decimal('0'),
            'cost': Decimal('0'),
            'unrealized_pnl': Decimal('0'),
            'principal_total': Decimal('0'),
            'product_codes': set(),
            'stale_products': 0,
            'max_lag_days': 0
        }
        
        for product_code, rec_list in product_records.items():
            latest_record = None
            for item in rec_list:
                if item['fetch_date'] <= fetch_date:
                    latest_record = item['record']
                else:
                    break
            
            if latest_record is None:
                continue
            
            row_info = f"(fetch_date={fetch_date}, 产品{product_code})"
            
            # 使用新字段
            total_value = safe_decimal(latest_record.get('total_value') or latest_record.get('value', '0'),
                                       field_name='total_value', row_info=row_info)
            total_pnl = safe_decimal(latest_record.get('total_pnl', '0'),
                                     field_name='total_pnl', row_info=row_info)
            pnl_day = safe_decimal(latest_record.get('pnl_day') or latest_record.get('pnl', '0'),
                                   field_name='pnl_day', row_info=row_info)
            cost = safe_decimal(latest_record.get('cost', '0'), field_name='cost', row_info=row_info)
            unrealized_pnl = safe_decimal(latest_record.get('unrealized_pnl', '0'),
                                          field_name='unrealized_pnl', row_info=row_info)
            principal_total = safe_decimal(latest_record.get('principal_total', '0'),
                                           field_name='principal_total', row_info=row_info)
            
            nav_date = parse_date(latest_record.get('nav_date', ''))
            
            agg['total_value'] += total_value
            agg['total_pnl'] += total_pnl
            agg['pnl_day'] += pnl_day
            agg['cost'] += cost
            agg['unrealized_pnl'] += unrealized_pnl
            agg['principal_total'] += principal_total
            agg['product_codes'].add(product_code)
            
            # 计算滞后
            if nav_date and fetch_date:
                lag_days = (fetch_date - nav_date).days
                if lag_days > 0:
                    agg['stale_products'] += 1
                    agg['max_lag_days'] = max(agg['max_lag_days'], lag_days)
        
        aggregated[fetch_date] = agg
    
    return aggregated


def aggregate_by_category(records):
    """
    按分类（fund/bank）和采集日期聚合
    """
    all_fetch_dates = set()
    product_records = defaultdict(list)
    
    for idx, record in enumerate(records):
        if 'fetch_date' in record and record['fetch_date']:
            fetch_date = parse_date(record['fetch_date'])
        else:
            fetched_at = record.get('fetched_at', '')
            fetch_date = parse_datetime(fetched_at)
        
        product_code = record.get('product_code', '')
        category = record.get('category', 'fund')
        
        if not fetch_date:
            continue
        
        all_fetch_dates.add(fetch_date)
        product_records[product_code].append({
            'record': record,
            'fetch_date': fetch_date,
            'category': category
        })
    
    for product_code in product_records:
        product_records[product_code].sort(key=lambda x: x['fetch_date'])
    
    aggregated = {}
    sorted_fetch_dates = sorted(all_fetch_dates)
    
    for fetch_date in sorted_fetch_dates:
        category_data = defaultdict(lambda: {
            'total_value': Decimal('0'),
            'total_pnl': Decimal('0'),
            'pnl_day': Decimal('0'),
            'cost': Decimal('0'),
            'unrealized_pnl': Decimal('0'),
            'principal_total': Decimal('0'),
            'product_codes': set()
        })
        
        for product_code, rec_list in product_records.items():
            latest_item = None
            for item in rec_list:
                if item['fetch_date'] <= fetch_date:
                    latest_item = item
                else:
                    break
            
            if latest_item is None:
                continue
            
            record = latest_item['record']
            category = latest_item['category']
            row_info = f"(fetch_date={fetch_date}, 产品{product_code})"
            
            total_value = safe_decimal(record.get('total_value') or record.get('value', '0'),
                                       field_name='total_value', row_info=row_info)
            total_pnl = safe_decimal(record.get('total_pnl', '0'),
                                     field_name='total_pnl', row_info=row_info)
            pnl_day = safe_decimal(record.get('pnl_day') or record.get('pnl', '0'),
                                   field_name='pnl_day', row_info=row_info)
            cost = safe_decimal(record.get('cost', '0'), field_name='cost', row_info=row_info)
            unrealized_pnl = safe_decimal(record.get('unrealized_pnl', '0'),
                                          field_name='unrealized_pnl', row_info=row_info)
            principal_total = safe_decimal(record.get('principal_total', '0'),
                                           field_name='principal_total', row_info=row_info)
            
            category_data[category]['total_value'] += total_value
            category_data[category]['total_pnl'] += total_pnl
            category_data[category]['pnl_day'] += pnl_day
            category_data[category]['cost'] += cost
            category_data[category]['unrealized_pnl'] += unrealized_pnl
            category_data[category]['principal_total'] += principal_total
            category_data[category]['product_codes'].add(product_code)
        
        aggregated[fetch_date] = dict(category_data)
    
    return aggregated


def write_portfolio_by_nav_date(aggregated, output_path):
    """写入按净值日期汇总的文件"""
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    
    fieldnames = ['nav_date', 'total_value', 'total_pnl', 'pnl_day', 'cost', 
                  'unrealized_pnl', 'principal_total', 'product_count']
    chinese_headers = ['净值日期', '总资产', '总盈亏', '日变动', '成本', 
                       '浮动盈亏', '累计投入本金', '产品数量']
    
    sorted_dates = sorted(aggregated.keys())
    
    with open(output_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        f.write(','.join(chinese_headers) + '\n')
        
        for nav_date in sorted_dates:
            data = aggregated[nav_date]
            writer.writerow({
                'nav_date': nav_date,
                'total_value': f"{data['total_value']:.2f}",
                'total_pnl': f"{data['total_pnl']:.2f}",
                'pnl_day': f"{data['pnl_day']:.2f}",
                'cost': f"{data['cost']:.2f}",
                'unrealized_pnl': f"{data['unrealized_pnl']:.2f}",
                'principal_total': f"{data['principal_total']:.2f}",
                'product_count': len(data['product_codes'])
            })


def write_portfolio_by_fetch_date(aggregated, output_path):
    """写入按采集日期汇总的文件"""
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    
    fieldnames = ['fetch_date', 'total_value', 'total_pnl', 'pnl_day', 'cost',
                  'unrealized_pnl', 'principal_total', 'pnl_vs_prev',
                  'product_count', 'stale_products', 'max_lag_days']
    chinese_headers = ['采集日期', '总资产', '总盈亏', '日变动', '成本',
                       '浮动盈亏', '累计投入本金', '相对前日变动',
                       '产品数量', '滞后产品数', '最大滞后天数']
    
    sorted_dates = sorted(aggregated.keys())
    
    with open(output_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        f.write(','.join(chinese_headers) + '\n')
        
        prev_total_value = None
        for fetch_date in sorted_dates:
            data = aggregated[fetch_date]
            current_total_value = data['total_value']
            
            if prev_total_value is not None:
                pnl_vs_prev = current_total_value - prev_total_value
            else:
                pnl_vs_prev = Decimal('0')
            
            writer.writerow({
                'fetch_date': fetch_date.strftime('%Y-%m-%d'),
                'total_value': f"{current_total_value:.2f}",
                'total_pnl': f"{data['total_pnl']:.2f}",
                'pnl_day': f"{data['pnl_day']:.2f}",
                'cost': f"{data['cost']:.2f}",
                'unrealized_pnl': f"{data['unrealized_pnl']:.2f}",
                'principal_total': f"{data['principal_total']:.2f}",
                'pnl_vs_prev': f"{pnl_vs_prev:.2f}",
                'product_count': len(data['product_codes']),
                'stale_products': data['stale_products'],
                'max_lag_days': data['max_lag_days']
            })
            
            prev_total_value = current_total_value


def write_portfolio_by_category(aggregated, output_path):
    """写入按分类汇总的文件"""
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    
    fieldnames = ['fetch_date', 'category', 'total_value', 'total_pnl', 'pnl_day',
                  'cost', 'unrealized_pnl', 'principal_total', 'pnl_vs_prev', 'product_count']
    chinese_headers = ['采集日期', '分类', '总资产', '总盈亏', '日变动',
                       '成本', '浮动盈亏', '累计投入本金', '相对前日变动', '产品数量']
    
    sorted_dates = sorted(aggregated.keys())
    prev_values = {'fund': None, 'bank': None, 'total': None}
    
    with open(output_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        f.write(','.join(chinese_headers) + '\n')
        
        for fetch_date in sorted_dates:
            category_data = aggregated[fetch_date]
            date_str = fetch_date.strftime('%Y-%m-%d')
            
            # 基金
            fund_data = category_data.get('fund', {})
            fund_value = fund_data.get('total_value', Decimal('0'))
            fund_pnl = fund_data.get('total_pnl', Decimal('0'))
            fund_pnl_day = fund_data.get('pnl_day', Decimal('0'))
            fund_cost = fund_data.get('cost', Decimal('0'))
            fund_unrealized = fund_data.get('unrealized_pnl', Decimal('0'))
            fund_principal = fund_data.get('principal_total', Decimal('0'))
            fund_count = len(fund_data.get('product_codes', set()))
            fund_pnl_vs_prev = fund_value - prev_values['fund'] if prev_values['fund'] is not None else Decimal('0')
            
            # 银行理财
            bank_data = category_data.get('bank', {})
            bank_value = bank_data.get('total_value', Decimal('0'))
            bank_pnl = bank_data.get('total_pnl', Decimal('0'))
            bank_pnl_day = bank_data.get('pnl_day', Decimal('0'))
            bank_cost = bank_data.get('cost', Decimal('0'))
            bank_unrealized = bank_data.get('unrealized_pnl', Decimal('0'))
            bank_principal = bank_data.get('principal_total', Decimal('0'))
            bank_count = len(bank_data.get('product_codes', set()))
            bank_pnl_vs_prev = bank_value - prev_values['bank'] if prev_values['bank'] is not None else Decimal('0')
            
            # 总计
            total_value = fund_value + bank_value
            total_pnl = fund_pnl + bank_pnl
            total_pnl_day = fund_pnl_day + bank_pnl_day
            total_cost = fund_cost + bank_cost
            total_unrealized = fund_unrealized + bank_unrealized
            total_principal = fund_principal + bank_principal
            total_count = fund_count + bank_count
            total_pnl_vs_prev = total_value - prev_values['total'] if prev_values['total'] is not None else Decimal('0')
            
            # 写入三行
            writer.writerow({
                'fetch_date': date_str,
                'category': '基金',
                'total_value': f"{fund_value:.2f}",
                'total_pnl': f"{fund_pnl:.2f}",
                'pnl_day': f"{fund_pnl_day:.2f}",
                'cost': f"{fund_cost:.2f}",
                'unrealized_pnl': f"{fund_unrealized:.2f}",
                'principal_total': f"{fund_principal:.2f}",
                'pnl_vs_prev': f"{fund_pnl_vs_prev:.2f}",
                'product_count': fund_count
            })
            
            writer.writerow({
                'fetch_date': date_str,
                'category': '银行理财',
                'total_value': f"{bank_value:.2f}",
                'total_pnl': f"{bank_pnl:.2f}",
                'pnl_day': f"{bank_pnl_day:.2f}",
                'cost': f"{bank_cost:.2f}",
                'unrealized_pnl': f"{bank_unrealized:.2f}",
                'principal_total': f"{bank_principal:.2f}",
                'pnl_vs_prev': f"{bank_pnl_vs_prev:.2f}",
                'product_count': bank_count
            })
            
            writer.writerow({
                'fetch_date': date_str,
                'category': '总资产',
                'total_value': f"{total_value:.2f}",
                'total_pnl': f"{total_pnl:.2f}",
                'pnl_day': f"{total_pnl_day:.2f}",
                'cost': f"{total_cost:.2f}",
                'unrealized_pnl': f"{total_unrealized:.2f}",
                'principal_total': f"{total_principal:.2f}",
                'pnl_vs_prev': f"{total_pnl_vs_prev:.2f}",
                'product_count': total_count
            })
            
            # 更新前日数据
            prev_values['fund'] = fund_value
            prev_values['bank'] = bank_value
            prev_values['total'] = total_value


def generate_portfolio_summary(snapshot_path, output_dir):
    """
    生成投资组合汇总
    :return: (nav_date_count, fetch_date_count)
    """
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
    
    # 按分类聚合
    by_category = aggregate_by_category(records)
    output_category = output_dir / "portfolio_by_category.csv"
    write_portfolio_by_category(by_category, output_category)
    
    return len(by_nav_date), len(by_fetch_date)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
    
    from config_loader import get_project_root
    
    root = get_project_root()
    snapshot_path = root / "data" / "snapshots" / "daily.csv"
    output_dir = root / "data" / "snapshots"
    
    nav_count, fetch_count = generate_portfolio_summary(snapshot_path, output_dir)
    print(f"✓ 生成投资组合汇总:")
    print(f"  - 按净值日期: {nav_count} 个日期")
    print(f"  - 按采集日期: {fetch_count} 个日期")
