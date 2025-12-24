-- ============================================================
-- MyDCA-Board 现有数据导入脚本
-- 从 products.json / accounts.json / categories.json 迁移数据
-- 适用于新数据库初始化（表不存在的情况）
-- 执行前请确保已执行 init_database.sql
-- ============================================================

USE dca;

-- ============================================================
-- 1. 导入场外产品（从 products.json）
-- ============================================================

INSERT INTO products (
    `code`, `channel`, `market`, `asset_type`, `currency`, `is_qdii`, 
    `product_name`, `category`, `source`, `buy_fee_rate`, `sell_fee_rate`,
    `buy_confirm_offset`, `sell_confirm_offset`, `cutoff_time`, `product_code`
) VALUES
    -- 银行理财
    ('FBAE41126E', 'OTC', 'NA', 'BANK_WM_NAV', 'CNY', 0, 
     '民生理财贵竹固收增利周周盈7天持有期26号理财产品E', 'bank', 'cmbc', 0, 0, 1, 1, '15:00', 'FBAE41126E'),
    
    -- 场外基金
    ('000307', 'OTC', 'NA', 'FUND', 'CNY', 0, 
     '易方达黄金ETF联接A', 'fund', 'fund', 0.0007, 0.002, 1, 1, '15:00', '000307'),
    
    ('020602', 'OTC', 'NA', 'FUND', 'CNY', 0, 
     '易方达红利低波ETF联接A', 'fund', 'fund', 0.0012, 0.015, 1, 1, '15:00', '020602'),
    
    ('110020', 'OTC', 'NA', 'FUND', 'CNY', 0, 
     '易方达沪深300ETF联接A', 'fund', 'fund', 0.0012, 0.005, 1, 1, '15:00', '110020'),
    
    ('012080', 'OTC', 'NA', 'FUND', 'CNY', 0, 
     '易方达中证500指数量化增强A', 'fund', 'fund', 0.0015, 0.0075, 1, 1, '15:00', '012080'),
    
    ('013308', 'OTC', 'NA', 'FUND', 'CNY', 0, 
     '易方达恒生科技ETF联接(QDII)A', 'fund', 'fund', 0.0006, 0.005, 1, 1, '15:00', '013308'),
    
    ('019767', 'OTC', 'NA', 'FUND', 'CNY', 0, 
     '景顺长城科创50指数增强A', 'fund', 'fund', 0.0012, 0.0075, 1, 1, '15:00', '019767'),
    
    -- QDII 场外基金
    ('161125', 'OTC', 'NA', 'FUND', 'CNY', 1, 
     '易方达标普500指数(QDII-LOF)A', 'fund', 'fund', 0.0012, 0.005, 2, 2, '15:00', '161125'),
    
    ('161130', 'OTC', 'NA', 'FUND', 'CNY', 1, 
     '易方达纳斯达克100ETF联接(QDII-LOF)A', 'fund', 'fund', 0.0012, 0.005, 2, 2, '15:00', '161130'),
    
    ('019172', 'OTC', 'NA', 'FUND', 'CNY', 1, 
     '摩根纳斯达克100指数(QDII)A', 'fund', 'fund', 0.0012, 0.015, 2, 2, '15:00', '019172'),
    
    ('017641', 'OTC', 'NA', 'FUND', 'CNY', 1, 
     '摩根标普500指数(QDII)A', 'fund', 'fund', 0.0012, 0.005, 2, 2, '15:00', '017641'),
    
    ('016452', 'OTC', 'NA', 'FUND', 'CNY', 1, 
     '南方纳斯达克100指数(QDII)A', 'fund', 'fund', 0.0012, 0.015, 2, 2, '15:00', '016452'),
    
    ('018043', 'OTC', 'NA', 'FUND', 'CNY', 1, 
     '天弘纳斯达克100指数(QDII)A', 'fund', 'fund', 0.001, 0.005, 2, 2, '15:00', '018043'),
    
    ('015299', 'OTC', 'NA', 'FUND', 'CNY', 1, 
     '华夏纳斯达克100ETF联接(QDII)A', 'fund', 'fund', 0.0012, 0.015, 2, 2, '15:00', '015299'),
    
    -- 163406 场外基金（重要：与场内 LOF 分离）
    ('163406', 'OTC', 'NA', 'FUND', 'CNY', 0, 
     '兴全合润混合(LOF)A', 'fund', 'fund', 0.0012, 0.005, 1, 1, '15:00', '163406'),
    
    -- 货币基金
    ('000686', 'OTC', 'NA', 'MMF', 'CNY', 0, 
     '建信嘉薪宝货币市场基金A类', 'fund', 'fund', 0, 0, 0, 0, '15:00', '000686');


