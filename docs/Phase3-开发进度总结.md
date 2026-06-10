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
