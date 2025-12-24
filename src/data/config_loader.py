"""配置加载器

产品配置字段说明：
- product_code: 产品唯一代码
- product_name: 产品名称
- source: 数据源 (fund/cmbc)
- category: 分类 (fund/bank)
- market: 市场类型 (cn/qdii/bank_nav/cash_like)，用于确定确认延迟
- buy_fee_rate: 申购费率，如 0.0012 表示 0.12%
- sell_fee_rate: 赎回费率
- buy_confirm_offset: 买入确认延迟交易日数（默认 cn=1, qdii=2）
- sell_confirm_offset: 赎回确认延迟交易日数
- cutoff_time: 交易截止时间（默认 15:00）
"""
import json
import os
from pathlib import Path
from typing import Dict, List, Any, Optional

_project_root = None

# 产品配置默认值
PRODUCT_DEFAULTS = {
    'market': 'cn',           # 市场类型：cn/qdii/bank_nav/cash_like
    'buy_fee_rate': 0.0,      # 申购费率（0.0012 = 0.12%）
    'sell_fee_tiers': [],     # 赎回费率阶梯（按持有天数）
    'buy_confirm_offset': None,   # None 表示根据 market 自动计算
    'sell_confirm_offset': None,
    'cutoff_time': '15:00',   # 交易截止时间
}

# 默认赎回费率阶梯（适用于大多数基金）
DEFAULT_SELL_FEE_TIERS = [
    {"min_days": 0, "max_days": 7, "rate": 0.015},      # 7天内：1.5%
    {"min_days": 7, "max_days": 30, "rate": 0.0075},    # 7-30天：0.75%
    {"min_days": 30, "max_days": 365, "rate": 0.005},   # 30-365天：0.5%
    {"min_days": 365, "max_days": 730, "rate": 0.0025}, # 1-2年：0.25%
    {"min_days": 730, "max_days": None, "rate": 0},     # 2年以上：0
]

# 根据 market 的默认确认延迟
MARKET_CONFIRM_OFFSET = {
    'cn': 1,        # 国内基金 T+1
    'bank_nav': 1,  # 银行理财 T+1
    'qdii': 2,      # QDII T+2
    'cash_like': 0, # 货币基金 T+0
}


def get_project_root():
    """获取项目根目录
    
    优先使用环境变量 MYDCA_PROJECT_ROOT（用于测试），
    否则从 src/data/ 向上找到项目根目录
    """
    global _project_root
    
    # 每次检查环境变量（支持运行时修改）
    env_root = os.environ.get('MYDCA_PROJECT_ROOT')
    if env_root:
        return Path(env_root)
    
    if _project_root is None:
        # 当前文件在 src/data/，需要向上 3 层到达项目根目录
        _project_root = Path(__file__).parent.parent.parent
    
    return _project_root


def _apply_product_defaults(product: Dict) -> Dict:
    """为产品配置应用默认值"""
    result = dict(product)
    
    for key, default_value in PRODUCT_DEFAULTS.items():
        if key not in result:
            result[key] = default_value
    
    # 根据 market 计算默认的 confirm_offset
    market = result.get('market', 'cn')
    default_offset = MARKET_CONFIRM_OFFSET.get(market, 1)
    
    if result.get('buy_confirm_offset') is None:
        result['buy_confirm_offset'] = default_offset
    if result.get('sell_confirm_offset') is None:
        result['sell_confirm_offset'] = default_offset
    
    return result


def load_products() -> List[Dict]:
    """加载产品配置（从数据库读取，带默认值填充）"""
    from data.product_service import get_products
    return get_products()


def load_products_raw() -> List[Dict]:
    """加载产品配置（从数据库读取，原始数据）"""
    from data.product_service import get_products
    return get_products()


def get_product(product_code: str) -> Optional[Dict]:
    """根据产品代码获取产品配置（从数据库读取，兼容旧接口）"""
    from data.product_service import get_product_by_code
    return get_product_by_code(product_code)


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
    
    # 如果没有配置阶梯费率，使用默认值
    if not tiers:
        tiers = DEFAULT_SELL_FEE_TIERS
    
    for tier in tiers:
        min_days = tier.get('min_days', 0)
        max_days = tier.get('max_days')  # None 表示无上限
        
        if holding_days >= min_days:
            if max_days is None or holding_days < max_days:
                return tier.get('rate', 0)
    
    # 未匹配到任何阶梯，返回0
    return 0.0


