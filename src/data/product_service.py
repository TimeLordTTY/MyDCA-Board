"""产品服务 - 从数据库读取产品配置"""
import logging
from typing import List, Dict, Optional
from decimal import Decimal

from data.db_connector import execute_query, execute_one, execute_update, execute_insert
from data.config_loader import PRODUCT_DEFAULTS, MARKET_CONFIRM_OFFSET

logger = logging.getLogger(__name__)


def get_products(channel: Optional[str] = None, market: Optional[str] = None, 
                 asset_type: Optional[str] = None, is_active: bool = True) -> List[Dict]:
    """
    从数据库获取产品列表
    
    Args:
        channel: 渠道筛选 (EXCHANGE/OTC)
        market: 市场筛选 (SH/SZ/NA)
        asset_type: 资产类型筛选
        is_active: 是否只返回启用产品
    
    Returns:
        产品列表
    """
    sql = """
        SELECT 
            id, code, channel, market, asset_type, currency, is_qdii, track_index,
            product_name, category, source, buy_fee_rate, sell_fee_rate,
            buy_confirm_offset, sell_confirm_offset, cutoff_time,
            product_code, note, is_active, created_at, updated_at
        FROM products
        WHERE 1=1
    """
    params = []
    
    if channel:
        sql += " AND channel = %s"
        params.append(channel)
    
    if market:
        sql += " AND market = %s"
        params.append(market)
    
    if asset_type:
        sql += " AND asset_type = %s"
        params.append(asset_type)
    
    if is_active:
        sql += " AND is_active = 1"
    
    sql += " ORDER BY code, channel, market"
    
    products = execute_query(sql, tuple(params))
    
    # 应用默认值并格式化
    result = []
    for p in products:
        product = _apply_product_defaults(p)
        result.append(product)
    
    return result


def get_product_by_id(product_id: int) -> Optional[Dict]:
    """根据 product_id 获取产品"""
    sql = """
        SELECT 
            id, code, channel, market, asset_type, currency, is_qdii, track_index,
            product_name, category, source, buy_fee_rate, sell_fee_rate,
            buy_confirm_offset, sell_confirm_offset, cutoff_time,
            product_code, note, is_active, created_at, updated_at
        FROM products
        WHERE id = %s
    """
    product = execute_one(sql, (product_id,))
    if product:
        return _apply_product_defaults(product)
    return None


def get_product_by_code(product_code: str, channel: Optional[str] = None, 
                        market: Optional[str] = None) -> Optional[Dict]:
    """
    根据产品代码获取产品（兼容旧接口）
    
    Args:
        product_code: 产品代码
        channel: 渠道（可选，默认优先返回 OTC）
        market: 市场（可选）
    
    Returns:
        产品配置字典，如果 channel/market 未指定，优先返回 OTC 产品
    """
    if channel and market:
        sql = """
            SELECT 
                id, code, channel, market, asset_type, currency, is_qdii, track_index,
                product_name, category, source, buy_fee_rate, sell_fee_rate,
                buy_confirm_offset, sell_confirm_offset, cutoff_time,
                product_code, note, is_active, created_at, updated_at
            FROM products
            WHERE code = %s AND channel = %s AND market = %s
            LIMIT 1
        """
        product = execute_one(sql, (product_code, channel, market))
    elif channel:
        sql = """
            SELECT 
                id, code, channel, market, asset_type, currency, is_qdii, track_index,
                product_name, category, source, buy_fee_rate, sell_fee_rate,
                buy_confirm_offset, sell_confirm_offset, cutoff_time,
                product_code, note, is_active, created_at, updated_at
            FROM products
            WHERE code = %s AND channel = %s
            ORDER BY channel = 'OTC' DESC
            LIMIT 1
        """
        product = execute_one(sql, (product_code, channel))
    else:
        # 未指定 channel，优先返回 OTC
        sql = """
            SELECT 
                id, code, channel, market, asset_type, currency, is_qdii, track_index,
                product_name, category, source, buy_fee_rate, sell_fee_rate,
                buy_confirm_offset, sell_confirm_offset, cutoff_time,
                product_code, note, is_active, created_at, updated_at
            FROM products
            WHERE code = %s
            ORDER BY channel = 'OTC' DESC, id ASC
            LIMIT 1
        """
        product = execute_one(sql, (product_code,))
    
    if product:
        return _apply_product_defaults(product)
    return None


