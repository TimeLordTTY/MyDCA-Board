-- ============================================
-- Phase3 草稿强幂等 · 步骤 1/3：重复数据预检（只读，不修改任何数据）
-- 目标仓库：TimeLordTTY/MyDCA-Board（分支 v2）
-- 背景：draft_ledger_entry 此前只有应用层“先查后插”幂等，没有数据库唯一约束，
--       并发重放同一次采集时存在极窄的重复插入窗口。
-- 本组脚本把“同一可见归属作用域 + source_type + source_ref 只能有一条草稿”下推到数据库。
-- 执行顺序：01（本脚本，只读预检）→ 02_normalize_blank_draft_ledger_source_ref.sql
--           → 03_add_draft_ledger_source_unique_keys.sql
-- 数据安全：本组脚本不含任何 DELETE，不删除历史重复数据，不修改金额、状态或正式账本相关数据。
-- ============================================

-- 预检 1：同一个人 + 同来源的重复草稿（唯一键 uk_draft_ledger_user_source 的覆盖范围）。
-- 返回任何行都会让步骤 03 的 ADD UNIQUE KEY 以 1062 失败，必须先人工处理。
SELECT
    owner_user_id,
    source_type,
    source_ref,
    COUNT(*)      AS duplicate_rows,
    MIN(id)       AS keep_candidate_min_id,
    GROUP_CONCAT(id ORDER BY id) AS duplicate_ids
FROM draft_ledger_entry
WHERE source_ref IS NOT NULL
GROUP BY owner_user_id, source_type, source_ref
HAVING COUNT(*) > 1
ORDER BY duplicate_rows DESC, owner_user_id, source_type, source_ref;

-- 预检 2：同一个家庭 + 同来源的重复草稿（唯一键 uk_draft_ledger_family_source 的覆盖范围）。
-- owner_family_id 为 NULL 的行不参与家庭级唯一性，因此这里显式排除。
SELECT
    owner_family_id,
    source_type,
    source_ref,
    COUNT(*)      AS duplicate_rows,
    MIN(id)       AS keep_candidate_min_id,
    GROUP_CONCAT(id ORDER BY id) AS duplicate_ids
FROM draft_ledger_entry
WHERE source_ref IS NOT NULL
  AND owner_family_id IS NOT NULL
GROUP BY owner_family_id, source_type, source_ref
HAVING COUNT(*) > 1
ORDER BY duplicate_rows DESC, owner_family_id, source_type, source_ref;

-- 预检 3：source_ref 为空串或纯空白的草稿行。
-- 这些行会被步骤 02 归一化为 NULL；在此之前先列出明细，避免操作者不知道有哪些行被改写。
SELECT
    id,
    owner_user_id,
    owner_family_id,
    source_type,
    CONCAT('[', source_ref, ']') AS raw_source_ref,
    status,
    created_at
FROM draft_ledger_entry
WHERE source_ref IS NOT NULL
  AND TRIM(source_ref) = ''
ORDER BY id;

-- 预检 4：总体统计。duplicate_user_scope_rows 与 duplicate_family_scope_rows 都为 0 时才能执行步骤 03。
SELECT
    (SELECT COUNT(*) FROM draft_ledger_entry)                                          AS total_rows,
    (SELECT COUNT(*) FROM draft_ledger_entry WHERE source_ref IS NOT NULL)              AS rows_with_source_ref,
    (SELECT COUNT(*) FROM draft_ledger_entry
      WHERE source_ref IS NOT NULL AND TRIM(source_ref) = '')                           AS rows_with_blank_source_ref,
    (SELECT COUNT(*) FROM (
        SELECT 1 FROM draft_ledger_entry
        WHERE source_ref IS NOT NULL
        GROUP BY owner_user_id, source_type, source_ref
        HAVING COUNT(*) > 1) AS user_scope_duplicates)                                  AS duplicate_user_scope_rows,
    (SELECT COUNT(*) FROM (
        SELECT 1 FROM draft_ledger_entry
        WHERE source_ref IS NOT NULL AND owner_family_id IS NOT NULL
        GROUP BY owner_family_id, source_type, source_ref
        HAVING COUNT(*) > 1) AS family_scope_duplicates)                                AS duplicate_family_scope_rows;

-- 阻断策略（明确不静默处理）：
-- 1. 预检 1 / 2 返回任何行时，步骤 03 会以 Duplicate entry（错误码 1062）失败，不会删除或改写任何草稿；
--    请先在测试环境人工决定保留哪一条（keep_candidate_min_id 只是候选，不是自动决策），
--    再由人工处理其余重复草稿（例如标记 IGNORED 并写明原因），本迁移不代做合并。
-- 2. 预检 3 返回行是正常可收敛情况：步骤 02 只把空串/空白串写成 NULL，不删除行、不改状态。
-- 3. 本组脚本不连接生产数据库、不执行交易、不写入正式 ledger / order / settlement / holding。
