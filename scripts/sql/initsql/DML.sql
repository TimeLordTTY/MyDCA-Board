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

 Date: 28/12/2025 16:37:28
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Records of account_groups
-- ----------------------------
INSERT INTO `account_groups` VALUES (1, 'wenlibao', '稳利宝', 1, 6, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `account_groups` VALUES (2, 'ylb', '余利宝', NULL, NULL, '2025-12-25 02:30:40', '2025-12-25 02:30:40');

-- ----------------------------
-- Records of account_pool_rules
-- ----------------------------
INSERT INTO `account_pool_rules` VALUES (1, 3, 17, 0.350000, 1000.00, 1.00, 1, '2025-12-27 13:26:19', '2025-12-27 13:26:19');
INSERT INTO `account_pool_rules` VALUES (2, 3, 18, 0.100000, 1000.00, 1.00, 1, '2025-12-27 13:26:39', '2025-12-27 13:28:04');
INSERT INTO `account_pool_rules` VALUES (3, 3, 22, 0.200000, 1000.00, 1.00, 1, '2025-12-27 13:26:58', '2025-12-27 13:26:58');
INSERT INTO `account_pool_rules` VALUES (4, 3, 20, 0.200000, 1000.00, 1.00, 1, '2025-12-27 13:27:04', '2025-12-27 13:27:04');
INSERT INTO `account_pool_rules` VALUES (5, 3, 19, 0.100000, 1000.00, 1.00, 1, '2025-12-27 13:27:15', '2025-12-27 13:27:52');
INSERT INTO `account_pool_rules` VALUES (6, 3, 23, 0.050000, 500.00, 1.00, 1, '2025-12-27 13:28:16', '2025-12-27 13:28:16');
INSERT INTO `account_pool_rules` VALUES (7, 8, 23, 1.000000, 1000.00, 1.00, 1, '2025-12-27 15:25:48', '2025-12-27 15:25:48');
INSERT INTO `account_pool_rules` VALUES (8, 7, 23, 0.050000, 500.00, 1.00, 1, '2025-12-27 15:26:14', '2025-12-27 15:26:14');
INSERT INTO `account_pool_rules` VALUES (9, 7, 17, 0.350000, 1000.00, 1.00, 1, '2025-12-27 15:26:31', '2025-12-27 15:26:31');
INSERT INTO `account_pool_rules` VALUES (10, 7, 18, 0.100000, 1000.00, 1.00, 1, '2025-12-27 15:26:39', '2025-12-27 15:26:39');
INSERT INTO `account_pool_rules` VALUES (11, 7, 19, 0.100000, 1000.00, 1.00, 1, '2025-12-27 15:26:46', '2025-12-27 15:26:46');
INSERT INTO `account_pool_rules` VALUES (12, 7, 22, 0.200000, 1000.00, 1.00, 1, '2025-12-27 15:26:51', '2025-12-27 15:26:51');
INSERT INTO `account_pool_rules` VALUES (13, 7, 20, 0.200000, 1000.00, 1.00, 1, '2025-12-27 15:26:54', '2025-12-27 15:26:54');

-- ----------------------------
-- Records of accounts
-- ----------------------------
INSERT INTO `accounts` VALUES (1, 'couple_pocket', 'couple_pocket', '情侣小荷包', 'FUND_MAPPED', NULL, 16, 'CNY', '使用余额宝(建信嘉薪宝货币基金A)，收益直接加到余额', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `accounts` VALUES (2, 'ylb_life', 'ylb_life', '余利宝生活费', 'CASH', NULL, NULL, 'CNY', '银行组合理财产品，查不到净值，收益需手工录入', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `accounts` VALUES (3, 'ylb_finance', 'ylb_finance', '余利宝理财金', 'CASH', NULL, NULL, 'CNY', '基金定投资金来源，定期从稳利宝理财金转入', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `accounts` VALUES (4, 'wenlibao_rent', 'wenlibao_rent', '稳利宝-房租预备金', 'PRODUCT_SUB', NULL, 1, 'CNY', '每月10号投入4000，下月3号前两个交易日转出', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `accounts` VALUES (5, 'wenlibao_safe', 'wenlibao_safe', '稳利宝-安全金', 'PRODUCT_SUB', NULL, 1, 'CNY', '暂停投入，待2026-03-10发工资时恢复', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `accounts` VALUES (6, 'wenlibao_project', 'wenlibao_project', '稳利宝-项目资金', 'PRODUCT_SUB', NULL, 1, 'CNY', '每月投入5500，稳利宝收益归入此账户', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `accounts` VALUES (7, 'wenlibao_finance', 'wenlibao_finance', '稳利宝-理财金', 'PRODUCT_SUB', NULL, 1, 'CNY', '基金定投主要来源，定期转到余利宝理财金', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `accounts` VALUES (8, 'wenlibao_active', 'wenlibao_active', '稳利宝-理财金主动投入', 'PRODUCT_SUB', NULL, 1, 'CNY', '来自163406两次卖出，全部买入稳利宝', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `accounts` VALUES (9, 'fund_account', 'fund_account', '基金账户', 'FUND_TOTAL', NULL, NULL, 'CNY', '与daily.csv基金总和保持一致', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `accounts` VALUES (10, 'bank_card', 'bank_card', '银行卡', 'CASH', NULL, NULL, 'CNY', '工资卡等银行卡', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `accounts` VALUES (11, 'wechat', 'wechat', '微信零钱', 'CASH', NULL, NULL, 'CNY', '微信零钱', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');

-- ----------------------------
-- Records of categories
-- ----------------------------
INSERT INTO `categories` VALUES (1, 'expense', '其他', NULL, 0, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (2, 'expense', '购物消费', '日常家具', 1, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (3, 'expense', '购物消费', '个护美妆', 2, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (4, 'expense', '购物消费', '手机数码', 3, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (5, 'expense', '购物消费', '虚拟充值', 4, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (6, 'expense', '购物消费', '生活电器', 5, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (7, 'expense', '购物消费', '配饰腕表', 6, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (8, 'expense', '购物消费', '母婴玩具', 7, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (9, 'expense', '购物消费', '服饰运动', 8, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (10, 'expense', '购物消费', '宠物用品', 9, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (11, 'expense', '购物消费', '办公用品', 10, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (12, 'expense', '购物消费', '装修装饰', 11, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (13, 'expense', '食品餐饮', '水果', 20, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (14, 'expense', '食品餐饮', '早餐', 21, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (15, 'expense', '食品餐饮', '午餐', 22, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (16, 'expense', '食品餐饮', '晚餐', 23, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (17, 'expense', '食品餐饮', '饮料酒水', 24, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (18, 'expense', '食品餐饮', '休闲零食', 25, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (19, 'expense', '食品餐饮', '生鲜食品', 26, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (20, 'expense', '出行交通', '公共交通', 30, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (21, 'expense', '出行交通', '打车租车', 31, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (22, 'expense', '出行交通', '共享单车', 32, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (23, 'expense', '出行交通', '加油', 33, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (24, 'expense', '出行交通', '停车', 34, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (25, 'expense', '出行交通', '机票', 35, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (26, 'expense', '出行交通', '火车', 36, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (27, 'expense', '休闲娱乐', '电影唱歌', 40, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (28, 'expense', '休闲娱乐', '游戏', 41, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (29, 'expense', '休闲娱乐', '旅行度假', 42, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (30, 'expense', '休闲娱乐', '运动健身', 43, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (31, 'expense', '休闲娱乐', '足浴按摩', 44, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (32, 'expense', '休闲娱乐', '棋牌桌游', 45, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (33, 'expense', '休闲娱乐', '酒吧', 46, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (34, 'expense', '休闲娱乐', '演出', 47, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (35, 'expense', '居家生活', '话费宽带', 50, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (36, 'expense', '居家生活', '电费', 51, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (37, 'expense', '居家生活', '水费', 52, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (38, 'expense', '居家生活', '燃气费', 53, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (39, 'expense', '居家生活', '物业费', 54, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (40, 'expense', '居家生活', '房租还贷', 55, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (41, 'expense', '居家生活', '车位费', 56, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (42, 'expense', '居家生活', '家政清洁', 57, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (43, 'expense', '文化教育', '学费', 60, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (44, 'expense', '文化教育', '培训考试', 61, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (45, 'expense', '文化教育', '书报杂志', 62, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (46, 'expense', '送礼人情', '红包礼金', 70, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (47, 'expense', '送礼人情', '礼物', 71, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (48, 'expense', '送礼人情', '孝敬长辈', 72, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (49, 'expense', '健康医疗', '医院', 80, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (50, 'expense', '健康医疗', '体检保险', 81, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (51, 'expense', '健康医疗', '买药', 82, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (52, 'expense', '理财投资', '基金定投', 90, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (53, 'expense', '理财投资', '定期理财', 91, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (54, 'expense', '理财投资', '基金补仓', 92, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (55, 'income', '其他', NULL, 0, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (56, 'income', '初始余额', NULL, 10, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (57, 'income', '退款', NULL, 20, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (58, 'income', '工资', NULL, 30, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (59, 'income', '奖金', NULL, 40, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (60, 'income', '兼职外快', NULL, 50, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (61, 'income', '理财盈利', '利息收益', 60, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (62, 'income', '理财盈利', '基金分红', 61, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (63, 'income', '理财盈利', '产品赎回', 62, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (64, 'income', '中奖', NULL, 70, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (65, 'income', '礼金人情', NULL, 80, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (66, 'income', '借入', NULL, 90, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (67, 'income', '二手闲置', NULL, 100, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (68, 'income', '补贴', NULL, 110, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `categories` VALUES (69, 'income', '报销', NULL, 120, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');

-- ----------------------------
-- Records of indicator_daily
-- ----------------------------
INSERT INTO `indicator_daily` VALUES (1, 19, '2025-12-26', 750, 0.991968, 5.302000, 6.006000, 7.484000, 9.642000, -0.007882, 9.255700, 9.000083, '2025-12-27 15:44:04');
INSERT INTO `indicator_daily` VALUES (3, 23, '2025-12-26', 750, 0.975904, 1.346000, 1.524000, 1.660000, 2.171000, -0.029940, 2.036600, 2.036533, '2025-12-27 16:50:18');
INSERT INTO `indicator_daily` VALUES (4, 17, '2025-12-26', 750, 0.951807, 1.337000, 1.499000, 1.674000, 2.000000, -0.035000, 1.929300, 1.904900, '2025-12-27 16:50:19');
INSERT INTO `indicator_daily` VALUES (5, 18, '2025-12-26', 750, 0.720884, 0.479000, 0.618000, 0.755000, 0.876000, -0.162100, 0.742550, 0.782917, '2025-12-27 16:50:19');
INSERT INTO `indicator_daily` VALUES (6, 22, '2025-12-26', 750, 0.977912, 1.807000, 1.999000, 2.198000, 2.488000, -0.012058, 2.447550, 2.399933, '2025-12-27 16:50:19');
INSERT INTO `indicator_daily` VALUES (7, 20, '2025-12-26', 750, 0.568273, 1.309000, 1.392000, 1.443000, 1.509000, -0.070245, 1.424750, 1.439250, '2025-12-27 16:50:19');
INSERT INTO `indicator_daily` VALUES (98, 23, '2025-12-27', 750, 0.971888, NULL, 1.525000, 1.660000, 2.171000, -0.037310, 2.042000, 2.035550, '2025-12-28 04:20:06');
INSERT INTO `indicator_daily` VALUES (100, 17, '2025-12-27', 750, 0.949799, NULL, 1.500000, 1.676000, 2.000000, -0.035500, 1.928600, 1.907400, '2025-12-28 04:20:12');
INSERT INTO `indicator_daily` VALUES (102, 18, '2025-12-27', 750, 0.726908, NULL, 0.618000, 0.755000, 0.876000, -0.157534, 0.741800, 0.780800, '2025-12-28 04:20:16');
INSERT INTO `indicator_daily` VALUES (104, 22, '2025-12-27', 750, 0.981928, NULL, 1.999000, 2.199000, 2.488000, -0.010852, 2.447450, 2.403117, '2025-12-28 04:20:20');
INSERT INTO `indicator_daily` VALUES (106, 20, '2025-12-27', 750, 0.550201, NULL, 1.392000, 1.443000, 1.509000, -0.071571, 1.422400, 1.439350, '2025-12-28 04:20:24');
INSERT INTO `indicator_daily` VALUES (108, 19, '2025-12-27', 750, 0.997992, NULL, 6.006000, 7.486000, 9.650000, 0.000000, 9.284850, 9.025150, '2025-12-28 04:20:30');

-- ----------------------------
-- Records of job_config
-- ----------------------------
INSERT INTO `job_config` VALUES (1, 'rt_quote_1m', '*/1 9-11,13-14 * * 1-5', 1, '2025-12-27 14:59:13', 'OK', '成功: 5/6', '2025-12-25 02:30:31', '2025-12-27 14:59:13');
INSERT INTO `job_config` VALUES (2, 'otc_update_0800', '0 8 * * *', 1, '2025-12-28 14:00:04', 'OK', '场外净值更新完成', '2025-12-25 02:30:31', '2025-12-28 14:00:04');
INSERT INTO `job_config` VALUES (3, 'otc_update_1400', '0 14 * * *', 1, NULL, NULL, NULL, '2025-12-25 02:30:31', '2025-12-25 02:30:31');
INSERT INTO `job_config` VALUES (4, 'otc_update_2200', '0 22 * * *', 1, NULL, NULL, NULL, '2025-12-25 02:30:31', '2025-12-25 02:30:31');
INSERT INTO `job_config` VALUES (9, 'indicator_daily', '0 22 * * *', 1, '2025-12-27 22:00:07', 'OK', '成功: 6, 失败: 0', '2025-12-27 13:24:24', '2025-12-27 22:00:06');
INSERT INTO `job_config` VALUES (10, 'advisor_suggestion_1m', '*/1 9-11,13-14 * * 1-5', 1, '2025-12-27 14:59:02', 'OK', '成功: 6, 失败: 0', '2025-12-27 13:24:24', '2025-12-27 14:59:01');

-- ----------------------------
-- Records of product_nav_range
-- ----------------------------
INSERT INTO `product_nav_range` VALUES (1, '000307', '易方达黄金ETF联接A', '2016-05-26', '2025-12-25', 2338, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (2, '000686', '建信嘉薪宝货币市场基金A类', '2025-12-20', '2025-12-28', 9, '2025-12-28 04:54:58', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (3, '015299', '华夏纳斯达克100ETF联接(QDII)A', '2022-04-14', '2025-12-24', 900, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (4, '016452', '南方纳斯达克100指数(QDII)A', '2022-11-29', '2025-12-24', 721, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (5, '017641', '摩根标普500指数(QDII)A', '2023-04-06', '2025-12-24', 645, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (6, '018043', '天弘纳斯达克100指数(QDII)A', '2023-04-11', '2025-12-24', 657, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (7, '019172', '摩根纳斯达克100指数(QDII)A', '2023-09-25', '2025-12-24', 538, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (8, '019767', '景顺长城科创50指数增强A', '2024-05-28', '2025-12-25', 364, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (9, '020602', '易方达红利低波ETF联接A', '2024-03-12', '2025-12-25', 437, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (10, '110020', '易方达沪深300ETF联接A', '2009-08-26', '2025-12-25', 3971, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (11, '161125', '易方达标普500指数(QDII-LOF)A', '2016-12-02', '2025-12-24', 2187, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (12, '161130', '易方达纳斯达克100ETF联接(QDII-LOF)A', '2017-06-23', '2025-12-24', 2050, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (13, '012080', '易方达中证500指数量化增强A', '2021-06-15', '2025-12-25', 1088, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (14, '013308', '易方达恒生科技ETF联接(QDII)A', '2022-04-29', '2025-12-25', 892, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (15, '163406', '兴全合润混合A', '2010-04-22', '2025-12-25', 3808, '2025-12-27 02:25:38', '2025-12-27 02:25:38');
INSERT INTO `product_nav_range` VALUES (16, 'FBAE41126E', '民生理财贵竹固收增利周周盈7天持有期26号理财产品E', '2025-12-17', '2025-12-24', 5, '2025-12-27 02:25:38', '2025-12-27 02:25:38');

-- ----------------------------
-- Records of product_strategy_bind
-- ----------------------------
INSERT INTO `product_strategy_bind` VALUES (1, 18, 'percentile', '513180', 1, 'TRIGGER', 0, 1000.00, 2000.00, 0.000845, 0.20, '2025-12-28 04:10:28', '2025-12-27 13:24:24');
INSERT INTO `product_strategy_bind` VALUES (2, 17, 'percentile', '513100', 1, 'TRIGGER', 0, 1000.00, 2000.00, 0.000845, 0.20, '2025-12-28 04:10:28', '2025-12-27 13:24:24');
INSERT INTO `product_strategy_bind` VALUES (3, 22, 'percentile', '513500', 1, 'TRIGGER', 0, 1000.00, 2000.00, 0.000845, 0.20, '2025-12-28 04:10:28', '2025-12-27 13:24:24');
INSERT INTO `product_strategy_bind` VALUES (4, 20, 'percentile', '515450', 1, 'TRIGGER', 0, 1000.00, 2000.00, 0.000845, 0.20, '2025-12-28 04:10:28', '2025-12-27 13:24:24');
INSERT INTO `product_strategy_bind` VALUES (5, 19, 'percentile', '518880', 1, 'TRIGGER', 0, 1000.00, 2000.00, 0.000845, 0.20, '2025-12-28 04:10:28', '2025-12-27 13:24:24');
INSERT INTO `product_strategy_bind` VALUES (6, 23, 'percentile', '163406', 1, 'TRIGGER', 0, 1000.00, 2000.00, 0.000845, 0.20, '2025-12-28 04:10:28', '2025-12-27 13:24:24');
INSERT INTO `product_strategy_bind` VALUES (7, 19, 'profit_recycle', 'd7fa2b85567e0395', 0, 'TRIGGER', 0, 1000.00, 2000.00, 0.000845, 0.20, '2025-12-28 04:24:26', '2025-12-27 20:20:32');

-- ----------------------------
-- Records of products
-- ----------------------------
INSERT INTO `products` VALUES (1, 'FBAE41126E', 'OTC', 'NA', 'BANK_WM_NAV', 'CNY', 0, NULL, '民生理财贵竹固收增利周周盈7天持有期26号理财产品E', 'bank', 'cmbc', 0.000000, 0.000000, 1, 1, '15:00', 'FBAE41126E', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (2, '000307', 'OTC', 'NA', 'FUND', 'CNY', 0, NULL, '易方达黄金ETF联接A', 'fund', 'fund', 0.000700, 0.002000, 1, 1, '15:00', '000307', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (3, '020602', 'OTC', 'NA', 'FUND', 'CNY', 0, NULL, '易方达红利低波ETF联接A', 'fund', 'fund', 0.001200, 0.015000, 1, 1, '15:00', '020602', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (4, '110020', 'OTC', 'NA', 'FUND', 'CNY', 0, NULL, '易方达沪深300ETF联接A', 'fund', 'fund', 0.001200, 0.005000, 1, 1, '15:00', '110020', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (5, '012080', 'OTC', 'NA', 'FUND', 'CNY', 0, NULL, '易方达中证500指数量化增强A', 'fund', 'fund', 0.001500, 0.007500, 1, 1, '15:00', '012080', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (6, '013308', 'OTC', 'NA', 'FUND', 'CNY', 0, NULL, '易方达恒生科技ETF联接(QDII)A', 'fund', 'fund', 0.000600, 0.005000, 1, 1, '15:00', '013308', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (7, '019767', 'OTC', 'NA', 'FUND', 'CNY', 0, NULL, '景顺长城科创50指数增强A', 'fund', 'fund', 0.001200, 0.007500, 1, 1, '15:00', '019767', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (8, '161125', 'OTC', 'NA', 'FUND', 'CNY', 1, NULL, '易方达标普500指数(QDII-LOF)A', 'fund', 'fund', 0.001200, 0.005000, 2, 2, '15:00', '161125', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (9, '161130', 'OTC', 'NA', 'FUND', 'CNY', 1, NULL, '易方达纳斯达克100ETF联接(QDII-LOF)A', 'fund', 'fund', 0.001200, 0.005000, 2, 2, '15:00', '161130', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (10, '019172', 'OTC', 'NA', 'FUND', 'CNY', 1, NULL, '摩根纳斯达克100指数(QDII)A', 'fund', 'fund', 0.001200, 0.015000, 2, 2, '15:00', '019172', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (11, '017641', 'OTC', 'NA', 'FUND', 'CNY', 1, NULL, '摩根标普500指数(QDII)A', 'fund', 'fund', 0.001200, 0.005000, 2, 2, '15:00', '017641', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (12, '016452', 'OTC', 'NA', 'FUND', 'CNY', 1, NULL, '南方纳斯达克100指数(QDII)A', 'fund', 'fund', 0.001200, 0.015000, 2, 2, '15:00', '016452', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (13, '018043', 'OTC', 'NA', 'FUND', 'CNY', 1, NULL, '天弘纳斯达克100指数(QDII)A', 'fund', 'fund', 0.001000, 0.005000, 2, 2, '15:00', '018043', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (14, '015299', 'OTC', 'NA', 'FUND', 'CNY', 1, NULL, '华夏纳斯达克100ETF联接(QDII)A', 'fund', 'fund', 0.001200, 0.015000, 2, 2, '15:00', '015299', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (15, '163406', 'OTC', 'NA', 'FUND', 'CNY', 0, NULL, '兴全合润混合A', 'fund', 'fund', 0.001200, 0.000000, 1, 1, '15:00', '163406', '场外基金版本：扣款/确认按 T+1 建模；申购费率先按 0.12% 默认值，可在产品管理中调整', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (16, '000686', 'OTC', 'NA', 'MMF', 'CNY', 0, NULL, '建信嘉薪宝货币市场基金A类', 'fund', 'fund', 0.000000, 0.000000, 0, 0, '15:00', '000686', NULL, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (17, '513100', 'EXCHANGE', 'SH', 'ETF', 'CNY', 1, '纳斯达克100指数', '国泰纳斯达克100ETF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '513100', '场内QDII ETF；交易成本请走成交流水的 fee/tax 字段；溢价/IOPV 用于刹车规则', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (18, '513180', 'EXCHANGE', 'SH', 'ETF', 'CNY', 1, '恒生科技指数', '恒生科技ETF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '513180', '场内QDII ETF；必须接入溢价率/IOPV 做溢价刹车；交易费用走成交流水', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (19, '518880', 'EXCHANGE', 'SH', 'ETF', 'CNY', 0, 'AU9999', '华安黄金ETF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '518880', '场内黄金ETF；跟踪标的以 AU9999 为核心口径；交易费用走成交流水', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (20, '515450', 'EXCHANGE', 'SH', 'ETF', 'CNY', 0, '中证红利低波动指数', '红利低波ETF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '515450', '场内ETF；跟踪中证红利低波动指数；交易费用走成交流水', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (22, '513500', 'EXCHANGE', 'SH', 'ETF', 'CNY', 1, '标普500净总收益指数', '博时标普500ETF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '513500', '场内QDII ETF；需要接入溢价率/IOPV 做溢价刹车；交易成本请走成交流水 fee/tax 字段', 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `products` VALUES (23, '163406', 'EXCHANGE', 'SZ', 'LOF', 'CNY', 0, NULL, '兴全合润混合A(LOF)', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '163406', '场内LOF版本：与场外(OTC)持仓完全隔离；交易费用走成交流水', 1, '2025-12-25 02:18:25', '2025-12-25 02:18:25');

-- ----------------------------
-- Records of strategy_config
-- ----------------------------
INSERT INTO `strategy_config` VALUES (1, 'profit_recycle', 'v11', 'd7fa2b85567e0395', '{\"ma_window\": 250, \"high_bias\": 0.2, \"lock_ratio_low\": 0.0, \"lock_ratio_mid\": 0.05, \"lock_ratio_high\": 0.2, \"deep_dip_levels\": [{\"threshold\": -0.1, \"use_ratio\": 0.5}, {\"threshold\": -0.15, \"use_ratio\": 1.0}], \"allow_multi_deep_dip\": true, \"rebound_reset_rate\": 0.05, \"debounce_days\": 30, \"take_profit_enabled\": true, \"take_profit_bias\": 0.18, \"take_profit_sell_ratio\": 0.05, \"take_profit_cooldown_days\": 60, \"near_peak_ratio\": 0.98, \"premium_brake_enabled\": true, \"premium_t1\": 0.01, \"premium_t2\": 0.02}', 1, '2025-12-27 03:31:14', '2025-12-27 10:53:43');
INSERT INTO `strategy_config` VALUES (3, 'percentile', 'default', 'c636783ac7e173ad', '{\"base_amount\": 1000.0, \"window\": 250, \"buy_percentile\": 20.0, \"hold_percentile\": 80.0}', 0, '2025-12-27 03:40:55', '2025-12-28 04:26:48');
INSERT INTO `strategy_config` VALUES (4, 'drawdown', 'default', 'a2ac2b0d388b3303', '{\"base_amount\": 1000.0, \"drawdown_thresholds\": [0.02, 0.04, 0.08], \"use_ratios\": [0.3, 0.5, 1.0], \"reset_on_new_high\": true}', 1, '2025-12-27 03:41:00', '2025-12-27 10:52:47');
INSERT INTO `strategy_config` VALUES (5, 'simple', 'default', 'e21c49821b51c6f1', '{\"base_amount\": 1000.0, \"frequency\": \"monthly\", \"day\": 10}', 1, '2025-12-27 03:41:04', '2025-12-27 10:52:23');
INSERT INTO `strategy_config` VALUES (82, 'percentile', 'default', 'default', '{\"window_days\": 750, \"buy_percentile\": 0.20, \"max_buy_per_day\": 2000}', 1, '2025-12-27 13:24:24', '2025-12-27 13:24:24');
INSERT INTO `strategy_config` VALUES (83, 'drawdown', 'default', 'default', '{\"window_days\": 750, \"levels\": [0.02, 0.04, 0.08], \"buy_amounts\": [1000, 1500, 2000]}', 1, '2025-12-27 13:24:24', '2025-12-27 13:24:24');
INSERT INTO `strategy_config` VALUES (84, 'profit_recycle', 'v11', 'v11', '{\"ma_window\": 250, \"high_bias\": 0.20, \"lock_ratio_low\": 0.00, \"lock_ratio_mid\": 0.05, \"lock_ratio_high\": 0.20, \"deep_dip_levels\": [{\"threshold\": -0.10, \"use_ratio\": 0.50}, {\"threshold\": -0.15, \"use_ratio\": 1.00}], \"take_profit_enabled\": true, \"take_profit_bias\": 0.18, \"take_profit_sell_ratio\": 0.05, \"take_profit_cooldown_days\": 60, \"near_peak_ratio\": 0.98, \"allow_multi_deep_dip\": true, \"rebound_reset_rate\": 0.05, \"debounce_days\": 30}', 1, '2025-12-27 13:24:24', '2025-12-27 13:24:24');
INSERT INTO `strategy_config` VALUES (85, 'simple', 'default', 'default', '{\"max_buy_per_day\": 2000}', 1, '2025-12-27 13:24:24', '2025-12-27 13:24:24');
INSERT INTO `strategy_config` VALUES (86, 'percentile', 'default', '515450', '{\"window_days\": 750, \"max_buy_per_day\": 2000, \"tiers\": [{\"max_rank\": 0.3, \"suggest_ratio\": 1.50, \"label\": \"S3-极低估\"}, {\"max_rank\": 0.55, \"suggest_ratio\": 1.00, \"label\": \"S2-偏低估\"}, {\"max_rank\": 0.70, \"suggest_ratio\": 0.50, \"label\": \"S1-略偏低位\"}, {\"max_rank\": 0.85, \"suggest_ratio\": 0.25, \"label\": \"S0-不触发\"}, {\"max_rank\": 1.01, \"suggest_ratio\": 0.00, \"label\": \"VETO-分位过高\"}]}', 1, '2025-12-28 04:10:28', '2025-12-28 16:24:40');
INSERT INTO `strategy_config` VALUES (87, 'percentile', 'default', '518880', '{\"window_days\": 750, \"max_buy_per_day\": 2000, \"tiers\": [{\"max_rank\": 0.15, \"suggest_ratio\": 1.00, \"label\": \"S3-极低估\"}, {\"max_rank\": 0.25, \"suggest_ratio\": 0.70, \"label\": \"S2-偏低估\"}, {\"max_rank\": 0.40, \"suggest_ratio\": 0.40, \"label\": \"S1-略偏低位\"}, {\"max_rank\": 0.60, \"suggest_ratio\": 0.00, \"label\": \"S0-不触发\"}, {\"max_rank\": 1.01, \"suggest_ratio\": 0.00, \"label\": \"VETO-分位过高\"}]}', 1, '2025-12-28 04:10:28', '2025-12-28 16:28:29');
INSERT INTO `strategy_config` VALUES (88, 'percentile', 'default', '513180', '{\"window_days\": 750, \"max_buy_per_day\": 2000, \"tiers\": [{\"max_rank\": 0.20, \"suggest_ratio\": 1.00, \"label\": \"S3-极低估\"}, {\"max_rank\": 0.35, \"suggest_ratio\": 1.00, \"label\": \"S2-偏低估\"}, {\"max_rank\": 0.55, \"suggest_ratio\": 0.50, \"label\": \"S1-略偏低位\"}, {\"max_rank\": 0.75, \"suggest_ratio\": 0.25, \"label\": \"S0-不触发\"}, {\"max_rank\": 1.01, \"suggest_ratio\": 0.00, \"label\": \"VETO-分位过高\"}]}', 1, '2025-12-28 04:10:28', '2025-12-28 16:26:22');
INSERT INTO `strategy_config` VALUES (89, 'percentile', 'default', '513100', '{\"window_days\": 750, \"max_buy_per_day\": 2000, \"tiers\": [{\"max_rank\": 0.30, \"suggest_ratio\": 1.00, \"label\": \"S3-极低估\"}, {\"max_rank\": 0.55, \"suggest_ratio\": 1.00, \"label\": \"S2-偏低估\"}, {\"max_rank\": 0.70, \"suggest_ratio\": 0.50, \"label\": \"S1-略偏低位\"}, {\"max_rank\": 0.85, \"suggest_ratio\": 0.25, \"label\": \"S0-不触发\"}, {\"max_rank\": 1.01, \"suggest_ratio\": 0.00, \"label\": \"VETO-分位过高\"}]}', 1, '2025-12-28 04:10:28', '2025-12-28 16:30:05');
INSERT INTO `strategy_config` VALUES (90, 'percentile', 'default', '513500', '{\"window_days\": 750, \"max_buy_per_day\": 2000, \"tiers\": [{\"max_rank\": 0.30, \"suggest_ratio\": 1.00, \"label\": \"S3-极低估\"}, {\"max_rank\": 0.55, \"suggest_ratio\": 1.00, \"label\": \"S2-偏低估\"}, {\"max_rank\": 0.70, \"suggest_ratio\": 0.50, \"label\": \"S1-略偏低位\"}, {\"max_rank\": 0.85, \"suggest_ratio\": 0.25, \"label\": \"S0-不触发\"}, {\"max_rank\": 1.01, \"suggest_ratio\": 0.00, \"label\": \"VETO-分位过高\"}]}', 1, '2025-12-28 04:10:28', '2025-12-28 16:31:08');
INSERT INTO `strategy_config` VALUES (91, 'percentile', 'default', '163406', '{\"window_days\": 750, \"max_buy_per_day\": 2000, \"tiers\": [{\"max_rank\": 0.25, \"suggest_ratio\": 1.50, \"label\": \"S3-极低估\"}, {\"max_rank\": 0.45, \"suggest_ratio\": 1.00, \"label\": \"S2-偏低估\"}, {\"max_rank\": 0.60, \"suggest_ratio\": 0.50, \"label\": \"S1-略偏低位\"}, {\"max_rank\": 0.85, \"suggest_ratio\": 0.00, \"label\": \"S0-不触发\"}, {\"max_rank\": 1.01, \"suggest_ratio\": 0.00, \"label\": \"VETO-分位过高\"}]}', 1, '2025-12-28 04:10:28', '2025-12-28 16:22:56');

SET FOREIGN_KEY_CHECKS = 1;
