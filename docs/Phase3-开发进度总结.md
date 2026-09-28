# Phase3 开发进度总结

<!-- CURRENT-SNAPSHOT:START -->
## 当前实现快照（2026-09-28）

> 本文件保留 Phase3 的演进时间线；当前事实与下一步以 `docs/CURRENT_DEVELOPMENT_STATE.md` 为准。

- Android 当前版本：`0.14.0 / versionCode 15`；草稿历史与安全恢复、Outbox 恢复控制、只读策略实验室和发布硬化已写入代码。此前 v0.7 至 v0.13 的采集、转账、投资与人工结算能力仍在。
- v0.14 发布验证与待办见 `docs/mydca_v014_release_hardening_20260929.md`；真机体验、生产 migration 与真实 CI APK 制品仍需独立验收。
- v0.8 后端强幂等已完成：result commit `ea8b3618e25c648127c62750c307ae8af976dd56`；应用层幂等 + user/family scope 数据库唯一键 + DuplicateKey 并发恢复均已落地。
- v0.8 migration 已进入 Git，但未由自动任务连接或执行到任何数据库；生产迁移需单独授权并先跑只读重复数据预检。
- v0.7 CI APK 已真实产出：Run `36320197608`，Artifact `10931548073`，APK SHA-256 `D18D0CC67F7428495E6A6F2B0ED50100D556301368D6853FD0489AD2325E3B2B`。
- 采集链继续严格停在 DRAFT：手工/OCR/通知候选/Outbox 均不会自动 preview、confirm 或正式入账。
- v0.8.0 外部分享快速采集已落地：系统 Share Sheet 文本 / 单图只预填到现有手工 / OCR 流程，不自动 parse/OCR/draft/preview/confirm。
- v0.8.0 CI APK 已真实产出：Run `36329990920`，Artifact `10934923148`，APK SHA-256 `F3C03CF9685C376782C2DB0CB799836971A63B5B4763BC38A9F1B0A96E837E08`。
- v0.9.0 桌面快速记账小组件已落地：只做系统级入口，点击后仅打开既有页面，不联网、不读写账本、不自动记账。
- v0.9.0 CI APK 已真实产出：Run `36367375440`，Artifact `10946904834`，APK SHA-256 `5E1059E630D2F66C76A93271C62285C36276A7FAAA65867A445709AAD2A0A13C`。
- v0.10.0 转账草稿闭环已落地：文本候选识别 TRANSFER、双账户影响预览、二次确认后经 `QuickEntryService.quickTransfer` 生成一笔正式转账流水，重复确认不重复记账。
- v0.10.0 CI APK 已真实产出：Run `36369966197`，Artifact `10949105553`，APK SHA-256 `FD39508EA408807DB5ECEEBAFD2B2F4630D766447398E29D1397D8721A5304F0`。
- v0.11.0 投资买入 / 申购草稿闭环已落地：BUY / SUBSCRIPTION 候选解析、真实产品 + 单一资金来源账户选择、只读订单与 CASH / RECEIVABLE 资金影响预览、二次确认后经 OrderService 创建 PENDING 订单并生成付款账本；不自动结算、不生成最终持仓。
- v0.11.0 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）待 owner push 后回填（普通自动任务只提交、不 push）。
- v0.12.0 投资卖出 / 赎回草稿闭环已落地：SELL / REDEMPTION 候选解析、真实产品 + 该产品真实持仓来源 + 份额 + 到账账户选择、可用份额只读预览（已扣除 PENDING 占用）、二次确认后经 OrderService 仅创建 PENDING 订单并登记 SOURCE / TARGET 资金线；确认阶段不生成账本流水、不改现金余额、不改持仓，不自动结算。
- v0.12.0 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）待 owner push 后回填（普通自动任务只提交、不 push）。
- v0.13.0 人工结算预览与二次确认闭环已落地：四类 PENDING 订单（买入 / 申购 / 卖出 / 赎回）先经只读 `POST /api/v2/settlements/preview` 展示现金 / 持仓 / 手续费影响与 fresh 令牌，主人二次确认后携带令牌 `POST /api/v2/settlements/confirm` 才生成内部 settlement_confirm 与账本 / 持仓影响；preview 只读、confirm 幂等且事务完整，不自动结算、不后台 confirm。
- v0.13.0 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）待 owner push 后回填（普通自动任务只提交、不 push）。
- 下一普通工程目标：四类投资动作（买入 / 申购 / 卖出 / 赎回）草稿闭环与人工结算预览 / 二次确认闭环均已落地；后续的策略建议 / 回测闭环尚无 owner 授权任务，需单独规划与批准。数据库 migration 上线与真机人工验收继续独立处理。

### 已被后续版本完成的旧待办

本文件早期章节中的“Hermes 文本记账 MVP”“Android 原生 App 草稿箱”“OCR”“通知监听”“真实登录”等曾经是后续待办，现均已完成。保留原文仅用于历史时间线，不再作为当前任务清单。
<!-- CURRENT-SNAPSHOT:END -->

## 当前基线

Phase3 主线是“对话优先的草稿闭环与移动端基础”。首版优先完成服务端草稿能力，让 Hermes、PC 端和后续 Android 原生 App 都能围绕同一套草稿 API 协作。

## 已完成

### 服务端草稿表与草稿 API 首版

- 新增 `draft_ledger_entry` 增量迁移脚本，位置为 `sql/updatesql/20260610/01_create_draft_ledger_entry.sql`。
- 新增 `DraftLedgerEntry` 模型，字段覆盖草稿归属、来源追踪、原始输入、解析结果、预览结果、状态、置信度、缺失字段、确认关联和忽略原因。
- 新增 `DraftLedgerEntryMapper` 与 XML 映射，提供创建、权限内查询、预览更新、确认标记和忽略标记能力。
- 新增 `DraftLedgerEntryService`，实现草稿创建、列表、详情、编辑、预览、确认和忽略的状态机。
- 新增 `DraftLedgerEntryController`，提供 `/api/v2/drafts` 系列接口。
- 新增 shared 层 `draftApi` 与草稿类型定义，便于前端和后续移动端复用。

## 当前安全边界

- 草稿创建、查询、编辑、预览和忽略只操作 `draft_ledger_entry`，不写入正式 `ledger_txn` 或 `ledger_posting`。
- 草稿不参与资产、余额、收益、持仓成本或流水统计计算。
- 确认支持 `EXPENSE` / `INCOME` / `TRANSFER` 快速记账，并且必须通过 `QuickEntryService` 进入既有账本校验链路（TRANSFER 走 `quickTransfer`，生成一笔平衡转账）。
- 不支持的草稿类型不会绕过 `LedgerService`、`OrderService` 或 `SettlementService` 直接写正式表。
- 已确认或已忽略草稿不可再次编辑；已确认草稿重复确认时直接幂等返回，不二次入账。

## 本轮验证与加固（2026-06-10）

- 补充服务层单元测试，覆盖缺失字段预览、非法 JSON、非法 `accountId`、非法 `amount`、`EXPENSE` 确认、`INCOME` 确认、不支持类型确认、重复确认、已忽略草稿确认和非 `DRAFT` 编辑。
- 加固 `parsed_payload_json` 解析错误处理：非法 JSON、非数字 `accountId`、非数字 `amount` 会返回清晰业务错误，不再暴露底层解析异常。
- 确认 `create`、`update`、`preview`、`ignore` 不调用 `QuickEntryService`，因此不会写入正式流水或分录。
- 确认 `confirm` 仅在 `EXPENSE` / `INCOME` 且字段齐全时调用 `QuickEntryService`，不支持的交易类型会被阻断。
- 确认 `CONFIRMED` 草稿重复确认不会重复调用 `QuickEntryService`，`IGNORED` 草稿不能确认。

## 后续待办

- Hermes 文本记账 MVP：把自然语言解析结果写入草稿 API。
- 草稿影响预览增强：展示更完整的账户、订单、结算和持仓影响。
- Android 原生 App 草稿箱：承接草稿列表、详情、编辑、确认和忽略体验。
- 扩展确认类型：在既有账本、订单和结算服务校验能力完善后，再支持投资订单类草稿确认。

## 文本记账解析 MVP（2026-06-10）

