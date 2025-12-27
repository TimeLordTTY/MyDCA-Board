-- ============================================================
-- MyDCA-Board 场内交易系统升级迁移脚本 v1.0
-- 适用于新数据库（表不存在的情况）
-- 执行前请备份数据库！
-- ============================================================

USE dca;

-- ============================================================
-- 1. 创建 products 表（支持场内/场外分离）
-- ============================================================

DROP TABLE IF EXISTS products;
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(32) NOT NULL COMMENT '交易代码/基金代码',
    `channel` ENUM('EXCHANGE','OTC') NOT NULL DEFAULT 'OTC' COMMENT '场内/场外',
    `market` ENUM('SH','SZ','NA') NOT NULL DEFAULT 'NA' COMMENT '市场类型: SH/SZ/NA',
    `asset_type` ENUM('ETF','LOF','FUND','MMF','BANK_WM_NAV','BANK_WM_BOX') NOT NULL DEFAULT 'FUND' COMMENT '资产类型',
    `currency` ENUM('CNY','USD','HKD') DEFAULT 'CNY' COMMENT '货币类型',
    `is_qdii` TINYINT(1) DEFAULT 0 COMMENT '是否QDII',
    `track_index` VARCHAR(64) NULL COMMENT '跟踪指数',
    `product_name` VARCHAR(128) NOT NULL COMMENT '产品名称',
    `category` VARCHAR(20) DEFAULT 'fund' COMMENT '分类: fund/bank',
    `source` VARCHAR(32) NULL COMMENT '数据源 (fund/cmbc/akshare)',
    `buy_fee_rate` DECIMAL(10,6) DEFAULT 0 COMMENT '申购费率',
    `sell_fee_rate` DECIMAL(10,6) DEFAULT 0 COMMENT '赎回费率',
    `buy_confirm_offset` INT DEFAULT 1 COMMENT '买入确认延迟交易日数',
    `sell_confirm_offset` INT DEFAULT 1 COMMENT '赎回确认延迟交易日数',
    `cutoff_time` VARCHAR(10) DEFAULT '15:00' COMMENT '交易截止时间',
    `product_code` VARCHAR(32) NULL COMMENT '产品代码（兼容字段，等于code）',
    `note` VARCHAR(500) NULL COMMENT '备注',
    `is_active` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_prod_code_channel_market (`code`, `channel`, `market`),
    INDEX idx_code (`code`),
    INDEX idx_channel (`channel`),
    INDEX idx_asset_type (`asset_type`),
    INDEX idx_is_active (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='产品配置表';


-- ============================================================
-- 2. 创建 accounts 表（支持账户层级和产品关联）
-- ============================================================

DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    `account_code` VARCHAR(64) NOT NULL UNIQUE COMMENT '账户代码（唯一标识）',
    `account_id` VARCHAR(64) NULL COMMENT '账户ID（兼容字段，等于account_code）',
    `account_name` VARCHAR(128) NOT NULL COMMENT '账户名称',
    `account_type` ENUM('CASH','BUCKET','FUND_MAPPED','PRODUCT_SUB','FUND_TOTAL','SUMMARY') NOT NULL COMMENT '账户类型',
    `parent_account_id` BIGINT NULL COMMENT '父账户ID（桶挂在大账户下）',
    `product_id` BIGINT NULL COMMENT '账户背后绑定的产品ID',
    `currency` ENUM('CNY','USD','HKD') DEFAULT 'CNY' COMMENT '货币类型',
    `note` VARCHAR(500) NULL COMMENT '备注',
    `is_active` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_account_type (`account_type`),
    INDEX idx_parent_account (`parent_account_id`),
    INDEX idx_product_id (`product_id`),
    INDEX idx_is_active (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='账户配置表';


-- ============================================================
-- 3. 为现有表添加 product_id 字段（如果表存在）
-- ============================================================
-- 注意：如果表不存在，这些 ALTER 语句会失败，但不会影响脚本继续执行
-- 如果表已存在但字段已存在，ALTER 会失败，需要手动处理

-- 3.1 transactions 表
-- 如果表存在，尝试添加字段（如果字段已存在会报错，可忽略）
ALTER TABLE transactions ADD COLUMN `product_id` BIGINT NULL COMMENT '产品ID（外键）' AFTER id;
ALTER TABLE transactions ADD INDEX idx_product_id (`product_id`);

-- 3.2 orders 表
ALTER TABLE orders ADD COLUMN `product_id` BIGINT NULL COMMENT '产品ID（外键）' AFTER id;
ALTER TABLE orders ADD INDEX idx_product_id (`product_id`);

-- 3.3 daily_snapshot 表
ALTER TABLE daily_snapshot ADD COLUMN `product_id` BIGINT NULL COMMENT '产品ID（外键）' AFTER id;
ALTER TABLE daily_snapshot ADD INDEX idx_product_id (`product_id`);

-- 3.4 nav 表
ALTER TABLE nav ADD COLUMN `product_id` BIGINT NULL COMMENT '产品ID（外键）' AFTER id;
ALTER TABLE nav ADD INDEX idx_product_id (`product_id`);


-- ============================================================
-- 4. 数据迁移：将 product_code 关联到 product_id（如果表存在且有数据）
-- ============================================================
-- 注意：这些 UPDATE 语句只有在表存在且有数据时才会执行
-- 如果表不存在或没有数据，可以安全地忽略

-- 4.1 更新 transactions 表
UPDATE transactions t
INNER JOIN products p ON t.product_code = p.code AND p.channel = 'OTC'
SET t.product_id = p.id
WHERE t.product_id IS NULL;

-- 4.2 更新 orders 表
UPDATE orders o
INNER JOIN products p ON o.product_code = p.code AND p.channel = 'OTC'
SET o.product_id = p.id
WHERE o.product_id IS NULL;

-- 4.3 更新 daily_snapshot 表
UPDATE daily_snapshot d
INNER JOIN products p ON d.product_code = p.code AND p.channel = 'OTC'
SET d.product_id = p.id
WHERE d.product_id IS NULL;

-- 4.4 更新 nav 表
UPDATE nav n
INNER JOIN products p ON n.product_code = p.code AND p.channel = 'OTC'
SET n.product_id = p.id
WHERE n.product_id IS NULL;


-- ============================================================
-- 5. 创建新表：场内交易相关
-- ============================================================

-- 5.1 场内成交流水表 (trade_fills)
DROP TABLE IF EXISTS trade_fills;
CREATE TABLE trade_fills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trade_date DATE NOT NULL COMMENT '成交日期',
    trade_time DATETIME NOT NULL COMMENT '成交时间（精确到秒）',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    side ENUM('BUY','SELL') NOT NULL COMMENT '买卖方向',
    qty DECIMAL(18,6) NOT NULL COMMENT '成交数量（份额/股数）',
    price DECIMAL(18,6) NOT NULL COMMENT '成交价',
    amount DECIMAL(18,2) NOT NULL COMMENT '成交金额（含费）',
    fee DECIMAL(18,2) DEFAULT 0 COMMENT '手续费（佣金等）',
    tax DECIMAL(18,2) DEFAULT 0 COMMENT '印花税（ETF通常0）',
    other_fee DECIMAL(18,2) DEFAULT 0 COMMENT '其他费用',
    broker_order_id VARCHAR(64) NULL COMMENT '券商订单号（用于去重）',
    remark VARCHAR(255) NULL COMMENT '备注',
    source ENUM('IMPORT','MANUAL') NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id),
    INDEX idx_trade_date (trade_date),
    INDEX idx_side (side),
    INDEX idx_broker_order_id (broker_order_id),
    UNIQUE KEY uk_fill_source_order (source, broker_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='场内成交流水表';

-- 5.2 场内实时行情表 (market_quote_rt)
DROP TABLE IF EXISTS market_quote_rt;
CREATE TABLE market_quote_rt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '产品ID',
    quote_time DATETIME NOT NULL COMMENT '行情时间（精确到秒）',
    price DECIMAL(18,6) NOT NULL COMMENT '当前价格',
    prev_close DECIMAL(18,6) NULL COMMENT '昨收价',
    pct_chg DECIMAL(10,6) NULL COMMENT '涨跌幅（%）',
    volume DECIMAL(20,2) NULL COMMENT '成交量',
    amount DECIMAL(20,2) NULL COMMENT '成交额',
    source VARCHAR(32) NOT NULL DEFAULT 'AKSHARE' COMMENT '数据源',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id),
    INDEX idx_quote_time (quote_time),
    UNIQUE KEY uk_rt_product_time_source (product_id, quote_time, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='场内实时行情表';

-- 5.3 场内日K线表 (market_bar_d)
DROP TABLE IF EXISTS market_bar_d;
CREATE TABLE market_bar_d (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '产品ID',
    trade_date DATE NOT NULL COMMENT '交易日期',
    open_price DECIMAL(18,6) NULL COMMENT '开盘价',
    high_price DECIMAL(18,6) NULL COMMENT '最高价',
    low_price DECIMAL(18,6) NULL COMMENT '最低价',
    close_price DECIMAL(18,6) NOT NULL COMMENT '收盘价',
    volume DECIMAL(20,2) NULL COMMENT '成交量',
    amount DECIMAL(20,2) NULL COMMENT '成交额',
    prev_close DECIMAL(18,6) NULL COMMENT '昨收价',
    source VARCHAR(32) NOT NULL DEFAULT 'AKSHARE' COMMENT '数据源',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id),
    INDEX idx_trade_date (trade_date),
    UNIQUE KEY uk_bar_product_date_source (product_id, trade_date, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='场内日K线表';

-- 5.4 QDII 溢价率表 (qdii_premium_rt)
DROP TABLE IF EXISTS qdii_premium_rt;
CREATE TABLE qdii_premium_rt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '产品ID',
    quote_time DATETIME NOT NULL COMMENT '行情时间',
    iopv DECIMAL(18,6) NULL COMMENT 'IOPV（基金份额参考净值）',
    premium_rate DECIMAL(10,6) NOT NULL COMMENT '溢价率（如 0.0123 表示 1.23%）',
    source VARCHAR(32) NOT NULL DEFAULT 'AKSHARE' COMMENT '数据源',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id),
    INDEX idx_quote_time (quote_time),
    UNIQUE KEY uk_prem_product_time_source (product_id, quote_time, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='QDII溢价率表';


-- ============================================================
-- 6. 创建新表：资金池与定投
-- ============================================================

-- 6.1 资金池分配规则表 (account_pool_rules)
DROP TABLE IF EXISTS account_pool_rules;
CREATE TABLE account_pool_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_account_id BIGINT NOT NULL COMMENT '来源账户ID（如余利宝理财金）',
    to_product_id BIGINT NOT NULL COMMENT '目标产品ID（基金/ETF/LOF）',
    ratio DECIMAL(10,6) NOT NULL COMMENT '分配比例（如 0.35 表示 35%）',
    min_amount DECIMAL(18,2) DEFAULT 0 COMMENT '最小分配金额',
    round_step DECIMAL(18,2) DEFAULT 1 COMMENT '取整粒度（如 1/10/100）',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_from_account (from_account_id),
    INDEX idx_to_product (to_product_id),
    UNIQUE KEY uk_pool_account_product (from_account_id, to_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资金池分配规则表';

-- 6.2 待买入池表 (pending_buy_pool)
DROP TABLE IF EXISTS pending_buy_pool;
CREATE TABLE pending_buy_pool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '产品ID',
    from_account_id BIGINT NOT NULL COMMENT '来源账户ID',
    pending_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '待买入金额（累加）',
    reason VARCHAR(255) NULL COMMENT '扣留原因（溢价刹车等）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id),
    INDEX idx_from_account (from_account_id),
    UNIQUE KEY uk_pool_product_account (product_id, from_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='待买入池表';


-- ============================================================
-- 7. 创建新表：调度配置
-- ============================================================

-- 7.1 任务调度配置表 (job_config)
DROP TABLE IF EXISTS job_config;
CREATE TABLE job_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_code VARCHAR(64) NOT NULL UNIQUE COMMENT '任务代码',
    cron_expr VARCHAR(64) NOT NULL COMMENT 'Cron表达式',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    last_run_at DATETIME NULL COMMENT '最后执行时间',
    last_status ENUM('OK','FAIL') NULL COMMENT '最后执行状态',
    last_message VARCHAR(255) NULL COMMENT '最后执行消息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务调度配置表';


-- ============================================================
-- 8. 创建新表：分类配置（从 categories.json 迁移）
-- ============================================================

DROP TABLE IF EXISTS categories;
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entry_type ENUM('expense','income') NOT NULL COMMENT '记账类型',
    category_l1 VARCHAR(50) NOT NULL COMMENT '一级分类',
    category_l2 VARCHAR(50) NULL COMMENT '二级分类（可为空）',
    display_order INT DEFAULT 0 COMMENT '显示顺序',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_entry_type (entry_type),
    INDEX idx_category_l1 (category_l1),
    UNIQUE KEY uk_category (entry_type, category_l1, category_l2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分类配置表';


-- ============================================================
-- 9. 创建新表：账户组配置（从 accounts.json 迁移）
-- ============================================================

DROP TABLE IF EXISTS account_groups;
CREATE TABLE account_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_code VARCHAR(64) NOT NULL UNIQUE COMMENT '组代码（如 wenlibao/ylb）',
    group_name VARCHAR(100) NOT NULL COMMENT '组名称',
    linked_product_id BIGINT NULL COMMENT '关联产品ID（如稳利宝）',
    profit_account_id BIGINT NULL COMMENT '收益归属账户ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_linked_product (linked_product_id),
    INDEX idx_profit_account (profit_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='账户组配置表';


-- ============================================================
-- 完成
-- ============================================================

SELECT 'Migration completed! Please run import_existing_data.sql to import products/accounts/categories data.' AS message;
