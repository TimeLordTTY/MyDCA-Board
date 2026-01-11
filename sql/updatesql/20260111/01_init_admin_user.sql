-- ============================================
-- SQL脚本目的：初始化系统管理员用户
-- 文件编号：01
-- 日期：2024-01
-- 执行顺序：在DDL.sql和DML.sql之后执行（如果使用updatesql方式）
-- ============================================
-- 
-- 功能说明：
-- 1. 创建系统管理员用户（用户名: timelordtty，密码: tty980626）
-- 2. 为该用户创建默认家庭
-- 3. 设置用户为家庭管理员角色（ADMIN）
-- 
-- 注意事项：
-- - 密码使用BCrypt加密存储（密码: tty980626）
-- - 如果用户已存在，执行前请先删除或修改
-- - 执行后可通过查询验证用户和角色是否正确创建
-- - BCrypt哈希值需要先通过工具生成（可使用后端项目的BCryptGenerator工具类）
-- 
-- 说明：此脚本内容已合并到 initsql/DML.sql 中
-- 如果是从全新数据库初始化，请使用 initsql/DML.sql
-- 如果是在现有数据库中补充管理员用户，可使用此脚本
-- ============================================

SET NAMES utf8mb4;

-- 1. 插入用户
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

-- 2. 创建默认家庭（以用户ID为管理员）
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

-- 3. 更新用户的家庭ID
SET @family_id = LAST_INSERT_ID();
UPDATE `users` SET `family_id` = @family_id WHERE `id` = @user_id;

-- 4. 添加用户家庭角色（管理员）
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

-- 查询验证
SELECT 
    u.id,
    u.username,
    u.nickname,
    u.family_id,
    f.family_name,
    ufr.role
FROM users u
LEFT JOIN families f ON u.family_id = f.id
LEFT JOIN user_family_roles ufr ON u.id = ufr.user_id AND f.id = ufr.family_id
WHERE u.username = 'timelordtty';