-- ============================================================
-- 2. 导入场内产品（ETF/LOF）
-- ============================================================
/* 513100 - 国泰纳斯达克100ETF（QDII，场内 SH） */
INSERT INTO products
(`code`,`channel`,`market`,`asset_type`,`currency`,`is_qdii`,`track_index`,`product_name`,
 `category`,`source`,`buy_fee_rate`,`sell_fee_rate`,`buy_confirm_offset`,`sell_confirm_offset`,
 `cutoff_time`,`product_code`,`note`,`is_active`)
VALUES
('513100','EXCHANGE','SH','ETF','CNY',1,'纳斯达克100指数','国泰纳斯达克100ETF',
 'fund','akshare',0,0,0,0,'15:00','513100',
 '场内QDII ETF；交易成本请走成交流水的 fee/tax 字段；溢价/IOPV 用于刹车规则',1)
ON DUPLICATE KEY UPDATE
`asset_type`=VALUES(`asset_type`),
`currency`=VALUES(`currency`),
`is_qdii`=VALUES(`is_qdii`),
`track_index`=VALUES(`track_index`),
`product_name`=VALUES(`product_name`),
`category`=VALUES(`category`),
`source`=VALUES(`source`),
`buy_fee_rate`=VALUES(`buy_fee_rate`),
`sell_fee_rate`=VALUES(`sell_fee_rate`),
`buy_confirm_offset`=VALUES(`buy_confirm_offset`),
`sell_confirm_offset`=VALUES(`sell_confirm_offset`),
`cutoff_time`=VALUES(`cutoff_time`),
`product_code`=VALUES(`product_code`),
`note`=VALUES(`note`),
`is_active`=VALUES(`is_active`);

/* 513180 - 恒生科技ETF（QDII，场内 SH） */
INSERT INTO products
(`code`,`channel`,`market`,`asset_type`,`currency`,`is_qdii`,`track_index`,`product_name`,
 `category`,`source`,`buy_fee_rate`,`sell_fee_rate`,`buy_confirm_offset`,`sell_confirm_offset`,
 `cutoff_time`,`product_code`,`note`,`is_active`)
VALUES
('513180','EXCHANGE','SH','ETF','CNY',1,'恒生科技指数','恒生科技ETF',
 'fund','akshare',0,0,0,0,'15:00','513180',
 '场内QDII ETF；必须接入溢价率/IOPV 做溢价刹车；交易费用走成交流水',1)
ON DUPLICATE KEY UPDATE
`asset_type`=VALUES(`asset_type`),
`currency`=VALUES(`currency`),
`is_qdii`=VALUES(`is_qdii`),
`track_index`=VALUES(`track_index`),
`product_name`=VALUES(`product_name`),
`category`=VALUES(`category`),
`source`=VALUES(`source`),
`buy_fee_rate`=VALUES(`buy_fee_rate`),
`sell_fee_rate`=VALUES(`sell_fee_rate`),
`buy_confirm_offset`=VALUES(`buy_confirm_offset`),
`sell_confirm_offset`=VALUES(`sell_confirm_offset`),
`cutoff_time`=VALUES(`cutoff_time`),
`product_code`=VALUES(`product_code`),
`note`=VALUES(`note`),
`is_active`=VALUES(`is_active`);

/* 518880 - 华安黄金ETF（场内 SH） */
INSERT INTO products
(`code`,`channel`,`market`,`asset_type`,`currency`,`is_qdii`,`track_index`,`product_name`,
 `category`,`source`,`buy_fee_rate`,`sell_fee_rate`,`buy_confirm_offset`,`sell_confirm_offset`,
 `cutoff_time`,`product_code`,`note`,`is_active`)
VALUES
('518880','EXCHANGE','SH','ETF','CNY',0,'AU9999','华安黄金ETF',
 'fund','akshare',0,0,0,0,'15:00','518880',
 '场内黄金ETF；跟踪标的以 AU9999 为核心口径；交易费用走成交流水',1)
ON DUPLICATE KEY UPDATE
`asset_type`=VALUES(`asset_type`),
`currency`=VALUES(`currency`),
`is_qdii`=VALUES(`is_qdii`),
`track_index`=VALUES(`track_index`),
`product_name`=VALUES(`product_name`),
`category`=VALUES(`category`),
`source`=VALUES(`source`),
`buy_fee_rate`=VALUES(`buy_fee_rate`),
`sell_fee_rate`=VALUES(`sell_fee_rate`),
`buy_confirm_offset`=VALUES(`buy_confirm_offset`),
`sell_confirm_offset`=VALUES(`sell_confirm_offset`),
`cutoff_time`=VALUES(`cutoff_time`),
`product_code`=VALUES(`product_code`),
`note`=VALUES(`note`),
`is_active`=VALUES(`is_active`);

