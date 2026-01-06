# 财富中枢 - 净值采集系统

一个可控、易读、易维护的理财产品净值采集和快照系统。支持场外基金、场内ETF/LOF、银行理财等多种产品类型，提供实时行情采集、定投计划、溢价刹车等高级功能。

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
create_daily_snapshot()         # 生成产品快照（daily_snapshot表）
↓
输出汇总日志                    # 显示执行结果
```

2. **`process_single_product()`** - 单产品处理
```python
fetch_and_validate_nav()    # 获取并校验净值
↓
save_nav_record()           # 存储到数据库
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
   - 持仓份额完全由 `transactions` 表计算
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
├── config/                          # 配置文件（仅保留数据库连接配置）
│   └── db_config.json               # 数据库连接配置
│
├── scripts/                         # SQL脚本和调度器
│   ├── sql/                         # SQL脚本目录
│   │   ├── initsql/                 # 初始化脚本
│   │   │   └── init_database.sql    # 数据库建表脚本
│   │   └── update/                   # 升级脚本
│   │       ├── migrate_to_exchange_v1.sql  # 场内交易升级脚本
│   │       └── import_existing_data.sql    # 初始数据导入脚本
│
├── docs/                            # 文档
│   └── field_spec.md                # 字段计算规则合同（对账级规范）
│
├── src/                             # 源代码（模块化组织）
│   ├── core/                        # 核心业务模块
│   │   ├── nav_collector.py         # 主控协调器
│   │   ├── snapshot.py              # 产品快照生成（daily_snapshot表）
│   │   ├── holdings_calculator.py   # 持仓与成本计算器（场外）
│   │   ├── exchange_holdings_calculator.py  # 场内持仓计算器
│   │   ├── ledger_service.py        # 账本业务服务（UI/CLI共用）
│   │   ├── invest_service.py        # 理财业务服务（UI/CLI共用）
│   │   ├── snapshot_service.py      # 快照服务（UI/CLI共用）
│   │   ├── market_quote_service.py  # 行情服务（实时/日K/QDII溢价）
│   │   ├── premium_brake.py         # 溢价刹车逻辑
│   │   ├── pending_buy_service.py   # 待买入池服务
│   │   └── scheduler_service.py    # 调度器服务（APScheduler）
│   │
│   ├── data/                        # 数据层模块
│   │   ├── config_loader.py         # 配置加载（数据库驱动）
│   │   ├── product_service.py       # 产品配置服务（数据库）
│   │   ├── account_service.py       # 账户配置服务（数据库）
│   │   ├── category_service.py      # 分类配置服务（数据库）
│   │   ├── data_store.py            # 数据存储（transactions/orders/ledger）
│   │   ├── db_connector.py          # 数据库连接
│   │   └── nav_reader.py            # 净值读取
│   │
│   ├── utils/                       # 工具模块
│   │   ├── validator.py             # 数据校验器
│   │   ├── trade_calendar.py        # 交易日历（中国节假日）
│   │   └── nav_range_manager.py     # 净值范围管理
│   │
│   ├── adaptor/                     # 数据源适配器
│   │   ├── cmbc_client.py           # 民生银行适配器
│   │   ├── fund_client.py           # 东方财富基金适配器
│   │   └── akshare_client.py        # AKShare适配器（场内行情）
│   │
│   └── backtest/                    # 策略回测引擎
│       ├── engine/                  # 回测核心
│       ├── strategies/              # 策略库
│       └── utils/                   # 回测工具
│
├── scripts/                         # 辅助脚本
│   └── run_backtest.py              # 策略回测入口
│
├── data/                            # 数据目录（可选，用于数据导出）
│   └── nav/                         # 净值历史数据（可选）
│
└── README.md
```

---

## 🖥️ UI 使用说明

### 数据库初始化

1. **创建数据库**：
```bash
mysql -u root -p
CREATE DATABASE dca CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

2. **执行初始化脚本**：
```bash
mysql -u root -p dca < scripts/sql/initsql/init_database.sql
mysql -u root -p dca < scripts/sql/update/import_existing_data.sql
```

3. **配置数据库连接**：
编辑 `config/db_config.json`，填入数据库连接信息。

### 安装依赖

```bash
pip install -r requirements.txt
```

**主要依赖**：
- `streamlit`: Web UI框架
- `pymysql`: MySQL数据库连接
- `akshare`: 场内行情数据源
- `APScheduler`: 任务调度器
- `chinese-calendar`: 中国节假日判断库

