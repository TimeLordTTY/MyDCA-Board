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

### 确认入账映射规则

#### EXPENSE 支出

- 借：`EXPENSE` 虚拟账户。
- 贷：用户选择的现金叶子账户。
- 复用 `QuickEntryService` 的支出逻辑，保证分录方向、余额更新、虚拟账户获取方式与 PC 端快速录入一致。
- 草稿确认时，如果 `account_id` 缺失，应要求用户补全现金叶子账户后才能入账。

#### INCOME 收入

- 借：现金叶子账户。
- 贷：`INCOME` 虚拟账户。
- 复用 `QuickEntryService` 的收入逻辑，保持收入类虚拟账户口径一致。
- 草稿确认时，如果账户或金额缺失，应保持 `DRAFT` 状态并返回失败原因。

#### TRANSFER 转账

- 从一个现金叶子账户转出，到另一个现金叶子账户转入。
- 转账不计入生活收支统计，除非统计接口明确传入 `includeTransfer=true`。
- 转账草稿至少需要转出账户、转入账户、金额、发生时间；若当前 `draft_ledger_entry.account_id` 只表达主账户，后续 DDL 或 `raw_payload` 解析规则需补充目标账户字段。

#### 投资买入、卖出、申购、赎回

- App 只提供投资补录入口，不把复杂投资行为直接简化成普通生活流水。
- 对于涉及 T+N、手续费、确认净值、确认份额的投资行为，建议优先生成 `Order` / `Settlement` 流程，而不是直接写普通生活流水。
- 这部分与现有订单、结算模块衔接；原生 App 在 M1/M2 可先提供入口和预填确认页，M3 再完善与订单/结算的闭环。

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

### 表定位

- `draft_ledger_entry` 是移动端草稿表。
- 该表只保存“尚未正式入账”的移动端草稿，或“已由草稿确认入账后的草稿记录”。
- 正式账本仍然是 `ledger_txn` + `ledger_posting`。
- 草稿表不是正式账本，不参与资产、余额、收益、持仓计算。
- 只有 `CONFIRMED` 状态的草稿才会关联 `confirmed_txn_id`。
- `DRAFT` 草稿只代表待处理输入，不应出现在 PC 端正式流水、持仓、市值、收益或统计口径中。

### 字段语义

| 字段 | 含义 |
|------|------|
| `id` | 草稿ID，服务端生成，作为草稿箱编辑、确认、忽略的主键 |
| `user_id` / `family_id` | 权限隔离字段；个人草稿按 `user_id` 隔离，家庭视图按 `family_id` 过滤 |
| `status` | 草稿状态：`DRAFT` 待处理、`CONFIRMED` 已确认入账、`IGNORED` 已忽略 |
| `channel` | 来源渠道：`ALIPAY`、`WECHAT`、`BANK`、`CASH`、`MANUAL`、`OTHER` |
| `direction` | 资金方向：`EXPENSE` 支出、`INCOME` 收入、`TRANSFER` 转账 |
| `amount` | 交易金额，使用正数表达金额大小，方向由 `direction` 决定 |
| `currency` | 币种，默认 `CNY`，后续多币种扩展时可复用 |
| `occurred_at` | 交易发生时间，优先使用用户输入或捕获内容中的真实交易时间 |
| `captured_at` | App 捕获或创建草稿的时间，用于追踪录入来源和延迟 |
| `merchant_raw` | 原始商户或对方名称，保留未经清洗的文本 |
| `merchant_norm` | 标准化商户名，用于分类规则、搜索和弱幂等 |
| `account_id` | 预期出入账账户，必须是可记账现金叶子账户；转账场景后续需补充目标账户表达 |
| `category_id` | 交易分类，用于统计、复盘和分类规则学习 |
| `note` | 用户备注或系统生成的辅助说明 |
| `pay_method` | 支付方式，例如余额、银行卡、信用卡、现金等 |
| `external_ref` | 外部流水号、订单号或第三方交易号，作为强幂等依据 |
| `dedup_key` | 弱幂等键，用于没有外部流水号的手工录入或分享文本场景 |
| `raw_payload` | 原始捕获内容、分享文本摘要或手工录入来源摘要，便于追溯和重新解析 |
| `confirmed_txn_id` | 草稿确认后生成的正式 `ledger_txn` ID；仅 `CONFIRMED` 状态允许有值 |
| `created_at` / `updated_at` | 草稿创建与最后更新时间 |

