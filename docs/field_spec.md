# 字段计算规则合同 (Field Spec) v3.0

本文档定义 MyDCA-Board 财富中枢系统中所有数据文件的字段规范，确保：
- 字段命名统一（snake_case 全小写）
- 口径一致（同一指标在所有模块中计算方式相同）
- 可解释性（每个字段都能通过交易/净值变化解释数值变化）
- 可复现性（关键数值固化到流水，不依赖未来配置反算）

**v3.0 更新**：
- 新增场内交易相关表规范（trade_fills, market_quote_rt, market_bar_d, qdii_premium_rt）
- 新增资金池与定投相关表规范（account_pool_rules, dca_plan, task_dca, pending_buy_pool）
- 新增调度配置表规范（job_config）
- 新增分类和账户组表规范（categories, account_groups）
- 所有配置迁移到数据库，不再使用 JSON 文件

---

## 0. 总原则

| 原则 | 说明 |
|------|------|
| 字段命名统一 | snake_case 全小写，禁止同一语义多个字段名 |
| 不允许冗余字段 | 任何字段必须有清晰用途与计算规则 |
| 口径一致性 | 同一指标在持仓/快照/汇总/回测中必须一致 |
| 可解释性 | 每个字段都有"人类解释"，能通过交易/净值变化解释 |
| 可复现性 | fee 实际金额、订单确认偏移等必须固化到流水 |
| 健壮与幂等 | settle 缺净值时保持 pending；重复 settle 不重复 confirm |
| 份额精度 | shares 存储精度至少 `Decimal('0.0001')`，展示时可格式化为2位 |

---

## 1. transactions 表（投资交易事实表）

### 1.1 数据库表字段（固定）

```
id, date, product_id, product_code, action, amount, shares, fee, nav, nav_date, order_id, note, created_at
```

### 1.2 action 枚举与含义

| action | 含义 | amount | shares | fee | nav/nav_date | order_id |
|--------|------|--------|--------|-----|--------------|----------|
| buy_debit | 买入扣款（钱已扣，份额未确认） | 扣款金额（含申购费） | 空 | 申购费金额 | 可空 | **必填** |
| buy_confirm | 买入确认（份额到账） | 可空 | 确认份额（≥4位小数） | **固定0** | **必填** | **必填** |
| sell_confirm | 赎回确认/到账 | 到账净额 | 赎回份额 | 赎回费金额 | **必填** | **必填** |
| dividend | 分红（红利再投） | 可空或0 | 分红份额 | 可空 | **必填** | 可空 |
| buy | 兼容模式（同日扣款+确认） | 扣款金额 | 确认份额 | 申购费 | **必填** | 可空 |
| sell | 兼容模式（同日赎回确认） | 到账净额 | 赎回份额 | 赎回费 | **必填** | 可空 |

### 1.3 字段定义

| 字段 | 定义 | 数据类型 | 约束 |
|------|------|----------|------|
| date | 该条流水发生日期 | YYYY-MM-DD | 必填 |
| product_code | 产品代码 | string | 必填，需在 products 表中存在 |
| action | 交易类型 | string | 必填，枚举值见上表（存储小写） |
| amount | 金额 | Decimal | 非负；buy_debit=扣款含手续费；sell_confirm=到账净额 |
| shares | 份额 | Decimal | 正数；精度≥0.0001；确认/赎回/分红时必填 |
| fee | 手续费金额 | Decimal | 非负；buy_confirm 固定为 0 |
| nav | 净值 | Decimal | 正数；确认类事件必填 |
| nav_date | 净值日期 | YYYY-MM-DD | 确认类事件必填 |
| order_id | 订单号 | string | buy_debit/buy_confirm/sell_confirm 必填 |
| note | 备注 | string | 可空 |

### 1.4 强校验规则

1. buy_confirm 必须能匹配同 order_id 的 buy_debit（否则报错/拒绝落账）
2. buy_debit 必须有 order_id
3. sell_confirm 必须有 order_id
4. 金额、份额、净值必须可解析为 Decimal，且非负
5. shares > 0（确认/赎回/分红时），精度至少 0.0001
6. 同一 order_id 不得出现多次 buy_confirm 或 sell_confirm

### 1.5 order_id 格式规范

格式：`YYYYMMDDHHMMSS_{product_code}_{seq}`

- `YYYYMMDDHHMMSS`：生成时间戳
- `product_code`：产品代码
- `seq`：同一秒内递增序号（从现有 orders/transactions 中同秒同产品的最大 seq + 1）

示例：`20251220143956_161130_001`

### 1.6 buy 兼容模式说明

`buy` 动作是"同日扣款+确认"的原子组合，在计算层应等价视为：
1. 先执行 buy_debit 逻辑：`principal_total += amount`，`cash_in_transit += (amount - fee)`
2. 再执行 buy_confirm 逻辑：`shares += shares`，`cost += (amount - fee)`，`cash_in_transit -= (amount - fee)`

代码应复用 buy_debit/buy_confirm 的同一套计算路径，而不是写两套分叉逻辑。

### 1.7 交易动作对状态变量的影响（P0-2 核算规则）

每个资产维度维护以下状态变量：

| 状态变量 | 定义 | 精度 |
|---------|------|------|
| shares | 当前份额 | DECIMAL(20,6) |
| cost | 持仓成本（仅针对持仓部分，净申购额口径） | DECIMAL(20,2) |
| cash_in_transit | 在途资金（扣款未确认/赎回未到账） | DECIMAL(20,2) |
| realized_pnl | 已实现盈亏（卖出确认时结转） | DECIMAL(20,2) |
| fees_total | 累计费用（可选，若已有字段则复用） | DECIMAL(20,2) |
| principal_total | 累计投入本金（扣款时增加，卖出不减少） | DECIMAL(20,2) |

#### 1.7.1 buy_debit（买入扣款）

**含义**：钱已从资金账户扣除，但份额尚未确认到账。

**状态变化**：
- `principal_total += amount`（扣款金额，含手续费）
- `cash_in_transit += (amount - fee)`（净申购额进入在途）
- `fees_total += fee`（累计费用增加）
- `shares` 不变
- `cost` 不变

**硬约束**：
- 必须有 `order_id`
- `amount > 0`
- `fee >= 0`
- `amount - fee > 0`（净申购额必须为正）

#### 1.7.2 buy_confirm（买入确认）

**含义**：份额正式到账，成本入账。

