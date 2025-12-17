# 财富中枢 - 净值采集系统

一个可控、易读、易维护的理财产品净值采集和快照系统。

---

## 📚 学习路线（30分钟掌控全局）

### 第一步：理解核心流程（10分钟）

**先看 `src/nav_collector.py`**

1. **`collect_and_store()`** - 主入口函数
```python
validate_configs()          # 校验配置文件
↓
process_single_product()    # 处理每个产品
↓
create_daily_snapshot()     # 生成快照（含收益率）
↓
generate_portfolio_summary() # 生成资产汇总
↓
输出汇总日志               # 显示执行结果
```

2. **`process_single_product()`** - 单产品处理
```python
fetch_and_validate_nav()    # 获取并校验净值
↓
save_nav_record()           # 存储到CSV
↓
计算PNL                     # 计算盈亏
```

**读完收获**：知道系统怎么运行，数据怎么流转

---

### 第二步：理解数据标准（10分钟）

**再看 `src/validator.py`**

1. **`validate_nav_record()`** - 净值数据校验
   - 检查必需字段：`PRODUCT_CODE`, `ISS_DATE`, `NAV`, `fetched_at`
   - 校验日期格式：必须是 `YYYY-MM-DD`
   - 校验NAV是数字
   - 校验PRODUCT_CODE一致性

2. **`validate_holdings_config()`** - 持仓配置校验
   - 确保 holdings.json 中的产品ID都在 products.json 中
   - 配置错误会立即退出

**读完收获**：知道什么数据能通过，什么会报错

---

### 第三步：理解数据源对接（10分钟）

**最后看 `src/adaptor/fund_client.py`**

1. **`query_latest_nav()`** - 获取净值
   - HTTP请求获取原始数据
   - 解析HTML表格
   - 标准化成统一格式
   - 返回 `List[Dict]`

2. **`_normalize_nav_record()`** - 数据标准化
   - 转换成系统统一格式
   - 添加必需字段
   - 格式化日期

**读完收获**：知道怎么对接新数据源

---

## 🏗️ 项目结构

```
MyDCA-Board/
├── config/                          # 配置文件
│   ├── products.json                # 产品列表
│   └── holdings.json                # 持仓配置
│
├── src/                             # 源代码
│   ├── nav_collector.py             # 【核心】主控协调器
│   ├── validator.py                 # 【核心】数据校验器
│   ├── holdings_calculator.py       # 【核心】持仓与成本计算器
│   ├── portfolio_summary.py         # 资产汇总模块
│   │
│   ├── adaptor/                     # 适配器目录
│   │   ├── __init__.py              
│   │   ├── cmbc_client.py           # 民生银行适配器
│   │   └── fund_client.py           # 东方财富基金适配器
│   │
│   ├── storage_csv.py               # CSV存储模块
│   ├── snapshot.py                  # 快照生成模块
│   └── config_loader.py             # 配置加载模块
│
├── scripts/                         # 脚本目录
│   ├── run_daily.py                 # 日常运行入口
│   ├── validate_transactions.py     # 交易流水校验工具
│   ├── export_nav_history.py        # 净值历史导出工具
│   ├── self_test.py                 # 自测脚本
│   └── test_force_rebuild.py        # 覆盖/重建功能测试
│
├── data/                            # 数据目录
│   ├── transactions.csv             # 交易流水（含确认净值）
│   ├── nav/                         # 净值CSV文件
│   │   └── {code}_{name}.csv        # 格式：产品代码_产品名称.csv
│   └── snapshots/                   # 快照目录
│       ├── daily.csv                # 日快照（含收益率，每天每产品一条）
│       ├── portfolio_by_nav_date.csv   # 按净值日期汇总
│       ├── portfolio_by_fetch_date.csv # 按采集日期汇总
│       └── portfolio_by_category.csv   # 按产品类型汇总
│
└── README.md                        # 本文件
```

---

## 🎯 框架设计

### 架构图
```
┌─────────────────────────────────────────────────────────┐
│                    nav_collector.py                      │
│                      (主控协调器)                        │
└────────────┬────────────────────────────────────────────┘
             │
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
┌─────────┐     ┌──────────┐
│validator│     │ adaptor/ │
│  (校验)  │     │ (适配器) │
└─────────┘     └────┬─────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
    ┌──────┐    ┌──────┐    ┌──────┐
    │ cmbc │    │ fund │    │ ... │
    └──────┘    └──────┘    └──────┘
                     │
                     ▼
              ┌──────────────┐
              │ storage_csv  │
              │  (存储层)     │
              └──────┬───────┘
                     │
                     ▼
              ┌──────────────┐
              │  snapshot    │
              │  (快照层)     │
              └──────────────┘
```

### 核心模块职责

