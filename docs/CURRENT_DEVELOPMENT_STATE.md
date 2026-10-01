# 财富中枢当前开发状态

- **作者**：ChatGPT（依据 v2 代码、AiCore Delivery Manifest 与 GitHub Actions 证据同步）
- **更新时间**：2026-09-30 +08:00
- **工程仓库**：`TimeLordTTY/MyDCA-Board@v2`

> 本文件是“当前实现状态”的首要事实源。长篇设计文档、版本专项报告和 Phase1/Phase2 历史总结保留设计/历史价值；若其中的“当前状态、下一步、尚未实现”与本文件冲突，以本文件和实际代码为准。

## 当前阶段

v0.16 研究方案后端新增 owner/family 双隔离的持久化研究元数据 API、创建时证据快照、独立参数草稿、DRAFT/ACTIVE/ARCHIVED 生命周期及只读失效警告。仅后端能力；UI 未接入，数据库脚本 `backend/migrations/20261001_research_plan.sql` 仅提交、未部署。历史研究不代表未来表现，不触发回测或交易。详见 `docs/mydca_v016_research_plan_backend_20261001.md`。

财富中枢已不处于项目初始化阶段。当前主线为 **Phase3：原生 Android + 草稿式安全记账闭环**，并持续保持 PC/Web、Java 后端、MySQL、Python 工具能力。

当前 Android 应用版本：
- `versionName = 0.15.0`
- `versionCode = 16`

v0.15 已加入持久化的 owner 作用域回测历史、2 至 5 条历史对比、只读策略研究候选与可校验证据包，以及 PC/Android 每日财富雷达。PC 策略实验室提供历史、对比、候选与证据导出；Android 展示只读雷达和最近成功回测。雷达未知值保留 `null`/「未知」，刷新失败的旧快照明确标识；研究结果不生成订单或交易。详见 `docs/mydca_v015_release_hardening_20260930.md`。

v0.15 回测历史使用部署方提供的本地持久化目录 `../data/backtest-history`（可配置 `backtest.history-root`），**没有新增 SQL migration**。既有 v0.8、v0.14 migration 在 Git 中，目标环境是否已部署仍待逐环境确认；本次未连接数据库或执行迁移。目录不可写或记录损坏时历史接口会失败，界面显示错误而不报空列表或假成功。

v0.14 发布硬化已对齐 GitHub Actions APK 制品名与 Android 版本，并补齐 Android 最近回测、PC 待结算和策略实验室的失败态。完整验证与未完成的真机/生产迁移边界见 `docs/mydca_v014_release_hardening_20260929.md`。

v0.14 草稿生命周期审计与安全恢复已写入代码；需要部署 `sql/updatesql/20260928/01_create_draft_lifecycle_event.sql` 后才可使用事件持久化。详细边界见 `docs/mydca_v014_draft_lifecycle_audit_20260928.md`。

v0.14 人工结算历史与只读对账已写入代码；新结算精确流水关联和展示用预览摘要依赖部署 `sql/updatesql/20260929/01_settlement_audit_link.sql`。历史旧记录、关联账户份额无法可靠回溯时显示 `WARNING`，不会自动修复。详见 `docs/mydca_v014_settlement_audit_reconciliation_20260929.md`。

“v0.8 草稿强幂等”是后端可靠性里程碑；Android 当前 `0.15.0` 增加只读雷达与回测结果展示（`0.14.0` 为草稿历史查看与安全恢复）。前后端版本号不属于同一层，互不依赖即可独立发布。

## 已完成的 Phase3 主能力