- 已打通 `POST /api/v2/ai/accounting/parse-text`，首版采用规则解析，不连接真实大模型。
- 已打通 `POST /api/v2/ai/accounting/draft-from-intent`，复用 `DraftLedgerEntryService.createDraft` 创建 `DRAFT` 草稿，不绕过草稿服务直接写 Mapper。
- 解析结果保留 `sourceType`、`sourceRef`、`rawInput`、`txnType`、`amount`、`note`、`accountNameHint`、`confidence`、`missingFields` 和 `parsedPayloadJson`，用于用户复核。
- 首版可识别常见支出文本、收入文本、金额和账户名称提示；账户不确定时只返回 `accountNameHint`，不强行匹配错误账户。
- Hermes 后续只能调用 parse / draft 接口生成草稿，不能直接确认入账；正式入账仍必须由用户通过 `/api/v2/drafts/{draftId}/confirm` 手动确认。
- shared 层已新增 `aiAccountingApi` 和 `aiAccounting` 类型，供 PC、移动端或 Hermes 调用方复用。
## 文本记账解析 MVP 验证与加固（2026-06-11）

- 已补充服务层验证，覆盖空文本、支出文本、收入文本、金额缺失、类型不确定、日期/账号数字误提取、负金额、标准 `parsedPayloadJson` 字段、空 intent、默认 `HERMES_TEXT` 和 `APP_FORM` 来源兼容。
- 金额提取改为保守候选过滤：跳过日期片段、负数片段和疑似长账号数字；交易类型不确定时不生成可确认金额，等待用户补齐。
- `draft-from-intent` 在 intent 缺少 `sourceType` 时默认使用 `HERMES_TEXT`，在 `missingFields` 为空时由服务端重新计算缺失字段，并重新生成标准 intent JSON。
- 本轮仍保持 parse-text 只返回 intent；draft-from-intent 只通过 `DraftLedgerEntryService.createDraft` 创建 `DRAFT` 草稿，不调用 `QuickEntryService`，不写正式账本。
## PC 草稿箱与文本入口首版（2026-06-15）

- 新增 PC 端 `DraftInbox.vue` 页面，并在顶部导航加入“草稿箱”入口。
- 文本记账入口按两步执行：先调用 `aiAccountingApi.parseText` 生成候选意图，再调用 `aiAccountingApi.draftFromIntent` 创建 `DRAFT` 草稿。
- 草稿列表调用 `draftApi.listDrafts`，支持按 `DRAFT`、`CONFIRMED`、`IGNORED` 状态过滤。
- 草稿详情支持调用 `draftApi.previewDraft` 预览、`draftApi.ignoreDraft` 忽略、`draftApi.confirmDraft` 确认。
- PC 端确认按钮仅在 `preview.confirmSupported=true` 且草稿仍为 `DRAFT` 时启用，并在确认前再次提示：确认后才会正式记账。
- 页面明确提示：AI/文本解析只生成草稿，不会直接写入正式账本；正式入账必须由用户点击确认触发。

## PC 草稿箱与文本入口验证加固（2026-06-16）

- 已加固草稿确认入口：确认按钮必须同时满足当前选中草稿为 `DRAFT`、当前预览 `draftId` 与选中草稿一致、且 `preview.confirmSupported=true`，避免旧预览或切换草稿后误确认。
- 已加固预览异步回填：预览接口返回后只更新仍被选中的同一条草稿；切换草稿、刷新列表或草稿不在当前筛选结果中时会清空旧预览。
- 已加固错误提示：前端优先展示后端返回的 `message` 或 `error`，避免只显示 Axios 默认错误。
- 已保留两步文本记账流程：`parse-text` 只生成候选 intent，`draft-from-intent` 只创建 `DRAFT` 草稿，正式入账仍必须由用户在草稿预览后点击确认。
- 已确认首版仍不包含账户自动补全、分类自动补全、图片 OCR、真实大模型接入和支付通知监听；这些能力留给 Phase3 后续迭代。

## PC 草稿编辑与账户补全首版（2026-06-17）

- 已在 PC 草稿箱详情区和列表操作区加入“编辑草稿”入口，仅 `DRAFT` 状态可编辑；`CONFIRMED` / `IGNORED` 草稿仍不可修改。
- 编辑面板支持补齐 `txnType`、`amount`、`note`、`accountId` 和 `accountNameHint`，保存时只调用 `draftApi.updateDraft` 更新草稿字段，不会直接入账。
- 账户选择复用现有账户 store / 账户 API，不新增后端接口；优先展示现金/活钱相关叶子账户，并显示账户名称、账户类型、`fundUsage` 和币种，用户必须显式选择 `accountId`。
- 保存草稿后会清空旧预览并刷新草稿详情和列表；用户可以立即重新生成预览，待 `preview.confirmSupported=true` 后再二次确认正式记账。
- 文本草稿现在可以从缺少 `accountId` 的状态，通过人工补齐账户与金额等字段进入可预览、可确认闭环。
- 当前仍不包含分类自动补全、图片 OCR、真实大模型接入和支付通知监听；这些能力继续留给 Phase3 后续迭代。

## PC 草稿编辑与账户补全验证加固（2026-06-17）

- 已加固保存流程：保存时立即清空旧 preview，并阻止重复保存，避免旧预览在保存期间被误认为仍可确认。
- 已加固“保存并预览”：保存成功并刷新列表后，使用刷新后的当前草稿生成 preview，而不是旧草稿对象。
- 已加固账户字段校验：`accountId` 必须是大于 0 的正整数，`missingFieldsJson` 对非法或缺失账户会继续标记 `accountId`。
- 已验证保存草稿只调用 `draftApi.updateDraft` 路径，不自动调用 `confirmDraft`；正式入账仍必须由用户在可确认 preview 后点击“确认记账”。
- 已执行 `web/shared` build/type-check、`web/pc-app` build、后端 `mvn test` 和项目编译 hook；`web/pc-app` 全量 type-check 仍受既有历史类型问题阻断，但本轮未引入 `DraftInbox.vue` 新类型错误。

## PC 草稿影响预览增强首版（2026-06-17）

- 已扩展 `DraftPreviewDTO` 与 PC 草稿箱预览展示，确认前可以看到账户名称、账户类型、`fundUsage`、账户影响方向、账户变动金额和风险提示。
- 当前首版仅覆盖 `EXPENSE` / `INCOME`：支出草稿预览显示账户资金减少，`accountDelta` 为负数；收入草稿预览显示账户资金增加，`accountDelta` 为正数。
- `accountId` 不存在、已停用、非 REAL 或当前用户/家庭不可见时，`confirmSupported=false`，并提示用户重新选择账户。
- 首版仍不会生成订单、待结算或持仓变化，`willCreateOrder`、`willCreateSettlement`、`willAffectHolding` 均为 false。
- `fundUsage` 仅作为确认前提示展示，不在 preview 阶段强制阻断；正式入账仍必须由用户点击确认，并通过 `QuickEntryService` 完成。

## 草稿影响预览验证加固（2026-06-17）

- 已验证 `AccountMapper.selectVisibleRealById` 只允许查询当前用户或当前家庭可见的 active REAL 账户，避免越权预览其他账户。
- 不存在、停用、非 REAL 或不可见账户会导致 `confirmSupported=false`，`missingFields` 包含 `accountId`，并返回清晰的 message / warnings。
- 已覆盖 EXPENSE 负向账户影响、INCOME 正向账户影响、非 REAL / 不可见账户、unsupported txnType、缺失 accountId 不查询 mapper、preview 不调用 `QuickEntryService` 等边界。
- preview 阶段只更新草稿预览 JSON，不调用 confirm，不写正式账本；confirm 仍只支持 `EXPENSE` / `INCOME`，不生成订单、待结算或持仓变化。

## 今日待办与确认入口首版（2026-06-17）

- 新增 `GET /api/v2/todos/today` 只读接口，首版聚合当前用户或当前家庭可见的 `DRAFT` 草稿，返回总数、草稿数、待结算数、策略建议数和前 20 条待处理项。
- 待结算和策略建议首版暂未接入，当前明确返回 0，避免把尚未建模的能力误展示为已实现。
- 新增 shared 层 `todoApi` 与 `TodayTodo` / `TodoItem` 类型，供 PC 端、移动端和后续 Hermes 摘要入口复用。
- PC 仪表盘新增“今日待办”卡片，展示待确认草稿数量和待处理列表；点击草稿待办进入草稿箱，不在首页直接确认。
- 今日待办只负责发现和导航，不调用 `confirmDraft`，不写正式账本，不修改账户余额，不生成订单、结算或持仓变化。
- 用户仍必须在草稿箱中查看 preview，且仅在 `preview.confirmSupported=true` 时手动点击确认，才会进入正式记账流程。

## 今日待办与确认入口验证加固（2026-06-17）

