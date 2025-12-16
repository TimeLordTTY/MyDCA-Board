"""净值采集协调器 - 可控加固版本"""
import logging
import sys
from datetime import date

from adaptor import cmbc_client, fund_client
from storage_csv import save_nav_record
from snapshot import create_daily_snapshot, get_last_snapshot_value
from config_loader import load_products, get_holdings_map, load_holdings, get_project_root
from validator import (
    validate_product_config, 
    validate_holdings_config,
    validate_adaptor_exists,
    validate_nav_record
)

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 适配器映射表
ADAPTOR_MAP = {
    'cmbc': cmbc_client,
    'fund': fund_client,
}

def validate_configs():
    """
    校验配置文件的完整性和正确性
    :return: (products, holdings, products_map, holdings_map) 或异常退出
    """
    logger.info("=== 配置校验阶段 ===")
    
    # 加载配置
    products = load_products()
    holdings = load_holdings()
    
    # 1. 校验产品配置
    for product in products:
        is_valid, error = validate_product_config(product)
        if not is_valid:
            logger.error(f"产品配置错误 {product.get('id', 'UNKNOWN')}: {error}")
            sys.exit(1)
        
        # 校验source是否有对应适配器
        source = product['source']
        is_valid, error = validate_adaptor_exists(source, ADAPTOR_MAP)
        if not is_valid:
            logger.error(f"产品 {product['id']} 配置错误: {error}")
            sys.exit(1)
    
    # 2. 校验持仓配置
    is_valid, error = validate_holdings_config(holdings, products)
    if not is_valid:
        logger.error(f"持仓配置错误: {error}")
        sys.exit(1)
    
    # 构建映射
    products_map = {p['id']: p['name'] for p in products}
    holdings_map = get_holdings_map()
    
    logger.info(f"✓ 配置校验通过: {len(products)}个产品, {len(holdings)}个持仓")
    return products, holdings, products_map, holdings_map

def fetch_and_validate_nav(adaptor, product_code, product_name, source):
    """
    获取并校验净值数据
    :return: (nav_list, error_message)
    """
    try:
        nav_list = adaptor.query_latest_nav(product_code, date.today(), 0)
        
        # 必须返回列表
        if not isinstance(nav_list, list):
            return None, f"适配器返回类型错误: 期望list，实际{type(nav_list)}"
        
        if not nav_list:
            return None, "未获取到数据"
        
        # 校验每条记录
        for nav_record in nav_list:
            is_valid, error = validate_nav_record(nav_record, product_code)
            if not is_valid:
                return None, f"数据校验失败: {error}"
        
        return nav_list, None
        
    except Exception as e:
        return None, f"采集异常: {str(e)}"

def process_single_product(product, products_map, holdings_map, nav_records):
    """
    处理单个产品的采集、存储和记录
    :return: 处理结果字典（用于日志）
    """
    product_code = product['id']
    product_name = product['name']
    source = product['source']
    
    result = {
        'code': product_code,
        'source': source,
        'date': '-',
        'nav': '-',
        'csv': 'N',
        'snapshot': 'N',
        'pnl': '-',
        'status': 'FAIL'
    }
    
    # 获取适配器
    adaptor = ADAPTOR_MAP.get(source)
    
    # 获取并校验净值
    nav_list, error = fetch_and_validate_nav(adaptor, product_code, product_name, source)
    if error:
        result['status'] = f'ERR: {error}'
        return result
    
    # 只处理第一条记录
    nav_record = nav_list[0]
    result['date'] = nav_record['ISS_DATE']
    result['nav'] = nav_record['NAV']
    
    # 存储到CSV
    is_new = save_nav_record(product_code, product_name, nav_record)
    if is_new:
        result['csv'] = 'Y'
        result['status'] = 'OK'
        # 记录用于生成快照
        nav_records[product_code] = nav_record
        
        # 计算PNL（如果有持仓）
        shares = holdings_map.get(product_code, 0)
        if shares > 0:
            snapshot_path = get_project_root() / "data" / "snapshots" / "daily.csv"
            last_value = get_last_snapshot_value(snapshot_path, product_code)
            current_value = float(shares) * float(nav_record['NAV'])
            if last_value:
                pnl = current_value - float(last_value)
                result['pnl'] = f"{pnl:+.2f}"
        result['snapshot'] = 'Y'
    else:
        result['csv'] = 'SKIP'
        result['status'] = 'EXIST'
    
    return result

def collect_and_store():
    """采集净值并存储，生成快照 - 可控加固版"""
    
    logger.info("=== 财富中枢净值采集任务启动 ===")
    
    # 1. 校验配置（失败则退出）
    products, holdings, products_map, holdings_map = validate_configs()
    
    # 2. 采集净值并存储
    logger.info("=== 净值采集阶段 ===")
    nav_records = {}
    results = []
    
    for product in products:
        result = process_single_product(product, products_map, holdings_map, nav_records)
        results.append(result)
    
    # 3. 生成快照
    snapshot_count = 0
    if nav_records:
        logger.info("=== 快照生成阶段 ===")
        snapshot_count = create_daily_snapshot(nav_records, holdings_map, products_map)
        logger.info(f"✓ 新增 {snapshot_count} 条快照记录")
    
    # 4. 输出汇总日志（10行内）
    logger.info("=== 执行汇总 ===")
    logger.info(f"{'产品代码':<12} {'来源':<6} {'净值日期':<12} {'净值':<8} {'CSV':<6} {'快照':<6} {'PNL':<10} {'状态'}")
    logger.info("-" * 85)
    for r in results:
        logger.info(f"{r['code']:<12} {r['source']:<6} {r['date']:<12} {r['nav']:<8} {r['csv']:<6} {r['snapshot']:<6} {r['pnl']:<10} {r['status']}")
    
    success_count = sum(1 for r in results if r['csv'] == 'Y')
    logger.info(f"\n✓ 任务完成: 成功{success_count}/{len(products)}, 快照{snapshot_count}条")
    logger.info("=" * 85)

if __name__ == "__main__":
    collect_and_store()

