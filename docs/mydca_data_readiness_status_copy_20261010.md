# 数据就绪页结构状态文案修正（2026-10-10）

## 范围与事实证据

任务：task-aiw-r1-01-readiness-status-copy-20261010。仅修改 PC 数据就绪展示、组件测试与说明，不修改后端或 shared GET 契约。

已 fetch 并读取远端 main/v2：main 为 b9560d35f4458c8b4c7fbd8ecba390b66f216821，v2 与本地基线均为 cbab2c57da332354ce927cddba5205edb0923715。历史提交 150c6d88a3f9d37c4ff507ecdaaf1eed96cf66ff 的 docs/mydca_production_deployment_20261010.md 已记录指定生产环境 9 表、2 字段、2 唯一索引及最终部署验收；本任务仅阅读仓库记录，未再次连接环境核验。该报告不能证明其他环境或当前业务来源完整。

现有 backend/DATA_READINESS_API.md 与 DataReadinessService 的 SCHEMA 固定 UNKNOWN；授权业务读取不包含实时迁移覆盖事实。旧版静态黄色告警及 SCHEMA 文案会把接口的验证能力边界误表述为未部署事实。

## 改动

- 页面使用中性结构说明，展示「数据库结构由部署记录核验，当前页面不能实时验证其结构状态」。注明可阅读仓库报告路径、目标环境边界、仓库访问权限和管理员详细证据访问边界；不链接服务器或提供探测入口。
- SCHEMA 展示与脱敏摘要统一为 UNKNOWN / 本页无法实时验证，解释 UNKNOWN 不表示迁移未部署；展示层替换旧接口结构断言，不更改原始响应。
- 资产、目标、预算等业务项的 READY/PARTIAL/UNKNOWN/UNAVAILABLE、来源、时效和权限保持原有语义。没有新增接口、SQL、部署操作或财务写入。
- 中文回归覆盖旧告警消失、结构来源缺失及旧断言、意外 READY 不能伪装结构成功、目标/预算/资产 PARTIAL、401/403/网络失败和手动恢复。复用既有 Vue SFC setup/模板编译测试入口。

## 验证与 Delivery 证据

- npm --prefix web run build:shared：通过。
- web 目录 node --test pc-app/tests/dataReadiness.test.mjs：8/8 通过。
- web 目录 node --test pc-app/tests/*.test.mjs：125/125 通过。
- powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1 -MaxFixRounds 0：通过（后端跳过测试打包、shared/PC/H5 构建）；日志 .codex-hooks/logs/latest-build.log。成功仅 stdout/log，hook 未修改。
- git diff --check：通过；最终提交前再核对。
- 测试使用模拟授权 API，不证明真实权限/数据库结构/浏览器体验；真实环境人工验收未运行。
- 本地 Git commit 与本说明供 AiCore 自动链生成正式 Delivery Manifest；当前会话没有 AiCore/Hermes 交付工具，外部 Manifest/企业微信收据为 NOT_VERIFIED，不声称已通知或验收。
- 不推送，不部署，不连接生产数据库，不修改真实财务记录，不编辑 agent-inbox 或派生任务。