- `draft_ledger_entry` 草稿表、列表/详情/编辑/preview/ignore/confirm API。
- 文本记账 `parse-text → draft-from-intent`；解析只生成候选和 DRAFT。
- Android 真实登录与 Keystore/AES-GCM Token 安全存储。
- 总览、今日待办、草稿箱、资产、设置。
- 手工文本、Photo Picker + 本地 ML Kit 中文 OCR、支付通知候选。
- v0.6 加密 Draft Outbox：可恢复网络/5xx 失败只重试“创建 DRAFT”，401/403 暂停，业务 4xx 不循环。
- v0.7 全局“记一笔”快速采集中心：手工、OCR、支付通知候选、Outbox 四入口统一导航。
- v0.8.0 外部分享快速采集：系统 Share Sheet 的文本 / 单张图片只预填到现有人工采集流程，不自动 parse/OCR/draft/preview/confirm。
- v0.9.0 桌面快速记账小组件：四个静态中文入口（记一笔 / 手工记账 / 图片识别 / 草稿箱）只打开既有页面，不联网、不读写账本、不自动记账。
- v0.10.0 TRANSFER 转账草稿闭环：文本候选识别转账、双账户（转出 / 转入）影响预览、主人二次确认后经 `QuickEntryService.quickTransfer` 生成一笔正式转账流水；重复确认不重复记账。
- v0.11.0 投资买入 / 申购草稿闭环：BUY / SUBSCRIPTION 候选解析、真实产品 + 单一资金来源账户选择、只读订单与 CASH / RECEIVABLE 资金影响预览、主人二次确认后经 `OrderService.createInvestmentDraftOrder` 创建 PENDING 订单并生成付款账本；不自动结算、不生成最终持仓、重复确认不重复建单。
- v0.12.0 投资卖出 / 赎回草稿闭环：SELL / REDEMPTION 候选解析、真实产品 + 该产品真实持仓来源 + 份额 + 到账账户选择、只读可用份额预览（已扣除 PENDING 占用）、主人二次确认后经 `OrderService.createSellRedeemDraftOrder` 仅创建 PENDING 订单并登记 SOURCE / TARGET 资金线；确认阶段不生成账本流水、不改现金余额、不改持仓，不自动结算、重复确认不重复建单。
- v0.13.0 人工结算预览与二次确认闭环：四类 PENDING 订单（BUY / SUBSCRIPTION / SELL / REDEMPTION）先经只读 `POST /api/v2/settlements/preview` 展示现金 / 持仓 / 手续费影响与 fresh 令牌，主人二次确认后携带令牌 `POST /api/v2/settlements/confirm` 才生成内部 `settlement_confirm` 与账本 / 持仓影响；preview 不写任何业务数据、不改 `reserved_amount` / `initial_shares` / 订单状态，confirm 幂等且事务完整，不自动结算、不后台 confirm、不调用真实交易渠道。

## v0.8 强幂等（已完成）

任务：`task-mydca-v08-draft-strong-idempotency-20260927`  
结果提交：`ea8b3618e25c648127c62750c307ae8af976dd56`

- 应用层 `sourceType + sourceRef` 幂等查询继续保留。
- 新增 `sql/updatesql/20260927/` 三步 migration：重复来源预检、空来源归一化、user/family scope 唯一键。
- `source_ref IS NULL` 保持非幂等旧语义。
- 仅捕获明确 `DuplicateKeyException`；冲突后按同一可见作用域重查既有草稿，其他数据库异常不伪装为成功。
- 后端 70 项测试通过；Android 24 个测试类 / 119 项通过。
- AiCore Delivery Manifest：`completed / verified_wecom_receipt`。

**重要**：migration 尚未由本次自动任务连接或执行到任何数据库。上线前必须先在目标环境执行只读重复数据预检并人工确认；生产数据库迁移不属于普通后台自动任务。

## Android v0.8.0 外部分享快速采集（已完成）

- 任务：`task-mydca-android-v080-external-share-capture-20260927`
- 详细说明：`docs/mydca_android_v080_external_share_capture_20260927.md`

- `ACTION_SEND` 单条 `text/*` 或 `image/*` 进入既有手工文本 / 图片 OCR 采集页，**只预填**。
- 分享文本与图片 URI 只在进程内保留，不落盘、不写日志、不上传；同一 payload 只消费一次，未登录期间保留到登录后消费一次。
- 分享图片不自动 OCR，必须用户点击“使用此图片并识别”；识别后仍需用户手动解析、手动生成 DRAFT。
- sourceRef：`android-share-text-<uuid>` / `android-share-image-<uuid>`，不含原文 / 文件名 / URI，Outbox 重试复用同一值。
- 返回 / 取消、切换底部导航、成功生成 DRAFT 都会清空一次性 share target。
- 未新增任何广泛权限；`ACTION_SEND_MULTIPLE` 明确不支持。
- Android 28 个测试类 / 156 项通过、`assembleDebug`、`lintDebug`（0 error / 2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 全部通过。

## Android v0.9.0 桌面快速记账小组件（已完成）