| 模块 | 职责 | 关键函数 |
|------|------|---------|
| **nav_collector** | 总控流程，串联各模块 | `collect_and_store()` |
| **validator** | 校验配置和数据 | `validate_nav_record()` |
| **adaptor** | 对接各数据源 | `query_latest_nav()` |
| **storage_csv** | 净值数据落库 | `save_nav_record()` |
| **snapshot** | 生成持仓快照 | `create_daily_snapshot()` |
| **portfolio_summary** | 生成资产汇总（健壮性加固） | `generate_portfolio_summary()`, `safe_decimal()` |
| **holdings_calculator** | 从交易流水计算份额和成本 | `calc_position_incremental()` |
| **config_loader** | 加载配置文件 | `load_products()` |

---

## 🔄 功能流程详解

### 1. 配置校验阶段 (`validate_configs`)

```
加载 products.json 和 holdings.json
↓
校验每个产品配置 (id, name, source 必须存在)
↓
校验数据源有对应适配器
↓
校验持仓产品ID都在产品列表中
↓
通过 → 继续 | 失败 → 报错退出 (exit 1)
```

**目的**：提前发现配置错误，避免浪费时间

---

### 2. 净值采集阶段 (`process_single_product`)

```
遍历每个产品:
    ├─ 1. 选择对应适配器 (ADAPTOR_MAP[source])
    │
    ├─ 2. 调用适配器获取净值
    │      adaptor.query_latest_nav()
    │      ↓
    │      返回 List[Dict] (包含必需字段)
    │
    ├─ 3. 校验净值数据
    │      validate_nav_record()
    │      ↓
    │      检查字段完整性和格式正确性
    │
    ├─ 4. 存储到CSV
    │      save_nav_record()
    │      ↓
    │      按ISS_DATE去重（已存在则跳过）
    │
    └─ 5. 记录成功的产品（用于生成快照）
```

**目的**：逐个处理产品，错误隔离，单个失败不影响其他

---

### 3. 快照生成阶段 (`create_daily_snapshot`)

```
遍历成功采集的产品:
    ├─ 获取持仓份额 (transactions.csv + holdings.json回退)
    │
    ├─ 计算市值 (value = shares × NAV)
    │
    ├─ 计算PNL (当前value - 上一采集日value)
    │
    └─ 写入 daily.csv
         ├─ 按 (fetch_date, product_code) 去重
         ├─ 同一采集日多次运行 → 覆盖更新
         └─ 记录: 采集日期、产品、净值日期、净值、份额、市值、盈亏
```

**设计理念**：
- **采集日期 (fetch_date)** 是快照的主维度，每天每产品一条记录
- **净值日期 (nav_date)** 只记录净值来源日期（可能滞后 T+1）
- 同一天多次运行会覆盖（保持最新状态），不会产生重复数据

**目的**：汇总持仓情况，追踪盈亏变化

---

### 4. 资产汇总阶段 (`generate_portfolio_summary`)

```
读取 daily.csv:
    │
    ├─ 按净值日期聚合 (nav_date)
    │   ├─ 按product_code去重统计产品数
    │   └─ 写入 portfolio_by_nav_date.csv
    │       └─ total_value, total_pnl, product_count
    │
    └─ 按采集日期聚合 (fetched_at 的日期部分)
        ├─ 按product_code去重统计产品数
        ├─ 计算滞后产品数和最大滞后天数
        ├─ 计算真实日变动 (total_pnl_vs_prev_fetch)
        └─ 写入 portfolio_by_fetch_date.csv
            └─ total_value, total_pnl, total_pnl_vs_prev_fetch,
               product_count, stale_products, max_lag_days
```

**目的**：
- 按净值日期汇总：用于对账和核算（准确反映资产真实变化）
- 按采集日期汇总：用于日常看盘（快速查看当天总资产和真实日变动）

**幂等性**：采用全量重算覆盖写入，重复运行不会产生重复数据

**示例场景**：
```
假设今天2023-12-16运行系统：
- 产品A：获取到12-16净值（最新）
- 产品B：获取到12-15净值（滞后1天，如QDII）
- 产品C：获取到12-16净值（最新）

fetch_date汇总会显示：
- total_value: 三个产品的市值总和
- stale_products: 1 (只有产品B滞后)
- max_lag_days: 1 (最大滞后1天)
- total_pnl_vs_prev_fetch: 今天总市值 - 昨天总市值（真实日变动）
```

---

### 6. 日志输出阶段

```
输出汇总表格:
┌──────────┬──────┬────────────┬──────┬─────┬──────┬────────┬────────┐
│ 产品代码 │ 来源 │ 净值日期   │ 净值 │ CSV │ 快照 │  PNL   │  状态  │
├──────────┼──────┼────────────┼──────┼─────┼──────┼────────┼────────┤
│ 163406   │ fund │ 2023-12-14 │ 2.12 │  Y  │  Y   │ +10.50 │   OK   │
│ 000307   │ fund │ 2023-12-14 │ 1.56 │ SKIP│  N   │   -    │ EXIST  │
└──────────┴──────┴────────────┴──────┴─────┴──────┴────────┴────────┘

统计: 成功13/14, 快照13条
```

**目的**：一目了然看到所有产品的处理结果

---

## 📋 数据标准

### nav_record（净值记录）

