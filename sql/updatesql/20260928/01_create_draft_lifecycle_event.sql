-- 草稿生命周期事件只存安全摘要，不存 OCR、通知原文或完整候选 JSON。
-- 本文件仅供部署流程审阅；本任务不执行 migration。
-- 不使用草稿外键：确认尝试/失败由独立事务写入，不能在草稿行锁上等待外键共享锁。
CREATE TABLE draft_lifecycle_event (
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