/* 515450 - 红利低波ETF（场内 SH） */
INSERT INTO products
(`code`,`channel`,`market`,`asset_type`,`currency`,`is_qdii`,`track_index`,`product_name`,
 `category`,`source`,`buy_fee_rate`,`sell_fee_rate`,`buy_confirm_offset`,`sell_confirm_offset`,
 `cutoff_time`,`product_code`,`note`,`is_active`)
VALUES
('515450','EXCHANGE','SH','ETF','CNY',0,'中证红利低波动指数','红利低波ETF',
 'fund','akshare',0,0,0,0,'15:00','515450',
 '场内ETF；跟踪中证红利低波动指数；交易费用走成交流水',1)
ON DUPLICATE KEY UPDATE
`asset_type`=VALUES(`asset_type`),
`currency`=VALUES(`currency`),
`is_qdii`=VALUES(`is_qdii`),
`track_index`=VALUES(`track_index`),
`product_name`=VALUES(`product_name`),
`category`=VALUES(`category`),
`source`=VALUES(`source`),
`buy_fee_rate`=VALUES(`buy_fee_rate`),
`sell_fee_rate`=VALUES(`sell_fee_rate`),
`buy_confirm_offset`=VALUES(`buy_confirm_offset`),
`sell_confirm_offset`=VALUES(`sell_confirm_offset`),
`cutoff_time`=VALUES(`cutoff_time`),
`product_code`=VALUES(`product_code`),
`note`=VALUES(`note`),
`is_active`=VALUES(`is_active`);

/* 163406 - 兴全合润混合A（场外 OTC 版本：用于你的场外定投/净值体系） */
INSERT INTO products
(`code`,`channel`,`market`,`asset_type`,`currency`,`is_qdii`,`track_index`,`product_name`,
 `category`,`source`,`buy_fee_rate`,`sell_fee_rate`,`buy_confirm_offset`,`sell_confirm_offset`,
 `cutoff_time`,`product_code`,`note`,`is_active`)
VALUES
('163406','OTC','NA','FUND','CNY',0,NULL,'兴全合润混合A',
 'fund','fund',0.001200,0,1,1,'15:00','163406',
 '场外基金版本：扣款/确认按 T+1 建模；申购费率先按 0.12% 默认值，可在产品管理中调整',1)
ON DUPLICATE KEY UPDATE
`asset_type`=VALUES(`asset_type`),
`currency`=VALUES(`currency`),
`is_qdii`=VALUES(`is_qdii`),
`track_index`=VALUES(`track_index`),
`product_name`=VALUES(`product_name`),
`category`=VALUES(`category`),
`source`=VALUES(`source`),
`buy_fee_rate`=VALUES(`buy_fee_rate`),
`sell_fee_rate`=VALUES(`sell_fee_rate`),
`buy_confirm_offset`=VALUES(`buy_confirm_offset`),
`sell_confirm_offset`=VALUES(`sell_confirm_offset`),
`cutoff_time`=VALUES(`cutoff_time`),
`product_code`=VALUES(`product_code`),
`note`=VALUES(`note`),
`is_active`=VALUES(`is_active`);

/* 513500 - 博时标普500ETF（QDII，场内 SH） */
INSERT INTO products
(`code`,`channel`,`market`,`asset_type`,`currency`,`is_qdii`,`track_index`,`product_name`,
 `category`,`source`,`buy_fee_rate`,`sell_fee_rate`,`buy_confirm_offset`,`sell_confirm_offset`,
 `cutoff_time`,`product_code`,`note`,`is_active`)
VALUES
('513500','EXCHANGE','SH','ETF','CNY',1,'标普500净总收益指数','博时标普500ETF',
 'fund','akshare',0,0,0,0,'15:00','513500',
 '场内QDII ETF；需要接入溢价率/IOPV 做溢价刹车；交易成本请走成交流水 fee/tax 字段',1)
