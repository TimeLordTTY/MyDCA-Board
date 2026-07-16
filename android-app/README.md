# MyDCA Android App

MyDCA Android App 是 Phase3 的原生移动端基础壳，用于承接今日待办、草稿查看、草稿预览和用户手动确认体验。

## 当前范围

- Kotlin + Jetpack Compose + Material 3。
- 首版包含总览、今日待办、草稿箱、账户 / 流水 / 持仓、设置五个底部导航入口。
- 今日待办页调用 `GET /api/v2/todos/today`，展示待办数量和列表。
- 草稿箱页调用 `GET /api/v2/drafts`、`GET /api/v2/drafts/{draftId}`、`POST /api/v2/drafts/{draftId}/preview`、`POST /api/v2/drafts/{draftId}/ignore` 和 `POST /api/v2/drafts/{draftId}/confirm`。
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
http://10.0.2.2:8080/
```

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
- 当前未接入 OCR、企业微信入口或真实大模型。

## 草稿确认边界

- BaseUrl 输入无效时，App 会显示配置错误，不会在组合期直接崩溃。
- 文本解析和 AI 入口只生成草稿。
- 草稿箱必须先调用 preview 接口生成影响预览。
- 确认按钮必须同时满足：当前草稿为 `DRAFT`、当前预览 `draftId` 与草稿 ID 一致、`preview.confirmSupported=true`。
- 点击确认前仍会弹出二次确认。
- 最终校验仍由后端 `/api/v2/drafts/{draftId}/confirm` 统一完成。

## 安全边界

- 不要提交真实 token、cookie、密码或私有服务地址。
- Token 由 Android Keystore 管理的 AES-GCM 密钥加密后持久化，不得硬编码到源码或输出到日志。
- 生产构建应使用 HTTPS BaseUrl，不应依赖 debug 明文 HTTP 配置。
- Android App 不直接写数据库。
- Android App 不计算最终账本影响，只展示后端 preview。
- 当前不包含 OCR、企业微信入口或真实大模型接入；通知候选与草稿仍需用户手动确认。

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

- 草稿箱详情页支持编辑 DRAFT 草稿的 `txnType`、`amount`、`note`、`accountId` 和 `accountNameHint`。
- 保存草稿调用 `PUT /api/v2/drafts/{draftId}`，只更新草稿候选内容，不会直接写正式账本。
- `accountId` 是后端真实账户 ID，必须输入正整数；`accountNameHint` 只是提示，不会替代真实账户。
- 保存后旧 preview 会被清空；“保存并预览”会基于保存后的草稿重新生成 preview。
- 确认按钮仍必须等待当前草稿 DRAFT、preview 匹配当前草稿且 `preview.confirmSupported=true`，并由用户二次确认。
- 当前仍未接入企业微信入口、OCR 或真实大模型。

## 真实登录与安全会话

- 后端真实契约为 `POST /api/v2/auth/login`，请求字段是 `username` / `password`，成功响应返回 `token` 和用户摘要。
- 业务请求只向当前配置的 MyDCA API 主机附加 `Authorization: Bearer <token>`；登录、注册和登出请求不会携带陈旧 Token。
- 后端没有 refresh token 契约，因此 Android 不实现伪刷新；受保护接口返回 401 后会清理会话并要求重新登录，不会自动重试。
- App 启动时先从安全存储恢复会话。Token 使用 Android Keystore 管理的 AES-GCM 密钥加密，SharedPreferences 只保存密文和随机 IV。
- 密文损坏或密钥失效时会清理不可用状态并安全降级为未登录；密码始终只存在于当前登录表单内存中。
- 退出登录会调用现有无状态 logout 端点，并始终清除本地 Token；远端请求失败不会阻断本地退出。
- BaseUrl 默认值仍只用于模拟器访问本机开发服务。不要提交账号、密码、Token、Cookie、`local.properties`、keystore 或签名密钥。

本轮真实验证（2026-07-16）：

- `testDebugUnitTest`：通过，共 29 项测试。
- `assembleDebug`：通过。
- `lintDebug`：通过。
- Debug APK：已生成，大小 10,584,229 bytes，SHA-256：`EDF0B2FB6BA57B632F39F8CF5630AE805C338B117BFAC13A398690F50C163C2A`；APK 不提交到 Git。
