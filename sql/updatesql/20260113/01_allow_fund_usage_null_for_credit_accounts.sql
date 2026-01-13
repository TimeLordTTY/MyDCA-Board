-- ============================================
-- SQL脚本目的：允许 fund_usage 字段为 NULL（用于信贷账户）
-- 文件编号：01_allow_fund_usage_null_for_credit_accounts.sql
-- 执行日期：2026-01-13
-- 执行顺序：在创建信贷账户之前执行
-- ============================================
-- 
-- 功能说明：
-- 1. 修改 accounts 表的 fund_usage 字段，允许为 NULL
-- 2. 信贷账户（CREDIT_CARD、HUABEI、BAITIAO、LOAN）不需要资金用途
-- 3. 因为信贷账户是贷款账户，没有资金，所以不需要资金用途字段
-- 
-- 注意事项：
-- - 执行前请确保已备份数据库
-- - 此修改不会影响现有数据（已有数据的 fund_usage 保持不变）
-- ============================================

SET NAMES utf8mb4;

-- ============================================
-- 1. 修改 accounts 表的 fund_usage 字段，允许为 NULL
-- ============================================
-- 说明：信贷账户（信用卡、花呗、白条、贷款）不需要资金用途，因为这是贷款账户，没有资金
-- 修改后，fund_usage 字段可以为 NULL，用于标识信贷账户

ALTER TABLE `accounts` 
MODIFY COLUMN `fund_usage` ENUM('SPENDABLE','RESERVED','INVESTABLE') NULL DEFAULT NULL 
COMMENT '资金用途（SPENDABLE=可支出，允许日常支出/生活消费；RESERVED=专款，房租/项目/安全金等，禁止日常支出和默认禁止投资；INVESTABLE=可投资，可用于投资如ETF/逆回购等，默认不用于日常支出。仅对account_kind=REAL且account_type=CASH且为叶子账户的场景做约束校验。信贷账户（CREDIT_CARD、HUABEI、BAITIAO、LOAN）不需要资金用途，此字段为NULL）';

-- ============================================
-- 2. 验证修改结果
-- ============================================
-- 可以执行以下 SQL 验证字段是否允许 NULL：
-- SHOW COLUMNS FROM accounts WHERE Field = 'fund_usage';
