# v0.14 草稿生命周期审计与安全恢复

草稿变更新增 `draft_lifecycle_event` append-only 事件：创建、编辑、预览、忽略、恢复、确认尝试、确认成功、确认失败及复制。事件保存时间、操作者、来源类型、状态前后与固定安全摘要，不复制 OCR 或通知原文。历史接口 `GET /api/v2/drafts/{id}/history` 先验证当前用户或家庭可见性。Android 和 PC 草稿详情可查看历史、加载失败和空态。

`POST /api/v2/drafts/{id}/reopen` 仅允许未关联正式结果的 `IGNORED` 草稿；行锁串行化恢复与确认，恢复时重新验证 owner scope 和 `sourceType + sourceRef`，清除旧预览。重复恢复返回当前 `DRAFT`，不写第二个恢复事件。`CONFIRMED` 无恢复入口且后端拒绝恢复。`POST /api/v2/drafts/{id}/copy` 仅允许显式复制已确认草稿，新草稿使用新的 ID 和随机 `sourceRef`，状态为 `DRAFT`，必须再次预览和二次确认。

上线前的旧草稿没有可凭空重建的历史事件；其历史视图为空，原有创建、确认、忽略时间仍保留在草稿详情中。

确认尝试与失败事件通过独立事务保留；成功事件与正式结果在同一业务事务提交。既有环境 migration 位于 `sql/updatesql/20260928/01_create_draft_lifecycle_event.sql`，全新环境初始化配套位于 `sql/initsql/20260928_draft_lifecycle_event.sql`，两者互斥。本工程任务没有执行脚本，也没有连接生产数据库。部署前必须由授权流程评审并执行对应脚本；脚本未部署前，新事件写入不可用。
