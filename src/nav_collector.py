"""净值采集协调器"""
import logging
from datetime import date

from adaptor import cmbc_client
from storage_csv import save_nav_records
from snapshot import create_daily_snapshot
from config_loader import load_products, get_holdings_map

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 适配器映射表
ADAPTOR_MAP = {
    'cmbc': cmbc_client,
    # 未来可以添加更多：
    # 'icbc': icbc_client,
    # 'ccb': ccb_client,
}

def collect_and_store():
    """采集净值并存储，生成快照"""
    
    # 1. 加载配置
    logger.info("加载配置...")
    products = load_products()
    holdings_map = get_holdings_map()
    
    # 3. 采集净值并存储
    nav_records = {}
    for product in products:
        product_code = product['id']
        product_name = product['name']
        source = product.get('source', 'cmbc')  # 默认使用cmbc
        
        logger.info(f"采集产品 {product_name} ({product_code}) [来源: {source}]...")
        
        # 选择对应的适配器
        adaptor = ADAPTOR_MAP.get(source)
        if not adaptor:
            logger.error(f"不支持的数据源: {source}，跳过产品 {product_code}")
            continue
        
        try:
            # 初始化会话（每个产品单独初始化，避免cookie失效）
            cookies = adaptor.bootstrap_session(product_code)
            
            nav_list = adaptor.query_latest_nav(cookies, product_code, date.today(), 0)
            
            if not nav_list:
                logger.warning(f"产品 {product_code} 未获取到净值数据")
                continue
            
            # 存储到CSV
            new_count = save_nav_records(product_code, nav_list)
            logger.info(f"产品 {product_code} 新增 {new_count} 条净值记录")
            
            if new_count > 0:
                nav_records[product_code] = nav_list
                
        except Exception as e:
            logger.error(f"采集产品 {product_code} 失败: {e}")
            continue
    
    # 4. 生成快照
    if nav_records:
        logger.info("生成日快照...")
        snapshot_count = create_daily_snapshot(nav_records, holdings_map)
        logger.info(f"新增 {snapshot_count} 条快照记录")
    else:
        logger.info("无新净值数据，跳过快照生成")
    
    logger.info("采集任务完成")

if __name__ == "__main__":
    collect_and_store()

