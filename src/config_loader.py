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
    否则使用文件所在目录的父目录
    """
    global _project_root
    
    # 每次检查环境变量（支持运行时修改）
    env_root = os.environ.get('MYDCA_PROJECT_ROOT')
    if env_root:
        return Path(env_root)
    
    if _project_root is None:
        _project_root = Path(__file__).parent.parent
    
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
    """加载产品配置（带默认值填充）"""
    config_path = get_project_root() / "config" / "products.json"
    with open(config_path, 'r', encoding='utf-8') as f:
        products = json.load(f)
    
    # 为每个产品应用默认值
    return [_apply_product_defaults(p) for p in products]


def load_products_raw() -> List[Dict]:
    """加载产品配置（原始数据，不填充默认值）"""
    config_path = get_project_root() / "config" / "products.json"
    with open(config_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def get_product(product_code: str) -> Optional[Dict]:
    """根据产品代码获取产品配置"""
    products = load_products()
    for p in products:
        if p['product_code'] == product_code:
            return p
    return None


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
    """加载持仓配置"""
    config_path = get_project_root() / "config" / "holdings.json"
    with open(config_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def get_holdings_map():
    """获取产品代码到持仓份额的映射"""
    holdings = load_holdings()
    return {h['product_code']: h['amount'] for h in holdings}


def load_json_config(filename: str) -> Any:
    """加载 JSON 配置文件"""
    config_path = get_project_root() / "config" / filename
    if not config_path.exists():
        return None
    
    with open(config_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def load_accounts() -> List[Dict]:
    """加载账户配置 (accounts.json)"""
    config = load_json_config('accounts.json')
    if config is None:
        # 返回默认账户列表
        return [
            {'id': 'ylb_life', 'name': '余利宝生活费'},
            {'id': 'ylb_finance', 'name': '余利宝理财金'},
            {'id': 'couple_pocket', 'name': '情侣小荷包'},
            {'id': 'other', 'name': '其他'},
        ]
    return config.get('accounts', [])


def load_categories() -> Dict:
    """加载分类配置 (categories.json)"""
    config = load_json_config('categories.json')
    if config is None:
        # 返回默认分类
        return {
            'expense': {
                '其他': [],
                '购物消费': ['日用百货', '服饰鞋包', '数码电子'],
                '食品餐饮': ['早午晚餐', '饮料甜点', '水果零食'],
                '出行交通': ['公共交通', '打车租车', '加油停车'],
                '休闲娱乐': ['电影演出', '游戏充值', '旅行度假'],
                '居家生活': ['水电煤气', '物业房租', '家居家电'],
                '文化教育': ['书籍资料', '培训课程'],
                '送礼人情': ['红包礼金', '礼品馈赠'],
                '健康医疗': ['医疗药品', '运动健身'],
            },
            'income': {
                '其他': [],
                '中奖': [],
                '理财盈利': [],
                '礼金人情': [],
                '借入': [],
                '奖金': [],
                '兼职外快': [],
                '工资': [],
                '二手闲置': [],
                '补贴': [],
                '报销': [],
            }
        }
    return config