- 任务：`task-mydca-android-v090-home-widget-quick-capture-20260928`
- 详细说明：`docs/mydca_android_v090_home_widget_quick_capture_20260928.md`

- 使用系统 `AppWidgetProvider` + `RemoteViews`（不引入 Glance、未做架构重构）：完整尺寸提供「记一笔 / 手工记账 / 图片识别 / 草稿箱」四个静态中文入口，尺寸不足自动折叠为「记一笔 + 草稿箱」。
- 点击只构造显式 Intent 指向本 App `MainActivity`，action 只能取自受控枚举 `WidgetNavigationTarget`：不接受任意外部 route 字符串，Intent 不携带任何 extra。
- 小组件层没有 repository / network / `parse` / `draft` / `preview` / `confirm` 能力；`updatePeriodMillis=0`，无后台轮询 / Alarm / WorkManager / 前台服务 / 常驻通知。
- 三个一次性系统入口优先级：桌面小组件 > 外部分享 > 通知候选；被接管目标与 `selectedDraftId` / `QuickCaptureFocus` 同时清空。
- 未登录时目标只在当前进程保留一次；未申请任何新权限，`MainActivity` 未新增 intent-filter。
- Android 35 个测试类 / 195 项通过、`assembleDebug`、`lintDebug`（0 error / 2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 全部通过。

## v0.10.0 TRANSFER 转账草稿闭环（已完成）

- 任务：`task-mydca-v010-transfer-draft-loop-20260928`
- 详细说明：`docs/mydca_v010_transfer_draft_loop_20260928.md`

- 候选结构统一为 `txnType=TRANSFER` + `accountId` / `accountNameHint`（转出）+ `targetAccountId` / `targetAccountNameHint`（转入）+ `amount` + `note`；`sourceAccountId` / `cashAccountId`、`toAccountId` / `destinationAccountId` 只作兼容读取别名，写回只用标准字段。
- 文本解析：`转账` / `转到` / `转入` / `转出` 或「从…到…」优先判定 TRANSFER，优先级高于「到账 / 付款 / 支付」；`工资到账 5000` 仍为 INCOME、`支付午饭 30` 仍为 EXPENSE。规则解析不把账户名称提示映射成真实账户 ID，缺 ID 时只生成 DRAFT 并把 `accountId` / `targetAccountId` 写入 `missingFields`。
- 预览：转出 / 转入两个账户都必须当前 user / family 可见、active REAL、叶子账户、互不相同、币种一致；TRANSFER 不套用 EXPENSE 的 SPENDABLE 限制，跨资金用途时给出中文风险提示。新增 `targetAccountId` / `targetAccountName` / `targetAccountType` / `targetFundUsage` / `targetAccountDelta`；转出记 `accountDelta = -amount`，转入记 `targetAccountDelta = +amount`。
- 正式入账：`QuickEntryService.quickTransfer(userId, familyId, sourceAccountId, targetAccountId, amount, note)` 经既有 `LedgerService.createTransaction(..., "TRANSFER_OUT", ...)` 创建一笔平衡交易（转出 CREDIT + 转入 DEBIT）；流水页按既有语义展示转出 / 转入两条视图，但底层只有一笔交易，不计入收入 / 支出净现金流。
- confirm：TRANSFER 必须重新 `buildPreview`，只有 `confirmSupported=true` 才调用 `quickTransfer`；`txnId` 写回 `confirmTxnId`，重复确认幂等，`IGNORED` 不可确认，不存在自动 preview / confirm。
- Android：`versionName = 0.10.0`（`versionCode = 11`）；草稿编辑支持支出 / 收入 / 转账三种类型与转出 / 转入双账户，切回支出 / 收入会清空目标账户；确认弹窗为「确认将 ¥X 从 A 转到 B？」。
- PC / shared：`web/shared` 同步 TRANSFER 与 target 字段类型；PC 草稿箱可查看 / 预览双账户与双 delta，并补齐最小安全编辑，不再把 TRANSFER 显示成不支持类型。
- 本轮未新增数据库表 / migration，未连接任何数据库；后端 95 项测试、Android 206 项测试、`assembleDebug`、`lintDebug`（0 error / 2 条既有 warning）、`scripts/post-task-compile-hook.ps1`（成功静默）全部通过。

## v0.11.0 投资买入 / 申购草稿闭环（已完成）

