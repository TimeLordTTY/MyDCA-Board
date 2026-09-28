# MyDCA Android v0.9.0 桌面快速记账小组件

- **作者**：Codex（AiCore 普通工程任务执行）
- **任务**：`task-mydca-android-v090-home-widget-quick-capture-20260928`
- **工程仓库**：`TimeLordTTY/MyDCA-Board@v2`
- **风险级别 / 授权**：L3，owner 直接批准（`approval_source: direct_owner_chat`）
- **完成时间**：2026-09-28 +08:00

> 本文记录本次任务的当时事实。当前状态与后续计划以 `docs/CURRENT_DEVELOPMENT_STATE.md` 为准。

## 1. 目标

给 Android 桌面增加一个安静、不打扰的快速记账小组件：主人不必先打开 App、切页面，就能从系统桌面进入既有的“记一笔 / 手工记账 / 图片识别 / 草稿箱”。

本任务**只新增系统级入口**，不新增任何自动记账逻辑：小组件点击后只能打开 App 并定位到既有页面，不在后台解析、建档、预览、确认或正式入账，也不发通知、不弹窗、不播放声音。

## 2. 交付内容

### 2.1 形态与技术选型

- 使用系统 `AppWidgetProvider` + `RemoteViews`，**未引入 Jetpack Glance**，未做任何架构重构。
- 首版为 2×2 紧凑布局，完整尺寸提供四个入口：`记一笔` / `手工记账` / `图片识别` / `草稿箱`。
- 尺寸不足时按 `WidgetSizePolicy` 自动折叠为两个主入口：`记一笔` + `草稿箱`（`WidgetEntryPoints.COMPACT`）。
- 小组件只做静态入口，不显示余额、持仓、通知候选原文，也不显示任何计数，因此不需要实时刷新。
- 小组件文案全中文，文案唯一来源是 `WidgetNavigationTarget.label`（布局里不写死文字），视觉沿用现有 Material 3 蓝色主色，不做动画。

尺寸规则（`WidgetSizePolicy`）：

| 条件 | 布局 | 入口 |
| --- | --- | --- |
| `minWidth ≥ 180dp` 且 `minHeight ≥ 110dp` | `@layout/widget_quick_capture` | 四个全部 |
| 明确量到 `minWidth < 180dp` 或 `minHeight < 110dp` | `@layout/widget_quick_capture_compact` | 记一笔 / 草稿箱 |
| 系统尚未给出尺寸（`<= 0`，例如刚添加到桌面） | `@layout/widget_quick_capture` | 四个全部（不退化为两个） |

### 2.2 一次性导航协议

- 受控枚举 `WidgetNavigationTarget`：`QuickCapture` / `ManualText` / `ImageOcr` / `Drafts`，每个目标带固定 action（`com.timelordtty.mydca.widget.action.*`）与固定 `requestCode`（4201–4204）。
- 小组件**只构造显式 Intent**：`Intent(context, MainActivity::class.java)`，只指向本 App 的 `MainActivity`。
- **不接受任意外部 route 字符串**：只有四个固定 action 能解析出目标，未知 / 空白 / 系统 action / 分享 action / 大小写变体一律安全忽略（`WidgetNavigationResolverTest`）。
- `onCreate` 与 `onNewIntent` 共用同一解析规则（`WidgetNavigationResolver.resolve(intent?.action)`），冷启动与复用实例结果一致。
- 同一目标只消费一次：`WidgetNavigationPendingStore.consume()` 取出即清空，Compose 重组不会重复跳转。
- 目标消费后立即清理 pending，并同时清掉 `NotificationNavigationTarget`、`ExternalSharePendingStore` 与旧 `selectedDraftId` / `QuickCaptureFocus`，避免与 v0.7 / v0.8 的一次性状态互相污染。

### 2.3 优先级（三个一次性系统入口）

`WidgetNavigationArbiter` 固定优先级：**桌面小组件 > 外部分享 > 通知候选**。

- 桌面点击是用户最新的显式系统级操作，接管时必须清掉分享与通知目标。
- 没有小组件目标时沿用 v0.8 既有规则：分享优先于通知候选。
- `MyDcaApp` 里三个入口合并为**一个**确定优先级的消费点（`LaunchedEffect(pendingWidgetTarget, pendingShare, notificationTarget)`），一次只应用最高优先级目标，因此不会出现“先跳目标页、再被残留目标二次跳转”。

### 2.4 未登录处理

