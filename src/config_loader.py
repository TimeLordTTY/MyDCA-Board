"""配置加载器"""
import json
from pathlib import Path

def get_project_root():
    """获取项目根目录"""
    return Path(__file__).parent.parent

def load_products():
    """加载产品配置"""
    config_path = get_project_root() / "config" / "products.json"
    with open(config_path, 'r', encoding='utf-8') as f:
        return json.load(f)

def load_holdings():
    """加载持仓配置"""
    config_path = get_project_root() / "config" / "holdings.json"
    with open(config_path, 'r', encoding='utf-8') as f:
        return json.load(f)

def get_holdings_map():
    """获取产品代码到持仓份额的映射"""
    holdings = load_holdings()
    return {h['products_id']: h['amount'] for h in holdings}