- 任务：`task-mydca-v011-invest-buy-draft-loop-20260928`
- 详细说明：`docs/mydca_v011_invest_buy_draft_loop_20260928.md`

- 候选结构统一为 `txnType=BUY / SUBSCRIPTION` + `productId`（必须由主人明确选择）+ `productNameHint`（仅提示）+ `amount` + `accountId`（本轮单资金来源）+ `accountNameHint` + `note` + 可选 `expectedNavDate` / `expectedConfirmDate`。
- 文本解析：`买入` → BUY，`申购` / `定投` → SUBSCRIPTION，判定优先级高于 `买` / `付款` / `支付` 等 EXPENSE 关键词；`买奶茶 30` 仍为 EXPENSE。规则解析只识别语义、提取金额与产品名称提示，禁止把名称提示映射成真实 `productId`；缺 ID 时只生成 DRAFT 并把 `productId` / `accountId` 写入 `missingFields`。
- 只读预览：产品必须存在且启用、币种与资金账户一致；资金账户必须当前 user / family 可见、active REAL、叶子账户、可用余额足够；资金用途一般投资只允许 `INVESTABLE`，`BOND_REPO` 额外允许 `RESERVED`，`SPENDABLE` / 普通 `RESERVED` / 父账户 / VIRTUAL 一律阻断。投资影响口径为 `accountDelta = -amount`、`receivableDelta = +amount`，`willCreateOrder=true`、`willCreateLedgerTxn=true`、`willCreateSettlement=false`、`willAffectHolding=false`。preview 不调用 `OrderService` / `LedgerService` / `SettlementService`。
- 正式入账：新增 `OrderService.createInvestmentDraftOrder(...)` 安全入口（只允许 BUY / SUBSCRIPTION，重新校验产品、币种、资金用途、账户可见性与可用余额），复用既有 `createOrder` 创建 `status=PENDING` 订单并生成下单付款账本（CASH CREDIT + RECEIVABLE DEBIT）；订单仍需后续 SettlementService 才能结算并影响持仓。
- confirm：BUY / SUBSCRIPTION 必须重新 `buildPreview`，只有 `confirmSupported=true` 才调用安全入口；订单 ID 写回 `confirmOrderId`，重复确认幂等，`IGNORED` 不可确认，不存在自动 preview / confirm / settle。
- Android：`versionName = 0.11.0`（`versionCode = 12`）；草稿编辑支持支出 / 收入 / 转账 / 买入 / 申购，投资表单支持真实产品 + 单一资金账户，确认弹窗为「确认创建【产品】买入/申购订单 ¥X？」。PC / shared 同步投资类型与预览字段。
- 本轮未新增数据库表 / migration，未连接任何数据库；后端 121 项测试、Android 221 项测试、`assembleDebug`、`lintDebug`（0 error / 2 条既有 warning）、`scripts/post-task-compile-hook.ps1`（成功静默）全部通过。
## v0.12.0 投资卖出 / 赎回草稿闭环（已完成）

- 任务：`task-mydca-v012-invest-sell-redeem-draft-loop-20260928`
- 详细说明：`docs/mydca_v012_invest_sell_redeem_draft_loop_20260928.md`