ON DUPLICATE KEY UPDATE
`asset_type`=VALUES(`asset_type`),
`currency`=VALUES(`currency`),
`is_qdii`=VALUES(`is_qdii`),
`track_index`=VALUES(`track_index`),
`product_name`=VALUES(`product_name`),
`category`=VALUES(`category`),
`source`=VALUES(`source`),
`buy_fee_rate`=VALUES(`buy_fee_rate`),
`sell_fee_rate`=VALUES(`sell_fee_rate`),
`buy_confirm_offset`=VALUES(`buy_confirm_offset`),
`sell_confirm_offset`=VALUES(`sell_confirm_offset`),
`cutoff_time`=VALUES(`cutoff_time`),
`product_code`=VALUES(`product_code`),
`note`=VALUES(`note`),
`is_active`=VALUES(`is_active`);
-- ============================================================
-- 3. 导入账户（从 accounts.json）
-- ============================================================

-- 注意：需要先导入 products，以便关联 product_id

INSERT INTO accounts (
    `account_code`, `account_id`, `account_name`, `account_type`, 
    `product_id`, `currency`, `is_active`, `note`
) 
SELECT 
    a.account_code,
    a.account_id,
    a.account_name,
    a.account_type,
    p.id AS product_id,
    'CNY' AS currency,
    1 AS is_active,
    a.note
FROM (
    SELECT 'couple_pocket' AS account_code, 'couple_pocket' AS account_id, '情侣小荷包' AS account_name, 
           'FUND_MAPPED' AS account_type, '000686' AS linked_product, 
           '使用余额宝(建信嘉薪宝货币基金A)，收益直接加到余额' AS note
    UNION ALL
    SELECT 'ylb_life', 'ylb_life', '余利宝生活费', 'CASH', NULL, 
           '银行组合理财产品，查不到净值，收益需手工录入'
    UNION ALL
    SELECT 'ylb_finance', 'ylb_finance', '余利宝理财金', 'CASH', NULL, 
           '基金定投资金来源，定期从稳利宝理财金转入'
    UNION ALL
    SELECT 'wenlibao_rent', 'wenlibao_rent', '稳利宝-房租预备金', 'PRODUCT_SUB', 'FBAE41126E', 
           '每月10号投入4000，下月3号前两个交易日转出'
    UNION ALL
    SELECT 'wenlibao_safe', 'wenlibao_safe', '稳利宝-安全金', 'PRODUCT_SUB', 'FBAE41126E', 
           '暂停投入，待2026-03-10发工资时恢复'
    UNION ALL
    SELECT 'wenlibao_project', 'wenlibao_project', '稳利宝-项目资金', 'PRODUCT_SUB', 'FBAE41126E', 
           '每月投入5500，稳利宝收益归入此账户'
    UNION ALL
    SELECT 'wenlibao_finance', 'wenlibao_finance', '稳利宝-理财金', 'PRODUCT_SUB', 'FBAE41126E', 
           '基金定投主要来源，定期转到余利宝理财金'
    UNION ALL
    SELECT 'wenlibao_active', 'wenlibao_active', '稳利宝-理财金主动投入', 'PRODUCT_SUB', 'FBAE41126E', 
           '来自163406两次卖出，全部买入稳利宝'
    UNION ALL
    SELECT 'fund_account', 'fund_account', '基金账户', 'FUND_TOTAL', NULL, 
           '与daily.csv基金总和保持一致'
    UNION ALL
    SELECT 'bank_card', 'bank_card', '银行卡', 'CASH', NULL, '工资卡等银行卡'
    UNION ALL
    SELECT 'wechat', 'wechat', '微信零钱', 'CASH', NULL, '微信零钱'
) AS a
LEFT JOIN products p ON a.linked_product = p.code AND p.channel = 'OTC';


-- ============================================================
-- 4. 导入账户组配置（从 accounts.json）
-- ============================================================

INSERT INTO account_groups (
    `group_code`, `group_name`, `linked_product_id`, `profit_account_id`
)
SELECT 
    'wenlibao' AS group_code,
    '稳利宝' AS group_name,
    p.id AS linked_product_id,
    a.id AS profit_account_id
FROM products p
LEFT JOIN accounts a ON a.account_code = 'wenlibao_project'
WHERE p.code = 'FBAE41126E' AND p.channel = 'OTC';

INSERT INTO account_groups (
    `group_code`, `group_name`, `linked_product_id`, `profit_account_id`
)
VALUES
    ('ylb', '余利宝', NULL, NULL);


-- ============================================================
-- 5. 导入分类配置（从 categories.json）
-- ============================================================

