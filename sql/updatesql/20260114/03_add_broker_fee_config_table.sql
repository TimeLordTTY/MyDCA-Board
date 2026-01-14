-- ============================================
-- SQL脚本目的：创建券商费率配置表
-- 文件编号：03
-- 日期：2026-01-14
-- 执行顺序：在02_add_product_note_field.sql之后执行
-- ============================================
-- 
-- 功能说明：
-- 1. 创建broker_fee_config表，用于存储券商账户的费率配置
-- 2. 费率配置与券商账户绑定，而不是与产品绑定
-- 3. 支持不同产品类型的费率规则（A股、ETF、LOF、可转债等）
-- 4. 支持最低手续费（起收金额）配置
-- 
-- 注意事项：
-- - 执行前请确保accounts表已存在
-- - 费率配置只对account_type=BROKER的账户生效
-- - 如果某个账户没有配置费率，可以使用DEFAULT规则
-- ============================================

SET NAMES utf8mb4;

-- ============================================
-- 创建券商费率配置表
-- ============================================
CREATE TABLE `broker_fee_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '费率配置ID',
  `account_id` BIGINT NOT NULL COMMENT '券商账户ID（外键accounts.id，account_type必须为BROKER）',
  `fee_rule_type` ENUM('STOCK', 'ETF', 'LOF', 'LOF_SUBSCRIPTION', 'CONVERTIBLE_BOND_SH', 'CONVERTIBLE_BOND_SZ', 'BOND_REPO', 'FUND_OTC', 'DEFAULT') NOT NULL COMMENT '费率规则类型（STOCK=A股，ETF=ETF，LOF=LOF场内交易，LOF_SUBSCRIPTION=LOF场内申购，CONVERTIBLE_BOND_SH=上海可转债，CONVERTIBLE_BOND_SZ=深圳可转债，BOND_REPO=逆回购，FUND_OTC=场外基金，DEFAULT=默认规则）',
  `buy_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '买入费率（如0.0001154表示万1.154）',
  `sell_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '卖出费率',
  `buy_min_fee` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '买入最低手续费（起收金额，如2.00表示2元起收）',
  `sell_min_fee` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '卖出最低手续费',
  `subscription_discount_rate` DECIMAL(10, 7) NULL COMMENT '申购折扣率（如0.1表示一折，仅用于LOF_SUBSCRIPTION）',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `note` VARCHAR(500) NULL COMMENT '备注',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_fee_rule` (`account_id`, `fee_rule_type`),
  KEY `idx_account_id` (`account_id`),
  KEY `idx_fee_rule_type` (`fee_rule_type`),
  KEY `idx_is_active` (`is_active`),
  CONSTRAINT `fk_broker_fee_account` FOREIGN KEY (`account_id`) REFERENCES `accounts`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='券商费率配置表';

-- ============================================
-- 验证查询
-- ============================================
SELECT 
    TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'broker_fee_config'
ORDER BY ORDINAL_POSITION;

-- ============================================
-- 脚本完成
-- ============================================
