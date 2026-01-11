-- ============================================
-- SQL脚本目的：初始化数据（DML）
-- 文件编号：DML
-- 执行顺序：在DDL.sql之后执行
-- ============================================
-- 
-- 功能说明：
-- 1. 创建系统必需的虚拟账户（用于分录归集）
-- 2. 创建系统管理员用户和默认家庭
-- 
-- 注意事项：
-- - 执行前请确保已执行DDL.sql创建所有表结构
-- - 虚拟账户是系统必需的，建议必须执行
-- - 管理员用户可根据需要选择是否创建
-- ============================================

SET NAMES utf8mb4;

-- ============================================
-- 1. 创建虚拟账户（用于分录归集）
-- ============================================
-- 说明：这些虚拟账户是系统必需的，用于复式记账的分录归集
-- 如果账户已存在，使用ON DUPLICATE KEY UPDATE更新账户名称

INSERT INTO accounts (
    account_code, 
    account_name, 
    account_kind, 
    account_type, 
    virtual_subtype, 
    currency, 
    balance, 
    reserved_amount, 
    initial_balance, 
    owner_type, 
    owner_user_id
) VALUES 
  ('VIRTUAL-POSITION', '持仓账户', 'VIRTUAL', 'OTHER', 'POSITION', 'CNY', 0, 0, 0, 'PERSONAL', NULL),
  ('VIRTUAL-FEE', '费用账户', 'VIRTUAL', 'OTHER', 'FEE', 'CNY', 0, 0, 0, 'PERSONAL', NULL),
  ('VIRTUAL-INCOME', '收入账户', 'VIRTUAL', 'OTHER', 'INCOME', 'CNY', 0, 0, 0, 'PERSONAL', NULL),
  ('VIRTUAL-EXPENSE', '支出账户', 'VIRTUAL', 'OTHER', 'EXPENSE', 'CNY', 0, 0, 0, 'PERSONAL', NULL),
  ('VIRTUAL-RECEIVABLE', '应收账户', 'VIRTUAL', 'OTHER', 'RECEIVABLE', 'CNY', 0, 0, 0, 'PERSONAL', NULL),
  ('VIRTUAL-LIABILITY', '负债账户', 'VIRTUAL', 'OTHER', 'LIABILITY', 'CNY', 0, 0, 0, 'PERSONAL', NULL)
ON DUPLICATE KEY UPDATE account_name = VALUES(account_name);

-- ============================================
-- 2. 创建系统管理员用户和默认家庭
-- ============================================
-- 说明：创建默认管理员用户（用户名: timelordtty，密码: tty980626）
-- 如果用户已存在，此部分会失败，可手动删除或修改后再执行

-- 2.1 插入用户
INSERT INTO `users` (
    `username`, 
    `password_hash`, 
    `nickname`, 
    `email`, 
    `phone`, 
    `family_id`, 
    `is_active`, 
    `created_at`, 
    `updated_at`
) VALUES (
    'timelordtty',
    '$2a$10$8iFj9aEzVPPW1TW1ZolBFOLotudZJAebaB2myD1bE72JTKguRJUHy', -- 密码: tty980626 (BCrypt哈希)
    '管理员',
    NULL,
    NULL,
    NULL, -- 先不关联家庭，后续创建家庭后再更新
    1,
    NOW(),
    NOW()
);

-- 2.2 创建默认家庭（以用户ID为管理员）
SET @user_id = LAST_INSERT_ID();

INSERT INTO `families` (
    `family_code`,
    `family_name`,
    `admin_user_id`,
    `is_active`,
    `created_at`,
    `updated_at`
) VALUES (
    CONCAT('FAM-', DATE_FORMAT(NOW(), '%Y%m%d'), '-', LPAD(@user_id, 3, '0')),
    '默认家庭',
    @user_id,
    1,
    NOW(),
    NOW()
);

-- 2.3 更新用户的家庭ID
SET @family_id = LAST_INSERT_ID();
UPDATE `users` SET `family_id` = @family_id WHERE `id` = @user_id;

-- 2.4 添加用户家庭角色（管理员）
INSERT INTO `user_family_roles` (
    `user_id`,
    `family_id`,
    `role`,
    `created_at`,
    `updated_at`
) VALUES (
    @user_id,
    @family_id,
    'ADMIN',
    NOW(),
    NOW()
);

-- ============================================
-- 3. 验证查询（可选）
-- ============================================
-- 执行以下查询可验证数据是否正确创建

-- 验证虚拟账户
-- SELECT account_code, account_name, account_kind, virtual_subtype 
-- FROM accounts 
-- WHERE account_kind = 'VIRTUAL' 
-- ORDER BY account_code;

-- 验证管理员用户和家庭
-- SELECT 
--     u.id,
--     u.username,
--     u.nickname,
--     u.family_id,
--     f.family_name,
--     ufr.role
-- FROM users u
-- LEFT JOIN families f ON u.family_id = f.id
-- LEFT JOIN user_family_roles ufr ON u.id = ufr.user_id AND f.id = ufr.family_id
-- WHERE u.username = 'timelordtty';

-- ============================================
-- DML脚本完成
-- ============================================
