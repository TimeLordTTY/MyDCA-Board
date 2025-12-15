# 民生理财净值采集最小闭环说明

## 📋 架构概览

```
采集净值 → CSV落库 → 生成快照
   ↓          ↓          ↓
cmbc_client  nav/      snapshots/
            *.csv     daily.csv
```

## 📁 新增/修改文件

### 1. **src/config_loader.py** (新增)
**职责**: 加载配置文件（products.json、holdings.json）
- `load_products()`: 加载产品配置
- `load_holdings()`: 加载持仓配置
- `get_holdings_map()`: 返回 {产品代码: 份额} 映射

### 2. **src/storage_csv.py** (新增)
**职责**: CSV存储逻辑，按ISS_DATE去重
- `save_nav_records(product_code, nav_list)`: 保存净值到 `data/nav/{product_code}.csv`
- 自动检测并跳过已存在日期的记录
- 不存在文件时自动创建并写入表头
- 追加 `fetched_at` 字段记录采集时间

### 3. **src/snapshot.py** (新增)
**职责**: 生成日快照文件
- `create_daily_snapshot(nav_records, holdings_map)`: 生成 `data/snapshots/daily.csv`
- 字段: snapshot_date, product_code, nav, shares, value, pnl, fetched_at
- pnl 计算逻辑: value - 上一条同产品value（无则为0）
- 按 (snapshot_date, product_code) 去重

### 4. **src/nav_collector.py** (新增)
**职责**: 净值采集协调器，串联整个流程
- `collect_and_store()`: 主函数
  1. 加载配置
  2. 初始化民生理财会话
  3. 遍历产品采集净值并存储
  4. 生成快照

### 5. **scripts/run_daily.py** (修改)
**职责**: 日常任务入口脚本
- 添加 src 到 Python 路径
- 调用 `nav_collector.collect_and_store()`

## 🚀 运行命令

### 方式1: 直接运行入口脚本（推荐）
```bash
python scripts/run_daily.py
```

### 方式2: 在 src 目录运行
```bash
cd src
python nav_collector.py
```

## 📊 输出文件示例

### data/nav/FBAE41126E.csv
```csv
ISS_DATE,NAV,TOT_NAV,INCOME,WEEK_CLIENTRATE,fetched_at
20231215,1.0234,1.0456,0.0012,0.0234,2023-12-15 10:30:00
20231216,1.0245,1.0467,0.0011,0.0233,2023-12-16 10:30:00
```

### data/snapshots/daily.csv
```csv
snapshot_date,product_code,nav,shares,value,pnl,fetched_at
20231215,FBAE41126E,1.0234,1,1.0234,0,2023-12-15 10:30:00
20231216,FBAE41126E,1.0245,1,1.0245,0.0011,2023-12-16 10:30:00
```

## ✅ 去重机制

1. **净值去重**: 按 `ISS_DATE` 去重，同一天的净值不会重复写入
2. **快照去重**: 按 `(snapshot_date, product_code)` 去重，同一天同产品的快照不会重复写入
3. **幂等性保证**: 多次运行同一天的采集任务，不会产生重复数据

## 📦 依赖

仅使用 Python 标准库：
- `csv`: CSV文件读写
- `pathlib`: 路径操作
- `datetime`: 时间处理
- `logging`: 日志记录
- `requests`: HTTP请求（已在 cmbc_client 中使用）
- `decimal`: 精确数值计算

## 🔄 数据流

```
1. run_daily.py 启动
   ↓
2. nav_collector 加载配置
   ↓
3. 调用 cmbc_client.query_latest_nav() 获取净值
   ↓
4. storage_csv 检查去重 → 追加到 nav/*.csv
   ↓
5. snapshot 计算 value/pnl → 追加到 snapshots/daily.csv
   ↓
6. 完成（输出日志）
```

## 📝 日志示例

```
2023-12-15 10:30:00 - INFO - 加载配置...
2023-12-15 10:30:01 - INFO - 初始化民生理财会话...
2023-12-15 10:30:02 - INFO - 采集产品 民生理财贵竹固收增利周周盈7天持有期26号理财产品E (FBAE41126E) 净值...
2023-12-15 10:30:05 - INFO - 产品 FBAE41126E 新增 1 条净值记录
2023-12-15 10:30:05 - INFO - 生成日快照...
2023-12-15 10:30:05 - INFO - 新增 1 条快照记录
2023-12-15 10:30:05 - INFO - 采集任务完成
```

## ⚠️ 注意事项

1. **首次运行**: 会自动创建 `data/nav/` 和 `data/snapshots/` 目录及文件
2. **重复运行**: 同一天重复运行不会产生重复数据（去重机制）
3. **错误处理**: 单个产品采集失败不会影响其他产品
4. **时间回溯**: cmbc_client 会自动回溯15天查找最新净值