### 启动 UI

```bash
streamlit run ui_app.py
```

启动后浏览器会自动打开 `http://localhost:8501`

**注意**：调度器已集成在 UI 中，启动 UI 时会自动启动调度器。调度任务在 `job_config` 表中配置：
- `rt_quote_1m`: 场内实时行情（交易时间每分钟）
- `otc_update_0800/1400/2200`: 场外净值更新（每日3次）

### 页面功能

#### 1. Dashboard（📊 资产总览）

- **资产指标**：显示总资产、总盈亏、基金总值等
- **账户余额表格**：可按组筛选（余利宝/稳利宝/基金）
- **产品持仓表格**：显示各产品的净值、份额、市值、盈亏
- **产品行情**：
  - 支持场内/场外选择（默认场内）
  - 场内产品：显示实时行情、日K线、QDII溢价率、买入建议
  - 场外产品：显示净值行情、历史净值走势
  - 支持手动刷新行情数据
- **操作按钮**：
  - `一键日更`：采集净值 + 结算到期订单 + 生成快照
  - `运行校验`：检查数据一致性

#### 2. 资产详情（💼）

- **资产总览**：显示总资产、浮动盈亏、总盈亏、基金总值、数据日期等指标
- **账户余额表格**：
  - 可按组筛选（余利宝/稳利宝/基金）
  - **基金账户重构**：
    - **场外基金账户**：原基金账户，统计场外基金（channel=OTC）市值和收益
    - **场内基金账户**：新增账户，统计场内基金（channel=EXCHANGE）市值和收益
    - **基金(合计)账户**：场内+场外基金合计
  - 显示账户名称、类型、余额、产品市值、差异、昨日收益、持有收益、累计收益
- **产品持仓表格**：
  - 支持场内/场外选择（默认场内）
  - 根据选择筛选显示对应的产品持仓
  - 显示产品名称、净值日期、净值、份额、市值、总盈亏、收益率

#### 3. 生活记账（📝）

- **支出**：选择账户、分类、输入金额和备注
- **收入**：选择账户、分类、输入金额和备注
- **转账**：选择转出/转入账户、输入金额
- **退款**：选择原支出记录，输入退款金额
- **最近记录**：可编辑历史记录，支持修改任意历史记录

**余额对账功能**：
- 每条记录显示「余额」和「父账户余额」两列，可用于与银行/APP对账
- 余额采用**动态计算**，不存储在数据库中
- 修改任何历史记录后，后续所有记录的余额会**自动重算**
- 无需担心修改历史导致余额不一致问题

#### 4. 理财录入（📈）

- **交易类型选择**：支持场内/场外选择（默认场内）
  - **场内模式**：场内成交录入
    - 选择产品（仅显示场内ETF/LOF）
    - 选择资金来源账户
    - 输入成交金额、份额、手续费（自动计算，可覆盖）
    - 系统自动扣减等待池和现金池，更新持仓，刷新建议
  - **场外模式**：买入扣款/赎回发起
    - **买入扣款**：选择产品、输入金额，自动计算手续费和确认日期
    - **赎回发起**：选择产品、输入份额和持有天数，自动计算费率
- **补录历史**：补录已完成的买入/卖出/分红记录
- **产品选择**：所有产品下拉框默认显示场内产品（场内优先排序）

#### 5. 订单结算（📋）

- **待结算订单表格**：显示所有 pending 状态的订单
- **结算按钮**：
  - `结算今日可结算`：只结算 confirm_date <= 今天 的订单
  - `结算全部到期`：结算所有到期订单
- **结算结果**：显示成功/跳过/失败的订单及原因

#### 6. 产品管理（📦）

- **产品列表**：查看所有产品（场外/场内）
- **添加产品**：支持场外基金、场内ETF/LOF、银行理财等
- **编辑产品**：修改费率、确认延迟、交易截止时间等
- **产品字段**：
  - `code`: 产品代码（如 163406）
  - `channel`: 渠道（EXCHANGE/OTC）
  - `market`: 市场（SH/SZ/NA）
  - `asset_type`: 资产类型（ETF/LOF/FUND/MMF/BANK_WM_NAV）
  - `is_qdii`: 是否QDII（用于溢价刹车）

#### 7. 账户管理（💳）

