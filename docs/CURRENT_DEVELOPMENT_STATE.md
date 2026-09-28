# 财富中枢当前开发状态

- **作者**：ChatGPT（依据 v2 代码、AiCore Delivery Manifest 与 GitHub Actions 证据同步）
- **更新时间**：2026-09-28 +08:00
- **工程仓库**：`TimeLordTTY/MyDCA-Board@v2`

> 本文件是“当前实现状态”的首要事实源。长篇设计文档、版本专项报告和 Phase1/Phase2 历史总结保留设计/历史价值；若其中的“当前状态、下一步、尚未实现”与本文件冲突，以本文件和实际代码为准。

## 当前阶段

财富中枢已不处于项目初始化阶段。当前主线为 **Phase3：原生 Android + 草稿式安全记账闭环**，并持续保持 PC/Web、Java 后端、MySQL、Python 工具能力。

当前 Android 应用版本：
- `versionName = 0.11.0`
- `versionCode = 12`

“v0.8 草稿强幂等”是后端可靠性里程碑；Android 当前 `0.11.0` 是投资买入 / 申购草稿闭环版本（上一版 `0.10.0` 为 TRANSFER 转账草稿闭环，更早 `0.9.0` 为桌面快速记账小组件、`0.8.0` 为外部分享快速采集）。前后端版本号不属于同一层，互不依赖即可独立发布。

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
## Android CI APK 真实证据

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
`ChatGPT/owner → ai-core Git task → Windows scheduler → approved Codex executor → MyDCA v2 → tests/build → commit/push → finalizer → Hermes → verified WeCom receipt`

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

1. **真实设备体验验收**：转账草稿的双账户选择 / 确认弹窗 / 流水页转出与转入两条视图，桌面小组件添加 / 尺寸回调 / 点击跳转，以及系统 Share Sheet 文本 / 单图、Photo Picker、支付截图 OCR、不同厂商 Content URI 与通知监听授权 / 候选体验仍需真机人工验收。
2. **投资订单类草稿确认**：BUY / SUBSCRIPTION 已纳入草稿确认闭环（v0.11.0）；SELL / REDEMPTION 仍未纳入，作为下一阶段独立任务。现有 OrderService 对 BUY / SUBSCRIPTION 的真实语义是：创建 PENDING 订单时同步生成付款账本（CASH CREDIT + RECEIVABLE DEBIT）；preview 已明确展示该资金影响，不把它描述成只占用资金。仍不自动结算、不生成最终持仓、不调用真实交易渠道。
3. **数据库 migration 上线**：v0.8 唯一键脚本尚未部署；生产执行前必须先跑重复数据预检。
4. **长期能力**：投资订单类草稿确认、完整结算/持仓影响、策略建议与回测闭环继续按设计推进。

## 下一工程任务

下一项普通、可自动化的业务任务是**投资卖出 / 赎回（SELL / REDEMPTION）草稿闭环**，作为独立任务推进；本轮 v0.11.0 已完成 BUY / SUBSCRIPTION，SELL / REDEMPTION 尚未支持。投资订单类确认始终复用既有 OrderService 与安全边界：输入的解析只产生候选与 DRAFT，preview 必须明确显示将创建 PENDING 订单并同步生成付款账本（CASH CREDIT + RECEIVABLE DEBIT）。不自动结算、不生成最终持仓、不调用任何真实交易渠道。