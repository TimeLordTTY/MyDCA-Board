-- ============================================
-- SQL脚本目的：初始化产品主数据（product_master）
-- 文件编号：01
-- 日期：2026-01-12
-- 执行顺序：在DDL.sql之后执行
-- ============================================
-- 
-- 功能说明：
-- 1. 初始化产品主数据表（product_master）
-- 2. 参考V1版本的产品数据，转换为v2版本的表结构
-- 
-- 注意事项：
-- - 如果产品已存在（根据product_code+channel+market唯一键），使用ON DUPLICATE KEY UPDATE更新
-- - 产品数据可根据实际需要调整
-- - 执行前请确保product_master表已创建
-- ============================================

SET NAMES utf8mb4;

-- ============================================
-- 初始化产品主数据（product_master）
-- ============================================
-- 字段映射说明：
--   V1.products.code -> product_master.product_code
--   V1.products.channel -> product_master.channel
--   V1.products.market -> product_master.market
--   V1.products.asset_type -> product_master.asset_type
--   V1.products.currency -> product_master.currency
--   V1.products.product_name -> product_master.product_name
--   V1.products.is_qdii -> product_master.is_qdii
--   V1.products.track_index -> product_master.track_index
--   V1.products.buy_fee_rate -> product_master.buy_fee_rate
--   V1.products.sell_fee_rate -> product_master.sell_fee_rate
--   V1.products.buy_confirm_offset -> product_master.buy_confirm_offset
--   V1.products.sell_confirm_offset -> product_master.sell_confirm_offset
--   V1.products.cutoff_time -> product_master.cutoff_time
--   V1.products.source -> product_master.data_source
--   V1.products.is_active -> product_master.is_active