def _apply_product_defaults(product: Dict) -> Dict:
    """为产品配置应用默认值"""
    result = dict(product)
    
    # 应用默认值
    for key, default_value in PRODUCT_DEFAULTS.items():
        if key not in result or result[key] is None:
            result[key] = default_value
    
    # 根据 market 计算默认的 confirm_offset
    market = result.get('market', 'NA')
    if market == 'NA':
        # 场外产品，根据 is_qdii 判断
        is_qdii = result.get('is_qdii', 0)
        default_offset = 2 if is_qdii else 1
    else:
        # 场内产品，T+0
        default_offset = 0
    
    if result.get('buy_confirm_offset') is None:
        result['buy_confirm_offset'] = default_offset
    if result.get('sell_confirm_offset') is None:
        result['sell_confirm_offset'] = default_offset
    
    # 兼容字段：product_code = code
    if 'product_code' not in result or result['product_code'] is None:
        result['product_code'] = result.get('code', '')
    
    # 兼容字段：sell_fee_tiers（从 sell_fee_rate 生成，或使用默认阶梯）
    if 'sell_fee_tiers' not in result or not result.get('sell_fee_tiers'):
        # 如果 sell_fee_rate > 0，生成简单阶梯；否则使用默认阶梯
        sell_fee_rate = float(result.get('sell_fee_rate', 0))
        if sell_fee_rate > 0:
            # 简单阶梯：7天内 1.5%，7天以上 sell_fee_rate
            result['sell_fee_tiers'] = [
                {"min_days": 0, "max_days": 7, "rate": 0.015},
                {"min_days": 7, "max_days": None, "rate": sell_fee_rate}
            ]
        else:
            # 使用默认阶梯（从 config_loader 导入）
            from data.config_loader import DEFAULT_SELL_FEE_TIERS
            result['sell_fee_tiers'] = DEFAULT_SELL_FEE_TIERS
    
    return result


def create_product(product_data: Dict) -> int:
    """创建新产品"""
    sql = """
        INSERT INTO products (
            code, channel, market, asset_type, currency, is_qdii, track_index,
            product_name, category, source, buy_fee_rate, sell_fee_rate,
            buy_confirm_offset, sell_confirm_offset, cutoff_time, product_code, note
        ) VALUES (
            %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
        )
    """
    params = (
        product_data.get('code'),
        product_data.get('channel', 'OTC'),
        product_data.get('market', 'NA'),
        product_data.get('asset_type', 'FUND'),
        product_data.get('currency', 'CNY'),
        product_data.get('is_qdii', 0),
        product_data.get('track_index'),
        product_data.get('product_name'),
        product_data.get('category', 'fund'),
        product_data.get('source', 'fund'),
        product_data.get('buy_fee_rate', 0),
        product_data.get('sell_fee_rate', 0),
        product_data.get('buy_confirm_offset'),
        product_data.get('sell_confirm_offset'),
        product_data.get('cutoff_time', '15:00'),
        product_data.get('code'),  # product_code = code
        product_data.get('note')
    )
    return execute_insert(sql, params)


def update_product(product_id: int, product_data: Dict) -> bool:
    """更新产品"""
    updates = []
    params = []
    
    for key in ['code', 'channel', 'market', 'asset_type', 'currency', 'is_qdii', 
                'track_index', 'product_name', 'category', 'source', 
                'buy_fee_rate', 'sell_fee_rate', 'buy_confirm_offset', 
                'sell_confirm_offset', 'cutoff_time', 'note', 'is_active']:
        if key in product_data:
            updates.append(f"{key} = %s")
            params.append(product_data[key])
    
    if not updates:
        return False
    
    # 如果更新 code，同时更新 product_code
    if 'code' in product_data:
        updates.append("product_code = %s")
        params.append(product_data['code'])
    
    params.append(product_id)
    
    sql = f"UPDATE products SET {', '.join(updates)} WHERE id = %s"
    execute_update(sql, tuple(params))
    return True


def delete_product(product_id: int) -> bool:
    """删除产品（软删除：设置 is_active=0）"""
    sql = "UPDATE products SET is_active = 0 WHERE id = %s"
    execute_update(sql, (product_id,))
    return True


def get_sell_fee_rate(product: Dict, holding_days: int) -> float:
    """
    根据持有天数获取赎回费率
    
    Args:
        product: 产品配置字典
        holding_days: 持有天数
    
    Returns:
        float: 赎回费率（如 0.015 表示 1.5%）
    """
    tiers = product.get('sell_fee_tiers', [])
    
    if not tiers:
        return 0.0
    
    for tier in tiers:
        min_days = tier.get('min_days', 0)
        max_days = tier.get('max_days')  # None 表示无上限
        
        if holding_days >= min_days:
            if max_days is None or holding_days < max_days:
                return tier.get('rate', 0)
    
    return 0.0



