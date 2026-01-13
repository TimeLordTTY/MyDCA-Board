-- ============================================
-- SQL脚本目的：初始化数据（DML）
-- 文件编号：DML
-- 执行顺序：在DDL.sql之后执行
-- ============================================
-- 
-- 功能说明：
-- 1. 创建系统必需的虚拟账户（用于分录归集）
-- 2. 创建系统管理员用户和默认家庭
-- 3. 初始化产品主数据（product_master）
-- 
-- 注意事项：
-- - 执行前请确保已执行DDL.sql创建所有表结构
-- - 虚拟账户是系统必需的，建议必须执行
-- - 管理员用户可根据需要选择是否创建
-- - 产品数据可根据实际需要调整
-- ============================================

SET NAMES utf8mb4;

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
-- 3. 初始化产品主数据（product_master）
-- ============================================
-- 说明：参考V1版本的产品数据，转换为v2版本的表结构
-- 字段映射：
--   V1.products.code -> product_master.product_code
--   V1.products.channel -> product_master.channel
--   V1.products.market -> product_master.market
--   V1.products.asset_type -> product_master.asset_type
--   V1.products.currency -> product_master.currency
--   V1.products.product_name -> product_master.product_name
--   V1.products.is_qdii -> product_master.is_qdii
--   V1.products.track_index -> product_master.track_index
--   V1.products.buy_fee_rate -> product_master.buy_fee_rate
--   V1.products.sell_fee_rate -> product_master.sell_fee_rate
--   V1.products.buy_confirm_offset -> product_master.buy_confirm_offset
--   V1.products.sell_confirm_offset -> product_master.sell_confirm_offset
--   V1.products.cutoff_time -> product_master.cutoff_time
--   V1.products.source -> product_master.data_source
--   V1.products.is_active -> product_master.is_active

