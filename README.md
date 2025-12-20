# 财富中枢 - 净值采集系统

一个可控、易读、易维护的理财产品净值采集和快照系统。

---

## 📚 学习路线（30分钟掌控全局）

### 第一步：理解核心流程（10分钟）

**先看 `src/core/nav_collector.py`**

1. **`collect_and_store()`** - 主入口函数
```python
validate_configs()              # 校验配置文件
↓
process_single_product()        # 处理每个产品
↓
create_daily_snapshot()         # 生成产品快照（daily.csv）
↓
create_daily_balance_snapshot() # 生成账户余额快照（daily_balance.csv）
↓
输出汇总日志                    # 显示执行结果
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

**再看 `src/utils/validator.py`**

1. **`validate_nav_record()`** - 净值数据校验
   - 检查必需字段：`product_code`, `nav_date`, `nav`, `fetched_at`
   - 校验日期格式：必须是 `YYYY-MM-DD`
   - 校验NAV是数字
   - 校验product_code一致性

2. **持仓计算** - 基于交易流水
   - 持仓份额完全由 `transactions.csv` 计算
   - 无需静态配置文件

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
├── tx_cli.py                        # 【主入口】CLI工具：记账/理财/结算/采集
├── ui_app.py                        # 【UI入口】Streamlit本地UI
├── requirements.txt                 # 依赖包
│
├── config/                          # 配置文件
│   ├── products.json                # 产品配置（含市场类型、费率等）
│   ├── accounts.json                # 账户配置（含 account_groups）
│   ├── categories.json              # 分类配置（记账用）
│   └── nav_range.json               # 净值范围配置（自动更新）
│
├── docs/                            # 文档
│   └── field_spec.md                # 字段计算规则合同（对账级规范）
│
├── src/                             # 源代码（模块化组织）
│   ├── core/                        # 核心业务模块
│   │   ├── nav_collector.py         # 主控协调器
│   │   ├── snapshot.py              # 产品快照生成（daily.csv）
│   │   ├── daily_balance.py         # 账户余额快照（daily_balance.csv）
│   │   ├── holdings_calculator.py   # 持仓与成本计算器
│   │   ├── ledger_service.py        # 账本业务服务（UI/CLI共用）
│   │   ├── invest_service.py        # 理财业务服务（UI/CLI共用）
│   │   └── snapshot_service.py      # 快照服务（UI/CLI共用）
│   │
│   ├── data/                        # 数据层模块
│   │   ├── config_loader.py         # 配置加载
│   │   ├── data_store.py            # 数据存储（transactions/orders/ledger）
│   │   ├── storage_csv.py           # CSV存储
│   │   └── nav_reader.py            # 净值读取
│   │
│   ├── utils/                       # 工具模块
│   │   ├── validator.py             # 数据校验器
│   │   ├── trade_calendar.py        # 交易日历（中国节假日）
│   │   └── nav_range_manager.py     # 净值范围管理
│   │
│   ├── adaptor/                     # 数据源适配器
│   │   ├── cmbc_client.py           # 民生银行适配器
│   │   └── fund_client.py           # 东方财富基金适配器
│   │
│   └── backtest/                    # 策略回测引擎
│       ├── engine/                  # 回测核心
│       ├── strategies/              # 策略库
│       └── utils/                   # 回测工具
│
├── scripts/                         # 辅助脚本
│   ├── run_backtest.py              # 策略回测入口
│   ├── export_nav_history.py        # 净值历史导出
│   └── validate_transactions.py     # 交易流水校验
│
├── data/                            # 数据目录
│   ├── transactions.csv             # 交易流水
│   ├── orders.csv                   # 理财任务队列
│   ├── ledger.csv                   # 生活账本
│   ├── nav/                         # 净值CSV文件
│   └── snapshots/                   # 快照目录（daily.csv, daily_balance.csv）
│
└── README.md
```

---

## 🖥️ UI 使用说明

### 安装依赖

```bash
pip install -r requirements.txt
```

### 启动 UI

```bash
streamlit run ui_app.py
```

启动后浏览器会自动打开 `http://localhost:8501`

### 页面功能

#### 1. Dashboard（📊 资产总览）

