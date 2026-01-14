-- ============================================
-- SQL脚本目的：更新费率字段精度，支持7位小数（如0.0001154）
-- 文件编号：06
-- 日期：2026-01-14
-- 执行顺序：在05_add_fund_sell_fee_tier_table.sql之后执行
-- ============================================
-- 
-- 功能说明：
-- 1. 将费率相关字段从DECIMAL(10, 6)改为DECIMAL(10, 7)，支持7位小数精度
-- 2. 支持万1.154这样的费率（0.0001154需要7位小数）
-- 
-- 注意事项：
-- - 执行前请确保相关表已存在
-- - 此脚本会修改现有表的字段类型，如果表中有数据，数据会自动转换
-- ============================================

SET NAMES utf8mb4;

-- ============================================
-- 更新product_master表的费率字段精度
-- ============================================
ALTER TABLE `product_master`
MODIFY COLUMN `buy_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '申购费率（默认值，实际费率优先从broker_fee_config获取）',
MODIFY COLUMN `sell_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '赎回费率（默认值，实际费率优先从broker_fee_config获取）';

-- ============================================
-- 更新broker_fee_config表的费率字段精度
-- ============================================
ALTER TABLE `broker_fee_config`
MODIFY COLUMN `buy_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '买入费率（如0.0001154表示万1.154）',
MODIFY COLUMN `sell_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '卖出费率',
MODIFY COLUMN `subscription_discount_rate` DECIMAL(10, 7) NULL COMMENT '申购折扣率（如0.1表示一折，仅用于LOF_SUBSCRIPTION）';

-- ============================================
-- 更新fund_sell_fee_tier表的费率字段精度
-- ============================================
ALTER TABLE `fund_sell_fee_tier`
MODIFY COLUMN `sell_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '卖出费率（如0.0015表示0.15%）';

-- ============================================
-- 验证查询
-- ============================================
SELECT 
    TABLE_NAME, 
    COLUMN_NAME, 
    COLUMN_TYPE, 
    IS_NULLABLE, 
    COLUMN_DEFAULT, 
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME IN ('product_master', 'broker_fee_config', 'fund_sell_fee_tier')
  AND COLUMN_NAME IN ('buy_fee_rate', 'sell_fee_rate', 'subscription_discount_rate')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- ============================================
-- 脚本完成
-- ============================================
