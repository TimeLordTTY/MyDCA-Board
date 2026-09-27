# MyDCA Android v0.8.0 外部分享快速采集

核验日期：2026-09-27
目标仓库：`TimeLordTTY/MyDCA-Board`（分支 `v2`）
对应任务：`task-mydca-android-v080-external-share-capture-20260927`（owner 直接批准，L3）
前置基线：Android v0.7.0（`6098a972` 快速记账采集中心）、v0.6.0（`a732c3b` 安全草稿 Outbox）、v0.8 后端草稿强幂等（`ea8b361`）

本任务范围：让系统 Share Sheet 的**文本**与**单张图片**安全进入现有手工文本 / 本地 OCR 采集流程，并发布 v0.8.0。
分享动作本身只做**预填**：不自动解析、不自动 OCR、不自动创建草稿、不自动 preview、不自动 confirm、不自动正式入账。

执行进程本地提交；是否已推送 `v2` 与 CI 制品证据必须以真实 GitHub Actions 结果为准（见第 8 节）。

## 1. 系统分享入口

- `AndroidManifest.xml` 的 `MainActivity` **只新增一个 `ACTION_SEND` intent-filter**：
  `text/*` 与 `image/*`，带 `DEFAULT` category。
- 不声明 `ACTION_SEND_MULTIPLE`，因此系统多选分享不会把多份内容送进来；即使被送达，解析层也会拒绝。
- 未新增 `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` / 短信 / 通讯录 / 定位 / 录音等任何权限；
  APK 实测权限与 v0.7.0 一致（`INTERNET`、`POST_NOTIFICATIONS` 为清单声明，其余为依赖库带入）。
- 未修改 `launchMode`：`onCreate` 与 `onNewIntent` 两条路径都会走同一套分享接收逻辑。

## 2. 可测试的分享解析层

新增 `share/` 包，全部为纯 Kotlin，无 Android 依赖，可直接 JVM 单元测试：

| 类型 | 职责 |
| --- | --- |
| `ExternalSharePayload` | 只有两种形态：`Text`（单条文本）与 `Image`（系统授权临时 `content://` URI 字符串） |
| `ExternalShareRequest` | 从 Intent 抽出的纯数据视图：`action` / `mimeType` / `sharedText` / `sharedUri` |
| `ExternalShareResolver` | 唯一解析规则：能否接收、接收成什么；超长文本安全截断 `MAX_TEXT_LENGTH = 2000` |
| `ExternalShareRejection` | 5 种安全拒绝原因，每种都带**中文用户提示** |
| `ExternalShareResolution` | `Accepted(payload, truncated)` 或 `Rejected(rejection)` |
| `ExternalShareSourceRef` | `android-share-text-<uuid>` / `android-share-image-<uuid>` 规则 |
| `ExternalSharePendingStore` | 进程内一次性 pending share（只消费一次；未登录期间保留） |
| `SharedImageOcrGate` | 分享图片的本地 OCR 门：只有用户显式点击才放行，且只放行一次 |

隐私约束在代码层固定：

- 分享文本与图片 URI **只存在当前进程内存**，不写 SharedPreferences / DataStore / 文件；
- 不打印原文、不打印 URI、不上传图片；
- `ExternalSharePendingStore` 的 payload 取出即清空，第二次 `consume()` 返回 `null`；
- 被拒绝时只留一条中文提示，`pending` 保持为空，App 不会因为一次坏分享而跳页。

`MainActivity` 只做 Intent 字段读取（`intent.action` / `intent.type` / `EXTRA_TEXT` / `EXTRA_STREAM`），
读取失败时按“不支持”处理并给中文提示；它不解析记账候选、不创建草稿、不发起任何网络请求。

## 3. 文本分享

系统分享 → MyDCA → 既有“手工记一笔”页面（`OcrEntryMode.ManualText` → `OcrDraftScreen`），预填分享文字。

- 页面顶部固定提示“来自系统分享，尚未解析/未生成草稿”，并给出本次分享的说明文案。
- 文字进入可编辑输入框：用户可以直接编辑，也可以清空；清空后解析按钮保持禁用。
- 进入页面**不发起任何网络请求**（`startTextEntry` 只准备文本）。
- 只有用户点击“解析记账候选”才调用 `parse-text`；只有用户复核并点击“确认生成 DRAFT”才创建草稿。
- 未登录时先正常登录：pending share 在进程内保留，登录后只消费一次，不为跨进程恢复持久化原文；
  登录成功后进入既有手工记账页并完成同样的一次性预填。

## 4. 单图分享

系统分享 → MyDCA → 既有图片识别页（`OcrEntryMode.Image`），分享进来的图片先处于“图片已选择”状态。

