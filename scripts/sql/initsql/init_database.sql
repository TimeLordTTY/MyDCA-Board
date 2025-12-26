-- MyDCA-Board 数据库初始化脚本
-- 数据库: dca
-- 字符集: utf8mb4_general_ci

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS dca 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_general_ci;

USE dca;

-- ============================================================
-- 1. 交易流水表 (transactions)
-- ============================================================
DROP TABLE IF EXISTS transactions;
CREATE TABLE transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    `product_id` BIGINT NULL COMMENT '产品ID（外键）',
    `date` DATE NOT NULL COMMENT '交易日期',
    product_code VARCHAR(20) NOT NULL COMMENT '产品代码',
    action VARCHAR(20) NOT NULL COMMENT '交易类型: buy_debit/buy_confirm/buy/sell/sell_confirm/dividend',
    amount DECIMAL(20,2) DEFAULT NULL COMMENT '交易金额',
    shares DECIMAL(20,6) DEFAULT NULL COMMENT '份额',
    fee DECIMAL(20,2) DEFAULT NULL COMMENT '手续费',
    nav DECIMAL(20,6) DEFAULT NULL COMMENT '单位净值',
    nav_date DATE DEFAULT NULL COMMENT '净值日期',
    order_id VARCHAR(50) DEFAULT NULL COMMENT '关联订单号',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_date (date),
    INDEX idx_product (product_code),
    INDEX idx_order_id (order_id),
    INDEX idx_action (action),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='交易流水表';


-- ============================================================
-- 2. 订单表 (orders)
-- ============================================================
DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    `product_id` BIGINT NULL COMMENT '产品ID（外键）',
    order_id VARCHAR(50) NOT NULL UNIQUE COMMENT '订单号',
    product_code VARCHAR(20) NOT NULL COMMENT '产品代码',
    order_type VARCHAR(20) NOT NULL COMMENT '订单类型: buy_debit/redeem_request',
    amount DECIMAL(20,2) DEFAULT NULL COMMENT '金额',
    fee DECIMAL(20,2) DEFAULT NULL COMMENT '手续费',
    shares DECIMAL(20,6) DEFAULT NULL COMMENT '份额（赎回时）',
    requested_at DATETIME NOT NULL COMMENT '发起时间',
    trade_date DATE DEFAULT NULL COMMENT '交易日期',
    nav_date DATE DEFAULT NULL COMMENT '净值日期',
    confirm_date DATE DEFAULT NULL COMMENT '确认日期',
    holding_days INT DEFAULT NULL COMMENT '持有天数（赎回时）',
    sell_fee_rate DECIMAL(10,6) DEFAULT NULL COMMENT '赎回费率',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/done/cancelled',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product (product_code),
    INDEX idx_status (status),
    INDEX idx_confirm_date (confirm_date),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单表';


