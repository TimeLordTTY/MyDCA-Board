/*
 Navicat Premium Dump SQL

 Source Server         : 124.220.229.91-dca
 Source Server Type    : MySQL
 Source Server Version : 50744 (5.7.44-log)
 Source Host           : 124.220.229.91:9009
 Source Schema         : dca

 Target Server Type    : MySQL
 Target Server Version : 50744 (5.7.44-log)
 File Encoding         : 65001

 Date: 27/12/2025 11:34:29
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for account_groups
-- ----------------------------
DROP TABLE IF EXISTS `account_groups`;
CREATE TABLE `account_groups`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '组代码（如 wenlibao/ylb）',
  `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '组名称',
  `linked_product_id` bigint(20) NULL DEFAULT NULL COMMENT '关联产品ID（如稳利宝）',
  `profit_account_id` bigint(20) NULL DEFAULT NULL COMMENT '收益归属账户ID',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `group_code`(`group_code`) USING BTREE,
  INDEX `idx_linked_product`(`linked_product_id`) USING BTREE,
  INDEX `idx_profit_account`(`profit_account_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '账户组配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for account_pool_rules
-- ----------------------------
DROP TABLE IF EXISTS `account_pool_rules`;
CREATE TABLE `account_pool_rules`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `from_account_id` bigint(20) NOT NULL COMMENT '来源账户ID（如余利宝理财金）',
  `to_product_id` bigint(20) NOT NULL COMMENT '目标产品ID（基金/ETF/LOF）',
  `ratio` decimal(10, 6) NOT NULL COMMENT '分配比例（如 0.35 表示 35%）',
  `min_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '最小分配金额',
  `round_step` decimal(18, 2) NULL DEFAULT 1.00 COMMENT '取整粒度（如 1/10/100）',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_pool_account_product`(`from_account_id`, `to_product_id`) USING BTREE,
  INDEX `idx_from_account`(`from_account_id`) USING BTREE,
  INDEX `idx_to_product`(`to_product_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '资金池分配规则表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for accounts
-- ----------------------------
DROP TABLE IF EXISTS `accounts`;
CREATE TABLE `accounts`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '账户代码（唯一标识）',
  `account_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '账户ID（兼容字段，等于account_code）',
  `account_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '账户名称',
  `account_type` enum('CASH','BUCKET','FUND_MAPPED','PRODUCT_SUB','FUND_TOTAL','SUMMARY') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '账户类型',
  `parent_account_id` bigint(20) NULL DEFAULT NULL COMMENT '父账户ID（桶挂在大账户下）',
  `product_id` bigint(20) NULL DEFAULT NULL COMMENT '账户背后绑定的产品ID',
  `currency` enum('CNY','USD','HKD') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'CNY' COMMENT '货币类型',
  `note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `account_code`(`account_code`) USING BTREE,
  INDEX `idx_account_type`(`account_type`) USING BTREE,
  INDEX `idx_parent_account`(`parent_account_id`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE,
  INDEX `idx_is_active`(`is_active`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 16 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '账户配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for backtest_daily
-- ----------------------------
DROP TABLE IF EXISTS `backtest_daily`;
CREATE TABLE `backtest_daily`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `summary_id` bigint(20) NOT NULL COMMENT '关联backtest_summary.id',
  `trade_date` date NOT NULL COMMENT '交易日期',
  `nav` decimal(18, 6) NOT NULL COMMENT '净值/收盘价',
  `cash_pool` decimal(20, 2) NOT NULL COMMENT '可用现金池',
  `wait_pool` decimal(20, 2) NOT NULL COMMENT '等待池',
  `holdings_value` decimal(20, 2) NOT NULL COMMENT '持仓市值',
  `total_value` decimal(20, 2) NOT NULL COMMENT '总资产',
  `drawdown` decimal(10, 6) NOT NULL COMMENT '当前回撤',
  `fee_cum` decimal(20, 2) NOT NULL COMMENT '累计手续费',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_summary_date`(`summary_id`, `trade_date`) USING BTREE,
  INDEX `idx_summary`(`summary_id`) USING BTREE,
  INDEX `idx_trade_date`(`trade_date`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 163364 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '回测每日数据表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for backtest_summary
-- ----------------------------
DROP TABLE IF EXISTS `backtest_summary`;
CREATE TABLE `backtest_summary`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL COMMENT '产品ID',
  `strategy_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '策略标识',
  `strategy_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'default' COMMENT '策略版本',
  `param_set_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '参数组合ID',
  `start_date` date NOT NULL COMMENT '回测开始日期',
  `end_date` date NOT NULL COMMENT '回测结束日期',
  `initial_cash` decimal(20, 2) NOT NULL COMMENT '初始现金',
  `final_value` decimal(20, 2) NOT NULL COMMENT '最终总资产',
  `total_return` decimal(10, 6) NOT NULL COMMENT '总收益率',
  `annual_return` decimal(10, 6) NOT NULL COMMENT '年化收益率',
  `max_drawdown` decimal(10, 6) NOT NULL COMMENT '最大回撤',
  `trade_count` int(11) NOT NULL COMMENT '成交次数',
  `total_fees` decimal(20, 2) NOT NULL COMMENT '手续费总额',
  `fee_ratio` decimal(10, 6) NOT NULL COMMENT '手续费占收益比例',
  `wait_pool_ratio` decimal(10, 6) NOT NULL COMMENT 'wait_pool滞留比例',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_product`(`product_id`) USING BTREE,
  INDEX `idx_strategy`(`strategy_key`, `strategy_version`) USING BTREE,
  INDEX `idx_param_set`(`param_set_id`) USING BTREE,
  INDEX `idx_start_date`(`start_date`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 86 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '回测汇总表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for backtest_trades
-- ----------------------------
DROP TABLE IF EXISTS `backtest_trades`;
CREATE TABLE `backtest_trades`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `summary_id` bigint(20) NOT NULL COMMENT '关联backtest_summary.id',
  `trade_date` date NOT NULL COMMENT '成交日期',
  `side` enum('BUY','SELL') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '买卖方向',
  `amount` decimal(20, 2) NOT NULL COMMENT '成交金额',
  `price` decimal(18, 6) NOT NULL COMMENT '成交价格',
  `shares` decimal(20, 6) NOT NULL COMMENT '成交份额',
  `fee` decimal(20, 2) NOT NULL COMMENT '手续费',
  `reasons` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '成交原因（JSON数组）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_summary`(`summary_id`) USING BTREE,
  INDEX `idx_trade_date`(`trade_date`) USING BTREE,
  INDEX `idx_side`(`side`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5391 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '回测成交表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for categories
-- ----------------------------
DROP TABLE IF EXISTS `categories`;
CREATE TABLE `categories`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `entry_type` enum('expense','income') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '记账类型',
  `category_l1` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '一级分类',
  `category_l2` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '二级分类（可为空）',
  `display_order` int(11) NULL DEFAULT 0 COMMENT '显示顺序',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_category`(`entry_type`, `category_l1`, `category_l2`) USING BTREE,
  INDEX `idx_entry_type`(`entry_type`) USING BTREE,
  INDEX `idx_category_l1`(`category_l1`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 70 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '分类配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for daily_balance
-- ----------------------------
DROP TABLE IF EXISTS `daily_balance`;
CREATE TABLE `daily_balance`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `fetch_date` date NOT NULL,
  `account_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `account_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `account_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `balance` decimal(20, 2) NULL DEFAULT NULL COMMENT '账户余额',
  `related_product` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `product_value` decimal(20, 2) NULL DEFAULT NULL COMMENT '产品市值',
  `diff` decimal(20, 2) NULL DEFAULT NULL COMMENT '差异/收益',
  `note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_date_account`(`fetch_date`, `account_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1700 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for daily_snapshot
-- ----------------------------
DROP TABLE IF EXISTS `daily_snapshot`;
CREATE TABLE `daily_snapshot`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NULL DEFAULT NULL COMMENT '产品ID（外键）',
  `fetch_date` date NOT NULL,
  `product_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `product_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `category` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `nav_date` date NULL DEFAULT NULL,
  `nav` decimal(20, 6) NULL DEFAULT NULL COMMENT '单位净值',
  `shares` decimal(20, 6) NULL DEFAULT NULL COMMENT '已确认份额',
  `value` decimal(20, 2) NULL DEFAULT NULL COMMENT '持仓市值',
  `pnl_day` decimal(20, 2) NULL DEFAULT NULL COMMENT '日盈亏',
  `cost` decimal(20, 2) NULL DEFAULT NULL COMMENT '持仓成本',
  `unrealized_pnl` decimal(20, 2) NULL DEFAULT NULL COMMENT '浮动盈亏',
  `return_rate` decimal(10, 6) NULL DEFAULT NULL,
  `cash_in_transit` decimal(20, 2) NULL DEFAULT NULL COMMENT '在途资金',
  `total_value` decimal(20, 2) NULL DEFAULT NULL COMMENT '产品总资产',
  `principal_total` decimal(20, 2) NULL DEFAULT NULL COMMENT '累计本金',
  `total_redemption` decimal(20, 2) NULL DEFAULT NULL COMMENT '累计赎回',
  `total_pnl` decimal(20, 2) NULL DEFAULT NULL COMMENT '总盈亏',
  `real_return` decimal(10, 6) NULL DEFAULT NULL,
  `data_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ok' COMMENT '数据状态: ok/carried_forward/missing/holiday',
  `fetched_at` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_date_product`(`fetch_date`, `product_code`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2882 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for dca_plan
-- ----------------------------
DROP TABLE IF EXISTS `dca_plan`;
CREATE TABLE `dca_plan`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL COMMENT '产品ID',
  `from_account_id` bigint(20) NOT NULL COMMENT '来源账户ID',
  `weekday` enum('MON','TUE','WED','THU','FRI','SAT','SUN') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '定投日期（星期几）',
  `amount` decimal(18, 2) NOT NULL COMMENT '定投金额',
  `enabled` tinyint(1) NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE,
  INDEX `idx_from_account`(`from_account_id`) USING BTREE,
  INDEX `idx_enabled`(`enabled`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '定投计划表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for job_config
-- ----------------------------
DROP TABLE IF EXISTS `job_config`;
CREATE TABLE `job_config`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `job_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务代码',
  `cron_expr` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Cron表达式',
  `enabled` tinyint(1) NULL DEFAULT 1 COMMENT '是否启用',
  `last_run_at` datetime NULL DEFAULT NULL COMMENT '最后执行时间',
  `last_status` enum('OK','FAIL') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '最后执行状态',
  `last_message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '最后执行消息',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `job_code`(`job_code`) USING BTREE,
  INDEX `idx_enabled`(`enabled`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '任务调度配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ledger
-- ----------------------------
DROP TABLE IF EXISTS `ledger`;
CREATE TABLE `ledger`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `event_time` datetime NOT NULL,
  `entry_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `amount` decimal(20, 2) NOT NULL COMMENT '金额',
  `category_l1` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `category_l2` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `account_from` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `account_to` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `discount` decimal(20, 2) NULL DEFAULT NULL COMMENT '折扣/优惠',
  `reimbursable` tinyint(1) NULL DEFAULT 0,
  `note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_event_time`(`event_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 82 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for market_bar_d
-- ----------------------------
DROP TABLE IF EXISTS `market_bar_d`;
CREATE TABLE `market_bar_d`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL COMMENT '产品ID',
  `trade_date` date NOT NULL COMMENT '交易日期',
  `open_price` decimal(18, 6) NULL DEFAULT NULL COMMENT '开盘价',
  `high_price` decimal(18, 6) NULL DEFAULT NULL COMMENT '最高价',
  `low_price` decimal(18, 6) NULL DEFAULT NULL COMMENT '最低价',
  `close_price` decimal(18, 6) NOT NULL COMMENT '收盘价',
  `volume` decimal(20, 2) NULL DEFAULT NULL COMMENT '成交量',
  `amount` decimal(20, 2) NULL DEFAULT NULL COMMENT '成交额',
  `prev_close` decimal(18, 6) NULL DEFAULT NULL COMMENT '昨收价',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'AKSHARE' COMMENT '数据源',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_bar_product_date_source`(`product_id`, `trade_date`, `source`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE,
  INDEX `idx_trade_date`(`trade_date`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12725 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '场内日K线表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for market_quote_rt
-- ----------------------------
DROP TABLE IF EXISTS `market_quote_rt`;
CREATE TABLE `market_quote_rt`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL COMMENT '产品ID',
  `quote_time` datetime NOT NULL COMMENT '行情时间（精确到秒）',
  `price` decimal(18, 6) NOT NULL COMMENT '当前价格',
  `prev_close` decimal(18, 6) NULL DEFAULT NULL COMMENT '昨收价',
  `pct_chg` decimal(10, 6) NULL DEFAULT NULL COMMENT '涨跌幅（%）',
  `volume` decimal(20, 2) NULL DEFAULT NULL COMMENT '成交量',
  `amount` decimal(20, 2) NULL DEFAULT NULL COMMENT '成交额',
  `iopv` decimal(18, 6) NULL DEFAULT NULL COMMENT 'IOPV实时估值（基金份额参考净值）',
  `premium_rate` decimal(10, 6) NULL DEFAULT NULL COMMENT '溢价率（小数，如 0.0123 表示 1.23%）',
  `open_price` decimal(18, 6) NULL DEFAULT NULL COMMENT '开盘价',
  `high_price` decimal(18, 6) NULL DEFAULT NULL COMMENT '最高价',
  `low_price` decimal(18, 6) NULL DEFAULT NULL COMMENT '最低价',
  `turnover_rate` decimal(10, 6) NULL DEFAULT NULL COMMENT '换手率（小数，如 0.0188 表示 1.88%）',
  `amplitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '振幅（小数，如 0.0072 表示 0.72%）',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'AKSHARE' COMMENT '数据源',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_rt_product_time_source`(`product_id`, `quote_time`, `source`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE,
  INDEX `idx_quote_time`(`quote_time`) USING BTREE,
  INDEX `idx_premium_rate`(`premium_rate`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10018 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '场内实时行情表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for nav
-- ----------------------------
DROP TABLE IF EXISTS `nav`;
CREATE TABLE `nav`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NULL DEFAULT NULL COMMENT '产品ID（外键）',
  `product_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `nav_date` date NOT NULL,
  `nav` decimal(20, 6) NOT NULL COMMENT '单位净值',
  `acc_nav` decimal(20, 6) NULL DEFAULT NULL COMMENT '累计净值',
  `daily_return` decimal(10, 6) NULL DEFAULT NULL,
  `dividend` decimal(20, 2) NULL DEFAULT NULL COMMENT '分红',
  `fetched_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_product_date`(`product_code`, `nav_date`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 21563 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for orders
-- ----------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NULL DEFAULT NULL COMMENT '产品ID（外键）',
  `order_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `product_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `order_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `amount` decimal(20, 2) NULL DEFAULT NULL COMMENT '金额',
  `fee` decimal(20, 2) NULL DEFAULT NULL COMMENT '手续费',
  `shares` decimal(20, 6) NULL DEFAULT NULL COMMENT '份额（赎回时）',
  `requested_at` datetime NOT NULL,
  `trade_date` date NULL DEFAULT NULL,
  `nav_date` date NULL DEFAULT NULL,
  `confirm_date` date NULL DEFAULT NULL,
  `holding_days` int(11) NULL DEFAULT NULL,
  `sell_fee_rate` decimal(10, 6) NULL DEFAULT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'pending',
  `note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `order_id`(`order_id`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 32 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pending_buy_pool
-- ----------------------------
DROP TABLE IF EXISTS `pending_buy_pool`;
CREATE TABLE `pending_buy_pool`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL COMMENT '产品ID',
  `from_account_id` bigint(20) NOT NULL COMMENT '来源账户ID',
  `pending_amount` decimal(18, 2) NOT NULL DEFAULT 0.00 COMMENT '待买入金额（累加）',
  `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '扣留原因（溢价刹车等）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_pool_product_account`(`product_id`, `from_account_id`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE,
  INDEX `idx_from_account`(`from_account_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '待买入池表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for product_nav_range
-- ----------------------------
DROP TABLE IF EXISTS `product_nav_range`;
CREATE TABLE `product_nav_range`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品代码',
  `product_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品名称',
  `earliest_nav_date` date NULL DEFAULT NULL COMMENT '最早净值日期',
  `latest_nav_date` date NULL DEFAULT NULL COMMENT '最新净值日期',
  `record_count` int(11) NULL DEFAULT 0 COMMENT '记录数',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_product_code`(`product_code`) USING BTREE,
  INDEX `idx_earliest_date`(`earliest_nav_date`) USING BTREE,
  INDEX `idx_latest_date`(`latest_nav_date`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 22 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '产品净值范围表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for products
-- ----------------------------
DROP TABLE IF EXISTS `products`;
CREATE TABLE `products`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '交易代码/基金代码',
  `channel` enum('EXCHANGE','OTC') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'OTC' COMMENT '场内/场外',
  `market` enum('SH','SZ','NA') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'NA' COMMENT '市场类型: SH/SZ/NA',
  `asset_type` enum('ETF','LOF','FUND','MMF','BANK_WM_NAV','BANK_WM_BOX') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'FUND' COMMENT '资产类型',
  `currency` enum('CNY','USD','HKD') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'CNY' COMMENT '货币类型',
  `is_qdii` tinyint(1) NULL DEFAULT 0 COMMENT '是否QDII',
  `track_index` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '跟踪指数',
  `product_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '产品名称',
  `category` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'fund' COMMENT '分类: fund/bank',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '数据源 (fund/cmbc/akshare)',
  `buy_fee_rate` decimal(10, 6) NULL DEFAULT 0.000000 COMMENT '申购费率',
  `sell_fee_rate` decimal(10, 6) NULL DEFAULT 0.000000 COMMENT '赎回费率',
  `buy_confirm_offset` int(11) NULL DEFAULT 1 COMMENT '买入确认延迟交易日数',
  `sell_confirm_offset` int(11) NULL DEFAULT 1 COMMENT '赎回确认延迟交易日数',
  `cutoff_time` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '15:00' COMMENT '交易截止时间',
  `product_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品代码（兼容字段，等于code）',
  `note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_prod_code_channel_market`(`code`, `channel`, `market`) USING BTREE,
  INDEX `idx_code`(`code`) USING BTREE,
  INDEX `idx_channel`(`channel`) USING BTREE,
  INDEX `idx_asset_type`(`asset_type`) USING BTREE,
  INDEX `idx_is_active`(`is_active`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '产品配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for qdii_premium_rt
-- ----------------------------
DROP TABLE IF EXISTS `qdii_premium_rt`;
CREATE TABLE `qdii_premium_rt`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL COMMENT '产品ID',
  `quote_time` datetime NOT NULL COMMENT '行情时间',
  `iopv` decimal(18, 6) NULL DEFAULT NULL COMMENT 'IOPV（基金份额参考净值）',
  `premium_rate` decimal(10, 6) NOT NULL COMMENT '溢价率（如 0.0123 表示 1.23%）',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'AKSHARE' COMMENT '数据源',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_prem_product_time_source`(`product_id`, `quote_time`, `source`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE,
  INDEX `idx_quote_time`(`quote_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5960 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'QDII溢价率表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for strategy_config
-- ----------------------------
DROP TABLE IF EXISTS `strategy_config`;
CREATE TABLE `strategy_config`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `strategy_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '策略标识',
  `strategy_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'default' COMMENT '策略版本',
  `param_set_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '参数组合ID',
  `param_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '参数JSON',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_strategy_param`(`strategy_key`, `strategy_version`, `param_set_id`) USING BTREE,
  INDEX `idx_strategy_key`(`strategy_key`) USING BTREE,
  INDEX `idx_is_active`(`is_active`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 82 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '策略配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for task_dca
-- ----------------------------
DROP TABLE IF EXISTS `task_dca`;
CREATE TABLE `task_dca`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `plan_id` bigint(20) NULL DEFAULT NULL COMMENT '关联计划ID（可为空，手动任务）',
  `task_date` date NOT NULL COMMENT '任务日期',
  `product_id` bigint(20) NOT NULL COMMENT '产品ID',
  `from_account_id` bigint(20) NOT NULL COMMENT '来源账户ID',
  `planned_amount` decimal(18, 2) NOT NULL COMMENT '计划金额',
  `premium_rate` decimal(10, 6) NULL DEFAULT NULL COMMENT '溢价率（QDII）',
  `executed_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '执行金额（实际买入）',
  `pending_amount` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '待买入金额（溢价刹车扣留）',
  `status` enum('PENDING','MATCH','PARTIAL','MISS') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '对账状态',
  `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '原因说明',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_date_product`(`task_date`, `product_id`, `from_account_id`) USING BTREE,
  INDEX `idx_task_date`(`task_date`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '定投任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for trade_fills
-- ----------------------------
DROP TABLE IF EXISTS `trade_fills`;
CREATE TABLE `trade_fills`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `trade_date` date NOT NULL COMMENT '成交日期',
  `trade_time` datetime NOT NULL COMMENT '成交时间（精确到秒）',
  `product_id` bigint(20) NOT NULL COMMENT '产品ID',
  `side` enum('BUY','SELL') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '买卖方向',
  `qty` decimal(18, 6) NOT NULL COMMENT '成交数量（份额/股数）',
  `price` decimal(18, 6) NOT NULL COMMENT '成交价',
  `amount` decimal(18, 2) NOT NULL COMMENT '成交金额（含费）',
  `fee` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '手续费（佣金等）',
  `tax` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '印花税（ETF通常0）',
  `other_fee` decimal(18, 2) NULL DEFAULT 0.00 COMMENT '其他费用',
  `broker_order_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '券商订单号（用于去重）',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `source` enum('IMPORT','MANUAL') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_fill_source_order`(`source`, `broker_order_id`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE,
  INDEX `idx_trade_date`(`trade_date`) USING BTREE,
  INDEX `idx_side`(`side`) USING BTREE,
  INDEX `idx_broker_order_id`(`broker_order_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '场内成交流水表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for transactions
-- ----------------------------
DROP TABLE IF EXISTS `transactions`;
CREATE TABLE `transactions`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NULL DEFAULT NULL COMMENT '产品ID（外键）',
  `date` date NOT NULL,
  `product_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `action` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `amount` decimal(20, 2) NULL DEFAULT NULL COMMENT '交易金额',
  `shares` decimal(20, 6) NULL DEFAULT NULL COMMENT '份额',
  `fee` decimal(20, 2) NULL DEFAULT NULL COMMENT '手续费',
  `nav` decimal(20, 6) NULL DEFAULT NULL COMMENT '单位净值',
  `nav_date` date NULL DEFAULT NULL,
  `order_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_date`(`date`) USING BTREE,
  INDEX `idx_product`(`product_code`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 307 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
