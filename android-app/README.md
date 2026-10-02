# MyDCA Android App

v0.19 功能增量：总览「查看目标进度与本月预算」只读分页，PARTIAL/UNKNOWN 保持未知，中文作用域、仅计划预留和人工重试。详见 [专项说明](GOAL_BUDGET_VIEW.md) 与 [发布回归](../docs/mydca_v019_release_hardening_20261002.md)。

v0.18 功能增量：总览「配置偏离与止盈观察」只读查看主人规则、区间与收益观察；主动读取数学情景预览。GET + 无请求体只读 POST evaluate，不创建/编辑配置、不交易、不占用、不结算、不入账。详见 [专项说明](ALLOCATION_VIEW.md) 与 [发布回归](../docs/mydca_v018_release_hardening_20261002.md)。

v0.17 功能增量：总览「风险观察中心」只读查看严重级别分组提醒、OPEN/RESOLVED 过滤、规则详情和分页历史，保留未知/陈旧提示。仅 GET，不重新评估、不编辑、不 ACK/MUTE。详见 [专项说明](../docs/mydca_v017_risk_center_android_20261002.md)。

v0.16 功能增量：底部「研究」提供 DRAFT/ACTIVE 方案分页列表、证据警告、参数快照与关联回测历史只读详情。仅 GET，不能创建/编辑方案或发起回测。历史先读取最近 50 条，可手动继续读取更早记录；研究结果不代表未来表现。发布收口版本为 0.16.0 / 17，详见 [专项说明](../docs/mydca_v016_research_view_android_20261001.md)。

<!-- CURRENT-SNAPSHOT:START -->
## 当前发布状态（2026-10-02）

- 当前 Android：`versionName=0.19.0`、`versionCode=20`。每日财富雷达与最近回测均为只读；刷新失败保留旧结果并明确标识，未知指标不显示为零。
- v0.19.0 制品：尚无真实 CI Run / Artifact / APK SHA-256；预期 artifact 为 `mydca-android-v0.19.0-<full-sha>`，仅为命名约定。
- v0.15.0 制品：本次只本地提交，尚无 CI Run / Artifact / APK SHA-256 证据。
- v0.14.0 草稿箱支持历史查看、已忽略草稿二次确认恢复及已确认草稿显式复制；复制后仍为 DRAFT，必须重新预览并二次确认。
- v0.13.0 人工结算预览与二次确认闭环已完成：新增「待结算」体验（总览 / 今日待办进入），先「生成结算预览」只读展示现金 / 持仓 / 手续费影响与 fresh 令牌，再「确认结算」并二次确认弹窗后才调用 `POST /api/v2/settlements/confirm`；修改任一字段会清除旧预览，未 preview 不能 confirm，成功后刷新订单 / 持仓 / 资产页面。四类 PENDING 订单（买入 / 申购 / 卖出 / 赎回）均支持；preview 只读不写业务数据，confirm 幂等且事务完整。
- v0.12.0 投资卖出 / 赎回草稿闭环已完成：草稿箱可切换支出 / 收入 / 转账 / 买入 / 申购 / 卖出 / 赎回，卖出 / 赎回表单支持真实产品 + 该产品真实持仓来源 + 份额 + 到账账户 + 备注，预览展示可用 / 本次 / 预计剩余份额与到账账户；确认弹窗明确「当前只创建内部待处理记录，不立即减少持仓，也不立即增加到账余额」。只有后端 `preview.confirmSupported=true` 才可确认，重复确认不重复建订单。
- v0.11.0 投资买入 / 申购草稿闭环已完成：草稿箱可切换支出 / 收入 / 转账 / 买入 / 申购，投资表单支持真实产品 + 单一资金来源账户、订单与 CASH / RECEIVABLE 资金影响预览与「确认创建【产品】买入/申购订单 ¥X？」二次确认；只有后端 `preview.confirmSupported=true` 才可确认，重复确认不重复建订单。
- v0.10.0 TRANSFER 转账草稿闭环已完成：草稿箱可切换支出 / 收入 / 转账，转账表单支持转出 / 转入双账户、双账户影响预览与「确认将 ¥X 从 A 转到 B？」二次确认；只有后端 `preview.confirmSupported=true` 才可确认，重复确认不重复记账。
- v0.9.0 桌面快速记账小组件已完成：四个静态中文入口只打开既有页面，不联网、不读写账本、不自动记账。
- v0.8.0 系统分享快速采集已完成：Share Sheet 文本 / 单图只预填到现有手工 / OCR 流程。
- v0.13.0 制品：待 owner push 后回填（普通自动任务只提交、不 push，本轮不声称 CI APK 已交付）。
- v0.12.0 制品：待 owner push 后回填（普通自动任务只提交、不 push，本轮不声称 CI APK 已交付）。
- v0.11.0 制品：待 owner push 后回填（普通自动任务只提交、不 push，本轮不声称 CI APK 已交付）。
- v0.10.0 已有真实成功 CI 制品：
- Run ID `36369966197`
- Artifact ID `10949105553`
- Artifact `mydca-android-v0.10.0-e5c544db7d90f82858ddc03a1ca285ec7659e037`
- APK `MyDCA-Board-v0.10.0-e5c544db.apk`
- CI APK SHA-256 `FD39508EA408807DB5ECEEBAFD2B2F4630D766447398E29D1397D8721A5304F0`

<!-- CURRENT-SNAPSHOT:END -->

MyDCA Android App 是 Phase3 的原生移动端基础壳，用于承接今日待办、草稿查看、草稿预览和用户手动确认体验。

