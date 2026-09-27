# MyDCA Android v0.6 安全草稿 Outbox 与三入口恢复交付核验

核验日期：2026-09-27
目标仓库：`TimeLordTTY/MyDCA-Board`（分支 `v2`）
对应任务：`task-mydca-v06-secure-outbox-20260926`
服务端前置（依赖）：`task-mydca-v06-draft-idempotency-20260926`，提交 `2dd1e29ded002238fcab46f5f26cca8149b19f78`
本任务范围：Android 本地“待创建草稿”安全 Outbox + 手工文本 / OCR / 支付通知候选三入口恢复
本轮未推送：改动只提交到本地 `v2`，由 owner 批准的 Codex auto-executor 在进程退出后校验 `allowed_paths` 再推送

## 版本信息

- `versionCode = 7`，`versionName = "0.6.0"`。
- APK 制品按 `MyDCA-Board-v0.6.0-<short-sha>.apk` 记录（`android-app/README.md`）。
- CI 制品上传名（`.github/workflows/android-test-apk.yml`）的重命名不在本任务 `allowed_paths`（仅 `android-app/**`、`docs/**`）内，由同一目标下的 `task-mydca-v06-release-hardening-20260926` 收口；本任务提交不包含该文件改动。

## 实现范围

新增的 Outbox 抽象位于 `android-app/app/src/main/java/com/timelordtty/mydca/outbox/`：

- `DraftOutboxQueue`：队列核心逻辑（入队、受控批量重试、单条立即重试、丢弃、恢复），持有唯一的网络依赖 `DraftCreationGateway`。
- `DraftCreationGateway`：只声明 `createDraft(intent)` 一个方法，类型上不存在 preview / confirm / ignore / QuickEntry / 正式账本入口。
- `DraftOutboxStorage` / `EncryptedDraftOutboxStorage` / `InMemoryDraftOutboxStorage`：持久化边界与加密实现。
- `DraftOutboxCodec`：显式字段白名单编解码，只有允许的字段可以被持久化。
- `DraftOutboxRetryPolicy`：有限退避（30s / 120s / 600s / 1800s，封顶）与自动重试次数上限（默认 5 次）。
- `DraftOutboxErrorClassifier`：把“创建 DRAFT”的失败映射为可重试 / 需重新登录 / 业务拒绝 / 未知四类。
- `AndroidDraftOutbox`：Android 侧装配入口，复用 Android Keystore AES-GCM 能力。

加密与密钥复用：

- `core/security/` 新增 `SecretCipher` / `AesGcmSecretCipher` / `AndroidKeystoreAesKey` / `SecureKeyValueStore`，把 AES-GCM 细节收敛到一处。
- `KeystoreTokenStore` 改为复用同一套加解密实现；密钥别名（`mydca_android_access_token`）、偏好文件名与键名全部保持不变，已登录设备的 Token 仍可解密，不会强制重新登录。
- Outbox 使用独立密钥别名（`mydca_android_draft_outbox`）与独立偏好文件（`secure_draft_outbox`），只写入密文与随机 IV。

可持久化内容（字段白名单）：

- 用户已确认 / 编辑过的记账文本或结构化 `AccountingIntent`；
- 稳定 `sourceType` / `sourceRef`；
- 队列自身 `id`、`createdAt`、`retryCount`、`nextRetryAt`、`lastAttemptAt`、`lastErrorCategory`、`lastErrorMessage`、`status`、展示用脱敏 `summary`。

不存在可写入 Token、密码、Cookie、Secret、图片 / URI、通知完整原文的字段；通知来源只写入既有脱敏摘要（`NotificationDraftInput.summary`，上限 48 字符）。

三类入口的 sourceRef 规则（与幂等基础文档一致）：

- 手工文本：`OcrDraftCoordinator` 进入输入页时生成一次请求号，`sourceRef = android-ocr-<requestId>`，同一次输入内的所有重试沿用同一 `sourceRef`。
- OCR：与手工文本共用同一状态机，同一张图片的同一次复核不会产生第二个 `sourceRef`；图片与 URI 从不进入 outbox。
- 支付通知候选：`sourceRef = candidate.fingerprint`，`sourceType = PAYMENT_NOTIFICATION`，只使用脱敏摘要。

只有“创建 DRAFT”失败才可能入队：

