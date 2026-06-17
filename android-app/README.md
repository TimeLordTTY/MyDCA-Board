# MyDCA Android App

MyDCA Android App 是 Phase3 的原生移动端基础壳，用于承接今日待办、草稿查看、草稿预览和用户手动确认体验。

## 当前范围

- Kotlin + Jetpack Compose + Material 3。
- 首版包含总览、今日待办、草稿箱、账户 / 流水 / 持仓、设置五个底部导航入口。
- 设置页包含 BaseUrl 和 Bearer Token 配置占位。
- API 边界已声明 `/api/v2/todos/today` 和 `/api/v2/drafts` 相关接口。
- 当前 UI 默认使用安全空状态，不会自动预览、自动确认或写正式账本。

## 本地开发要求

- JDK 17。
- Android Studio。
- Android SDK，建议包含 `compileSdk = 35` 对应平台。
- Gradle 可使用 Android Studio 内置 Gradle，或后续补充项目 Gradle wrapper。

首次打开工程时，在 Android Studio 中选择 `android-app/` 目录。

如果本地需要配置 SDK 路径，请创建本机私有文件：

```properties
sdk.dir=C:\\Users\\<your-user>\\AppData\\Local\\Android\\Sdk
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

## 安全边界

- 不要提交真实 token、cookie、密码或私有服务地址。
- Token 后续必须进入 Android 安全存储，不得硬编码到源码。
- 生产构建应使用 HTTPS BaseUrl，不应依赖 debug 明文 HTTP 配置。
- Android App 不直接写数据库。
- Android App 不计算最终账本影响，只展示后端 preview。
- `confirm` 必须由用户手动点击，并由后端 `/api/v2/drafts/{draftId}/confirm` 统一校验。
- 当前不包含通知监听、OCR、支付通知解析或真实大模型接入。

## 验证命令

在配置 Android SDK 和 Gradle 后，可执行：

```bash
cd android-app
gradle test
gradle assembleDebug
```

当前仓库没有提交 Gradle wrapper；如后续需要无人值守 CI 构建，可在确认版本和二进制来源后再补充 wrapper。