## 当前范围

- Kotlin + Jetpack Compose + Material 3。
- 首版包含总览、今日待办、草稿箱、账户 / 流水 / 持仓、设置五个底部导航入口。
- 当前版本 `versionName = 0.19.0`（`versionCode = 20`），APK 制品命名为 `MyDCA-Board-v0.19.0-<short-sha>.apk`。
- 今日待办页调用 `GET /api/v2/todos/today`，展示待办数量和列表。
- 草稿箱页调用 `GET /api/v2/drafts`、`GET /api/v2/drafts/{draftId}`、`POST /api/v2/drafts/{draftId}/preview`、`POST /api/v2/drafts/{draftId}/ignore` 和 `POST /api/v2/drafts/{draftId}/confirm`。
- 草稿确认支持支出 / 收入 / 转账 / 买入 / 申购 / 卖出 / 赎回：TRANSFER 需要转出账户 + 转入账户 + 金额；BUY / SUBSCRIPTION 需要主人明确选择的真实产品 + 单一资金来源账户 + 金额；SELL / REDEMPTION 需要主人明确选择的真实产品 + 该产品真实持仓来源账户 + 份额 + 到账账户；确认前必须先看到资金影响预览。确认投资买入 / 申购草稿会创建 PENDING 订单并生成付款账本（CASH CREDIT + RECEIVABLE DEBIT），但不结算、不生成最终持仓；确认卖出 / 赎回草稿只创建内部 PENDING 订单并登记份额占用，不生成账本流水、不改现金余额、不改持仓。
- 待结算页调用 `GET /api/v2/settlements/pending` 列出 PENDING 订单，结算编辑后调用 `POST /api/v2/settlements/preview` 生成只读预览，主人二次确认后携带 `freshPreviewToken` 调用 `POST /api/v2/settlements/confirm` 生成内部结算落账；preview 不写任何业务数据，未 preview 不能 confirm，修改任一字段即失效。
- 未登录时展示真实用户名/密码登录入口；密码不持久化，登录 Token 由 Android Keystore 加密保护。
- Android App 不接入真实大模型，不自动预览、不自动确认、不直接写数据库。

## 本地开发要求

- JDK 17。
- Android Studio 或 Android SDK command-line tools。
- Android SDK，至少包含 `platforms;android-35`、`build-tools;35.0.0` 和 `platform-tools`。
- 项目已提交 Gradle Wrapper：Gradle `8.7`，用于匹配当前 Android Gradle Plugin `8.5.2` 和 JDK 17。

首次打开工程时，在 Android Studio 中选择 `android-app/` 目录。

如果本地需要配置 SDK 路径，请创建本机私有文件：

```properties
sdk.dir=C:\Users\<your-user>\AppData\Local\Android\Sdk
```

该文件应保存为：

```text
android-app/local.properties
```

`local.properties` 已被 `.gitignore` 排除，不应提交。

## 默认 BaseUrl

默认开发地址为：

```text
https://www.timelordtty.cn/
```

默认值用于可安装测试 APK，已与当前 HTTPS 生产入口对齐。开发者仍可在登录页或设置页临时改为 `http://10.0.2.2:8080/` 连接本机模拟器服务；该输入只保留在当前运行，不包含账号、密码或 Token。

这是 Android 模拟器访问宿主机本地后端服务的常见地址，只用于开发占位，不代表生产地址。

Debug 构建会通过 `app/src/debug/AndroidManifest.xml` 允许明文 HTTP，方便本地联调；release 构建不应默认放开明文网络。

## 通知监听基础壳

- App 声明 `NotificationListenerService`，需要用户手动在系统通知监听设置中授权。
- 当前只生成本地内存候选通知，不会自动生成草稿。
- 候选通知只展示脱敏摘要、疑似支付状态、金额和来源提示。
- 金额识别保持保守：验证码、订单号、手机号、卡号等上下文不会作为金额。
- 常驻通知和聚合通知默认跳过，减少系统噪音。
- 完整通知原文不落库、不写文件、不打印到日志。
- 通知候选现在支持用户手动点击“生成草稿”，流程只调用 `parse-text` 和 `draft-from-intent` 创建 `DRAFT` 草稿。
- 通知候选不会自动生成草稿；生成成功后仍必须进入草稿箱手动 preview，并且只有 `preview.confirmSupported=true` 后才能 confirm。
- 当前不会自动 preview、不会自动 confirm、不会写正式账本。
- 当前未接入企业微信入口或真实大模型。

## 草稿确认边界

- BaseUrl 输入无效时，App 会显示配置错误，不会在组合期直接崩溃。
- 文本解析和 AI 入口只生成草稿。
- 草稿箱必须先调用 preview 接口生成影响预览。
- 确认按钮必须同时满足：当前草稿为 `DRAFT`、当前预览 `draftId` 与草稿 ID 一致、`preview.confirmSupported=true`。
- 点击确认前仍会弹出二次确认。
- 最终校验仍由后端 `/api/v2/drafts/{draftId}/confirm` 统一完成。
- 投资买入 / 申购草稿（BUY / SUBSCRIPTION）确认后由后端创建 PENDING 订单并同步生成付款账本，但不调用 SettlementService、不生成最终持仓。
- 投资卖出 / 赎回草稿（SELL / REDEMPTION）确认后由后端只创建 PENDING 订单并登记 SOURCE / TARGET 资金线与份额占用，不生成任何账本流水、不改现金余额、不改持仓，真正的资金与持仓变化只在后续人工结算时产生。

## 安全边界

