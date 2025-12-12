# 策略版本管理指南

## 概述

回测引擎现在支持策略的版本管理，允许同一个策略名称（如 `profit_recycle`）同时存在多个版本实现（如 `v8`, `v10`），并能在运行时灵活选择。

## 核心特性

1. **双重选择机制**：`strategy_name` + `strategy_version`
2. **展示名称统一**：版本号不出现在用户界面展示中
3. **向后兼容**：不指定版本时自动使用默认版本
4. **结果可区分**：CSV 和 summary 中包含版本信息

## 使用方法

### 1. 在 run_backtest.py 中选择策略和版本

```python
# 选择策略名称
策略类型 = "profit_recycle"

# 选择策略版本（可选）
策略版本 = "v10"  # 或 None 表示使用默认版本
```

### 2. 可用策略及版本

当前注册的策略：

- **profit_recycle**
  - `v8`：深跌小额补仓版（基础版）
  - `v10`：动态预投入 + 分级深跌补仓版（默认）
  
- **pure_sip**
  - `default`：纯基础定投策略
  
- **ma_enhanced**
  - `v2`：MA250 均线增强定投策略

### 3. 策略展示

无论选择哪个版本，策略的展示名称都不包含版本号：

```
策略名称: 利润回收策略 v10 — 动态预投入 + 分级深跌补仓版
策略标识: profit_recycle
策略版本: v10
```

## 开发指南：添加新版本策略

### 1. 创建策略类

在策略类中定义三个类属性：

```python
class ProfitRecycleStrategyV11(Strategy):
    """
    利润回收策略 v11 — 新特性说明
    """
    
    # 策略标识（用于注册表）
    strategy_key = "profit_recycle"  # 保持一致，表示同一策略家族
    strategy_version = "v11"  # 新版本号
    display_name = "利润回收策略 v11 — 新特性"  # 展示名称
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)
        # ... 你的实现
```

### 2. 注册策略

在 `core/backtest/strategies/__init__.py` 中注册：

```python
from .profit_recycle_v11 import ProfitRecycleStrategyV11

# 注册新版本
STRATEGY_REGISTRY.register(
    "profit_recycle", 
    ProfitRecycleStrategyV11, 
    version="v11", 
    set_as_default=True  # 是否设为默认版本
)
```

### 3. 配置参数

在 `scripts/run_backtest.py` 中添加配置（如果需要新参数）：

```python
利润回收策略参数_v11 = {
    "new_param": 0.5,
    # ... 其他参数
}
```

## 回测结果

### Summary 包含的版本信息

```python
summary = {
    'strategy_name': '利润回收策略 v10 — 动态预投入 + 分级深跌补仓版',  # 展示名称
    'strategy_key': 'profit_recycle',  # 策略标识
    'strategy_version': 'v10',  # 版本号
    'fund_code': '163406',
    # ... 其他统计信息
}
```

### CSV 文件命名

输出文件名会包含版本信息（如果不是 default）：

```
backtest_163406_profit_recycle_v10.csv  # 指定了 v10 版本
backtest_163406_pure_sip.csv  # 使用默认版本，不带版本后缀
```

## 策略对比分析

当你想对比不同版本的策略时：

```python
# 第一次运行
策略类型 = "profit_recycle"
策略版本 = "v8"
# 运行后生成: backtest_163406_profit_recycle_v8.csv

# 第二次运行
策略类型 = "profit_recycle"
策略版本 = "v10"
# 运行后生成: backtest_163406_profit_recycle_v10.csv
```

两个 CSV 文件中都包含 `strategy_version` 字段，便于后续分析。

## API 使用

如果你在代码中直接使用策略注册表：

```python
from core.backtest.strategies import STRATEGY_REGISTRY

# 获取特定版本
strategy_class = STRATEGY_REGISTRY.get("profit_recycle", "v10")

# 获取默认版本
strategy_class = STRATEGY_REGISTRY.get("profit_recycle")  # 或 version=None

# 列出所有可用策略
strategies = STRATEGY_REGISTRY.list_strategies()
# 返回: {'profit_recycle': ['default', 'v8', 'v10'], 'pure_sip': ['default'], ...}
```

## 注意事项

1. **版本号不影响展示**：用户界面显示的策略名称由 `display_name` 决定，与版本号无关
2. **默认版本设置**：每个策略家族应该有一个 `default` 版本，或者明确指定某个版本为默认
3. **向后兼容**：旧代码不指定版本号时，自动使用默认版本
4. **结果记录**：所有回测结果都包含完整的版本信息，便于追溯和对比

## 示例：完整流程

```python
# 在 scripts/run_backtest.py 中配置
策略类型 = "profit_recycle"
策略版本 = "v10"

# 运行回测
python scripts/run_backtest.py

# 输出示例
"""
📈 策略类型: profit_recycle
   策略版本: v10
   策略名称: 利润回收策略 v10 — 动态预投入 + 分级深跌补仓版
   策略标识: profit_recycle
   策略版本: v10
"""

# 生成的文件
# backtest_163406_profit_recycle_v10.csv
```

## 故障排除

### 错误：未知策略名称

```
❌ 策略加载失败: 未知策略名称: 'xxx'
可用策略: ['profit_recycle', 'pure_sip', 'ma_enhanced']
```

**解决**：检查 `策略类型` 是否拼写正确

### 错误：版本不存在

```
❌ 策略加载失败: 策略 'profit_recycle' 不存在版本: 'v99'
可用版本: ['default', 'v8', 'v10']
```

**解决**：检查 `策略版本` 是否正确，或设为 `None` 使用默认版本

