# MyDCA Android v0.6.0 可靠记账采集发布加固与交付证据

核验日期：2026-09-27
目标仓库：`TimeLordTTY/MyDCA-Board`（分支 `v2`）
对应任务：`task-mydca-v06-release-hardening-20260926`
前置子任务（均已完成）：
- `task-mydca-v06-draft-idempotency-20260926`，提交 `2dd1e29ded002238fcab46f5f26cca8149b19f78`
- `task-mydca-v06-secure-outbox-20260926`，提交 `76c1552e53ea5cb938e12c49a3f1f6d01e2f93f9`

本任务范围：把“可靠记账采集”收口为 Android v0.6.0，补齐版本 / 工作流命名、最终回归、文档与交付证据。
本轮未推送：改动只提交到本地 `v2`，由 owner 批准的 Codex auto-executor 在进程退出后校验 `allowed_paths` 再推送。

## 1. 版本收口

| 项 | 值 | 证据 |
| --- | --- | --- |
| `versionName` | `0.6.0` | `android-app/app/build.gradle.kts`（前序提交 `76c1552` 由 `0.5.0` 升级） |
| `versionCode` | `7`（由 `6` 递增） | 同上 |
| 已构建 APK 的清单 | `versionCode='7' versionName='0.6.0'` | `aapt2 dump badging app-debug.apk` 实测输出 |

版本升级本身在依赖子任务 `task-mydca-v06-secure-outbox-20260926` 中已随代码提交完成，本任务只做核对，未重复修改。

## 2. APK 工作流命名收口

`.github/workflows/android-test-apk.yml`：

- APK 文件：`app-debug.apk` → `MyDCA-Board-v0.6.0-<short-sha>.apk`。
- `SHA256SUMS.txt` 内的文件名同步为 v0.6.0。
- Artifact 上传名：`mydca-android-v0.5.0-<sha>` → `mydca-android-v0.6.0-<sha>`。
- 继续使用一次性 debug 签名（`assembleDebug`，不引入 release keystore），APK 不提交到 Git。
- 触发条件不变：`workflow_dispatch` 或推送到 `v2` 且改动命中 `android-app/**` 或该工作流文件本身。

## 3. v0.6 最终回归