### 状态机

```text
DRAFT -> CONFIRMED
DRAFT -> IGNORED
```

- `DRAFT` 表示待补全、待确认或待忽略的草稿。
- `DRAFT -> CONFIRMED`：通过单笔确认或批量确认生成正式 `ledger_txn` / `ledger_posting` 后流转。
- `DRAFT -> IGNORED`：用户明确忽略后流转。
- `CONFIRMED` 不允许再次确认；重复确认应返回 `already_confirmed` 或 `duplicate`，不得重复生成正式流水。
- `IGNORED` 默认不再参与草稿箱待处理数量，但可以在“已忽略”筛选中查看。
- 如果确认失败，草稿仍保持 `DRAFT`，接口返回失败原因，App 应提示用户补全或修正。
- `CONFIRMED` 和 `IGNORED` 默认禁止编辑，避免历史状态被悄悄改写。

### DDL 建议

以下 DDL 为 Phase3 设计建议，不要求当前立即执行；正式落地时应结合现有命名规范、字符集、审计字段和迁移脚本风格调整。

```sql
CREATE TABLE `draft_ledger_entry` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '草稿ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `family_id` BIGINT NULL COMMENT '家庭ID',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/CONFIRMED/IGNORED',
  `channel` VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源渠道',
  `direction` VARCHAR(32) NOT NULL COMMENT '资金方向：EXPENSE/INCOME/TRANSFER',
  `amount` DECIMAL(18,2) NULL COMMENT '金额',
  `currency` VARCHAR(16) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `occurred_at` DATETIME NULL COMMENT '交易发生时间',
  `captured_at` DATETIME NULL COMMENT 'App捕获/创建时间',
  `merchant_raw` VARCHAR(255) NULL COMMENT '原始商户/对方名称',
  `merchant_norm` VARCHAR(255) NULL COMMENT '标准化商户名',
  `account_id` BIGINT NULL COMMENT '预期出入账账户',
  `category_id` BIGINT NULL COMMENT '分类ID',
  `note` VARCHAR(500) NULL COMMENT '备注',
  `pay_method` VARCHAR(128) NULL COMMENT '支付方式',
  `external_ref` VARCHAR(128) NULL COMMENT '外部流水号/订单号',
  `dedup_key` VARCHAR(255) NULL COMMENT '弱幂等键',
  `raw_payload` TEXT NULL COMMENT '原始捕获内容/分享文本摘要/手工录入来源摘要',
  `confirmed_txn_id` BIGINT NULL COMMENT '确认后生成的正式ledger_txn ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_draft_user_status` (`user_id`, `status`),
  KEY `idx_draft_family_status` (`family_id`, `status`),
  KEY `idx_draft_occurred_at` (`occurred_at`),
  UNIQUE KEY `uk_draft_external_ref` (`channel`, `external_ref`),
  UNIQUE KEY `uk_draft_dedup_key` (`dedup_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='移动端草稿流水表';
```

索引说明：

- `idx_draft_user_status(user_id, status)`：支撑个人草稿箱待处理列表。
- `idx_draft_family_status(family_id, status)`：支撑家庭视图筛选。
- `idx_draft_occurred_at(occurred_at)`：支撑按发生时间范围查询。
- `uk_draft_external_ref(channel, external_ref)`：用于强幂等；由于 `external_ref` 可能为空，需谨慎实现，可通过业务层在非空时校验，或结合数据库可空唯一策略避免多个空值语义混乱。
- `uk_draft_dedup_key(dedup_key)`：用于弱幂等；当 `dedup_key` 为空时同样应在业务层处理，避免误伤正常手工草稿。
- 后续如引入分类规则学习，可增加 `merchant_norm`、`category_id` 相关索引，但 M1 不强制。

## 服务端新增接口

- `POST /api/v2/mobile/drafts`
- `GET /api/v2/mobile/drafts`
- `GET /api/v2/mobile/drafts/{id}`
- `PUT /api/v2/mobile/drafts/{id}`
- `POST /api/v2/mobile/drafts/{id}/confirm`
- `POST /api/v2/mobile/drafts/{id}/ignore`
- `POST /api/v2/mobile/drafts/batch-confirm`
- `POST /api/v2/mobile/ledger/quick-entry`

### POST `/api/v2/mobile/drafts`

用途：创建服务端草稿。

请求字段：

- `channel`
- `direction`
- `amount`
- `currency`
- `occurredAt`
- `capturedAt`
- `merchantRaw`
- `merchantNorm`
- `accountId`
- `categoryId`
- `note`
- `payMethod`
- `externalRef`
- `dedupKey`
- `rawPayload`

返回字段：

- `id`
- `status`
- `createdAt`
- `updatedAt`
- `missingFields`
- `duplicate`
- `existingDraftId`

业务规则：

- 服务端先做幂等检查。
- 如果命中 `channel + external_ref` 或 `dedup_key`，返回已存在草稿，不重复创建。
- 如果金额、方向、账户缺失，允许保存草稿，状态仍为 `DRAFT`，并在 `missingFields` 中标记待补全字段。
- 服务端应补齐 `user_id`、`family_id`、`captured_at`、`created_at`、`updated_at` 等不可由前端信任的字段。
- 创建草稿不生成正式 `ledger_txn`，也不更新账户余额。

### GET `/api/v2/mobile/drafts`

用途：查询草稿箱。

支持参数：

- `status`
- `startDate`
- `endDate`
- `channel`
- `direction`
- `keyword`
- `page`
- `pageSize`

返回字段：

- `items`：草稿列表
- `total`：符合筛选条件的总数
- `pendingCount`：待确认数量，默认统计 `DRAFT`
- `missingFieldCount`：缺失必要字段的草稿数量
- `page`
- `pageSize`

业务规则：

- 默认查询 `DRAFT` 草稿，`IGNORED` 仅在显式传入状态时返回。
- `keyword` 可匹配商户、备注、支付方式、原始摘要。
- 查询必须按当前用户或家庭权限过滤。

### GET `/api/v2/mobile/drafts/{id}`

用途：查看草稿详情。

返回字段：

- 草稿完整字段
- `rawPayload`
- `missingFields`
- 若已确认，返回 `confirmedTxnId` 和正式流水摘要

业务规则：

- 只允许查看当前用户或当前家庭范围内可访问的草稿。
- `CONFIRMED` 草稿详情应展示正式流水摘要，方便用户从草稿追溯到正式账本。

### PUT `/api/v2/mobile/drafts/{id}`

用途：编辑草稿。

业务规则：

- 只允许编辑 `DRAFT` 状态草稿。
- `CONFIRMED` 和 `IGNORED` 默认禁止编辑。
- 修改 `account_id`、`category_id`、`amount`、`note`、`merchant_norm`、`occurred_at` 等字段时更新 `updated_at`。
- 修改后不直接影响正式账本，只有 confirm 后才落账。
- 编辑后应重新计算 `missingFields`，用于草稿箱缺失字段提示。

### POST `/api/v2/mobile/drafts/{id}/confirm`

用途：单笔草稿确认入账。

业务规则：

- 只允许 `DRAFT` 状态确认。
- 必须校验 `account_id`、`amount`、`direction` 等入账必要字段。
- `EXPENSE`、`INCOME` 优先调用 `QuickEntryService`；复杂类型或投资补录调用 `LedgerService` 或订单/结算模块。
- 成功后草稿状态改为 `CONFIRMED`，写入 `confirmed_txn_id`。
- 如果失败，草稿状态保持 `DRAFT`，返回失败原因。
- 如果草稿已是 `CONFIRMED`，返回 `already_confirmed`，并返回 `confirmed_txn_id`，不得重复生成正式流水。

### POST `/api/v2/mobile/drafts/{id}/ignore`

用途：忽略草稿。

业务规则：

- `DRAFT` 可忽略。
- 忽略后状态改为 `IGNORED`。
- `IGNORED` 不进入默认待处理数量。
- 后续可通过筛选查看。
- 已 `CONFIRMED` 的草稿不允许忽略。

### POST `/api/v2/mobile/drafts/batch-confirm`

用途：批量确认草稿。

请求字段：

- `draftIds`
- 可选统一覆盖字段：`accountId`、`categoryId`、`notePrefix`

返回字段：

- `successIds`
- `failedItems`：每个失败项包含 `draftId`、`reason`、`code`
- `duplicateItems`：已确认或幂等命中的项目

业务规则：

- 每条草稿使用独立事务或可控事务边界。
- 部分失败不能影响其他成功项。
- 成功项状态改为 `CONFIRMED`。
- 失败项保持 `DRAFT`。
- 已 `CONFIRMED` 的草稿再次确认，应返回 `already_confirmed`，不重复生成正式流水。

### POST `/api/v2/mobile/ledger/quick-entry`

用途：不经过草稿，直接从 App 预填确认页入账。

业务规则：

- 复用现有 quick-entry / ledger 体系。
- 成功后直接生成正式流水。
- 仍然需要 `external_ref` / `dedup_key` 进行幂等控制，避免重复点击导致重复入账。
- 重复请求返回已有正式流水摘要，不重复写入 `ledger_txn` / `ledger_posting`。
- 如果必要字段缺失，应返回校验失败，提示用户改为存草稿或补全后确认。

## 幂等与去重

- `external_ref` 优先。
- 若 `external_ref` 为空，则使用 `dedup_key`。
- `dedup_key` 可由 `channel + amount + merchant_norm + occurred_at` 时间窗口生成。
- 服务端必须保证重复提交不会重复写入正式账本或草稿。
- 批量确认时，部分失败不能影响其他成功项，失败项保留草稿状态并返回原因。

### 强幂等

- `external_ref` 存在时优先使用 `channel + external_ref`。
- 适用于平台流水号、订单号、第三方交易号。
- 同一渠道下相同 `external_ref` 命中时，应返回已有草稿或已有正式流水摘要。
- 如果草稿已确认，则应返回 `confirmed_txn_id`，不得再次创建草稿或再次落账。

### 弱幂等

- `external_ref` 不存在时使用 `dedup_key`。
- `dedup_key` 由 `channel + direction + amount + merchant_norm + occurred_at` 时间窗口生成。
- 时间窗口建议 60~120 秒，默认 90 秒。
- 对手工录入、分享文本、无外部流水号的场景，弱幂等只用于降低重复点击和短时间重复提交风险，不应替代用户确认。

### 服务端兜底

- App 可以先生成 `dedup_key`，但服务端必须重新计算或校验。
- 服务端不信任前端传来的幂等结果。
- 重复请求返回已有草稿或已有正式流水摘要。
- 对于确认直落账，幂等记录可落在正式流水扩展字段、幂等表或业务侧查询逻辑中；无论采用哪种方式，都必须保证不会重复写入分录。

### 批量确认幂等

- 已 `CONFIRMED` 的草稿再次确认，应返回 `duplicate` / `already_confirmed`，不重复生成正式流水。
- 部分失败必须明确 `reason`，例如 `missing_account`、`missing_amount`、`invalid_status`、`duplicate_external_ref`。
- 批量接口返回成功、失败、重复三类结果，App 根据结果局部刷新列表。

## App 页面

### 1. 首页看板

- 总资产
- 净资产
- 可用资金
- 今日待处理：草稿数、待结算数、逾期结算数
- 核心持仓摘要
- 展示草稿数、待结算数、逾期结算数。
- 点击草稿数进入草稿箱。
- 点击待结算进入待结算页。
- 点击核心持仓进入持仓页。
- 首页刷新时以服务端返回为准，本地缓存仅用于弱网下的临时展示。

### 2. 快速录入

- 消费
- 收入
- 转账
- 投资买入、卖出、申购、赎回补录入口
- 进入预填确认页前，先根据默认账户、默认分类、商户规则进行预填。
- 用户可选择“确认入账”或“存草稿”。
- 如果默认账户或默认分类缺失，应显示待补全提示，不阻止存草稿。
- 投资补录入口应明确引导到订单/结算流程或投资草稿，不直接按普通消费流水处理。

### 3. 预填确认页

- 金额、时间、账户、分类、商户、备注
- 按钮：确认入账、存草稿、忽略
- 字段：金额、发生时间、渠道、账户、分类、商户/对方、备注。
- “确认入账”：调用 `/api/v2/mobile/ledger/quick-entry` 或草稿确认接口，成功后跳转流水详情或返回上一页。
- “存草稿”：调用 `/api/v2/mobile/drafts`，成功后返回草稿箱或继续录入。
- “忽略”：对于已有草稿调用忽略接口；对于未保存的预填内容仅本地丢弃。
- “继续编辑”：留在当前页，允许补全账户、分类、备注等字段。
- 如果确认失败，页面保留用户输入并显示失败原因。

### 4. 草稿箱

- 草稿列表
- 编辑草稿
- 单笔确认
- 批量确认
- 批量忽略
- 分类规则学习
- 支持状态筛选：`DRAFT`、`CONFIRMED`、`IGNORED`。
- 支持渠道筛选：支付宝、微信、银行、现金、手工、其他。
- 支持关键词搜索：商户、备注、支付方式、原始摘要。
- 支持单笔编辑、单笔确认、批量确认、批量忽略。
- 支持缺失字段提示，缺失金额、方向、账户、分类时在列表中标识。
- 批量确认前应展示确认预览，提示成功项、风险项和缺失项。
- 分类规则学习入口可基于 `merchant_norm` 和用户最终选择的 `category_id` 生成规则建议。

### 5. 待结算

- 复用现有待结算接口
- 支持确认结算
- 支持从首页待处理卡片进入。
- 支持查看订单详情、资金来源、预计确认日、逾期状态。
- 确认结算仍走现有订单/结算接口，不通过草稿表落账。

### 6. 持仓

- 持仓列表
- 产品详情
- 从首页核心持仓摘要可直接进入。
- 持仓详情应展示产品、份额、成本、市值、盈亏和最近交易摘要。
- 持仓数据以服务端持仓接口和行情/净值接口为准。

### 7. 流水

- 流水列表
- 流水详情
- 支持查看由 App 直接确认生成的正式流水。
- 支持从已确认草稿跳转到 `confirmed_txn_id` 对应流水。
- 流水详情展示交易头、分录、账户影响和备注。

### 8. 设置

- 登录信息
- 服务器地址
- Token
- 默认账户
- 默认分类
- 分类规则
- 草稿同步设置
- Token 管理：查看登录状态、刷新或退出登录。
- 默认账户：用于快速录入预填，必须是现金叶子账户。
- 默认分类：用于消费/收入预填，可按方向设置。
- 分类规则：维护商户到分类的映射建议。
- 草稿同步策略：控制进入草稿箱时自动刷新、弱网下缓存保留时长。
- 是否启用失败重试队列：控制网络失败时是否写入本地 outbox 并自动重试。

## 移动端技术选型

- Android 原生：Kotlin + Jetpack Compose。
- 本地存储只做缓存和失败重试队列，不作为最终真相源。
- 后端 API 是最终数据来源。
- 鸿蒙适配作为后续评估项，不在 M1 强制实现。
- 网络层建议使用 Retrofit + OkHttp 访问后端 `/api/v2/**`。
- Room 只做本地缓存和失败重试队列，不作为真相源。
- DataStore 保存 Token、服务端地址、默认账户、默认分类、草稿同步策略等设置。
- 本地 outbox 用于网络失败重试，记录待重放请求、重试次数、最近失败原因。
- 所有最终数据以服务端返回为准；本地缓存命中后仍应在网络恢复时刷新服务端数据。
- Token 失效时统一跳转登录，并保留未成功提交的 outbox 项。

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

### 服务端测试

- 创建草稿成功，返回 `DRAFT` 状态与草稿ID。
- 重复 `external_ref` 不重复创建草稿。
- 重复 `dedup_key` 不重复创建草稿。
- 编辑 `DRAFT` 草稿成功，并更新 `updated_at`。
- 编辑 `CONFIRMED` 草稿失败。
- 确认 `DRAFT` 草稿成功，并生成 `ledger_txn`。
- 重复确认 `CONFIRMED` 草稿不重复生成 `ledger_txn`。
- 忽略 `DRAFT` 草稿成功，状态变为 `IGNORED`。
- 批量确认部分成功、部分失败时，成功项为 `CONFIRMED`，失败项保持 `DRAFT`。
- `CONFIRMED` 草稿必须能通过 `confirmed_txn_id` 查询到正式流水摘要。
- `IGNORED` 草稿默认不进入待处理数量。

### App 测试

- 快速录入后可确认入账。
- 快速录入后可存草稿。
- 网络失败进入 outbox。
- outbox 重试成功后清除本地记录。
- 默认账户、分类缺失时提示补全。
- 草稿箱状态筛选、渠道筛选、关键词搜索正常。
- 草稿箱单笔编辑、单笔确认、批量确认、批量忽略正常。
- 已确认草稿可跳转到正式流水详情。