**状态变化**：
- `shares += confirmed_shares`（份额增加）
- `cost += net_amount`（净申购额入账，`net_amount = 对应 buy_debit 的 amount - fee`）
- `cash_in_transit -= net_amount`（在途资金减少）
- `principal_total` 不变（已在 buy_debit 时计入）
- `fees_total` 不变（已在 buy_debit 时计入）

**硬约束**：
- 必须有 `order_id`，且必须能找到对应的 `buy_debit`
- `shares > 0`
- `fee = 0`（申购费已在 buy_debit 时扣除）
- 必须提供 `nav` 和 `nav_date`

**成本口径统一规则（A）**：
- `cost` 永远使用"净申购额"（`amount - fee`）入账
- 严禁同一系统里一部分用毛额、一部分用净额

#### 1.7.3 buy（兼容/原子模式）

**含义**：同日扣款+确认的原子组合。

**状态变化**：
- 等价于先执行 `buy_debit`，再执行 `buy_confirm`（同日）
- 最终结果：`shares += shares`，`cost += (amount - fee)`，`principal_total += amount`
- `cash_in_transit` 不变（当天确认，不产生在途）

**硬约束**：
- 必须提供 `amount`、`shares`、`fee`、`nav`、`nav_date`
- 必须遵循净申购额口径

#### 1.7.4 sell_request（赎回发起）

**含义**：赎回/卖出发起（如系统已有 orders 状态机）。

**状态变化**：
- 不改变任何状态变量（仅记录订单）
- 等待 `sell_confirm` 时再处理

#### 1.7.5 sell_confirm（卖出确认）

**含义**：卖出确认，份额减少，成本按比例减少，已实现盈亏结转，现金回笼。

**状态变化**：
- `shares -= sold_shares`（份额减少）
- `sold_cost = cost * (sold_shares / prev_shares)`（按比例计算卖出成本）
- `cost -= sold_cost`（成本按比例减少）
- `realized_pnl += (sell_amount_net - sold_cost - sell_fee - sell_tax)`（已实现盈亏）
- `cash_in_transit` 不变（或根据系统设计，回笼现金进入目标账户/池）
- `principal_total` 不变（卖出不减少累计投入本金）
- `fees_total += sell_fee`（累计费用增加）

**卖出成本结转规则（B）**：
- 使用平均成本法：`sold_cost = avg_cost * sold_shares`
- 其中 `avg_cost = cost / shares`（卖出前的平均成本）
- `realized_pnl = sell_amount_net - sold_cost - sell_fee - sell_tax`

**现金回笼闭环规则（C）**：
- 卖出回笼的现金必须有归属（进入 `cash_pool`/目标账户/资金池）
- 不能"凭空消失"导致总资产下降
- 若系统暂时没有账户系统闭环，至少在产品维度保留 `cash_pool` 并写清楚它属于资产的一部分

**硬约束**：
- 必须有 `order_id`
- `shares > 0`（卖出份额必须为正）
- `shares <= prev_shares`（不能卖出超过持有份额）
- `cost >= 0`（卖出后成本不能为负）
- 必须提供 `nav`、`nav_date`、`amount`（到账净额）

#### 1.7.6 dividend_cash（现金分红）

**含义**：现金分红，不改份额，现金增加。

**状态变化**：
- `shares` 不变
- `cost` 不变
- `cash_in_transit` 或目标现金账户增加 `dividend_amount`
- `principal_total` 不变
- `realized_pnl` 不变（现金分红不计入已实现盈亏，属于收益分配）

#### 1.7.7 dividend_reinvest（红利再投）

**含义**：红利再投，份额增加，成本按净申购额入账。

**状态变化**：
- `shares += dividend_shares`（份额增加）
- `cost += net_dividend_amount`（净分红额入账，若分红有税费则扣除）
- `cash_in_transit` 不变（红利再投不涉及现金流动）
- `principal_total` 不变（红利再投不增加本金）
- `fees_total` 不变（红利再投通常无费用）

**硬约束**：
- `shares > 0`
- 必须提供 `nav`、`nav_date`
- 成本口径必须与 `buy_confirm` 一致（净额）

#### 1.7.8 fee / tax / other_fee（费用拆分）

**含义**：如系统拆分费用类型，必须说明归集口径。

**状态变化**：
- `fees_total += fee_amount`（累计费用增加）
- 其他状态变量根据费用类型决定（如申购费在 `buy_debit` 时扣除，赎回费在 `sell_confirm` 时扣除）

#### 1.7.9 硬约束汇总

**D) order_id/订单状态硬约束**：
- `buy_confirm` 必须能找到对应 `buy_debit`（有 `order_id`）或走明确的兼容分支
- 兼容分支必须同时补齐本金/成本统计，不能吞导致收益率虚高

**E) 不允许负份额/负成本**：
- 任意时刻 `shares < 0` 或 `cost < 0` 必须抛错并提示"流水不一致"
- 系统应在 `holdings_calculator` 和 `invariants` 中检查

---

## 2. orders 表（清算任务队列）

### 2.1 数据库表字段（固定）

```
id, order_id, product_id, product_code, order_type, amount, fee, shares, requested_at, trade_date, nav_date, confirm_date, holding_days, sell_fee_rate, status, note, created_at
```

**注意**：`orders` 表没有 `updated_at` 字段，只有 `created_at` 字段。

### 2.2 order_type 枚举

| order_type | 含义 | 将来生成 |
|------------|------|----------|
| buy_debit | 买入扣款订单 | buy_confirm |
| redeem_request | 赎回发起订单 | sell_confirm |

### 2.3 status 枚举

| status | 含义 |
|--------|------|
| pending | 待处理（等待 settle） |
| done | 已完成（已生成对应 confirm） |
| cancelled | 已取消 |

### 2.4 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| order_id | 订单唯一标识 | string（格式见 1.5） |
| product_code | 产品代码 | string |
| order_type | 订单类型 | string |
| amount | 扣款/到账金额 | Decimal |
| fee | 手续费金额 | Decimal |
| shares | 份额（赎回时必填） | Decimal（精度≥0.0001） |
| requested_at | 发起时间 | YYYY-MM-DD HH:MM:SS |
| trade_date | 交易日 | YYYY-MM-DD |
| nav_date | 净值日期 | YYYY-MM-DD |
| confirm_date | 确认日期 | YYYY-MM-DD |
| holding_days | 持有天数（赎回时用于费率计算） | int（可空） |
| sell_fee_rate | 赎回费率（固化，确保历史可复现） | Decimal（可空） |
| status | 订单状态 | string |
| note | 备注 | string |

### 2.5 关键规则

