# 财富中枢 Mobile H5（历史兼容）

本目录是 Vue 3 + TypeScript + Vant 4 的历史 Mobile H5 实现。

**当前状态（2026-09-27）**：长期主移动端已经切换到仓库根目录 `android-app/` 的 Kotlin/Jetpack Compose 原生应用。H5 继续用于历史兼容、已有页面维护或明确指定的 Web 移动需求，不再默认承接 Phase3 新移动能力。

当前 Android 已包含真实登录、安全 Token、草稿、OCR、支付通知候选、加密 Outbox、全局“记一笔”快速采集中心；后端 v0.8 已完成草稿强幂等。不要因为本目录旧实现的功能清单而推断原生 App 尚未实现这些能力。

## 开发约定

- 共享 API/type 优先维护在 `../shared/`。
- 依赖必须从 `web/` workspace 根统一安装，避免重复 Vue/Pinia。
- 只有任务明确要求 H5 兼容时才在本目录新增功能。
- 新的系统级移动能力（通知监听、Photo Picker、Share Sheet、Keystore 等）默认进入 `android-app/`。

项目当前事实见 `../../docs/CURRENT_DEVELOPMENT_STATE.md`。