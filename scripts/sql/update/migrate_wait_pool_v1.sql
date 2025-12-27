-- ============================================================
-- 等待池与预算逻辑完善 - 数据库迁移脚本
-- ============================================================

-- 1. 扩展 advisor_suggestion 表：添加新字段
-- new_budget
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'new_budget');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD COLUMN `new_budget` decimal(18, 2) NULL DEFAULT NULL COMMENT ''本轮新增预算（根据资金规则计算出的新可投入金额）'' AFTER `wait_pool_balance`', 
  'SELECT "Column new_budget already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- wait_pool_before
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'wait_pool_before');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD COLUMN `wait_pool_before` decimal(18, 2) NULL DEFAULT NULL COMMENT ''等待池余额（before，历史累计）'' AFTER `new_budget`', 
  'SELECT "Column wait_pool_before already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- planned_amount
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'advisor_suggestion' 
               AND column_name = 'planned_amount');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `advisor_suggestion` ADD COLUMN `planned_amount` decimal(18, 2) NULL DEFAULT NULL COMMENT ''本轮可用于买入（=new_budget + wait_pool_before）'' AFTER `wait_pool_before`', 
  'SELECT "Column planned_amount already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 扩展 pending_buy_pool 表：添加审计字段
-- last_change_reason
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'pending_buy_pool' 
               AND column_name = 'last_change_reason');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `pending_buy_pool` ADD COLUMN `last_change_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT ''最后变更原因（如NON_TRADE_DAY, PREMIUM_BRAKE, MIN_TRADE_LIMIT）'' AFTER `reason`', 
  'SELECT "Column last_change_reason already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- last_change_time
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'pending_buy_pool' 
               AND column_name = 'last_change_time');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `pending_buy_pool` ADD COLUMN `last_change_time` datetime NULL DEFAULT NULL COMMENT ''最后变更时间'' AFTER `last_change_reason`', 
  'SELECT "Column last_change_time already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- version（乐观锁）
SET @exist := (SELECT COUNT(*) FROM information_schema.columns 
               WHERE table_schema = DATABASE() 
               AND table_name = 'pending_buy_pool' 
               AND column_name = 'version');
SET @sqlstmt := IF(@exist = 0, 
  'ALTER TABLE `pending_buy_pool` ADD COLUMN `version` bigint(20) NOT NULL DEFAULT 0 COMMENT ''版本号（乐观锁，避免并发冲突）'' AFTER `last_change_time`', 
  'SELECT "Column version already exists"');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 创建 budget_trace 表（审计日志）
DROP TABLE IF EXISTS `budget_trace`;
CREATE TABLE `budget_trace` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL COMMENT '产品ID',
  `as_of_time` datetime NOT NULL COMMENT '建议生成时间',
  `new_budget` decimal(18, 2) NOT NULL COMMENT '本轮新增预算',
  `wait_pool_before` decimal(18, 2) NOT NULL COMMENT '等待池余额（before）',
  `planned_amount` decimal(18, 2) NOT NULL COMMENT '本轮可用于买入',
  `executed_amount` decimal(18, 2) NOT NULL COMMENT '本轮建议执行金额',
  `moved_to_wait` decimal(18, 2) NOT NULL COMMENT '本轮进入等待池金额',
  `wait_pool_after` decimal(18, 2) NOT NULL COMMENT '等待池余额（after）',
  `reason_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '原因代码（如NON_TRADE_DAY, PREMIUM_BRAKE）',
  `reason_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '原因说明（结构化文本）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_trace` (`product_id`, `as_of_time`) USING BTREE,
  INDEX `idx_time` (`as_of_time`) USING BTREE,
  INDEX `idx_product` (`product_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '预算追踪审计日志表' ROW_FORMAT = Dynamic;

SELECT '等待池与预算逻辑完善 - 数据库迁移完成！' AS result;