- **账户列表**：查看所有账户
- **添加账户**：支持现金账户、产品子账户、货币基金映射账户等
- **账户层级**：支持父子账户关系（如稳利宝各子账户）

#### 8. 资金池规则（💰）

- **规则列表**：查看资金池分配规则
- **添加规则**：配置从来源账户到目标产品的分配比例
- **规则字段**：
  - `from_account_id`: 来源账户（如余利宝理财金）
  - `to_product_id`: 目标产品
  - `ratio`: 分配比例（如 0.35 表示 35%）
  - `min_amount`: 最小分配金额
  - `round_step`: 取整粒度

#### 9. 策略实验室（🔬）

- **策略管理**：创建、编辑、删除策略配置
- **运行回测**：
  - 支持场内/场外选择（默认场内）
  - 根据选择筛选产品列表
  - 选择策略、配置参数、设置回测日期范围
  - 显示产品行情数据范围
  - 支持初始资金、每月定投金额、定投日期、最小交易金额等配置
- **回测结果**：查看历史回测记录，支持删除和详情查看
- **参数对比**：对比同一策略不同参数组合的回测结果

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

### transactions 表（交易流水）

交易流水记录所有交易事件，支持**扣款/确认分离模式**，同时兼容传统 buy 模式。

**数据库表字段（固定 13 列）**：
```
id, product_id, date, product_code, action, amount, shares, fee, nav, nav_date, order_id, note, created_at
```

**注意**：实际数据库表包含 `product_id` 字段（外键关联 products 表），字段顺序与文档中略有不同。

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
| `dividend` | 分红（红利再投） | date, product_code, shares, nav, nav_date | shares += shares（成本不变） |

**order_id 规则**：
- `buy_debit`：必须提供 order_id（tx_cli 自动生成）
- `buy_confirm`：必须提供 order_id（与 debit 匹配）
- 其他 action：可选

**示例数据**：

| date | product_code | action | amount | shares | fee | nav | nav_date | order_id | note |
|------|--------------|--------|--------|--------|-----|-----|----------|----------|------|
| 2025-12-18 | 017641 | buy_debit | 100 | NULL | 0.12 | NULL | NULL | ORD20251218A1B2C3 | 每周定投扣款 |
| 2025-12-19 | 017641 | buy_confirm | NULL | 63.58 | 0 | 1.5709 | 2025-12-18 | ORD20251218A1B2C3 | 份额到账 |
| 2025-12-18 | 163406 | buy | 99.88 | 61.37 | 0.12 | 1.6274 | 2025-12-17 | NULL | 兴全合润混合(LOF)A |
| 2025-12-19 | 163406 | sell_confirm | 1041.14 | 520.88 | 5.26 | 2.0190 | 2025-12-18 | ORD20251219123456 | 赎回确认 |
| 2025-12-15 | 020602 | dividend | NULL | 5.85 | NULL | 1.0951 | 2025-12-15 | NULL | 易方达红利低波ETF联接A |

---

### orders 表（理财任务队列）

用户只需录入"扣款/赎回发起"，系统通过 `tx_cli settle` 自动生成确认事件写入 `transactions` 表。

**数据库表字段（固定 17 列）**：
```
id, order_id, product_id, product_code, order_type, amount, fee, shares, requested_at, trade_date, nav_date, confirm_date, holding_days, sell_fee_rate, status, note, created_at
```

