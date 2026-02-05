-- ============================================================
-- 修复小荷包账户和持仓数据
-- 问题：支出后账户余额和持仓份额没有正确更新
-- 日期：2026-02-05
-- ============================================================

-- ============================================================
-- 第一步：诊断问题 - 请先执行以下查询确认数据状态
-- ============================================================

-- 1.1 查看小荷包父账户的配置
SELECT 
    id,
    account_name,
    account_type,
    parent_account_id,
    linked_product_id,
    initial_shares,
    balance,
    initial_balance
FROM accounts 
WHERE account_name = '小荷包' AND parent_account_id IS NULL;

-- 1.2 查看小荷包下的子账户
SELECT 
    id,
    account_name,
    account_type,
    parent_account_id,
    linked_product_id,
    initial_shares,
    balance,
    initial_balance
FROM accounts 
WHERE parent_account_id = (SELECT id FROM accounts WHERE account_name = '小荷包' AND parent_account_id IS NULL);

-- 1.3 查看关联的产品信息
SELECT id, product_name, product_code, asset_type, channel 
FROM product_master 
WHERE product_code = '000686' OR product_name LIKE '%建信嘉薪宝%';

-- 1.4 查看相关流水记录
SELECT 
    t.txn_id,
    t.txn_type,
    t.requested_at,
    t.note,
    p.posting_type,
    p.account_type,
    p.amount,
    p.account_balance_after,
    a.account_name
FROM ledger_txn t
JOIN ledger_posting p ON t.txn_id = p.txn_id
JOIN accounts a ON p.account_id = a.id
WHERE a.account_name LIKE '%小荷包%' OR a.account_name LIKE '%待分配%'
ORDER BY t.requested_at DESC
LIMIT 20;

-- ============================================================
-- 第二步：修复数据
-- 注意：执行前请先确认上面的查询结果
-- ============================================================

-- 2.1 计算子账户余额之和
-- 这应该是父账户的正确余额和份额
SELECT 
    parent.id as parent_id,
    parent.account_name as parent_name,
    parent.initial_shares as current_shares,
    COALESCE(SUM(child.balance), 0) as correct_shares
FROM accounts parent
LEFT JOIN accounts child ON child.parent_account_id = parent.id AND child.is_active = 1
WHERE parent.account_name = '小荷包' 
  AND parent.parent_account_id IS NULL
GROUP BY parent.id, parent.account_name, parent.initial_shares;

-- 2.2 修复：根据子账户余额更新父账户的 initial_shares 和 balance
-- 使用 JOIN 方式避免 MySQL 1093 错误
UPDATE accounts parent
INNER JOIN (
    SELECT 
        p.id as parent_id,
        COALESCE(SUM(c.balance), 0) as total_balance
    FROM accounts p
    LEFT JOIN accounts c ON c.parent_account_id = p.id 
        AND c.is_active = 1
        AND (c.account_type IS NULL OR c.account_type NOT IN ('CREDIT_CARD', 'HUABEI', 'BAITIAO', 'LOAN'))
    WHERE p.account_name = '小荷包' 
      AND p.parent_account_id IS NULL
      AND p.linked_product_id IS NOT NULL
    GROUP BY p.id
) calc ON parent.id = calc.parent_id
SET 
    parent.initial_shares = calc.total_balance,
    parent.balance = calc.total_balance,
    parent.updated_at = NOW();

-- ============================================================
-- 第三步：验证修复结果
-- ============================================================

-- 3.1 确认父账户数据已更新
SELECT 
    id,
    account_name,
    initial_shares,
    balance
FROM accounts 
WHERE account_name = '小荷包' AND parent_account_id IS NULL;

-- 3.2 确认持仓页面会显示正确数据
-- 持仓计算逻辑：如果 initial_shares <= 0，则不会显示持仓
SELECT 
    a.id,
    a.account_name,
    a.linked_product_id,
    a.initial_shares,
    p.product_name,
    p.product_code
FROM accounts a
JOIN product_master p ON a.linked_product_id = p.id
WHERE a.initial_shares > 0
  AND a.account_kind = 'REAL';
