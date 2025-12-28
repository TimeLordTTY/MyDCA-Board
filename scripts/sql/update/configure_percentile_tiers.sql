-- ============================================================
-- 配置各产品的 Percentile 强度档位（S3/S2/S1/S0/VETO）
-- ============================================================
-- 
-- 强度档位说明：
-- S3（强）：明显低位，允许"吃肉"（100%预算）
-- S2（中）：偏低位，正常投入（100%预算）
-- S1（弱）：略偏低位，小额保持盘感（50%预算）
-- S0（不触发）：不买（预算进等待池/或留现金池）
-- VETO（否决）：即使触发也直接不买（例如分位太高、非交易日、QDII溢价过高等）
--
-- ============================================================

USE dca;

-- ============================================================
-- 1. 515450 红利低波（低波、很少深坑）——阈值要放宽
-- ============================================================
-- S3：≤ 25%
-- S2：≤ 40%
-- S1：≤ 55%
-- VETO：> 75%

INSERT INTO strategy_config (strategy_key, strategy_version, param_set_id, param_json, is_active)
SELECT 'percentile', 'default', '515450', 
'{"window_days": 750, "max_buy_per_day": 2000, "tiers": [{"max_rank": 0.25, "suggest_ratio": 1.00, "label": "S3-极低估"}, {"max_rank": 0.40, "suggest_ratio": 1.00, "label": "S2-偏低估"}, {"max_rank": 0.55, "suggest_ratio": 0.50, "label": "S1-略偏低位"}, {"max_rank": 0.75, "suggest_ratio": 0.00, "label": "S0-不触发"}, {"max_rank": 1.01, "suggest_ratio": 0.00, "label": "VETO-分位过高"}]}', 
1
WHERE NOT EXISTS (SELECT 1 FROM strategy_config WHERE strategy_key = 'percentile' AND strategy_version = 'default' AND param_set_id = '515450');

UPDATE product_strategy_bind psb
INNER JOIN products p ON psb.product_id = p.id
SET psb.param_set_id = '515450'
WHERE p.code = '515450' AND p.channel = 'EXCHANGE' AND psb.strategy_code = 'percentile';

-- ============================================================
-- 2. 518880 黄金ETF（趋势强、回归慢）——阈值要更苛刻
-- ============================================================
-- S3：≤ 15%
-- S2：≤ 25%
-- S1：≤ 35%
-- VETO：> 70%

INSERT INTO strategy_config (strategy_key, strategy_version, param_set_id, param_json, is_active)
SELECT 'percentile', 'default', '518880', 
'{"window_days": 750, "max_buy_per_day": 2000, "tiers": [{"max_rank": 0.15, "suggest_ratio": 1.00, "label": "S3-极低估"}, {"max_rank": 0.25, "suggest_ratio": 1.00, "label": "S2-偏低估"}, {"max_rank": 0.35, "suggest_ratio": 0.50, "label": "S1-略偏低位"}, {"max_rank": 0.70, "suggest_ratio": 0.00, "label": "S0-不触发"}, {"max_rank": 1.01, "suggest_ratio": 0.00, "label": "VETO-分位过高"}]}', 
1
WHERE NOT EXISTS (SELECT 1 FROM strategy_config WHERE strategy_key = 'percentile' AND strategy_version = 'default' AND param_set_id = '518880');

UPDATE product_strategy_bind psb
INNER JOIN products p ON psb.product_id = p.id
SET psb.param_set_id = '518880'
WHERE p.code = '518880' AND p.channel = 'EXCHANGE' AND psb.strategy_code = 'percentile';

-- ============================================================
-- 3. 513180 恒生科技（QDII、高波动、深回撤常见）——阈值中等偏宽
-- ============================================================
-- S3：≤ 20%
-- S2：≤ 35%
-- S1：≤ 50%
-- VETO：> 80%

INSERT INTO strategy_config (strategy_key, strategy_version, param_set_id, param_json, is_active)
SELECT 'percentile', 'default', '513180', 
'{"window_days": 750, "max_buy_per_day": 2000, "tiers": [{"max_rank": 0.20, "suggest_ratio": 1.00, "label": "S3-极低估"}, {"max_rank": 0.35, "suggest_ratio": 1.00, "label": "S2-偏低估"}, {"max_rank": 0.50, "suggest_ratio": 0.50, "label": "S1-略偏低位"}, {"max_rank": 0.80, "suggest_ratio": 0.00, "label": "S0-不触发"}, {"max_rank": 1.01, "suggest_ratio": 0.00, "label": "VETO-分位过高"}]}', 
1
WHERE NOT EXISTS (SELECT 1 FROM strategy_config WHERE strategy_key = 'percentile' AND strategy_version = 'default' AND param_set_id = '513180');

UPDATE product_strategy_bind psb
INNER JOIN products p ON psb.product_id = p.id
SET psb.param_set_id = '513180'
WHERE p.code = '513180' AND p.channel = 'EXCHANGE' AND psb.strategy_code = 'percentile';