所有适配器**必须**返回 `List[Dict]`，每个Dict包含：

```python
[{  # 注意：即使只有一条也必须用列表
    # === 必需字段（系统强制要求）===
    'PRODUCT_CODE': '163406',              # 产品代码
    'ISS_DATE': '2023-12-15',              # 净值日期 (YYYY-MM-DD格式)
    'NAV': '1.2345',                       # 单位净值 (str格式，可转float)
    'fetched_at': '2023-12-15T10:30:00',   # 采集时间 (ISO格式)
    
    # === 可选字段（扩展信息）===
    'TOT_NAV': '1.5678',                   # 累计净值
    'INCOME': '0.0012',                    # 万份收益
    'WEEK_CLIENTRATE': '0.0234'            # 7日年化/日增长率
}]
```

### products.json（产品配置）

```json
[
    {
        "id": "163406",                    // 必需：产品代码
        "name": "兴全合润混合",            // 必需：产品名称
        "source": "fund"                   // 必需：数据源 (cmbc/fund)
    }
]
```

### holdings.json（持仓配置 - 静态份额）

```json
[
    {
        "product_code": "163406",          // 必需：产品代码（必须在products.json中存在）
        "amount": 1000                     // 必需：持仓份额
    }
]
```

> **注意**：如果配置了交易流水（transactions.csv），系统会优先从流水计算份额和成本，holdings.json 作为回退。

### transactions.csv（交易流水）

交易流水文件用于自动计算持仓份额和成本，支持真实的浮动盈亏（unrealized_pnl）和收益率计算。

```csv
date,product_code,action,amount,shares,fee,nav,nav_date,note
2023-12-01,163406,BUY,100,50.25,0.12,1.9900,2023-11-30,定投建仓
2023-12-08,163406,BUY,100,49.75,0.12,2.0100,2023-12-07,每周定投
2023-12-15,163406,SELL,50,24.50,0.05,2.0408,2023-12-14,部分赎回
```

**字段说明**：

| 字段 | 必需 | 说明 |
|------|------|------|
| `date` | ✅ | 确认日期（份额入账日期，YYYY-MM-DD） |
| `product_code` | ✅ | 产品代码 |
| `action` | ✅ | 交易类型：`BUY`(买入) / `SELL`(卖出) |
| `amount` | ✅ | **确认金额**（扣除手续费后实际买入的金额） |
| `shares` | ✅ | **确认份额**（实际获得的份额） |
| `fee` | ⭕ | **手续费**（可选，默认0） |
| `nav` | ⭕ | **确认净值**（可选，可通过 amount/shares 反推） |
| `nav_date` | ⭕ | **净值日期**（可选，不知道可留空） |
| `note` | ⭕ | 备注（可选） |

**💡 历史交易不知道净值日期怎么办？**

如果你只知道确认金额和确认份额，可以这样填写：

```csv
date,product_code,action,amount,shares,fee,nav,nav_date,note
2024-03-15,017641,buy,99.88,62.50,0.12,,,历史定投（净值日期未知）
```

- **nav** 可以通过 `amount / shares = 99.88 / 62.50 = 1.5981` 反推
- **nav_date** 不知道就留空，不影响成本计算
- 成本计算只需要 `amount + fee`，与净值/净值日期无关

**💡 如何从支付宝/天天基金录入交易记录**：

以支付宝交易记录为例：
```
买入时间：2025-12-15
确认金额：99.88
确认份额：63.58
确认净值：1.5709
确认时间：2025-12-17
申购费：0.12元
```

对应 transactions.csv 填写：
```csv
2025-12-17,017641,buy,99.88,63.58,0.12,1.5709,2025-12-15,摩根标普500定投
```

**关键字段对应**：
- `date` = 确认时间（份额入账日）
- `amount` = 确认金额（扣费后）
- `shares` = 确认份额
- `fee` = 申购费
- `nav` = 确认净值
- `nav_date` = 买入时间（净值使用的日期）

**成本计算公式**：
```
实际成本 = 确认金额 + 手续费 = 99.88 + 0.12 = 100.00元
```
这样能准确反映你从账户划出了多少钱用于这笔定投。

---

**成本计算逻辑**：

- **买入**：`shares += 确认份额`，`cost += 确认金额 + 手续费`
- **卖出**：`shares -= 卖出份额`，`cost -= 卖出前cost × (卖出份额/卖出前份额)`（按比例减少成本）

**收益率计算**（需要完整交易流水）：
- **unrealized_pnl** = 当前市值 - 持仓成本 = `value - cost`
- **收益率** = (当前市值 - 持仓成本) / 持仓成本 × 100% = `unrealized_pnl / cost × 100%`

---

**🔥 全量交易流水模式（推荐）**：

如果你想要准确计算历史持有收益率，建议从第一笔定投开始完整记录交易流水：

1. **修改 holdings.json**：将所有产品的份额设为 0
   ```json
   [
       {"product_code": "163406", "amount": 0},
       {"product_code": "017641", "amount": 0}
   ]
   ```