1. **orders 是清算状态表**，不是资产事实表
2. **settle 只处理**：`status=pending` 且 `confirm_date <= today` 的订单
3. **幂等性**：如果 transactions 已存在同 order_id + confirm_action，则不重复写
4. **净值缺失处理**：保持 pending，不崩溃，不写0，输出友好提示

---

## 3. ledger 表（生活记账流水表）

### 3.1 数据库表字段（固定）

```
id, event_time, entry_type, amount, category_l1, category_l2, account_from, account_to, discount, reimbursable, note, created_at
```

### 3.2 entry_type 枚举

| entry_type | 含义 | account_from | account_to |
|------------|------|--------------|------------|
| expense | 支出 | **必填** | 可空 |
| income | 收入 | 可空 | **必填** |
| transfer | 转账 | **必填** | **必填**，且≠account_from |

### 3.3 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| event_time | 发生时间 | YYYY-MM-DD HH:MM:SS |
| entry_type | 记账类型 | string |
| amount | 金额 | Decimal（正数） |
| category_l1 | 一级分类 | string |
| category_l2 | 二级分类 | string（可空） |
| account_from | 支出账户 | string |
| account_to | 收入账户 | string |
| discount | 优惠金额 | Decimal（默认0） |
| reimbursable | 是否可报销 | 0/1（默认0） |
| note | 备注 | string |

### 3.4 账户余额计算（动态计算，不存储）

为便于对账，UI 显示时会动态计算以下字段：

| 字段 | 定义 | 计算方式 |
|------|------|----------|
| balance_after | 操作后账户余额 | 截止当前时间该账户的所有入账 - 出账 |
| parent_balance_after | 操作后父账户余额 | 若账户属于某账户组（如 ylb_life 属于余利宝），则为组内所有账户余额之和 |

**设计原则**：
- 余额**不存储**在数据库中，而是**查询时动态计算**
- 这样做的好处是：修改任何历史记录后，后续所有记录的余额会**自动正确**
- 无需手动更新历史余额，避免数据不一致问题

**账户组定义**（来自 `account_groups` 表）：
- 余利宝组：ylb_life（生活费）、ylb_project（项目资金）
- 稳利宝组：wlb_life（生活费）、wlb_project（项目资金）

**示例**：
```
记录：从 ylb_life 支出 100 元
balance_after: ylb_life 的余额（截止该时间）
parent_balance_after: ylb_life + ylb_project 的总余额（截止该时间）
```

---

## 4. daily_snapshot 表（每日快照事实表）

### 4.1 数据库表字段（固定）

```
id, fetch_date, product_id, product_code, product_name, category, nav_date, nav, shares, value, pnl_day, cost, unrealized_pnl, return_rate, cash_in_transit, total_value, principal_total, total_redemption, total_pnl, real_return, data_status, fetched_at, created_at
```

### 4.2 中文表头映射

| 字段 | 中文表头 |
|------|----------|
| fetch_date | 采集日期 |
| product_code | 产品代码 |
| product_name | 产品名称 |
| category | 分类 |
| nav_date | 净值日期 |
| nav | 净值 |
| shares | 份额 |
| value | 市值 |
| pnl_day | 日变动 |
| cost | 成本 |
| unrealized_pnl | 浮动盈亏 |
| return_rate | 收益率 |
| cash_in_transit | 在途资金 |
| total_value | 总资产 |
| principal_total | 累计投入本金 |
| total_redemption | 累计赎回 |
| total_pnl | 总盈亏 |
| real_return | 真实收益率 |
| data_status | 数据状态 |
| fetched_at | 采集时间 |

### 4.3 字段定义与公式

| 字段 | 定义 | 公式 | 变化触发 |
|------|------|------|----------|
| fetch_date | 快照生成日期 | YYYY-MM-DD | 每次运行 |
| product_code | 产品代码 | - | 固定 |
| product_name | 产品名称 | 来自 products 表 | 固定 |
| category | 分类 | fund/bank | 固定 |
| nav_date | 用于估值的净值日期 | 可能滞后 T+1 | 净值更新 |
| nav | 单位净值 | Decimal | 净值更新 |
| shares | 当前确认持有份额 | `Σ buy_confirm.shares - Σ sell_confirm.shares + Σ dividend.shares` | 确认/赎回/分红 |
| value | 持仓市值 | `shares × nav` | 净值变/份额变 |
| pnl_day | 日变动（净值波动） | `prev_shares × (nav - prev_nav)` | 净值变 |
| cost | 持仓成本（净申购额口径） | `Σ net_amount - 卖出按比例结转` | 确认/赎回 |
| unrealized_pnl | 浮动盈亏 | `value - cost` | 净值变/份额变 |
| return_rate | 持仓收益率 | `unrealized_pnl / cost × 100%` | 净值变/份额变 |
| cash_in_transit | 在途资金 | `Σ pending_buy_debit.(amount - fee)` | 扣款/确认 |
| total_value | 产品总资产 | `value + cash_in_transit` | 净值变/扣款/确认 |
| principal_total | 累计投入本金 | `Σ buy_debit.amount`（不因卖出减少） | 扣款 |
| total_redemption | 累计赎回金额 | `Σ sell_confirm.amount`（到账净额） | 赎回确认 |
| **total_pnl** | **生命周期总盈亏** | `total_value + total_redemption - principal_total` | 净值变/扣款/确认/赎回 |
| real_return | 真实收益率 | `total_pnl / principal_total × 100%` | 同上 |
| data_status | 数据状态 | ok/carried_forward/missing/holiday | 每次运行 |
| fetched_at | 采集时间 | YYYY-MM-DD HH:MM:SS.mmm | 每次运行 |

### 4.4 关键口径说明

#### total_pnl（生命周期总盈亏）⭐

**定义**：该产品从开始到现在的总盈亏，包含已实现（已赎回）和未实现（仍持有）

**公式**：`total_pnl = total_value + total_redemption - principal_total`

**解读**：
- `total_value`：仍在产品里的资金（持仓市值 + 在途资金）
- `total_redemption`：已赎回回笼的现金（到账净额累计）
- `principal_total`：历史累计投入的本金
- **直观理解**：我投了 X 元，现在还有 Y 元在里面，已经拿回了 Z 元，所以总盈亏 = Y + Z - X

**关键特性**：
- 未卖出时：`total_redemption=0`，`total_pnl = total_value - principal_total`
- 全部赎回后：`total_value=0`，`total_pnl = total_redemption - principal_total = 实际利润`
- **不会反直觉**：全赎回后不会变成 -principal_total

#### pnl_day（日变动）

