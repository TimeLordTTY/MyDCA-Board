-- ============================================================
-- 添加交易时段自动刷新行情任务配置
-- ============================================================

-- 添加交易时段自动刷新行情任务（交易日9:30-11:30, 13:00-15:00，每60秒执行一次）
INSERT INTO job_config (job_code, cron_expr, enabled)
SELECT 'refresh_quote_trading', '*/1 9-11,13-14 * * 1-5', 1
WHERE NOT EXISTS (SELECT 1 FROM job_config WHERE job_code = 'refresh_quote_trading');

-- 说明：
-- job_code: refresh_quote_trading
-- cron_expr: */1 9-11,13-14 * * 1-5
--   含义：每分钟执行，在9-11点和13-14点，每周1-5（周一到周五）
--   注意：实际执行时会检查是否是交易日且在交易时间段内（9:30-11:30, 13:00-15:00）
-- enabled: 1（启用）

SELECT '交易时段自动刷新行情任务配置已添加！' AS result;