- 已补充服务层验证，确认 `draftCount` / `totalCount` 保留真实 DRAFT 总数，`items` 首版只取前 20 条用于首页展示。
- 已补充控制器验证，确认 `/api/v2/todos/today` 使用当前登录用户的 `userId` / `familyId` 调用只读待办服务。
- 已确认待办统计和列表查询复用同一套用户/家庭可见性条件，只统计 `DRAFT`，不把 `CONFIRMED` / `IGNORED` 纳入今日待办。
- 已加固 PC 草稿箱：从今日待办跳转 `/drafts?draftId=xxx` 时，会在当前列表中自动选中对应草稿；找不到时只提示用户切换筛选条件。
- 今日待办入口仍保持只读导航能力，不自动 preview、不自动 confirm、不写正式账本、不修改账户余额、不生成订单或结算。

## Android 原生 App 基础壳首版（2026-06-17）

- 新增 `android-app/` Android 原生工程骨架，采用 Kotlin、Jetpack Compose、Material 3 和 Gradle Kotlin DSL。
- 已包含启动入口、顶部标题栏、底部导航、登录 / Token 配置占位、总览、今日待办、草稿箱、账户 / 流水 / 持仓占位和设置页。
- 已新增 Android 侧 API 边界：`GET /api/v2/todos/today`、`GET /api/v2/drafts`、`GET /api/v2/drafts/{draftId}`、`POST /api/v2/drafts/{draftId}/preview`、`POST /api/v2/drafts/{draftId}/confirm`、`POST /api/v2/drafts/{draftId}/ignore`。
- 已新增最小 DTO：`TodayTodoDto`、`TodoItemDto`、`DraftLedgerEntryDto`、`DraftPreviewDto`，以及 BaseUrl、Token 拦截器和统一网络结果结构。
- 今日待办和草稿箱首版默认使用安全空状态；确认按钮保持禁用，后续必须在后端 `preview.confirmSupported=true` 后才允许用户二次确认。
- 当前未接入真实登录、通知监听、OCR、支付通知解析或真实大模型；移动端只承接查看与确认体验，不绕过后端 confirm，不直接写正式账本。
- 当前本地环境未配置 Gradle 命令和 Android SDK，Android `assembleDebug` 暂未在本机执行；已通过源码静态检查，并确认现有后端与 Web 构建链路未被破坏。

## Android 原生 App 基础壳验证加固（2026-06-17）

- 已补充 `android-app/README.md`，说明 Android Studio / JDK / SDK 要求、`local.properties` 用法、默认开发 BaseUrl 和 token 安全边界。
- 已将底部导航压缩为 5 个入口：总览、待办、草稿、资产、设置；登录与 Token 配置占位合并到设置页，避免移动端底部导航过载。
- 已关闭 Android manifest 的 `allowBackup`，避免金融类 App 首版默认允许系统备份潜在敏感本地状态。
- 已将本地 HTTP 明文访问限制在 debug manifest 中，仅用于模拟器访问 `10.0.2.2` 开发后端；生产构建应使用 HTTPS。
- 已移除未使用的 OkHttp logging-interceptor 依赖，降低首版网络层暴露面。
- 已再次确认 BaseUrl 仅使用 Android 模拟器访问本机后端的开发占位地址，未提交真实 token、cookie、密码或生产私密地址。
- 当前环境仍无 Gradle 命令、Gradle wrapper 和 Android SDK，因此 Android `test` / `assembleDebug` 未在本机执行；现有后端与 Web 构建链路验证通过。

## Android 今日待办与草稿箱接口接入首版（2026-06-18）

- Android 原生 App 的今日待办页已接入 `GET /api/v2/todos/today`，展示草稿、结算、建议数量和待办列表。
- 草稿待办支持从今日待办跳转到草稿箱，并按 `refId` 打开对应草稿详情。
- 草稿箱已接入 `GET /api/v2/drafts`、`GET /api/v2/drafts/{draftId}`、`POST /api/v2/drafts/{draftId}/preview`、`POST /api/v2/drafts/{draftId}/ignore` 和 `POST /api/v2/drafts/{draftId}/confirm`。
- 移动端确认按钮必须同时满足当前草稿仍为 `DRAFT`、当前预览 `draftId` 与草稿 ID 一致、且 `preview.confirmSupported=true`；点击确认前仍会二次提示。
- 设置页的 BaseUrl 和 Bearer Token 仅保存在当前内存状态中，不写入源码、不持久化、不打印明文 Token。
- 首版仍不包含真实登录、安全 Token 存储、通知监听、OCR、支付通知解析或真实大模型接入。

## Android 今日待办与草稿箱接口接入验证加固（2026-06-18）

- 已加固 BaseUrl 装配：非法或缺少 `http://` / `https://` 的 BaseUrl 不再在 Compose 组合期直接导致 App 崩溃，页面会显示接口配置错误并引导回设置页修正。
- 已加固 DTO 空值兼容：`TodayTodoDto.items`、`DraftPreviewDto.warnings`、`DraftPreviewDto.missingFields` 支持后端返回 `null` 时安全降级为空列表。
- 已加固从今日待办进入草稿箱的选中逻辑：列表刷新时优先保留目标 `draftId`，避免被默认第一条草稿覆盖。
- 已复核草稿确认边界：确认按钮仍必须满足当前草稿为 `DRAFT`、当前预览 `draftId` 匹配、且 `preview.confirmSupported=true`，并保留二次确认弹窗。
- 首版仍未接入真实登录、安全 Token 持久化、通知监听、OCR、支付通知解析、企业微信入口或真实大模型。

## Android 通知监听基础壳首版（2026-06-22）

- Android Manifest 已声明 `NotificationListenerService`，仅使用系统要求的 `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`，未新增短信、通讯录、相册等无关敏感权限。
- 设置页新增通知监听授权状态、打开系统授权页入口和手动刷新状态按钮；只有用户手动授权后 App 才能读取通知。
- 新增本地内存候选通知队列，通知事件仅保存在进程内存中，不落库、不写文件、不持久化完整通知原文。
- 新增保守 `PaymentNotificationParser`，只识别疑似支付关键词、金额和来源提示，不调用后端创建草稿。
- 设置页展示候选通知脱敏摘要，并明确当前只做本地候选，不自动生成草稿、不自动 preview、不自动 confirm、不写正式账本。
- 首版仍未接入真实登录、安全 Token 持久化、OCR、支付通知到草稿闭环、企业微信入口或真实大模型。

## Android 通知监听基础壳验证加固（2026-06-22）

- 已为 `PaymentNotificationParser` 补充 JVM 单元测试，覆盖微信/银行支付、普通聊天通知、验证码、订单号和退款到账候选场景。
- 金额识别规则已加固为必须带货币前缀或金额单位，避免把普通数字、验证码、订单号、手机号、卡号等误识别为金额。
- 通知监听服务已过滤 ongoing 常驻通知和 group summary 聚合通知，减少系统噪音进入本地候选队列。
- 已复核 Manifest 只声明通知监听服务所需权限，未新增短信、通讯录、相册、定位、录音等无关敏感权限。
- 当前仍只生成本地内存候选，不自动生成草稿、不自动 preview、不自动 confirm、不写正式账本。

## Android 通知候选手动生成草稿（2026-06-22）

- 设置页候选通知卡片新增“生成草稿”手动入口，仅在疑似支付、金额明确且 API 配置可用时启用。
- 点击后会先弹出确认说明，只调用 `parse-text` 和 `draft-from-intent`，生成的仍是 `DRAFT` 草稿。
- Android 侧传给后端的是脱敏候选摘要、来源、标题和金额等最小必要文本，不持久化完整通知原文。
- 生成成功后显示草稿 ID，并可跳转草稿箱；后续 preview / confirm 仍必须由用户在草稿箱手动触发。
- 当前仍未接入企业微信入口、OCR、真实大模型、真实登录和安全 Token 持久化。

## Android 通知候选生成草稿验证加固（2026-06-23）

- 已将候选通知到草稿的 rawInput 和按钮可用性判断抽成纯 Kotlin helper，便于测试和后续复核。
- 已补充 helper 单元测试，覆盖脱敏 rawInput 不包含完整 rawText、普通聊天不可创建草稿、缺失或非数值金额不可创建草稿、已创建后不重复创建。
- Compose 候选列表已使用 `candidate.id` 作为稳定 key，避免列表重排时将草稿生成状态错挂到其他候选通知。
- 已复核通知候选流程不会自动 preview、不会自动 confirm、不会写正式账本。


## Android 草稿编辑与账户补全首版（2026-06-24）