- 不要提交真实 token、cookie、密码或私有服务地址。
- Token 由 Android Keystore 管理的 AES-GCM 密钥加密后持久化，不得硬编码到源码或输出到日志。
- 生产构建应使用 HTTPS BaseUrl，不应依赖 debug 明文 HTTP 配置。
- Android App 不直接写数据库。
- Android App 不计算最终账本影响，只展示后端 preview。
- 当前不包含企业微信入口或真实大模型接入；图片 OCR、通知候选与草稿仍需用户手动确认。

## 验证命令

在配置 JDK 17 与 Android SDK 后，可执行：

```powershell
cd android-app
.\gradlew.bat --version
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat assembleDebug --no-daemon
```

Debug APK 默认输出位置：

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

`local.properties`、`.gradle/`、`build/` 和 APK 产物均不应提交到 Git。

本轮真实构建验证（2026-07-14）：

- Gradle Wrapper：`8.7`，`distributionUrl=https://services.gradle.org/distributions/gradle-8.7-bin.zip`，已记录 `distributionSha256Sum`。
- JDK：17。
- Android SDK：`platforms;android-35`、`build-tools;35.0.0`、`platform-tools`。
- `.\gradlew.bat testDebugUnitTest --no-daemon --stacktrace`：通过。
- `.\gradlew.bat assembleDebug --no-daemon --stacktrace`：通过。
- `.\gradlew.bat lintDebug --no-daemon --stacktrace`：通过。
- Debug APK：已生成，大小 10,483,798 bytes，SHA-256：A89F380790DDFA6090E1CC8047D6B711D9907BEE026194A48EAFE75EC04D4368；APK 不提交到 Git。

- 验证加固：生成草稿按钮使用候选 ID 作为稳定状态边界，并且只有金额可解析为数值时才允许创建草稿。


## 草稿编辑与账户补全

- 草稿箱详情页支持编辑 DRAFT 草稿的 `txnType`、`amount`、`note`、`accountId`、`accountNameHint`，以及转账的 `targetAccountId`。
- 交易类型支持「支出 EXPENSE / 收入 INCOME / 转账 TRANSFER」；切回支出 / 收入时会清空转入账户，避免目标账户残留到非转账 payload。
- 保存草稿调用 `PUT /api/v2/drafts/{draftId}`，只更新草稿候选内容，不会直接写正式账本。
- `accountId` 是后端真实账户 ID，必须输入正整数；`accountNameHint` 只是提示，不会替代真实账户。
- 保存后旧 preview 会被清空；“保存并预览”会基于保存后的草稿重新生成 preview。
- 确认按钮仍必须等待当前草稿 DRAFT、preview 匹配当前草稿且 `preview.confirmSupported=true`，并由用户二次确认。
- 当前仍未接入企业微信入口或真实大模型。

## 真实登录与安全会话

- 后端真实契约为 `POST /api/v2/auth/login`，请求字段是 `username` / `password`，成功响应返回 `token` 和用户摘要。
- 业务请求只向当前配置的 MyDCA API 主机附加 `Authorization: Bearer <token>`；登录、注册和登出请求不会携带陈旧 Token。
- 后端没有 refresh token 契约，因此 Android 不实现伪刷新；受保护接口返回 401 后会清理会话并要求重新登录，不会自动重试。
- App 启动时先从安全存储恢复会话。Token 使用 Android Keystore 管理的 AES-GCM 密钥加密，SharedPreferences 只保存密文和随机 IV。
- 密文损坏或密钥失效时会清理不可用状态并安全降级为未登录；密码始终只存在于当前登录表单内存中。
- 退出登录会调用现有无状态 logout 端点，并始终清除本地 Token；远端请求失败不会阻断本地退出。
- BaseUrl 默认使用 HTTPS 生产入口，开发联调时可手动切换到模拟器本机地址。不要提交账号、密码、Token、Cookie、`local.properties`、keystore 或签名密钥。

本轮真实验证（2026-07-16）：

- `testDebugUnitTest`：通过，共 29 项测试。
- `assembleDebug`：通过。
- `lintDebug`：通过。
- Debug APK：已生成，大小 10,584,229 bytes，SHA-256：`EDF0B2FB6BA57B632F39F8CF5630AE805C338B117BFAC13A398690F50C163C2A`；APK 不提交到 Git。

## 图片 OCR 到草稿

- 草稿箱内提供“图片识别记账”入口，使用系统 Photo Picker 主动选择单张图片；Manifest 未申请 `READ_MEDIA_IMAGES` 或 `READ_EXTERNAL_STORAGE`。
- OCR 使用 Google ML Kit Text Recognition v2 bundled 中文模型 `com.google.mlkit:text-recognition-chinese:16.0.1`，来自 Google Maven，模型随 APK 分发，不依赖首次远程下载。
- 图片 URI 只存在于当前页面识别协程中，图片、URI 和字节不会进入 Retrofit 请求，也不会写入文件、数据库或偏好设置。
- 新图片会清空上一张图片的识别、候选和草稿状态；异步结果按随机请求 ID 绑定，旧结果不能覆盖新图片。
- OCR 文本仅保存在页面内存中并允许编辑。只有用户点击“解析记账候选”后，当前编辑文本才会发送给自己的 MyDCA 后端。
- 候选 intent 由用户复核后，才允许调用既有 `draft-from-intent` 创建 `DRAFT`；来源类型复用后端已支持的 `APP_FORM`。
- 创建成功后不会自动 preview、不会自动 confirm、不会写正式账本；用户仍需进入草稿箱补齐账户并二次确认。
- 2026-07-16 自动验证：36 项 JVM 单元测试、`assembleDebug`、`lintDebug` 均通过。
- Debug APK：大小 56,429,546 bytes，SHA-256：`B1DBD06EEC2CB95DBE5ABE5BAF60EB8C1D47117CF9DA4170A754941B7545B2C0`；体积增长来自随 APK 分发的离线中文模型。
- 真实设备 Photo Picker、中文支付截图识别准确率和不同厂商 URI 兼容性仍需手工验证，不应把 JVM 测试视为真实 OCR 图片验证。

