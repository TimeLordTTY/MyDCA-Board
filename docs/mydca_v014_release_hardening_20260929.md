# v0.14 发布硬化与验证

- Android 版本：`0.14.0`，`versionCode 15`；该版本在前置任务中已设置，本轮核对未再递增。
- CI `Android test APK` 工作流在 `v2` 普通 push 且改动 `android-app/**` 或工作流文件时触发；制品命名已同步为 `mydca-android-v0.14.0-<commit>`，内含 `MyDCA-Board-v0.14.0-<short-sha>.apk`、`SHA256SUMS.txt` 和构建元数据。
- Android 最近回测页加载失败时清除过期结果，显示网络/登录提示与显式重试；结算历史失败时不再同时显示“暂无结算历史”。PC 待结算列表失败时显示页内错误与重试，策略实验室区分权限失败、加载失败和空数据。
- 采集入口、草稿、转账、投资订单、结算、Outbox 与回测的单元回归由本轮 Android、后端、Python 测试覆盖。无真机或生产数据的 UI 与端到端验收仍需主人完成。

## 验证记录

本地验证（2026-09-29）：Android `testDebugUnitTest assembleDebug lintDebug` 全部通过；后端 `mvn -B test` 183 项通过；前端 `npm run build` 通过；Python `unittest discover -s core/backtest -p 'test_*.py'` 5 项通过；`scripts/post-task-compile-hook.ps1` 后端和前端构建通过；`git diff --check` 通过。此处的回归为无生产数据的本地自动化验证，不能代替真机操作验收。

真实 GitHub Actions Run ID、Artifact ID、CI APK SHA-256：**待 push 后验证**。本轮只提交本地 Git，不推送，不将本地 APK 哈希充作 CI 制品证据。

## 发布边界

- 真机仍需人工验收大字体/TalkBack、旋转与进程恢复、冷启动及离线体验，以及 Share/Widget/通知/OCR → DRAFT → preview/confirm 的完整交互。
- v0.8 强幂等、v0.14 草稿生命周期事件与结算审计关联所需生产 migration 均未由本任务执行；部署需独立授权与目标环境核查。
- 不连接生产数据库、不修改真实财务记录、不自动交易；DRAFT、只读 preview 和主人明确 confirm 的边界不变。