2. **完整记录 transactions.csv**：从第一笔定投开始
   ```csv
   date,product_code,action,amount,shares,fee,nav,nav_date,note
   2024-01-08,017641,buy,99.88,67.32,0.12,1.4835,2024-01-05,首次定投
   2024-01-15,017641,buy,99.88,65.21,0.12,1.5315,2024-01-12,每周定投
   ...
   2025-12-17,017641,buy,99.88,63.58,0.12,1.5709,2025-12-15,每周定投
   ```

3. **系统自动计算**：
   - 累计份额 = 所有买入份额 - 所有卖出份额
   - 累计成本 = 所有买入(金额+手续费) - 卖出时按比例减少的成本
   - 浮动盈亏 = 当前市值 - 累计成本
   - 收益率 = 浮动盈亏 / 累计成本 × 100%

**使用场景**：

| 场景 | 数据源 | 说明 |
|------|--------|------|
| **全量交易流水（推荐）** | holdings.json=0 + 完整transactions.csv | 能计算准确的历史收益率 |
| 增量交易流水 | holdings.json基础份额 + 新增transactions.csv | 历史成本未知，只能计算增量 |
| 无交易流水 | 仅 holdings.json 的份额 | cost=0，无法计算收益率 |

---

## 🚀 使用方法

### 日常运行
```bash
# 采集所有产品净值并生成快照
python scripts/run_daily.py
```

**智能去重策略（按 fetch_date + product_code）**：

| 场景 | 处理方式 | 说明 |
|------|---------|------|
| 当天不存在 | ✅ 新增 | 新采集日的记录 |
| 当天存在但数据变化 | 🔄 覆盖更新 | 净值/份额/成本变化 |
| 当天存在且数据相同 | ⏭️ 跳过 | 重复数据，无需记录 |

**典型场景**：
```
时间轴：12-17下午 → 12-18早上(净值更新) → 12-18中午(份额确认)
---------------------------------------------------------------------
fetch_date:      12-17          12-18              12-18
nav_date:        12-16          12-17              12-17
(净值日期)
份额:            100            100                110
value:           200            210                231
处理:            写入           写入(新采集日)      覆盖更新(份额变)
```

**设计原理**：
- **fetch_date** 是主维度：每个采集日每个产品只有一条记录
- **nav_date** 只是属性：记录净值来源日期，不参与去重
- 同一天多次运行 → 覆盖同一条记录（保持最新状态）
- 第二天运行 → 新增一条记录（新的采集日）

**好处**：
- ✅ 每个采集日每产品一条记录（符合"日快照"直觉）
- ✅ 同一天多次运行自动覆盖（高效、干净）
- ✅ 份额/净值变化自动更新（不丢失）
- ✅ 历史数据可控（不会无限膨胀）

---

### 重建模式（修复历史 PnL 链）
```bash
# 从指定日期重建快照（修复链式 PnL）
python scripts/run_daily.py --rebuild-from 2025-12-01
```

**适用场景**：
- 📊 发现某天的 `pnl` 计算错误（因为 pnl 是链式计算，一个错误会传递）
- 📊 修改了历史份额配置，需要重新计算后续所有快照
- 📊 手动删除/修改了某些净值数据，需要重建快照

**重建逻辑**：
1. 删除 `fetch_date >= rebuild-from` 的所有快照记录
2. 保留 `rebuild-from` 之前的快照（作为 PnL 计算的基准）
3. 从现有净值CSV重新生成快照（按时间顺序，链式计算 PnL）

**重要提示**：
- ⚠️ 净值CSV不会被删除，可以复用
- ⚠️ 建议先备份 `daily.csv` 再执行重建

---

### 自测验证
```bash
# 运行核心功能自动化测试
python scripts/self_test.py

# 测试智能去重与重建功能
python scripts/test_force_rebuild.py
```

### 测试单个适配器
```bash
# 测试基金适配器
python src/adaptor/fund_client.py

# 测试民生银行适配器
python src/adaptor/cmbc_client.py
```

---

## 🔧 扩展新数据源

### 步骤1：创建适配器

```python
# src/adaptor/xxx_client.py
from datetime import datetime

def query_latest_nav(product_code, query_date, retry_num):
    """
    获取净值数据
    :return: List[Dict] 包含必需字段的净值记录列表
    """
    # 1. 调用API获取原始数据
    raw_data = fetch_from_xxx_api(product_code)
    
    # 2. 标准化格式
    nav_record = {
        'PRODUCT_CODE': product_code,
        'ISS_DATE': '2023-12-15',          # YYYY-MM-DD格式
        'NAV': '1.2345',                    # 字符串格式
        'fetched_at': datetime.now().isoformat(),
        # ... 其他字段
    }
    
    # 3. 返回列表（即使只有一条）
    return [nav_record]
```

### 步骤2：注册适配器

```python
# src/nav_collector.py
from adaptor import xxx_client

ADAPTOR_MAP = {
    'cmbc': cmbc_client,
    'fund': fund_client,
    'xxx': xxx_client,  # 添加新适配器
}
```

