-- 在 ledger_posting 表中增加字段记录该分录发生后的账户余额
-- 这样可以在查询交易列表时直接显示历史余额，而不需要实时计算
-- 注意：历史数据会被删除，所以不需要填充现有数据

SET NAMES utf8mb4;

-- ============================================
-- 1. 添加余额字段
-- ============================================
ALTER TABLE `ledger_posting` 
ADD COLUMN `account_balance_after` DECIMAL(18, 2) NULL COMMENT '该分录发生后的账户余额（用于显示历史余额）' AFTER `amount`,
ADD COLUMN `parent_account_balance_after` DECIMAL(18, 2) NULL COMMENT '该分录发生后的父账户余额（用于显示历史余额）' AFTER `account_balance_after`;

-- ============================================
-- 2. 验证结果
-- ============================================
SELECT 
    '字段添加成功' AS message,
    COUNT(*) AS existing_postings_count
FROM ledger_posting;
