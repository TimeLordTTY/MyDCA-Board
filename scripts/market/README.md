# 行情数据采集模块

## 目录结构

```
scripts/market/
├── README.md              # 本文件
├── requirements.txt       # Python依赖
├── config.py             # 配置文件
├── collector.py          # 数据采集主程序
├── fund_collector.py     # 基金净值采集
├── etf_collector.py      # ETF行情采集
├── stock_collector.py    # 股票行情采集
└── bond_collector.py     # 债券行情采集
```

## 功能说明

### 1. 基金净值采集（fund_collector.py）
- 使用akshare或fund库采集基金净值
- 支持场外基金（FUND）、货币基金（MMF）
- 数据写入nav表

### 2. ETF行情采集（etf_collector.py）
- 使用akshare采集ETF实时行情
- 数据写入market_quote_realtime表
- 日K线数据写入market_bar_daily表

### 3. 股票行情采集（stock_collector.py）
- 使用akshare采集股票实时行情
- 数据写入market_quote_realtime表
- 日K线数据写入market_bar_daily表

### 4. 债券行情采集（bond_collector.py）
- 使用akshare采集债券行情
- 数据写入market_quote_realtime表

## 使用方法

```bash
# 安装依赖
pip install -r requirements.txt

# 采集基金净值
python fund_collector.py

# 采集ETF行情
python etf_collector.py

# 采集股票行情
python stock_collector.py
```

## 配置说明

编辑 `config.py` 配置数据库连接信息：

```python
DB_CONFIG = {
    'host': '127.0.0.1',
    'port': 9009,
    'user': 'dca',
    'password': 'FW5GxWai5Shyrekb',
    'database': 'dca_v2'
}
```
