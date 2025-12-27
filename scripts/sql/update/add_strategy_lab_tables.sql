-- ============================================================
-- Strategy Lab 回测引擎相关表
-- ============================================================

-- 1. 策略配置表 (strategy_config)
DROP TABLE IF EXISTS strategy_config;
CREATE TABLE strategy_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    strategy_key VARCHAR(64) NOT NULL COMMENT '策略标识',
    strategy_version VARCHAR(32) DEFAULT 'default' COMMENT '策略版本',
    param_set_id VARCHAR(64) NOT NULL COMMENT '参数组合ID',
    param_json TEXT NOT NULL COMMENT '参数JSON',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_strategy_param (strategy_key, strategy_version, param_set_id),
    INDEX idx_strategy_key (strategy_key),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='策略配置表';


-- 2. 回测汇总表 (backtest_summary)
DROP TABLE IF EXISTS backtest_summary;
CREATE TABLE backtest_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '产品ID',
    strategy_key VARCHAR(64) NOT NULL COMMENT '策略标识',
    strategy_version VARCHAR(32) DEFAULT 'default' COMMENT '策略版本',
    param_set_id VARCHAR(64) NOT NULL COMMENT '参数组合ID',
    start_date DATE NOT NULL COMMENT '回测开始日期',
    end_date DATE NOT NULL COMMENT '回测结束日期',
    initial_cash DECIMAL(20,2) NOT NULL COMMENT '初始现金',
    final_value DECIMAL(20,2) NOT NULL COMMENT '最终总资产',
    total_return DECIMAL(10,6) NOT NULL COMMENT '总收益率',
    annual_return DECIMAL(10,6) NOT NULL COMMENT '年化收益率',
    max_drawdown DECIMAL(10,6) NOT NULL COMMENT '最大回撤',
    trade_count INT NOT NULL COMMENT '成交次数',
    total_fees DECIMAL(20,2) NOT NULL COMMENT '手续费总额',
    fee_ratio DECIMAL(10,6) NOT NULL COMMENT '手续费占收益比例',
    wait_pool_ratio DECIMAL(10,6) NOT NULL COMMENT 'wait_pool滞留比例',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product (product_id),
    INDEX idx_strategy (strategy_key, strategy_version),
    INDEX idx_param_set (param_set_id),
    INDEX idx_start_date (start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='回测汇总表';


-- 3. 回测每日数据表 (backtest_daily)
DROP TABLE IF EXISTS backtest_daily;
CREATE TABLE backtest_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    summary_id BIGINT NOT NULL COMMENT '关联backtest_summary.id',
    trade_date DATE NOT NULL COMMENT '交易日期',
    nav DECIMAL(18,6) NOT NULL COMMENT '净值/收盘价',
    cash_pool DECIMAL(20,2) NOT NULL COMMENT '可用现金池',
    wait_pool DECIMAL(20,2) NOT NULL COMMENT '等待池',
    holdings_value DECIMAL(20,2) NOT NULL COMMENT '持仓市值',
    total_value DECIMAL(20,2) NOT NULL COMMENT '总资产',
    drawdown DECIMAL(10,6) NOT NULL COMMENT '当前回撤',
    fee_cum DECIMAL(20,2) NOT NULL COMMENT '累计手续费',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_summary (summary_id),
    INDEX idx_trade_date (trade_date),
    UNIQUE KEY uk_summary_date (summary_id, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='回测每日数据表';


-- 4. 回测成交表 (backtest_trades)
DROP TABLE IF EXISTS backtest_trades;
CREATE TABLE backtest_trades (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    summary_id BIGINT NOT NULL COMMENT '关联backtest_summary.id',
    trade_date DATE NOT NULL COMMENT '成交日期',
    side ENUM('BUY','SELL') NOT NULL COMMENT '买卖方向',
    amount DECIMAL(20,2) NOT NULL COMMENT '成交金额',
    price DECIMAL(18,6) NOT NULL COMMENT '成交价格',
    shares DECIMAL(20,6) NOT NULL COMMENT '成交份额',
    fee DECIMAL(20,2) NOT NULL COMMENT '手续费',
    reasons TEXT COMMENT '成交原因（JSON数组）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_summary (summary_id),
    INDEX idx_trade_date (trade_date),
    INDEX idx_side (side)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='回测成交表';