- **资产指标**：显示总资产、总盈亏、基金总值等
- **账户余额表格**：可按组筛选（余利宝/稳利宝/基金）
- **产品持仓表格**：显示各产品的净值、份额、市值、盈亏
- **操作按钮**：
  - `一键日更`：采集净值 + 结算到期订单 + 生成快照
  - `仅生成快照`：不采集净值，只更新快照
  - `运行校验`：检查数据一致性

#### 2. 生活记账（📝）

- **支出**：选择账户、分类、输入金额和备注
- **收入**：选择账户、分类、输入金额和备注
- **转账**：选择转出/转入账户、输入金额
- **退款**：选择原支出记录，输入退款金额

#### 3. 理财录入（📈）

- **买入扣款**：选择产品、输入金额，自动计算手续费和确认日期
- **赎回发起**：选择产品、输入份额和持有天数，自动计算费率
- **补录历史**：补录已完成的买入/卖出/分红记录

#### 4. 订单结算（📋）

- **待结算订单表格**：显示所有 pending 状态的订单
- **结算按钮**：
  - `结算今日可结算`：只结算 confirm_date <= 今天 的订单
  - `结算全部到期`：结算所有到期订单
- **结算结果**：显示成功/跳过/失败的订单及原因

### 日常工作流程

```
早上：
1. 启动 UI：streamlit run ui_app.py
2. 点击「一键日更」自动完成：
   - 采集最新净值
   - 结算到期订单
   - 生成快照
3. 查看 Dashboard 确认资产状态

有消费时：
1. 进入「生活记账」页面
2. 选择支出/收入/转账
3. 填写信息并提交

有理财操作时：
1. 进入「理财录入」页面
2. 录入买入扣款或赎回发起
3. 等待确认日期到期后，点击「订单结算」结算
```

---

## 🎯 核心设计：扣款与份额确认分离

### 设计背景

在真实的定投场景中，**扣款时间**和**份额确认时间**通常不是同一天：

```
时间轴示例：
12/17 晚：系统统计一次（有净值，无新扣款）
12/18 早：新净值出来，统计一次（正常日变动）
12/18 晚：发生扣款100元 → cash=100, shares不变
12/19 早：新净值出来，统计一次（pnl_day 只由净值变化贡献，不受扣款影响）
12/19 上午：份额确认65份 → shares增加65, cash归零, pnl_day不跳变
```

### 核心概念

| 字段 | 说明 | 变化时机 |
|------|------|---------|
| `shares` | 已确认份额 | 只有份额确认(buy_confirm)或旧buy时才增加 |
| `cash` | 在途资金 | 扣款(buy_debit)时增加，确认(buy_confirm)时减少 |
| `cost` | 持仓成本 | 份额确认时增加（平均成本法） |
| `principal_total` | 累计投入本金 | 扣款时增加，卖出不减少 |
| `pnl_day` | 日变动 | **只由净值变化贡献**，公式：`prev_shares × (nav_today - nav_prev)` |

### 关键公式

```python
# 市值
value = shares × nav

# 总资产（含在途资金）
total_value = value + cash_in_transit

# 生命周期总盈亏（核心公式变更 v2）
total_pnl = total_value + total_redemption - principal_total
# 直观理解：投了 X 元，现在还有 Y 元在里面，已经拿回了 Z 元，总盈亏 = Y + Z - X
# 全赎回后：total_pnl = 0 + total_redemption - principal_total = 实际利润（不会反直觉）

# 日变动（核心：只反映净值涨跌，不受扣款/确认影响）
pnl_day = prev_day_shares × (nav_today - nav_prev)
```

---

## 📋 数据标准

### transactions.csv（交易流水）

交易流水记录所有交易事件，支持**扣款/确认分离模式**，同时兼容传统 buy 模式。

**CSV 格式（固定 10 列）**：
```csv
date,product_code,action,amount,shares,fee,nav,nav_date,order_id,note
```

**成本口径（统一规则）**：
```
cost = amount - fee  （净申购额入账）
```

**支持的交易类型 (action)**：

