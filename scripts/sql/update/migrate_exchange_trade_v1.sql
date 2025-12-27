-- ============================================================
-- 场内成交手动录入 + 等待池扣减闭环 - 数据库迁移脚本
-- ============================================================

-- 1. 扩展 trade_fills 表：添加 account_id 字段（资金来源账户）
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'trade_fills' 
               AND column_name = 'account_id');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `trade_fills` ADD COLUMN `account_id` bigint(20) NULL DEFAULT NULL COMMENT ''资金来源账户ID（外键关联accounts.id）'' AFTER `product_id`', 
  'SELECT "Column account_id already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引
SET @exist := (SELECT COUNT(*) FROM information_schema.statistics 
               WHERE table_schema = DATABASE() 
               AND table_name = 'trade_fills' 
               AND index_name = 'idx_account_id');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `trade_fills` ADD INDEX `idx_account_id`(`account_id`) USING BTREE', 
  'SELECT "Index idx_account_id already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT '场内成交手动录入 + 等待池扣减闭环 - 数据库迁移完成！' AS result;

