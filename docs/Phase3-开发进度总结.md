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

## ???????????2026-06-17?

- ??? `DraftPreviewDTO` ? PC ??????????????????????????`fundUsage`?????????????????
- ??????? `EXPENSE` / `INCOME`???????????? `accountDelta` ??????????????? `accountDelta` ????
- ??????? `accountId` ???????/????????????????????????? `confirmSupported=false`?
- ????????????????????`willCreateOrder`?`willCreateSettlement`?`willAffectHolding` ??? false?
- `fundUsage` ??????????????? preview ?????????????????????? `QuickEntryService` ???

## ?????????????2026-06-17?

- ??? `AccountMapper.selectVisibleRealById` ?????? REAL ???????????????????`ownerFamilyId` ???????? family ????????
- ????????????????????? REAL ???????/????????`confirmSupported=false`?`missingFields` ?? `accountId`?????? message / warnings?
- ??????????? EXPENSE ???????INCOME ??????????/? REAL ???????unsupported txnType ?????????? accountId ????? mapper??? preview ????? `QuickEntryService`?
- ???????preview ??????? JSON????????confirm ???? `EXPENSE` / `INCOME`????????????????