| action | 说明 | 必填字段 | 对持仓影响 |
|--------|------|---------|-----------|
| `buy_debit` | 扣款事件（钱已扣，份额未到） | date, product_code, amount, order_id | cash += (amount-fee), principal_total += amount |
| `buy_confirm` | 份额确认事件（份额到账） | date, product_code, shares, nav, nav_date, order_id | shares += shares, cost += matched_debit_net, cash -= matched |
| `buy` | 兼容模式（当天扣款+确认） | date, product_code, amount, shares, nav, nav_date | shares += shares, cost += (amount-fee), principal_total += amount |
| `sell` | 卖出（兼容模式） | date, product_code, shares, nav, nav_date | shares -= shares, cost 按比例减少 |
| `sell_confirm` | 赎回确认（自动结算生成） | date, product_code, shares, amount, fee, nav, nav_date, order_id | shares -= shares, cost 按比例减少, amount=到账净额 |
| `dividend` | 分红 | date, product_code, shares | shares += shares（成本不变） |

**order_id 规则**：
- `buy_debit`：必须提供 order_id（tx_cli 自动生成）
- `buy_confirm`：必须提供 order_id（与 debit 匹配）
- 其他 action：可选

**示例**：

```csv
date,product_code,action,amount,shares,fee,nav,nav_date,order_id,note
# 扣款/确认分离模式
2025-12-18,017641,buy_debit,100,,0.12,,,ORD20251218A1B2C3,每周定投扣款
2025-12-19,017641,buy_confirm,,63.58,,1.5709,2025-12-18,ORD20251218A1B2C3,份额到账
# 兼容模式（当天扣款+确认）
2025-12-18,163406,buy,99.88,61.37,0.12,1.6274,2025-12-17,,兴全合润混合(LOF)A
# 卖出（自动结算生成）
2025-12-19,163406,sell_confirm,1041.14,520.88,5.26,2.0190,2025-12-18,ORD20251219123456,赎回确认
# 分红
2025-12-15,020602,dividend,,5.85,,1.0951,2025-12-15,,易方达红利低波ETF联接A
```

---

### orders.csv（理财任务队列）

用户只需录入"扣款/赎回发起"，系统通过 `tx_cli settle` 自动生成确认事件写入 transactions.csv。

**CSV 格式（固定 14 列）**：
```csv
order_id,product_code,order_type,amount,fee,shares,requested_at,trade_date,nav_date,confirm_date,holding_days,sell_fee_rate,status,note
```

**字段说明**：

| 字段 | 说明 |
|------|------|
| `order_id` | 订单唯一标识（自动生成） |
| `order_type` | `buy_debit` 或 `redeem_request` |
| `amount` | 扣款金额（buy_debit）或到账净额（settle 后填充） |
| `fee` | 手续费（系统根据费率自动计算） |
| `shares` | 赎回份额（redeem_request）或确认份额（settle 后填充） |
| `trade_date` | 交易日期 |
| `nav_date` | 净值日期 |
| `confirm_date` | 确认日期（根据 T+N 计算） |
| `holding_days` | 持有天数（赎回时填写，用于确定费率） |
| `sell_fee_rate` | 赎回费率（根据持有天数查表确定） |
| `status` | `pending` / `done` / `cancelled` |

**工作流程**：
```
1. 买入扣款 -> buy_debit 写入 transactions + orders pending
2. tx_cli settle -> 读取净值 -> 生成 buy_confirm -> 标记 done
3. 赎回发起 -> orders pending（不写 transactions）
4. tx_cli settle -> 读取净值 -> 生成 sell_confirm -> 标记 done
```

---

### ledger.csv（生活账本）

记录日常收支，与理财交易分离。

**CSV 格式（固定 10 列）**：
```csv
event_time,entry_type,amount,category_l1,category_l2,account_from,account_to,discount,reimbursable,note
```

**entry_type 类型**：
- `expense`: 支出
- `income`: 收入
- `transfer`: 转账（account_from 和 account_to 都必填）

---

### daily.csv（日快照）- 完整字段定义

**字段顺序（固定）**：

