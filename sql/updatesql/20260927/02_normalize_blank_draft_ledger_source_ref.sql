-- ============================================
-- Phase3 草稿强幂等 · 步骤 2/3：把“空来源”草稿的 source_ref 归一化为 NULL
-- 目标仓库：TimeLordTTY/MyDCA-Board（分支 v2）
-- 前置：先执行 01_precheck_draft_ledger_source_duplicates.sql，并按预检 1/2 人工处理重复草稿。
-- 原因：应用层规则是“sourceRef 为空时保持旧的非幂等行为”，而 MySQL 唯一索引只把 NULL 视为“不参与唯一性”。
--       若保留空串或纯空白串，步骤 03 的唯一键会把它们当成有效来源参与去重，与上述规则冲突。
-- 安全性：本脚本不删除任何行，不修改 status、金额、确认 / 忽略追踪字段，只把无意义的空串改写为 NULL。
-- 回退：空串与 NULL 都表示“没有来源标识”，本步骤不丢业务信息；无需回退（若强行改回空串会重新与唯一键冲突）。
-- ============================================

-- 执行前核对：下面这条查询的结果应等于步骤 01 预检 3 的明细行数。
SELECT COUNT(*) AS blank_source_ref_rows_before
FROM draft_ledger_entry
WHERE source_ref IS NOT NULL
  AND TRIM(source_ref) = '';

UPDATE draft_ledger_entry
SET source_ref = NULL,
    updated_at = NOW()
WHERE source_ref IS NOT NULL
  AND TRIM(source_ref) = '';

-- 执行后核对：应为 0。
SELECT COUNT(*) AS blank_source_ref_rows_after
FROM draft_ledger_entry
WHERE source_ref IS NOT NULL
  AND TRIM(source_ref) = '';