- 收到分享 Intent **不会立刻 OCR**：`OcrDraftCoordinator.selectImage(requestId, sourceRef)` 只登记请求与 sourceRef。
- 页面显示“使用此图片并识别”按钮；只有用户点击后，`SharedImageOcrGate.startRecognitionByUser()` 才放行一次识别。
- 识别复用既有 ML Kit 中文模型（`MlKitImageTextRecognizer`），未复制第二套 OCR 流程；图片仍不上传、不落盘。
- 识别完成后仍要用户手动“解析记账候选”，再手动“确认生成 DRAFT”。
- 未新增相册广泛读取权限；图片只接受系统授权的临时 `content://` URI，`file://` 与未知 scheme 一律拒绝。

## 5. 导航与一次性状态

- 未改动 v0.7 的四个快速采集入口与底部导航信息架构；`QuickCaptureHubTest` 全部继续通过。
- 新增 `ui/ExternalShareHub.kt`：分享落点决策 `ExternalShareDestination(route, entryMode)` 与预填文案；
  文本 → 草稿箱 + 手工文本，图片 → 草稿箱 + 图片识别。
- 新增 `ui/ExternalShareCaptureSession`：采集侧一次性分享状态。以下路径都会清空它：
  返回 / 取消（`onCaptureExit`）、主动切换底部导航（`onManualNavigation`）、成功生成 DRAFT（`onDraftCreated`）。
- 采纳一次分享时会同时清空旧的 `selectedDraftId`、调用 `NotificationNavigationTarget.clear()`、
  复位 `QuickCaptureFocus`，避免 share target 与草稿选中 / 通知目标 / 快速定位相互残留。
- 通知点击导致的跳页同样会丢弃当前分享，避免两套一次性导航状态叠加。

## 6. sourceRef

- 文本分享：`android-share-text-<uuid>`；图片分享：`android-share-image-<uuid>`。
- 同一次分享事件在内存中只生成一次 sourceRef，解析候选、创建 DRAFT、Outbox 重试重放全程复用同一个值。
- 不同分享事件必然不同（UUID + 单调 token），即使两次分享的文字完全相同。
- sourceRef 不包含分享文字、文件名、URI 或图片原文（单元测试逐项断言）。
- 既有 `android-ocr-<requestId>` 规则与通知候选 `fingerprint` 规则未改动，v0.6 / v0.7 行为不变。

## 7. 安全边界

必须用代码 / 测试固定为：收到文本不自动 parse；收到图片不自动 OCR；OCR 不自动 parse；
parse 不自动 draft；draft 不自动 preview；任何路径不自动 confirm / QuickEntry / 正式入账。

- 分享相关类型上不存在 preview / confirm / ignore 能力，也不直接依赖网络写入门面
  （`externalShareLayerExposesNoParsePreviewConfirmCapability` 反射断言）。
- 外部分享只有在用户已经明确创建 DRAFT 且发生可恢复网络失败后，才沿用 v0.6 Outbox；
  Outbox 重试的唯一网络动作仍是“创建 DRAFT”，且继续复用原 sourceRef。
- 草稿最终确认仍由 `DraftInboxScreen` 守门：`DRAFT` + 当前 preview + `confirmSupported=true` + 二次确认对话框。

## 8. 测试与验证证据（本轮真实执行）

新增 4 个测试类（`ExternalShareResolverTest` 11 项、`ExternalSharePendingStoreTest` 9 项、`SharedImageOcrGateTest` 5 项、`ExternalShareCaptureRegressionTest` 12 项，共 37 项），全部为纯 Kotlin JVM 测试：

