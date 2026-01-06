-- ============================================================
-- 新增系统使用的分类
-- 系统在理财投资、转账等模块自动创建的记账记录使用这些分类
-- ============================================================

-- 查看当前最大 ID
-- SELECT MAX(id) FROM categories;

-- 1. 转账分类（用于账户间转账）
-- expense: 转账 > 转出
INSERT INTO `categories` (`entry_type`, `category_l1`, `category_l2`, `sort_order`, `is_active`, `created_at`, `updated_at`) 
VALUES ('expense', '转账', '转出', 100, 1, NOW(), NOW());

-- income: 转账 > 转入
INSERT INTO `categories` (`entry_type`, `category_l1`, `category_l2`, `sort_order`, `is_active`, `created_at`, `updated_at`) 
VALUES ('income', '转账', '转入', 130, 1, NOW(), NOW());


-- 2. 理财投资分类（收入类，用于买入确认和赎回确认）
-- income: 理财投资 > 买入确认
INSERT INTO `categories` (`entry_type`, `category_l1`, `category_l2`, `sort_order`, `is_active`, `created_at`, `updated_at`) 
VALUES ('income', '理财投资', '买入确认', 63, 1, NOW(), NOW());

-- income: 理财投资 > 赎回确认（资金到账）
INSERT INTO `categories` (`entry_type`, `category_l1`, `category_l2`, `sort_order`, `is_active`, `created_at`, `updated_at`) 
VALUES ('income', '理财投资', '赎回确认', 64, 1, NOW(), NOW());


-- 3. 理财投资分类（支出类，用于赎回时持仓减少）
-- expense: 理财投资 > 赎回持仓减少
INSERT INTO `categories` (`entry_type`, `category_l1`, `category_l2`, `sort_order`, `is_active`, `created_at`, `updated_at`) 
VALUES ('expense', '理财投资', '赎回持仓减少', 93, 1, NOW(), NOW());


-- ============================================================
-- 验证插入结果
-- ============================================================
-- SELECT * FROM categories WHERE category_l1 IN ('转账', '理财投资') ORDER BY entry_type, sort_order;

