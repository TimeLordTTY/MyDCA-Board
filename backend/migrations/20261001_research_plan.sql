-- Manual deployment only; not registered with any automatic migration runner.
-- Research metadata, never orders, settlements or ledger records.
CREATE TABLE research_plan (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    owner_family_id BIGINT NULL,
    payload LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_research_plan_json CHECK (JSON_VALID(payload)),
    INDEX idx_research_plan_owner (owner_user_id, owner_family_id, created_at)
);