INSERT INTO `product_master` (
    `product_code`,
    `channel`,
    `market`,
    `asset_type`,
    `currency`,
    `product_name`,
    `is_qdii`,
    `track_index`,
    `buy_fee_rate`,
    `sell_fee_rate`,
    `buy_confirm_offset`,
    `sell_confirm_offset`,
    `cutoff_time`,
    `data_source`,
    `is_active`,
    `created_at`,
    `updated_at`
) VALUES
-- 场外基金（OTC）
('000307', 'OTC', 'NA', 'FUND', 'CNY', '易方达黄金ETF联接A', 0, NULL, 0.000700, 0.002000, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('020602', 'OTC', 'NA', 'FUND', 'CNY', '易方达红利低波ETF联接A', 0, NULL, 0.001200, 0.015000, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('110020', 'OTC', 'NA', 'FUND', 'CNY', '易方达沪深300ETF联接A', 0, NULL, 0.001200, 0.005000, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('012080', 'OTC', 'NA', 'FUND', 'CNY', '易方达中证500指数量化增强A', 0, NULL, 0.001500, 0.007500, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('013308', 'OTC', 'NA', 'FUND', 'CNY', '易方达恒生科技ETF联接(QDII)A', 0, NULL, 0.000600, 0.005000, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('019767', 'OTC', 'NA', 'FUND', 'CNY', '景顺长城科创50指数增强A', 0, NULL, 0.001200, 0.007500, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('161125', 'OTC', 'NA', 'FUND', 'CNY', '易方达标普500指数(QDII-LOF)A', 1, NULL, 0.001200, 0.005000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('161130', 'OTC', 'NA', 'FUND', 'CNY', '易方达纳斯达克100ETF联接(QDII-LOF)A', 1, NULL, 0.001200, 0.005000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('019172', 'OTC', 'NA', 'FUND', 'CNY', '摩根纳斯达克100指数(QDII)A', 1, NULL, 0.001200, 0.015000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('017641', 'OTC', 'NA', 'FUND', 'CNY', '摩根标普500指数(QDII)A', 1, NULL, 0.001200, 0.005000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('016452', 'OTC', 'NA', 'FUND', 'CNY', '南方纳斯达克100指数(QDII)A', 1, NULL, 0.001200, 0.015000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('018043', 'OTC', 'NA', 'FUND', 'CNY', '天弘纳斯达克100指数(QDII)A', 1, NULL, 0.001000, 0.005000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('015299', 'OTC', 'NA', 'FUND', 'CNY', '华夏纳斯达克100ETF联接(QDII)A', 1, NULL, 0.001200, 0.015000, 2, 2, '15:00', 'fund', 1, NOW(), NOW()),
('163406', 'OTC', 'NA', 'FUND', 'CNY', '兴全合润混合A', 0, NULL, 0.001200, 0.000000, 1, 1, '15:00', 'fund', 1, NOW(), NOW()),
('000686', 'OTC', 'NA', 'MMF', 'CNY', '建信嘉薪宝货币市场基金A类', 0, NULL, 0.000000, 0.000000, 0, 0, '15:00', 'fund', 1, NOW(), NOW()),
-- 场内ETF/LOF（EXCHANGE）
('513650', 'EXCHANGE', 'SH', 'ETF', 'CNY', '标普500ETF南方', 1, '标普500净总收益指数', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('163406', 'EXCHANGE', 'SZ', 'LOF', 'CNY', '兴全合润混合A(LOF)', 0, NULL, 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('563020', 'EXCHANGE', 'SZ', 'ETF', 'CNY', '红利低波动ETF', 0, '中证红利低波动指数', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('518850', 'EXCHANGE', 'SH', 'ETF', 'CNY', '黄金ETF华夏', 0, 'AU9999', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('513010', 'EXCHANGE', 'SH', 'ETF', 'CNY', '恒生科技ETF易方达', 1, '恒生科技指数', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('159659', 'EXCHANGE', 'SZ', 'ETF', 'CNY', '纳斯达克100ETF', 1, '纳斯达克100指数', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('161125', 'EXCHANGE', 'SZ', 'LOF', 'CNY', '标普500LOF', 1, '标普500', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('161130', 'EXCHANGE', 'SZ', 'LOF', 'CNY', '纳斯达克100LOF', 1, '纳斯达克100LOF', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('159206', 'EXCHANGE', 'SZ', 'ETF', 'CNY', '卫星ETF', 0, '卫星通信', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('562500', 'EXCHANGE', 'SH', 'ETF', 'CNY', '机器人ETF', 0, '机器人', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('513310', 'EXCHANGE', 'SH', 'ETF', 'CNY', '中韩半导体ETF', 0, '中韩半导体', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
('588000', 'EXCHANGE', 'SH', 'ETF', 'CNY', '科创50ETF', 0, '科创50', 0.000000, 0.000000, 0, 0, '15:00', 'akshare', 1, NOW(), NOW()),
-- 银行理财产品
('FBAE41126E', 'OTC', 'NA', 'BANK_WM_NAV', 'CNY', '民生理财贵竹固收增利周周盈7天持有期26号理财产品E', 0, NULL, 0.000000, 0.000000, 1, 1, '15:00', 'cmbc', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    `product_name` = VALUES(`product_name`),
    `is_qdii` = VALUES(`is_qdii`),
    `track_index` = VALUES(`track_index`),
    `buy_fee_rate` = VALUES(`buy_fee_rate`),
    `sell_fee_rate` = VALUES(`sell_fee_rate`),
    `buy_confirm_offset` = VALUES(`buy_confirm_offset`),
    `sell_confirm_offset` = VALUES(`sell_confirm_offset`),
    `cutoff_time` = VALUES(`cutoff_time`),
    `data_source` = VALUES(`data_source`),
    `is_active` = VALUES(`is_active`),
    `updated_at` = NOW();

-- 验证查询
SELECT 
    product_code, 
    product_name, 
    channel, 
    market, 
    asset_type, 
    is_qdii, 
    is_active,
    COUNT(*) as count
FROM product_master
GROUP BY product_code, channel, market
ORDER BY channel, asset_type, product_code;