- 候选结构统一为 `txnType=SELL / REDEMPTION` + `productId`（必须由主人明确选择）+ `productNameHint`（仅提示）+ `shares` + `sourceAccountId`（持仓来源）+ `sourceAccountNameHint` + `targetAccountId`（到账账户）+ `targetAccountNameHint` + `note` + 可选 `expectedNavDate` / `expectedConfirmDate`。
- 文本解析：`卖出` → SELL、`赎回` → REDEMPTION，判定优先级高于 `买` / `付款` / `支付` 等 EXPENSE 关键词；只提取 `shares` 与产品名称提示，禁止把名称提示映射成真实 `productId`，也绝不自动匹配持仓来源或到账账户；缺字段时只生成 DRAFT 并写入 `missingFields`。
- 只读预览：持仓来源必须是该产品在当前 user / family 下的真实持仓来源，`shares > 0` 且不超过可用份额；可用份额 = 该来源账户真实持仓份额 − 同产品 / 来源账户下仍为 PENDING 的 SELL / REDEMPTION 占用份额；到账账户必须当前 user / family 可见、active REAL 叶子账户、币种与产品一致，禁止 VIRTUAL / POSITION / 父账户。预览明确展示产品、持仓来源、当前可用份额、本次份额、预计剩余份额与到账账户；`willCreateOrder=true`（仅当字段齐全）、`willCreateLedgerTxn=false`、`willCreateSettlement=false`、`willAffectHolding=false`，`impactDirection=NONE`、deltas 为 0；preview 不调用 `OrderService` / `LedgerService` / `SettlementService`。
- 正式登记：新增 `OrderService.createSellRedeemDraftOrder(...)` 安全入口（只允许 SELL / REDEMPTION），复用既有 `OrderService` 创建 `status=PENDING` 订单，并写入 `SOURCE`（sourceAccountId + shares）与 `TARGET`（targetAccountId）资金线；confirm 阶段不生成 CASH / POSITION / FEE 账本流水、不改现金余额、不改持仓，真正的资金与持仓变化仍只在后续 SettlementService 人工结算时产生。
- confirm：SELL / REDEMPTION 必须重新 `buildPreview`，只有 `confirmSupported=true` 才调用安全入口；订单 ID 写回 `confirmOrderId`，重复确认幂等，异常整体回滚且草稿保持 DRAFT，`IGNORED` 不可确认，不存在自动 preview / confirm / settle。
- Android：`versionName = 0.12.0`（`versionCode = 13`）；草稿编辑新增卖出 / 赎回，表单为真实产品 + 持仓来源 + 份额 + 到账账户 + 备注，产品选定后复用 `GET /api/v2/holdings/product/{productId}/by-account` 展示持仓来源；确认文案明确「当前只创建内部待处理记录，不立即减少持仓，也不立即增加到账余额」；切换到其它交易类型会清理 SELL / REDEMPTION 专属字段。PC / shared 同步卖出 / 赎回类型与预览字段，PC 草稿箱支持查看 / 编辑 / preview / 二次确认。
- 本轮未新增数据库表 / migration，未连接任何数据库；后端 145 项测试、Android 230 项测试、`assembleDebug`、`lintDebug`（0 error / 2 条既有 warning）、`scripts/post-task-compile-hook.ps1`（成功静默）全部通过。

## v0.13.0 人工结算预览与二次确认闭环（已完成）

- 任务：`task-mydca-v013-manual-settlement-preview-confirm-20260928`
- 详细说明：`docs/mydca_v013_manual_settlement_preview_confirm_20260928.md`

- 统一四类投资订单的 settlement 语义：不再另造第二套结算引擎，把既有 `SettlementService` 的结算计算 / 校验抽成无副作用 preview，preview 与 confirm 复用同一套纯函数规则（`runSettlement`）。BUY / SUBSCRIPTION 下单阶段已有付款账本（CASH CREDIT + RECEIVABLE DEBIT），结算阶段不重复扣下单现金，清理 RECEIVABLE 并形成最终 POSITION / 关联账户与手续费；SELL / REDEMPTION 下单阶段无账本，结算阶段才真正产生 CASH / POSITION / FEE 影响。
- 只读预览 API：新增 `POST /api/v2/settlements/preview`，接受 `orderId` / `confirmDate` / `navDate` / `confirmNav` / `confirmShares` / `confirmAmount` / `confirmFee`（null 表示按 `BrokerFeeService` 估算、显式 0 用 0），校验订单存在且 PENDING、owner scope、产品启用、资金来源行完整且账户可见、日期非空、净值 > 0、份额 / 金额 / 手续费非负；不写 `settlement_confirm` / `ledger_txn` / `ledger_posting`，不改 `reserved_amount` / `initial_shares` / `order.status`。返回 `SettlementPreviewDTO`（`postingsPreview[]` / `summaryLines[]` / `confirmSupported` / `blockingReasons` / `willCreateSettlementConfirm` / `willCreateLedgerTxn` / `willChangeHolding` / `willChangeCash` / `freshPreviewToken`），全部中文文案。
- fresh preview gate：`freshPreviewToken`（同时作为 `previewFingerprint`）由订单（含 `updated_at` / `status`）、owner 作用域、输入参数、资金来源行、关键账户快照与计算结果取 SHA-256；confirm 重新计算并逐字比对，任一变化即失效。令牌不落库、不新增数据库字段、不做 migration。
- 人工确认 API：新增 `POST /api/v2/settlements/confirm`，复用同一输入并携带 `freshPreviewToken`；令牌校验通过后才创建虚拟 / 持仓账户，写入唯一一条 `settlement_confirm`、释放资金来源行 `reserved_amount`、更新关联账户 `initial_shares`、只生成一套 `ledger_txn` / `ledger_posting`，订单置 `CONFIRMED`；全程 `@Transactional`，重复确认幂等、异常整体回滚、`CANCELLED` / `FAILED` / `CONFIRMED` 不可再次结算。
- Android：`versionName = 0.13.0`（`versionCode = 14`）；新增「待结算」体验（总览 / 今日待办进入，待结算列表 → 结算编辑 → 只读预览 → 二次确认弹窗 → confirm → 成功刷新），按 `orderType` 动态字段，修改字段即清除旧预览，未 preview 不能 confirm，新增 `SettlementRepository` / `SettlementEditState` / `SettlementUiState` / `PendingSettlementScreen`。PC / shared 同步 `SettlementPreview` 类型、`previewSettlement` API 与 `SettlementConfirmModal` / `Settlements` 安全链。
- 本轮未新增数据库表 / migration，未连接任何数据库；后端 169 项测试、Android 267 项测试、`assembleDebug`、`lintDebug`、`scripts/post-task-compile-hook.ps1`（成功静默）全部通过。

