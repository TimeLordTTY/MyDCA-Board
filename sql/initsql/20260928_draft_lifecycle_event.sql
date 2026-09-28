-- 仅用于全新环境初始化；既有环境使用 updatesql/20260928 的增量 migration。
-- 不执行本脚本属于本工程任务范围。草稿主表由既有 Phase3 初始化/增量脚本提供。
CREATE TABLE IF NOT EXISTS draft_lifecycle_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    status_before VARCHAR(16) NULL,
    status_after VARCHAR(16) NULL,
    summary VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_draft_lifecycle_event_draft (draft_id, id)
);