**定义**：今日净值波动带来的市值变化（只由"份额 × 净值变化"产生）

**公式**：`pnl_day = prev_shares × (nav - prev_nav)`

**说明**：
- 不受当日扣款/确认/赎回影响（必须剔除资金流）
- `prev_shares` 和 `prev_nav` 来自上一交易日快照
- 若缺失上一交易日快照，则 `pnl_day = 0`，并标记 `data_status = 'missing'`
- 若当天无净值/行情，可延用上一交易日价格并标记 `data_status = 'carried_forward'`（不得默默当0）

**缺数据兜底规则**：
- 交易日判断：节假日/周末不生成交易快照或标 `data_status = 'holiday'`
- 若当天无净值/行情：可延用上一交易日价格并标记 `data_status = 'carried_forward'`（不得默默当0）
- `data_status` 枚举：`ok`（正常）、`carried_forward`（延用前日价格）、`missing`（缺数据）、`holiday`（节假日）

**关键特性**：
- 资金流入日不把"投入金额"算成当日收益
- 例如：D1 投入 1000 元，D2 净值上涨 1%，`pnl_day` 应基于 D1 的份额计算，而不是把 1000 元算成收益

---

## 5. daily_balance（账户余额快照表）

**注意**：账户余额快照数据存储在 MySQL 数据库的 `daily_balance` 表中。

### 5.1 数据库表字段（固定）

```
id, fetch_date, account_id, account_name, account_type, balance, related_product, product_value, diff, note, created_at
```

**注意**：`yesterday_pnl`、`unrealized_pnl`、`total_pnl` 字段在代码中计算并在 UI 中显示，但**不存储**在数据库中。这些字段仅在查询时动态计算。

### 5.2 中文表头映射

| 字段 | 中文表头 |
|------|----------|
| fetch_date | 采集日期 |
| account_id | 账户ID |
| account_name | 账户名称 |
| account_type | 账户类型 |
| balance | 账户余额 |
| related_product | 关联产品 |
| product_value | 产品市值 |
| diff | 差异 |
| yesterday_pnl | 昨日收益 |
| unrealized_pnl | 持有收益 |
| total_pnl | 累计收益 |
| note | 备注 |

### 5.3 account_type 枚举

**注意**：`daily_balance` 表中的 `account_type` 字段类型为 `varchar(30)`，存储小写值。`accounts` 表中的 `account_type` 字段类型为 `enum('CASH','BUCKET','FUND_MAPPED','PRODUCT_SUB','FUND_TOTAL','SUMMARY')`，存储大写值。

| account_type | 含义 | 余额计算规则 |
|--------------|------|--------------|
| cash | 现金账户（余利宝、银行卡等） | `Σ ledger 入账 - Σ ledger 出账` |
| bucket | 现金桶账户（未来扩展） | `Σ ledger 入账 - Σ ledger 出账` |
| fund_mapped | 货币基金映射账户（小荷包） | `关联产品市值 + 当日收益`（收益自动入账） |
| product_sub | 产品子账户（稳利宝各子账户） | `Σ ledger 入账 - Σ ledger 出账` |
| fund_total | 基金账户汇总 | `Σ daily_snapshot 表基金市值`（排除 fund_mapped 关联产品） |
| summary | 汇总行 | 各组账户合计 |

### 5.4 字段定义与公式

| 字段 | 定义 | 公式 | 变化触发 |
|------|------|------|----------|
| fetch_date | 快照生成日期 | YYYY-MM-DD | 每次同步 |
| account_id | 账户唯一标识 | 来自 accounts 表 | 固定 |
| account_name | 账户显示名称 | 来自 accounts 表 | 固定 |
| account_type | 账户类型 | 见上表枚举 | 固定 |
| balance | 账户余额（本金分桶） | 见上表计算规则 | ledger 变动 |
| related_product | 关联产品代码 | 来自 accounts 表的 product_id 关联 | 固定 |
| product_value | 展示市值 | 见 5.5 说明 | 净值变动 |
| diff | 收益/差异 | 见 5.5 说明 | 净值变动 |
| note | 备注 | 来自 accounts 表或动态生成 | 净值变动 |

**动态计算字段**（不存储在数据库，仅在查询时计算）：
- `yesterday_pnl`：昨日收益（前一天的 pnl_day，仅基金、余利宝生活费、稳利宝）
- `unrealized_pnl`：持有收益（浮动盈亏，仅基金、余利宝生活费、稳利宝）
- `total_pnl`：累计收益（生命周期总盈亏，仅基金、余利宝生活费、稳利宝）

**注意**：这些动态计算字段在 `snapshot_service.py` 的 `read_latest_daily_balance()` 函数中计算，用于 UI 显示，但不写入数据库。

### 5.5 product_value 与 diff 字段说明

**核心原则**：
- `balance` **永远**来自 ledger 计算（本金分桶余额），不含收益
- `product_value` 是**展示口径**，可包含收益
- `diff` 显示收益/亏损差异

| account_type | balance | product_value | diff |
|--------------|---------|---------------|------|
| cash | ledger 余额 | 空 | 空 |
| fund_mapped | 市值+日收益 | 同 balance | `balance - ledger_balance` |
| **product_sub（普通）** | ledger 余额 | **= balance** | 空 |
| **product_sub（profit_account）** | ledger 余额 | **= balance + group_profit** | **group_profit** |
| summary | 子账户合计 | 父产品市值 | group_profit |

#### product_sub 收益分配展示规则

对于有 `linked_product` 的账户组（如稳利宝），系统会将父产品收益**展示分配**到指定的 `profit_account`：

1. **group_profit 计算**：`父产品 total_value - 子账户 balance 合计`
2. **profit_account 查找规则**（优先级）：
   - `account_groups[group_id].profit_account`（如 `wenlibao_project`）
   - 子账户中 `receives_profit=true` 的账户
   - 找不到则不分配，仅在汇总行显示
3. **展示结果**：
   - 普通子账户：`product_value = balance`，`diff` 为空
   - profit_account：`product_value = balance + group_profit`，`diff = group_profit`

**重要**：此展示分配**不写入 ledger**，不改变本金口径，仅用于更直观阅读。

**示例**：
```
假设：父产品 FBAE41126E 的 total_value = 24322.06
      子账户 balance 合计 = 24320.87
      group_profit = 1.19

结果：
- wenlibao_rent:    balance=4000.00, product_value=4000.00, diff=空
- wenlibao_project: balance=9475.30, product_value=9476.49, diff=1.19  ← profit_account
- wenlibao_total:   balance=24320.87, product_value=24322.06, diff=1.19
```