- 草稿箱详情页新增 DRAFT 草稿编辑面板，支持补齐 `txnType`、`amount`、`note`、`accountId` 和 `accountNameHint`。
- Android 侧新增 `PUT /api/v2/drafts/{draftId}` 调用，保存只更新草稿候选内容，不写正式 `ledger_txn`、不修改账户余额、不生成订单或交易。
- `accountId` 首版采用手动输入，必须是大于 0 的后端真实账户 ID；`accountNameHint` 只是人工提示，不会替代真实账户 ID。
- 保存草稿会立即清空旧 preview；“保存并预览”会使用保存后的草稿重新调用 preview，不会自动 confirm。
- 确认按钮仍必须满足当前草稿为 `DRAFT`、当前 preview 的 `draftId` 与草稿 ID 匹配、且 `preview.confirmSupported=true`，并保留二次确认。
- 当前仍未接入企业微信入口、真实登录、安全 Token 持久化、OCR 或真实大模型。

## Android 可信构建链与 APK 验证（2026-07-14）

- 已为 `android-app/` 建立 Gradle Wrapper 构建入口，使用 Gradle 8.7、JDK 17、Android SDK 35。
- 已新增项目级 `gradle.properties`，启用 AndroidX，并保留 AGP 8.5.2 与 compileSdk 35 的明确抑制配置，不升级 AGP/Kotlin/Compose 技术栈。
- 已真实执行 `testDebugUnitTest`、`assembleDebug` 和 `lintDebug`，均通过；debug APK 已生成，大小 10,483,798 bytes，SHA-256 为 A89F380790DDFA6090E1CC8047D6B711D9907BEE026194A48EAFE75EC04D4368。
- 构建链只产出 debug APK，不生成 release signing key，不提交 APK、SDK、Gradle 缓存或本机 `local.properties`。
- 当前移动端边界不变：不接真实模型、不自动 confirm、不连接生产数据库、不修改真实账本、账户、持仓、订单或交易数据。

## Android 真实登录与安全 Token 持久化（2026-07-16）

- 已接通后端现有 `POST /api/v2/auth/login` JWT 登录契约，未新增 refresh token、OAuth/OIDC 或认证绕过逻辑。
- App 已建立初始化、未登录、登录中、已登录、失败与失效状态；启动恢复完成前不会进入受保护页面。
- 登录 Token 由 Android Keystore 管理的 AES-GCM 密钥加密持久化，密码不保存，Token 不展示、不打印、不进入调试 UI。
- 同源业务请求自动附加 Bearer Token；登录端点排除陈旧认证头，受保护请求 401 后清除会话且不自动重试。
- 设置页已提供幂等退出登录入口，远端无状态 logout 失败时仍保证本地凭据清理和返回登录页。
- 已新增 JVM 单元测试覆盖认证会话、仓库和网络拦截器；2026-07-16 真实通过 29 项单元测试、debug APK 构建和 lint。
- 真实大模型、自动入账、自动交易仍未接入；现有草稿 preview / confirm 人工边界未改变。

## Android 图片 OCR 到草稿闭环（2026-07-16）

- 草稿箱已新增系统 Photo Picker 单图入口，不申请广泛相册权限，不后台扫描相册或截图目录。
- 已接入随 APK 分发的 Google ML Kit 中文离线文本识别模型；图片和 URI 不进入网络层、不持久化、不写日志。
- OCR 页面按图片请求 ID 隔离异步结果，支持识别文字编辑，并将“图片选择、文字识别、候选解析、草稿创建、进入草稿箱”明确分阶段。
- 只有用户主动点击后，编辑后的文字才发送到现有 `parse-text`；候选 intent 复核后才调用 `draft-from-intent` 创建 `DRAFT`。
- 来源类型复用后端已支持的 `APP_FORM`，未修改数据库结构或新增不兼容枚举；创建后不自动 preview、不自动 confirm。
- 已通过 36 项 Android JVM 单元测试、`assembleDebug` 和 `lintDebug`；新 debug APK SHA-256 为 B1DBD06EEC2CB95DBE5ABE5BAF60EB8C1D47117CF9DA4170A754941B7545B2C0。
- 真实设备图片选择、中文支付截图准确率和厂商 URI 兼容性仍需手工验证；真实大模型、自动入账和自动交易继续不在本阶段范围内。

## 草稿创建幂等基础（2026-09-27，Android v0.6 前置）

- 为 Android v0.6“可靠记账采集”补齐服务端草稿创建可安全重放的前置能力，对应任务 `task-mydca-v06-draft-idempotency-20260926`，
  详细说明见 `docs/mydca_android_v06_draft_idempotency_20260926.md`。
- `DraftLedgerEntryMapper` 新增 `selectVisibleBySource`：按当前用户/家庭可见性 + `source_type` + `source_ref` 查询既有草稿，
  不限制草稿状态；本任务当时未新增数据库 schema 与唯一约束，复用既有 `idx_draft_ledger_source` 索引
  （数据库唯一约束已于 v0.8 补齐，见下文“草稿强幂等与并发去重（2026-09-27，MyDCA v0.8）”）。
- `DraftLedgerEntryService.createDraft` 在插入前先做幂等查找：`sourceRef` 非空且命中时直接返回既有草稿（`DRAFT` / `CONFIRMED` / `IGNORED` 均可），
  不再插入新行；`sourceRef` 为空时保持原有非幂等行为，不按金额或备注做模糊去重。
- `draft-from-intent` 与 `POST /api/v2/drafts` 共用同一 `createDraft` 入口，因此两条创建路径遵循同一套重放语义；重放路径不会调用 `QuickEntryService`。
- 已明确 Android 三类来源的 `sourceRef` 约定：手工文本与 OCR 使用每次输入/选图生成的 `android-ocr-<requestId>`，
  通知候选使用 `fingerprint`，同一次采集尝试的重试沿用同一 `sourceRef`。
- 本轮服务端测试由 46 项增至 55 项，新增覆盖重复请求返回同一草稿、不同来源创建不同草稿、跨用户/家庭不互相命中、
  空 `sourceRef` 保持旧行为、已确认/已忽略来源重试不新建草稿、重放不触发 `QuickEntryService`。

## Android 安全草稿 Outbox 与三入口恢复（2026-09-27，Android v0.6）

- 对应任务 `task-mydca-v06-secure-outbox-20260926`，依赖服务端幂等前置 `task-mydca-v06-draft-idempotency-20260926`，
  详细说明见 `docs/mydca_android_v06_secure_outbox_20260926.md`。
- 新增 `com.timelordtty.mydca.outbox` 抽象：`DraftOutboxQueue` / `DraftOutboxStorage` / `EncryptedDraftOutboxStorage` /
  `DraftOutboxCodec` / `DraftOutboxRetryPolicy` / `DraftOutboxErrorClassifier` / `DraftCreationGateway` / `AndroidDraftOutbox`。
- Outbox 只承载“创建草稿”链路：唯一的网络依赖 `DraftCreationGateway` 只有 `createDraft`，类型上不存在
  preview / confirm / ignore / QuickEntry / 正式账本入口，重试不可能越过人工确认边界。
- 加密落盘：`core/security` 抽取 `SecretCipher` / `AesGcmSecretCipher` / `AndroidKeystoreAesKey` / `SecureKeyValueStore`，
  `KeystoreTokenStore` 复用同一实现（别名与偏好键不变，已登录会话不丢失）；Outbox 使用独立密钥别名与独立偏好文件，
  只写入密文与随机 IV，且字段白名单里不存在 Token、密码、Cookie、图片 / URI 或通知完整原文。
- 三类入口统一 sourceRef 规则：手工文本与 OCR 使用每次采集生成的 `android-ocr-<requestId>`，通知候选使用 `fingerprint`，
  同一次采集的重试始终复用同一 `sourceRef`；解析失败不入队，缺少稳定 `sourceRef` 不入队。
- 错误分类：网络中断 / 超时 / 5xx 可重试（有限退避 30s/120s/600s/1800s，默认上限 5 次，用尽转 `EXHAUSTED`）；
  401 / 403 转 `AUTH_PAUSED` 等待重新登录；其他 4xx 与未知异常转 `BLOCKED`，只展示原因供用户修改或丢弃，不自动循环重试。
- 受控重试触发点：App 启动、从后台恢复（`ON_START`）、进入草稿箱页面、用户“立即重试”/“立即重试全部”/“丢弃”；
  未引入常驻后台服务或系统级调度；批量重试单实例执行，与手动重试互斥。