INSERT INTO `product_master` (
    `product_code`,
    `channel`,
    `market`,
    `asset_type`,
    `currency`,
    `product_name`,
    `is_qdii`,
    `track_index`,
    `buy_fee_rate`,
    `sell_fee_rate`,
    `buy_confirm_offset`,
    `sell_confirm_offset`,
    `cutoff_time`,
    `data_source`,
    `is_active`,
    `created_at`,
    `updated_at`
) VALUES
-- 场外基金（OTC）
('000307', 'OTC', 'NA', 'FUND', 'CNY', '易方达黄金ETF联接A', 0, NULL, 0.000700, 0.002000, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('020602', 'OTC', 'NA', 'FUND', 'CNY', '易方达红利低波ETF联接A', 0, NULL, 0.001200, 0.015000, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('110020', 'OTC', 'NA', 'FUND', 'CNY', '易方达沪深300ETF联接A', 0, NULL, 0.001200, 0.005000, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('012080', 'OTC', 'NA', 'FUND', 'CNY', '易方达中证500指数量化增强A', 0, NULL, 0.001500, 0.007500, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('013308', 'OTC', 'NA', 'FUND', 'CNY', '易方达恒生科技ETF联接(QDII)A', 0, NULL, 0.000600, 0.005000, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('019767', 'OTC', 'NA', 'FUND', 'CNY', '景顺长城科创50指数增强A', 0, NULL, 0.001200, 0.007500, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('161125', 'OTC', 'NA', 'FUND', 'CNY', '易方达标普500指数(QDII-LOF)A', 1, NULL, 0.001200, 0.005000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('161130', 'OTC', 'NA', 'FUND', 'CNY', '易方达纳斯达克100ETF联接(QDII-LOF)A', 1, NULL, 0.001200, 0.005000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('019172', 'OTC', 'NA', 'FUND', 'CNY', '摩根纳斯达克100指数(QDII)A', 1, NULL, 0.001200, 0.015000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('017641', 'OTC', 'NA', 'FUND', 'CNY', '摩根标普500指数(QDII)A', 1, NULL, 0.001200, 0.005000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('016452', 'OTC', 'NA', 'FUND', 'CNY', '南方纳斯达克100指数(QDII)A', 1, NULL, 0.001200, 0.015000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('018043', 'OTC', 'NA', 'FUND', 'CNY', '天弘纳斯达克100指数(QDII)A', 1, NULL, 0.001000, 0.005000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('015299', 'OTC', 'NA', 'FUND', 'CNY', '华夏纳斯达克100ETF联接(QDII)A', 1, NULL, 0.001200, 0.015000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('163406', 'OTC', 'NA', 'FUND', 'CNY', '兴全合润混合A', 0, NULL, 0.001200, 0.000000, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('000686', 'OTC', 'NA', 'MMF', 'CNY', '建信嘉薪宝货币市场基金A类', 0, NULL, 0.000000, 0.000000, 0, 0, '15:00', 'fund', 1, NOW(), NOW()),
-- 场内ETF/LOF（EXCHANGE）
('513650', 'EXCHANGE', 'SH', 'ETF', 'CNY', '标普500ETF南方', 1, '标普500净总收益指数', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('163406', 'EXCHANGE', 'SZ', 'LOF', 'CNY', '兴全合润混合A(LOF)', 0, NULL, 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('563020', 'EXCHANGE', 'SZ', 'ETF', 'CNY', '红利低波动ETF', 0, '中证红利低波动指数', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('518850', 'EXCHANGE', 'SH', 'ETF', 'CNY', '黄金ETF华夏', 0, 'AU9999', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('513010', 'EXCHANGE', 'SH', 'ETF', 'CNY', '恒生科技ETF易方达', 1, '恒生科技指数', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('159659', 'EXCHANGE', 'SZ', 'ETF', 'CNY', '纳斯达克100ETF', 1, '纳斯达克100指数', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('161125', 'EXCHANGE', 'SZ', 'LOF', 'CNY', '标普500LOF', 1, '标普500', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('161130', 'EXCHANGE', 'SZ', 'LOF', 'CNY', '纳斯达克100LOF', 1, '纳斯达克100LOF', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('159206', 'EXCHANGE', 'SZ', 'ETF', 'CNY', '卫星ETF', 0, '卫星通信', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('562500', 'EXCHANGE', 'SH', 'ETF', 'CNY', '机器人ETF', 0, '机器人', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('513310', 'EXCHANGE', 'SH', 'ETF', 'CNY', '中韩半导体ETF', 0, '中韩半导体', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('588000', 'EXCHANGE', 'SH', 'ETF', 'CNY', '科创50ETF', 0, '科创50', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
-- 银行理财产品
('FBAE41126E', 'OTC', 'NA', 'BANK_WM_NAV', 'CNY', '民生理财贵竹固收增利周周盈7天持有期26号理财产品E', 0, NULL, 0.000000, 0.000000, 1, 1, '15:00', 'cmbc', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    `product_name` = VALUES(`product_name`),
    `is_qdii` = VALUES(`is_qdii`),
    `track_index` = VALUES(`track_index`),
    `buy_fee_rate` = VALUES(`buy_fee_rate`),
    `sell_fee_rate` = VALUES(`sell_fee_rate`),
    `buy_confirm_offset` = VALUES(`buy_confirm_offset`),
    `sell_confirm_offset` = VALUES(`sell_confirm_offset`),
    `cutoff_time` = VALUES(`cutoff_time`),
    `data_source` = VALUES(`data_source`),
    `is_active` = VALUES(`is_active`),
    `updated_at` = NOW();

-- ============================================
-- 4. 验证查询（可选）
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

-- 验证产品数据
-- SELECT product_code, product_name, channel, market, asset_type, is_qdii, is_active
-- FROM product_master
-- ORDER BY channel, asset_type, product_code;

-- ============================================
-- DML脚本完成
-- ============================================