### 5.6 fund_mapped 账户收益计算（货币基金）

对于关联货币基金的账户（如小荷包 -> 000686），余额包含当日收益：

**收益公式**：`日收益 = 持有份额 / 10000 × 万份收益`

**余额公式**：`balance = 关联产品市值 + 当日收益`

**示例**：
- 持有份额：348.25
- 万份收益：0.3249
- 日收益：348.25 / 10000 × 0.3249 = 0.01
- 余额：348.25 + 0.01 = 348.26

### 5.7 汇总行规则

| 汇总行 | account_id | 计算规则 |
|--------|------------|----------|
| 稳利宝合计 | wenlibao_total | balance = Σ 子账户余额，product_value = 父产品市值，diff = group_profit |
| 余利宝合计 | ylb_total | balance = ylb_life + ylb_finance |
| 基金合计 | fund_total | balance = Σ daily_snapshot 表基金市值（排除 fund_mapped 关联产品） |

**汇总行 diff 与 profit_account diff 一致**：表示该产品组在该快照日的收益/亏损。

---

## 6. 组合汇总规则

### 6.1 global_mode（防止双计）

系统支持两种全局汇总模式，根据产品配置自动判断：

| 模式 | 判断条件 | 说明 |
|------|----------|------|
| no_cash_bucket | products.json 中无 `is_cash_bucket=true` 的产品 | 当前阶段默认 |
| cash_bucket | products.json 中存在 `market="cash_like"` 且 `is_cash_bucket=true` 的产品 | 未来阶段 |

### 6.2 no_cash_bucket 模式（当前默认）

赎回回笼的现金未被"现金桶产品"承接，需要在汇总中单独加回：

| 汇总字段 | 公式 |
|----------|------|
| global_value | `Σ daily.total_value + Σ daily.total_redemption` |
| global_principal | `Σ daily.principal_total` |
| **global_pnl** | `global_value - global_principal` |
| global_return | `global_pnl / global_principal × 100%` |

### 6.3 cash_bucket 模式（未来阶段）

赎回回笼的现金已体现在"现金桶产品"的 total_value 中，不能再加 total_redemption（否则双计）：

| 汇总字段 | 公式 |
|----------|------|
| global_value | `Σ daily.total_value` |
| global_principal | `Σ daily.principal_total` |
| **global_pnl** | `global_value - global_principal` |
| global_return | `global_pnl / global_principal × 100%` |

### 6.4 如何升级到 cash_bucket 模式

1. 在 `products.json` 中为现金账户（如 ylb_finance）添加：
   ```json
   {
     "product_code": "ylb_finance",
     "market": "cash_like",
     "is_cash_bucket": true
   }
   ```
2. 赎回时将回笼资金作为"买入"写入该现金桶产品
3. 系统自动切换到 cash_bucket 模式

---

## 7. 示例时间线

以产品 163406（兴全合润）为例，演示字段如何变化。

### D1 晚：初始快照

```
nav=2.0000, shares=0, value=0, cost=0, cash_in_transit=0
principal_total=0, total_redemption=0, total_pnl=0
```

### D2 早：净值更新 nav=2.0100

```
nav=2.0100, shares=0, value=0
pnl_day=0 (因为 shares=0)
```

### D2 午：录入 buy_debit amount=500 fee=0.60

```
# transactions 表新增:
date=D2, action=buy_debit, amount=500, fee=0.60, order_id=20251202120000_163406_001

# orders 表新增:
order_id=20251202120000_163406_001, status=pending, confirm_date=D3

# daily_snapshot 表变化:
shares=0 (份额未变)
value=0
cash_in_transit=499.40 (=500-0.60，在途资金增加)
total_value=499.40
principal_total=500 (累计扣款本金增加)
total_redemption=0
total_pnl=-0.60 (=499.40+0-500，扣了手续费所以为负)
```

**解释**：扣款后，principal_total 增加，cash_in_transit 增加，但份额还没到账，所以 total_pnl 反映手续费损失。

### D3 早：净值更新 nav=2.0200

```
nav=2.0200
pnl_day=0 (因为 shares 仍为 0)
cash_in_transit=499.40 (不变)
```

### D3 午：settle 生成 buy_confirm

```
# 计算份额（精度≥4位）: shares = 499.40 / 2.0100 = 248.4577

# transactions 表新增:
date=D3, action=buy_confirm, shares=248.4577, nav=2.0100, nav_date=D2, fee=0, order_id=20251202120000_163406_001

# orders 表更新:
order_id=20251202120000_163406_001, status=done

# daily_snapshot 表变化:
shares=248.4577 (份额增加)
value=248.4577 × 2.0200 = 501.88
cost=499.40 (净申购额)
cash_in_transit=0 (在途归零)
total_value=501.88
principal_total=500 (不变)
total_redemption=0
total_pnl=1.88 (=501.88+0-500，开始盈利)
```

**解释**：份额确认后，cash_in_transit 归零，value 开始计算，total_pnl 开始反映真实盈亏。注意 total_value 不会"跳变"，因为 cash_in_transit 的减少被 value 的增加抵消（除了净值变化）。

### D4：全部赎回 sell_confirm

假设全部赎回 248.4577 份，当日 nav=2.0500

```
# 计算到账: gross=248.4577×2.0500=509.34, fee=2.55(0.5%), amount=506.79

# transactions 表新增:
date=D4, action=sell_confirm, amount=506.79, shares=248.4577, fee=2.55, nav=2.0500, order_id=20251204120000_163406_001

# daily_snapshot 表变化:
shares=0 (全部赎回)
value=0
cost=0 (全部结转)
cash_in_transit=0
total_value=0
principal_total=500 (不变！卖出不减本金)
total_redemption=506.79 (累计赎回增加)
total_pnl=6.79 (=0+506.79-500=实际利润！)
real_return=1.36% (=6.79/500)
```

**关键验证**：
- 全部赎回后 `total_pnl = 6.79`，正是实际利润（到账506.79 - 投入500）
- **不会**变成 `-500`（反直觉的巨亏）
- `real_return = 1.36%` 直观反映投资回报率

---

## 8. 常见误解澄清

### Q1: 为什么 total_pnl 改为生命周期口径？

**A**: 之前的公式 `total_pnl = total_value - principal_total` 在全赎回后会变成 `0 - 500 = -500`，非常反直觉。改为 `total_value + total_redemption - principal_total` 后，全赎回后 = `0 + 506.79 - 500 = 6.79`，正是实际利润。

### Q2: 为什么 principal_total 不因卖出减少？

