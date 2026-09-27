# MyDCA Android v0.7.0 快速记账采集中心

核验日期：2026-09-27
目标仓库：`TimeLordTTY/MyDCA-Board`（分支 `v2`）
对应任务：`task-mydca-android-v07-quick-capture-hub-20260927`（owner 直接批准，L3）
前置基线：Android v0.6.0（`a732c3b` 发布加固；`76c1552` 安全草稿 Outbox）

本任务范围：只优化 Android 端采集体验，把原本分散在草稿箱、今日待办和设置页的四条采集路径收拢为
一个“快速记账采集中心”，并发布 v0.7.0。**不改变任何后端账本语义**，不新增自动 preview / confirm / 正式入账。

本轮不推送：改动只提交到本地 `v2`，由 owner 批准的 Codex auto-executor 在进程退出后校验 `allowed_paths` 再推送。

## 1. 全局“记一笔”入口

- 在已登录的主 `Scaffold` 上新增 `ExtendedFloatingActionButton`，文案为中文“记一笔”。
- 可见规则集中在 `QuickCaptureHub.isEntryVisibleOn(route)`：总览、今日待办、草稿箱、资产四个主要页面可见；
  设置页布局不适合放悬浮入口，保持一致隐藏。已进入手工 / 图片录入子页面时也隐藏，规则集中在同一个 helper 并由单元测试固定。
- 未改动 5 个底部导航的信息架构，也未新增底部导航项。
- 点击入口只打开“快速记账”面板（`ModalBottomSheet`），不执行任何网络写入。

## 2. 快速记账面板

`QuickCaptureSheet` 提供四个入口，每项 = 清晰标题 + 一句说明 + 可选数量徽标，文案全部面向日常用户：

| 入口 | 说明 | 徽标 |
| --- | --- | --- |
| 手工记一笔 | 输入一句话，例如“早餐 18 元 微信支付”，再由你确认解析 | 无 |
| 图片识别 | 用系统相册选图，在本机识别文字，图片不会上传 | 无 |
| 支付通知候选 | 查看本机脱敏候选，只有你手动点击才会生成草稿 | 未处理候选数 |
| 待重试 | 创建草稿失败的采集已加密暂存，只会重试创建草稿 | Outbox 条目数 |

- 面板本身不承载任何解析 / 预览 / 正式确认按钮，只有四个“去哪里采集”的入口。
- 空候选 / 空 Outbox 时入口仍然可见，徽标显示“暂无待处理”，不会把入口隐藏掉。
- 面板与 FAB 只存在于已登录分支内，未登录或接口配置错误时不会渲染，因此不会因此崩溃。

## 3. 路由与返回体验

- 打开 / 关闭面板不产生导航决策（`QuickCaptureHub.onPanelOpen()` / `onPanelDismiss()` 返回 `null`），
  关闭后仍停留在打开面板前的页面。
- 手工记一笔 / 图片识别 → 复用既有 `OcrDraftScreen`（`OcrEntryMode.ManualText` / `OcrEntryMode.Image`），
  仍由用户显式点击“解析记账候选”，再显式点击创建 DRAFT；成功后仍提供“打开草稿”。
- 支付通知候选 → 路由到今日待办的候选区，并在候选区顶部给出“已定位到待处理候选”标记。
- 待重试 → 路由到草稿箱的“本地待重试草稿”区，并给出“已定位到你刚才打开的待重试区域”标记。
- 每次点击入口都会清空旧的 `selectedDraftId` 与通知候选 `candidateId`（`QuickCaptureDecision.clearSelectedDraftId` /
  `clearSelectedCandidateId` 恒为 `true`），避免返回后错误跳页或残留旧高亮。
- 用户主动切换底部导航时统一复位一次性定位（`QuickCaptureHub.focusAfterManualNavigation()`）、清空录入模式，
  并调用新增的 `NotificationNavigationTarget.clear()` 丢弃待处理的通知导航目标。
- 新增的导航状态只有 `QuickCaptureFocus` 一个小型数据类，未引入任何导航框架重构。

## 4. 状态计数与隐私

- 候选数来自 `NotificationCandidateStore.candidates`（本机脱敏候选），统计口径与候选区一致：排除 `DISMISSED`。
- 重试数来自 `DraftOutboxQueue.entries`（加密本地队列）的条目数。
- 面板只显示数量与来源提示，不显示通知原文、Token、密码、Cookie 或图片 URI。
- 计数完全来自本地 StateFlow，本轮未新增任何网络轮询、未新增权限、未新增明文落盘。
- 文案测试断言面板与入口文案不出现 `outbox` / `draft` / `ocr` / `preview` / `confirm` / `quickentry` /
  `manualtext` / `sourceref` 等内部名称。

## 5. 既有安全边界保持不变

- 快速入口与面板本身不调用 parse / draft / preview / confirm：类型守卫测试断言
  `QuickCaptureHub` / `QuickCaptureAction` / `QuickCaptureEntry` / `QuickCaptureBadge` / `QuickCaptureFocus` /
  `QuickCaptureDecision` / `QuickCaptureCounts` 不声明任何包含 `preview` / `confirm` / `ignore` / `quickentry` /
  `createdraft` / `draftfromintent` / `parsetext` / `execute` 的方法，也不在方法签名中出现
  `AiAccountingRepository` / `DraftRepository` / `WealthHubApi`。