### 步骤3：配置产品

```json
// config/products.json
{
    "source": "xxx",      // 使用新适配器
    "name": "新产品",
    "id": "NEW001"
}
```

### 步骤4：测试

```bash
python src/adaptor/xxx_client.py
python scripts/run_daily.py
```

---

## 🎓 关键设计理念

### 1. 统一接口
- 所有适配器返回相同格式：`List[Dict]`
- 所有记录包含相同的必需字段
- 降低理解成本，易于扩展

### 2. 提前校验
- 配置错误：立即退出，不浪费时间
- 数据错误：跳过该产品，不影响其他
- 快速发现问题

### 3. 幂等安全
- 按 `ISS_DATE` 去重
- 重复运行不会重复写入
- 可以放心重跑

### 4. 错误隔离
- 单个产品失败不影响其他产品
- 每个产品独立处理
- 提高系统稳定性

### 5. 清晰日志
- 10行内展示所有产品状态
- 每个产品一行：代码、净值、状态、盈亏
- 快速定位问题

---

## 📊 输出文件说明

### 净值CSV (`data/nav/{code}_{name}.csv`)

```csv
product_code,product_name,ISS_DATE,NAV,TOT_NAV,INCOME,WEEK_CLIENTRATE,fetched_at
163406,兴全合润混合,2023-12-14,2.1234,3.4567,0,0.15,2023-12-15 10:30:00
163406,兴全合润混合,2023-12-15,2.1345,3.4678,0,0.16,2023-12-16 10:30:00
```

**说明**：
- 每个产品一个文件
- 按时间顺序追加
- 按 ISS_DATE 自动去重

### 快照CSV (`data/snapshots/daily.csv`)

```csv
fetch_date,product_code,product_name,category,nav_date,nav,shares,value,pnl,cost,unrealized_pnl,return_rate,fetched_at
2025-12-18,163406,兴全合润混合(LOF)A,fund,2025-12-17,2.0523,651.09,1336.23,0.00,1270.82,65.41,5.15%,2025-12-18 02:12:06
2025-12-18,017641,摩根标普500指数(QDII)A,fund,2025-12-16,1.5670,456.63,715.54,0.00,720.00,-4.46,-0.62%,2025-12-18 02:12:06
```

**设计理念**：
- **fetch_date** 是快照的"日期维度"，每天每产品一条记录
- **nav_date** 只记录净值来源日期（可能滞后 T+1）
- 同一采集日多次运行 → 覆盖同一条记录（保持最新状态）

**字段说明**：

| 字段 | 说明 |
|------|------|
| **fetch_date** | ⭐ 采集日期（主维度），与 product_code 构成唯一键 |
| **product_code** | 产品代码 |
| **product_name** | 产品名称 |
| **category** | 产品类型（fund=基金，bank=银行理财） |
| **nav_date** | 净值日期（净值来源日期，可能滞后） |
| **nav** | 净值（保持原始精度） |
| **shares** | 份额（保留两位小数） |
| **value** | 市值 = shares × nav |
| **pnl** | 相比上一采集日的市值变化 |
| **cost** | 持仓成本（从交易流水计算） |
| **unrealized_pnl** | 浮动盈亏 = value - cost |
| **return_rate** | ⭐ 收益率 = unrealized_pnl / cost × 100% |
| **fetched_at** | 采集时间（精确到秒） |

**数值精度**：
- 净值（nav）：保持原始精度（如 `1.5709`、`1.040966`）
- 份额/金额（shares/value/cost/pnl）：保留两位小数（如 `1234.56`）

---

### 🎯 资产汇总文件（核心功能）

系统自动基于 `daily.csv` 生成两种口径的投资组合汇总。

**🎯 汇总口径说明（重要）**：
- **主口径**：`fetch_date`（采集日期 = fetched_at 的日期部分）
  - 用户每天跑任务，关心"**今天采集到的完整资产快照**"
  - 同一采集日的所有产品（无论净值是否滞后）都汇总在一起
  - **避免因净值滞后导致资产被拆散到不同日期，看起来像少钱**
  
- **辅助口径**：`nav_date`（净值日期）
  - 仅用于标识该产品净值对应的**交易日**
  - 可能滞后 0~N 天（如QDII产品通常滞后1天）
  - 用于查看净值滞后情况和交易日口径分布

**✨ 健壮性保障**：
- ✅ **fetched_at 支持多格式**：兼容 `YYYY-MM-DD HH:MM:SS`, `YYYY-MM-DDTHH:MM:SS`, 带毫秒、带时区等格式
- ✅ **金额字段用 safe_decimal 防脏数据**：自动处理空值、`-`、带逗号数字（如 `12,345.67`），防止程序崩溃
- ✅ **异常数据自动跳过**：遇到无法解析的数据会记录警告并使用默认值，不影响整体任务

#### 2️⃣ 按净值日期汇总 (`portfolio_by_nav_date.csv`) - 【辅助视图】交易日口径