- 解析失败（`parse-text`）在状态机里直接回到可编辑态，绝不会入队；`OcrDraftCoordinatorTest.ocrParseFailureNeverEntersOutbox` 覆盖。
- 缺少稳定 `sourceRef` 时 `enqueue` 返回 `null`，不入队（避免重放产生重复草稿）。

错误分类与队列状态：

| 分类 | 触发 | 自动重试 | 队列状态 |
| --- | --- | --- | --- |
| `RETRYABLE` | 网络中断、超时、连接失败、明确 5xx | 是（退避 + 次数上限） | `PENDING` → 用尽后 `EXHAUSTED` |
| `AUTH_REQUIRED` | 401 / 403 | 否，暂停等待重新登录 | `AUTH_PAUSED` |
| `BUSINESS` | 其他 4xx | 否，展示原因供用户修改或丢弃 | `BLOCKED` |
| `UNKNOWN` | 其他异常 | 否，同上 | `BLOCKED` |

触发点（v0.6 不要求常驻后台服务）：

- App 启动（已登录后）执行一次受控重试；
- App 从后台回到前台（`Lifecycle.Event.ON_START`）执行一次受控重试；
- 进入草稿箱页面时执行一次受控重试；
- 用户对单条“立即重试”或“立即重试全部”；
- 用户“丢弃”。

并发与节流：批量重试使用互斥锁实现单实例执行（已在执行时直接跳过），手动重试与批量重试互斥；退避未到期、`AUTH_PAUSED`、`BLOCKED`、`EXHAUSTED` 的条目不会被自动重试。

UI（草稿箱页 `DraftOutboxSection`）：

- 展示待重试数量、来源（手工文本 / 图片 OCR / 支付通知候选）、创建时间、队列状态、重试次数、下次自动重试时间、最近失败原因；
- 提供“立即重试”“丢弃”“立即重试全部”；
- 默认只展示脱敏摘要，不展示完整敏感原文，也不提供任何 preview / confirm 入口；
- 重试成功后从队列移除：服务端返回 `DRAFT` 时提供“打开草稿”引导；返回同一 `sourceRef` 的既有 `CONFIRMED` / `IGNORED` 草稿时只展示状态，不发起任何确认动作。

## 草稿与安全边界（未放松）

- Outbox 只承载草稿创建链路；预览、确认、忽略、QuickEntry、正式账本写入、订单 / 结算 / 持仓动作全部不在队列能力范围内。
- 网络恢复不会自动正式入账：重试只会再次调用 `draft-from-intent` 创建 `DRAFT`。
- 确认必须同时满足：当前草稿 `status=DRAFT`、预览 `draftId` 与草稿一致、`preview.confirmSupported=true`、表单无未保存修改、无进行中的保存 / 预览 / 确认，并且用户二次确认。
- 支付通知候选仍最多生成 `DRAFT`；手工与 OCR 入口仍不自动 preview / confirm。
- SPENDABLE / RESERVED / INVESTABLE 校验仍由后端 preview / confirm 最终守门。
- 未连接生产数据库，未创建真实草稿，未使用真实账号做写入 smoke，未修改任何真实账本、余额、持仓或订单。

## 测试结果

- `android-app: gradlew.bat --no-daemon testDebugUnitTest`：通过，22 个测试类共 102 项测试，0 失败、0 错误、0 跳过（v0.5 为 68 项，本轮新增 34 项）。
- `android-app: gradlew.bat --no-daemon assembleDebug`：通过。
- `android-app: gradlew.bat --no-daemon lintDebug`：通过；0 error，仅 2 条既有 warning（`DataExtractionRules`、`ObsoleteSdkInt`），均不由本次改动引入。
- 本轮未改动 `backend/` 与 `web/`；后端幂等能力沿用上一轮已验证结果（`mvn -B test` 55 项通过）。

新增测试与任务要求的对应关系：