- 未登录时小组件目标只保留在当前进程内存（`WidgetNavigationPendingStore`），走正常登录流程。
- 登录成功后消费一次并跳到目标页面；不把 Token、账本数据或敏感内容写入小组件 extras（**小组件 Intent 不含任何 extra**）。
- 进程被杀导致目标丢失时安全回到普通首页，不做任何跨进程持久化恢复。

### 2.5 PendingIntent 与 Manifest 最小化

- 每个入口使用独立 `requestCode` + 独立 action，避免四个 `PendingIntent` 被系统复用（`WidgetNavigationTargetTest.actionAndRequestCodeIdentifyEachPendingIntentUniquely`）。
- 使用 `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`（`targetSdk 35`）。
- 启动标志 `NEW_TASK | CLEAR_TOP | SINGLE_TOP`：复用已有任务，已在前台时走 `onNewIntent`。
- 不暴露可注入任意 route 的组件；`MainActivity` 未为此新增任何 intent-filter（小组件走显式 Intent）。
- Receiver 只声明系统 AppWidget 协议：`android.appwidget.action.APPWIDGET_UPDATE` + `android.appwidget.provider` meta-data，不新增自定义广播 action、不声明权限、不覆写 `onReceive`。
  - `android:exported="true"`：与 Android 11 及以前带 intent-filter 的 receiver 默认导出行为一致，保证系统一定能投递 `APPWIDGET_UPDATE`；由于只保留系统 action 且 `onReceive` 未覆写、不承载任何数据通道，暴露面仍是最小的。`lintDebug` 未就此产生任何 `ExportedReceiver` 提示。
- `appwidget-provider` 里 `updatePeriodMillis=0`：**没有任何周期性后台刷新**；没有 Alarm、WorkManager、前台服务或常驻通知。

## 3. 明确不做（安全边界）

- 不做桌面余额 / 资产 / 计数展示，不做动态刷新。
- 不做通知栏常驻入口，不做 Quick Settings Tile。
- 小组件层不连接网络、不查询后端、不读取或写入真实账本数据。
- 不自动 parse / OCR / 建档 / preview / confirm，不自动正式入账、不自动交易。
- 不新增任何系统权限（权限清单仍为 `INTERNET` + `POST_NOTIFICATIONS`）。
- 不连接生产数据库、不部署后端、不执行 migration。
- 点击入口的反馈完全交给系统，不额外 Toast / Popup / 声音。

## 4. 代码与资源清单

新增（main）：

- `android-app/app/src/main/java/com/timelordtty/mydca/widget/WidgetNavigationTarget.kt`（受控枚举：action / requestCode / 中文文案）
- `.../widget/WidgetNavigationResolver.kt`（唯一解析规则，未知 action 安全忽略）
- `.../widget/WidgetNavigationPendingStore.kt`（进程内一次性目标，单调 token + 只消费一次）
- `.../widget/WidgetNavigationHub.kt`（目标 → 既有页面决策，复用 v0.7 面板决策）
- `.../widget/WidgetNavigationArbiter.kt`（三入口确定优先级）
- `.../widget/WidgetSizePolicy.kt`（尺寸 → 完整 / 紧凑）
- `.../widget/WidgetEntryPoints.kt`（完整 / 紧凑入口顺序）
- `.../widget/WidgetEntryViews.kt`（入口 ↔ 布局控件 id 的唯一对应）
- `.../widget/WidgetNavigationIntents.kt`（显式 Intent + PendingIntent，无 extra）
- `.../widget/QuickCaptureWidgetProvider.kt`（AppWidgetProvider，只渲染静态入口 + 绑定点击）
- `app/src/main/res/layout/widget_quick_capture.xml`、`widget_quick_capture_compact.xml`
- `app/src/main/res/xml/widget_quick_capture_info.xml`
- `app/src/main/res/drawable/widget_surface_bg.xml`、`widget_entry_bg.xml`
- `app/src/main/res/values/colors.xml`（新增）、`strings.xml` / `styles.xml`（追加小组件条目）

修改（main）：

- `MainActivity.kt`：`onCreate` / `onNewIntent` 各新增一次 `acceptWidgetNavigation(intent)`（只读 `intent.action`，不读 extra）。
- `ui/MyDcaApp.kt`：三个一次性系统入口合并为一个确定优先级的消费点；底部导航与快速面板主动复位小组件目标。
- `AndroidManifest.xml`：新增 `QuickCaptureWidgetProvider` receiver。
- `app/build.gradle.kts`：`versionCode 9 → 10`、`versionName 0.8.0 → 0.9.0`。
- `.github/workflows/android-test-apk.yml`：APK / Artifact 命名同步 `v0.9.0`。