## v0.5 日常可用化

- 总览、资产、今日待办、草稿箱统一改为非破坏式刷新：已有数据时刷新只显示“最近更新”时间与刷新按钮，不隐藏当前数据；刷新失败只提示错误并保留上一次成功数据，仅首次加载失败才显示整页错误态。
- 草稿箱新增“手工记一笔”入口：直接把输入文本交给后端解析为记账候选，跳过图片阶段；解析与生成 DRAFT 仍必须由用户逐步点击，不会自动 preview、confirm 或正式入账。
- 草稿详情新增“解析信息复核”，展示 DRAFT 状态、交易类型、金额、账户、备注、置信度和缺失字段；草稿列表展示待人工确认数量与摘要。
- 草稿账户选择统一走 `DraftAccountSelection`：普通消费只允许 SPENDABLE 叶子账户，父账户、RESERVED、INVESTABLE 和待分配账户会给出明确原因且不可选择。
- 资产页可对单个账户按需读取 `GET /api/v2/mobile/accounts/{accountId}` 的服务端详情，不参与列表批量刷新。
- 保留 v0.4.1 的资金用途筛选状态与选中态恢复行为。
- 2026-09-26 自动验证：`testDebugUnitTest` 68 项测试通过、`assembleDebug` 通过、`lintDebug` 通过（2 条既有 warning，0 error）。
- Debug APK：大小 55,718,565 bytes，SHA-256：`C8B3D341A968EB9FDB94DE5815FA9D2E1F75B72AAE1DA9EE8E5B65B693307D53`；APK 不提交到 Git。

## v0.6 可靠记账采集（发布加固）

- 版本收口为 `versionName = 0.6.0`（`versionCode = 7`）；已构建 APK 的清单经 `aapt2 dump badging` 实测确认为 `versionCode='7' versionName='0.6.0'`。
- APK 工作流（`.github/workflows/android-test-apk.yml`）制品名与文件名同步为 v0.6.0：artifact `mydca-android-v0.6.0-<sha>`、文件 `MyDCA-Board-v0.6.0-<short-sha>.apk`，继续使用一次性 debug 签名，APK 不提交到 Git。
- 草稿创建失败不再直接丢弃：可恢复的网络 / 5xx 失败会加密进入本地 Outbox（独立密钥别名与独立偏好文件），按 30s / 120s / 600s / 1800s 有限退避重试，默认上限 5 次，用尽转 `EXHAUSTED`。
- 401 / 403 转 `AUTH_PAUSED` 等待重新登录；其他 4xx 与未知异常转 `BLOCKED`，只展示原因供人工修改或丢弃，不做无限自动重试。
- 重试的唯一网络动作是“创建 DRAFT”，类型上不存在 preview / confirm / ignore 能力；重试成功即出队，服务端返回 `DRAFT` 时提供“打开草稿”引导，返回既有 `CONFIRMED` / `IGNORED` 时只展示状态。
- 手工文本、图片 OCR、支付通知候选三入口统一 `sourceRef` 规则（`android-ocr-<requestId>` / 通知 `fingerprint`），同一次采集的重试始终复用同一 `sourceRef`；重放由服务端幂等基础兜底，不产生重复草稿。
- 草稿确认边界不变：仍必须当前草稿为 `DRAFT`、当前 preview 的 `draftId` 与草稿 ID 一致、`preview.confirmSupported=true`，并由用户在二次确认对话框中确认。
- v0.6 明确未做：不自动 preview、不自动 confirm、不自动正式入账、不自动交易、不接入真实大模型、不实现后台常驻无限重试（无常驻服务或系统级调度）。
- 2026-09-27 发布加固自动验证：后端 `mvn -B test` 55 项通过；Android `testDebugUnitTest` 23 个测试类共 104 项通过、`assembleDebug` 通过、`lintDebug` 通过（0 error，2 条既有 warning）；`scripts/post-task-compile-hook.ps1` 通过。
- Debug APK：大小 55,916,141 bytes，SHA-256：`6149D8FD47C8A4A1E9FA38726979341959892F344913884BC8860C3F4859C891`；APK 不提交到 Git。CI 制品（Run ID / Artifact ID / 文件名 / SHA-256）需在真实推送触发工作流后回填，不在本轮声称已交付。

## v0.7 快速记账采集中心

