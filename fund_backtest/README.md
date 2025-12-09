# 基金定投策略回测引擎

一个灵活、可扩展的基金定投策略回测框架。引擎与策略分离，支持以插件形式添加新策略。

## 特性

- **引擎与策略分离**：引擎只负责数据迭代和交易执行，策略逻辑完全独立
- **易于扩展**：新增策略只需继承基类并实现 `on_bar` 方法
- **纯 Python 实现**：仅使用标准库，无需安装额外依赖
- **详细的回测结果**：输出每日持仓、交易、收益等详细数据

## 项目结构

```
fund_backtest/
├── engine/                  # 回测引擎核心
│   ├── __init__.py
│   ├── types.py            # 数据结构定义
│   ├── data_feed.py        # 行情数据迭代器
│   ├── portfolio.py        # 组合管理与交易
│   └── backtester.py       # 回测主循环
├── strategies/             # 策略实现
│   ├── __init__.py
│   ├── base.py            # 策略基类
│   ├── sample_sip.py      # 示例：普通定投策略
│   └── sample_tp_dip.py   # 示例：止盈补仓策略
├── utils/                  # 工具函数
│   ├── __init__.py
│   └── csv_loader.py      # CSV 数据加载
├── main.py                # 命令行入口
├── requirements.txt       # 依赖声明
└── README.md
```

## 快速开始

### 1. 生成示例数据

```bash
cd fund_backtest
python main.py --generate-sample
```

这会生成 `sample_nav.csv` 文件，包含 3 年的模拟净值数据。

### 2. 运行回测

**普通定投策略（每月定额买入，不止盈不补仓）：**

```bash
python main.py --csv sample_nav.csv --fund TEST --strategy sip
```

**止盈补仓策略（多档止盈 + 多档逢低加仓）：**

```bash
python main.py --csv sample_nav.csv --fund TEST --strategy tp_dip
```

### 3. 查看结果

回测完成后会：
- 在终端打印摘要统计
- 生成 `result_<基金代码>_<策略名>.csv` 详细结果文件

## 命令行参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--csv` | 净值 CSV 文件路径 | 必填 |
| `--fund` | 基金代码（标记用） | unknown |
| `--strategy` | 策略名称 (sip/tp_dip) | sip |
| `--initial` | 初始投入金额 | 10000 |
| `--periodic` | 每月定投金额 | 1000 |
| `--buy-fee` | 买入费率 | 0.0015 |
| `--sell-fee` | 卖出费率 | 0.005 |
| `--date-col` | CSV 日期列名 | date |
| `--nav-col` | CSV 净值列名 | nav |
| `--output` | 输出文件路径 | 自动生成 |

## CSV 数据格式

CSV 文件至少需要两列：
- 日期列（支持列名：date, 净值日期, FSRQ, 日期）
- 净值列（支持列名：nav, 单位净值, DWJZ, 净值）

示例：
```csv
date,nav
2023-01-03,1.0000
2023-01-04,1.0120
2023-01-05,0.9980
...
```

## 如何添加新策略

### 1. 创建策略文件

在 `strategies/` 目录下创建新文件，如 `my_strategy.py`：

```python
from typing import Dict, Any
from .base import Strategy, Context, Signal

class MyStrategy(Strategy):
    """我的自定义策略"""
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)
        # 读取配置参数
        self.param1 = self.config.get('param1', 100)
    
    def on_start(self) -> None:
        """初始化策略状态"""
        self.state['my_state'] = 0
    
    def on_bar(self, ctx: Context) -> Signal:
        """
        每日处理逻辑
        
        ctx 包含：
        - ctx.date: 当前日期
        - ctx.nav: 当前净值
        - ctx.portfolio: 组合对象（可读取持仓信息）
        - ctx.cash_inflow: 本日新增定投资金
        - ctx.cash_pool: 可用现金池
        - ctx.state: 策略状态字典
        
        返回 Signal 指示交易操作：
        - buy_cash: 买入金额
        - sell_units: 卖出份额
        - note: 备注
        """
        # 你的策略逻辑
        if some_buy_condition:
            return Signal(buy_cash=1000, note="买入信号")
        elif some_sell_condition:
            return Signal(sell_units=100, note="卖出信号")
        return Signal()
```

### 2. 注册策略

在 `main.py` 中添加导入和注册：

```python
from fund_backtest.strategies.my_strategy import MyStrategy

STRATEGY_REGISTRY = {
    'sip': SipStrategy,
    'tp_dip': TpDipStrategy,
    'my_strategy': MyStrategy,  # 添加新策略
}
```

### 3. 使用新策略

```bash
python main.py --csv nav.csv --strategy my_strategy
```

## 内置策略说明

### 1. 普通定投策略 (sip)

最朴素的定期定额策略：
- 每月第一个交易日，将新增资金全部买入
- 不做任何止盈或补仓操作
- 作为基准策略，用于对比其他复杂策略的效果

### 2. 止盈补仓策略 (tp_dip)

多档止盈 + 多档逢低加仓：

**止盈档位（默认）：**
| 收益率 | 卖出比例 |
|--------|----------|
| ≥10% | 卖出 25% |
| ≥20% | 卖出 25% |
| ≥30% | 卖出 50% |

**补仓档位（默认）：**
| 回撤 | 补仓金额 |
|------|----------|
| ≥5% | 500 元 |
| ≥10% | 1000 元 |
| ≥15% | 1500 元 |

可通过配置自定义档位参数。

## 核心概念

### Context（上下文）

策略的 `on_bar` 方法接收的上下文对象，包含当日所有必要信息：

```python
@dataclass
class Context:
    date: datetime       # 当前日期
    nav: float          # 当前净值
    portfolio: Portfolio # 组合对象
    cash_inflow: float  # 本日新增资金
    cash_pool: float    # 可用现金池总额
    state: Dict         # 策略状态
```

### Signal（信号）

策略返回的交易信号：

```python
@dataclass
class Signal:
    buy_cash: float = 0.0    # 买入金额
    sell_units: float = 0.0  # 卖出份额
    note: str = ""           # 备注
```

### Portfolio（组合）

可读取的组合属性：
- `units`: 持有份额
- `total_cost`: 累计投入本金
- `market_value`: 当前市值
- `total_value`: 总资产（市值 + 组合内现金）
- `unrealized_pnl`: 浮动盈亏
- `unrealized_pnl_pct`: 浮动盈亏比例

## 设计原则

1. **引擎不包含策略逻辑**：所有买卖判断都在策略的 `on_bar` 方法中
2. **策略可独立开发测试**：策略只需依赖 Context 和 Signal
3. **配置驱动**：策略参数通过 config 字典传入，便于调参
4. **状态持久化**：策略可通过 `ctx.state` 保存内部状态

## License

MIT