完整验证命令（本轮真实执行）：

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| 后端测试 | `backend: mvn -B test` | 通过，55 项测试，0 失败 / 0 错误 / 0 跳过 |
| Android 单元测试 | `android-app: gradlew.bat --no-daemon testDebugUnitTest` | 通过，23 个测试类共 104 项测试，0 失败 / 0 错误 / 0 跳过 |
| Android 打包 | `android-app: gradlew.bat --no-daemon assembleDebug` | 通过 |
| Android 静态检查 | `android-app: gradlew.bat --no-daemon lintDebug` | 通过，0 error，2 条既有 warning（`DataExtractionRules`、`ObsoleteSdkInt`，非本轮引入） |
| 仓库编译钩子 | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1` | 见第 6 节 |

任务要求的回归项与本轮证据的对应关系：

| 回归要求 | 证据 |
| --- | --- |
| 手工文本 / OCR / 通知候选三入口都能正常创建 DRAFT | `DraftOutboxReleaseRegressionTest.allThreeCaptureOriginsOnlyCreateDraftAndDequeueAfterRetry`（三入口逐一遍历，只调用 `createDraft`，成功即出队）、`OcrDraftCoordinatorTest.manualTextEntryStillRequiresExplicitParseAndDraftSteps`、`NotificationDraftInputTest.paymentCandidateWithAmountCanCreateDraftWhenApiReady` |
| 可恢复网络错误会进入 / 保留 outbox | `DraftOutboxQueueTest.transientCreationFailureIsQueuedWithStableSourceRef`、`retryReusesSameSourceRefAcrossTransientFailures`、`DraftOutboxErrorClassifierTest.networkInterruptionsAndTimeoutsAreRetryable`、`serverErrorsAreRetryable` |
| 重试成功后出队并可打开草稿 | `DraftOutboxQueueTest.retryRespectsBackoffThenSucceedsAndDequeues`（断言 `canOpenDraft`）、`DraftOutboxReleaseRegressionTest.allThreeCaptureOriginsOnlyCreateDraftAndDequeueAfterRetry` |
| 401 / 业务校验错误不会无限重试 | `DraftOutboxQueueTest.unauthorizedFailurePausesUntilUserRetries`、`businessFailureIsNeverRetriedAutomaticallyButCanBeDiscarded`、`automaticRetryStopsAfterTheAttemptLimit`、`DraftOutboxRetryPolicyTest.automaticRetryStopsAfterTheAttemptLimit`、`DraftOutboxErrorClassifierTest.unauthorizedFailuresPauseForLogin`、`businessErrorsAreNotAutoRetryable` |
| 任何 outbox 路径都不会调用 preview / confirm | `DraftOutboxNetworkBoundaryTest`（MockWebServer 端到端：只请求 `POST /api/v2/ai/accounting/draft-from-intent`，断言无 `/preview`、`/confirm`、`/ignore`、`/api/v2/drafts`、`/quick-entry`、`settlement`、`order`）、`DraftOutboxReleaseRegressionTest.outboxTypesExposeNoPreviewConfirmOrIgnoreCapability`（类型层面：`DraftCreationGateway` 只声明 `createDraft`，outbox 各类不暴露含 `preview` / `confirm` / `ignore` / `quickentry` 的方法） |
| 草稿确认仍必须 DRAFT + 当前 preview + confirmSupported + 用户二次确认 | `DraftEditStateTest.confirmRequiresFreshPreviewAndIdleState`、`DraftEditStateTest.nonDraftCannotBuildSaveRequest`、`DraftReviewTest.onlyDraftStatusCountsAsPending`；二次确认对话框为 `DraftInboxScreen` 的 `AlertDialog`，确认前会再次通过 `DraftEditState.canConfirm` 复核 |
| 服务端幂等可安全重放 | `DraftLedgerEntryServiceTest`（26 项，覆盖重复请求返回同一草稿、已确认 / 已忽略来源重试不新建草稿、跨用户 / 家庭不互相命中、空 `sourceRef` 保持旧行为、重放不触发 `QuickEntryService`） |

本轮新增测试（相对 `76c1552` 的 102 项 / 22 个测试类）：

- `DraftOutboxReleaseRegressionTest`（2 项）：三入口端到端回归 + outbox 类型层面的确认边界守卫。

## 4. 本地构建产物（本轮）

- 产物路径：`android-app/app/build/outputs/apk/debug/app-debug.apk`
- 文件名：`app-debug.apk`（工作流在 CI 中重命名为 `MyDCA-Board-v0.6.0-<short-sha>.apk`）
- 大小：55,916,141 bytes
- SHA-256：`6149D8FD47C8A4A1E9FA38726979341959892F344913884BC8860C3F4859C891`
- APK 清单：`versionCode='7' versionName='0.6.0'`（`aapt2 dump badging` 实测）
- 该产物为本地 `assembleDebug` 输出；APK 含打包时间戳，重建后字节与哈希可能变化，仅作为“本地构建成功”的证据。
- APK 不进入源码仓库（`.gitignore` 已忽略 `*.apk` 与 `build/`）。

### GitHub Actions 最终制品证据

任务进程结束后，auto-executor 已将结果提交推送到 `v2`，随后 GitHub Actions 真实触发并成功：

- Workflow：`Android test APK`
- Run ID：`36306899948`
- Head SHA：`a732c3bc569959a4f53a6448d8c7e6ce3dcc63ee`
- Artifact ID：`10928005959`
- Artifact：`mydca-android-v0.6.0-a732c3bc569959a4f53a6448d8c7e6ce3dcc63ee`
- APK：`MyDCA-Board-v0.6.0-a732c3bc.apk`
- CI APK SHA-256：`2298E56D4B9DF18218CAD17A1CCFA3EA094592364F2AFA2FD5E5582B103BB0CD`

因此早期“NOT_PRODUCED”仅描述执行进程尚未 push 的瞬间状态，不再是最终交付状态。

## 5. 安全边界与明确未实现

安全边界（本轮遵守，且未触碰）：

- 未连接生产数据库，未创建或修改任何真实账本、余额、持仓、订单、交易记录。
- 未部署生产后端，未执行真实记账或交易写入；回归全部基于本地 `mvn test`、JVM 单元测试与 MockWebServer。
- 未输出或提交 Token、密码、Cookie、Keystore、签名密钥等敏感信息。
- 改动只落在 `allowed_paths`（`android-app/**`、`docs/**`、`.github/workflows/android-test-apk.yml`）之内。

明确未实现（v0.6 范围外）：

- 不自动 preview、不自动 confirm、不自动正式入账、不自动交易。
- 不实现后台常驻无限重试（无常驻服务、无 WorkManager / JobScheduler 常驻队列）；重试只发生在 App 启动、前台恢复（`ON_START`）、进入草稿箱页面与用户显式操作时，且受有限退避与次数上限约束。
- 本任务当时未引入服务端 `draft_ledger_entry` 唯一约束（`allowed_paths` 不含 `sql/**`），并发重放的极窄竞争窗口随后已由 v0.8 收敛：`sql/updatesql/20260927/` 添加用户作用域与家庭作用域唯一键，服务端 `createDraft` 在唯一冲突时按同一可见作用域重查既有草稿；客户端仍按 `sourceType + sourceRef` 去重并在命中既有草稿时直接出队。详见 `docs/mydca_v08_draft_strong_idempotency_20260927.md`。
- 不让通知 / OCR 原文无加密落地；outbox 只加密保存字段白名单内的内容（不含 Token、密码、Cookie、图片 / URI、通知完整原文）。
- 未接入真实大模型做账本决策。
- 真实设备上的通知监听、弱网重试与厂商 URI 兼容性仍需人工验证，JVM 测试不等于真实设备验证。

## 6. 仓库编译钩子

按仓库 `AGENTS.md` 要求执行编译钩子 `scripts/post-task-compile-hook.ps1`；第 1 轮即通过：后端 `mvn -DskipTests package` BUILD SUCCESS，前端 `npm run build`（vite）构建成功，未触发自动修复轮。构建日志见 `.codex-hooks/logs/latest-build.log`。后续已按主人要求移除成功蜂鸣与 Windows Toast/Popup；当前成功构建只写 stdout/log，不再打扰桌面。