**A**: `principal_total` 是"真实投入的本金累计"，用于回答"我一共往这里投了多少钱"。卖出是取回，不是减少投入。这样设计使得 `total_pnl = total_value + total_redemption - principal_total` 永远成立。

### Q3: buy_confirm 的 fee 为什么固定为 0？

**A**: 申购费在 buy_debit 时已经扣除并记录。buy_confirm 只是确认份额，如果再记录一次 fee 会导致重复扣费。

### Q4: global_mode 的两种模式有什么区别？

**A**: 
- `no_cash_bucket`：赎回的钱"消失"在系统外，需要在汇总时加回 `total_redemption`
- `cash_bucket`：赎回的钱进入"现金桶产品"，已经在某个产品的 `total_value` 中体现，不能再加 `total_redemption`（否则双计）

### Q5: shares 为什么要保留4位小数？

**A**: 高净值基金（如纳斯达克ETF净值约4.0）买入10元只能获得约2.5份，如果只保留2位小数会有明显误差。存储时保留4位，展示时可以格式化为2位。

---

## 附录：数据库表字段对照表

### transactions 表

| 英文 | 中文 |
|------|------|
| date | 日期 |
| product_code | 产品代码 |
| action | 操作类型 |
| amount | 金额 |
| shares | 份额 |
| fee | 手续费 |
| nav | 净值 |
| nav_date | 净值日期 |
| order_id | 订单号 |
| note | 备注 |

### orders 表

| 英文 | 中文 |
|------|------|
| order_id | 订单号 |
| product_code | 产品代码 |
| order_type | 订单类型 |
| amount | 金额 |
| fee | 手续费 |
| shares | 份额 |
| requested_at | 发起时间 |
| trade_date | 交易日 |
| nav_date | 净值日期 |
| confirm_date | 确认日期 |
| holding_days | 持有天数 |
| sell_fee_rate | 赎回费率 |
| status | 状态 |
| note | 备注 |

### ledger 表

| 英文 | 中文 |
|------|------|
| event_time | 发生时间 |
| entry_type | 记账类型 |
| amount | 金额 |
| category_l1 | 一级分类 |
| category_l2 | 二级分类 |
| account_from | 支出账户 |
| account_to | 收入账户 |
| discount | 优惠 |
| reimbursable | 可报销 |
| note | 备注 |

### daily_snapshot 表

| 英文 | 中文 |
|------|------|
| fetch_date | 采集日期 |
| product_code | 产品代码 |
| product_name | 产品名称 |
| category | 分类 |
| nav_date | 净值日期 |
| nav | 净值 |
| shares | 份额 |
| value | 市值 |
| pnl_day | 日变动 |
| cost | 成本 |
| unrealized_pnl | 浮动盈亏 |
| return_rate | 收益率 |
| cash_in_transit | 在途资金 |
| total_value | 总资产 |
| principal_total | 累计投入本金 |
| total_redemption | 累计赎回 |
| total_pnl | 总盈亏 |
| real_return | 真实收益率 |
| fetched_at | 采集时间 |

### daily_balance

| 英文 | 中文 |
|------|------|
| fetch_date | 采集日期 |
| account_id | 账户ID |
| account_name | 账户名称 |
| account_type | 账户类型 |
| balance | 账户余额 |
| related_product | 关联产品 |
| product_value | 产品市值 |
| diff | 差异 |
| note | 备注 |

---

## 6. nav 表（净值历史表）

### 6.1 数据库表字段（固定）

```
id, product_id, product_code, nav_date, nav, acc_nav, daily_return, dividend, fetched_at
```

### 6.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| product_id | 产品ID（外键） | bigint |
| product_code | 产品代码 | string |
| nav_date | 净值日期 | YYYY-MM-DD |
| nav | 单位净值 | Decimal(20,6) |
| acc_nav | 累计净值 | Decimal(20,6)（可空） |
| daily_return | 日收益率 | Decimal(10,6)（可空） |
| dividend | 分红 | Decimal(20,2)（可空） |
| fetched_at | 采集时间 | timestamp |

### 6.3 唯一键

- `(product_code, nav_date)` - 同一产品同一日期只能有一条记录

---

## 7. products 表（产品配置表）

### 7.1 数据库表字段（固定）

```
id, code, channel, market, asset_type, currency, is_qdii, track_index, product_name, category, source, buy_fee_rate, sell_fee_rate, buy_confirm_offset, sell_confirm_offset, cutoff_time, product_code, note, is_active, created_at, updated_at
```

### 7.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| code | 交易代码/基金代码 | varchar(32) |
| channel | 渠道 | enum('EXCHANGE','OTC') |
| market | 市场类型 | enum('SH','SZ','NA') |
| asset_type | 资产类型 | enum('ETF','LOF','FUND','MMF','BANK_WM_NAV','BANK_WM_BOX') |
| currency | 货币类型 | enum('CNY','USD','HKD') |
| is_qdii | 是否QDII | tinyint(1) |
| track_index | 跟踪指数 | varchar(64)（可空） |
| product_name | 产品名称 | varchar(128) |
| category | 分类 | varchar(20)（fund/bank） |
| source | 数据源 | varchar(32)（fund/cmbc/akshare） |
| buy_fee_rate | 申购费率 | decimal(10,6) |
| sell_fee_rate | 赎回费率 | decimal(10,6) |
| buy_confirm_offset | 买入确认延迟交易日数 | int |
| sell_confirm_offset | 赎回确认延迟交易日数 | int |
| cutoff_time | 交易截止时间 | varchar(10)（如 '15:00'） |
| product_code | 产品代码（兼容字段，等于code） | varchar(32) |
| note | 备注 | varchar(500)（可空） |
| is_active | 是否启用 | tinyint(1) |

### 7.3 唯一键

- `(code, channel, market)` - 同一代码可以同时存在场外和场内版本

---

## 8. accounts 表（账户配置表）

### 8.1 数据库表字段（固定）

```
id, account_code, account_id, account_name, account_type, parent_account_id, product_id, currency, note, is_active, created_at, updated_at
```

### 8.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| account_code | 账户代码（唯一标识） | varchar(64) |
| account_id | 账户ID（兼容字段，等于account_code） | varchar(64) |
| account_name | 账户名称 | varchar(128) |
| account_type | 账户类型 | enum('CASH','BUCKET','FUND_MAPPED','PRODUCT_SUB','FUND_TOTAL','SUMMARY') |
| parent_account_id | 父账户ID | bigint（可空） |
| product_id | 账户背后绑定的产品ID | bigint（可空） |
| currency | 货币类型 | enum('CNY','USD','HKD') |
| note | 备注 | varchar(500)（可空） |
| is_active | 是否启用 | tinyint(1) |

