-- 重新创建虚拟账户
-- 说明：删除所有现有的虚拟账户，然后为每个用户重新创建正确的虚拟账户
-- 执行前请确保已备份数据

SET NAMES utf8mb4;

-- ============================================
-- 1. 删除所有现有的虚拟账户
-- ============================================
-- 注意：删除前请确保没有关联的 ledger_posting 记录，或者先处理关联数据

-- 先删除关联的分录（可选，如果需要保留历史数据，可以跳过这一步）
-- DELETE FROM ledger_posting WHERE account_id IN (SELECT id FROM accounts WHERE account_kind = 'VIRTUAL');

-- 删除所有虚拟账户
DELETE FROM accounts WHERE account_kind = 'VIRTUAL';

-- ============================================
-- 2. 为每个用户创建虚拟账户
-- ============================================
-- 为每个用户创建 INCOME、EXPENSE、FEE、RECEIVABLE、LIABILITY 五个虚拟账户
-- POSITION 账户会在买入/申购时自动创建

-- 2.1 为个人用户创建虚拟账户
INSERT INTO accounts (
    account_code,
    account_name,
    account_kind,
    account_type,
    virtual_subtype,
    owner_type,
    owner_user_id,
    owner_family_id,
    currency,
    fund_usage,
    balance,
    reserved_amount,
    initial_balance,
    is_active,
    note,
    created_at,
    updated_at
)
SELECT 
    CONCAT('VIRTUAL-', vt.virtual_subtype, '-USER-', u.id) AS account_code,
    CASE vt.virtual_subtype
        WHEN 'INCOME' THEN '收入账户'
        WHEN 'EXPENSE' THEN '费用账户'
        WHEN 'FEE' THEN '手续费账户'
        WHEN 'RECEIVABLE' THEN '应收账户'
        WHEN 'LIABILITY' THEN '负债账户'
        ELSE CONCAT(vt.virtual_subtype, '账户')
    END AS account_name,
    'VIRTUAL' AS account_kind,
    'OTHER' AS account_type,
    vt.virtual_subtype,
    'PERSONAL' AS owner_type,
    u.id AS owner_user_id,
    u.family_id AS owner_family_id,  -- 如果用户有家庭，也设置 familyId
    'CNY' AS currency,
    NULL AS fund_usage,  -- 虚拟账户不应该有 fund_usage
    0.00 AS balance,
    0.00 AS reserved_amount,
    0.00 AS initial_balance,
    1 AS is_active,
    '系统自动创建的虚拟账户' AS note,
    NOW() AS created_at,
    NOW() AS updated_at
FROM users u
CROSS JOIN (
    SELECT 'INCOME' AS virtual_subtype
    UNION SELECT 'EXPENSE'
    UNION SELECT 'FEE'
    UNION SELECT 'RECEIVABLE'
    UNION SELECT 'LIABILITY'
) AS vt
WHERE u.id IS NOT NULL;

-- 2.2 为家庭创建虚拟账户
INSERT INTO accounts (
    account_code,
    account_name,
    account_kind,
    account_type,
    virtual_subtype,
    owner_type,
    owner_user_id,
    owner_family_id,
    currency,
    fund_usage,
    balance,
    reserved_amount,
    initial_balance,
    is_active,
    note,
    created_at,
    updated_at
)
SELECT 
    CONCAT('VIRTUAL-', vt.virtual_subtype, '-FAMILY-', f.id) AS account_code,
    CASE vt.virtual_subtype
        WHEN 'INCOME' THEN '收入账户'
        WHEN 'EXPENSE' THEN '费用账户'
        WHEN 'FEE' THEN '手续费账户'
        WHEN 'RECEIVABLE' THEN '应收账户'
        WHEN 'LIABILITY' THEN '负债账户'
        ELSE CONCAT(vt.virtual_subtype, '账户')
    END AS account_name,
    'VIRTUAL' AS account_kind,
    'OTHER' AS account_type,
    vt.virtual_subtype,
    'FAMILY' AS owner_type,
    NULL AS owner_user_id,
    f.id AS owner_family_id,
    'CNY' AS currency,
    NULL AS fund_usage,  -- 虚拟账户不应该有 fund_usage
    0.00 AS balance,
    0.00 AS reserved_amount,
    0.00 AS initial_balance,
    1 AS is_active,
    '系统自动创建的虚拟账户' AS note,
    NOW() AS created_at,
    NOW() AS updated_at
FROM families f
CROSS JOIN (
    SELECT 'INCOME' AS virtual_subtype
    UNION SELECT 'EXPENSE'
    UNION SELECT 'FEE'
    UNION SELECT 'RECEIVABLE'
    UNION SELECT 'LIABILITY'
) AS vt
WHERE f.id IS NOT NULL;

-- ============================================
-- 3. 验证结果
-- ============================================
SELECT 
    id,
    account_code,
    account_name,
    account_kind,
    account_type,
    virtual_subtype,
    owner_type,
    owner_user_id,
    owner_family_id,
    fund_usage,
    balance,
    note
FROM accounts
WHERE account_kind = 'VIRTUAL'
ORDER BY owner_type, owner_user_id, owner_family_id, virtual_subtype;

-- ============================================
-- 4. 统计信息
-- ============================================
SELECT 
    owner_type,
    COUNT(*) AS account_count,
    GROUP_CONCAT(DISTINCT virtual_subtype ORDER BY virtual_subtype) AS virtual_types
FROM accounts
WHERE account_kind = 'VIRTUAL'
GROUP BY owner_type, owner_user_id, owner_family_id
ORDER BY owner_type, owner_user_id, owner_family_id;