新增（test）：`app/src/test/java/com/timelordtty/mydca/widget/` 下 7 个测试类共 39 项。

> 说明：打包后的 APK 清单里还会看到 ML Kit / GMS / AndroidX 合并进来的 receiver、service（如 `MlKitComponentDiscoveryService`、`ProfileInstallReceiver`），它们来自依赖库的 manifest 合并，与本轮无关；本文的“最小化”断言针对本仓库自己的 `AndroidManifest.xml`。

## 5. 测试矩阵（任务要求 → 证据）

| 任务要求 | 证据（测试 / 断言） |
| --- | --- |
| 1. QUICK_CAPTURE → QuickCaptureHub | `WidgetNavigationHubTest.quickCaptureOpensTheExistingQuickCaptureHubWithoutChangingPage` |
| 2. MANUAL_TEXT → 现有手工文本采集页 | `WidgetNavigationHubTest.manualTextEntryReusesTheExistingManualTextFlow`（与 `QuickCaptureHub.decide(ManualText)` 逐字段一致） |
| 3. IMAGE_OCR → 现有图片采集页但不自动 OCR | `WidgetNavigationHubTest.imageOcrEntryReusesTheExistingOcrFlowWithoutStartingRecognition`（决策里没有任何“开始识别 / 选图”字段） |
| 4. DRAFTS → Drafts | `WidgetNavigationHubTest.draftsEntryOpensTheDraftInboxWithoutAnyEntryMode` |
| 5. 未知 action / target 安全忽略 | `WidgetNavigationTargetTest.unknownBlankAndForeignActionsAreIgnored`、`WidgetNavigationResolverTest.unrelatedActionsResolveToNoTarget` |
| 6. 同一 widget target 只消费一次 | `WidgetNavigationPendingStoreTest.publishedTargetIsConsumedExactlyOnce`、`repeatedClicksOnTheSameEntryAreDistinctEvents` |
| 7. onCreate / onNewIntent 同一解析规则 | `WidgetNavigationResolverTest.onCreateAndOnNewIntentShareTheSameParsingRule`、`repeatedResolutionIsPureAndHasNoSideEffect` |
| 8. 未登录 pending → 登录后一次消费 | `WidgetNavigationPendingStoreTest.pendingTargetSurvivesUntilLoginAndIsThenConsumedOnce`、`nonWidgetIntentDoesNotOverwriteAPendingTarget` |
| 9. 主动底部导航后旧 widget target 不再触发 | `WidgetNavigationPendingStoreTest.manualNavigationClearsThePendingTarget`（+ `WidgetNavigationArbiter.clearsWidgetTargetOnManualNavigation()`，由 `MyDcaApp` 底导与快速面板主动 `clear()` 落实） |
| 10. widget 与 share / 通知目标优先级确定且无残留 | `WidgetNavigationArbiterTest`（4 项优先级表）、`newerClickReplacesTheOlderTargetInsteadOfStacking` |
| 11. Widget 层无 repository / network / preview / confirm / QuickEntry 能力 | `WidgetNavigationHubTest.hubExposesNoParseDraftPreviewConfirmCapability`、`WidgetManifestContractTest.providerIsAnAppWidgetProviderWithNoExtraCapability` |
| 12. Manifest / AppWidget provider 配置只含必要 receiver / meta-data | `WidgetManifestContractTest.widgetProviderIsDeclaredOnlyForTheSystemAppWidgetProtocol`、`manifestComponentInventoryStaysMinimal`、`widgetInfoDeclaresNoPeriodicRefreshAndNoConfigEntry` |
| 13. 四个 PendingIntent 身份彼此独立 | `WidgetNavigationTargetTest.actionAndRequestCodeIdentifyEachPendingIntentUniquely`、`WidgetManifestContractTest.entryViewIdsAreUniqueAndNameable` |
| 14. v0.7 / v0.8 / 通知候选 / confirm gating 原测试继续通过 | 全量 35 个测试类 195 项全部通过（含 `QuickCaptureHubTest`、`ExternalShare*`、`OcrDraftCoordinatorTest`、`DraftOutbox*`、`DraftCreationGateTest`） |

补充静态契约：布局只引用受控入口 id、紧凑布局确实只含两个入口、布局不写死文案（`layoutsOnlyCarryTheControlledEntryIdsAndNoHardcodedCopy`）。

## 6. 验证与结果