- 草稿箱新增“本地待重试草稿”区块：展示待重试数量、来源、创建时间、队列状态、重试次数、下次自动重试时间与最近失败原因，
  默认只展示脱敏摘要；重试成功即出队，服务端返回 `DRAFT` 时提供“打开草稿”引导，返回既有 `CONFIRMED` / `IGNORED`
  草稿时只展示状态、不发起任何确认动作。
- 本轮 Android 测试由 68 项增至 102 项（22 个测试类，0 失败），新增覆盖加密往返、明文不落盘、sourceRef 稳定、
  transient 入队、401 暂停、4xx 不自动重试、重试成功出队、既有草稿出队、重试不触达 preview / confirm、
  进程重启恢复、退避与单实例；`assembleDebug` 与 `lintDebug` 通过（0 error，2 条既有 warning）。

## Android v0.6.0 发布加固与 APK 交付证据（2026-09-27）

- 对应任务 `task-mydca-v06-release-hardening-20260926`，前置为 `task-mydca-v06-draft-idempotency-20260926` 与 `task-mydca-v06-secure-outbox-20260926`，
  详细说明见 `docs/mydca_android_v06_reliable_capture_20260926.md`。
- 版本核对：`versionName = 0.6.0`、`versionCode = 7`；已构建 APK 经 `aapt2 dump badging` 实测为 `versionCode='7' versionName='0.6.0'`。
- APK 工作流命名收口：artifact `mydca-android-v0.6.0-<sha>`、文件 `MyDCA-Board-v0.6.0-<short-sha>.apk`，`SHA256SUMS.txt` 同步，
  继续使用一次性 debug 签名且不提交 APK；触发条件不变（`workflow_dispatch` 或 `v2` 上命中 `android-app/**` / 工作流文件的推送）。
- 最终回归新增 `DraftOutboxReleaseRegressionTest`：三入口（手工文本 / OCR / 通知候选）端到端只创建 `DRAFT` 并在重试成功后出队；
  并以类型守卫断言 `DraftCreationGateway` 只声明 `createDraft`、outbox 各类不暴露 `preview` / `confirm` / `ignore` / `quickentry` 能力。
- 本轮验证：后端 `mvn -B test` 55 项通过；Android `testDebugUnitTest` 23 个测试类共 104 项通过（由 102 项增至 104 项）、
  `assembleDebug` 通过、`lintDebug` 通过（0 error，2 条既有 warning）；`scripts/post-task-compile-hook.ps1` 通过。
- 本地 Debug APK：55,916,141 bytes，SHA-256 `6149D8FD47C8A4A1E9FA38726979341959892F344913884BC8860C3F4859C891`，APK 不提交到 Git。
- CI 制品状态：本进程按指令不推送，工作流未触发，Run ID / Artifact ID / APK 文件名 / SHA-256 均为 NOT_PRODUCED；
  因此本轮不声称 APK 交付完成，需在真实推送后由工作流产出并回填。
## Android v0.7.0 快速记账采集中心（2026-09-27）

- 对应任务 `task-mydca-android-v07-quick-capture-hub-20260927`（owner 直接批准，L3，`allowed_paths` 仅 `android-app/**`、`docs/**`、
  `.github/workflows/android-test-apk.yml`），详细说明见 `docs/mydca_android_v07_quick_capture_hub_20260927.md`。
- 目标是把原本分散在草稿箱 / 今日待办 / 设置页的四条采集路径（手工文本、图片 OCR、支付通知候选、本地 Outbox）收拢为一个
  “快速记账采集中心”，只改采集体验，不改任何后端账本语义。
- 新增 `ui/QuickCaptureHub.kt` 纯 Kotlin helper：入口可见规则（主要页面可见、设置页隐藏）、四个入口的面板条目与数量徽标、
  点击后的确定性路由决策（`QuickCaptureDecision`）、一次性定位信号（`QuickCaptureFocus`）与只读计数（`QuickCaptureCounts`）。
- 已登录主 `Scaffold` 新增“记一笔”`ExtendedFloatingActionButton`，点击打开 `QuickCaptureSheet`（`ModalBottomSheet`）：
  手工记一笔 / 图片识别复用既有 `OcrDraftScreen`；支付通知候选路由到今日待办候选区；待重试路由到草稿箱“本地待重试草稿”区，
  两处均显示定位标记；空候选 / 空 Outbox 仍可打开并显示“暂无待处理”。面板不含任何解析 / 预览 / 正式确认按钮。
- 返回体验收口：打开 / 关闭面板不改变当前页面；每次点击入口都会清空旧 `selectedDraftId` 与通知候选 `candidateId`；
  主动切换底部导航时复位一次性定位与录入模式，并新增 `NotificationNavigationTarget.clear()` 丢弃待处理的通知导航目标；
  未引入导航框架重构。
- 隐私与计数：候选数取自本机脱敏候选 store（排除 `DISMISSED`），重试数取自加密本地 Outbox，均为本地 StateFlow，
  无新增网络轮询、无新增权限、无新增明文落盘；面板文案不展示内部类名 / 枚举名。
- 安全边界不变：快速入口与面板不调用 parse / draft / preview / confirm；手工与 OCR 仍需显式点击才解析、再显式点击才创建 DRAFT；
  通知候选不会自动生成草稿；Outbox 只重试创建 DRAFT；草稿最终确认仍由 `DraftInboxScreen` 的 `DRAFT` + 当前 preview +
  `confirmSupported=true` + 二次确认守门。
- 版本收口：`versionName = 0.7.0`、`versionCode = 8`（`aapt2 dump badging` 实测 `versionCode='8' versionName='0.7.0'`）；
  工作流制品名与文件名同步为 `mydca-android-v0.7.0-<sha>` / `MyDCA-Board-v0.7.0-<short-sha>.apk`，仍为一次性 debug 签名，APK 不提交到 Git。
- 本轮验证：Android `testDebugUnitTest` 24 个测试类共 119 项通过（由 23 类 104 项增至 24 类 119 项，新增 15 项 `QuickCaptureHubTest`）、
  `assembleDebug` 通过、`lintDebug` 通过（0 error，2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 通过。
- 本轮未推送：CI 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）为 `NOT_PRODUCED`，不得用本地 APK 哈希冒充 CI artifact。

## 草稿强幂等与并发去重（2026-09-27，MyDCA v0.8）

- 对应任务 `task-mydca-v08-draft-strong-idempotency-20260927`（owner 直接批准，L3），
  详细说明见 `docs/mydca_v08_draft_strong_idempotency_20260927.md`；目标是补齐 v0.6 起持续保留的可靠性缺口，
  把“同一采集事件重复提交只产生一条草稿”从应用层幂等提升为数据库级可验证的强幂等。
- 数据库唯一性：新增增量 migration `sql/updatesql/20260927/`（01 只读预检 → 02 空来源归一化为 `NULL` → 03 添加唯一键），
  添加 `uk_draft_ledger_user_source (owner_user_id, source_type, source_ref)` 与
  `uk_draft_ledger_family_source (owner_family_id, source_type, source_ref)`；两个唯一键合起来正好等于
  “同一个人或同一个家庭可见”的可见作用域，不同用户 / 家庭之间互不冲突；`source_ref IS NULL` 不参与唯一性判定，
  保留“空来源不强制幂等”的旧行为；未改写 `20260610` 的历史建表脚本。
- 预检与阻断：预检脚本按用户 / 家庭作用域列出历史重复草稿与空来源明细；存在重复时唯一键创建会以 1062 失败，
  迁移不含任何 `DELETE`，不静默删除真实数据，由人工决定保留哪一条；脚本末尾给出 `DROP INDEX` 回退语句。
- 并发恢复：`DraftLedgerEntryService.createDraft` 先保留应用层幂等查询，仅捕获 `DuplicateKeyException`，
  按同一可见作用域重查并返回既有草稿（`DRAFT` / `CONFIRMED` / `IGNORED` 均直接返回）；重查不到时原样抛出冲突；
  其他数据库异常不进入恢复分支。恢复路径不 preview、不 confirm、不调用 `QuickEntryService`、不写正式账本 / 订单 / 结算 / 持仓。
- 空来源归一化：`sourceRef` 为空 / 空白串时按 `NULL` 落库，避免空串被唯一键当成有效来源参与去重；
  `updateDraft` 改写 `source_ref` 撞唯一键时转为明确业务提示，不再表现为未处理的数据库异常。
- Android 侧未改动代码：核对确认手工文本 / OCR 的 `android-ocr-<requestId>` 与通知候选 `fingerprint` 仍是
  “一次采集事件”级别且重试复用同一值，Outbox 只重试创建 DRAFT，未新增后台常驻重试。