| 任务要求 | 用例 |
| --- | --- |
| ACTION_SEND text/plain → Text payload | `ExternalShareResolverTest.plainTextShareBecomesTextPayload`、`otherReasonablyTextMimeTypesAreAcceptedAsText` |
| ACTION_SEND image/* + content URI → Image payload | `singleImageShareWithContentUriBecomesImagePayload` |
| ACTION_SEND_MULTIPLE → Unsupported | `sendMultipleIsRejectedAsUnsupported` |
| file:// / 未知 scheme → Unsupported | `fileSchemeImageIsRejected`、`unknownSchemesAndBarePathsAreRejected` |
| 空文本 / 空 URI → Unsupported | `blankTextAndBlankUriAreRejected`、`unsupportedMimeTypeIsRejected` |
| 超长文本边界 | `overLongTextIsSafelyTruncatedAtTheLimit`、`textAtTheExactLimitIsKeptAsIs`、`truncatedShareSurfacesTruncationNoticeOnce` |
| 同一 payload 不重复消费 | `ExternalSharePendingStoreTest.acceptedShareIsConsumedExactlyOnce`、`twoShareEventsStayDistinctEvenWithIdenticalContent` |
| 登录前 pending → 登录后一次消费 | `pendingShareSurvivesUntilLoginAndIsThenConsumedOnce` |
| 文本预填不触发 parse/draft | `ExternalShareCaptureRegressionTest.sharedTextPrefillNeverParsesAndNeverDrafts`、`sharedTextReachesDraftOnlyThroughTwoExplicitUserActions`、`userCanClearThePrefilledShareTextWithoutCreatingAnything` |
| 图片预填不自动 OCR，只有用户操作后才 OCR | `SharedImageOcrGateTest`（5 项：门控一次、协调器预填只登记、未点击时识别结果被忽略、识别后仍需人工解析） |
| share sourceRef 稳定且不含原文 / URI | `shareSourceRefIsStablePrefixedAndFreeOfPayloadDetails`、`sourceRefStaysStableForOneCaptureEvenWhenAskedRepeatedly`、`sharedTextDraftKeepsAStableSourceRefAcrossOutboxRetry` |
| 切换导航清理旧 share target | `leavingTheCaptureFlowClearsTheActiveShare`、`adoptingNothingKeepsTheCurrentShareInsteadOfDroppingIt` |
| 既有回归（QuickCaptureHub / OcrDraftCoordinator / Outbox / confirm gating） | `quickCaptureHubStillOwnsTheFourV07Entries`、`shareDestinationReusesTheExistingManualAndOcrFlows`、`rejectedShareNeverProducesAShareTarget`、`externalShareLayerExposesNoParsePreviewConfirmCapability`，以及原有全部测试类 |

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| Android 单元测试 | `android-app: gradlew.bat --no-daemon testDebugUnitTest` | 通过，28 个测试类共 156 项，0 失败 / 0 错误 / 0 跳过（由 24 类 119 项增至 28 类 156 项） |
| Android 打包 | `android-app: gradlew.bat --no-daemon assembleDebug` | 通过 |
| Android 静态检查 | `android-app: gradlew.bat --no-daemon lintDebug` | 通过，0 error，2 条既有 warning（`DataExtractionRules`、`ObsoleteSdkInt`，非本轮引入） |
| 仓库编译钩子 | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1` | 通过（后端 `mvn -DskipTests package` + 前端 `npm run build`），成功静默、无 Beep / Toast / Popup |
| 权限与版本核对 | `aapt2 dump badging app-debug.apk` | `versionCode='9' versionName='0.8.0'`；未新增广泛权限 |
| 分享入口核对 | `aapt2 dump xmltree --file AndroidManifest.xml app-debug.apk` | 只有 `android.intent.action.SEND` + `text/*` + `image/*`，无 `ACTION_SEND_MULTIPLE` |

- 本地 Debug APK：55,800,680 bytes，SHA-256 `E5E6737C11C305BFF76639F3260E1FF1028FC28E32F8CF67387723E835C7236A`；APK 不提交到 Git。
- 注意：debug APK 的本地字节不可复现（同一份源码连续两次 `assembleDebug` 的体积与 SHA-256 都会不同），因此上面的本机观察值与制品身份无关；只有 CI 工作流产出的 SHA-256 才可作为验收依据。
- **CI 制品状态**：本轮执行进程只做本地提交、不推送，因此 GitHub Actions `Android test APK` 未被本次执行触发，
  Run ID / Artifact ID / 文件名 / CI APK SHA-256 为 `NOT_PRODUCED`；
  不得用上面的本地 APK 哈希冒充 CI artifact，推送后必须按真实工作流输出回填。

## 9. 版本与制品

| 项 | 值 | 证据 |
| --- | --- | --- |
| `versionName` | `0.8.0`（由 `0.7.0` 升级） | `android-app/app/build.gradle.kts` |
| `versionCode` | `9`（由 `8` 递增） | 同上 |
| 已构建 APK 清单 | `versionCode='9' versionName='0.8.0'` | `aapt2 dump badging app-debug.apk` 实测输出 |
| APK 文件命名 | `MyDCA-Board-v0.8.0-<short-sha>.apk` | `.github/workflows/android-test-apk.yml` |
| Artifact 名 | `mydca-android-v0.8.0-<sha>` | 同上 |

- 继续使用一次性 debug 签名（`assembleDebug`），不新增 release signing key，APK 不提交到 Git。
- 触发条件不变：`workflow_dispatch` 或推送到 `v2` 且改动命中 `android-app/**` 或该工作流文件本身。

## 10. 明确未做

- 不自动解析分享内容；不自动 OCR；不自动创建草稿；不自动 preview；不自动 confirm；不自动正式入账；不执行交易。
- 不持久化原始分享文本或图片 URI；不上传分享图片；不新增广泛存储权限。
- 不连接生产数据库、不部署后端或 migration、不修改真实财务记录。
- 未新增后台常驻服务或系统级调度；外部分享的采集仍只在用户前台操作时发生。
- 未实现桌面小组件 / 通知栏快捷入口等其它系统级入口；本轮只接系统 Share Sheet 的文本与单图。
