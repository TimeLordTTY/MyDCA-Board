-- ============================================
-- SQL脚本目的：为产品主数据表添加备注字段
-- 文件编号：02
-- 日期：2026-01-14
-- 执行顺序：在01_add_posting_balance_fields.sql之后执行
-- ============================================
-- 
-- 功能说明：
-- 1. 为product_master表添加note字段，用于产品备注
-- 
-- 注意事项：
-- - 执行前请确保product_master表已存在
-- - note字段允许NULL，用于存储产品备注信息
-- ============================================

SET NAMES utf8mb4;

-- ============================================
-- 添加note字段
-- ============================================
ALTER TABLE `product_master`
ADD COLUMN `note` VARCHAR(500) NULL COMMENT '备注' AFTER `sort_order`;

-- ============================================
-- 验证查询
-- ============================================
SELECT 
    COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_master'
AND COLUMN_NAME = 'note';

-- ============================================
-- 脚本完成
-- ============================================
