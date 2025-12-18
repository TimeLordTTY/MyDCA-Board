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
│   ├── holdings.json                # 持仓配置（基础份额）
│   └── nav_range.json               # 净值范围配置（自动更新）
│
├── src/                             # 源代码
│   ├── nav_collector.py             # 【核心】主控协调器
│   ├── validator.py                 # 【核心】数据校验器
│   ├── holdings_calculator.py       # 【核心】持仓与成本计算器（支持扣款/确认分离）
│   ├── nav_range_manager.py         # 净值范围管理模块
│   ├── portfolio_summary.py         # 资产汇总模块
│   │
│   ├── adaptor/                     # 适配器目录
│   │   ├── __init__.py              
│   │   ├── cmbc_client.py           # 民生银行适配器
│   │   └── fund_client.py           # 东方财富基金适配器
│   │
│   ├── storage_csv.py               # CSV存储模块
│   ├── snapshot.py                  # 快照生成模块
│   ├── config_loader.py             # 配置加载模块
│   │
│   └── backtest/                    # 【策略回测引擎】
│       ├── __init__.py
│       ├── engine/                  # 回测核心引擎
│       │   ├── backtester.py        # 回测主循环
│       │   ├── data_feed.py         # 行情数据源
│       │   ├── portfolio.py         # 投资组合管理
│       │   └── types.py             # 数据结构定义
│       ├── strategies/              # 策略库
│       │   ├── base.py              # 策略基类
│       │   ├── registry.py          # 策略注册表
│       │   ├── pure_sip.py          # 纯定投策略
│       │   ├── profit_recycle_v11.py # 利润回收策略 v11
│       │   ├── profit_recycle.py    # 利润回收策略 v10
│       │   └── ma_enhanced.py       # MA250均线增强策略
│       └── utils/                   # 工具模块
│           └── nav_loader.py        # 净值数据加载器
│
├── scripts/                         # 脚本目录
│   ├── run_daily.py                 # 日常运行入口
│   ├── run_backtest.py              # 【策略回测入口】
│   ├── verify_debit_confirm.py      # 【扣款/确认分离验证】
│   ├── validate_transactions.py     # 交易流水校验工具
│   ├── export_nav_history.py        # 净值历史导出工具
│   ├── self_test.py                 # 自测脚本
│   └── test_force_rebuild.py        # 覆盖/重建功能测试
│
├── data/                            # 数据目录
│   ├── transactions.csv             # 交易流水（支持扣款/确认分离）
│   ├── nav/                         # 净值CSV文件
│   │   └── {code}_{name}.csv        # 格式：产品代码_产品名称.csv
│   ├── snapshots/                   # 快照目录
│   │   ├── daily.csv                # 日快照（每天每产品一条）
│   │   ├── portfolio_by_nav_date.csv   # 按净值日期汇总
│   │   ├── portfolio_by_fetch_date.csv # 按采集日期汇总
│   │   └── portfolio_by_category.csv   # 按产品类型汇总
│   └── backtest_results/            # 回测结果目录
│       └── backtest_{code}_{strategy}_{timestamp}.csv
│
└── README.md                        # 本文件
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
total_value = value + cash

# 总盈亏
total_pnl = total_value - principal_total

