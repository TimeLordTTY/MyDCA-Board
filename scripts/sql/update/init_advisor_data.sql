-- ============================================================
-- Advisor 生产建议层初始化数据
-- ============================================================

USE dca;

-- ============================================================
-- 1. 插入6支场内产品（如果不存在）
-- ============================================================

-- 513180 - 红利低波ETF
INSERT INTO products (code, channel, market, asset_type, currency, is_qdii, product_name, category, source, is_active)
SELECT '513180', 'EXCHANGE', 'SH', 'ETF', 'CNY', 0, '红利低波ETF', 'fund', 'akshare', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = '513180' AND channel = 'EXCHANGE' AND market = 'SH');

-- 513100 - 纳斯达克100ETF
INSERT INTO products (code, channel, market, asset_type, currency, is_qdii, product_name, category, source, is_active)
SELECT '513100', 'EXCHANGE', 'SH', 'ETF', 'CNY', 1, '纳斯达克100ETF', 'fund', 'akshare', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = '513100' AND channel = 'EXCHANGE' AND market = 'SH');

-- 513500 - 标普500ETF
INSERT INTO products (code, channel, market, asset_type, currency, is_qdii, product_name, category, source, is_active)
SELECT '513500', 'EXCHANGE', 'SH', 'ETF', 'CNY', 1, '标普500ETF', 'fund', 'akshare', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = '513500' AND channel = 'EXCHANGE' AND market = 'SH');

-- 515450 - 红利低波ETF（另一个）
INSERT INTO products (code, channel, market, asset_type, currency, is_qdii, product_name, category, source, is_active)
SELECT '515450', 'EXCHANGE', 'SH', 'ETF', 'CNY', 0, '红利低波ETF', 'fund', 'akshare', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = '515450' AND channel = 'EXCHANGE' AND market = 'SH');

-- 518880 - 黄金ETF
INSERT INTO products (code, channel, market, asset_type, currency, is_qdii, product_name, category, source, is_active)
SELECT '518880', 'EXCHANGE', 'SH', 'ETF', 'CNY', 0, '黄金ETF', 'fund', 'akshare', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = '518880' AND channel = 'EXCHANGE' AND market = 'SH');

-- 163406 - 兴全合润LOF（场内）
INSERT INTO products (code, channel, market, asset_type, currency, is_qdii, product_name, category, source, is_active)
SELECT '163406', 'EXCHANGE', 'SZ', 'LOF', 'CNY', 0, '兴全合润LOF', 'fund', 'akshare', 1
WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = '163406' AND channel = 'EXCHANGE' AND market = 'SZ');

-- ============================================================
-- 2. 插入四策略的默认参数集（如果不存在）
-- ============================================================

-- percentile@default
INSERT INTO strategy_config (strategy_key, strategy_version, param_set_id, param_json, is_active)
SELECT 'percentile', 'default', 'default', '{"window_days": 750, "buy_percentile": 0.20, "max_buy_per_day": 2000}', 1
WHERE NOT EXISTS (SELECT 1 FROM strategy_config WHERE strategy_key = 'percentile' AND strategy_version = 'default' AND param_set_id = 'default');

-- drawdown@default
INSERT INTO strategy_config (strategy_key, strategy_version, param_set_id, param_json, is_active)
SELECT 'drawdown', 'default', 'default', '{"window_days": 750, "levels": [0.02, 0.04, 0.08], "buy_amounts": [1000, 1500, 2000]}', 1
WHERE NOT EXISTS (SELECT 1 FROM strategy_config WHERE strategy_key = 'drawdown' AND strategy_version = 'default' AND param_set_id = 'default');

-- profit_recycle@v11
INSERT INTO strategy_config (strategy_key, strategy_version, param_set_id, param_json, is_active)
SELECT 'profit_recycle', 'v11', 'v11', '{"ma_window": 250, "high_bias": 0.20, "lock_ratio_low": 0.00, "lock_ratio_mid": 0.05, "lock_ratio_high": 0.20, "deep_dip_levels": [{"threshold": -0.10, "use_ratio": 0.50}, {"threshold": -0.15, "use_ratio": 1.00}], "take_profit_enabled": true, "take_profit_bias": 0.18, "take_profit_sell_ratio": 0.05, "take_profit_cooldown_days": 60, "near_peak_ratio": 0.98, "allow_multi_deep_dip": true, "rebound_reset_rate": 0.05, "debounce_days": 30}', 1
WHERE NOT EXISTS (SELECT 1 FROM strategy_config WHERE strategy_key = 'profit_recycle' AND strategy_version = 'v11' AND param_set_id = 'v11');

-- simple@default
INSERT INTO strategy_config (strategy_key, strategy_version, param_set_id, param_json, is_active)
SELECT 'simple', 'default', 'default', '{"max_buy_per_day": 2000}', 1
WHERE NOT EXISTS (SELECT 1 FROM strategy_config WHERE strategy_key = 'simple' AND strategy_version = 'default' AND param_set_id = 'default');

