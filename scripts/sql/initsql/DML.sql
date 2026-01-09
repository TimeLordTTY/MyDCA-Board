/*
 Navicat Premium Dump SQL

 Source Server         : localhost-dca
 Source Server Type    : MySQL
 Source Server Version : 80036 (8.0.36)
 Source Host           : 127.0.0.1:9009
 Source Schema         : dca

 Target Server Type    : MySQL
 Target Server Version : 80036 (8.0.36)
 File Encoding         : 65001

 Date: 10/01/2026 00:25:01
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
INSERT INTO `account_pool_rules` VALUES (1, 3, 27, 0.350000, 1000.00, 1.00, 1, '2025-12-27 13:26:19', '2025-12-29 01:15:33');
INSERT INTO `account_pool_rules` VALUES (2, 3, 26, 0.100000, 1000.00, 1.00, 1, '2025-12-27 13:26:39', '2025-12-29 00:38:17');
INSERT INTO `account_pool_rules` VALUES (3, 3, 22, 0.200000, 1000.00, 1.00, 1, '2025-12-27 13:26:58', '2025-12-27 13:26:58');
INSERT INTO `account_pool_rules` VALUES (4, 3, 24, 0.200000, 1000.00, 1.00, 1, '2025-12-27 13:27:04', '2025-12-29 00:14:30');
INSERT INTO `account_pool_rules` VALUES (5, 3, 25, 0.100000, 1000.00, 1.00, 1, '2025-12-27 13:27:15', '2025-12-29 00:38:18');
INSERT INTO `account_pool_rules` VALUES (6, 3, 23, 0.050000, 500.00, 1.00, 1, '2025-12-27 13:28:16', '2025-12-27 13:28:16');
INSERT INTO `account_pool_rules` VALUES (7, 8, 23, 1.000000, 1000.00, 1.00, 1, '2025-12-27 15:25:48', '2025-12-27 15:25:48');
INSERT INTO `account_pool_rules` VALUES (8, 7, 23, 0.050000, 500.00, 1.00, 1, '2025-12-27 15:26:14', '2025-12-27 15:26:14');
INSERT INTO `account_pool_rules` VALUES (9, 7, 27, 0.350000, 1000.00, 1.00, 1, '2025-12-27 15:26:31', '2025-12-29 01:15:35');
INSERT INTO `account_pool_rules` VALUES (10, 7, 26, 0.100000, 1000.00, 1.00, 1, '2025-12-27 15:26:39', '2025-12-29 00:36:44');
INSERT INTO `account_pool_rules` VALUES (11, 7, 25, 0.100000, 1000.00, 1.00, 1, '2025-12-27 15:26:46', '2025-12-29 00:36:42');
INSERT INTO `account_pool_rules` VALUES (12, 7, 22, 0.200000, 1000.00, 1.00, 1, '2025-12-27 15:26:51', '2025-12-27 15:26:51');
INSERT INTO `account_pool_rules` VALUES (13, 7, 24, 0.200000, 1000.00, 1.00, 1, '2025-12-27 15:26:54', '2025-12-29 00:14:26');

-- ----------------------------
-- Records of accounts
-- ----------------------------
INSERT INTO `accounts` VALUES (1, 'couple_pocket', 'couple_pocket', '情侣小荷包', 'FUND_MAPPED', NULL, 16, 'CNY', '使用余额宝(建信嘉薪宝货币基金A)，收益直接加到余额', 59.79, 0.000000, 1, '2025-12-25 02:30:40', '2026-01-06 15:56:26');
INSERT INTO `accounts` VALUES (2, 'ylb_life', 'ylb_life', '余利宝生活费', 'CASH', NULL, NULL, 'CNY', '银行组合理财产品，查不到净值，收益需手工录入', 1100.92, 0.000000, 1, '2025-12-25 02:30:40', '2026-01-07 13:56:34');
INSERT INTO `accounts` VALUES (3, 'ylb_finance', 'ylb_finance', '余利宝理财金', 'CASH', NULL, NULL, 'CNY', '基金定投资金来源，定期从稳利宝理财金转入', 0.00, 0.000000, 1, '2025-12-25 02:30:40', '2026-01-07 14:30:25');
INSERT INTO `accounts` VALUES (4, 'wenlibao_rent', 'wenlibao_rent', '稳利宝-房租预备金', 'PRODUCT_SUB', NULL, 1, 'CNY', '每月10号投入4000，下月3号前两个交易日转出', 0.00, 0.000000, 1, '2025-12-25 02:30:40', '2026-01-07 01:42:38');
INSERT INTO `accounts` VALUES (5, 'wenlibao_safe', 'wenlibao_safe', '稳利宝-安全金', 'PRODUCT_SUB', NULL, 1, 'CNY', '暂停投入，待2026-03-10发工资时恢复', 3084.24, 2957.937682, 1, '2025-12-25 02:30:40', '2026-01-07 14:29:35');
INSERT INTO `accounts` VALUES (6, 'wenlibao_project', 'wenlibao_project', '稳利宝-项目资金', 'PRODUCT_SUB', NULL, 1, 'CNY', '每月投入5500，稳利宝收益归入此账户', 9475.57, 9087.552318, 1, '2025-12-25 02:30:40', '2026-01-07 23:20:49');
INSERT INTO `accounts` VALUES (7, 'wenlibao_finance', 'wenlibao_finance', '稳利宝-理财金', 'PRODUCT_SUB', NULL, 1, 'CNY', '基金定投主要来源，定期转到余利宝理财金', 0.00, 0.000000, 1, '2025-12-25 02:30:40', '2026-01-07 14:28:39');
INSERT INTO `accounts` VALUES (8, 'wenlibao_active', 'wenlibao_active', '稳利宝-理财金主动投入', 'PRODUCT_SUB', NULL, 1, 'CNY', '来自163406两次卖出，全部买入稳利宝', 0.00, 0.000000, 1, '2025-12-25 02:30:40', '2026-01-07 14:29:35');
INSERT INTO `accounts` VALUES (9, 'fund_account', 'fund_account', '基金账户', 'FUND_TOTAL', NULL, NULL, 'CNY', '与daily.csv基金总和保持一致', 0.00, 0.000000, 1, '2025-12-25 02:30:40', '2025-12-25 02:30:40');
INSERT INTO `accounts` VALUES (10, 'bank_card', 'bank_card', '银行卡', 'CASH', NULL, NULL, 'CNY', '工资卡等银行卡', 0.00, 0.000000, 1, '2025-12-25 02:30:40', '2026-01-07 14:30:35');
INSERT INTO `accounts` VALUES (11, 'wechat', 'wechat', '微信零钱', 'CASH', NULL, NULL, 'CNY', '微信零钱', 6.66, 0.000000, 1, '2025-12-25 02:30:40', '2026-01-06 15:56:26');
INSERT INTO `accounts` VALUES (16, 'huabao_account', 'huabao_account', '华宝证券账户', 'CASH', NULL, NULL, 'CNY', NULL, 4176.44, 0.000000, 1, '2025-12-31 14:48:51', '2026-01-09 15:10:30');
INSERT INTO `accounts` VALUES (17, 'yuebao', 'yuebao', '余额宝', 'CASH', NULL, NULL, 'CNY', NULL, 2.25, 0.000000, 1, '2025-12-31 15:56:54', '2026-01-07 23:20:49');

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
INSERT INTO `categories` VALUES (70, 'expense', '转账', '转出', 100, 1, '2026-01-07 00:54:12', '2026-01-07 00:54:12');
INSERT INTO `categories` VALUES (71, 'income', '转账', '转入', 130, 1, '2026-01-07 00:54:12', '2026-01-07 00:54:12');
INSERT INTO `categories` VALUES (72, 'income', '理财投资', '买入确认', 63, 1, '2026-01-07 00:54:12', '2026-01-07 00:54:12');
INSERT INTO `categories` VALUES (73, 'income', '理财投资', '赎回确认', 64, 1, '2026-01-07 00:54:12', '2026-01-07 00:54:12');
INSERT INTO `categories` VALUES (74, 'expense', '理财投资', '赎回持仓减少', 93, 1, '2026-01-07 00:54:12', '2026-01-07 00:54:12');

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
INSERT INTO `products` VALUES (22, '513650', 'EXCHANGE', 'SH', 'ETF', 'CNY', 1, '标普500净总收益指数', '标普500ETF南方', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '513650', '场内QDII ETF；需要接入溢价率/IOPV 做溢价刹车；交易成本请走成交流水 fee/tax 字段', 1, '2025-12-25 02:30:40', '2026-01-04 19:24:22');
INSERT INTO `products` VALUES (23, '163406', 'EXCHANGE', 'SZ', 'LOF', 'CNY', 0, NULL, '兴全合润混合A(LOF)', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '163406', '场内LOF版本：与场外(OTC)持仓完全隔离；交易费用走成交流水', 1, '2025-12-25 02:18:25', '2025-12-25 02:18:25');
INSERT INTO `products` VALUES (24, '563020', 'EXCHANGE', 'SZ', 'ETF', 'CNY', 0, '中证红利低波动指数', '红利低波动ETF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '563020', NULL, 1, '2025-12-29 00:05:48', '2025-12-29 00:10:20');
INSERT INTO `products` VALUES (25, '518850', 'EXCHANGE', 'SH', 'ETF', 'CNY', 0, 'AU9999', '黄金ETF华夏', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '518850', '场内黄金ETF；跟踪标的以 AU9999 为核心口径；交易费用走成交流水', 1, '2025-12-25 02:30:40', '2025-12-29 00:54:54');
INSERT INTO `products` VALUES (26, '513010', 'EXCHANGE', 'SH', 'ETF', 'CNY', 1, '恒生科技指数', '恒生科技ETF易方达', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '513010', '场内QDII ETF；必须接入溢价率/IOPV 做溢价刹车；交易费用走成交流水', 1, '2025-12-25 02:30:40', '2025-12-29 00:54:48');
INSERT INTO `products` VALUES (27, '159659', 'EXCHANGE', 'SZ', 'ETF', 'CNY', 1, '纳斯达克100指数', '纳斯达克100ETF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '159659', '场内QDII ETF；交易成本请走成交流水的 fee/tax 字段；溢价/IOPV 用于刹车规则', 1, '2025-12-25 02:30:40', '2026-01-04 17:08:49');
INSERT INTO `products` VALUES (28, '161125', 'EXCHANGE', 'SZ', 'LOF', 'CNY', 1, '标普500', '标普500LOF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '161125', '场内QDII ETF；交易成本请走成交流水的 fee/tax 字段；溢价/IOPV 用于刹车规则', 1, '2025-12-25 02:30:40', '2026-01-07 22:35:05');
INSERT INTO `products` VALUES (29, '161130', 'EXCHANGE', 'SZ', 'LOF', 'CNY', 1, '纳斯达克100LOF', '纳斯达克100LOF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '161130', '场内QDII ETF；交易成本请走成交流水的 fee/tax 字段；溢价/IOPV 用于刹车规则', 1, '2025-12-25 02:30:40', '2026-01-07 22:37:17');
INSERT INTO `products` VALUES (30, '159206', 'EXCHANGE', 'SZ', 'ETF', 'CNY', 0, '卫星通信', '卫星ETF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '159206', '', 1, '2025-12-25 02:30:40', '2026-01-09 17:37:30');
INSERT INTO `products` VALUES (31, '562500', 'EXCHANGE', 'SH', 'ETF', 'CNY', 0, '机器人', '机器人ETF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '562500', NULL, 1, '2026-01-09 11:16:49', '2026-01-09 17:37:30');
INSERT INTO `products` VALUES (32, '513310', 'EXCHANGE', 'SH', 'ETF', 'CNY', 0, '中韩半导体', '中韩半导体ETF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '513310', NULL, 1, '2026-01-09 11:16:49', '2026-01-09 17:37:30');
INSERT INTO `products` VALUES (33, '588000', 'EXCHANGE', 'SH', 'ETF', 'CNY', 0, '科创50', '科创50ETF', 'fund', 'akshare', 0.000000, 0.000000, 0, 0, '15:00', '588000', NULL, 1, '2026-01-09 11:16:49', '2026-01-09 17:37:43');

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
INSERT INTO `strategy_config` VALUES (86, 'percentile', 'default', '563020', '{\"window_days\": 750, \"max_buy_per_day\": 2000, \"tiers\": [{\"max_rank\": 0.3, \"suggest_ratio\": 1.50, \"label\": \"S3-极低估\"}, {\"max_rank\": 0.55, \"suggest_ratio\": 1.00, \"label\": \"S2-偏低估\"}, {\"max_rank\": 0.70, \"suggest_ratio\": 0.50, \"label\": \"S1-略偏低位\"}, {\"max_rank\": 0.85, \"suggest_ratio\": 0.25, \"label\": \"S0-不触发\"}, {\"max_rank\": 1.01, \"suggest_ratio\": 0.00, \"label\": \"VETO-分位过高\"}]}', 1, '2025-12-28 04:10:28', '2025-12-29 00:09:43');
INSERT INTO `strategy_config` VALUES (87, 'percentile', 'default', '518850', '{\"window_days\": 750, \"max_buy_per_day\": 2000, \"tiers\": [{\"max_rank\": 0.15, \"suggest_ratio\": 1.00, \"label\": \"S3-极低估\"}, {\"max_rank\": 0.25, \"suggest_ratio\": 0.70, \"label\": \"S2-偏低估\"}, {\"max_rank\": 0.40, \"suggest_ratio\": 0.40, \"label\": \"S1-略偏低位\"}, {\"max_rank\": 0.60, \"suggest_ratio\": 0.00, \"label\": \"S0-不触发\"}, {\"max_rank\": 1.01, \"suggest_ratio\": 0.00, \"label\": \"VETO-分位过高\"}]}', 1, '2025-12-28 04:10:28', '2025-12-29 00:35:40');
INSERT INTO `strategy_config` VALUES (88, 'percentile', 'default', '513010', '{\"window_days\": 750, \"max_buy_per_day\": 2000, \"tiers\": [{\"max_rank\": 0.20, \"suggest_ratio\": 1.00, \"label\": \"S3-极低估\"}, {\"max_rank\": 0.35, \"suggest_ratio\": 1.00, \"label\": \"S2-偏低估\"}, {\"max_rank\": 0.55, \"suggest_ratio\": 0.50, \"label\": \"S1-略偏低位\"}, {\"max_rank\": 0.75, \"suggest_ratio\": 0.25, \"label\": \"S0-不触发\"}, {\"max_rank\": 1.01, \"suggest_ratio\": 0.00, \"label\": \"VETO-分位过高\"}]}', 1, '2025-12-28 04:10:28', '2025-12-29 01:03:29');
INSERT INTO `strategy_config` VALUES (89, 'percentile', 'default', '159659', '{\"window_days\": 750, \"max_buy_per_day\": 2000, \"tiers\": [{\"max_rank\": 0.30, \"suggest_ratio\": 1.00, \"label\": \"S3-极低估\"}, {\"max_rank\": 0.55, \"suggest_ratio\": 1.00, \"label\": \"S2-偏低估\"}, {\"max_rank\": 0.70, \"suggest_ratio\": 0.50, \"label\": \"S1-略偏低位\"}, {\"max_rank\": 0.85, \"suggest_ratio\": 0.25, \"label\": \"S0-不触发\"}, {\"max_rank\": 1.01, \"suggest_ratio\": 0.00, \"label\": \"VETO-分位过高\"}]}', 1, '2025-12-28 04:10:28', '2026-01-04 17:09:48');
INSERT INTO `strategy_config` VALUES (90, 'percentile', 'default', '513650', '{\"window_days\": 750, \"max_buy_per_day\": 2000, \"tiers\": [{\"max_rank\": 0.30, \"suggest_ratio\": 1.00, \"label\": \"S3-极低估\"}, {\"max_rank\": 0.55, \"suggest_ratio\": 1.00, \"label\": \"S2-偏低估\"}, {\"max_rank\": 0.70, \"suggest_ratio\": 0.50, \"label\": \"S1-略偏低位\"}, {\"max_rank\": 0.85, \"suggest_ratio\": 0.25, \"label\": \"S0-不触发\"}, {\"max_rank\": 1.01, \"suggest_ratio\": 0.00, \"label\": \"VETO-分位过高\"}]}', 1, '2025-12-28 04:10:28', '2026-01-04 19:24:27');
INSERT INTO `strategy_config` VALUES (91, 'percentile', 'default', '163406', '{\"window_days\": 750, \"max_buy_per_day\": 2000, \"tiers\": [{\"max_rank\": 0.25, \"suggest_ratio\": 1.50, \"label\": \"S3-极低估\"}, {\"max_rank\": 0.45, \"suggest_ratio\": 1.00, \"label\": \"S2-偏低估\"}, {\"max_rank\": 0.60, \"suggest_ratio\": 0.50, \"label\": \"S1-略偏低位\"}, {\"max_rank\": 0.85, \"suggest_ratio\": 0.00, \"label\": \"S0-不触发\"}, {\"max_rank\": 1.01, \"suggest_ratio\": 0.00, \"label\": \"VETO-分位过高\"}]}', 1, '2025-12-28 04:10:28', '2025-12-28 16:22:56');

SET FOREIGN_KEY_CHECKS = 1;
