-- ============================================================
-- 修改 advisor_suggestion 表：每个 product_id 只保留一条记录
-- ============================================================
-- 说明：
-- 1. 添加唯一键约束，确保每个 product_id 只有一条记录
-- 2. 保留 as_of_time 字段，用于记录最后更新时间
-- 3. 移除按时间查询的索引（不再需要）
-- ============================================================

-- 1. 删除重复记录，只保留每个 product_id 最新的记录（按 as_of_time DESC）
DELETE t1 FROM advisor_suggestion t1
INNER JOIN advisor_suggestion t2 
WHERE t1.product_id = t2.product_id 
  AND t1.as_of_time < t2.as_of_time;

-- 2. 删除不再需要的索引
DROP INDEX `idx_time` ON `advisor_suggestion`;
DROP INDEX `idx_prod_time` ON `advisor_suggestion`;

-- 3. 添加唯一键约束（确保每个 product_id 只有一条记录）
-- 先检查是否已存在唯一键
SET @exist := (SELECT COUNT(*) FROM information_schema.table_constraints 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND constraint_name = 'uk_product');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD UNIQUE KEY `uk_product` (`product_id`) USING BTREE', 
  'SELECT "Unique key uk_product already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 添加 product_id 索引（用于快速查询）
SET @exist := (SELECT COUNT(*) FROM information_schema.statistics 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND index_name = 'idx_product_id');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD INDEX `idx_product_id` (`product_id`) USING BTREE', 
  'SELECT "Index idx_product_id already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

