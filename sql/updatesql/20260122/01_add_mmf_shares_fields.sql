-- 添加 MMF 份额管理相关字段
-- 用于支持货币基金类型平台的份额管理功能

-- 平台账户：初始份额（仅MMF平台账户使用）
ALTER TABLE `accounts` ADD COLUMN `initial_shares` DECIMAL(18,6) NULL DEFAULT NULL COMMENT '初始份额（仅MMF平台账户使用，用于份额管理）' AFTER `linked_product_id`;

-- 子账户：是否固定金额（仅MMF子账户使用，如房租预备金）
ALTER TABLE `accounts` ADD COLUMN `is_fixed_amount` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否固定金额子账户（仅MMF子账户使用，如房租预备金）' AFTER `initial_shares`;

-- 子账户：固定金额值（仅is_fixed_amount=1时有效）
ALTER TABLE `accounts` ADD COLUMN `fixed_amount` DECIMAL(18,2) NULL DEFAULT NULL COMMENT '固定金额值（仅is_fixed_amount=1时有效）' AFTER `is_fixed_amount`;
