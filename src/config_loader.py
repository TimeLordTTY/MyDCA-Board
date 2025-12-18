"""配置加载器"""
import json
import os
from pathlib import Path

_project_root = None

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
    return {h['product_code']: h['amount'] for h in holdings}

