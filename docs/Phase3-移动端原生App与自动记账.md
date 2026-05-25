# Phase 3 开发进度总结（移动端原生 App 与自动记账）

## 阶段定位

- Phase3 是在 Phase2 已完成 PC 端、Mobile H5、行情、指标、定时任务高优先级闭环之后插入的新阶段。
- Phase3 的目标是用 Android 原生 App 替代原 Mobile H5，未来可评估鸿蒙适配。
- 原生 App 负责全部移动端功能：看板、快速录入、草稿箱、待结算、持仓、流水、设置。
- 原生 App 额外负责日常消费、收入、转账等移动端快速记账入口。
- 服务端数据库仍是唯一真相源，App 本地数据只作为缓存、重试队列和临时展示状态。
- Phase2 的 `web/mobile-app` 为 Mobile H5 历史交付，可保留为兼容入口或历史参考；Phase3 起不再作为长期主移动端路线。

## 核心业务目标

- 用户在手机端记录消费、收入、转账、投资相关流水时，可以进入预填确认页。
- 如果用户点击“确认入账”，App 调用服务器接口，直接写入财富中枢正式账本。
- 如果用户点击“存草稿”，App 调用服务器接口，写入服务端草稿表。
- 草稿后续可以在 App 草稿箱中继续编辑、确认、忽略、批量处理。
- 不把导入支付宝、微信 CSV 作为主路径，因为导出格式复杂且不适配系统现有账本模型。

## A+B 双路径

### A. 确认直落账

- App 调用服务端快速入账接口。
- 服务端复用现有 `QuickEntryService`、`LedgerService`、`ledger_txn`、`ledger_posting`。
- 成功后返回正式 `txn_id`。
- PC 端和 App 端都能立即查询到该流水。

### B. 服务端草稿入库

- App 调用草稿创建接口。
- 服务端写入 `draft_ledger_entry`。
- 草稿不是手机本地临时数据，而是服务器数据。
- 换手机、重新登录后仍能看到草稿。
- 草稿确认后生成正式 `ledger_txn`，并记录 `confirmed_txn_id`。

## 服务端新增表

### draft_ledger_entry

建议字段：

- `id`
- `user_id` / `family_id`
- `status`：`DRAFT` / `CONFIRMED` / `IGNORED`
- `channel`：`ALIPAY` / `WECHAT` / `BANK` / `CASH` / `MANUAL` / `OTHER`
- `direction`：`EXPENSE` / `INCOME` / `TRANSFER`
- `amount`
- `currency`
- `occurred_at`
- `captured_at`
- `merchant_raw`
- `merchant_norm`
- `account_id`
- `category_id`
- `note`
- `pay_method`
- `external_ref`
- `dedup_key`
- `raw_payload`
- `confirmed_txn_id`
- `created_at`
- `updated_at`

## 服务端新增接口

- `POST /api/v2/mobile/drafts`
- `GET /api/v2/mobile/drafts`
- `GET /api/v2/mobile/drafts/{id}`
- `PUT /api/v2/mobile/drafts/{id}`
- `POST /api/v2/mobile/drafts/{id}/confirm`
- `POST /api/v2/mobile/drafts/{id}/ignore`
- `POST /api/v2/mobile/drafts/batch-confirm`
- `POST /api/v2/mobile/ledger/quick-entry`

## 幂等与去重

- `external_ref` 优先。
- 若 `external_ref` 为空，则使用 `dedup_key`。
- `dedup_key` 可由 `channel + amount + merchant_norm + occurred_at` 时间窗口生成。
- 服务端必须保证重复提交不会重复写入正式账本或草稿。
- 批量确认时，部分失败不能影响其他成功项，失败项保留草稿状态并返回原因。

## App 页面

### 1. 首页看板

- 总资产
- 净资产
- 可用资金
- 今日待处理：草稿数、待结算数、逾期结算数
- 核心持仓摘要

### 2. 快速录入

- 消费
- 收入
- 转账
- 投资买入、卖出、申购、赎回补录入口

### 3. 预填确认页

- 金额、时间、账户、分类、商户、备注
- 按钮：确认入账、存草稿、忽略

### 4. 草稿箱

- 草稿列表
- 编辑草稿
- 单笔确认
- 批量确认
- 批量忽略
- 分类规则学习

### 5. 待结算

- 复用现有待结算接口
- 支持确认结算

### 6. 持仓

- 持仓列表
- 产品详情

### 7. 流水

- 流水列表
- 流水详情

### 8. 设置

- 登录信息
- 服务器地址
- Token
- 默认账户
- 默认分类
- 分类规则
- 草稿同步设置

## 移动端技术选型

- Android 原生：Kotlin + Jetpack Compose。
- 本地存储只做缓存和失败重试队列，不作为最终真相源。
- 后端 API 是最终数据来源。
- 鸿蒙适配作为后续评估项，不在 M1 强制实现。

## 里程碑

### M1

- 原生 App 项目搭建
- 登录与 Token 管理
- 首页看板
- 快速录入
- 预填确认页
- 确认直落账
- 草稿入库
- 草稿箱基础列表

### M2

- 草稿编辑
- 单笔确认
- 批量确认
- 批量忽略
- 分类规则学习
- 失败重试队列

### M3

- 替代 Mobile H5 的全部移动端功能
- 待结算确认
- 持仓查看
- 流水查询
- 产品、账户轻量管理
- 冻结或下线 `web/mobile-app` 的发布链路

### M4

- 鸿蒙兼容性评估
- 通知、快捷入口增强
- 更完善的移动端统计与复盘入口

## 测试用例

- 确认入账成功后正式流水可查
- 存草稿后服务端草稿可查
- 草稿确认后生成正式流水并更新状态
- 重复提交不会重复入账
- 批量确认部分失败时成功项与失败项状态正确
- 网络失败时 App 可重试
- 默认账户、分类缺失时提示用户补全
