"""
指标计算配置文件
"""
import os
import json
from pathlib import Path

# 获取项目根目录（scripts/indicator/config.py -> scripts/indicator -> scripts -> 项目根目录）
def get_project_root():
    """获取项目根目录"""
    current_file = Path(__file__).resolve()
    # scripts/indicator/config.py -> scripts/indicator -> scripts -> 项目根目录
    scripts_dir = current_file.parent.parent
    project_root = scripts_dir.parent
    return project_root

# 从 config/db_config.json 读取数据库配置
def load_db_config():
    """从 config/db_config.json 读取数据库配置"""
    project_root = get_project_root()
    config_file = project_root / 'config' / 'db_config.json'
    
    # 优先使用环境变量（如果设置了）
    db_config = {
        'host': os.getenv('DB_HOST'),
        'port': os.getenv('DB_PORT'),
        'user': os.getenv('DB_USER'),
        'password': os.getenv('DB_PASSWORD'),
        'database': os.getenv('DB_NAME'),
        'charset': os.getenv('DB_CHARSET', 'utf8mb4')
    }
    
    # 如果环境变量未设置，从配置文件读取
    if not all([db_config['host'], db_config['port'], db_config['user'], 
                db_config['password'], db_config['database']]):
        if config_file.exists():
            try:
                with open(config_file, 'r', encoding='utf-8') as f:
                    file_config = json.load(f)
                    # 环境变量优先级更高，如果环境变量未设置则使用文件配置
                    db_config['host'] = db_config['host'] or file_config.get('host', '127.0.0.1')
                    db_config['port'] = db_config['port'] or file_config.get('port', 9009)
                    db_config['user'] = db_config['user'] or file_config.get('user', 'dca')
                    db_config['password'] = db_config['password'] or file_config.get('password', '')
                    db_config['database'] = db_config['database'] or file_config.get('database', 'dca_v2')
                    db_config['charset'] = db_config['charset'] or file_config.get('charset', 'utf8mb4')
            except Exception as e:
                print(f"警告：无法读取数据库配置文件 {config_file}: {e}")
                print("使用默认配置")
        else:
            print(f"警告：数据库配置文件不存在: {config_file}")
            print("使用默认配置")
    
    # 确保类型正确
    db_config['port'] = int(db_config['port']) if db_config['port'] else 9009
    
    # 如果仍然有未设置的项，使用默认值
    db_config['host'] = db_config['host'] or '127.0.0.1'
    db_config['user'] = db_config['user'] or 'dca'
    db_config['password'] = db_config['password'] or ''
    db_config['database'] = db_config['database'] or 'dca_v2'
    db_config['charset'] = db_config['charset'] or 'utf8mb4'
    
    return db_config

# 数据库配置（从 config/db_config.json 读取）
DB_CONFIG = load_db_config()