-- 支出分类
INSERT INTO categories (`entry_type`, `category_l1`, `category_l2`, `display_order`) VALUES
    ('expense', '其他', NULL, 0),
    ('expense', '购物消费', '日常家具', 1),
    ('expense', '购物消费', '个护美妆', 2),
    ('expense', '购物消费', '手机数码', 3),
    ('expense', '购物消费', '虚拟充值', 4),
    ('expense', '购物消费', '生活电器', 5),
    ('expense', '购物消费', '配饰腕表', 6),
    ('expense', '购物消费', '母婴玩具', 7),
    ('expense', '购物消费', '服饰运动', 8),
    ('expense', '购物消费', '宠物用品', 9),
    ('expense', '购物消费', '办公用品', 10),
    ('expense', '购物消费', '装修装饰', 11),
    ('expense', '食品餐饮', '水果', 20),
    ('expense', '食品餐饮', '早餐', 21),
    ('expense', '食品餐饮', '午餐', 22),
    ('expense', '食品餐饮', '晚餐', 23),
    ('expense', '食品餐饮', '饮料酒水', 24),
    ('expense', '食品餐饮', '休闲零食', 25),
    ('expense', '食品餐饮', '生鲜食品', 26),
    ('expense', '出行交通', '公共交通', 30),
    ('expense', '出行交通', '打车租车', 31),
    ('expense', '出行交通', '共享单车', 32),
    ('expense', '出行交通', '加油', 33),
    ('expense', '出行交通', '停车', 34),
    ('expense', '出行交通', '机票', 35),
    ('expense', '出行交通', '火车', 36),
    ('expense', '休闲娱乐', '电影唱歌', 40),
    ('expense', '休闲娱乐', '游戏', 41),
    ('expense', '休闲娱乐', '旅行度假', 42),
    ('expense', '休闲娱乐', '运动健身', 43),
    ('expense', '休闲娱乐', '足浴按摩', 44),
    ('expense', '休闲娱乐', '棋牌桌游', 45),
    ('expense', '休闲娱乐', '酒吧', 46),
    ('expense', '休闲娱乐', '演出', 47),
    ('expense', '居家生活', '话费宽带', 50),
    ('expense', '居家生活', '电费', 51),
    ('expense', '居家生活', '水费', 52),
    ('expense', '居家生活', '燃气费', 53),
    ('expense', '居家生活', '物业费', 54),
    ('expense', '居家生活', '房租还贷', 55),
    ('expense', '居家生活', '车位费', 56),
    ('expense', '居家生活', '家政清洁', 57),
    ('expense', '文化教育', '学费', 60),
    ('expense', '文化教育', '培训考试', 61),
    ('expense', '文化教育', '书报杂志', 62),
    ('expense', '送礼人情', '红包礼金', 70),
    ('expense', '送礼人情', '礼物', 71),
    ('expense', '送礼人情', '孝敬长辈', 72),
    ('expense', '健康医疗', '医院', 80),
    ('expense', '健康医疗', '体检保险', 81),
    ('expense', '健康医疗', '买药', 82),
    ('expense', '理财投资', '基金定投', 90),
    ('expense', '理财投资', '定期理财', 91),
    ('expense', '理财投资', '基金补仓', 92);

-- 收入分类
INSERT INTO categories (`entry_type`, `category_l1`, `category_l2`, `display_order`) VALUES
    ('income', '其他', NULL, 0),
    ('income', '初始余额', NULL, 10),
    ('income', '退款', NULL, 20),
    ('income', '工资', NULL, 30),
    ('income', '奖金', NULL, 40),
    ('income', '兼职外快', NULL, 50),
    ('income', '理财盈利', '利息收益', 60),
    ('income', '理财盈利', '基金分红', 61),
    ('income', '理财盈利', '产品赎回', 62),
    ('income', '中奖', NULL, 70),
    ('income', '礼金人情', NULL, 80),
    ('income', '借入', NULL, 90),
    ('income', '二手闲置', NULL, 100),
    ('income', '补贴', NULL, 110),
    ('income', '报销', NULL, 120);


-- ============================================================
-- 6. 初始化任务调度配置
-- ============================================================

INSERT INTO job_config (job_code, cron_expr, enabled) VALUES
    ('rt_quote_1m', '*/1 9-11,13-14 * * 1-5', 1),
    ('otc_update_0800', '0 8 * * *', 1),
    ('otc_update_1400', '0 14 * * *', 1),
    ('otc_update_2200', '0 22 * * *', 1);


-- ============================================================
-- 完成
-- ============================================================

SELECT 'Data import completed!' AS message;
SELECT COUNT(*) AS product_count FROM products;
SELECT COUNT(*) AS account_count FROM accounts;
SELECT COUNT(*) AS category_count FROM categories;
SELECT COUNT(*) AS account_group_count FROM account_groups;
SELECT COUNT(*) AS job_config_count FROM job_config;
