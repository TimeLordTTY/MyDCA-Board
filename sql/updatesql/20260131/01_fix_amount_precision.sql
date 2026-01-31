-- ============================================
-- 修复 ledger_posting.amount 字段精度问题
-- 问题：2位小数精度导致成本价反算不精确
-- 解决：增加精度到4位小数
-- 执行时间：2026-01-31
-- ============================================

-- 1. 修改 ledger_posting.amount 字段精度为4位小数
ALTER TABLE ledger_posting 
MODIFY COLUMN amount DECIMAL(18, 4) NOT NULL 
COMMENT '金额（永远为正数，方向由posting_type决定，4位小数用于精确计算成本价）';

-- 2. 修复国投瑞银白银期货(LOF)A 的初始持仓金额
-- 计算：38.45 × 2.5980 = 99.8421
UPDATE ledger_posting p
JOIN ledger_txn t ON p.txn_id = t.txn_id
SET p.amount = 99.8421
WHERE t.txn_type = 'ADJUST'
  AND t.product_id = (SELECT id FROM product_master WHERE product_code = '161226' AND channel = 'OTC')
  AND p.account_type IN ('POSITION', 'INCOME');
