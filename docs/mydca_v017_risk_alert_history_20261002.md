# v0.17 风险提醒历史与去重

提醒仅供观察，不构成交易建议。仅写观察元数据，不创建订单、草稿、结算或账本动作，不接 AiCore/Hermes 外部通知。

## API 与状态

在 `/api/v2/risk-watch-rules/{id}` 下：
- `GET /events?page=0&size=20`：分页历史；`unresolved=true` 只返回未解除事件。
- `PATCH /events/{fingerprint}/acknowledge`：手工已读，重复操作保留首次时间。
- `PATCH /mute`，请求 `{"mutedUntil":"2026-10-03T00:00:00Z"}`：规则级静默窗口；null 清除窗口。既有 config.muted 仍表示持续静默。

事件包含 fingerprint、完整评估 evidence（中文命中原因、数据时间、阈值、观察值、免责声明）、state、visible、acknowledgedAt、resolvedAt、mutedUntil。
状态优先级 RESOLVED > MUTED > ACKNOWLEDGED > OPEN。仅 OPEN 的 visible 为 true；静默到期重新展示尚未已读事件，已读时间保留。静默和已读不修改命中证据。

fingerprint 使用 SHA-256(ruleId + sourceDataHash + severity)，数据库主键保证重复评估不增加事件；sourceDataHash 排序输入，不含展示用静默设置。相同证据曾解除后再次评估不会重开或重复建事件，新来源证据另建事件。
显式 evaluate 中已知且不命中将该规则未解除事件标为 RESOLVED，历史不删除；UNKNOWN 不解除。不会后台自动评估或发送通知。

## 隔离与部署

所有查询和更新同时限制当前 user/family；家庭规则仍要求管理员。评估、规则编辑、状态更新在事务中锁规则行，串行处理同规则变更。
新增 `backend/migrations/20261002_risk_alert_history.sql`，初始化副本位于 backend/sql/initsql。依赖先前风险观察表。仅供部署方人工审核执行；本任务未连接数据库，未部署迁移。测试使用 mock Mapper，不代表真实数据库上线。

## 验证

后端测试覆盖稳定去重、命中证据保留、OPEN/已读/静默到期/RESOLVED、UNKNOWN 不解除、owner scope 和只读财务依赖；执行全部 Maven 测试、post-task compile hook 和 git diff --check。仅本地提交，不推送。
