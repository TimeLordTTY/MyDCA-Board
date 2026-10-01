-- Manual deployment only. Observation metadata; no financial writes.
CREATE TABLE risk_watch_event (
 fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 rule_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 owner_user_id BIGINT NOT NULL, owner_family_id BIGINT NULL,
 payload LONGTEXT NOT NULL,
 acknowledged_at TIMESTAMP(6) NULL, resolved_at TIMESTAMP(6) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT chk_risk_watch_event_json CHECK (JSON_VALID(payload)),
 INDEX idx_risk_watch_event_owner(owner_user_id,owner_family_id,rule_id,resolved_at,created_at)
);
CREATE TABLE risk_watch_mute (
 rule_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_user_id BIGINT NOT NULL, owner_family_id BIGINT NULL,
 muted_until TIMESTAMP(6) NULL
);
