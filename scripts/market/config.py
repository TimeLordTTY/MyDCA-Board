"""
行情数据采集配置文件
"""
import os

# 数据库配置
DB_CONFIG = {
    'host': os.getenv('DB_HOST', '127.0.0.1'),
    'port': int(os.getenv('DB_PORT', 9009)),
    'user': os.getenv('DB_USER', 'dca'),
    'password': os.getenv('DB_PASSWORD', 'FW5GxWai5Shyrekb'),
    'database': os.getenv('DB_NAME', 'dca_v2'),
    'charset': 'utf8mb4'
}

# 数据源配置
DATA_SOURCE = {
    'fund': 'akshare',  # 基金数据源：akshare 或 fund
    'stock': 'akshare',  # 股票数据源
    'etf': 'akshare',   # ETF数据源
}

# 采集配置
COLLECT_CONFIG = {
    'fund_nav_update_time': '18:00',  # 基金净值更新时间（通常T+1日18:00）
    'market_open_time': '09:30',      # 市场开盘时间
    'market_close_time': '15:00',     # 市场收盘时间
    'retry_times': 3,                 # 重试次数
    'retry_delay': 5,                 # 重试延迟（秒）
}