| 字段名 | 中文名 | 说明 |
|--------|-------|------|
| `fetch_date` | 采集日期 | 运行当天 (YYYY-MM-DD) |
| `product_code` | 产品代码 | 产品唯一标识 |
| `product_name` | 产品名称 | 产品全称 |
| `category` | 分类 | fund/bank |
| `nav_date` | 净值日期 | 净值来源日期（可能滞后） |
| `nav` | 净值 | 单位净值 |
| `shares` | 份额 | **已确认份额**（精度≥4位小数） |
| `value` | 市值 | shares × nav |
| `pnl_day` | 日变动 | **只由净值涨跌贡献**：prev_shares × (nav - prev_nav) |
| `cost` | 成本 | 持仓成本（净申购额口径，卖出按比例扣减） |
| `unrealized_pnl` | 浮动盈亏 | value - cost |
| `return_rate` | 收益率 | unrealized_pnl / cost × 100% |
| `cash_in_transit` | 在途资金 | 扣款已发生但份额未确认的净额 |
| `total_value` | 总资产 | value + cash_in_transit |
| `principal_total` | 累计投入本金 | 按扣款累计，不因卖出回笼减少 |
| `total_redemption` | 累计赎回 | 卖出到账净额累计 |
| `total_pnl` | 生命周期总盈亏 | **total_value + total_redemption - principal_total** |
| `real_return` | 真实收益率 | total_pnl / principal_total × 100% |
| `fetched_at` | 采集时间 | 毫秒精度 |

**CSV 表头规则**：
- 第 1 行：字段名（机器读）
- 第 2 行：中文表头（人读）
- 第 3 行起：数据

**示例**：
```csv
fetch_date,product_code,product_name,category,nav_date,nav,shares,value,pnl_day,cost,unrealized_pnl,return_rate,cash_in_transit,total_value,principal_total,total_redemption,total_pnl,real_return,fetched_at
采集日期,产品代码,产品名称,分类,净值日期,净值,份额,市值,日变动,成本,浮动盈亏,收益率,在途资金,总资产,累计投入本金,累计赎回,总盈亏,真实收益率,采集时间
2025-12-20,163406,兴全合润混合A,fund,2025-12-19,2.0279,651.0900,1320.35,0.00,1267.76,52.58,4.15%,0.00,1320.35,3046.33,1855.76,129.78,4.26%,2025-12-20 15:28:05.023
```

---

### products.json（产品配置）

```json
[
    {
        "source": "fund",                      // 数据源 (cmbc/fund)
        "product_name": "兴全合润混合(LOF)A",  // 产品名称
        "product_code": "163406",              // 产品代码
        "type": "fund",                        // 产品类型 (fund/nav)
        "category": "fund",                    // 产品分类 (fund/bank)
        "market": "cn",                        // 市场类型 (cn/qdii/bank_nav/cash_like)
        "buy_fee_rate": 0.0012,                // 申购费率（0.12%）
        "sell_fee_tiers": [                    // 赎回费率阶梯（按持有天数）
            {"min_days": 0, "max_days": 7, "rate": 0.015},
            {"min_days": 7, "max_days": 30, "rate": 0.0075},
            {"min_days": 30, "max_days": 365, "rate": 0.005},
            {"min_days": 365, "max_days": 730, "rate": 0.0025},
            {"min_days": 730, "max_days": null, "rate": 0}
        ],
        "buy_confirm_offset": 1,               // 买入确认 T+N（默认 cn=1, qdii=2）
        "sell_confirm_offset": 1,              // 赎回确认 T+N
        "cutoff_time": "15:00"                 // 交易截止时间
    }
]
```

**sell_fee_tiers 赎回费率阶梯**：
| 持有天数 | 费率 | 说明 |
|---------|------|------|
| 0-7天 | 1.5% | 惩罚性费率 |
| 7-30天 | 0.75% | - |
| 30-365天 | 0.5% | - |
| 365-730天 | 0.25% | - |
| 730天以上 | 0% | 免赎回费 |

**market 类型与默认确认延迟**：
| market | 说明 | 默认 T+N |
|--------|------|---------|
| `cn` | 国内基金 | T+1 |
| `qdii` | QDII 基金 | T+2 |
| `bank_nav` | 银行理财 | T+1 |
| `cash_like` | 货币基金 | T+0 |

---

## 🚀 使用方法

### 日常运行
```bash
python tx_cli.py              # 交互模式（推荐）
python tx_cli.py collect      # 手动同步（一般不需要）
```

**自动同步特性**：
- 启动时自动采集净值并更新账户余额
- 记账/理财操作后自动后台同步（无输出）
- 同一天多次运行会覆盖（保持最新状态）
- 历史数据永远不会被删除

### CLI 工具（统一入口）

