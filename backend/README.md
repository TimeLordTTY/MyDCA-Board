# 财富中枢后端服务

## 当前定位

后端是财富中枢业务真相层，提供 `/api/v2/**` API、账本校验、草稿状态机、账户/持仓/行情等能力。当前项目处于 Phase3，不是 Phase1 初始化阶段。

当前事实源：`../docs/CURRENT_DEVELOPMENT_STATE.md`。

## 技术栈

- Java 17
- Spring Boot 3.2
- MyBatis XML Mapper
- MySQL 8
- JWT / BCrypt
- Maven

## Phase3 关键能力

- `draft_ledger_entry` 草稿状态机。
- `/api/v2/drafts/**`：列表、详情、编辑、preview、ignore、confirm。
- `/api/v2/ai/accounting/parse-text` 与 `draft-from-intent`。
- v0.6 应用层 sourceType + sourceRef 幂等。
- v0.8 数据库强幂等与并发冲突恢复：commit `ea8b3618e25c648127c62750c307ae8af976dd56`。

## v0.8 migration

增量脚本位于 `../sql/updatesql/20260927/`。两个唯一键分别保护 user scope 与 family scope；`source_ref IS NULL` 保持旧的非幂等行为。应用层仅对明确 `DuplicateKeyException` 做重查恢复。

这些脚本已经进入 Git，但**没有因为代码完成而自动部署到任何数据库**。环境迁移必须先跑只读 precheck，并单独确认。

## 草稿与正式账本边界

- create/update/preview/ignore 不直接写正式账本。
- confirm 只在既有规则允许时进入正式账本服务。
- Android、OCR、支付通知候选、Outbox 均不得自动 confirm。
- 不新增绕过既有账本校验链的快捷写库路径。

## 启动与测试

开发启动：`mvn spring-boot:run`  
测试：`mvn -B test`

仓库任务最终还必须运行根目录：
`powershell -ExecutionPolicy Bypass -File scripts/post-task-compile-hook.ps1`

成功 hook 静默，不播放声音或弹窗。

## 配置安全

运行时数据库连接和凭据以环境真实配置为准；README 不作为生产连接串事实源。不得把数据库密码、Token、Cookie 或私密地址写入 Git。
## v0.17 风险观察
规则与历史快照接口：见 [专项说明](../docs/mydca_v017_risk_watch_backend_20261002.md)。仅供观察，不构成交易建议。migration 仅提交，须人工部署。
