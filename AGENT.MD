# Agent Guide

本文件是 MyDCA-Board 当前 AI 编码代理指南。若与历史文档冲突，以用户当前指令、`AGENTS.md`、本文件和 `docs/CURRENT_DEVELOPMENT_STATE.md` 为准。

## 开始前

1. 读取 `AGENTS.md`。
2. 读取 `docs/CURRENT_DEVELOPMENT_STATE.md` 和 `docs/DOCUMENT_INDEX.md`。
3. 再读取任务对应模块 README / Phase3 / 专项文档。
4. 同步 `v2`，不要覆盖用户未提交改动。

## 项目结构

- `backend/`：Java 17 + Spring Boot 3.2 + MyBatis + MySQL 8。
- `web/`：Vue 3 monorepo；PC 为主，Mobile H5 为历史兼容。
- `android-app/`：主移动端，Kotlin + Jetpack Compose。
- `scripts/`：行情、指标、调度等工具。
- `sql/initsql/`：初始化结构。
- `sql/updatesql/`：增量 migration。
- `docs/`：设计、实施、Phase 总结和版本专项文档。

## Phase3 核心边界

- 文本、OCR、通知候选、Outbox 只能创建/恢复 DRAFT。
- preview 只是确认前影响预览。
- confirm 必须由用户明确操作并经过后端校验。
- 不自动正式入账、不自动交易。
- 普通工程不连接生产数据库、不修改真实财务记录。
- v0.8 sourceRef 强幂等已实现，但 migration 是否部署必须以目标环境真实 schema 为准。

## 数据与隐私

- 不提交 Token、Cookie、密码、keystore、API key、数据库密码。
- Android Token 使用 Keystore/AES-GCM；密码不持久化。
- 完整支付通知、图片 URI/字节不写普通日志或明文持久化。
- 金额、份额、净值、费率使用项目既有精度和账本口径。

## SQL

- 已发布历史脚本不重写；新变更使用增量 migration。
- 生产 migration 不属于普通后台工程任务。
- v0.8 强幂等上线前先跑重复来源 precheck；不得静默删除真实重复数据。

## 移动端

- Mobile H5 仅历史兼容；新增 Phase3 移动能力默认进入 Android 原生。
- Android 不为方便申请短信、通讯录、广泛相册、定位、录音等无关权限。
- 新入口必须复用现有 DRAFT 安全边界。

## 验证

任何代码或项目文件编辑后必须运行：
`powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1`

成功构建必须静默：**不播放声音、不弹系统通知/窗口**。结果只写 stdout/log，并由 AiCore/Hermes 负责 owner-facing 交付通知。

Android 变更通常至少运行 `testDebugUnitTest`、`assembleDebug`、`lintDebug`。

## 自动执行

正常 owner-approved 工程由 AiCore Git 任务自动调度。实际 provider/model 以 AiCore run record / Delivery Manifest 为准，本仓库不硬编码自动执行模型策略。