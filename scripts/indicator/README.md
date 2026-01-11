# 指标计算模块

## 目录结构

```
scripts/indicator/
├── README.md              # 本文件
├── requirements.txt       # Python依赖
├── calculator.py          # 指标计算主程序
├── ma_calculator.py       # 移动平均线计算
├── macd_calculator.py     # MACD指标计算
├── rsi_calculator.py      # RSI指标计算
└── utils.py              # 工具函数
```

## 功能说明

### 1. 移动平均线（MA）
- 计算MA5、MA10、MA20、MA30、MA60等
- 基于收盘价计算

### 2. MACD指标
- 计算DIF、DEA、MACD柱
- 默认参数：快线12，慢线26，信号线9

### 3. RSI指标
- 计算RSI6、RSI12、RSI24
- 相对强弱指标

## 使用方法

```bash
# 安装依赖
pip install -r requirements.txt

# 计算所有指标
python calculator.py

# 计算特定指标
python ma_calculator.py
python macd_calculator.py
python rsi_calculator.py
```
