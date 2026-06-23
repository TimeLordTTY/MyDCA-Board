# MyDCA Android App

MyDCA Android App 是 Phase3 的原生移动端基础壳，用于承接今日待办、草稿查看、草稿预览和用户手动确认体验。

## 当前范围

- Kotlin + Jetpack Compose + Material 3。
- 首版包含总览、今日待办、草稿箱、账户 / 流水 / 持仓、设置五个底部导航入口。
- 今日待办页调用 `GET /api/v2/todos/today`，展示待办数量和列表。
- 草稿箱页调用 `GET /api/v2/drafts`、`GET /api/v2/drafts/{draftId}`、`POST /api/v2/drafts/{draftId}/preview`、`POST /api/v2/drafts/{draftId}/ignore` 和 `POST /api/v2/drafts/{draftId}/confirm`。
- 设置页包含 BaseUrl 和 Bearer Token 输入，仅用于开发联调，当前只保存在内存中。
- Android App 不接入真实大模型，不自动预览、不自动确认、不直接写数据库。

## 本地开发要求

- JDK 17。
- Android Studio。
- Android SDK，建议包含 `compileSdk = 35` 对应平台。
- Gradle 可使用 Android Studio 内置 Gradle，或后续补充项目 Gradle wrapper。

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
- Token 后续必须进入 Android 安全存储，不得硬编码到源码。
- 生产构建应使用 HTTPS BaseUrl，不应依赖 debug 明文 HTTP 配置。
- Android App 不直接写数据库。
- Android App 不计算最终账本影响，只展示后端 preview。
- 当前不包含真实登录、安全 Token 持久化、通知监听、OCR、支付通知解析、企业微信入口或真实大模型接入。

## 验证命令

在配置 Android SDK 和 Gradle 后，可执行：

```bash
cd android-app
gradle test
gradle assembleDebug
```

当前仓库没有提交 Gradle wrapper；如后续需要无人值守 CI 构建，可在确认版本和二进制来源后再补入 wrapper。

- 验证加固：生成草稿按钮使用候选 ID 作为稳定状态边界，并且只有金额可解析为数值时才允许创建草稿。