- 版本收口为 `versionName = 0.7.0`（`versionCode = 8`）；已构建 APK 经 `aapt2 dump badging` 实测为 `versionCode='8' versionName='0.7.0'`。
- 已登录主 Scaffold 新增全局“记一笔”悬浮入口（`ExtendedFloatingActionButton`）：总览、今日待办、草稿箱、资产四个主要页面可见，设置页与手工 / 图片录入子页面按统一规则隐藏；不改变既有 5 个底部导航的信息架构。
- 入口打开“快速记账”面板（`ModalBottomSheet`），提供四个采集入口：手工记一笔、图片识别、支付通知候选（显示未处理数量）、待重试（显示 Outbox 数量）；空候选 / 空 Outbox 时入口仍在，徽标显示“暂无待处理”。
- 面板只做导航，不含任何解析 / 预览 / 正式确认按钮：打开与关闭面板都不改变当前页面；选择入口后才跳转，并清空旧的 `selectedDraftId` 与通知候选 `candidateId`。
- 手工 / 图片入口复用既有 `OcrDraftScreen`；通知候选进入今日待办候选区并显示“已定位到待处理候选”；待重试进入草稿箱“本地待重试草稿”区并显示定位标记。
- 用户主动切换底部导航时复位一次性定位、清空录入模式，并丢弃待处理的通知导航目标（`NotificationNavigationTarget.clear()`），避免返回后错误跳页或残留旧高亮。
- 计数只来自本机脱敏候选 store 与加密本地 Outbox，无新增网络轮询、无新增权限、无新增明文落盘；面板文案不展示内部类名 / 枚举名。
- 安全边界不变：快速入口不调用 parse / draft / preview / confirm；手工与 OCR 仍需用户显式点击才解析、再显式点击才创建 DRAFT；通知候选不会自动生成草稿；Outbox 只重试创建 DRAFT；草稿最终确认仍由 `DraftInboxScreen` 的 `DRAFT` + 当前 preview + `confirmSupported=true` + 二次确认守门。
- 新增 `QuickCaptureHubTest`（纯 Kotlin，无 instrumentation）15 项：入口可见性、打开 / 关闭保持原 route、四个入口路由、数量统计口径、空数量占位、状态清空、文案不含内部名称，以及类型守卫断言 helper 不存在 preview / confirm / 快速入账能力。
- 2026-09-27 验证：`testDebugUnitTest` 24 个测试类共 119 项通过、`assembleDebug` 通过、`lintDebug` 通过（0 error，2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 通过。
- Debug APK：大小 55,825,296 bytes，SHA-256：`AAE92D1D5C867D89AFC5499237FFA72D3940A7C0BB1769B2F9CE77D6097F4DA0`；APK 不提交到 Git。CI 制品（Run ID / Artifact ID / 文件名 / CI APK SHA-256）需在真实推送触发工作流后回填，本轮未推送，不声称 CI APK 已交付。

## v0.8.0 系统分享快速采集

- 版本收口为 `versionName = 0.8.0`（`versionCode = 9`）；已构建 APK 经 `aapt2 dump badging` 实测为 `versionCode='9' versionName='0.8.0'`。
- `MainActivity` 只新增一个 `ACTION_SEND` intent-filter（`text/*` + `image/*`），**不声明 `ACTION_SEND_MULTIPLE`**，不新增任何广泛权限，也不修改 `launchMode`；`onCreate` 与 `onNewIntent` 共用同一接收逻辑。
- 新增 `share/` 纯 Kotlin 解析层：`ExternalSharePayload`（只有 `Text` / `Image` 两种形态）、`ExternalShareRequest`、`ExternalShareResolver`（唯一解析规则 + `MAX_TEXT_LENGTH = 2000` 超长截断）、`ExternalShareRejection`（5 类安全拒绝，均带中文提示）、`ExternalShareResolution`、`ExternalShareSourceRef`、`ExternalSharePendingStore`（进程内一次性 pending）、`SharedImageOcrGate`（图片识别门）。
- 文本分享进入既有“手工记一笔”页面并预填：页面固定提示“来自系统分享，尚未解析/未生成草稿”，文字可编辑可清空，进入页面不发起任何网络请求；只有用户点击“解析记账候选”才解析，只有再点击“确认生成 DRAFT”才建档。
- 单图分享进入既有图片识别页但**不自动 OCR**：必须先点击“使用此图片并识别”，复用既有 ML Kit 中文模型；识别后仍需手动解析、手动生成 DRAFT。
- 隐私：分享文本与图片 URI 只存在当前进程内存，不写偏好设置 / 文件、不打印、不上传；同一 payload 只消费一次，未登录期间保留到登录后消费一次，不为跨进程恢复持久化原文。
- 图片只接受系统授权的临时 `content://` URI；`file://` 与未知 scheme 安全拒绝并给中文提示；`ACTION_SEND_MULTIPLE` 多选一律拒绝。
- sourceRef：`android-share-text-<uuid>` / `android-share-image-<uuid>`，不同分享事件必然不同，不含分享原文 / 文件名 / URI；解析、创建 DRAFT 与 Outbox 重试重放复用同一值；既有 `android-ocr-<requestId>` 与通知 `fingerprint` 规则未改动。
- 一次性导航状态收口：返回 / 取消（`onCaptureExit`）、主动切换底部导航（`onManualNavigation`）、成功生成 DRAFT（`onDraftCreated`）都会清空 share target，并同时清空旧 `selectedDraftId`、`NotificationNavigationTarget` 与 `QuickCaptureFocus`；v0.7 四入口与底部导航信息架构未改动。
- 新增 4 个测试类 37 项：`ExternalShareResolverTest` 11 项、`ExternalSharePendingStoreTest` 9 项、`SharedImageOcrGateTest` 5 项、`ExternalShareCaptureRegressionTest` 12 项；覆盖解析与拒绝、超长边界、一次性消费、登录前后消费、预填不解析 / 不建档、图片不自动 OCR、sourceRef 稳定性、导航清理，并反射断言分享层不存在 preview / confirm / QuickEntry 能力。
- 2026-09-27 验证：`testDebugUnitTest` 28 个测试类共 156 项通过（由 24 类 119 项增至 28 类 156 项）、`assembleDebug` 通过、`lintDebug` 通过（0 error，2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 通过（成功静默）。
- Debug APK：大小 55,800,680 bytes，SHA-256：`E5E6737C11C305BFF76639F3260E1FF1028FC28E32F8CF67387723E835C7236A`（debug APK 本地字节不可复现：同一份源码重复 `assembleDebug` 的体积与 SHA-256 都会变化，该值只作本机观察，不作为制品身份）；APK 不提交到 Git。CI 制品（Run ID / Artifact ID / 文件名 / CI APK SHA-256）状态为 `NOT_PRODUCED`，需在真实推送触发工作流后回填，本轮不声称 CI APK 已交付。
- 明确未做：不自动解析分享内容、不自动 OCR、不自动创建草稿、不自动 preview / confirm / 正式入账；未新增后台常驻服务、桌面小组件或通知栏快捷入口。

