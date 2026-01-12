-- ============================================
-- SQL脚本目的：为ledger_txn表添加分类和报销相关字段
-- 文件名称：03_add_category_and_reimbursable_fields.sql
-- 执行日期：2026-01-12
-- ============================================
-- 
-- 功能说明：
-- 1. 添加category_id字段：关联categories表，用于收入/支出的分类
-- 2. 添加is_reimbursable字段：标记支出是否可报销
-- 3. 添加is_reimbursed字段：标记支出是否已报销
-- 
-- 基于：《财富中枢系统完整设计方案.md》
-- ============================================

SET NAMES utf8mb4;

-- 添加分类ID字段
ALTER TABLE `ledger_txn` 
ADD COLUMN `category_id` BIGINT NULL COMMENT '分类ID（外键categories.id，用于收入/支出分类）' AFTER `note`,
ADD INDEX `idx_category_id` (`category_id`);

-- 添加是否可报销字段
ALTER TABLE `ledger_txn` 
ADD COLUMN `is_reimbursable` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可报销（仅用于EXPENSE类型交易）' AFTER `category_id`;

-- 添加是否已报销字段
ALTER TABLE `ledger_txn` 
ADD COLUMN `is_reimbursed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已报销（仅用于EXPENSE类型交易）' AFTER `is_reimbursable`;

-- 添加外键约束（可选，如果categories表存在）
-- ALTER TABLE `ledger_txn` 
-- ADD CONSTRAINT `fk_ledger_txn_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON DELETE SET NULL;