**注意**：`orders` 表没有 `updated_at` 字段。

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
1. 买入扣款 -> buy_debit 写入 transactions表 + orders表 pending
2. tx_cli settle -> 读取净值 -> 生成 buy_confirm -> 标记 done
3. 赎回发起 -> orders表 pending（不写 transactions表）
4. tx_cli settle -> 读取净值 -> 生成 sell_confirm -> 标记 done
```

---

### ledger 表（生活账本）

记录日常收支，与理财交易分离。

**数据库表字段（固定 11 列）**：
```
id, event_time, entry_type, amount, category_l1, category_l2, account_from, account_to, discount, reimbursable, note, created_at
```

**entry_type 类型**：
- `expense`: 支出
- `income`: 收入
- `transfer`: 转账（account_from 和 account_to 都必填）

---

### daily_snapshot 表（日快照）- 完整字段定义

**数据库表字段（固定 22 列）**：

| 字段名 | 中文名 | 说明 |
|--------|-------|------|
| `id` | 主键 | 自增ID |
| `fetch_date` | 采集日期 | 运行当天 (YYYY-MM-DD) |
| `product_id` | 产品ID | 外键关联 products表 |
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
| `data_status` | 数据状态 | ok/carried_forward/missing/holiday（用于缺数据兜底） |
| `fetched_at` | 采集时间 | 毫秒精度 |
| `created_at` | 创建时间 | 数据库自动生成 |

---

### 产品配置（数据库表：products）

**重要变更**：所有产品配置已迁移到 MySQL 数据库的 `products` 表中，不再使用 JSON 文件。

**核心字段**：
- `code`: 产品代码（如 163406）
- `channel`: 渠道（EXCHANGE/OTC）
- `market`: 市场（SH/SZ/NA）
- `asset_type`: 资产类型（ETF/LOF/FUND/MMF/BANK_WM_NAV/BANK_WM_BOX）
- `is_qdii`: 是否QDII（用于溢价刹车）
- `buy_fee_rate`: 申购费率
- `sell_fee_rate`: 赎回费率（场内使用固定费率）
- `buy_confirm_offset`: 买入确认延迟交易日数
- `sell_confirm_offset`: 赎回确认延迟交易日数
- `cutoff_time`: 交易截止时间

**场外产品**（channel=OTC）：
- 支持场外基金、银行理财、货币基金
- 赎回费率通过 `sell_fee_rate` 字段配置（或通过持有天数查表）

**场内产品**（channel=EXCHANGE）：
- 支持ETF、LOF
- `market` 必须为 SH 或 SZ
- 实时行情通过 AKShare 采集
- QDII ETF 支持溢价率监控和溢价刹车

**163406 分离规则**：
- 163406 场外基金（channel=OTC, market=NA）和 163406 LOF（channel=EXCHANGE, market=SH）是两个独立产品
- 通过 `(code, channel, market)` 唯一键区分

---

## 📊 数据库表总览

系统共包含 **29 张数据库表**，分为以下几类：

### 核心业务表（5张）
1. **transactions** - 交易流水表（所有投资交易记录）
2. **orders** - 理财任务队列表（待结算订单）
3. **ledger** - 生活账本表（日常收支记录）
4. **daily_snapshot** - 产品日快照表（每日资产快照）
5. **nav** - 净值历史表（所有产品历史净值）

**注意**：账户余额现在直接从 `accounts` 表的 `balance` 字段读取（实时数据），不再使用 `daily_balance` 快照表。

### 配置表（6张）
7. **products** - 产品配置表（产品基本信息、费率等）
8. **accounts** - 账户配置表（账户基本信息）
9. **account_groups** - 账户组配置表（账户组关系）
10. **categories** - 分类配置表（生活记账分类）
11. **account_pool_rules** - 资金池分配规则表（资金池分配比例）
12. **job_config** - 任务调度配置表（APScheduler任务配置）

### 场内交易表（4张）
13. **market_quote_rt** - 场内实时行情表（交易时间每分钟采集）
14. **market_bar_d** - 场内日K线表（历史K线数据）
15. **qdii_premium_rt** - QDII溢价率表（QDII ETF溢价率）
16. **trade_fills** - 场内成交流水表（场内交易成交记录）

### 等待池表（1张）
17. **pending_buy_pool** - 待买入池表（溢价刹车扣留的资金）

### 策略实验室表（4张）
18. **strategy_config** - 策略配置表（策略参数配置）
19. **backtest_summary** - 回测汇总表（回测结果汇总）
20. **backtest_daily** - 回测每日数据表（回测每日数据）
21. **backtest_trades** - 回测成交表（回测逐笔成交记录）

### 生产建议层表（Advisor，5张）
24. **product_strategy_bind** - 产品策略绑定表（支持多策略组合）
25. **strategy_state** - 策略状态表（存储策略运行时状态）
26. **indicator_daily** - 日更慢指标表（分位排名、回撤、均线等）
27. **advisor_suggestion** - 建议输出表（存储每次生成的建议）
28. **budget_trace** - 预算追踪审计日志表（记录预算分配与延期情况）

### 辅助表（1张）
29. **product_nav_range** - 产品净值范围表（产品净值日期范围统计）

**详细字段定义请参阅 [docs/field_spec.md](docs/field_spec.md)**

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

**1. 记账模式**（写入 ledger 表）
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

策略实验室（Strategy Lab）是一个独立的回测引擎模块，用于验证策略参数，不侵入生产执行模块。

**数据库表**：
- `strategy_config`：策略参数配置
- `backtest_summary`：回测汇总结果
- `backtest_daily`：每日回测数据
- `backtest_trades`：逐笔成交记录

**UI 使用**：
- 进入「策略实验室」页面
- 选择「运行回测」tab
- **交易类型选择**：支持场内/场外选择（默认场内）
  - 根据选择自动筛选对应的产品列表
  - 显示产品行情数据范围（场内/场外）
- 选择策略、配置参数、设置回测日期范围
- 配置初始资金、每月定投金额、定投日期、最小交易金额等
- 点击「开始回测」执行回测
- 在「回测结果」tab 查看历史回测记录
- 在「参数对比」tab 对比不同参数组合的回测结果

**使用方法**（CLI）：
```bash
# 列出可用产品和策略
python scripts/run_backtest.py --list
python scripts/run_backtest.py --strategies

