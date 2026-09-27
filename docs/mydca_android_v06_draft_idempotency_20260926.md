# MyDCA Android v0.6 草稿创建幂等基础（服务端前置能力）

核验日期：2026-09-27
目标仓库：`TimeLordTTY/MyDCA-Board`（分支 `v2`）
对应任务：`task-mydca-v06-draft-idempotency-20260926`
本任务范围：仅服务端 `DRAFT` 创建幂等；不改变 preview / confirm / 正式入账语义

## 背景

Android v0.6 的目标是“可靠记账采集”：本地失败重试队列在弱网下会重放同一次采集请求。
在补齐客户端队列之前，服务端必须先保证 `POST /api/v2/ai/accounting/draft-from-intent`
对同一次采集尝试是“可安全重放”的，否则一次网络重试就会留下两条重复草稿。

## 实现范围（本任务）

- `DraftLedgerEntryMapper` 新增 `selectVisibleBySource`：按当前用户/家庭可见性 +
  `source_type` + `source_ref` 查询既有草稿，不限制草稿状态，按 `id` 升序取第一条。
- `DraftLedgerEntryService.createDraft` 在插入前先做幂等查找：
  - `sourceRef` 非空且命中既有草稿时，直接返回该草稿，不再插入新行；
  - `sourceRef` 为空（`null` 或空白）时保持原有非幂等行为，每次插入新草稿。
- `AiAccountingService.draftFromIntent` 与 `POST /api/v2/drafts` 复用同一个 `createDraft` 入口，
  两条创建路径享有同一套重放语义，未新增分叉逻辑。
- 未新增数据库 schema，未执行任何数据库迁移；查询复用既有
  `idx_draft_ledger_source (source_type, source_ref)` 索引。

## 幂等语义

- 幂等键为 `sourceType + sourceRef`，只在同一次采集尝试内稳定；不使用金额、备注、账户等弱条件做模糊去重。
- 命中范围严格受调用者 `user` / `family` 可见性约束（与草稿列表、详情一致）：
  只能命中本人草稿或同家庭可见草稿，不会跨用户、跨家庭命中他人不可见草稿。
- 命中草稿无论处于 `DRAFT`、`CONFIRMED` 还是 `IGNORED`，都直接返回既有草稿并保持其状态，
  由客户端据状态决定下一步（继续复核、不再重复入账、或忽略）。
- 草稿归属始终以服务端当前登录用户为准，请求体不能指定 `ownerUserId` / `ownerFamilyId`。

## 明确未改变

- `draft-from-intent` 仍然只创建 `DRAFT`，不自动 preview、不自动 confirm、不自动正式入账。
- `confirmDraft`、`QuickEntryService` 与正式账本、余额、持仓、订单写入语义完全未改动。
- 幂等重放不会调用 `QuickEntryService`，因此重试不会产生正式流水、订单或结算记录。

## Android 三类来源的 sourceRef 约定

`sourceRef` 必须代表“一次用户确认的采集尝试”，在同一次尝试的整个重试链路中保持稳定且唯一，
重试时沿用同一个 `sourceRef`。三类来源的约定如下：

- 手工文本（草稿箱“手工记一笔”）：进入输入页时生成一次 UUID 请求号，
  解析与创建草稿统一使用 `android-ocr-<requestId>`；同一次输入内的重试沿用同一 `sourceRef`，
  重新发起一次新的输入才生成新的 `sourceRef`。
- OCR（图片识别）：与手工文本共用同一状态机，选择图片时生成一次请求号，
  `sourceRef` 同为 `android-ocr-<requestId>`；同一张图片的同一次复核不会产生第二个 `sourceRef`。
- 通知候选（支付通知）：使用候选的 `fingerprint` 作为 `sourceRef`，来源类型为 `PAYMENT_NOTIFICATION`；
  同一候选重试创建草稿时复用同一 `fingerprint`，不会因为重试而生成第二份草稿。

本任务只把该约定写入文档，未修改 Android 代码；Android 侧现有实现已符合上述约定。

## 服务端测试

- `backend: mvn -B test`：通过，55 项测试，0 失败、0 错误、0 跳过。
- 新增覆盖：
  - 相同 user + `sourceType` + `sourceRef` 重复请求返回同一 `draft id`，且只插入一条；
  - 不同 `sourceRef` 创建不同草稿；
  - 不同用户/家庭不能互相命中同 `sourceRef` 的草稿；
  - `sourceRef` 为空保持非幂等旧行为；
  - 已 `CONFIRMED` / 已 `IGNORED` 的相同来源重试不创建新草稿；
  - 幂等重放不会调用 `QuickEntryService`。
- 未连接生产数据库，未修改真实账本、余额、持仓或订单；未执行部署。

## 明确未实现（不在本任务范围）

- Android 本地失败重试队列 / outbox 仍未实现；本任务只提供其服务端前置能力。
- 本任务不改变 preview / confirm 的人工边界，不新增自动 preview、自动 confirm 或自动正式入账。
- 未在本轮为 `draft_ledger_entry` 引入数据库唯一约束。并发重放同 `sourceRef` 时仍存在极窄竞争窗口，
  理论上可能产生两条草稿；更早的既有草稿会在后续重试中被返回，该竞争留待 outbox 落地时结合唯一索引一并收敛。

## 提交与推送

本轮工作进程的执行约束为“提交但不推送”：改动只提交到本地 `v2`，由 owner 批准的 Codex auto-executor
在进程退出后校验改动路径落在 `allowed_paths` 内再推送。