## Android CI APK 真实证据

### v0.15.0
- source commit / Run ID / Artifact ID / APK 文件名 / CI APK SHA-256：尚无真实 CI 证据；本次只本地提交、不 push。

### v0.13.0
- source commit / Run ID / Artifact ID / APK 文件名 / CI APK SHA-256：待 owner push 后回填（普通自动任务只提交、不 push）。

### v0.12.0
- source commit / Run ID / Artifact ID / APK 文件名 / CI APK SHA-256：待 owner push 后回填（普通自动任务只提交、不 push）。

### v0.11.0
- source commit / Run ID / Artifact ID / APK 文件名 / CI APK SHA-256：待 owner push 后回填（普通自动任务只提交、不 push）。

### v0.10.0
- source commit：`e5c544db7d90f82858ddc03a1ca285ec7659e037`
- GitHub Actions：`Android test APK`
- Run ID：`36369966197`
- 结论：`success`
- Artifact ID：`10949105553`
- Artifact：`mydca-android-v0.10.0-e5c544db7d90f82858ddc03a1ca285ec7659e037`
- APK：`MyDCA-Board-v0.10.0-e5c544db.apk`
- CI APK SHA-256：`FD39508EA408807DB5ECEEBAFD2B2F4630D766447398E29D1397D8721A5304F0`

### v0.9.0
- source commit：`7fe07527a8d793018d4d7f284a5643359873ddd0`
- GitHub Actions：`Android test APK`
- Run ID：`36367375440`
- 结论：`success`
- Artifact ID：`10946904834`
- Artifact：`mydca-android-v0.9.0-7fe07527a8d793018d4d7f284a5643359873ddd0`
- APK：`MyDCA-Board-v0.9.0-7fe07527.apk`
- CI APK SHA-256：`5E1059E630D2F66C76A93271C62285C36276A7FAAA65867A445709AAD2A0A13C`

### v0.8.0
- source commit：`e8af769bf5446daa15ccf849b15a5f17786c7fcc`
- GitHub Actions：`Android test APK`
- Run ID：`36329990920`
- 结论：`success`
- Artifact ID：`10934923148`
- Artifact：`mydca-android-v0.8.0-e8af769bf5446daa15ccf849b15a5f17786c7fcc`
- APK：`MyDCA-Board-v0.8.0-e8af769b.apk`
- CI APK SHA-256：`F3C03CF9685C376782C2DB0CB799836971A63B5B4763BC38A9F1B0A96E837E08`

### v0.7.0
- source commit：`6098a9728f23dc6e0b6bbd5b7d0460c5630f4252`
- GitHub Actions：`Android test APK`
- Run ID：`36320197608`
- 结论：`success`
- Artifact ID：`10931548073`
- Artifact：`mydca-android-v0.7.0-6098a9728f23dc6e0b6bbd5b7d0460c5630f4252`
- APK：`MyDCA-Board-v0.7.0-6098a972.apk`
- CI APK SHA-256：`D18D0CC67F7428495E6A6F2B0ED50100D556301368D6853FD0489AD2325E3B2B`

