# Phase3 开发进度总结

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
- 首版确认只支持 `EXPENSE` / `INCOME` 快速记账，并且必须通过 `QuickEntryService` 进入既有账本校验链路。
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
