-- Manual deployment only. No automatic migration runner registration.
-- User-defined observation metadata only; no financial tables are changed.
CREATE TABLE monthly_budget (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_user_id BIGINT NOT NULL,
 owner_family_id BIGINT NULL,
 payload LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT chk_monthly_budget_json CHECK (JSON_VALID(payload)),
 INDEX idx_monthly_budget_owner(owner_user_id,owner_family_id,created_at)
);
