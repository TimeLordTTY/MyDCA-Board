-- Manual deployment only. No automatic migration runner registration.
-- User-defined observation metadata only; no financial tables are changed.
CREATE TABLE goal_tracking (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_user_id BIGINT NOT NULL,
 owner_family_id BIGINT NULL,
 payload LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT chk_goal_tracking_json CHECK (JSON_VALID(payload)),
 INDEX idx_goal_tracking_owner(owner_user_id,owner_family_id,created_at)
);