- 本轮验证：backend `mvn -B test` 70 项通过（由 55 项增至 70 项：新增 10 项并发恢复 + 5 项迁移等价可验证方案）；
  Android `testDebugUnitTest --rerun-tasks` 24 类 119 项通过（与 v0.7 基线一致，无回归）；
  `scripts/post-task-compile-hook.ps1` 通过；`git diff --check` 通过。未连接任何数据库、未执行 migration、未推送。

## Android v0.8.0 外部分享快速采集（2026-09-27）

- 对应任务 `task-mydca-android-v080-external-share-capture-20260927`（owner 直接批准，L3，`allowed_paths` 为 `android-app/**`、`docs/**`、
  `README.md`、`.github/workflows/android-test-apk.yml`），详细说明见 `docs/mydca_android_v080_external_share_capture_20260927.md`。
- 目标：把系统 Share Sheet 的**文本**与**单张图片**安全导入现有手工文本 / 本地 OCR 采集流程，只做预填，并发布 v0.8.0。
- 入口：`AndroidManifest.xml` 的 `MainActivity` 只新增一个 `ACTION_SEND` intent-filter（`text/*` + `image/*`），
  不声明 `ACTION_SEND_MULTIPLE`，不新增任何广泛权限，也未修改 `launchMode`；`onCreate` 与 `onNewIntent` 共用同一接收逻辑。
- 解析层：新增 `share/` 包（`ExternalSharePayload`、`ExternalShareRequest`、`ExternalShareResolver`、`ExternalShareRejection`、
  `ExternalShareResolution`、`ExternalShareSourceRef`、`ExternalSharePendingStore`、`SharedImageOcrGate`），全部为纯 Kotlin，
  可直接 JVM 单元测试；`MainActivity` 只读 intent 字段，不解析候选、不创建草稿、不发起网络请求。
- 隐私：分享文本与图片 URI 只存在当前进程内存，不写偏好设置 / 文件、不打印、不上传；同一 payload 只消费一次，
  未登录期间保留到登录后消费一次，不为跨进程恢复持久化原文；文本超限安全截断并提示。
- 图片：收到分享 Intent 不自动 OCR，必须用户点击“使用此图片并识别”；复用既有 ML Kit 中文模型，图片不上传不落盘；
  只接受系统授权临时 `content://` URI，`file://` 与未知 scheme 安全拒绝并给中文提示。
- sourceRef：`android-share-text-<uuid>` / `android-share-image-<uuid>`，不同分享事件必然不同，不含分享原文 / 文件名 / URI；
  解析、创建 DRAFT 与 Outbox 重试重放全程复用同一值（既有 `android-ocr-<requestId>` 与通知 `fingerprint` 规则未改动）。
- 一次性状态：新增 `ui/ExternalShareHub.kt`（落点决策 + 预填文案）与 `ui/ExternalShareCaptureSession`：
  返回 / 取消、主动切换底部导航、成功生成 DRAFT 都会清空 share target，并同时清空旧 `selectedDraftId`、
  `NotificationNavigationTarget` 与 `QuickCaptureFocus`，避免一次性导航状态互相残留；v0.7 四入口与底部导航未改动。
- 版本：`versionName = 0.8.0`、`versionCode = 9`（`aapt2 dump badging` 实测 `versionCode='9' versionName='0.8.0'`）；
  工作流制品名与文件名同步为 `mydca-android-v0.8.0-<sha>` / `MyDCA-Board-v0.8.0-<short-sha>.apk`，仍为一次性 debug 签名且不提交 APK。
- 新增 4 个测试类 37 项（`ExternalShareResolverTest` 11、`ExternalSharePendingStoreTest` 9、`SharedImageOcrGateTest` 5、
  `ExternalShareCaptureRegressionTest` 12），覆盖解析规则、拒绝路径、超长边界、一次性消费、登录前后消费、预填不解析 / 不建档、
  图片不自动 OCR、sourceRef 稳定性与导航清理，并用反射断言分享层不存在 preview / confirm / QuickEntry 能力。
- 本轮验证：`testDebugUnitTest` 28 类 156 项通过（由 24 类 119 项增至 28 类 156 项）、`assembleDebug` 通过、
  `lintDebug` 通过（0 error，2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 通过（成功静默）。未连接任何数据库、未推送。
- 本地 Debug APK：55,800,680 bytes，SHA-256 `E5E6737C11C305BFF76639F3260E1FF1028FC28E32F8CF67387723E835C7236A`（本机真实构建输出，但 debug APK 本地字节不可复现：同一份源码重复 `assembleDebug` 的体积与 SHA-256 都会变化，该值只作本机观察；APK 不提交到 Git）；
  本进程未推送，CI 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）为 `NOT_PRODUCED`，不得用本地哈希冒充。

## Android v0.9.0 桌面快速记账小组件（2026-09-28）

- 对应任务 `task-mydca-android-v090-home-widget-quick-capture-20260928`（owner 直接批准，L3，`allowed_paths` 为 `android-app/**`、`docs/**`、
  `README.md`、`.github/workflows/android-test-apk.yml`），详细说明见 `docs/mydca_android_v090_home_widget_quick_capture_20260928.md`。
- 目标：新增一个安静、不打扰的桌面快速记账小组件，把现有“记一笔”能力暴露到系统桌面，并发布 v0.9.0；只新增系统级入口，不新增任何自动记账逻辑。
- 形态：系统 `AppWidgetProvider` + `RemoteViews`（未引入 Jetpack Glance、未做架构重构）；完整尺寸四个静态中文入口，
  尺寸不足按 `WidgetSizePolicy` 折叠为“记一笔 + 草稿箱”。
- 导航：新增 `widget/` 纯 Kotlin 层（`WidgetNavigationTarget`、`WidgetNavigationResolver`、`WidgetNavigationPendingStore`、
  `WidgetNavigationHub`、`WidgetNavigationArbiter`、`WidgetSizePolicy`、`WidgetEntryPoints`、`WidgetEntryViews`、`WidgetNavigationIntents`）+
  `QuickCaptureWidgetProvider`；点击只构造显式 Intent 指向本 App `MainActivity`，action 只取自受控枚举，Intent 不带任何 extra。
- 一次性优先级固定为“桌面小组件 > 外部分享 > 通知候选”：`MyDcaApp` 把三个入口合并为一个仲裁消费点，
  接管时同时清空 `ExternalSharePendingStore`、`NotificationNavigationTarget`、`selectedDraftId` 与 `QuickCaptureFocus`，
  主动切换底部导航与进入快速采集也会清空小组件目标，避免一次性导航状态互相残留。
- 安全：小组件层无 repository / network / parse / draft / preview / confirm 能力；`updatePeriodMillis=0`，
  无后台轮询 / Alarm / WorkManager / 前台服务 / 常驻通知；receiver 只按系统 AppWidget 协议最小开放，
  不申请新权限，`MainActivity` 未新增 intent-filter；未登录时目标只在当前进程保留一次。
- 版本：`versionName = 0.9.0`、`versionCode = 10`（`aapt2 dump badging` 实测 `versionCode='10' versionName='0.9.0'`）；
  工作流制品名与文件名同步为 `mydca-android-v0.9.0-<sha>` / `MyDCA-Board-v0.9.0-<short-sha>.apk`，仍为一次性 debug 签名且不提交 APK。
- 新增 7 个测试类 39 项，覆盖解析与安全忽略、一次性消费、登录前后消费、主动导航清理、优先级仲裁、尺寸折叠、
  PendingIntent 身份独立，以及 Manifest / appwidget-provider / 布局静态契约。
- 本轮验证：`testDebugUnitTest` 35 类 195 项通过（由 28 类 156 项增至 35 类 195 项）、`assembleDebug` 通过、
  `lintDebug` 通过（0 error，2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 通过（成功静默）。未连接任何数据库、未推送。
- 本地 Debug APK：55,816,715 bytes，SHA-256 `C2CC3CBD10EFCD20177450CC367ACAF2C273A0E8C050BBCAFBEF58E704116617`（本机观察，debug APK 本地字节不可复现，不作为制品身份）；
  本进程未推送，CI 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）为 `NOT_PRODUCED`，不得用本地哈希冒充。

## Android / 后端 v0.10.0 TRANSFER 转账草稿闭环（2026-09-28）

