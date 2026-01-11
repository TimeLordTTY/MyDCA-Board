-- ============================================
-- SQL脚本目的：为产品主数据表添加排序字段
-- 文件编号：02
-- 日期：2026-01-12
-- 执行顺序：在01_init_product_master.sql之后执行
-- ============================================
-- 
-- 功能说明：
-- 1. 为product_master表添加sort_order字段，用于产品排序
-- 2. 为现有产品设置默认排序值（按id排序）
-- 
-- 注意事项：
-- - 执行前请确保product_master表已存在
-- - sort_order字段允许NULL，新产品的排序值需要手动设置
-- ============================================

SET NAMES utf8mb4;

-- ============================================
-- 添加sort_order字段
-- ============================================
ALTER TABLE `product_master`
ADD COLUMN `sort_order` INT NULL COMMENT '排序顺序（数字越小越靠前，NULL表示未设置）' AFTER `is_active`;

-- 为现有产品设置默认排序值（按id排序，场内和场外分别排序）
-- 场内产品（EXCHANGE）
SET @exchange_sort = 0;
UPDATE `product_master`
SET `sort_order` = (@exchange_sort := @exchange_sort + 1)
WHERE `channel` = 'EXCHANGE'
ORDER BY `id`;

-- 场外产品（OTC）
SET @otc_sort = 0;
UPDATE `product_master`
SET `sort_order` = (@otc_sort := @otc_sort + 1)
WHERE `channel` = 'OTC'
ORDER BY `id`;

-- 添加索引以优化排序查询
ALTER TABLE `product_master`
ADD KEY `idx_channel_sort_order` (`channel`, `sort_order`);

-- ============================================
-- 验证查询
-- ============================================
-- 执行以下查询可验证字段是否正确添加
-- SELECT 
--     id, 
--     product_code, 
--     product_name, 
--     channel, 
--     sort_order
-- FROM product_master
-- ORDER BY channel, sort_order, id;

-- ============================================
-- 脚本完成
-- ============================================
