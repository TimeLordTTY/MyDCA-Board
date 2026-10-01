-- Manual deployment only. No automatic migration runner registration.
-- Observation metadata only; no financial tables are changed.
CREATE TABLE risk_watch_rule (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_user_id BIGINT NOT NULL, owner_family_id BIGINT NULL,
 payload LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT chk_risk_watch_rule_json CHECK (JSON_VALID(payload)),
 INDEX idx_risk_watch_rule_owner(owner_user_id,owner_family_id,created_at)
);
CREATE TABLE risk_watch_snapshot (
 id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 rule_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 owner_user_id BIGINT NOT NULL, owner_family_id BIGINT NULL,
 payload LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT chk_risk_watch_snapshot_json CHECK (JSON_VALID(payload)),
 INDEX idx_risk_watch_snapshot_owner(owner_user_id,owner_family_id,rule_id,created_at)
);