| 验证 | 命令 | 结果 |
| --- | --- | --- |
| Android 单元测试 | `android-app: gradlew.bat --no-daemon testDebugUnitTest` | 通过：35 个测试类共 195 项，0 失败 / 0 错误 / 0 跳过（由 28 类 156 项增至 35 类 195 项） |
| Android 打包 | `android-app: gradlew.bat --no-daemon assembleDebug` | 通过 |
| Android 静态检查 | `android-app: gradlew.bat --no-daemon lintDebug` | 通过：0 error，2 条既有 warning（`DataExtractionRules`、`ObsoleteSdkInt`，非本轮引入）；本轮布局对 `NestedWeights` 已显式声明为可接受，未新增 warning |
| 仓库编译钩子 | `powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1` | 通过（后端 `mvn -DskipTests package` + 前端 `npm run build`），成功静默、无 Beep / Toast / Popup |
| 版本核对 | `aapt2 dump badging app-debug.apk` | `versionCode='10' versionName='0.9.0'` |
| 入口与权限核对 | `aapt2 dump xmltree --file AndroidManifest.xml app-debug.apk` | 本 App 源码 manifest 只声明 `INTERNET` + `POST_NOTIFICATIONS`；receiver 只含 `android.appwidget.action.APPWIDGET_UPDATE` + `android.appwidget.provider`，`MainActivity` 未新增任何 action。合并后的 APK 里出现的 `ACCESS_NETWORK_STATE`、`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 与 ML Kit / AndroidX 组件都来自依赖库合并（v0.8.0 之前即存在，本轮未新增） |
| 补丁卫生 | `git diff --check` | 通过，无空白错误 |

- 本地 Debug APK（本机观察值，不是制品身份）：55,816,715 bytes，SHA-256 `C2CC3CBD10EFCD20177450CC367ACAF2C273A0E8C050BBCAFBEF58E704116617`；APK 不提交到 Git。
- 注意：debug APK 的本地字节不可复现（同一份源码连续两次 `assembleDebug` 的体积与 SHA-256 都会不同），上面的本机观察值与制品身份无关；只有 CI 工作流产出的 SHA-256 才能作为验收依据。

## 7. 版本与制品

| 项 | 值 | 证据 |
| --- | --- | --- |
| `versionName` | `0.9.0`（由 `0.8.0` 升级） | `android-app/app/build.gradle.kts` |
| `versionCode` | `10`（由 `9` 递增） | 同上 |
| 已构建 APK 清单 | `versionCode='10' versionName='0.9.0'` | `aapt2 dump badging app-debug.apk` 实测输出 |
| APK 文件命名 | `MyDCA-Board-v0.9.0-<short-sha>.apk` | `.github/workflows/android-test-apk.yml` |
| Artifact 名 | `mydca-android-v0.9.0-<sha>` | 同上 |

- 继续使用一次性 debug 签名（`assembleDebug`），不新增 release keystore，APK 不提交到 Git。
- 触发条件不变：`workflow_dispatch` 或推送到 `v2` 且改动命中 `android-app/**` 或该工作流文件本身。

### CI 真实制品状态

- source commit：`7fe07527a8d793018d4d7f284a5643359873ddd0`
- GitHub Actions：`Android test APK`
- Run ID：`36367375440`
- 结论：`success`
- Artifact ID：`10946904834`
- Artifact：`mydca-android-v0.9.0-7fe07527a8d793018d4d7f284a5643359873ddd0`
- APK：`MyDCA-Board-v0.9.0-7fe07527.apk`
- CI APK SHA-256：`5E1059E630D2F66C76A93271C62285C36276A7FAAA65867A445709AAD2A0A13C`

该 SHA-256 来自 CI Artifact 内的 `SHA256SUMS.txt`，不使用本地 debug APK 哈希冒充 CI 制品。

## 8. 在 Android 桌面添加该小组件

1. 安装/更新到 v0.9.0 的测试 APK。
2. 长按桌面空白处（或双指捏合）→ 选择「小组件」。
3. 在列表中找到 **MyDCA 快速记账** → 拖到桌面。
4. 拖动边缘可调整大小：宽度/高度小于 180dp × 110dp 时会自动折叠成「记一笔 + 草稿箱」两个入口。
5. 点击任一入口即可进入对应页面；未登录时会先走正常登录，登录后自动进入该页面一次。小组件本身不联网、不读写账本、不自动记账。

## 9. 与后续工作的关系

- v0.7 快速面板、v0.8 系统分享、通知候选、Draft confirm gating 的既有行为与一次性导航语义均未改变，只新增了优先级仲裁与桌面入口。
- 真机体验验收（桌面添加、不同厂商 launcher 的尺寸回调、点击后跳转）仍是人工验收项，不属于普通后台自动任务。
- Quick Settings Tile、通知栏常驻入口、桌面余额展示等仍为后续独立任务。