```csv
nav_date,total_value,total_pnl,total_cost,total_unrealized_pnl,product_count
2023-12-14,8532.50,0.00,8000.00,532.50,10
2023-12-15,8650.20,117.70,8000.00,650.20,10
```

**字段说明**：
- `nav_date`: 净值日期（产品实际净值的发布日期）
- `total_value`: 总市值 = sum(所有产品的 value)
- `total_pnl`: 总盈亏 = sum(所有产品的 pnl)
- `total_cost`: 总成本（从交易流水计算）
- `total_unrealized_pnl`: 总浮盈 = total_value - total_cost
- `product_count`: 参与汇总的产品数量

**使用场景（辅助）**：
- ✅ **查看净值滞后情况**：了解哪些产品净值不是最新的
- ✅ **交易日口径分析**：按净值实际发布日期统计
- ✅ **对账核算**：与基金公司对账时使用此口径
- ⚠️ **注意**：QDII等产品净值有T+1滞后，同一天可能包含不同日期的净值，**不适合作为"今日资产"视图**

#### 1️⃣ 按采集日期汇总 (`portfolio_by_fetch_date.csv`) - 【主视图】今日资产汇总

```csv
fetch_date,total_value,total_pnl,total_cost,total_unrealized_pnl,total_pnl_vs_prev_fetch,product_count,stale_products,max_lag_days
2023-12-15,8532.50,0.00,8000.00,532.50,0.00,10,3,1
2023-12-16,8650.20,117.70,8000.00,650.20,117.70,10,0,0
2023-12-17,8700.00,50.00,8000.00,700.00,49.80,10,2,1
```

**字段说明**：
- `fetch_date`: 采集日期（运行系统的日期）
- `total_value`: 总市值（基于当天能获取到的所有净值计算）
- `total_pnl`: 总盈亏（= sum(daily.csv 中的 pnl)）
- `total_cost`: 总成本（从交易流水计算，无流水为0）
- `total_unrealized_pnl`: 总浮盈（= total_value - total_cost）
- `total_pnl_vs_prev_fetch`: **真实日变动**（= 今天total_value - 昨天total_value）⭐
- `product_count`: 参与汇总的产品数量（按product_code去重）
- `stale_products`: 使用滞后净值的产品数量（nav_date < fetch_date）
- `max_lag_days`: 最大滞后天数（0表示所有产品都是最新净值）

**⚠️ 关于两种盈亏字段的区别（重要）**：

| 字段 | 含义 | 计算方式 | 适用场景 |
|------|------|---------|---------|
| `total_pnl` | 净值日差分拼盘 | sum(每个产品的pnl) | 了解产品级盈亏分布 |
| `total_pnl_vs_prev_fetch` | 真实日变动 | 今天总市值 - 昨天总市值 | **日常看盘**（今天赚/亏多少） |

**为什么需要两个PNL字段？**

1. **`total_pnl`** 是把每个产品的pnl加起来：
   - 优点：能看到每个产品的贡献
   - 缺点：当产品净值日期不同时（如QDII滞后），这个数字**不反映真实的日变动**
   - 例如：产品A用12-15净值（pnl=5），产品B用12-16净值（pnl=3），加起来=8，但这不是12-16的真实变化

2. **`total_pnl_vs_prev_fetch`** 是按采集日视角的真实变动：
   - 优点：**直接反映今天相比昨天的资产变化**，符合直觉
   - 缺点：无法拆分到每个产品
   - 例如：昨天总市值8532.50，今天8650.20，真实日变动=117.70

**最佳实践**：
- 📱 **日常看盘**：看 `total_pnl_vs_prev_fetch`（今天赚/亏了多少）
- 📊 **产品分析**：看 `total_pnl`（哪些产品贡献大）
- 🔍 **数据质量**：如果 `stale_products > 0`，说明 `total_pnl_vs_prev_fetch` 包含了部分非今日净值的影响

**使用场景（主要）**：
- ⭐ **日常看盘**：每天运行系统后，看 `total_pnl_vs_prev_fetch` 了解今天赚/亏多少
- ⭐ **今日资产**：无论净值是否滞后，都显示在同一天，符合"今天采集的完整资产"直觉
- ✅ **实时监控**：快速了解当前持仓总市值
- ✅ **数据质量**：通过 `stale_products` 和 `max_lag_days` 评估数据新鲜度
- ✅ **趋势分析**：`total_pnl_vs_prev_fetch` 连续几天可看涨跌趋势
- ⚠️ **注意**：如果 `stale_products > 0`，说明部分产品净值不是当天的，但仍然汇总在当天采集日

**两种口径对比**：

| 维度 | 按采集日期 (fetch_date) 【主】 | 按净值日期 (nav_date) 【辅助】 |
|------|------------------------------|------------------------------|
| **时间维度** | 系统运行日期 | 净值实际发布日期 |
| **适用场景** | ⭐ **日常看盘、今日资产** | 交易日分布、对账核算 |
| **资产完整性** | ✅ 完整（所有采集产品都在） | ❌ 分散（按净值日期拆散） |
| **直觉性** | ✅ 符合"今天有多少钱" | ❌ 滞后产品算到昨天 |
| **盈亏指标** | **total_pnl_vs_prev_fetch**（真实日变动）⭐ | total_pnl（产品级分布） |
| **数据质量指标** | stale_products, max_lag_days | 无 |