---

## 9. account_groups 表（账户组配置表）

### 9.1 数据库表字段（固定）

```
id, group_code, group_name, linked_product_id, profit_account_id, created_at, updated_at
```

### 9.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| group_code | 组代码（如 wenlibao/ylb） | varchar(64) |
| group_name | 组名称 | varchar(100) |
| linked_product_id | 关联产品ID（如稳利宝） | bigint（可空） |
| profit_account_id | 收益归属账户ID | bigint（可空） |

### 9.3 唯一键

- `group_code` - 组代码唯一

---

## 10. categories 表（分类配置表）

### 10.1 数据库表字段（固定）

```
id, entry_type, category_l1, category_l2, display_order, is_active, created_at, updated_at
```

### 10.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| entry_type | 记账类型 | enum('expense','income') |
| category_l1 | 一级分类 | varchar(50) |
| category_l2 | 二级分类 | varchar(50)（可空） |
| display_order | 显示顺序 | int |
| is_active | 是否启用 | tinyint(1) |

### 10.3 唯一键

- `(entry_type, category_l1, category_l2)` - 同一类型下分类唯一

---

## 11. account_pool_rules 表（资金池分配规则表）

### 11.1 数据库表字段（固定）

```
id, from_account_id, to_product_id, ratio, min_amount, round_step, is_active, created_at, updated_at
```

### 11.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| from_account_id | 来源账户ID（如余利宝理财金） | bigint |
| to_product_id | 目标产品ID（基金/ETF/LOF） | bigint |
| ratio | 分配比例（如 0.35 表示 35%） | decimal(10,6) |
| min_amount | 最小分配金额 | decimal(18,2) |
| round_step | 取整粒度（如 1/10/100） | decimal(18,2) |
| is_active | 是否启用 | tinyint(1) |

### 11.3 唯一键

- `(from_account_id, to_product_id)` - 同一账户到同一产品的规则唯一

---

## 12. dca_plan 表（定投计划表）

### 12.1 数据库表字段（固定）

```
id, product_id, from_account_id, weekday, amount, enabled, created_at, updated_at
```

### 12.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| product_id | 产品ID | bigint |
| from_account_id | 来源账户ID | bigint |
| weekday | 定投日期（星期几） | enum('MON','TUE','WED','THU','FRI','SAT','SUN') |
| amount | 定投金额 | decimal(18,2) |
| enabled | 是否启用 | tinyint(1) |

---

## 13. task_dca 表（定投任务表）

### 13.1 数据库表字段（固定）

```
id, plan_id, task_date, product_id, from_account_id, planned_amount, premium_rate, executed_amount, pending_amount, status, reason, created_at, updated_at
```

### 13.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| plan_id | 关联计划ID（可为空，手动任务） | bigint（可空） |
| task_date | 任务日期 | date |
| product_id | 产品ID | bigint |
| from_account_id | 来源账户ID | bigint |
| planned_amount | 计划金额 | decimal(18,2) |
| premium_rate | 溢价率（QDII） | decimal(10,6)（可空） |
| executed_amount | 执行金额（实际买入） | decimal(18,2) |
| pending_amount | 待买入金额（溢价刹车扣留） | decimal(18,2) |
| status | 对账状态 | enum('PENDING','MATCH','PARTIAL','MISS') |
| reason | 原因说明 | varchar(255)（可空） |

### 13.3 唯一键

- `(task_date, product_id, from_account_id)` - 同一日期同一产品同一账户的任务唯一

---

## 14. pending_buy_pool 表（待买入池表）

### 14.1 数据库表字段（固定）

```
id, product_id, from_account_id, pending_amount, reason, created_at, updated_at
```

### 14.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| product_id | 产品ID | bigint |
| from_account_id | 来源账户ID | bigint |
| pending_amount | 待买入金额（累加） | decimal(18,2) |
| reason | 扣留原因（溢价刹车等） | varchar(255)（可空） |

### 14.3 唯一键

- `(product_id, from_account_id)` - 同一产品同一账户的待买入池唯一

---

## 15. job_config 表（任务调度配置表）

### 15.1 数据库表字段（固定）

```
id, job_code, cron_expr, enabled, last_run_at, last_status, last_message, created_at, updated_at
```

### 15.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| job_code | 任务代码 | varchar(64) |
| cron_expr | Cron表达式 | varchar(64) |
| enabled | 是否启用 | tinyint(1) |
| last_run_at | 最后执行时间 | datetime（可空） |
| last_status | 最后执行状态 | enum('OK','FAIL')（可空） |
| last_message | 最后执行消息 | varchar(255)（可空） |

### 15.3 唯一键

- `job_code` - 任务代码唯一

---

## 16. market_quote_rt 表（场内实时行情表）

### 16.1 数据库表字段（固定）

```
id, product_id, quote_time, price, prev_close, pct_chg, volume, amount, iopv, premium_rate, open_price, high_price, low_price, turnover_rate, amplitude, source, created_at
```

### 16.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| product_id | 产品ID | bigint |
| quote_time | 行情时间（精确到秒） | datetime |
| price | 当前价格 | decimal(18,6) |
| prev_close | 昨收价 | decimal(18,6)（可空） |
| pct_chg | 涨跌幅（%） | decimal(10,6)（可空） |
| volume | 成交量 | decimal(20,2)（可空） |
| amount | 成交额 | decimal(20,2)（可空） |
| iopv | IOPV实时估值（基金份额参考净值） | decimal(18,6)（可空） |
| premium_rate | 溢价率（小数，如 0.0123 表示 1.23%） | decimal(10,6)（可空） |
| open_price | 开盘价 | decimal(18,6)（可空） |
| high_price | 最高价 | decimal(18,6)（可空） |
| low_price | 最低价 | decimal(18,6)（可空） |
| turnover_rate | 换手率（小数） | decimal(10,6)（可空） |
| amplitude | 振幅（小数） | decimal(10,6)（可空） |
| source | 数据源 | varchar(32)（默认 'AKSHARE'） |

### 16.3 唯一键

- `(product_id, quote_time, source)` - 同一产品同一时间同一数据源的行情唯一

---

## 17. market_bar_d 表（场内日K线表）

### 17.1 数据库表字段（固定）

```
id, product_id, trade_date, open_price, high_price, low_price, close_price, volume, amount, prev_close, source, created_at
```