# 运行回测
python scripts/run_backtest.py --product 163406 --strategy pure_sip
```

**策略实现**：
- `SimpleStrategy`：固定周频/月频买入
- `DrawdownStrategy`：回撤触发加仓
- `PercentileStrategy`：滚动N日分位判断
- `ProfitRecycleStrategy`：利润回收策略（动态锁定池、深跌释放、高估收割）

**注意事项**：
- 所有结果写入数据库表，不输出CSV
- 每个 Decision 必须包含 reasons（可解释性）
- 所有策略参数存储在 `strategy_config` 表
- 回测独立，不侵入生产执行模块，不触发下单

### 账户余额

账户余额现在直接从 `accounts` 表的 `balance` 字段读取（实时数据），每次记账操作后自动更新。

**账户类型**：
| 账户类型 | 说明 |
|---------|------|
| `CASH` | 现金账户（余利宝、银行卡等） |
| `FUND_MAPPED` | 货币基金映射账户（小荷包 -> 000686），余额=市值+当日收益 |
| `PRODUCT_SUB` | 产品子账户（稳利宝各子账户） |
| `FUND_TOTAL` | 基金账户汇总（不含货币基金） |

**收益字段**（针对基金、余利宝生活费、稳利宝）：
- `yesterday_pnl`：昨日收益（前一天的 pnl_day）
- `unrealized_pnl`：持有收益（浮动盈亏）
- `total_pnl`：累计收益（生命周期总盈亏）

**注意**：这些收益字段在 UI 中动态计算显示，从 `daily_snapshot` 表读取产品持仓数据。

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

## 💡 生产建议层（Advisor）

### 架构概述

生产建议层（Advisor）是一个**信号评估系统**，不参与自动下单，只输出买入/持有/等待建议及详细原因。系统采用**三层策略组合架构**（VETO/TRIGGER/SCORE），支持每个产品绑定多个策略。

### 核心特性

1. **不自动下单**：只输出建议/理由，人工执行
2. **日线指标用"到昨天为止"的日K计算**，盘中只用实时价 `last_price` 代入判断
3. **多策略组合**：每个产品可以绑定多个策略，采用"三层结构：否决优先 + 多触发 + 强度评估"
4. **ETF溢价刹车**：QDII产品必须执行溢价刹车（≤1%正常买，1%-2%半买，>2%全部等待池）
5. **数字自洽**：UI上的每个数字必须自洽，不能出现"建议金额=0但比例=100%"等矛盾

### 三层策略架构

#### VETO层（否决层）
- 任一策略命中 → 直接返回 WAIT/HOLD/SKIP
- 例如：分位门控（pct_rank > 70% 则拒绝买入）

#### TRIGGER层（触发层）
- 任一策略命中 → 进入SCORE层
- 例如：分位策略（根据 pct_rank 命中 tiers 档位）、回撤策略（drawdown ≥ 4%）

#### SCORE层（强度层）
- 决定买入金额档位
- 例如：4%定投策略（根据回撤档位决定买入金额）

### 统一输出模型（AdvisorViewModel）

所有建议通过 `AdvisorViewModel` 统一输出，确保UI数字自洽：

**行情状态**：
- `is_trade_day` / `is_trade_time`（分开判断）
- `last_price` / `prev_close` / `pct_change`
- `iopv` / `premium_rate`（QDII可用时）

**慢指标（昨日）**：
- `pct_rank`（0-1，如0.72表示72%分位）
- `peak_close`、`drawdown_from_peak`
- `ma20`、`ma60`、`price_over_ma20`、`price_over_ma60`

**资金与预算**（核心：三个金额概念）：
- `new_budget`：本轮新增预算（根据资金规则计算出的"新可投入金额"）
- `wait_pool_before`：等待池余额（before，历史累计）
- `planned_amount`：本轮可用于买入（=new_budget + wait_pool_before）
- `cash_available`：可用现金池余额（可用于今天执行）
- `wait_pool_balance`：等待池累计金额（after，=wait_pool_before + moved_to_wait）
- `plan_budget_today`：今天"计划预算"（兼容字段，等于new_budget）
- `budget_for_execution`：本次允许用于执行的预算（=min(planned_amount, cash_available)）
- `budget_to_execute`：本次建议实际执行金额
- `budget_to_wait_pool`：本次应转入等待池的预算金额

**交易成本与门槛**：
- `fee_rate`、`fee_min`、`min_trade_amount`、`ideal_trade_amount`
- `estimated_fee`、`lot_size`、`suggest_shares`、`rounded_amount`（ETF/LOF一手约束）

**最终建议**：
- `action`：BUY/HOLD/WAIT/SKIP
- `execute_ratio`：执行比例（0~1）
- `wait_ratio`：转等待池比例（0~1）
- `limit_price_hint`、`time_window_hint`
- `reason_blocks`：结构化原因列表

### 策略实现

系统内置以下策略：

1. **percentile**（分位策略）：基于滚动N日close分位判断买入时机
2. **drawdown**（回撤策略）：根据相对高点回撤触发加仓
3. **profit_recycle**（利润回收策略）：动态调整利润池，释放资金
4. **simple**（简单策略）：按计划买/预算够就买
5. **dca_4pct**（4%定投策略）：基于回撤档位触发买入，支持分位门控

### 使用流程

1. **配置策略绑定**：在产品管理页为每个产品绑定策略（可绑定多个）
2. **配置资金池规则**：在资金池规则页配置账户分配比例
3. **自动生成建议**：调度器每分钟生成一次建议（交易时段）
4. **查看建议**：在Dashboard的产品行情处查看最新建议
5. **人工执行**：根据建议手动下单

### 等待池与预算逻辑

#### 三个金额概念

1. **new_budget（本轮新增预算）**：本次运行Advisor时，根据资金规则（account_pool_rules）计算出的"本轮新可投入金额"。可以是0（非定投日、或规则未分配到该产品）。

2. **wait_pool_balance（等待池余额）**：之前已经决定要买、但因为条件不满足而延期的"保留资金"，按产品维度累计。跨天累计，永不凭空消失，除非被"实际成交扣减"消耗。

3. **planned_amount（本轮可用于买入）**：`new_budget + wait_pool_before`。这是本轮最多可用于该产品的预算上限，不是必须全花。

#### 等待池增加规则（关键语义修正）

**等待池语义**：WAIT_BUY 只记录"被规则否决而延期执行的资金"，不包括非交易日冻结的预算。

**等待池增加条件**（必须同时满足）：
1. **交易日**：非交易日时 `moved_to_wait_pool` 必须为 0（预算冻结，不迁移）
2. **规则否决**：只有在 VETO 的"规则否决类原因"触发时，才允许把预算迁移到 WAIT_BUY
   - QDII溢价过高（>2%全部等待，1%-2%半买）
   - 最小成交额不足（<min_trade_amount）
   - 风控上限等
3. **有买入意图**：只有当 `planned_amount > 0` 且策略触发买入意图时，才进入等待池
   - 如果策略未触发（如分位太高，action=HOLD且suggest_amount=0），`moved_to_wait = 0`

**非交易日处理**：
- 非交易日时，action=HOLD，`moved_to_wait_pool = 0`
- reason明确说明"【非交易日】市场关闭：预算冻结，下一交易日开盘前重新评估"
- 预算冻结，不迁移到等待池（这是"延后评估"，不是"条件否决"）

**半买处理**：`executed_amount = floor_to_trade_unit(planned_amount * execute_ratio)`，`remainder_amount = planned_amount - executed_amount`，`moved_to_wait = remainder_amount`

#### 等待池扣减规则

等待池减少只能由"真实成交导入/确认"触发：
- 扣减顺序：先扣该产品的WAIT_POOL（等待池），不足部分再扣CASH_FREE（自由现金）
- 在 `buy_confirm` 或 `buy` 确认时，自动调用 `reduce_pending_amount_by_transaction` 扣减等待池

#### 避免重复累加

- 检查上次建议的 `budget_to_wait_pool`，只增加"增量"部分，而不是每次都累加全部
- 记录 `last_change_reason`（如 'NON_TRADE_DAY', 'PREMIUM_BRAKE', 'MIN_TRADE_LIMIT'）和 `last_change_time`

### 关键约束

- **金额非负**：所有金额字段必须 ≥ 0
- **planned_amount恒等式**：`planned_amount == new_budget + wait_pool_before`
- **比例恒等式**：`budget_to_execute + budget_to_wait_pool <= planned_amount` 且 `budget_to_execute + budget_to_wait_pool <= budget_for_execution`
- **wait_pool_after恒等式**：`wait_pool_after == wait_pool_before + moved_to_wait`（Advisor不扣减）
- **比例计算**：`execute_ratio = budget_to_execute / budget_for_execution`（若0则0），`wait_ratio = budget_to_wait_pool / budget_for_execution`
- **BUY约束**：action=BUY时必须满足 `min_trade_amount` & 现金足够 & 一手约束，且 `executed_amount > 0`
- **溢价刹车**：premium>2%时必须 `budget_to_execute=0` 且 `budget_to_wait_pool=budget_for_execution`（全部可执行预算进入等待池）
- **非交易日约束**：非交易日时 `moved_to_wait_pool` 必须为 0（预算冻结，不迁移）
- **reason可解释性**：reason长度>=60字，且必须包含关键数值（pct_rank、premium、budget、suggest_amount、moved_to_wait_pool等）

## 🎓 关键设计理念

1. **成本口径统一**：`cost = amount - fee`（净申购额），全系统一致
2. **pnl_day 只反映净值变化**：`prev_shares × (nav - prev_nav)`
3. **同日覆盖**：同一 (fetch_date, product_code) 只保留一条，保持数据干净
4. **数据库驱动配置**：所有配置（产品、账户、分类、资金池规则、调度任务）和数据（交易流水、订单、账本、快照）都存储在 MySQL 数据库中，不再使用任何文件配置
5. **字段零冗余**：每个字段都有明确用途，不引入无用字段
6. **交易日自动判断**：使用 `chinese-calendar` 开源库，无需维护节假日配置
7. **场内/场外分离**：同一代码的产品可以同时存在场外和场内版本（如 163406）
8. **实时行情去重**：相同时间点的行情会覆盖，不同时间点的行情会保留（历史可追溯）
9. **溢价刹车**：QDII ETF 溢价率过高时自动减少买入金额，剩余资金进入待买入池
10. **定投计划**：支持按星期几定投，自动生成任务并应用溢价刹车

---

## 🏷️ 字段命名规范

系统使用统一的字段命名（snake_case，小写），同一语义只有一个字段名：

### 日期字段
| 字段 | 说明 | 示例 |
|------|------|------|
| `date` | 交易日期（transactions表） | 2025-12-18 |
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

---

## 🆕 v3.0 新功能

### 场内交易支持

- **ETF/LOF 支持**：支持场内交易产品，通过 AKShare 采集实时行情
- **实时行情**：交易时间每分钟采集一次实时价格、涨跌幅、成交量
- **日K线**：自动采集历史K线数据，用于技术分析
- **QDII溢价率**：监控 QDII ETF 的溢价率，避免高溢价买入

### 溢价刹车

- **自动刹车**：QDII ETF 溢价率过高时自动减少买入金额
- **规则**：
  - 溢价率 ≤ 1%：正常买入（100%）
  - 1% < 溢价率 ≤ 2%：买入一半（50%），剩余进入待买入池
  - 溢价率 > 2%：暂停买入（0%），全部进入待买入池
- **待买入池**：扣留的资金会累加到待买入池，可在溢价率降低时手动买入

### 调度器服务

- **表驱动配置**：所有调度任务配置存储在 `job_config` 表中
- **自动采集**：
  - 场内实时行情：交易时间每分钟采集
  - 场外净值更新：每日 08:00、14:00、22:00 采集
- **幂等执行**：防止重复执行，失败自动重试

### 数据库驱动配置

- **零文件配置**：所有业务配置存储在 MySQL 中
- **产品管理**：通过 UI 或数据库直接管理产品配置
- **账户管理**：支持账户层级和产品关联
- **资金池规则**：通过 UI 配置资金池分配规则

---

---

## 📊 数据库表总览

系统共包含 **28 张数据库表**，分为以下几类：

### 核心业务表（5张）
1. **transactions** - 交易流水表
2. **orders** - 理财任务队列表
3. **ledger** - 生活账本表
4. **daily_snapshot** - 产品日快照表
5. **nav** - 净值历史表

**注意**：账户余额现在直接从 `accounts` 表的 `balance` 字段读取（实时数据），不再使用 `daily_balance` 快照表。

### 配置表（6张）
7. **products** - 产品配置表
8. **accounts** - 账户配置表
9. **account_groups** - 账户组配置表
10. **categories** - 分类配置表
11. **account_pool_rules** - 资金池分配规则表
12. **job_config** - 任务调度配置表

### 场内交易表（4张）
13. **market_quote_rt** - 场内实时行情表
17. **market_bar_d** - 场内日K线表
18. **qdii_premium_rt** - QDII溢价率表
19. **trade_fills** - 场内成交流水表

### 策略实验室表（4张）
18. **strategy_config** - 策略配置表
19. **backtest_summary** - 回测汇总表
20. **backtest_daily** - 回测每日数据表
21. **backtest_trades** - 回测成交表

### 生产建议层表（Advisor，5张）
22. **product_strategy_bind** - 产品策略绑定表（支持多策略组合）
23. **strategy_state** - 策略状态表（存储策略运行时状态）
24. **indicator_daily** - 日更慢指标表（分位排名、回撤、均线等）
25. **advisor_suggestion** - 建议输出表（存储每次生成的建议）
26. **budget_trace** - 预算追踪审计日志表（记录预算分配与延期情况）

### 辅助表（1张）
27. **product_nav_range** - 产品净值范围表

详细字段定义请参阅 [docs/field_spec.md](docs/field_spec.md)

---

---

## 📋 完整数据库表列表

系统共包含 **27 张数据库表**，详细字段定义请参阅 [docs/field_spec.md](docs/field_spec.md)：

### 核心业务表（5张）
1. `transactions` - 交易流水表（所有投资交易记录）
2. `orders` - 理财任务队列表（待结算订单）
3. `ledger` - 生活账本表（日常收支记录）
4. `daily_snapshot` - 产品日快照表（每日资产快照）
5. `nav` - 净值历史表（所有产品历史净值）

**注意**：账户余额现在直接从 `accounts` 表的 `balance` 字段读取（实时数据），不再使用 `daily_balance` 快照表。

### 配置表（6张）
7. `products` - 产品配置表（产品基本信息、费率等）
8. `accounts` - 账户配置表（账户基本信息）
9. `account_groups` - 账户组配置表（账户组关系）
10. `categories` - 分类配置表（生活记账分类）
11. `account_pool_rules` - 资金池分配规则表（资金池分配比例）
12. `job_config` - 任务调度配置表（APScheduler任务配置）

### 场内交易表（4张）
13. `market_quote_rt` - 场内实时行情表（交易时间每分钟采集）
14. `market_bar_d` - 场内日K线表（历史K线数据）
15. `qdii_premium_rt` - QDII溢价率表（QDII ETF溢价率）
16. `trade_fills` - 场内成交流水表（场内交易成交记录）

### 等待池表（1张）
17. `pending_buy_pool` - 待买入池表（溢价刹车扣留的资金）

### 策略实验室表（4张）
18. `strategy_config` - 策略配置表（策略参数配置）
19. `backtest_summary` - 回测汇总表（回测结果汇总）
20. `backtest_daily` - 回测每日数据表（回测每日数据）
21. `backtest_trades` - 回测成交表（回测逐笔成交记录）

### 生产建议层表（Advisor，5张）
22. `product_strategy_bind` - 产品策略绑定表（支持多策略组合）
23. `strategy_state` - 策略状态表（存储策略运行时状态）
24. `indicator_daily` - 日更慢指标表（分位排名、回撤、均线等）
25. `advisor_suggestion` - 建议输出表（存储每次生成的建议）
26. `budget_trace` - 预算追踪审计日志表（记录预算分配与延期情况）

### 辅助表（1张）
27. `product_nav_range` - 产品净值范围表（产品净值日期范围统计）

---

**最后更新**: 2025-12-20 (v3.0 - 场内交易版)  
**预计学习时间**: 30分钟掌握核心，1小时完全掌控
