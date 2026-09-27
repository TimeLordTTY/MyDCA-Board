-- ============================================
-- Phase3 草稿强幂等 · 步骤 3/3：为 draft_ledger_entry 增加来源唯一键
-- 目标仓库：TimeLordTTY/MyDCA-Board（分支 v2）
-- 前置：01（只读预检）与 02（空来源归一化为 NULL）均已执行，且预检 1/2 无重复行。
-- 目标：source_ref 非空时，同一“可见归属作用域 + source_type + source_ref”只能存在一条草稿；
--       不同用户 / 不同家庭之间互不冲突；source_ref 为 NULL 时保持旧的非幂等行为。
-- 数据库：MySQL 8.0（项目要求），只用普通复合唯一索引，不使用生成列或函数索引等额外特性。
-- 可重入：MySQL 不支持 CREATE INDEX IF NOT EXISTS；重复执行会报 1061（索引已存在），视为已应用。
-- 回滚：见文件末尾的 DROP INDEX 语句。
-- 数据安全：本脚本只加索引，不删除行、不改写任何草稿内容或状态。
-- ============================================

-- 唯一键 1：同一个人的同一个来源只能有一条草稿。
-- 覆盖个人视角作用域，包含 owner_family_id 为 NULL 的个人草稿。
ALTER TABLE draft_ledger_entry
    ADD UNIQUE KEY uk_draft_ledger_user_source (owner_user_id, source_type, source_ref);

-- 唯一键 2：同一个家庭的同一个来源只能有一条草稿。
-- 覆盖家庭视角作用域；owner_family_id 为 NULL 时 MySQL 唯一索引不参与去重（多行 NULL 不冲突）。
ALTER TABLE draft_ledger_entry
    ADD UNIQUE KEY uk_draft_ledger_family_source (owner_family_id, source_type, source_ref);

-- 为什么这样既精确对应“可见归属作用域”，又不会把不同用户 / 家庭的合法草稿误判为同一条：
-- 1. 草稿可见性定义（backend/src/main/resources/mapper/DraftLedgerEntryMapper.xml 的 VisibleCondition）是
--    owner_user_id = :userId OR owner_family_id = :familyId；
--    因此两行草稿只要“同一个人”或“同一个家庭”可见，就必须被去重：唯一键 1 覆盖“同一个人”，
--    唯一键 2 覆盖“同一个家庭”，二者合起来正好等于可见作用域，不多不少。
-- 2. 不同用户、不同家庭不会命中同一个唯一键：唯一键 1 要求 owner_user_id 相同，唯一键 2 要求
--    owner_family_id 非空且相同；因此不同用户 / 家庭可以合法地复用相同 sourceRef。
-- 3. source_ref IS NULL 的行在 MySQL 唯一索引中互不冲突，空串 / 空白串已由步骤 02 归一化为 NULL，
--    所以“空来源不强制幂等”的旧行为完整保留。
-- 4. 应用层并发恢复（DraftLedgerEntryService.createDraft 捕获 DuplicateKeyException 后按同一作用域重查）
--    依赖这两个唯一键把竞态收敛为“后写一方收到唯一冲突”。MySQL InnoDB 只回滚失败的语句，
--    事务其余部分仍可继续重查，因此恢复路径不需要新事务。

-- 验证：应返回 6 行（两个唯一键各 3 个列，按定义顺序）。
SELECT index_name, seq_in_index, column_name, non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'draft_ledger_entry'
  AND index_name IN ('uk_draft_ledger_user_source', 'uk_draft_ledger_family_source')
ORDER BY index_name, seq_in_index;

-- 回滚（按需执行；回滚后并发重放的极窄重复窗口会重新出现）：
-- ALTER TABLE draft_ledger_entry DROP INDEX uk_draft_ledger_family_source;
-- ALTER TABLE draft_ledger_entry DROP INDEX uk_draft_ledger_user_source;