```bash
# 交互模式（推荐）
python tx_cli.py

# 快速子命令
python tx_cli.py settle      # 结算确认（处理到期订单）
python tx_cli.py list-ledger # 查看账本
python tx_cli.py list-orders # 查看订单
python tx_cli.py list-tx     # 查看交易
python tx_cli.py check       # 数据校验
```

**菜单结构**：
```
财富中枢 CLI
==================================================
  [1] 记账 (生活收支)
  [2] 理财 (买入/赎回)
  [3] 工具 (查看/校验)
  [0] 退出
```

**tx_cli.py 功能**：

**1. 记账模式**（写入 ledger.csv）
- 支持 expense/income/transfer 三种类型
- 从 accounts.json 选择账户
- 从 categories.json 选择分类
- 支持优惠金额、是否可报销标记
- 记账后自动后台同步数据

**2. 理财模式**
- **买入扣款**：录入扣款金额 -> 系统根据费率计算 fee -> 生成 order_id -> 计算确认日期 -> 写入 transactions + orders
- **赎回发起**：录入赎回份额 + 持有天数 -> 系统查表确定费率 -> 只写入 orders（不影响持仓）
- **结算确认**：扫描到期 pending 订单 -> 读取净值 -> 生成 buy_confirm/sell_confirm -> 写入 transactions
- **补录历史交易**：直接录入已完成的 buy/sell/dividend，不走 orders 流程（适用于历史数据补录）
- 每次操作后自动后台同步数据

**3. 工具模式**
- **查看账户余额**：显示所有账户余额（含货币基金收益）
- **查看账本/订单/交易**：查看历史记录
- **数据校验**：检查数据完整性

**4. 结算特性（健壮+幂等）**
- 缺净值时跳过不崩溃，保持 pending
- 重复执行不产生重复 confirm
- 详细日志输出处理结果

### 策略回测（可选）
```bash
# 列出可用产品和策略
python scripts/run_backtest.py --list
python scripts/run_backtest.py --strategies

# 运行回测
python scripts/run_backtest.py --product 163406 --strategy pure_sip
```

### 账户余额快照

系统会在启动时和每次操作后自动生成 `daily_balance.csv`，展示各账户余额：

| 账户类型 | 说明 |
|---------|------|
| `cash` | 现金账户（余利宝、银行卡等） |
| `fund_mapped` | 货币基金映射账户（小荷包 -> 000686），余额=市值+当日收益 |
| `product_sub` | 产品子账户（稳利宝各子账户） |
| `fund_total` | 基金账户汇总（不含货币基金） |
| `summary` | 汇总行（稳利宝合计、余利宝合计、基金合计） |

---

## 📊 资产汇总文件

### portfolio_by_fetch_date.csv - 主视图

按采集日期汇总，显示"今天的总资产"。

| 字段 | 说明 |
|------|------|
| `fetch_date` | 采集日期 |
| `total_value` | 总资产 |
| `total_pnl` | 总盈亏 |
| `pnl_day` | 日变动合计 |
| `cost` | 总成本 |
| `unrealized_pnl` | 总浮动盈亏 |
| `principal_total` | 累计投入本金 |
| `pnl_vs_prev` | 相对前日变动 |
| `product_count` | 产品数量 |
| `stale_products` | 滞后产品数 |
| `max_lag_days` | 最大滞后天数 |

### portfolio_by_category.csv - 分类视图

每个采集日输出三行：基金汇总、银行理财汇总、总资产汇总。

---

## 🔧 扩展新数据源

### 步骤1：创建适配器

```python
# src/adaptor/xxx_client.py
def query_latest_nav(product_code, query_date, retry_num):
    return [{
        'product_code': product_code,
        'nav_date': '2023-12-15',
        'nav': '1.2345',
        'fetched_at': datetime.now().isoformat(),
    }]
```

### 步骤2：注册适配器

```python
# src/nav_collector.py
ADAPTOR_MAP = {
    'cmbc': cmbc_client,
    'fund': fund_client,
    'xxx': xxx_client,
}
```

### 步骤3：配置产品

```json
{
    "source": "xxx",
    "product_name": "新产品",
    "product_code": "NEW001",
    "category": "fund"
}
```

---

## 📦 安装依赖