## v0.9.0 桌面快速记账小组件

- 版本收口为 `versionName = 0.9.0`（`versionCode = 10`）；已构建 APK 经 `aapt2 dump badging` 实测为 `versionCode='10' versionName='0.9.0'`。
- 使用系统 `AppWidgetProvider` + `RemoteViews`，未引入 Jetpack Glance，也未做架构重构；新增 `widget/` 包与 `res/layout/widget_quick_capture*.xml`、`res/xml/widget_quick_capture_info.xml`。
- 完整尺寸（`minWidth ≥ 180dp` 且 `minHeight ≥ 110dp`）提供四个静态中文入口：`记一笔` / `手工记账` / `图片识别` / `草稿箱`；尺寸不足时折叠为 `记一笔` + `草稿箱`。
- 点击只构造显式 Intent（`Intent(context, MainActivity::class.java)`）并只把固定 action 交给 `WidgetNavigationResolver`；action 只能取自受控枚举 `WidgetNavigationTarget`，不接受任意外部 route 字符串，Intent **不带任何 extra**。
- 四个入口使用独立 `requestCode`（4201–4204）与独立 action，`PendingIntent` 使用 `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`，启动标志为 `NEW_TASK | CLEAR_TOP | SINGLE_TOP`；`onCreate` 与 `onNewIntent` 共用同一解析规则。
- 一次性优先级：**桌面小组件 > 外部分享 > 通知候选**（`WidgetNavigationArbiter`）；`MyDcaApp` 只有一个仲裁消费点，接管时同时清空 `ExternalSharePendingStore`、`NotificationNavigationTarget`、`selectedDraftId` 与 `QuickCaptureFocus`。
- 未登录时目标只在当前进程保留，登录后消费一次；进程被杀导致丢失时安全回到普通首页，不做跨进程持久化。
- 小组件层在类型层面没有 repository / network / parse / draft / preview / confirm 能力；`updatePeriodMillis=0`，无后台轮询、Alarm、WorkManager、前台服务或常驻通知；不申请任何新权限，`MainActivity` 未新增 intent-filter。
- 新增 7 个测试类 39 项：`WidgetNavigationTargetTest` 8、`WidgetNavigationResolverTest` 4、`WidgetNavigationPendingStoreTest` 6、`WidgetNavigationHubTest` 6、`WidgetNavigationArbiterTest` 4、`WidgetSizePolicyTest` 5、`WidgetManifestContractTest` 6；覆盖解析与安全忽略、一次性消费、登录前后消费、主动导航清理、优先级仲裁、尺寸折叠、PendingIntent 身份独立，以及 Manifest / appwidget-provider / 布局静态契约。
- 2026-09-28 验证：`testDebugUnitTest` 35 个测试类共 195 项通过（由 28 类 156 项增至 35 类 195 项）、`assembleDebug` 通过、`lintDebug` 通过（0 error，2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 通过（成功静默）。
- Debug APK：大小 55,816,715 bytes，SHA-256：`C2CC3CBD10EFCD20177450CC367ACAF2C273A0E8C050BBCAFBEF58E704116617`（debug APK 本地字节不可复现：同一份源码重复 `assembleDebug` 的体积与 SHA-256 都会变化，该值只作本机观察，不作为制品身份）；APK 不提交到 Git。CI 制品（Run ID / Artifact ID / 文件名 / CI APK SHA-256）状态为 `NOT_PRODUCED`，需在真实推送触发工作流后回填，本轮不声称 CI APK 已交付。
- 明确未做：不做桌面余额 / 资产展示，不做动态计数或后台刷新，不做通知栏常驻入口，不做 Quick Settings Tile，不自动 parse / OCR / draft / preview / confirm，不自动正式入账、不自动交易，不新增广泛系统权限。

## v0.10.0 TRANSFER 转账草稿闭环