**最佳实践**：
- ⭐ **每日看盘（主要）**：查看 `portfolio_by_fetch_date.csv` 最新一行的 `total_value` 和 `total_pnl_vs_prev_fetch`
- 💰 **看今天赚亏**：`total_pnl_vs_prev_fetch` 正数=赚，负数=亏
- 📱 **看今日资产**：`total_value` 就是今天的总市值，无论净值是否滞后
- 🔍 **数据质量**：`stale_products` 显示有多少产品净值不是当天的，`max_lag_days` 显示最大滞后天数
- 📊 **交易日分析（辅助）**：使用 `portfolio_by_nav_date.csv` 了解净值滞后分布
- 📈 **周度趋势**：连续7天的 `total_pnl_vs_prev_fetch` 可看涨跌规律

---

## ⚠️ 重要约束

### 适配器约束
1. **返回类型**：必须是 `List[Dict]`，即使只有一条也用列表
2. **必需字段**：`PRODUCT_CODE`, `ISS_DATE`, `NAV`, `fetched_at`
3. **日期格式**：`ISS_DATE` 必须是 `YYYY-MM-DD`

### 配置约束
1. **products.json**：必须包含 `id`, `name`, `source`
2. **holdings.json**：`product_code` 必须存在于 products.json 中
3. **source**：必须在 `ADAPTOR_MAP` 中注册

### 运行约束
1. **配置错误**：立即退出（exit code 1）
2. **数据错误**：跳过该产品，继续处理其他
3. **重复数据**：自动去重，不会重复写入

---

## 🐛 故障排查

### 问题1：为什么没采集到数据？
1. 检查日志中的"状态"列
2. 如果显示 `ERR: xxx`，看错误信息
3. 检查对应适配器的 `query_latest_nav()` 函数

### 问题2：配置文件报错怎么办？
1. 检查是否缺少必需字段（id/name/source）
2. 检查 holdings.json 中的 product_code 是否在 products.json 中
3. 检查 source 是否有对应的适配器

### 问题3：数据格式错误？
1. 查看错误日志中的具体字段
2. 检查适配器的 `_normalize_nav_record()` 函数
3. 确保返回的数据包含所有必需字段

### 问题4：重复写入同一天数据？
1. 检查 `ISS_DATE` 格式是否正确（YYYY-MM-DD）
2. 查看 storage_csv.py 的去重逻辑
3. 运行 `python scripts/self_test.py` 验证

### 问题5：汇总文件生成失败？
1. 检查 daily.csv 是否存在且有数据
2. 查看日志中是否有字段解析警告
3. 验证 fetched_at 字段格式是否支持（支持多种格式）
4. 检查金额字段是否有异常值（系统会自动处理并警告）

### 问题6：发现当天快照数据有误？
1. **修正配置后重新采集**：
   ```bash
   # 修正 holdings.json 中的份额，然后直接重新运行
   python scripts/run_daily.py
   ```

2. **智能去重会自动处理**：
   - 如果 value 变化了（份额修正导致）→ 自动覆盖更新
   - 如果 value 没变 → 自动跳过（不产生重复数据）

3. **无需额外参数**：系统会智能判断是否需要更新

### 问题7：历史 PnL 链断了？
1. **场景**：修改了某天的份额配置，导致后续 PnL 不准确

2. **解决方案**：
   ```bash
   # 从该日期重建所有后续快照
   python scripts/run_daily.py --rebuild-from 2025-12-01
   ```

3. **重建原理**：
   - 删除指定日期及之后的所有快照
   - 保留之前的快照作为 PnL 基准
   - 从净值CSV按时间顺序重新生成快照
   - 链式计算 PnL（每天基于前一天的 value）

4. **安全提示**：
   - 重建前建议备份 `data/snapshots/daily.csv`
   - 净值CSV不会被删除，可安全复用
   - 可以多次重建直到 PnL 正确

---

## 🧪 自测说明

运行自测脚本验证系统可控性：

```bash
python scripts/self_test.py
```

**测试内容**：
1. ✅ **幂等性测试**：连续运行2次，验证不会重复写入
2. ✅ **配置校验**：故意写错配置，验证能正确报错退出
3. ✅ **字段完整性**：删除必需字段，验证能检测到
4. ✅ **Decimal脏数据测试**：验证空值、`-`、带逗号数字等异常数据不会导致崩溃
5. ✅ **fetched_at多格式测试**：验证5种不同时间格式都能正确聚合
6. ✅ **资产汇总功能**：验证滞后产品统计、PNL计算等核心逻辑

---

## 💡 最佳实践

### 开发新功能前
```bash
# 备份配置
cp config/products.json config/products.json.bak
cp config/holdings.json config/holdings.json.bak
```