- 手工 / OCR 仍需显式点击“解析记账候选”后才 parse，再显式点击后才创建 DRAFT（既有 `OcrDraftCoordinator` 回归不变）。
- 通知候选不会自动生成 DRAFT（既有 `PaymentCandidateCard` 的人工确认对话框不变，本轮只新增定位标记）。
- Outbox 只重试“创建 DRAFT”（`DraftCreationGateway` 只有 `createDraft`，类型层不变）。
- 草稿最终确认仍由既有 `DraftInboxScreen` 守门：`DRAFT` + 当前 preview + `confirmSupported=true` + 二次确认对话框。

## 6. 测试

新增 `QuickCaptureHubTest`（纯 Kotlin，无 instrumentation），覆盖任务要求的测试点：

| 任务要求 | 用例 |
| --- | --- |
| quick capture action 在允许页面可见 | `quickEntryIsVisibleOnMainRoutesAndHiddenOnSettings` |
| 打开 / 关闭面板保持原 route | `openingAndDismissingPanelKeepsCurrentRoute` |
| 手工入口路由正确 | `manualTextEntryRoutesToExistingManualFlow` |
| OCR 入口路由正确 | `imageOcrEntryRoutesToExistingOcrFlow` |
| notification candidate count 与路由正确 | `notificationCandidateEntryRoutesToCandidateSection`、`countsComeFromLocalStoresOnly`、`panelShowsBadgesForCandidatesAndOutbox` |
| outbox count 与路由正确 | `outboxEntryRoutesToOutboxSectionInDrafts`、`countsComeFromLocalStoresOnly`、`panelShowsBadgesForCandidatesAndOutbox` |
| 关闭 / 返回不残留 selectedCandidateId / selectedDraftId | `everyEntryClearsStaleDraftAndCandidateSelection`、`manualNavigationResetsFocusSoStaleHighlightCannotSurvive` |
| quick capture helper 不含 preview / confirm 能力 | `hubExposesNoParsePreviewConfirmOrQuickEntryCapability` |
| 空候选 / 空 Outbox 仍可打开并显示“暂无待处理” | `emptyCountsKeepEntriesVisibleWithPlaceholder`、`negativeCountsAreClampedInsteadOfLeakingRawNumbers` |
| UI 文案不暴露内部类名 / 枚举名 | `panelCopyStaysUserFacingWithoutInternalNames` |

原有回归继续通过：ManualText / OCR / Notification / Outbox 关键路径（`OcrDraftCoordinatorTest`、`TodayTodoStateHolderTest`、
`DraftOutboxReleaseRegressionTest`、`DraftOutboxQueueTest`、`DraftOutboxRetryPolicyTest`、`EncryptedDraftOutboxStorageTest` 等）
与 `DraftEditStateTest` 的 confirm gating 全部不变。

## 7. 版本与制品

| 项 | 值 | 证据 |
| --- | --- | --- |
| `versionName` | `0.7.0`（由 `0.6.0` 升级） | `android-app/app/build.gradle.kts` |
| `versionCode` | `8`（由 `7` 递增） | 同上 |
| 已构建 APK 的清单 | `versionCode='8' versionName='0.7.0'` | `aapt2 dump badging app-debug.apk` 实测输出 |
| APK 文件命名 | `MyDCA-Board-v0.7.0-<short-sha>.apk` | `.github/workflows/android-test-apk.yml` |
| Artifact 名 | `mydca-android-v0.7.0-<sha>` | 同上 |

- 继续使用一次性 debug 签名（`assembleDebug`），不引入 release keystore，APK 不提交到 Git。
- 触发条件不变：`workflow_dispatch` 或推送到 `v2` 且改动命中 `android-app/**` 或该工作流文件本身。

## 8. 验证证据（本轮真实执行）

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| Android 单元测试 | `android-app: gradlew.bat --no-daemon testDebugUnitTest` | 通过，24 个测试类共 119 项测试，0 失败 / 0 错误 / 0 跳过（由 23 类 104 项增至 24 类 119 项） |
| Android 打包 | `android-app: gradlew.bat --no-daemon assembleDebug` | 通过 |
| Android 静态检查 | `android-app: gradlew.bat --no-daemon lintDebug` | 通过，0 error，2 条既有 warning（`DataExtractionRules`、`ObsoleteSdkInt`，非本轮引入） |
| 仓库编译钩子 | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1` | 通过（后端 `mvn -DskipTests package` + 前端 `npm run build`） |
| 权限核对 | `aapt2 dump badging app-debug.apk` | 未新增权限；仍只有清单声明的 `INTERNET` / `POST_NOTIFICATIONS`（其余为依赖库带入） |

- 本地 Debug APK：大小 55,825,296 bytes，SHA-256 `AAE92D1D5C867D89AFC5499237FFA72D3940A7C0BB1769B2F9CE77D6097F4DA0`；APK 不提交到 Git。
- CI 制品状态：本进程按指令不推送，工作流未触发，Run ID / Artifact ID / APK 文件名 / CI APK SHA-256 均为 `NOT_PRODUCED`；
  因此本轮不声称 CI APK 交付完成，需在真实推送后由 `Android test APK` 工作流产出并回填，不得用本地 APK 哈希冒充 CI artifact。

## 9. 明确未做

- 不接入真实大模型做账本决策；不自动 preview、不自动 confirm、不自动正式入账、不自动交易。
- 不新增短信 / 通讯录 / 相册广泛读取 / 定位 / 录音权限；不把通知或 OCR 原文明文持久化。
- 不部署生产后端、不连接生产数据库、不修改真实财务记录。
- 未引入后台常驻服务或系统级调度；Outbox 重试时机与 v0.6 一致。
- 未把 `sourceRef` 唯一约束下推到数据库（本轮 `allowed_paths` 不含 `sql/**`），v0.6 的并发重放窗口保持不变。
