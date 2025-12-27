-- ============================================================
-- 迁移脚本：完善生产建议层Advisor（v1）
-- 执行时间：2025-12-27
-- 说明：为现有表添加新字段，支持多策略组合和ViewModel
-- 注意：此脚本可重复执行（幂等），使用存储过程检查字段是否存在
-- ============================================================

-- 1. 修改 product_strategy_bind 表：支持多策略绑定

-- 删除旧的唯一索引（如果存在）
SET @exist := (SELECT COUNT(*) FROM information_schema.statistics 
               WHERE table_schema = DATABASE() 
               AND table_name = 'product_strategy_bind' 
               AND index_name = 'uk_product');
SET @sqlstmt := IF(@exist > 0, 'ALTER TABLE `product_strategy_bind` DROP INDEX `uk_product`', 'SELECT "Index uk_product does not exist"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 strategy_type 字段（如果不存在）
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'product_strategy_bind' 
               AND column_name = 'strategy_type');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `product_strategy_bind` ADD COLUMN `strategy_type` enum(''VETO'',''TRIGGER'',''SCORE'') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT ''TRIGGER'' COMMENT ''策略类型：VETO=否决层，TRIGGER=触发层，SCORE=强度层'' AFTER `enabled`', 
  'SELECT "Column strategy_type already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 priority 字段（如果不存在）
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'product_strategy_bind' 
               AND column_name = 'priority');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `product_strategy_bind` ADD COLUMN `priority` int(11) NOT NULL DEFAULT 0 COMMENT ''优先级（数字越小越优先，同层内按此排序）'' AFTER `strategy_type`', 
  'SELECT "Column priority already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加新的唯一索引（如果不存在）
SET @exist := (SELECT COUNT(*) FROM information_schema.statistics 
               WHERE table_schema = DATABASE() 
               AND table_name = 'product_strategy_bind' 
               AND index_name = 'uk_product_strategy');
SET @sqlstmt := IF(@exist = 0, 
  'CREATE UNIQUE INDEX `uk_product_strategy` ON `product_strategy_bind`(`product_id`, `strategy_code`)', 
  'SELECT "Index uk_product_strategy already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加组合索引（如果不存在）
SET @exist := (SELECT COUNT(*) FROM information_schema.statistics 
               WHERE table_schema = DATABASE() 
               AND table_name = 'product_strategy_bind' 
               AND index_name = 'idx_product_type');
SET @sqlstmt := IF(@exist = 0, 
  'CREATE INDEX `idx_product_type` ON `product_strategy_bind`(`product_id`, `strategy_type`, `priority`)', 
  'SELECT "Index idx_product_type already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 修改 advisor_suggestion 表：扩展支持ViewModel

-- 修改action枚举（添加SKIP）- 需要先检查当前枚举值
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'action' 
               AND column_type LIKE '%SKIP%');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` MODIFY COLUMN `action` enum(''BUY'',''HOLD'',''WAIT'',''SKIP'') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT ''建议动作''', 
  'SELECT "Action enum already includes SKIP"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加扩展字段（逐个检查并添加）
-- cash_available
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'cash_available');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD COLUMN `cash_available` decimal(18, 2) NULL DEFAULT NULL COMMENT ''可用现金池余额'' AFTER `reason`', 
  'SELECT "Column cash_available already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- wait_pool_balance
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'wait_pool_balance');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD COLUMN `wait_pool_balance` decimal(18, 2) NULL DEFAULT NULL COMMENT ''等待池累计金额'' AFTER `cash_available`', 
  'SELECT "Column wait_pool_balance already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- plan_budget_today
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'plan_budget_today');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD COLUMN `plan_budget_today` decimal(18, 2) NULL DEFAULT NULL COMMENT ''今日计划预算'' AFTER `wait_pool_balance`', 
  'SELECT "Column plan_budget_today already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- budget_for_execution
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'budget_for_execution');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD COLUMN `budget_for_execution` decimal(18, 2) NULL DEFAULT NULL COMMENT ''本次允许用于执行的预算'' AFTER `plan_budget_today`', 
  'SELECT "Column budget_for_execution already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- budget_to_execute
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'budget_to_execute');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD COLUMN `budget_to_execute` decimal(18, 2) NULL DEFAULT NULL COMMENT ''本次建议实际执行金额'' AFTER `budget_for_execution`', 
  'SELECT "Column budget_to_execute already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- budget_to_wait_pool
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'budget_to_wait_pool');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD COLUMN `budget_to_wait_pool` decimal(18, 2) NULL DEFAULT NULL COMMENT ''本次应转入等待池的预算金额'' AFTER `budget_to_execute`', 
  'SELECT "Column budget_to_wait_pool already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- execute_ratio
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'execute_ratio');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD COLUMN `execute_ratio` decimal(18, 6) NULL DEFAULT NULL COMMENT ''执行比例（0~1）'' AFTER `budget_to_wait_pool`', 
  'SELECT "Column execute_ratio already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- wait_ratio
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'wait_ratio');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD COLUMN `wait_ratio` decimal(18, 6) NULL DEFAULT NULL COMMENT ''转等待池比例（0~1）'' AFTER `execute_ratio`', 
  'SELECT "Column wait_ratio already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- reason_blocks_json
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'reason_blocks_json');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD COLUMN `reason_blocks_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''结构化原因列表（JSON）'' AFTER `wait_ratio`', 
  'SELECT "Column reason_blocks_json already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 为现有数据设置默认值（用于兼容旧数据）
-- 如果现有记录的strategy_type为NULL，设置为TRIGGER
UPDATE `product_strategy_bind` 
SET `strategy_type` = 'TRIGGER' 
WHERE `strategy_type` IS NULL;

-- 如果现有记录的priority为NULL，设置为0
UPDATE `product_strategy_bind` 
SET `priority` = 0 
WHERE `priority` IS NULL;