-- ============================================================
-- 4. 513100 纳指100（QDII、长期趋势强）——分位要比恒科更宽一点
-- ============================================================
-- S3：≤ 25%
-- S2：≤ 40%
-- S1：≤ 55%
-- VETO：> 85%

INSERT INTO strategy_config (strategy_key, strategy_version, param_set_id, param_json, is_active)
SELECT 'percentile', 'default', '513100', 
'{"window_days": 750, "max_buy_per_day": 2000, "tiers": [{"max_rank": 0.25, "suggest_ratio": 1.00, "label": "S3-极低估"}, {"max_rank": 0.40, "suggest_ratio": 1.00, "label": "S2-偏低估"}, {"max_rank": 0.55, "suggest_ratio": 0.50, "label": "S1-略偏低位"}, {"max_rank": 0.85, "suggest_ratio": 0.00, "label": "S0-不触发"}, {"max_rank": 1.01, "suggest_ratio": 0.00, "label": "VETO-分位过高"}]}', 
1
WHERE NOT EXISTS (SELECT 1 FROM strategy_config WHERE strategy_key = 'percentile' AND strategy_version = 'default' AND param_set_id = '513100');

UPDATE product_strategy_bind psb
INNER JOIN products p ON psb.product_id = p.id
SET psb.param_set_id = '513100'
WHERE p.code = '513100' AND p.channel = 'EXCHANGE' AND psb.strategy_code = 'percentile';

-- ============================================================
-- 5. 513500 标普500（QDII、波动更小、趋势更稳）——介于红利低波与纳指之间
-- ============================================================
-- S3：≤ 22%
-- S2：≤ 38%
-- S1：≤ 55%
-- VETO：> 85%

INSERT INTO strategy_config (strategy_key, strategy_version, param_set_id, param_json, is_active)
SELECT 'percentile', 'default', '513500', 
'{"window_days": 750, "max_buy_per_day": 2000, "tiers": [{"max_rank": 0.22, "suggest_ratio": 1.00, "label": "S3-极低估"}, {"max_rank": 0.38, "suggest_ratio": 1.00, "label": "S2-偏低估"}, {"max_rank": 0.55, "suggest_ratio": 0.50, "label": "S1-略偏低位"}, {"max_rank": 0.85, "suggest_ratio": 0.00, "label": "S0-不触发"}, {"max_rank": 1.01, "suggest_ratio": 0.00, "label": "VETO-分位过高"}]}', 
1
WHERE NOT EXISTS (SELECT 1 FROM strategy_config WHERE strategy_key = 'percentile' AND strategy_version = 'default' AND param_set_id = '513500');

UPDATE product_strategy_bind psb
INNER JOIN products p ON psb.product_id = p.id
SET psb.param_set_id = '513500'
WHERE p.code = '513500' AND p.channel = 'EXCHANGE' AND psb.strategy_code = 'percentile';

-- ============================================================
-- 6. 163406 兴全合润LOF（主动、波动中等、净值更平滑）——阈值偏宽
-- ============================================================
-- S3：≤ 25%
-- S2：≤ 45%
-- S1：≤ 60%
-- VETO：> 85%

INSERT INTO strategy_config (strategy_key, strategy_version, param_set_id, param_json, is_active)
SELECT 'percentile', 'default', '163406', 
'{"window_days": 750, "max_buy_per_day": 2000, "tiers": [{"max_rank": 0.25, "suggest_ratio": 1.00, "label": "S3-极低估"}, {"max_rank": 0.45, "suggest_ratio": 1.00, "label": "S2-偏低估"}, {"max_rank": 0.60, "suggest_ratio": 0.50, "label": "S1-略偏低位"}, {"max_rank": 0.85, "suggest_ratio": 0.00, "label": "S0-不触发"}, {"max_rank": 1.01, "suggest_ratio": 0.00, "label": "VETO-分位过高"}]}', 
1
WHERE NOT EXISTS (SELECT 1 FROM strategy_config WHERE strategy_key = 'percentile' AND strategy_version = 'default' AND param_set_id = '163406');

UPDATE product_strategy_bind psb
INNER JOIN products p ON psb.product_id = p.id
SET psb.param_set_id = '163406'
WHERE p.code = '163406' AND p.channel = 'EXCHANGE' AND psb.strategy_code = 'percentile';

-- ============================================================
-- 验证配置
-- ============================================================

SELECT 
    p.code AS product_code,
    p.product_name,
    psb.param_set_id,
    sc.param_json
FROM products p
INNER JOIN product_strategy_bind psb ON p.id = psb.product_id
INNER JOIN strategy_config sc ON sc.strategy_key = 'percentile' 
    AND sc.strategy_version = 'default' 
    AND sc.param_set_id = psb.param_set_id
WHERE psb.strategy_code = 'percentile'
    AND p.code IN ('515450', '518880', '513180', '513100', '513500', '163406')
    AND p.channel = 'EXCHANGE'
ORDER BY p.code;

SELECT 'Percentile 强度档位配置完成！' AS result;

