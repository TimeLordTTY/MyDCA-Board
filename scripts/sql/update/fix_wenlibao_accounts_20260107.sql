-- ============================================================
-- 修复稳利宝账户份额和余额
-- 日期: 2026-01-07
-- 原因: 删除组合赎回记录时份额恢复逻辑错误导致数据错乱
-- ============================================================

-- 用户提供的正确数据:
-- 稳利宝总份额: 21,387.76
-- 最新净值 (2026-01-05): 1.042668
-- 稳利宝总金额: 22,307.43

-- 备份当前数据（可选，查看用）
-- SELECT account_code, account_name, shares, balance FROM accounts WHERE account_code LIKE 'wenlibao%';

-- 更新各账户份额和余额
UPDATE accounts SET shares = 3836.312230, balance = 4000.00 WHERE account_code = 'wenlibao_rent';
UPDATE accounts SET shares = 3117.003687, balance = 3250.00 WHERE account_code = 'wenlibao_safe';
UPDATE accounts SET shares = 9087.552318, balance = 9475.30 WHERE account_code = 'wenlibao_project';
UPDATE accounts SET shares = 1779.818696, balance = 1855.76 WHERE account_code = 'wenlibao_active';
UPDATE accounts SET shares = 3567.073069, balance = 3726.37 WHERE account_code = 'wenlibao_finance';

-- 验证更新结果
SELECT 
    account_code, 
    account_name, 
    shares, 
    balance,
    ROUND(shares * 1.042668, 2) as calculated_balance
FROM accounts 
WHERE account_code LIKE 'wenlibao%'
ORDER BY account_code;

-- 验证总份额
SELECT 
    SUM(shares) as total_shares,
    SUM(balance) as total_balance
FROM accounts 
WHERE account_code LIKE 'wenlibao%';