### v0.6.0
- source commit：`a732c3bc569959a4f53a6448d8c7e6ce3dcc63ee`
- Run ID：`36306899948`
- Artifact ID：`10928005959`
- APK：`MyDCA-Board-v0.6.0-a732c3bc.apk`
- CI APK SHA-256：`2298E56D4B9DF18218CAD17A1CCFA3EA094592364F2AFA2FD5E5582B103BB0CD`

旧文档中“v0.6/v0.7/v0.8.0/v0.9.0 CI 制品 NOT_PRODUCED / 待回填”的表述均已过期；以上以真实 GitHub Actions / Artifact 证据为准。

## 自动开发与交付

普通工程当前通过 AiCore：
`ChatGPT/owner → ai-core Git task → Windows scheduler → approved Codex executor → MyDCA v2 → tests/build → local commit → 后续由 owner 安排推送与交付`

`scripts/post-task-compile-hook.ps1` 构建成功后必须静默：只输出 stdout/log，不播放声音，不弹 Windows Toast/Popup。

## 当前安全边界

- 不自动交易。
- AI/通知/OCR/Outbox 不自动正式入账。
- 不把 preview 当 confirm。
- 不绕过 `DRAFT + fresh preview + confirmSupported + 用户二次确认`。
- 不把 Token、密码、Cookie、完整通知原文、图片 URI 写入普通日志或明文存储。
- 普通后台工程不连接生产数据库、不修改真实财务记录。
- migration 存在于 Git 不等于已部署。

## 当前真正未完成

1. **真实设备体验验收**：转账草稿的双账户选择 / 确认弹窗 / 流水页转出与转入两条视图，投资卖出 / 赎回草稿的持仓来源选择 / 可用份额预览 / 到账账户过滤 / 二次确认文案，待结算的结算编辑 / 现金与持仓影响预览 / 二次确认弹窗 / 成功后订单与持仓刷新，桌面小组件添加 / 尺寸回调 / 点击跳转，以及系统 Share Sheet 文本 / 单图、Photo Picker、支付截图 OCR、不同厂商 Content URI 与通知监听授权 / 候选体验仍需真机人工验收。
2. **投资订单结算**：BUY / SUBSCRIPTION 已纳入草稿确认闭环（v0.11.0），SELL / REDEMPTION 已在 v0.12.0 纳入；v0.13.0 起四类 PENDING 订单均可人工生成只读结算预览（`POST /api/v2/settlements/preview`）并经主人二次确认后携带 fresh 令牌 confirm（`POST /api/v2/settlements/confirm`）落内部账。既有 OrderService 真实语义保持不变：BUY / SUBSCRIPTION 创建 PENDING 订单时同步生成付款账本（CASH CREDIT + RECEIVABLE DEBIT），结算时清理 RECEIVABLE 并形成 POSITION / 关联账户与手续费、不重复扣下单现金；SELL / REDEMPTION 只创建 PENDING 订单并登记 SOURCE / TARGET 资金线与份额占用，结算时才产生 CASH / POSITION / FEE 影响。全流程不自动结算、不后台 confirm、不调用真实交易渠道。
3. **数据库 migration 上线**：v0.8 唯一键、v0.14 草稿生命周期与结算审计脚本都只在 Git 中确认存在；目标环境部署状态未由本任务验证。生产执行前必须先按各脚本的预检、备份和人工变更流程处理，尤其 v0.8 唯一键必须先检查重复来源。
4. **v0.15 运行与体验验收**：回测历史目录需在目标环境单独配置持久化和权限；PC/Android Radar、历史与研究候选需人工完成真实登录、权限、断网、过期行情和设备体验验收。真实 CI APK 证据须在后续推送并成功构建后回填。
5. **长期能力**：研究候选仍仅是历史证据，不构成自动策略执行；结算后的更多持仓影响场景继续按设计推进。

## 下一工程任务

v0.15 工程收敛后，下一步由 owner 单独安排目标环境的历史目录配置、未部署 migration 审核与执行、真机/PC 人工体验验收，以及推送后的 Android CI APK 证据回填。任何生产数据库迁移、真实财务记录修改或交易均不属于本次授权。