- 版本收口为 `versionName = 0.10.0`（`versionCode = 11`）；转账草稿继续走既有安全链路：`DRAFT → fresh preview → 主人二次确认 → 正式账本`。
- 草稿编辑新增「转账 TRANSFER」类型：转出账户（`accountId`）、转入账户（`targetAccountId`）、金额、备注；两边都只能选真实可记账叶子账户，转出 / 转入不能相同。
- 转账不受「日常消费只允许 SPENDABLE」限制：`SPENDABLE` / `RESERVED` / `INVESTABLE` 之间允许转移；币种不一致前端提前提示，最终以后端 preview 为准。
- TRANSFER 预览中文展示「从 / 到 / 金额 / 转出账户变动 / 转入账户变动 / 风险提示」；确认弹窗标题为「确认将 ¥X 从 A 转到 B？」而不是通用确认文案。
- 确认仍受 fresh preview gate 控制：必须当前草稿为 `DRAFT`、preview 匹配当前草稿且 `preview.confirmSupported=true`，再由用户二次确认；重复确认不重复记账，`IGNORED` 草稿不可确认。
- 转账不会自动发生：手工 / OCR / 支付通知候选 / 系统分享 / 桌面小组件任何入口都不具备转账能力，后端也不会在 parse 或创建 DRAFT 后自动 confirm。
- 2026-09-28 验证：`testDebugUnitTest` 35 个测试类共 206 项通过（由 195 项增至 206 项）、`assembleDebug` 通过、`lintDebug` 通过（0 error，2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 通过（成功静默）。
- Debug APK：大小 55,821,214 bytes，SHA-256：`4D5C1443EE30B0A8315ECBB848A3FFAD62ED3B675861236361B8971552C087DB`（debug APK 本地字节不可复现：同一份源码重复 `assembleDebug` 的体积与 SHA-256 都会变化，该值只作本机观察，不作为制品身份）；APK 不提交到 Git。CI 制品（Run ID / Artifact ID / 文件名 / CI APK SHA-256）状态为 `NOT_PRODUCED`，需在真实推送触发工作流后回填，本轮不声称 CI APK 已交付。
- 明确未做：不做自动 preview / confirm / 转账 / 交易，不做跨币种转账，不做投资订单 / 结算类草稿确认，不新增数据库表或 migration，不新增系统权限。

## v0.11.0 投资买入 / 申购草稿闭环

- 版本收口为 `versionName = 0.11.0`（`versionCode = 12`）；投资草稿继续走既有安全链路：`DRAFT → fresh preview → 主人二次确认 → 正式订单 / 账本`。
- 草稿编辑新增「买入 BUY」与「申购 SUBSCRIPTION」类型：必须由主人明确选择真实产品（`productId`）、单一资金来源账户（`accountId`）、金额，可选备注 / 预期净值日期 / 预期确认日期；产品名称提示只作人工提示，禁止自动匹配真实 `productId`。
- 投资资金账户过滤：只允许 `INVESTABLE` 真实叶子账户；`BOND_REPO` 额外允许 `RESERVED`；`SPENDABLE` / 普通 `RESERVED` / 父账户 / VIRTUAL 一律阻断并给出中文提示；产品币种必须与账户币种一致，最终以后端 preview 为准。
- 投资预览中文展示「产品 / 资金来源 / 可用余额 / 付款账户变动 / 待结算应收变动」；确认弹窗标题为「确认创建【产品】买入/申购订单 ¥X？」，正文为「确认后将立即从【账户】扣除 ¥X，并增加同额待结算应收；订单仍需后续结算，不会自动成交。」。
- 只有主人二次确认后，后端才复用既有 `OrderService.createInvestmentDraftOrder` 创建 `status=PENDING` 订单并生成付款账本（CASH CREDIT + RECEIVABLE DEBIT）；`willCreateSettlement=false`、`willAffectHolding=false`，不自动结算、不生成最终持仓、不调用真实交易渠道。
- 确认仍受 fresh preview gate 控制：必须当前草稿为 `DRAFT`、preview 匹配当前草稿且 `preview.confirmSupported=true`，再由用户二次确认；重复确认不重复建订单或付款账本，`IGNORED` 草稿不可确认。
- 投资不会自动发生：手工 / OCR / 支付通知候选 / 系统分享 / 桌面小组件任何入口都不具备投资下单能力，后端也不会在 parse 或创建 DRAFT 后自动 confirm。
- 2026-09-28 验证：`testDebugUnitTest` 35 个测试类共 221 项通过（由 206 项增至 221 项）、`assembleDebug` 通过、`lintDebug` 通过（0 error，2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 通过（成功静默）。
- Debug APK 本地字节不可复现，体积与 SHA-256 只作本机观察，不作为制品身份；APK 不提交到 Git。CI 制品（Run ID / Artifact ID / 文件名 / CI APK SHA-256）状态为 `NOT_PRODUCED`，需在真实推送触发工作流后回填，本轮不声称 CI APK 已交付。
- 明确未做（v0.11.0 范围）：不做自动 preview / confirm / 结算 / 交易，不做多资金来源组合投资，不新增数据库表或 migration，不新增系统权限；SELL / REDEMPTION 作为独立任务在后续 v0.12.0 完成。

## v0.12.0 投资卖出 / 赎回草稿闭环

