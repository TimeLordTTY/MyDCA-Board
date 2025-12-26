# Strategy Lab 回测引擎

独立的回测引擎模块，用于验证策略参数，不侵入生产执行模块。

## 目录结构

```
strategy_lab/
├── data/              # 数据层
│   ├── daily_bar.py   # DailyBar 数据结构
│   └── provider.py    # DataProvider（统一数据接口）
├── account/           # 账户与资金模型
│   ├── cash_model.py  # CashModel（cash_pool + wait_pool）
│   └── fee_model.py   # FeeModel（场内手续费）
├── strategy/          # 策略接口与实现
│   ├── base.py        # Strategy 基类
│   ├── decision.py    # Decision 数据结构
│   ├── context.py     # Context 数据结构
│   ├── simple.py      # SimpleStrategy
│   ├── drawdown.py    # DrawdownStrategy
│   ├── percentile.py  # PercentileStrategy
│   └── registry.py    # 策略注册表
├── simulator/         # 执行模拟器
│   └── executor.py    # ExecutionSimulator
├── metrics/           # 统计与评估
│   ├── calculator.py  # MetricsCalculator
│   └── reporter.py    # Reporter（输出到数据库）
└── runner/            # 回测运行器
    ├── backtester.py  # Backtester（主循环）
    └── param_runner.py # ParamRunner（参数组合对比）
```

## 使用示例

### 1. 简单回测

```python
from datetime import date
from strategy_lab.data.provider import DataProvider
from strategy_lab.account.cash_model import CashModel
from strategy_lab.strategy.simple import SimpleStrategy
from strategy_lab.runner.backtester import Backtester

# 创建数据提供者
data_provider = DataProvider(auto_fetch=True)

# 创建资金模型
cash_model = CashModel(
    initial_cash=10000.0,
    monthly_deposit=1000.0,
    deposit_day=10,
    min_trade_amount=1000.0
)

# 创建策略
strategy = SimpleStrategy({
    "base_amount": 1000.0,
    "frequency": "monthly",
    "day": 10
})

# 创建回测引擎
backtester = Backtester(
    data_provider=data_provider,
    cash_model=cash_model,
    strategy=strategy,
    product_id=1,
    is_exchange=True,
    start_date=date(2023, 1, 1),
    end_date=date(2024, 12, 31)
)

# 执行回测
result = backtester.run()
print(f"回测完成: summary_id={result['summary_id']}")
print(f"年化收益: {result['metrics']['annual_return']:.2%}")
```

### 2. 参数组合对比

```python
from strategy_lab.runner.param_runner import ParamRunner

# 创建参数运行器
param_runner = ParamRunner(
    data_provider=data_provider,
    product_id=1,
    strategy_key="drawdown",
    is_exchange=True
)

# 定义参数组合
param_sets = [
    {
        "param_set_id": "drawdown_2_4_8",
        "params": {
            "base_amount": 1000.0,
            "drawdown_thresholds": [0.02, 0.04, 0.08],
            "use_ratios": [0.3, 0.5, 1.0]
        }
    },
    {
        "param_set_id": "drawdown_5_10",
        "params": {
            "base_amount": 1000.0,
            "drawdown_thresholds": [0.05, 0.10],
            "use_ratios": [0.5, 1.0]
        }
    }
]

# 运行参数组合对比
results = param_runner.run_param_sets(
    param_sets=param_sets,
    initial_cash=10000.0,
    monthly_deposit=1000.0,
    start_date=date(2023, 1, 1),
    end_date=date(2024, 12, 31)
)

# 查看结果
for result in results:
    print(f"参数组合 {result['param_set_id']}: 年化收益 {result['metrics']['annual_return']:.2%}")
```

### 3. 生产判断层（AdviceEngine）

```python
from core.advice_engine import AdviceEngine

# 创建建议引擎
engine = AdviceEngine()

# 生成买入建议
advice = engine.generate_advice(
    product_id=1,
    planned_amount=1000.0,
    cash_pool=5000.0,
    wait_pool=0.0
)

print(f"建议状态: {advice.status}")
print(f"建议金额: {advice.suggested_amount}")
print(f"建议限价: {advice.suggested_limit_price}")
print(f"原因: {advice.reasons}")
```

## 数据库表

回测结果存储在以下数据库表中：

- `strategy_config`: 策略参数配置
- `backtest_summary`: 回测汇总结果
- `backtest_daily`: 每日回测数据
- `backtest_trades`: 逐笔成交记录

执行 SQL 脚本创建表：
```bash
mysql -u root -p dca < scripts/sql/update/add_strategy_lab_tables.sql
```

## 策略实现

### SimpleStrategy
- 固定周频/月频买入
- 参数：`base_amount`, `frequency`, `day`

### DrawdownStrategy
- 回撤触发加仓
- 参数：`base_amount`, `drawdown_thresholds`, `use_ratios`

### PercentileStrategy
- 滚动N日分位判断
- 参数：`base_amount`, `window`, `buy_percentile`, `hold_percentile`

## 注意事项

1. **不输出CSV**：所有结果写入数据库表
2. **可解释性**：每个 Decision 必须包含 reasons
3. **参数可配置**：所有策略参数存储在 `strategy_config` 表
4. **回测独立**：不侵入生产执行模块，不触发下单

