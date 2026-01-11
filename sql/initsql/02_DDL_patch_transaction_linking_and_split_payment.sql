-- ============================================
-- SQL脚本目的：数据库结构补丁 - 交易关联和组合支付功能
-- 文件编号：02
-- 执行顺序：已合并到01_DDL_v2_revised.sql，此文件仅作历史参考
-- ============================================
-- 
-- 【重要说明】
-- 本补丁的所有内容已合并到主DDL文件（01_DDL_v2_revised.sql）中：
-- 1. ledger_txn表的related_txn_id、related_order_id、relation_type字段已在主DDL中定义
-- 2. order_funding_line表已在主DDL中定义
-- 3. 相关索引已在主DDL中定义
-- 
-- 如果数据库是从01_DDL_v2_revised.sql全新创建的，无需执行此补丁。
-- 如果数据库是旧版本升级，且缺少相关字段，可执行此补丁的ALTER语句。
-- 
-- 执行时间：2024-01-XX
-- 
-- 注意事项：
-- - 此脚本为DDL补丁，用于在已有数据库结构基础上扩展功能
-- - 执行前请确保已执行01_DDL_v2_revised.sql
-- - 如果字段已存在，执行会报错，可忽略或手动删除已存在的字段
-- ============================================

-- ============================================
-- 1. 交易关联字段（ledger_txn表）
-- ============================================

-- 1.1 增加关联字段
ALTER TABLE `ledger_txn`
  ADD COLUMN `related_txn_id` VARCHAR(64) NULL COMMENT '关联的原交易txn_id（退款/报销等，指向原交易）' AFTER `order_id`,
  ADD COLUMN `related_order_id` VARCHAR(64) NULL COMMENT '关联的原订单号（可选，用于订单级退款/撤单）' AFTER `related_txn_id`,
  ADD COLUMN `relation_type` ENUM('NONE','TRANSFER_PAIR','REFUND','REIMBURSE','REVERSAL') NOT NULL DEFAULT 'NONE' COMMENT '关联类型（NONE=无关联，TRANSFER_PAIR=转账成对，REFUND=退款，REIMBURSE=报销，REVERSAL=撤销）' AFTER `related_order_id`;

-- 1.2 增加索引
ALTER TABLE `ledger_txn`
  ADD KEY `idx_related_txn_id` (`related_txn_id`),
  ADD KEY `idx_related_order_id` (`related_order_id`),
  ADD KEY `idx_relation_type` (`relation_type`);

-- 1.3 应用层约束说明（MySQL CHECK约束不支持引用外键列，需在应用层校验）
-- 约束1：related_txn_id 不能等于 txn_id（防止自引用）
-- 约束2：relation_type='REFUND'或'REIMBURSE'时，related_txn_id必须非空
-- 约束3：relation_type='TRANSFER_PAIR'时，使用biz_group_key关联（保持现有逻辑）
-- 约束4：relation_type='REVERSAL'时，related_txn_id指向被撤销的交易

-- ============================================
-- 2. 订单资金来源拆分表（order_funding_line）
-- ============================================

CREATE TABLE `order_funding_line` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资金来源行ID',
  `order_id` VARCHAR(64) NOT NULL COMMENT '订单ID（外键orders.order_id）',
  `line_no` INT NOT NULL COMMENT '行号（同一订单内从1开始递增）',
  `account_id` BIGINT NOT NULL COMMENT '资金来源账户ID（外键accounts.id，必须是叶子账户）',
  `amount` DECIMAL(18, 2) NOT NULL COMMENT '出资金额',
  `currency` ENUM('CNY', 'USD', 'HKD') NOT NULL DEFAULT 'CNY' COMMENT '货币',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_line` (`order_id`, `line_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_account_id` (`account_id`),
  CONSTRAINT `fk_funding_line_order` FOREIGN KEY (`order_id`) REFERENCES `orders`(`order_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_funding_line_account` FOREIGN KEY (`account_id`) REFERENCES `accounts`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单资金来源拆分表';

-- ============================================
-- 3. 数据迁移说明（如已有数据）
-- ============================================

-- 3.1 已有订单的资金来源迁移
-- 如果orders表有account_id字段（旧设计），需要迁移到order_funding_line表
-- 示例SQL（需根据实际情况调整）：
-- INSERT INTO order_funding_line (order_id, line_no, account_id, amount, currency)
-- SELECT order_id, 1, account_id, amount, 'CNY'
-- FROM orders
-- WHERE account_id IS NOT NULL AND status = 'PENDING';

-- 3.2 已有退款/报销交易的关联关系迁移
-- 如果已有REIMBURSE_IN/REIMBURSE_OUT类型的交易，需要根据业务逻辑补充related_txn_id
-- 示例SQL（需根据实际情况调整）：
-- UPDATE ledger_txn
-- SET related_txn_id = 'TXN-XXX', relation_type = 'REFUND'
-- WHERE txn_type IN ('REIMBURSE_IN', 'REIMBURSE_OUT')
--   AND related_txn_id IS NULL;

-- ============================================
-- 4. 验证SQL
-- ============================================

-- 验证1：检查关联字段是否正确添加
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
-- FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'ledger_txn'
--   AND COLUMN_NAME IN ('related_txn_id', 'related_order_id', 'relation_type');

-- 验证2：检查order_funding_line表是否正确创建
-- SELECT TABLE_NAME, TABLE_COMMENT
-- FROM INFORMATION_SCHEMA.TABLES
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'order_funding_line';

-- 验证3：检查索引是否正确创建
-- SHOW INDEX FROM ledger_txn WHERE Key_name IN ('idx_related_txn_id', 'idx_related_order_id', 'idx_relation_type');
-- SHOW INDEX FROM order_funding_line WHERE Key_name IN ('idx_order_id', 'idx_account_id', 'uk_order_line');
