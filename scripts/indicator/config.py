"""
指标计算配置文件
"""
import os

# 数据库配置（与market/config.py保持一致）
DB_CONFIG = {
    'host': os.getenv('DB_HOST', '127.0.0.1'),
    'port': int(os.getenv('DB_PORT', 9009)),
    'user': os.getenv('DB_USER', 'dca'),
    'password': os.getenv('DB_PASSWORD', 'FW5GxWai5Shyrekb'),
    'database': os.getenv('DB_NAME', 'dca_v2'),
    'charset': 'utf8mb4'
}
