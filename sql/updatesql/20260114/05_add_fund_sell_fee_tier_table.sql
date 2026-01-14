-- ============================================
-- SQL脚本目的：创建场外基金卖出费率分段表
-- 文件编号：05
-- 日期：2026-01-14
-- 执行顺序：在04_init_huabao_broker_fee_example.sql之后执行
-- ============================================
-- 
-- 功能说明：
-- 1. 创建fund_sell_fee_tier表，用于存储场外基金的卖出费率分段配置
-- 2. 场外基金的卖出费率按持有天数分段（0-7天、7-30天、30-180天等）
-- 3. 买入费率基本是0-100万一档（在product_master表中配置）
-- 4. 银行理财净值型（BANK_WM_NAV）和货币基金（MMF）买入卖出费率都是0
-- 
-- 注意事项：
-- - 执行前请确保product_master表已存在
-- - 持有天数分段使用左闭右开区间（如0-7表示[0, 7)，7-30表示[7, 30)）
-- - 最后一个分段可以使用NULL表示"以上"（如180以上）
-- ============================================

SET NAMES utf8mb4;

-- ============================================
-- 创建场外基金卖出费率分段表
-- ============================================
CREATE TABLE `fund_sell_fee_tier` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '费率分段ID',
  `product_id` BIGINT NOT NULL COMMENT '产品ID（外键product_master.id）',
  `min_days` INT NOT NULL COMMENT '最小持有天数（包含，如0表示持有0天及以上）',
  `max_days` INT NULL COMMENT '最大持有天数（不包含，如7表示持有7天以下，NULL表示无上限）',
  `sell_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '卖出费率（如0.0015表示0.15%）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序顺序（数字越小越靠前，用于确定分段优先级）',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `note` VARCHAR(500) NULL COMMENT '备注（如"持有0-7天"）',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_min_days` (`min_days`),
  KEY `idx_is_active` (`is_active`),
  KEY `idx_product_sort` (`product_id`, `sort_order`),
  CONSTRAINT `fk_fund_sell_fee_product` FOREIGN KEY (`product_id`) REFERENCES `product_master`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='场外基金卖出费率分段表';

-- ============================================
-- 验证查询
-- ============================================
SELECT 
    TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fund_sell_fee_tier'
ORDER BY ORDINAL_POSITION;

-- ============================================
-- 脚本完成
-- ============================================