# 日变动（核心：只反映净值涨跌，不受扣款/确认影响）
pnl_day = prev_day_shares × (nav_today - nav_prev)
```

---

## 📋 数据标准

### transactions.csv（交易流水）- 支持扣款/确认分离

交易流水支持**扣款与份额确认分离**，用于准确计算在途资金和持仓成本。

**CSV 格式**：
```csv
date,product_code,action,amount,shares,fee,nav,nav_date,order_id,note
```

**支持的交易类型 (action)**：

| action | 说明 | 必填字段 | 对持仓影响 |
|--------|------|---------|-----------|
| `buy_debit` | 扣款事件（钱已扣，份额未到） | date, product_code, amount, order_id | cash += amount, principal_total += amount |
| `buy_confirm` | 份额确认事件 | date, product_code, shares, nav, nav_date, order_id | shares += shares, cost += matched_debit_amount, cash -= matched_amount |
| `buy` | 兼容旧数据（当天既扣款又确认） | date, product_code, amount, shares, nav, nav_date | shares += shares, cost += amount, principal_total += amount |
| `sell` | 卖出 | date, product_code, shares, nav | shares -= shares, cost按比例减少 |
| `dividend` | 分红 | date, product_code, shares | shares += shares（成本不变） |

**完整示例**：

```csv
date,product_code,action,amount,shares,fee,nav,nav_date,order_id,note
# 12/18扣款100元
2025-12-18,017641,buy_debit,100,,,,,ORD20251218001,每周定投扣款
# 12/19份额确认
2025-12-19,017641,buy_confirm,,63.58,,1.5709,2025-12-15,ORD20251218001,份额到账
# 兼容旧格式（当天扣款+确认）
2025-12-01,017641,buy,99.88,63.58,0.12,1.5709,2025-11-28,,旧格式定投
```

**💡 order_id 的作用**：
- 关联同一笔定投的扣款和确认
- buy_debit 和 buy_confirm 通过 order_id 匹配
- 确认时从对应的扣款记录获取成本金额

**💡 兼容旧数据**：
- 旧的 `buy` 类型继续支持，视为"当天既扣款又确认"
- 如果 buy_confirm 找不到对应的 buy_debit 但有 amount 字段，会降级处理

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
| `shares` | 份额 | **已确认份额**（只有确认后才计入） |
| `value` | 市值 | shares × nav |
| `pnl_day` | 日变动 | **只由净值涨跌贡献**：prev_shares × (nav - prev_nav) |
| `cost` | 成本 | 持仓成本（平均成本法，卖出按比例扣减） |
| `unrealized_pnl` | 浮动盈亏 | value - cost |
| `return_rate` | 收益率 | unrealized_pnl / cost × 100% |
| `cash` | 在途资金 | 扣款已发生但份额未确认的净额 |
| `total_value` | 总资产 | value + cash |
| `principal_total` | 累计投入本金 | 按扣款累计，不因卖出回笼减少 |
| `total_pnl` | 总盈亏 | total_value - principal_total |
| `real_return` | 真实收益率 | total_pnl / principal_total × 100% |
| `fetched_at` | 采集时间 | 毫秒精度 |

**CSV 表头规则**：
- 第 1 行：字段名（机器读）
- 第 2 行：中文表头（人读）
- 第 3 行起：数据

**示例**：
```csv
fetch_date,product_code,product_name,category,nav_date,nav,shares,value,pnl_day,cost,unrealized_pnl,return_rate,cash,total_value,principal_total,total_pnl,real_return,fetched_at
采集日期,产品代码,产品名称,分类,净值日期,净值,份额,市值,日变动,成本,浮动盈亏,收益率,在途资金,总资产,累计投入本金,总盈亏,真实收益率,采集时间
2025-12-19,017641,摩根标普500指数A,fund,2025-12-18,1.5800,1063.58,1680.46,30.00,1700.00,-19.54,-1.15%,0.00,1680.46,1700.00,-19.54,-1.15%,2025-12-19 12:00:00.000
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
        "category": "fund"                     // 产品分类 (fund/bank)
    }
]
```

### holdings.json（持仓配置 - 静态份额）

```json
[
    {
        "product_code": "163406",
        "amount": 0                           // 设为0使用纯交易流水模式
    }
]
```

---

## 🚀 使用方法

### 日常运行
```bash
python scripts/run_daily.py
```

**智能去重策略**：
- 同一 (fetch_date, product_code) 只保留一条
- 同一天多次运行会覆盖（保持最新状态）
- pnl_day 只反映净值变化，不受扣款/确认影响

### 验证扣款/确认分离功能
```bash
python scripts/verify_debit_confirm.py
```

验证要点：
- ✅ 扣款后 cash 增加、shares 不变
- ✅ 份额到账后 shares 增加、cash 减少
- ✅ pnl_day 不因扣款/确认而跳变
- ✅ 同日多次统计只保留一条

### 重建模式（修复历史数据）
```bash
python scripts/run_daily.py --rebuild-from 2025-12-01
```

### 策略回测
```bash
# 列出可用产品和策略
python scripts/run_backtest.py --list
python scripts/run_backtest.py --strategies

# 运行回测
python scripts/run_backtest.py --product 163406 --strategy pure_sip
```

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
        'PRODUCT_CODE': product_code,
        'ISS_DATE': '2023-12-15',
        'NAV': '1.2345',
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

## 🎓 关键设计理念

1. **扣款/确认分离**：真实反映资金流动，总资产不因结算延迟而凭空减少
2. **pnl_day 只反映净值变化**：`prev_shares × (nav - prev_nav)`，不受扣款/确认影响
3. **同日覆盖**：同一 (fetch_date, product_code) 只保留一条，保持数据干净
4. **字段零冗余**：每个字段都有明确用途，不引入无用字段
5. **向后兼容**：旧的 `buy` 类型继续支持

---

## 🏷️ 字段命名规范

系统使用统一的字段命名，同一语义只有一个字段名：

| 字段 | 说明 | 注意 |
|------|------|------|
| `shares` | 已确认份额 | 不使用 units |
| `value` | 持仓市值 | 不使用 market_value |
| `cost` | 持仓成本 | 不使用 total_cost |
| `pnl_day` | 日变动 | 不使用 pnl（已废弃） |
| `cash` | 在途资金 | 不使用 cash_in_transit |
| `return_rate` | 收益率 | 不使用 unrealized_pnl_pct |

---

**最后更新**: 2025-12-18  
**预计学习时间**: 30分钟掌握核心，1小时完全掌控
