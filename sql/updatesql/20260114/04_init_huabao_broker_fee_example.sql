-- ============================================
-- SQL脚本目的：初始化华宝证券费率配置示例
-- 文件编号：04
-- 日期：2026-01-14
-- 执行顺序：在03_add_broker_fee_config_table.sql之后执行
-- ============================================
-- 
-- 功能说明：
-- 1. 为华宝证券账户配置费率规则示例
-- 2. 费率规则：
--    - A股：万1.154，2元起收
--    - ETF/LOF：万0.854，0.2元起收
--    - LOF基金场内申购：一折（0.1）
--    - 上海可转债：万0.44，0.5起收
--    - 深圳可转债：万0.8，0.5起收
-- 
-- 注意事项：
-- - 执行前请确保broker_fee_config表已创建
-- - 请根据实际的券商账户ID修改account_id
-- - 费率计算逻辑：fee = max(amount × fee_rate, min_fee)
-- ============================================

SET NAMES utf8mb4;

-- ============================================
-- 示例：为华宝证券账户配置费率
-- ============================================
-- 注意：请根据实际的券商账户ID修改account_id
-- 查询券商账户：SELECT id, account_name, account_type FROM accounts WHERE account_type = 'BROKER';

-- 查找华宝证券账户（根据账户名称匹配，如果不存在则使用第一个券商账户）
SET @broker_account_id = (
    SELECT id FROM accounts 
    WHERE account_type = 'BROKER' 
    AND (account_name LIKE '%华宝%' OR account_name LIKE '%Huabao%' OR account_name LIKE '%HB%')
    LIMIT 1
);

-- 如果没找到匹配的账户，使用第一个券商账户
SET @broker_account_id = COALESCE(@broker_account_id, (
    SELECT id FROM accounts 
    WHERE account_type = 'BROKER' 
    LIMIT 1
));

-- 如果还是没有找到，报错提示
SELECT 
    CASE 
        WHEN @broker_account_id IS NULL THEN 
            CONCAT('错误：未找到券商账户。请先创建券商账户（account_type=BROKER），然后手动修改此脚本中的 @broker_account_id 变量。')
        ELSE 
            CONCAT('找到券商账户ID: ', @broker_account_id, '，账户名称: ', 
                   (SELECT account_name FROM accounts WHERE id = @broker_account_id))
    END AS message;

-- 1. A股费率：万1.154，2元起收
INSERT INTO `broker_fee_config` (
    `account_id`, `fee_rule_type`, `buy_fee_rate`, `sell_fee_rate`, 
    `buy_min_fee`, `sell_min_fee`, `is_active`, `note`
) VALUES (
    @broker_account_id, 'STOCK', 0.0001154, 0.0001154, 
    2.00, 2.00, 1, 'A股：万1.154，2元起收'
) ON DUPLICATE KEY UPDATE
    `buy_fee_rate` = 0.0001154,
    `sell_fee_rate` = 0.0001154,
    `buy_min_fee` = 2.00,
    `sell_min_fee` = 2.00,
    `note` = 'A股：万1.154，2元起收';

-- 2. ETF费率：万0.854，0.2元起收
INSERT INTO `broker_fee_config` (
    `account_id`, `fee_rule_type`, `buy_fee_rate`, `sell_fee_rate`, 
    `buy_min_fee`, `sell_min_fee`, `is_active`, `note`
) VALUES (
    @broker_account_id, 'ETF', 0.0000854, 0.0000854, 
    0.20, 0.20, 1, 'ETF：万0.854，0.2元起收'
) ON DUPLICATE KEY UPDATE
    `buy_fee_rate` = 0.0000854,
    `sell_fee_rate` = 0.0000854,
    `buy_min_fee` = 0.20,
    `sell_min_fee` = 0.20,
    `note` = 'ETF：万0.854，0.2元起收';

-- 3. LOF场内交易费率：万0.854，0.2元起收
INSERT INTO `broker_fee_config` (
    `account_id`, `fee_rule_type`, `buy_fee_rate`, `sell_fee_rate`, 
    `buy_min_fee`, `sell_min_fee`, `is_active`, `note`
) VALUES (
    @broker_account_id, 'LOF', 0.0000854, 0.0000854, 
    0.20, 0.20, 1, 'LOF场内交易：万0.854，0.2元起收'
) ON DUPLICATE KEY UPDATE
    `buy_fee_rate` = 0.0000854,
    `sell_fee_rate` = 0.0000854,
    `buy_min_fee` = 0.20,
    `sell_min_fee` = 0.20,
    `note` = 'LOF场内交易：万0.854，0.2元起收';

-- 4. LOF场内申购费率：一折（0.1），0.2元起收
-- 注意：LOF场内申购需要结合产品表的申购费率，然后乘以折扣率
INSERT INTO `broker_fee_config` (
    `account_id`, `fee_rule_type`, `buy_fee_rate`, `sell_fee_rate`, 
    `buy_min_fee`, `sell_min_fee`, `subscription_discount_rate`, `is_active`, `note`
) VALUES (
    @broker_account_id, 'LOF_SUBSCRIPTION', 0.000000, 0.000000, 
    0.20, 0.00, 0.1, 1, 'LOF场内申购：一折，0.2元起收'
) ON DUPLICATE KEY UPDATE
    `buy_min_fee` = 0.20,
    `subscription_discount_rate` = 0.1,
    `note` = 'LOF场内申购：一折，0.2元起收';

-- 5. 上海可转债费率：万0.44，0.5元起收
INSERT INTO `broker_fee_config` (
    `account_id`, `fee_rule_type`, `buy_fee_rate`, `sell_fee_rate`, 
    `buy_min_fee`, `sell_min_fee`, `is_active`, `note`
) VALUES (
    @broker_account_id, 'CONVERTIBLE_BOND_SH', 0.000044, 0.000044, 
    0.50, 0.50, 1, '上海可转债：万0.44，0.5元起收'
) ON DUPLICATE KEY UPDATE
    `buy_fee_rate` = 0.000044,
    `sell_fee_rate` = 0.000044,
    `buy_min_fee` = 0.50,
    `sell_min_fee` = 0.50,
    `note` = '上海可转债：万0.44，0.5元起收';

-- 6. 深圳可转债费率：万0.8，0.5元起收
INSERT INTO `broker_fee_config` (
    `account_id`, `fee_rule_type`, `buy_fee_rate`, `sell_fee_rate`, 
    `buy_min_fee`, `sell_min_fee`, `is_active`, `note`
) VALUES (
    @broker_account_id, 'CONVERTIBLE_BOND_SZ', 0.000080, 0.000080, 
    0.50, 0.50, 1, '深圳可转债：万0.8，0.5元起收'
) ON DUPLICATE KEY UPDATE
    `buy_fee_rate` = 0.000080,
    `sell_fee_rate` = 0.000080,
    `buy_min_fee` = 0.50,
    `sell_min_fee` = 0.50,
    `note` = '深圳可转债：万0.8，0.5元起收';

-- ============================================
-- 验证查询
-- ============================================
SELECT 
    bfc.id,
    a.account_name AS broker_account,
    bfc.fee_rule_type,
    bfc.buy_fee_rate,
    bfc.sell_fee_rate,
    bfc.buy_min_fee,
    bfc.sell_min_fee,
    bfc.subscription_discount_rate,
    bfc.note
FROM broker_fee_config bfc
JOIN accounts a ON bfc.account_id = a.id
WHERE bfc.account_id = @broker_account_id
ORDER BY bfc.fee_rule_type;

-- ============================================
-- 脚本完成
-- ============================================