-- ============================================================
-- 3. 生活账本表 (ledger)
-- ============================================================
-- 注意：余额(balance_after)和父账户余额(parent_balance_after)不存储在数据库中，
-- 而是在查询时动态计算。这样修改任何历史记录后，后续余额会自动正确。
DROP TABLE IF EXISTS ledger;
CREATE TABLE ledger (
    id INT AUTO_INCREMENT PRIMARY KEY,
    event_time DATETIME NOT NULL COMMENT '事件时间',
    entry_type VARCHAR(20) NOT NULL COMMENT '类型: expense/income/transfer/refund',
    amount DECIMAL(20,2) NOT NULL COMMENT '金额',
    category_l1 VARCHAR(50) DEFAULT NULL COMMENT '一级分类',
    category_l2 VARCHAR(50) DEFAULT NULL COMMENT '二级分类',
    account_from VARCHAR(50) DEFAULT NULL COMMENT '来源账户',
    account_to VARCHAR(50) DEFAULT NULL COMMENT '目标账户',
    discount DECIMAL(20,2) DEFAULT NULL COMMENT '折扣/优惠',
    reimbursable TINYINT(1) DEFAULT 0 COMMENT '是否可报销',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_time (event_time),
    INDEX idx_entry_type (entry_type),
    INDEX idx_account_from (account_from),
    INDEX idx_account_to (account_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生活账本表';


-- ============================================================
-- 4. 每日快照表 (daily_snapshot) - 对应 daily.csv
-- ============================================================
DROP TABLE IF EXISTS daily_snapshot;
CREATE TABLE daily_snapshot (
    id INT AUTO_INCREMENT PRIMARY KEY,
    `product_id` BIGINT NULL COMMENT '产品ID（外键）',
    fetch_date DATE NOT NULL COMMENT '采集日期',
    product_code VARCHAR(20) NOT NULL COMMENT '产品代码',
    product_name VARCHAR(100) DEFAULT NULL COMMENT '产品名称',
    category VARCHAR(20) DEFAULT NULL COMMENT '分类: fund/bank',
    nav_date DATE DEFAULT NULL COMMENT '净值日期',
    nav DECIMAL(20,6) DEFAULT NULL COMMENT '单位净值',
    shares DECIMAL(20,6) DEFAULT NULL COMMENT '已确认份额',
    `value` DECIMAL(20,2) DEFAULT NULL COMMENT '持仓市值',
    pnl_day DECIMAL(20,2) DEFAULT NULL COMMENT '日盈亏',
    cost DECIMAL(20,2) DEFAULT NULL COMMENT '持仓成本',
    unrealized_pnl DECIMAL(20,2) DEFAULT NULL COMMENT '浮动盈亏',
    return_rate DECIMAL(10,6) DEFAULT NULL COMMENT '持仓收益率',
    cash_in_transit DECIMAL(20,2) DEFAULT NULL COMMENT '在途资金',
    total_value DECIMAL(20,2) DEFAULT NULL COMMENT '产品总资产',
    principal_total DECIMAL(20,2) DEFAULT NULL COMMENT '累计本金',
    total_redemption DECIMAL(20,2) DEFAULT NULL COMMENT '累计赎回',
    total_pnl DECIMAL(20,2) DEFAULT NULL COMMENT '总盈亏',
    real_return DECIMAL(10,6) DEFAULT NULL COMMENT '真实收益率',
    data_status VARCHAR(20) DEFAULT 'ok' COMMENT '数据状态: ok/carried_forward/missing/holiday',
    fetched_at VARCHAR(30) DEFAULT NULL COMMENT '采集时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_product (fetch_date, product_code),
    INDEX idx_fetch_date (fetch_date),
    INDEX idx_product (product_code),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='每日持仓快照表';


-- ============================================================
-- 5. 账户余额快照表 (daily_balance) - 对应 daily_balance.csv
-- ============================================================
DROP TABLE IF EXISTS daily_balance;
CREATE TABLE daily_balance (
    id INT AUTO_INCREMENT PRIMARY KEY,
    fetch_date DATE NOT NULL COMMENT '采集日期',
    account_id VARCHAR(50) NOT NULL COMMENT '账户ID',
    account_name VARCHAR(100) DEFAULT NULL COMMENT '账户名称',
    account_type VARCHAR(30) DEFAULT NULL COMMENT '账户类型',
    balance DECIMAL(20,2) DEFAULT NULL COMMENT '账户余额',
    related_product VARCHAR(20) DEFAULT NULL COMMENT '关联产品代码',
    product_value DECIMAL(20,2) DEFAULT NULL COMMENT '产品市值',
    diff DECIMAL(20,2) DEFAULT NULL COMMENT '差异/收益',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_account (fetch_date, account_id),
    INDEX idx_fetch_date (fetch_date),
    INDEX idx_account_type (account_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='账户余额快照表';


-- ============================================================
-- 6. 净值表 (nav) - 合并所有产品的净值数据
-- ============================================================
DROP TABLE IF EXISTS nav;
CREATE TABLE nav (
    id INT AUTO_INCREMENT PRIMARY KEY,
    `product_id` BIGINT NULL COMMENT '产品ID（外键）',
    product_code VARCHAR(20) NOT NULL COMMENT '产品代码',
    nav_date DATE NOT NULL COMMENT '净值日期',
    nav DECIMAL(20,6) NOT NULL COMMENT '单位净值',
    acc_nav DECIMAL(20,6) DEFAULT NULL COMMENT '累计净值',
    daily_return DECIMAL(10,6) DEFAULT NULL COMMENT '日涨跌幅',
    dividend DECIMAL(20,2) DEFAULT NULL COMMENT '分红',
    fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间',
    UNIQUE KEY uk_product_date (product_code, nav_date),
    INDEX idx_nav_date (nav_date),
    INDEX idx_product (product_code),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='净值表';


-- ============================================================
-- 7. 产品表 (products) - 支持场内/场外分离
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
-- 8. 账户表 (accounts) - 支持账户层级和产品关联
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
-- 9. 场内成交流水表 (trade_fills)
-- ============================================================
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


-- ============================================================
-- 10. 场内实时行情表 (market_quote_rt)
-- ============================================================
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


-- ============================================================
-- 11. 场内日K线表 (market_bar_d)
-- ============================================================
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


-- ============================================================
-- 12. QDII 溢价率表 (qdii_premium_rt)
-- ============================================================
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
-- 13. 资金池分配规则表 (account_pool_rules)
-- ============================================================
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


-- ============================================================
-- 14. 定投计划表 (dca_plan)
-- ============================================================
DROP TABLE IF EXISTS dca_plan;
CREATE TABLE dca_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '产品ID',
    from_account_id BIGINT NOT NULL COMMENT '来源账户ID',
    weekday ENUM('MON','TUE','WED','THU','FRI','SAT','SUN') NOT NULL COMMENT '定投日期（星期几）',
    amount DECIMAL(18,2) NOT NULL COMMENT '定投金额',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id),
    INDEX idx_from_account (from_account_id),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定投计划表';


-- ============================================================
-- 15. 定投任务表 (task_dca)
-- ============================================================
DROP TABLE IF EXISTS task_dca;
CREATE TABLE task_dca (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NULL COMMENT '关联计划ID（可为空，手动任务）',
    task_date DATE NOT NULL COMMENT '任务日期',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    from_account_id BIGINT NOT NULL COMMENT '来源账户ID',
    planned_amount DECIMAL(18,2) NOT NULL COMMENT '计划金额',
    premium_rate DECIMAL(10,6) NULL COMMENT '溢价率（QDII）',
    executed_amount DECIMAL(18,2) DEFAULT 0 COMMENT '执行金额（实际买入）',
    pending_amount DECIMAL(18,2) DEFAULT 0 COMMENT '待买入金额（溢价刹车扣留）',
    status ENUM('PENDING','MATCH','PARTIAL','MISS') DEFAULT 'PENDING' COMMENT '对账状态',
    reason VARCHAR(255) NULL COMMENT '原因说明',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_date (task_date),
    INDEX idx_product_id (product_id),
    INDEX idx_status (status),
    UNIQUE KEY uk_task_date_product (task_date, product_id, from_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定投任务表';


-- ============================================================
-- 16. 待买入池表 (pending_buy_pool)
-- ============================================================
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
-- 17. 任务调度配置表 (job_config)
-- ============================================================
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
-- 18. 分类配置表 (categories)
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
-- 19. 账户组配置表 (account_groups)
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
-- 20. 产品净值范围表 (product_nav_range)
-- ============================================================
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


-- 显示创建的表
SHOW TABLES;

