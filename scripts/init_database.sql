-- MyDCA-Board 数据库初始化脚本
-- 数据库: dca
-- 字符集: utf8mb4

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS dca 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

USE dca;

-- ============================================================
-- 1. 交易流水表 (transactions)
-- ============================================================
DROP TABLE IF EXISTS transactions;
CREATE TABLE transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    `date` DATE NOT NULL COMMENT '交易日期',
    product_code VARCHAR(20) NOT NULL COMMENT '产品代码',
    action VARCHAR(20) NOT NULL COMMENT '交易类型: buy_debit/buy_confirm/buy/sell/sell_confirm/dividend',
    amount DECIMAL(18,4) DEFAULT NULL COMMENT '交易金额',
    shares DECIMAL(18,6) DEFAULT NULL COMMENT '份额',
    fee DECIMAL(18,4) DEFAULT NULL COMMENT '手续费',
    nav DECIMAL(10,6) DEFAULT NULL COMMENT '单位净值',
    nav_date DATE DEFAULT NULL COMMENT '净值日期',
    order_id VARCHAR(50) DEFAULT NULL COMMENT '关联订单号',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_date (date),
    INDEX idx_product (product_code),
    INDEX idx_order_id (order_id),
    INDEX idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易流水表';


-- ============================================================
-- 2. 订单表 (orders)
-- ============================================================
DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(50) NOT NULL UNIQUE COMMENT '订单号',
    product_code VARCHAR(20) NOT NULL COMMENT '产品代码',
    order_type VARCHAR(20) NOT NULL COMMENT '订单类型: buy_debit/redeem_request',
    amount DECIMAL(18,4) DEFAULT NULL COMMENT '金额',
    fee DECIMAL(18,4) DEFAULT NULL COMMENT '手续费',
    shares DECIMAL(18,6) DEFAULT NULL COMMENT '份额（赎回时）',
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
    INDEX idx_confirm_date (confirm_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';


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
    amount DECIMAL(18,4) NOT NULL COMMENT '金额',
    category_l1 VARCHAR(50) DEFAULT NULL COMMENT '一级分类',
    category_l2 VARCHAR(50) DEFAULT NULL COMMENT '二级分类',
    account_from VARCHAR(50) DEFAULT NULL COMMENT '来源账户',
    account_to VARCHAR(50) DEFAULT NULL COMMENT '目标账户',
    discount DECIMAL(18,4) DEFAULT NULL COMMENT '折扣/优惠',
    reimbursable TINYINT(1) DEFAULT 0 COMMENT '是否可报销',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_time (event_time),
    INDEX idx_entry_type (entry_type),
    INDEX idx_account_from (account_from),
    INDEX idx_account_to (account_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生活账本表';


-- ============================================================
-- 4. 每日快照表 (daily_snapshot) - 对应 daily.csv
-- ============================================================
DROP TABLE IF EXISTS daily_snapshot;
CREATE TABLE daily_snapshot (
    id INT AUTO_INCREMENT PRIMARY KEY,
    fetch_date DATE NOT NULL COMMENT '采集日期',
    product_code VARCHAR(20) NOT NULL COMMENT '产品代码',
    product_name VARCHAR(100) DEFAULT NULL COMMENT '产品名称',
    category VARCHAR(20) DEFAULT NULL COMMENT '分类: fund/bank',
    nav_date DATE DEFAULT NULL COMMENT '净值日期',
    nav DECIMAL(10,6) DEFAULT NULL COMMENT '单位净值',
    shares DECIMAL(18,6) DEFAULT NULL COMMENT '已确认份额',
    `value` DECIMAL(18,4) DEFAULT NULL COMMENT '持仓市值',
    pnl_day DECIMAL(18,4) DEFAULT NULL COMMENT '日盈亏',
    cost DECIMAL(18,4) DEFAULT NULL COMMENT '持仓成本',
    unrealized_pnl DECIMAL(18,4) DEFAULT NULL COMMENT '浮动盈亏',
    return_rate DECIMAL(10,6) DEFAULT NULL COMMENT '持仓收益率',
    cash_in_transit DECIMAL(18,4) DEFAULT NULL COMMENT '在途资金',
    total_value DECIMAL(18,4) DEFAULT NULL COMMENT '产品总资产',
    principal_total DECIMAL(18,4) DEFAULT NULL COMMENT '累计本金',
    total_redemption DECIMAL(18,4) DEFAULT NULL COMMENT '累计赎回',
    total_pnl DECIMAL(18,4) DEFAULT NULL COMMENT '总盈亏',
    real_return DECIMAL(10,6) DEFAULT NULL COMMENT '真实收益率',
    fetched_at VARCHAR(30) DEFAULT NULL COMMENT '采集时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_product (fetch_date, product_code),
    INDEX idx_fetch_date (fetch_date),
    INDEX idx_product (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日持仓快照表';


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
    balance DECIMAL(18,4) DEFAULT NULL COMMENT '账户余额',
    related_product VARCHAR(20) DEFAULT NULL COMMENT '关联产品代码',
    product_value DECIMAL(18,4) DEFAULT NULL COMMENT '产品市值',
    diff DECIMAL(18,4) DEFAULT NULL COMMENT '差异/收益',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_account (fetch_date, account_id),
    INDEX idx_fetch_date (fetch_date),
    INDEX idx_account_type (account_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户余额快照表';


-- ============================================================
-- 6. 净值表 (nav) - 合并所有产品的净值数据
-- ============================================================
DROP TABLE IF EXISTS nav;
CREATE TABLE nav (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(20) NOT NULL COMMENT '产品代码',
    nav_date DATE NOT NULL COMMENT '净值日期',
    nav DECIMAL(10,6) NOT NULL COMMENT '单位净值',
    acc_nav DECIMAL(10,6) DEFAULT NULL COMMENT '累计净值',
    daily_return DECIMAL(10,6) DEFAULT NULL COMMENT '日涨跌幅',
    dividend DECIMAL(10,6) DEFAULT NULL COMMENT '分红',
    fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间',
    UNIQUE KEY uk_product_date (product_code, nav_date),
    INDEX idx_nav_date (nav_date),
    INDEX idx_product (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='净值表';


-- ============================================================
-- 7. 产品配置表 (products) - 可选，从 JSON 迁移
-- ============================================================
DROP TABLE IF EXISTS products;
CREATE TABLE products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(20) NOT NULL UNIQUE COMMENT '产品代码',
    product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
    category VARCHAR(20) NOT NULL COMMENT '分类: fund/bank',
    risk_level VARCHAR(20) DEFAULT NULL COMMENT '风险等级',
    buy_confirm_days INT DEFAULT 1 COMMENT '申购确认天数',
    sell_confirm_days INT DEFAULT 1 COMMENT '赎回确认天数',
    min_buy DECIMAL(18,4) DEFAULT NULL COMMENT '最低申购金额',
    fee_rate DECIMAL(10,6) DEFAULT NULL COMMENT '申购费率',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品配置表';


-- ============================================================
-- 8. 账户配置表 (accounts) - 可选，从 JSON 迁移
-- ============================================================
DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL UNIQUE COMMENT '账户ID',
    account_name VARCHAR(100) NOT NULL COMMENT '账户名称',
    account_type VARCHAR(30) NOT NULL COMMENT '账户类型',
    related_product VARCHAR(20) DEFAULT NULL COMMENT '关联产品代码',
    parent_account VARCHAR(50) DEFAULT NULL COMMENT '父账户ID',
    receives_profit TINYINT(1) DEFAULT 0 COMMENT '是否接收收益分配',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户配置表';


-- 显示创建的表
SHOW TABLES;