- 对应任务 `task-mydca-v010-transfer-draft-loop-20260928`（owner 直接批准，L3），详细说明见 `docs/mydca_v010_transfer_draft_loop_20260928.md`。
- 目标：把草稿正式确认从 EXPENSE / INCOME 扩展到 TRANSFER，让主人可以补齐转出 / 转入账户、先看到双账户影响预览、再二次确认生成一笔正式转账；
  不改变“DRAFT → fresh preview → 主人二次确认 → 正式账本”的安全边界。
- 候选结构：`txnType=TRANSFER` + `accountId` / `accountNameHint`（转出）+ `targetAccountId` / `targetAccountNameHint`（转入）+ `amount` + `note`；
  `sourceAccountId` / `cashAccountId`、`toAccountId` / `destinationAccountId` 只作兼容读取别名，写回只用标准字段。
- 文本解析：转账语义（`转账` / `转到` / `转入` / `转出` /「从…到…」）优先于「到账 / 付款 / 支付」；名称提示只进候选，不自动映射账户 ID，
  缺 ID 时仍只生成 DRAFT 并把 `accountId` / `targetAccountId` 记入 `missingFields`。
- 预览：两个账户都必须可见、active REAL、叶子、互不相同、币种一致；TRANSFER 不套用 EXPENSE 的 SPENDABLE 规则，跨资金用途转账给出中文风险提示；
  新增 `targetAccountId` / `targetAccountName` / `targetAccountType` / `targetFundUsage` / `targetAccountDelta`。
- 正式入账：新增 `QuickEntryService.quickTransfer`，经既有 `LedgerService.createTransaction(..., "TRANSFER_OUT", ...)` 创建一笔平衡交易
  （转出 CREDIT + 转入 DEBIT）；流水页仍展示转出 / 转入两条视图，底层只有一笔交易，不计入收入 / 支出净现金流；未新增表或 migration。
- confirm：TRANSFER 必须重新 `buildPreview`，只有 `confirmSupported=true` 才调用 `quickTransfer`；`txnId` 写回 `confirmTxnId`，重复确认幂等，
  `IGNORED` 草稿不可确认，不存在自动 preview / confirm。
- Android `versionName = 0.10.0`（`versionCode = 11`）：草稿编辑支持支出 / 收入 / 转账三种类型与转出 / 转入双账户，切回支出 / 收入清空目标账户；
  确认弹窗标题为「确认将 ¥X 从 A 转到 B？」；PC `web/shared` 与草稿箱补齐最小查看 / 预览与安全编辑兼容。
- 本轮验证：后端 `mvn -B test` 95 项通过（由 70 项增至 95 项）、Android `testDebugUnitTest` 35 类 206 项通过（由 195 项增至 206 项）、
  `assembleDebug` 通过、`lintDebug` 通过（0 error，2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 通过（成功静默）。未连接任何数据库、未推送。
- 本地 Debug APK：55,821,214 bytes，SHA-256 `4D5C1443EE30B0A8315ECBB848A3FFAD62ED3B675861236361B8971552C087DB`（本机观察，debug APK 本地字节不可复现，不作为制品身份）；
  本进程未推送，CI 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）为 `NOT_PRODUCED`，不得用本地哈希冒充。

## Android / 后端 v0.11.0 投资买入 / 申购草稿闭环（2026-09-28）

- 对应任务 `task-mydca-v011-invest-buy-draft-loop-20260928`（owner 直接批准，L3），详细说明见 `docs/mydca_v011_invest_buy_draft_loop_20260928.md`。
- 目标：把草稿正式确认从 EXPENSE / INCOME / TRANSFER 扩展到投资 BUY（场内买入）与 SUBSCRIPTION（场外申购），
  让主人可以补齐真实产品 + 单一资金来源账户、先看到订单与资金影响预览、再二次确认创建系统内 PENDING 订单；
  不改变「DRAFT → fresh preview → 主人二次确认 → 正式订单 / 账本」的安全边界。
- 候选结构：`txnType=BUY / SUBSCRIPTION` + `productId`（必须由主人明确选择）+ `productNameHint`（仅提示）+ `amount`
  + `accountId`（本轮单资金来源）+ `accountNameHint` + `note` + 可选 `expectedNavDate` / `expectedConfirmDate`。
- 文本解析：`买入` → BUY，`申购` / `定投` → SUBSCRIPTION，优先级高于 `买` / `付款` / `支付` 等 EXPENSE 关键词，
  `买奶茶 30` 仍为 EXPENSE；规则解析只识别语义、提取金额与产品名称提示，禁止把名称提示映射成真实 `productId`，
  缺 ID 时只生成 DRAFT 并把 `productId` / `accountId` 记入 `missingFields`。
- 只读预览：产品必须存在且启用、币种与资金账户一致；资金账户必须可见、active REAL、叶子、可用余额足够；
  资金用途一般投资只允许 `INVESTABLE`，`BOND_REPO` 额外允许 `RESERVED`，`SPENDABLE` / 普通 `RESERVED` / 父账户 / VIRTUAL 一律阻断；
  影响口径为 `accountDelta = -amount`、`receivableDelta = +amount`，`willCreateSettlement=false`、`willAffectHolding=false`；
  preview 绝不调用 `OrderService` / `LedgerService` / `SettlementService`。
- 正式入账：新增 `OrderService.createInvestmentDraftOrder(...)` 安全入口（只允许 BUY / SUBSCRIPTION，重新校验产品、币种、资金用途、
  账户可见性与可用余额），复用既有 `createOrder` 创建 `status=PENDING` 订单并生成下单付款账本（CASH CREDIT + RECEIVABLE DEBIT）；
  订单仍需后续 SettlementService 结算并影响持仓；未新增表或 migration。
- confirm：BUY / SUBSCRIPTION 必须重新 `buildPreview`，只有 `confirmSupported=true` 才调用安全入口；订单 ID 写回 `confirmOrderId`，
  重复确认幂等，`IGNORED` 草稿不可确认，不存在自动 preview / confirm / settle。
- Android `versionName = 0.11.0`（`versionCode = 12`）：草稿编辑支持支出 / 收入 / 转账 / 买入 / 申购，投资表单支持真实产品 + 单一资金账户，
  确认弹窗标题为「确认创建【产品】买入/申购订单 ¥X？」；PC `web/shared` 与草稿箱同步投资类型与预览字段。
- 本轮验证：后端 `mvn -B test` 121 项通过（由 95 项增至 121 项）、Android `testDebugUnitTest` 35 类 221 项通过（由 206 项增至 221 项）、
  `assembleDebug` 通过、`lintDebug` 通过（0 error，2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 通过（成功静默）。未连接任何数据库、未推送。
## Android / 后端 v0.12.0 投资卖出 / 赎回草稿闭环（2026-09-28）

- 对应任务 `task-mydca-v012-invest-sell-redeem-draft-loop-20260928`（owner 直接批准，L3），详细说明见 `docs/mydca_v012_invest_sell_redeem_draft_loop_20260928.md`。
- 目标：把草稿正式登记从 EXPENSE / INCOME / TRANSFER / BUY / SUBSCRIPTION 扩展到投资 SELL（场内卖出）与 REDEMPTION（场外赎回），
  让主人可以补齐真实产品 + 该产品真实持仓来源 + 份额 + 到账账户、先看到可用份额与剩余份额预览、再二次确认后仅创建系统内 PENDING 记录；
  不改变「DRAFT → fresh preview → 主人二次确认 → 仅内部 PENDING」的安全边界。
- 候选结构：`txnType=SELL / REDEMPTION` + `productId`（必须由主人明确选择）+ `productNameHint`（仅提示）+ `shares`
  + `sourceAccountId`（持仓来源）+ `sourceAccountNameHint` + `targetAccountId`（到账账户）+ `targetAccountNameHint` + `note`
  + 可选 `expectedNavDate` / `expectedConfirmDate`。
- 文本解析：`卖出` → SELL、`赎回` → REDEMPTION，优先级高于 `买` / `付款` / `支付` 等 EXPENSE 关键词；
  只提取 `shares` 与产品名称提示，禁止把名称提示映射成真实 `productId`，也绝不自动匹配持仓来源或到账账户；
  缺字段时只生成 DRAFT 并写入 `missingFields`。
- 只读预览：持仓来源必须是该产品在当前 user / family 下的真实持仓来源，`shares > 0` 且不超过可用份额；
  可用份额 = 该来源账户真实持仓份额 − 同产品 / 来源账户下仍为 PENDING 的 SELL / REDEMPTION 占用份额；
  到账账户必须当前 user / family 可见、active REAL 叶子账户、币种与产品一致，禁止 VIRTUAL / POSITION / 父账户；
  影响口径为 `willCreateOrder=true`（仅当字段齐全）、`willCreateLedgerTxn=false`、`willCreateSettlement=false`、`willAffectHolding=false`，
  `impactDirection=NONE`、deltas 为 0；preview 绝不调用 `OrderService` / `LedgerService` / `SettlementService`。