### 每次改动后
```bash
# 运行自测验证
python scripts/self_test.py
```

### 调试问题时
```bash
# 单独测试适配器
python src/adaptor/fund_client.py

# 查看完整日志
python scripts/run_daily.py 2>&1 | tee debug.log
```

---

## 🏷️ 字段关键字对照表

系统中所有字段的统一命名规范，确保一致性。

### 核心字段

| 英文字段名 | 中文名称 | 说明 | 使用位置 |
|-----------|---------|------|---------|
| `product_code` | 产品代码 | 产品唯一标识（如基金代码） | daily.csv, transactions.csv, nav/*.csv |
| `product_name` | 产品名称 | 产品全称 | daily.csv, nav/*.csv |
| `category` | 产品分类 | fund=基金, bank=银行理财 | daily.csv, products.json |
| `nav` | 净值 | 单位净值 | daily.csv |
| `shares` | 份额 | 持有份额 | daily.csv, transactions.csv |
| `value` | 市值 | 当前市值 = shares × nav | daily.csv |
| `cost` | 成本 | 持仓成本（买入金额+手续费） | daily.csv |
| `unrealized_pnl` | 浮动盈亏 | 未实现盈亏 = value - cost | daily.csv |
| `return_rate` | 收益率 | 收益率 = unrealized_pnl / cost × 100% | daily.csv |
| `pnl` | 日变动 | 相比上一采集日的市值变化 | daily.csv |

### 日期时间字段

| 英文字段名 | 中文名称 | 格式 | 说明 |
|-----------|---------|------|------|
| `fetch_date` | 采集日期 | YYYY-MM-DD | 系统运行日期（daily.csv 主维度） |
| `fetched_at` | 采集时间 | YYYY-MM-DD HH:MM:SS.mmm | 精确采集时间（毫秒精度） |
| `nav_date` | 净值日期 | YYYY-MM-DD | 净值对应的交易日（可能滞后） |
| `date` | 确认日期 | YYYY-MM-DD | 交易确认日期（transactions.csv） |

### 交易相关字段

| 英文字段名 | 中文名称 | 说明 |
|-----------|---------|------|
| `action` | 交易类型 | BUY=买入, SELL=卖出 |
| `amount` | 确认金额 | 扣除手续费后的实际金额 |
| `fee` | 手续费 | 交易手续费 |
| `note` | 备注 | 交易备注 |

### 汇总字段

| 英文字段名 | 中文名称 | 说明 |
|-----------|---------|------|
| `total_value` | 总市值 | 所有产品市值之和 |
| `total_cost` | 总成本 | 所有产品成本之和 |
| `total_pnl` | 总盈亏 | 所有产品日变动之和 |
| `total_unrealized_pnl` | 总浮动盈亏 | 所有产品浮动盈亏之和 |
| `total_pnl_vs_prev_fetch` | 真实日变动 | 今日总市值 - 昨日总市值 |
| `product_count` | 产品数量 | 参与统计的产品数 |
| `stale_products` | 滞后产品数 | 净值日期早于采集日期的产品数 |
| `max_lag_days` | 最大滞后天数 | 净值最大滞后天数 |

### 配置文件字段映射

| 文件 | 字段 | 说明 |
|------|------|------|
| products.json | `product_code` | 产品代码（唯一标识） |
| products.json | `product_name` | 产品名称 |
| products.json | `source` | 数据源（fund/cmbc） |
| holdings.json | `product_code` | 产品代码（与 products.json 对应） |
| holdings.json | `product_name` | 产品名称（仅用于显示） |
| holdings.json | `amount` | 初始份额（回退用） |

### 净值文件字段（nav/*.csv）

| 英文字段名 | 中文名称 | 说明 |
|-----------|---------|------|
| `product_code` | 产品代码 | 产品唯一标识 |
| `product_name` | 产品名称 | 产品全称 |
| `nav_date` | 净值日期 | 净值对应的交易日 |
| `nav` | 单位净值 | 单位净值 |
| `total_nav` | 累计净值 | 累计净值 |
| `income` | 日收益 | 日收益率 |
| `weekly_rate` | 周收益率 | 周收益率 |
| `fetched_at` | 采集时间 | 数据采集时间 |

---

## 📞 快速参考

### 数据流向
```
config/*.json → validator → adaptor → storage_csv → snapshot → portfolio_summary → 日志输出
```

### 核心文件（只需看3个）
1. `src/nav_collector.py` - 主控流程
2. `src/validator.py` - 数据校验
3. `src/adaptor/fund_client.py` - 数据源示例

### 日志字段含义
- **CSV**: `Y`=新增, `SKIP`=已存在, `N`=失败
- **快照**: `Y`=已生成, `N`=未生成  
- **PNL**: 相比上次的盈亏（正=盈利，负=亏损）
- **状态**: `OK`=成功, `EXIST`=已存在, `ERR`=错误

---

**最后更新**: 2025-12-18  
**预计学习时间**: 30分钟掌握核心，1小时完全掌控