-- ============================================================
-- 3. 为6支产品建立默认策略绑定（如果不存在）
-- ============================================================

-- 获取产品ID（使用子查询）
INSERT INTO product_strategy_bind (product_id, strategy_code, param_set_id, enabled, min_trade_amount, ideal_trade_amount, fee_rate, fee_min)
SELECT p.id, 'percentile', 'default', 1, 1000.00, 2000.00, 0.000845, 0.20
FROM products p
WHERE p.code = '513180' AND p.channel = 'EXCHANGE' AND p.market = 'SH'
  AND NOT EXISTS (SELECT 1 FROM product_strategy_bind WHERE product_id = p.id);

INSERT INTO product_strategy_bind (product_id, strategy_code, param_set_id, enabled, min_trade_amount, ideal_trade_amount, fee_rate, fee_min)
SELECT p.id, 'percentile', 'default', 1, 1000.00, 2000.00, 0.000845, 0.20
FROM products p
WHERE p.code = '513100' AND p.channel = 'EXCHANGE' AND p.market = 'SH'
  AND NOT EXISTS (SELECT 1 FROM product_strategy_bind WHERE product_id = p.id);

INSERT INTO product_strategy_bind (product_id, strategy_code, param_set_id, enabled, min_trade_amount, ideal_trade_amount, fee_rate, fee_min)
SELECT p.id, 'percentile', 'default', 1, 1000.00, 2000.00, 0.000845, 0.20
FROM products p
WHERE p.code = '513500' AND p.channel = 'EXCHANGE' AND p.market = 'SH'
  AND NOT EXISTS (SELECT 1 FROM product_strategy_bind WHERE product_id = p.id);

INSERT INTO product_strategy_bind (product_id, strategy_code, param_set_id, enabled, min_trade_amount, ideal_trade_amount, fee_rate, fee_min)
SELECT p.id, 'percentile', 'default', 1, 1000.00, 2000.00, 0.000845, 0.20
FROM products p
WHERE p.code = '515450' AND p.channel = 'EXCHANGE' AND p.market = 'SH'
  AND NOT EXISTS (SELECT 1 FROM product_strategy_bind WHERE product_id = p.id);

INSERT INTO product_strategy_bind (product_id, strategy_code, param_set_id, enabled, min_trade_amount, ideal_trade_amount, fee_rate, fee_min)
SELECT p.id, 'percentile', 'default', 1, 1000.00, 2000.00, 0.000845, 0.20
FROM products p
WHERE p.code = '518880' AND p.channel = 'EXCHANGE' AND p.market = 'SH'
  AND NOT EXISTS (SELECT 1 FROM product_strategy_bind WHERE product_id = p.id);

INSERT INTO product_strategy_bind (product_id, strategy_code, param_set_id, enabled, min_trade_amount, ideal_trade_amount, fee_rate, fee_min)
SELECT p.id, 'percentile', 'default', 1, 1000.00, 2000.00, 0.000845, 0.20
FROM products p
WHERE p.code = '163406' AND p.channel = 'EXCHANGE' AND p.market = 'SZ'
  AND NOT EXISTS (SELECT 1 FROM product_strategy_bind WHERE product_id = p.id);

-- ============================================================
-- 4. 为profit_recycle策略的产品插入默认状态（如果绑定且不存在）
-- ============================================================

-- 注意：这里只插入绑定profit_recycle策略的产品
INSERT INTO strategy_state (product_id, strategy_code, state_json)
SELECT psb.product_id, 'profit_recycle', '{"last_peak_price": 0, "locked_pool": 0, "last_action_date": null, "deep_dip_triggered": false, "last_dip_date": null, "last_dip_price": 0, "deep_dip_count": 0, "last_tp_date": null, "tp_count": 0}'
FROM product_strategy_bind psb
WHERE psb.strategy_code = 'profit_recycle'
  AND psb.enabled = 1
  AND NOT EXISTS (SELECT 1 FROM strategy_state WHERE product_id = psb.product_id AND strategy_code = 'profit_recycle');

-- ============================================================
-- 5. 添加调度任务（如果不存在）
-- ============================================================

-- 日更指标计算任务（每天22:00执行）
INSERT INTO job_config (job_code, cron_expr, enabled)
SELECT 'indicator_daily', '0 22 * * *', 1
WHERE NOT EXISTS (SELECT 1 FROM job_config WHERE job_code = 'indicator_daily');

-- 盘中建议生成任务（交易日每分钟执行：9:30-11:30, 13:00-15:00）
INSERT INTO job_config (job_code, cron_expr, enabled)
SELECT 'advisor_suggestion_1m', '*/1 9-11,13-14 * * 1-5', 1
WHERE NOT EXISTS (SELECT 1 FROM job_config WHERE job_code = 'advisor_suggestion_1m');

SELECT 'Advisor 初始化数据完成！' AS result;