- 版本收口为 `versionName = 0.12.0`（`versionCode = 13`）；卖出 / 赎回草稿继续走既有安全链路：`DRAFT → fresh preview → 主人二次确认 → 仅创建内部 PENDING 记录`。
- 草稿编辑新增「卖出 SELL」与「赎回 REDEMPTION」类型：必须由主人明确选择真实产品（`productId`）、该产品的真实持仓来源账户（`sourceAccountId`）、份额（`shares`）、到账账户（`targetAccountId`），可选备注；产品名称提示与持仓来源提示只作人工提示，禁止自动匹配真实产品 / 持仓来源 / 到账账户。
- 产品选定后复用 `GET /api/v2/holdings/product/{productId}/by-account` 只读展示该产品的真实持仓来源；份额必须大于 0，最终以后端 preview 的可用份额校验为准。
- 卖出 / 赎回预览中文展示「产品 / 持仓来源 / 当前可用份额 / 本次份额 / 预计剩余份额 / 到账账户」；可用份额已扣除同产品 / 来源账户下仍为 PENDING 的 SELL / REDEMPTION 占用份额，避免内部重复占用；到账账户只允许当前可见、active REAL 叶子账户，禁止 VIRTUAL / POSITION / 父账户，且币种必须与产品一致。
- 确认弹窗标题为「确认创建【产品】卖出/赎回 X 份（来源 A）的内部待处理记录？」，正文为「确认后只创建内部 PENDING 待处理记录，并占用【A】的 X 份；不会立即减少持仓，也不会立即增加【B】的到账余额。真正的资金与持仓变化只在后续人工结算时产生。」。
- 只有主人二次确认后，后端才复用既有 `OrderService.createSellRedeemDraftOrder` 创建 `status=PENDING` 订单并登记 `SOURCE`（sourceAccountId + shares）/ `TARGET`（targetAccountId）资金线；`willCreateLedgerTxn=false`、`willCreateSettlement=false`、`willAffectHolding=false`，不生成账本流水、不改现金余额、不改持仓、不自动结算、不调用真实交易渠道。
- 确认仍受 fresh preview gate 控制：必须当前草稿为 `DRAFT`、preview 匹配当前草稿且 `preview.confirmSupported=true`，再由用户二次确认；重复确认幂等，异常整体回滚且草稿保持 `DRAFT`，`IGNORED` 草稿不可确认。
- 切换到其它交易类型会清理 SELL / REDEMPTION 专属字段（份额 / 持仓来源 / 到账账户），保证候选 payload 干净。
- 卖出 / 赎回不会自动发生：手工 / OCR / 支付通知候选 / 系统分享 / 桌面小组件任何入口都不具备交易能力，后端也不会在 parse 或创建 DRAFT 后自动 confirm。
- 2026-09-28 验证：`testDebugUnitTest` 35 个测试类共 230 项通过（由 221 项增至 230 项）、`assembleDebug` 通过、`lintDebug` 通过（0 error，2 条既有 warning）、`scripts/post-task-compile-hook.ps1` 通过（成功静默）。
- Debug APK 本地字节不可复现，体积与 SHA-256 只作本机观察，不作为制品身份；APK 不提交到 Git。CI 制品（Run ID / Artifact ID / 文件名 / CI APK SHA-256）状态为 `NOT_PRODUCED`，需在真实推送触发工作流后回填，本轮不声称 CI APK 已交付。
- 明确未做：不做自动 preview / confirm / 结算 / 交易，不做跨账户 / 跨产品份额拆分，不做自动匹配持仓来源，不新增数据库表或 migration，不新增系统权限。

## v0.13.0 人工结算预览与二次确认闭环

- 版本收口为 `versionName = 0.13.0`（`versionCode = 14`）；结算走既有安全链路：`PENDING 订单 → 只读 preview → 主人二次确认 → 携带 fresh 令牌 confirm → 内部结算落账`。
- 新增「待结算」体验：从总览 / 今日待办进入待结算订单列表（`GET /api/v2/settlements/pending`），选择订单进入结算编辑页 / 弹层，填写真实结算结果。
- 按 `orderType` 动态字段：BUY / SUBSCRIPTION 为确认日期 / 净值日期 / 实际净值 / 实际份额（可自动计算并展示计算值）/ 手续费；SELL / REDEMPTION 追加实际确认金额。
- 结算输入经 `POST /api/v2/settlements/preview` 生成只读预览：中文展示「哪些账户 +/− 多少现金、哪些持仓 +/− 多少份额、手续费多少」，并返回 `freshPreviewToken` / `previewFingerprint`；preview 不写 `settlement_confirm` / 账本，也不改 `reserved_amount` / `initial_shares` / 订单状态。
- 按钮流程固定为「生成结算预览」→ 展示详细影响 → 「确认结算」→ 再弹一次中文确认弹窗 → `POST /api/v2/settlements/confirm`；页面打开不自动 preview，更不自动 confirm。
- fresh preview gate：修改任一结算字段即清除旧预览；只有 `preview.confirmSupported=true` 且携带与当前订单 / 资金 / 账户 / 输入一致的 `freshPreviewToken` 才可 confirm；后端会重新计算指纹并比对，任一变化即失效。
- 结算语义与后端一致：BUY / SUBSCRIPTION 结算清理 `RECEIVABLE` 并形成 `POSITION` / 关联账户与手续费，不重复扣下单现金；SELL / REDEMPTION 结算才真正产生 `CASH` / `POSITION` / `FEE` 影响。confirm 幂等、异常整体回滚。
- 新增 `SettlementRepository`、`SettlementEditState`、`SettlementUiState`（`PendingSettlementUiState` / `SettlementPreviewUiState` / `SettlementStateHolder`）与 `PendingSettlementScreen`；成功后通过 `onSettlementConfirmed` 刷新订单 / 持仓 / 资产相关页面。
- 结算不会自动发生：手工 / OCR / 支付通知候选 / 系统分享 / 桌面小组件任何入口都不具备结算或交易能力，后端也不会在 parse 或创建 DRAFT 后自动 confirm。
- 2026-09-28 验证：`testDebugUnitTest` 共 267 项通过（由 230 项增至 267 项）、`assembleDebug` 通过、`lintDebug` 通过、`scripts/post-task-compile-hook.ps1` 通过（成功静默）。
- Debug APK 本地字节不可复现，体积与 SHA-256 只作本机观察，不作为制品身份；APK 不提交到 Git。CI 制品（Run ID / Artifact ID / 文件名 / CI APK SHA-256）状态为 `NOT_PRODUCED`，需在真实推送触发工作流后回填，本轮不声称 CI APK 已交付。
- 明确未做：不做自动 preview / confirm / 结算 / 交易，不做跨账户 / 跨产品结算拆分，不新增数据库表或 migration，不新增系统权限。
