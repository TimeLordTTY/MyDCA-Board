# Python脚本使用说明

## 目录结构

```
scripts/
├── market/          # 行情数据采集
│   ├── README.md
│   ├── requirements.txt
│   ├── config.py
│   ├── fund_collector.py    # 基金净值采集
│   └── etf_collector.py     # ETF行情采集
├── indicator/       # 指标计算
│   ├── README.md
│   ├── requirements.txt
│   ├── calculator.py         # 指标计算主程序
│   ├── ma_calculator.py     # 移动平均线计算
│   ├── macd_calculator.py   # MACD指标计算
│   └── rsi_calculator.py    # RSI指标计算
├── scheduler/       # 定时任务调度
│   ├── README.md
│   ├── requirements.txt
│   └── scheduler.py          # 定时任务调度器
└── test_all.py     # 测试脚本
```

## 安装依赖

### 1. 行情数据采集依赖

```bash
cd scripts/market
pip install -r requirements.txt
```

### 2. 指标计算依赖

```bash
cd scripts/indicator
pip install -r requirements.txt
```

**注意**：ta-lib 库需要额外安装：
- Windows: 下载预编译的 wheel 文件或使用 conda
- Linux/Mac: 需要先安装 ta-lib C 库，然后安装 Python 包

### 3. 定时任务调度依赖

```bash
cd scripts/scheduler
pip install -r requirements.txt
```

### 4. 安装所有依赖

```bash
# 在项目根目录执行
pip install -r scripts/market/requirements.txt
pip install -r scripts/indicator/requirements.txt
pip install -r scripts/scheduler/requirements.txt
```

## 配置数据库连接

编辑 `scripts/market/config.py` 和 `scripts/indicator/config.py`，配置数据库连接信息：

```python
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'your_username',
    'password': 'your_password',
    'database': 'your_database',
    'charset': 'utf8mb4'
}
```

## 测试脚本

运行测试脚本验证所有模块是否正常工作：

```bash
cd scripts
python test_all.py
```

## 使用说明

### 行情数据采集

#### 基金净值采集

```bash
cd scripts/market
python fund_collector.py
```

#### ETF行情采集

```bash
cd scripts/market
python etf_collector.py
```

### 指标计算

```bash
cd scripts/indicator
python calculator.py
```

### 定时任务调度

```bash
cd scripts/scheduler
python scheduler.py
```

## 定时任务配置

定时任务默认配置：
- 基金净值采集：每天 18:00
- ETF实时行情：交易时间内每5分钟（9:30-15:00）
- ETF日K线：每天 15:30（收盘后）
- 指标计算：每天 16:00（数据采集完成后）

## 注意事项

1. **数据库连接**：确保数据库连接配置正确，且数据库服务正在运行
2. **数据源**：akshare 数据源需要网络连接，某些接口可能有访问限制
3. **错误处理**：脚本包含基本的错误处理，但建议在生产环境中添加更完善的日志和监控
4. **性能**：批量数据采集时，注意控制请求频率，避免对数据源造成压力

## 故障排查

### 导入错误

如果遇到模块导入错误，请检查：
1. Python 版本（建议 Python 3.8+）
2. 依赖是否已正确安装
3. 是否在正确的目录下运行脚本

### 数据库连接错误

如果遇到数据库连接错误，请检查：
1. 数据库服务是否运行
2. 连接配置是否正确
3. 数据库用户权限是否足够

### 数据采集失败

如果数据采集失败，请检查：
1. 网络连接是否正常
2. akshare 数据源是否可访问
3. 产品代码是否正确