### 17.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| product_id | 产品ID | bigint |
| trade_date | 交易日期 | date |
| open_price | 开盘价 | decimal(18,6)（可空） |
| high_price | 最高价 | decimal(18,6)（可空） |
| low_price | 最低价 | decimal(18,6)（可空） |
| close_price | 收盘价 | decimal(18,6) |
| volume | 成交量 | decimal(20,2)（可空） |
| amount | 成交额 | decimal(20,2)（可空） |
| prev_close | 昨收价 | decimal(18,6)（可空） |
| source | 数据源 | varchar(32)（默认 'AKSHARE'） |

### 17.3 唯一键

- `(product_id, trade_date, source)` - 同一产品同一日期同一数据源的K线唯一

---

## 18. qdii_premium_rt 表（QDII溢价率表）

### 18.1 数据库表字段（固定）

```
id, product_id, quote_time, iopv, premium_rate, source, created_at
```

### 18.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| product_id | 产品ID | bigint |
| quote_time | 行情时间 | datetime |
| iopv | IOPV（基金份额参考净值） | decimal(18,6)（可空） |
| premium_rate | 溢价率（如 0.0123 表示 1.23%） | decimal(10,6) |
| source | 数据源 | varchar(32)（默认 'AKSHARE'） |

### 18.3 唯一键

- `(product_id, quote_time, source)` - 同一产品同一时间同一数据源的溢价率唯一

---

## 19. trade_fills 表（场内成交流水表）

### 19.1 数据库表字段（固定）

```
id, trade_date, trade_time, product_id, side, qty, price, amount, fee, tax, other_fee, broker_order_id, remark, source, created_at
```

### 19.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| trade_date | 成交日期 | date |
| trade_time | 成交时间（精确到秒） | datetime |
| product_id | 产品ID | bigint |
| side | 买卖方向 | enum('BUY','SELL') |
| qty | 成交数量（份额/股数） | decimal(18,6) |
| price | 成交价 | decimal(18,6) |
| amount | 成交金额（含费） | decimal(18,2) |
| fee | 手续费（佣金等） | decimal(18,2) |
| tax | 印花税（ETF通常0） | decimal(18,2) |
| other_fee | 其他费用 | decimal(18,2) |
| broker_order_id | 券商订单号（用于去重） | varchar(64)（可空） |
| remark | 备注 | varchar(255)（可空） |
| source | 数据来源 | enum('IMPORT','MANUAL') |

### 19.3 唯一键

- `(source, broker_order_id)` - 同一数据源同一券商订单号唯一

---

## 20. product_nav_range 表（产品净值范围表）

### 20.1 数据库表字段（固定）

```
id, product_code, product_name, earliest_nav_date, latest_nav_date, record_count, updated_at, created_at
```

### 20.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| product_code | 产品代码 | varchar(32) |
| product_name | 产品名称 | varchar(128)（可空） |
| earliest_nav_date | 最早净值日期 | date（可空） |
| latest_nav_date | 最新净值日期 | date（可空） |
| record_count | 记录数 | int |
| updated_at | 更新时间 | timestamp |
| created_at | 创建时间 | timestamp |

### 20.3 唯一键

- `product_code` - 产品代码唯一

---

## 21. strategy_config 表（策略配置表）

### 21.1 数据库表字段（固定）

```
id, strategy_key, strategy_version, param_set_id, param_json, is_active, created_at, updated_at
```

### 21.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| strategy_key | 策略标识 | varchar(64) |
| strategy_version | 策略版本 | varchar(32)（默认 'default'） |
| param_set_id | 参数组合ID | varchar(64) |
| param_json | 参数JSON | text |
| is_active | 是否启用 | tinyint(1) |

### 21.3 唯一键

- `(strategy_key, strategy_version, param_set_id)` - 策略参数组合唯一

---

## 22. backtest_summary 表（回测汇总表）

### 22.1 数据库表字段（固定）

```
id, product_id, strategy_key, strategy_version, param_set_id, start_date, end_date, initial_cash, final_value, total_return, annual_return, max_drawdown, trade_count, total_fees, fee_ratio, wait_pool_ratio, created_at
```

### 22.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| product_id | 产品ID | bigint |
| strategy_key | 策略标识 | varchar(64) |
| strategy_version | 策略版本 | varchar(32) |
| param_set_id | 参数组合ID | varchar(64) |
| start_date | 回测开始日期 | date |
| end_date | 回测结束日期 | date |
| initial_cash | 初始现金 | decimal(20,2) |
| final_value | 最终总资产 | decimal(20,2) |
| total_return | 总收益率 | decimal(10,6) |
| annual_return | 年化收益率 | decimal(10,6) |
| max_drawdown | 最大回撤 | decimal(10,6) |
| trade_count | 成交次数 | int |
| total_fees | 手续费总额 | decimal(20,2) |
| fee_ratio | 手续费占收益比例 | decimal(10,6) |
| wait_pool_ratio | wait_pool滞留比例 | decimal(10,6) |

---

## 23. backtest_daily 表（回测每日数据表）

### 23.1 数据库表字段（固定）

```
id, summary_id, trade_date, nav, cash_pool, wait_pool, holdings_value, total_value, drawdown, fee_cum, created_at
```

### 23.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| summary_id | 关联backtest_summary.id | bigint |
| trade_date | 交易日期 | date |
| nav | 净值/收盘价 | decimal(18,6) |
| cash_pool | 可用现金池 | decimal(20,2) |
| wait_pool | 等待池 | decimal(20,2) |
| holdings_value | 持仓市值 | decimal(20,2) |
| total_value | 总资产 | decimal(20,2) |
| drawdown | 当前回撤 | decimal(10,6) |
| fee_cum | 累计手续费 | decimal(20,2) |

### 23.3 唯一键

- `(summary_id, trade_date)` - 同一回测同一日期的数据唯一

---

## 24. backtest_trades 表（回测成交表）

### 24.1 数据库表字段（固定）

```
id, summary_id, trade_date, side, amount, price, shares, fee, reasons, created_at
```

### 24.2 字段定义

| 字段 | 定义 | 数据类型 |
|------|------|----------|
| summary_id | 关联backtest_summary.id | bigint |
| trade_date | 成交日期 | date |
| side | 买卖方向 | enum('BUY','SELL') |
| amount | 成交金额 | decimal(20,2) |
| price | 成交价格 | decimal(18,6) |
| shares | 成交份额 | decimal(20,6) |
| fee | 手续费 | decimal(20,2) |
| reasons | 成交原因（JSON数组） | text（可空） |

---