```bash
pip install -r requirements.txt
```

**主要依赖**：
- `chinese-calendar`: 中国节假日判断库（用于交易日计算）

**交易日规则**：
- 使用 `chinese_calendar.is_workday()` 判断交易日（周一~周五，排除法定节假日）
- 自动获取节假日数据，无需手动维护节假日配置
- 支持 T+N 确认日期计算（A股 T+1，QDII T+2）

---

## 🎓 关键设计理念

1. **成本口径统一**：`cost = amount - fee`（净申购额），全系统一致
2. **pnl_day 只反映净值变化**：`prev_shares × (nav - prev_nav)`
3. **同日覆盖**：同一 (fetch_date, product_code) 只保留一条，保持数据干净
4. **原子写入**：daily.csv 重建使用临时文件 + os.replace，防止写坏
5. **字段零冗余**：每个字段都有明确用途，不引入无用字段
6. **交易日自动判断**：使用 `chinese-calendar` 开源库，无需维护节假日配置

---

## 🏷️ 字段命名规范

系统使用统一的字段命名（snake_case，小写），同一语义只有一个字段名：

### 日期字段
| 字段 | 说明 | 示例 |
|------|------|------|
| `date` | 交易日期（transactions.csv） | 2025-12-18 |
| `fetch_date` | 采集日期（运行当天） | 2025-12-19 |
| `nav_date` | 净值日期（可能滞后 T+1） | 2025-12-18 |
| `fetched_at` | 采集时间（毫秒精度） | 2025-12-19 12:00:00.123 |

### 金额/份额字段
| 字段 | 说明 | 注意 |
|------|------|------|
| `amount` | 交易金额 | 买入金额、卖出到账金额 |
| `shares` | 已确认份额 | 精度≥4位小数，不使用 units |
| `fee` | 手续费 | - |
| `nav` | 单位净值 | - |
| `value` | 持仓市值（shares × nav） | 不使用 market_value |
| `cost` | 持仓成本（净申购额累计） | 不使用 total_cost |
| `cash_in_transit` | 在途资金 | 扣款已发生但份额未确认的净额 |
| `total_value` | 总资产（value + cash_in_transit） | - |
| `principal_total` | 累计投入本金 | 扣款时增加，卖出不减少 |

### 盈亏字段
| 字段 | 说明 | 公式 |
|------|------|------|
| `pnl_day` | 日变动 | prev_shares × (nav_today - nav_prev)；**不使用 pnl** |
| `unrealized_pnl` | 浮动盈亏 | value - cost |
| `total_redemption` | 累计赎回金额 | Σ sell_confirm.amount（到账净额） |
| `total_pnl` | **生命周期总盈亏** | **total_value + total_redemption - principal_total** |
| `global_pnl` | 全局盈亏（组合汇总层） | 根据 global_mode 计算，见下 |
| `return_rate` | 持仓收益率 | unrealized_pnl / cost × 100% |
| `real_return` | 真实收益率 | total_pnl / principal_total × 100% |

### global_mode 说明（防止双计）

| 模式 | 条件 | global_value 公式 |
|------|------|------------------|
| `no_cash_bucket` | 无现金桶产品（当前默认） | Σ total_value + Σ total_redemption |
| `cash_bucket` | 存在 is_cash_bucket=true 的产品 | Σ total_value（赎回已体现在现金桶中） |

**global_pnl** = global_value - Σ principal_total

> **重要变更 (v2)**：`total_pnl` 现在是**生命周期口径**，包含已赎回收益。
> 全赎回后 total_pnl = total_redemption - principal_total = 实际利润（不会反直觉变成负数）。
> 详细字段规范请参阅 [docs/field_spec.md](docs/field_spec.md)

### 标识字段
| 字段 | 说明 | 规则 |
|------|------|------|
| `product_code` | 产品代码 | 唯一标识 |
| `product_name` | 产品名称 | - |
| `category` | 产品分类 | fund / bank |
| `action` | 交易类型 | buy_debit / buy_confirm / buy / sell / dividend |
| `order_id` | 订单号 | 关联 debit 和 confirm |
| `note` | 备注 | - |

---

**最后更新**: 2025-12-20 (v2.1 - 自动同步版)  
**预计学习时间**: 30分钟掌握核心，1小时完全掌控