def format_sell_fee_tiers(product: Dict) -> str:
    """格式化显示赎回费率阶梯"""
    tiers = product.get('sell_fee_tiers', [])
    if not tiers:
        tiers = DEFAULT_SELL_FEE_TIERS
    
    lines = []
    for tier in tiers:
        min_days = tier.get('min_days', 0)
        max_days = tier.get('max_days')
        rate = tier.get('rate', 0) * 100
        
        if max_days is None:
            lines.append(f"    {min_days}天以上: {rate:.2f}%")
        else:
            lines.append(f"    {min_days}-{max_days}天: {rate:.2f}%")
    
    return "\n".join(lines)


def load_holdings():
    """加载持仓配置（已废弃，始终返回空列表）"""
    # 持仓现在完全由 transactions.csv 计算得出
    return []


def get_holdings_map():
    """获取产品代码到持仓份额的映射（已废弃，始终返回空字典）"""
    # 持仓现在完全由 HoldingsCalculator 计算得出
    return {}


def load_json_config(filename: str) -> Any:
    """
    加载 JSON 配置文件（已废弃，保留仅为兼容）
    
    注意：db_config.json 仍从文件读取（数据库连接配置）
    """
    if filename == 'db_config.json':
        config_path = get_project_root() / "config" / filename
        if not config_path.exists():
            return None
        with open(config_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    # 其他配置文件已迁移到数据库，不再从文件读取
    return None


def load_accounts() -> List[Dict]:
    """加载账户配置（从数据库读取）"""
    from data.account_service import get_accounts
    accounts = get_accounts()
    # 转换为旧格式（兼容性）
    result = []
    for acc in accounts:
        result.append({
            'id': acc.get('account_code') or acc.get('account_id'),
            'name': acc.get('account_name'),
            'account_type': acc.get('account_type'),
            'linked_product': None,  # 需要通过 product_id 查询
            'group': None,  # 需要通过 account_groups 查询
            'receives_profit': False,  # 需要通过 account_groups 查询
            'note': acc.get('note')
        })
    return result


def load_account_groups() -> Dict:
    """加载账户组配置（从数据库读取）"""
    from data.db_connector import execute_query
    sql = """
        SELECT 
            ag.group_code, ag.group_name, ag.linked_product_id, ag.profit_account_id,
            p.code AS linked_product_code,
            a.account_code AS profit_account_code
        FROM account_groups ag
        LEFT JOIN products p ON ag.linked_product_id = p.id
        LEFT JOIN accounts a ON ag.profit_account_id = a.id
    """
    rows = execute_query(sql)
    
    result = {}
    for row in rows:
        group_code = row['group_code']
        result[group_code] = {
            'name': row['group_name'],
            'linked_product': row['linked_product_code'],
            'profit_account': row['profit_account_code']
        }
    return result


def get_account(account_id: str) -> Optional[Dict]:
    """根据账户ID获取账户配置（从数据库读取）"""
    from data.account_service import get_account_by_code
    acc = get_account_by_code(account_id)
    if acc:
        return {
            'id': acc.get('account_code') or acc.get('account_id'),
            'name': acc.get('account_name'),
            'account_type': acc.get('account_type'),
            'linked_product': None,  # 需要通过 product_id 查询
            'group': None,  # 需要通过 account_groups 查询
            'note': acc.get('note')
        }
    return None


def get_accounts_by_group(group_id: str) -> List[Dict]:
    """获取属于指定组的所有账户（从数据库读取）"""
    from data.account_service import get_accounts_by_group
    accounts = get_accounts_by_group(group_id)
    # 转换为旧格式
    result = []
    for acc in accounts:
        result.append({
            'id': acc.get('account_code') or acc.get('account_id'),
            'name': acc.get('account_name'),
            'account_type': acc.get('account_type'),
            'group': group_id,
            'note': acc.get('note')
        })
    return result


def get_wenlibao_accounts() -> List[Dict]:
    """获取所有稳利宝子账户（从数据库读取）"""
    return get_accounts_by_group('wenlibao')


def get_account_name(account_id: str) -> str:
    """获取账户名称（从数据库读取）"""
    from data.account_service import get_account_name as _get_account_name
    return _get_account_name(account_id)


def load_categories() -> Dict:
    """加载分类配置（从数据库读取）"""
    from data.category_service import get_categories
    return get_categories()

