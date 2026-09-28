# 财富中枢系统（Wealth Hub / MyDCA-Board）

**作者**：timelordtty  
**当前事实同步**：2026-09-28

财富中枢是个人/家庭财富管理平台。当前工程已进入 Phase3，重点是在统一账本和草稿安全边界之上，提供 PC/Web 与 Android 原生端的日常资产查看、记账采集、人工确认、行情/指标和后续策略能力。

> 开始开发前先读：`AGENTS.md` → `AGENT.MD` → `docs/CURRENT_DEVELOPMENT_STATE.md` → `docs/DOCUMENT_INDEX.md`。

## 技术栈

- 后端：Java 17 + Spring Boot 3.2 + MyBatis + MySQL 8 + JWT。
- PC Web：Vue 3 + TypeScript + Element Plus。
- Mobile H5：Vue 3 + Vant，历史兼容。
- 主移动端：Android 原生 Kotlin + Jetpack Compose + Material 3。
- Python：行情、指标、调度等任务型工具。

## 当前版本与里程碑

- Android：`0.10.0 / versionCode 11`。
- v0.7 全局“记一笔”快速采集中心、v0.8.0 系统 Share Sheet 文本/单图快速采集、v0.9.0 桌面快速记账小组件、v0.10.0 TRANSFER 转账草稿闭环均已完成。
- 最新后端可靠性里程碑：v0.8 草稿强幂等，result commit `ea8b3618e25c648127c62750c307ae8af976dd56`。
- v0.8 migration 已进入 Git，但未自动部署到数据库。
- v0.10.0 转账草稿闭环已完成：文本候选识别 TRANSFER、双账户影响预览、主人二次确认后经 `QuickEntryService.quickTransfer` 生成一笔正式转账流水，重复确认不重复记账。

## Phase3 当前能力

- 草稿表与 `/api/v2/drafts/**`。
- `/api/v2/ai/accounting/parse-text` 与 `draft-from-intent`。
- Android 真实登录、安全 Token、今日待办、草稿、账户/流水/持仓。
- 手工文本、图片 OCR、支付通知候选。
- 系统分享入口：Share Sheet 文本 / 单张图片只预填到现有人工采集流程。
- 桌面快速记账小组件：四个静态中文入口只打开既有页面，不联网、不读写账本、不自动记账。
- 转账草稿闭环：TRANSFER 候选解析、双账户（转出 / 转入）影响预览、二次确认后生成一笔正式转账流水。
- 加密 Draft Outbox，只重试“创建 DRAFT”。
- sourceRef 应用层幂等 + 数据库强幂等与并发冲突恢复。

## 记账安全边界

`文本/OCR/通知候选 → 用户复核 → DRAFT → preview → 用户二次确认 → 正式账本`

禁止自动 preview、自动 confirm、自动正式入账、自动交易，以及绕过后端账本校验。

转账（TRANSFER）同样走这条链路：解析只产生候选与 DRAFT，必须由主人补齐转出 / 转入账户、看到双账户预览并再次确认后，才通过 `QuickEntryService.quickTransfer` 写入一笔平衡转账流水。

## Android CI

v0.7.0 已有真实成功制品：

- Run ID `36320197608`
- Artifact ID `10931548073`
- APK `MyDCA-Board-v0.7.0-6098a972.apk`
- SHA-256 `D18D0CC67F7428495E6A6F2B0ED50100D556301368D6853FD0489AD2325E3B2B`

v0.8.0 已有真实成功 CI 制品：
- Run ID `36329990920`
- Artifact ID `10934923148`
- Artifact `mydca-android-v0.8.0-e8af769bf5446daa15ccf849b15a5f17786c7fcc`
- APK `MyDCA-Board-v0.8.0-e8af769b.apk`
- CI APK SHA-256 `F3C03CF9685C376782C2DB0CB799836971A63B5B4763BC38A9F1B0A96E837E08`

v0.9.0 已有真实成功 CI 制品：
- Run ID `36367375440`
- Artifact ID `10946904834`
- Artifact `mydca-android-v0.9.0-7fe07527a8d793018d4d7f284a5643359873ddd0`
- APK `MyDCA-Board-v0.9.0-7fe07527.apk`
- CI APK SHA-256 `5E1059E630D2F66C76A93271C62285C36276A7FAAA65867A445709AAD2A0A13C`

v0.10.0 已有真实成功 CI 制品：
- Run ID `36369966197`
- Artifact ID `10949105553`
- Artifact `mydca-android-v0.10.0-e5c544db7d90f82858ddc03a1ca285ec7659e037`
- APK `MyDCA-Board-v0.10.0-e5c544db.apk`
- CI APK SHA-256 `FD39508EA408807DB5ECEEBAFD2B2F4630D766447398E29D1397D8721A5304F0`

## 构建与验证

任务完成后必须运行：
`powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1`

成功构建只写 stdout/log，**不播放提示音，不弹 Windows Toast/Popup**。

Android 常用验证：
- `android-app\gradlew.bat --no-daemon testDebugUnitTest`
- `android-app\gradlew.bat --no-daemon assembleDebug`
- `android-app\gradlew.bat --no-daemon lintDebug`

## 当前下一步

下一项普通工程任务：**v0.11.0 投资买入 / 申购草稿闭环**。先把 BUY / SUBSCRIPTION 做成“候选 → DRAFT → 订单影响预览 → 主人二次确认 → 创建系统内 PENDING 订单”，不自动结算、不自动交易；真机验收继续作为人工项保留。

完整当前状态与长期文档关系见 `docs/CURRENT_DEVELOPMENT_STATE.md` 和 `docs/DOCUMENT_INDEX.md`。