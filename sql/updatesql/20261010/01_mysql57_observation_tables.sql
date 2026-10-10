-- MySQL 5.7 专用增量路径：仅用于这些表尚不存在的生产环境。
-- 与原始 backend/migrations 路径互斥；不转换现有表，不修改财务数据。
-- 5.7 忽略 CHECK，使用原生 JSON 列验证 payload；Mapper 仍按 JSON 文本读写。
-- 执行前必须完整备份并核对九表、两列、两索引缺口。

-- 来源：backend/migrations/20261001_research_plan.sql
-- Manual deployment only; not registered with any automatic migration runner.
-- Research metadata, never orders, settlements or ledger records.
CREATE TABLE research_plan (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    owner_family_id BIGINT NULL,
    payload JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_research_plan_owner (owner_user_id, owner_family_id, created_at)
);

-- 来源：backend/migrations/20261002_risk_watch.sql
-- Manual deployment only. No automatic migration runner registration.
-- Observation metadata only; no financial tables are changed.
CREATE TABLE risk_watch_rule (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_user_id BIGINT NOT NULL, owner_family_id BIGINT NULL,
 payload JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX idx_risk_watch_rule_owner(owner_user_id,owner_family_id,created_at)
);
CREATE TABLE risk_watch_snapshot (
 id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 rule_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 owner_user_id BIGINT NOT NULL, owner_family_id BIGINT NULL,
 payload JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX idx_risk_watch_snapshot_owner(owner_user_id,owner_family_id,rule_id,created_at)
);

-- 来源：backend/migrations/20261002_risk_alert_history.sql
-- Manual deployment only. Observation metadata; no financial writes.
CREATE TABLE risk_watch_event (
 fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 rule_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 owner_user_id BIGINT NOT NULL, owner_family_id BIGINT NULL,
 payload JSON NOT NULL,
 acknowledged_at TIMESTAMP(6) NULL, resolved_at TIMESTAMP(6) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX idx_risk_watch_event_owner(owner_user_id,owner_family_id,rule_id,resolved_at,created_at)
);
CREATE TABLE risk_watch_mute (
 rule_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_user_id BIGINT NOT NULL, owner_family_id BIGINT NULL,
 muted_until TIMESTAMP(6) NULL
);

-- 来源：backend/migrations/20261002_allocation_policy.sql
-- Manual deployment only. No automatic migration runner registration.
-- User-defined observation metadata only; no financial tables are changed.
CREATE TABLE allocation_policy (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_user_id BIGINT NOT NULL,
 owner_family_id BIGINT NULL,
 payload JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX idx_allocation_policy_owner(owner_user_id,owner_family_id,created_at)
);

-- 来源：backend/migrations/20261002_goal_tracking.sql
-- Manual deployment only. No automatic migration runner registration.
-- User-defined observation metadata only; no financial tables are changed.
CREATE TABLE goal_tracking (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_user_id BIGINT NOT NULL,
 owner_family_id BIGINT NULL,
 payload JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX idx_goal_tracking_owner(owner_user_id,owner_family_id,created_at)
);

-- 来源：backend/migrations/20261002_monthly_budget.sql
-- Manual deployment only. No automatic migration runner registration.
-- User-defined observation metadata only; no financial tables are changed.
CREATE TABLE monthly_budget (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_user_id BIGINT NOT NULL,
 owner_family_id BIGINT NULL,
 payload JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 INDEX idx_monthly_budget_owner(owner_user_id,owner_family_id,created_at)
);