- 正式登记：新增 `OrderService.createSellRedeemDraftOrder(...)` 安全入口（只允许 SELL / REDEMPTION），复用既有 `OrderService`
  创建 `status=PENDING` 订单并写入 `SOURCE`（sourceAccountId + shares）/ `TARGET`（targetAccountId）资金线；
  confirm 阶段不生成 CASH / POSITION / FEE 账本流水、不改现金余额、不改持仓，真正的资金与持仓变化仍只在后续 SettlementService 人工结算时产生；未新增表或 migration。
- confirm：SELL / REDEMPTION 必须重新 `buildPreview`，只有 `confirmSupported=true` 才调用安全入口；订单 ID 写回 `confirmOrderId`，
  重复确认幂等，异常整体回滚且草稿保持 `DRAFT`，`IGNORED` 草稿不可确认，不存在自动 preview / confirm / settle。
- Android `versionName = 0.12.0`（`versionCode = 13`）：草稿编辑新增卖出 / 赎回，表单为真实产品 + 持仓来源 + 份额 + 到账账户 + 备注，
  产品选定后复用 `GET /api/v2/holdings/product/{productId}/by-account` 展示持仓来源；切换其它交易类型会清理 SELL / REDEMPTION 专属字段；
  PC `web/shared` 与草稿箱同步卖出 / 赎回类型与预览字段，支持查看 / 编辑 / preview / 二次确认。
- 本轮验证：后端 `mvn -B test` 145 项通过（由 121 项增至 145 项）、Android `testDebugUnitTest` 35 类 230 项通过（由 221 项增至 230 项）、
  `assembleDebug` 通过、`lintDebug` 通过（0 error，2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 通过（成功静默）。未连接任何数据库、未推送。

## 后续待办（Android v0.6 可靠记账采集）

- 服务端 `draft_ledger_entry` 唯一约束缺口已收敛：该缺口最初因为 `allowed_paths` 不含 `sql/**` 而遗留，
  现已由 v0.8 `sql/updatesql/20260927/` 的两个作用域唯一键 + `createDraft` 唯一冲突恢复路径补齐，
  并发重放不再产生重复草稿（详见 `docs/mydca_v08_draft_strong_idempotency_20260927.md`）。
- 未实现常驻后台服务或系统级任务调度，重试只发生在 App 启动、前台恢复、进入草稿箱页面与用户显式操作时。
- 仍不改变 preview / confirm 的人工边界，不新增自动 preview、自动 confirm 或自动正式入账。
- v0.6.0 的 CI 制品证据（Run ID / Artifact ID / APK 文件名 / SHA-256）仍需在真实推送触发 `Android test APK` 工作流后回填；本轮未推送，未声称 APK 交付完成。

## 后续待办（Android v0.7 快速记账采集中心）

- 快速面板只做“定位 + 导航”，仍未实现桌面小组件 / 通知栏快捷入口这类系统级入口；
  其中“从其它 App 分享进 MyDCA”已由 v0.8.0 系统 Share Sheet 文本 / 单图入口补齐
  （详见 `docs/mydca_android_v080_external_share_capture_20260927.md`），其余系统级入口仍依赖先打开 App。
- 通知候选区与 Outbox 区的“定位”是路由 + 标记，不是滚动锚点；长列表下用户仍可能需要手动滚动。
- `sourceRef` 唯一约束已下推到数据库：v0.8 通过 `sql/updatesql/20260927/` 添加用户作用域与家庭作用域唯一键，
  并在 `DraftLedgerEntryService.createDraft` 增加唯一冲突恢复路径，v0.6 的并发重放窗口已关闭。
- v0.7.0 的 CI 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）仍需在真实推送触发 `Android test APK`
  工作流后回填；本轮未推送，未声称 CI APK 交付完成。
- 仍不改变 preview / confirm 的人工边界，不新增自动 preview、自动 confirm 或自动正式入账。

## 后续待办（Android v0.8.0 外部分享快速采集）

- v0.8.0 的 CI 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）需在真实推送触发 `Android test APK`
  工作流后回填；本轮执行进程只做本地提交、未推送，未声称 CI APK 交付完成。
- 系统 Share Sheet 的文本 / 单图需要真机人工验收：不同厂商 Share Sheet 行为、临时 `content://` URI 授权时长、
  以及“未登录时先登录再消费一次”的端到端体验。
- 未实现 `ACTION_SEND_MULTIPLE` 多选分享与通知栏快捷入口（桌面小组件已由 v0.9.0 补齐）；任何后续入口都继续只到 DRAFT。
- 仍不改变 preview / confirm 的人工边界，不新增自动 preview、自动 confirm 或自动正式入账。

## 后续待办（Android v0.9.0 桌面快速记账小组件）

- v0.9.0 的 CI 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）需在真实推送触发 `Android test APK`
  工作流后回填；本轮执行进程只做本地提交、未推送，未声称 CI APK 交付完成。
- 桌面小组件需要真机人工验收：桌面添加、不同厂商 launcher 的尺寸回调与折叠行为、点击后跳转，
  以及“未登录时先登录再消费一次”的端到端体验。
- 未实现 Quick Settings Tile、通知栏常驻入口、桌面余额 / 资产展示、动态计数与后台刷新；任何后续入口都继续只到 DRAFT。
- 仍不改变 preview / confirm 的人工边界，不新增自动 preview、自动 confirm 或自动正式入账。

## 后续待办（v0.10.0 TRANSFER 转账草稿闭环）

- v0.10.0 的 CI 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）需在真实推送触发 `Android test APK`
  工作流后回填；本轮执行进程只做本地提交、未推送，未声称 CI APK 交付完成。
- 真机人工验收：转账的双账户选择、确认弹窗文案，以及正式确认后流水页的转出 / 转入两条视图。
- 未做：跨币种转账、投资订单 / 结算类草稿确认、持仓影响确认；PC 未重做整套转账编辑 UI（只做最小查看 / 预览与安全编辑兼容）。
- 仍不改变 preview / confirm 的人工边界，不新增自动 preview、自动 confirm、自动转账或自动正式入账。

## 后续待办（v0.11.0 投资买入 / 申购草稿闭环）

- v0.11.0 的 CI 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）需在真实推送触发 `Android test APK`
  工作流后回填；本轮执行进程只做本地提交、未推送，未声称 CI APK 交付完成。
- 真机人工验收：投资草稿的产品选择、资金账户过滤、付款账户 / 待结算应收预览与二次确认文案。
- 未做（v0.11.0 范围）：多资金来源组合投资、完整结算 / 持仓影响确认；PC 未重做整套投资编辑 UI。SELL / REDEMPTION 作为独立任务已在 v0.12.0 完成。
- 仍不改变 preview / confirm 的人工边界，不新增自动 preview、自动 confirm、自动下单、自动结算或自动正式入账。

## 后续待办（v0.12.0 投资卖出 / 赎回草稿闭环）

- v0.12.0 的 CI 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）需在真实推送触发 `Android test APK`
  工作流后回填；本轮执行进程只做本地提交、未推送，未声称 CI APK 交付完成。
- 真机人工验收：卖出 / 赎回草稿的持仓来源选择、可用份额与剩余份额预览、到账账户过滤与二次确认文案。
- 未做：跨账户 / 跨产品份额拆分、自动匹配持仓来源、完整结算 / 持仓影响确认（SettlementService 人工结算需单独授权任务）。
- 仍不改变 preview / confirm 的人工边界，不新增自动 preview、自动 confirm、自动下单、自动结算或自动正式入账。

## 后续待办（v0.13.0 人工结算预览与二次确认闭环）

- v0.13.0 的 CI 制品证据（Run ID / Artifact ID / APK 文件名 / CI APK SHA-256）需在真实推送触发 `Android test APK`
  工作流后回填；本轮执行进程只做本地提交、未推送，未声称 CI APK 交付完成。
- 真机人工验收：待结算列表加载、四类订单字段差异、结算影响预览中文文案、二次确认弹窗，以及成功后订单 / 持仓 / 资产刷新。
- 未做：不做自动 preview / confirm / 结算 / 交易，不做跨账户 / 跨产品结算拆分，不新增数据库表或 migration，不新增系统权限。
- 仍不改变 preview / confirm 的人工边界，不新增自动 preview、自动 confirm、自动下单、自动结算或自动正式入账。