| 要求 | 覆盖测试 |
| --- | --- |
| 加密存储 round-trip | `EncryptedDraftOutboxStorageTest.encryptedRoundTripRestoresQueueAfterRestart`（真实 AES-GCM 往返，重建存储后队列可恢复） |
| 不持久化 Token / 密码 / 图片 URI / 完整通知原文 | `EncryptedDraftOutboxStorageTest.tokenPasswordImageUriAndFullNotificationBodyAreNeverPersisted`、`encryptedPayloadKeepsOnlyAllowListedFields` |
| 三类来源 sourceRef 稳定 | `OcrDraftCoordinatorTest.repeatedCreateInsideOneAttemptReusesTheSameSourceRef`、`eachNewCaptureAttemptGetsItsOwnSourceRef`、`queuedRetryOnlyRecreatesDraftAndReusesSourceRef`、`DraftOutboxQueueTest.paymentNotificationSourceRefIsPreservedForRetries` |
| transient failure 入队 | `DraftOutboxQueueTest.transientCreationFailureIsQueuedWithStableSourceRef` |
| 401 暂停 | `DraftOutboxQueueTest.unauthorizedFailurePausesUntilUserRetries` |
| 4xx 业务错误不循环自动重试 | `DraftOutboxQueueTest.businessFailureIsNeverRetriedAutomaticallyButCanBeDiscarded` |
| retry success 出队 | `DraftOutboxQueueTest.retryRespectsBackoffThenSucceedsAndDequeues` |
| duplicate / existing draft 视为创建链路成功并出队 | `DraftOutboxQueueTest.existingDraftWithSameSourceRefIsTreatedAsCreatedAndDequeued`、`DraftOutboxNetworkBoundaryTest.existingConfirmedDraftRemovesEntryWithoutAnyConfirmRequest` |
| retry 不调用 preview / confirm | `DraftOutboxNetworkBoundaryTest`（MockWebServer 端到端断言只请求 `POST /api/v2/ai/accounting/draft-from-intent`，无 `/preview`、`/confirm`、`/ignore`、`/api/v2/drafts`） |
| process restart 后队列仍可恢复 | `DraftOutboxQueueTest.queueIsRestoredAfterProcessRestart`、`EncryptedDraftOutboxStorageTest.encryptedRoundTripRestoresQueueAfterRestart` |
| backoff / 单实例避免重复并发 | `DraftOutboxRetryPolicyTest`、`DraftOutboxQueueTest.controlledRetryIsSingleInstanceEvenWhenCalledConcurrently`、`automaticRetryStopsAfterTheAttemptLimit` |

## 本地构建产物（本轮）

- 本地 `assembleDebug` 产物路径：`android-app/app/build/outputs/apk/debug/app-debug.apk`
- 文件名：`app-debug.apk`
- 大小：55,916,141 bytes
- SHA-256：`6149D8FD47C8A4A1E9FA38726979341959892F344913884BC8860C3F4859C891`
- 该产物为本次提交前最后一次 `assembleDebug` 输出；APK 中含打包时间戳，重建后字节与哈希必然变化，仅作为“本地构建成功”的证据。
- APK 不进入源码仓库（`.gitignore` 已忽略 `*.apk` 与 `build/`）。

### GitHub Actions 制品状态

- Workflow：Android test APK
- 本进程产生的 Run：NOT_PRODUCED（工作进程按指令不推送，工作流未在本进程内触发）
- 本进程产生的 Artifact ID / 名称：NOT_PRODUCED（同上）
- 推送后由 approved Codex auto-executor 触发工作流并记录真实 Run / Artifact 证据

工作流在 CI 用一次性 debug 签名密钥重新打包，因此 CI 产出的 APK 与本报告记录的本地产物 SHA-256 不会相同；两者只能分别作为“本地构建成功”与“CI 构建成功”的独立证据。

## 明确未实现

- 未实现常驻后台服务或系统级任务调度（无 WorkManager / JobScheduler 常驻队列）：重试只发生在 App 启动、前台恢复、进入草稿箱页面和用户显式操作时，符合 v0.6 的“不要求常驻后台服务”。
- 本任务当时未引入服务端 `draft_ledger_entry` 唯一约束（`allowed_paths` 不含 `sql/**`）；该并发重放窗口已由 v0.8 收敛（`sql/updatesql/20260927/` 的两个作用域唯一键 + `createDraft` 唯一冲突恢复，见 `docs/mydca_v08_draft_strong_idempotency_20260927.md`），客户端仍先按 `sourceType + sourceRef` 去重并在命中既有草稿时直接出队。
- 未实现 AI 自动资金分类、自动 preview、自动 confirm、自动正式入账，也未提供直接修改余额、持仓、订单或执行交易的入口；未接入真实大模型。
- 未连接生产数据库，未创建真实草稿，未使用真实账号做写入 smoke；真实设备上的通知监听与弱网重试仍需人工验证。
