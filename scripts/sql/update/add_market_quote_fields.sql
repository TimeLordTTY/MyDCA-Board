-- ============================================================
-- MyDCA-Board 场内行情表字段扩展迁移脚本
-- 添加 IOPV、溢价率、开盘价、最高价、最低价、换手率等字段
-- 执行前请备份数据库！
-- ============================================================

USE dca;

-- ============================================================
-- 扩展 market_quote_rt 表，添加核心字段
-- ============================================================

-- 添加 IOPV 实时估值字段
ALTER TABLE market_quote_rt 
ADD COLUMN iopv DECIMAL(18,6) NULL COMMENT 'IOPV实时估值（基金份额参考净值）' AFTER amount;

-- 添加溢价率字段
ALTER TABLE market_quote_rt 
ADD COLUMN premium_rate DECIMAL(10,6) NULL COMMENT '溢价率（小数，如 0.0123 表示 1.23%）' AFTER iopv;

-- 添加基础价格时间序列字段
ALTER TABLE market_quote_rt 
ADD COLUMN open_price DECIMAL(18,6) NULL COMMENT '开盘价' AFTER premium_rate;

ALTER TABLE market_quote_rt 
ADD COLUMN high_price DECIMAL(18,6) NULL COMMENT '最高价' AFTER open_price;

ALTER TABLE market_quote_rt 
ADD COLUMN low_price DECIMAL(18,6) NULL COMMENT '最低价' AFTER high_price;

-- 添加流动性指标字段
ALTER TABLE market_quote_rt 
ADD COLUMN turnover_rate DECIMAL(10,6) NULL COMMENT '换手率（小数，如 0.0188 表示 1.88%）' AFTER low_price;

-- 添加振幅字段
ALTER TABLE market_quote_rt 
ADD COLUMN amplitude DECIMAL(10,6) NULL COMMENT '振幅（小数，如 0.0072 表示 0.72%）' AFTER turnover_rate;

-- 添加索引（如果需要按溢价率查询）
CREATE INDEX idx_premium_rate ON market_quote_rt(premium_rate);

-- 完成
SELECT 'market_quote_rt 表字段扩展完成！' AS result;

