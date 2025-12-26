-- ============================================================
-- 净值范围表迁移脚本
-- 将 nav_range.json 迁移到数据库表
-- ============================================================

USE dca;

-- 创建 product_nav_range 表
DROP TABLE IF EXISTS product_nav_range;
CREATE TABLE product_nav_range (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(32) NOT NULL COMMENT '产品代码',
    product_name VARCHAR(128) DEFAULT NULL COMMENT '产品名称',
    earliest_nav_date DATE DEFAULT NULL COMMENT '最早净值日期',
    latest_nav_date DATE DEFAULT NULL COMMENT '最新净值日期',
    record_count INT DEFAULT 0 COMMENT '记录数',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_product_code (product_code),
    INDEX idx_earliest_date (earliest_nav_date),
    INDEX idx_latest_date (latest_nav_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='产品净值范围表';

