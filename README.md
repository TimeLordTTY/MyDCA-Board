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
create_daily_snapshot()     # 生成快照
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
│   └── self_test.py                 # 自测脚本
│
├── data/                            # 数据目录
│   ├── nav/                         # 净值CSV文件
│   │   └── {code}_{name}.csv        # 格式：产品代码_产品名称.csv
│   └── snapshots/                   # 快照目录
│       └── daily.csv                # 日快照
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
    ├─ 获取持仓份额 (holdings.json)
    │
    ├─ 计算市值 (value = shares × NAV)
    │
    ├─ 计算PNL (当前value - 上次value)
    │
    └─ 写入 daily.csv
         ├─ 按 (snapshot_date, product_code) 去重
         └─ 记录: 日期、产品、净值、份额、市值、盈亏
```

**目的**：汇总持仓情况，追踪盈亏变化

---

### 4. 日志输出阶段

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

### holdings.json（持仓配置）

```json
[
    {
        "products_id": "163406",           // 必需：产品代码（必须在products.json中存在）
        "amount": 1000                     // 必需：持仓份额
    }
]
```

---

## 🚀 使用方法

### 日常运行
```bash
# 采集所有产品净值并生成快照
python scripts/run_daily.py
```

### 自测验证
```bash
# 运行自动化测试
python scripts/self_test.py
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
snapshot_date,product_code,product_name,nav,shares,value,pnl,fetched_at
2023-12-14,163406,兴全合润混合,2.1234,1000,2123.40,0,2023-12-15 10:30:00
2023-12-15,163406,兴全合润混合,2.1345,1000,2134.50,11.10,2023-12-16 10:30:00
```

**说明**：
- 所有产品汇总在一个文件
- 记录每日持仓情况
- pnl = 当前value - 上次value

---

## ⚠️ 重要约束

### 适配器约束
1. **返回类型**：必须是 `List[Dict]`，即使只有一条也用列表
2. **必需字段**：`PRODUCT_CODE`, `ISS_DATE`, `NAV`, `fetched_at`
3. **日期格式**：`ISS_DATE` 必须是 `YYYY-MM-DD`

### 配置约束
1. **products.json**：必须包含 `id`, `name`, `source`
2. **holdings.json**：`products_id` 必须存在于 products.json 中
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
2. 检查 holdings.json 中的 products_id 是否在 products.json 中
3. 检查 source 是否有对应的适配器

### 问题3：数据格式错误？
1. 查看错误日志中的具体字段
2. 检查适配器的 `_normalize_nav_record()` 函数
3. 确保返回的数据包含所有必需字段

### 问题4：重复写入同一天数据？
1. 检查 `ISS_DATE` 格式是否正确（YYYY-MM-DD）
2. 查看 storage_csv.py 的去重逻辑
3. 运行 `python scripts/self_test.py` 验证

---

## 🧪 自测说明

运行自测脚本验证系统可控性：

```bash
python scripts/self_test.py
```

**测试内容**：
1. ✅ 幂等性测试：连续运行2次，验证不会重复写入
2. ✅ 配置校验：故意写错配置，验证能正确报错退出
3. ✅ 字段完整性：删除必需字段，验证能检测到

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

## 📞 快速参考

### 数据流向
```
config/*.json → validator → adaptor → storage_csv → snapshot → 日志输出
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

**最后更新**: 2024-12-16  
**预计学习时间**: 30分钟掌握核心，1小时完全掌控
