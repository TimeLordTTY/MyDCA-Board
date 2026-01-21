-- ============================================
-- SQL脚本目的：创建财富中枢系统完整数据库结构
-- 文件名称：DDL.sql（主DDL文件）
-- 执行顺序：第一个执行（基础DDL）
-- ============================================
-- 
-- 功能说明：
-- 1. 创建所有核心数据表（用户、家庭、账户、产品、订单、交易等）
-- 2. 创建表索引和约束
-- 3. 设置字符集为utf8mb4，支持完整的Unicode字符
-- 
-- 基于：《财富中枢系统完整设计方案.md》修订版
-- 数据库版本：MySQL 8.0+
-- 
-- 注意事项：
-- - 执行前请确保数据库已创建
-- - 执行后会关闭外键检查，执行完成后会自动恢复
-- - 建议在空数据库中执行，避免表名冲突
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 1. 用户与权限表
-- ============================================

-- 1.1 users - 用户表
CREATE TABLE `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名（登录用）',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希（BCrypt）',
  `nickname` VARCHAR(64) NULL COMMENT '昵称',
  `email` VARCHAR(128) NULL COMMENT '邮箱',
  `phone` VARCHAR(32) NULL COMMENT '手机号',
  `family_id` BIGINT NULL COMMENT '所属家庭ID',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `last_login_at` DATETIME NULL COMMENT '最后登录时间',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_family_id` (`family_id`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- 1.2 families - 家庭表
CREATE TABLE `families` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '家庭ID',
  `family_code` VARCHAR(64) NOT NULL COMMENT '家庭代码（唯一标识）',
  `family_name` VARCHAR(128) NOT NULL COMMENT '家庭名称',
  `admin_user_id` BIGINT NOT NULL COMMENT '管理员用户ID',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_code` (`family_code`),
  KEY `idx_admin_user_id` (`admin_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='家庭表';

-- 1.3 user_family_roles - 用户家庭角色关联表
CREATE TABLE `user_family_roles` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `family_id` BIGINT NOT NULL COMMENT '家庭ID',
  `role` ENUM('ADMIN', 'MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '角色',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_family` (`user_id`, `family_id`),
  KEY `idx_family_id` (`family_id`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户家庭角色关联表';

-- ============================================
-- 2. 产品与账户表
-- ============================================

-- 2.1 product_master - 产品主数据表
CREATE TABLE `product_master` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '产品ID',
  `product_code` VARCHAR(32) NOT NULL COMMENT '产品代码',
  `channel` ENUM('EXCHANGE', 'OTC') NOT NULL DEFAULT 'OTC' COMMENT '场内/场外',
  `market` ENUM('SH', 'SZ', 'NA') NOT NULL DEFAULT 'NA' COMMENT '市场：SH/SZ/NA',
  `asset_type` ENUM('ETF', 'LOF', 'FUND', 'MMF', 'BANK_WM_NAV', 'BANK_WM_BOX', 'STOCK', 'FUTURES', 'OPTIONS', 'BOND_REPO') NOT NULL DEFAULT 'FUND' COMMENT '资产类型（BOND_REPO=国债逆回购，场内交易标的，作为现金管理工具，默认1天期）',
  `currency` ENUM('CNY', 'USD', 'HKD') NOT NULL DEFAULT 'CNY' COMMENT '货币',
  `product_name` VARCHAR(128) NOT NULL COMMENT '产品名称',
  `is_qdii` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否QDII',
  `track_index` VARCHAR(64) NULL COMMENT '跟踪指数',
  `buy_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '申购费率（默认值，实际费率优先从broker_fee_config获取）',
  `sell_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '赎回费率（默认值，实际费率优先从broker_fee_config获取）',
  `buy_confirm_offset` INT NOT NULL DEFAULT 1 COMMENT '买入确认延迟交易日数（T+N）',
  `sell_confirm_offset` INT NOT NULL DEFAULT 1 COMMENT '赎回确认延迟交易日数（T+N）',
  `cutoff_time` VARCHAR(10) NOT NULL DEFAULT '15:00' COMMENT '交易截止时间',
  `data_source` VARCHAR(32) NULL COMMENT '数据源（akshare/fund/cmbc）',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `sort_order` INT NULL COMMENT '排序顺序（数字越小越靠前，NULL表示未设置）',
  `note` VARCHAR(500) NULL COMMENT '备注',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code_channel_market` (`product_code`, `channel`, `market`),
  KEY `idx_product_code` (`product_code`),
  KEY `idx_asset_type` (`asset_type`),
  KEY `idx_is_active` (`is_active`),
  KEY `idx_channel_sort_order` (`channel`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='产品主数据表';

-- 2.2 user_product - 用户产品配置表
CREATE TABLE `user_product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `product_id` BIGINT NOT NULL COMMENT '产品ID（外键product_master.id）',
  `alias` VARCHAR(128) NULL COMMENT '个人别名',
  `is_core` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否核心持仓',
  `risk_level` ENUM('LOW', 'MEDIUM', 'HIGH') NULL COMMENT '风险等级',
  `target_weight` DECIMAL(10, 6) NULL COMMENT '目标权重（0-1）',
  `min_weight` DECIMAL(10, 6) NULL COMMENT '最小权重',
  `max_weight` DECIMAL(10, 6) NULL COMMENT '最大权重',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户产品配置表';

-- 2.3 accounts - 账户表（修订：增加REAL/VIRTUAL区分和reserved_amount）
CREATE TABLE `accounts` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '账户ID',
  `account_code` VARCHAR(64) NOT NULL COMMENT '账户代码（唯一标识）',
  `account_name` VARCHAR(128) NOT NULL COMMENT '账户名称',
  `account_kind` ENUM('REAL', 'VIRTUAL') NOT NULL DEFAULT 'REAL' COMMENT '账户性质：REAL=现实账户，VIRTUAL=虚拟科目',
  `account_type` ENUM('BANK', 'PAYMENT', 'BROKER', 'MMF', 'CASH', 'CREDIT_CARD', 'HUABEI', 'BAITIAO', 'LOAN', 'OTHER') NOT NULL COMMENT '账户类型',
  `account_subtype` VARCHAR(32) NULL COMMENT '账户子类型（如CREDIT_CARD/HUABEI/BAITIAO/LOAN用于信贷账户）',
  `virtual_subtype` ENUM('POSITION', 'FEE', 'INCOME', 'EXPENSE', 'RECEIVABLE', 'LIABILITY') NULL COMMENT '虚拟科目子类型（仅VIRTUAL使用）',
  `owner_type` ENUM('PERSONAL', 'FAMILY') NOT NULL DEFAULT 'PERSONAL' COMMENT '归属类型：个人/家庭',
  `owner_user_id` BIGINT NULL COMMENT '归属用户ID（个人账户）',
  `owner_family_id` BIGINT NULL COMMENT '归属家庭ID（家庭账户）',
  `currency` ENUM('CNY', 'USD', 'HKD') NOT NULL DEFAULT 'CNY' COMMENT '货币',
  `parent_account_id` BIGINT NULL COMMENT '父账户ID（用于现实账户的资金分区/子账户，父账户为平台容器/分组节点，子账户为真实信封余额）',
  `linked_product_id` BIGINT NULL COMMENT '关联产品ID（如稳利宝、小荷包等与具体理财/基金产品绑定的账户，可用于初始化持仓）',
  `fund_usage` ENUM('SPENDABLE','RESERVED','INVESTABLE') NULL DEFAULT NULL COMMENT '资金用途（SPENDABLE=可支出，允许日常支出/生活消费；RESERVED=专款，房租/项目/安全金等，禁止日常支出和默认禁止投资；INVESTABLE=可投资，可用于投资如ETF/逆回购等，默认不用于日常支出。仅对account_kind=REAL且account_type=CASH且为叶子账户的场景做约束校验。信贷账户（CREDIT_CARD、HUABEI、BAITIAO、LOAN）不需要资金用途，此字段为NULL）',
  `balance` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '账面余额（由流水推导，REAL账户可手工调整）',
  `reserved_amount` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '占用/冻结金额（下单占用，结算确认后释放）',
  `initial_balance` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '初始余额',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `note` VARCHAR(500) NULL COMMENT '备注',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_code` (`account_code`),
  KEY `idx_owner_user_id` (`owner_user_id`),
  KEY `idx_owner_family_id` (`owner_family_id`),
  KEY `idx_account_kind` (`account_kind`),
  KEY `idx_account_type` (`account_type`),
  KEY `idx_is_active` (`is_active`),
  KEY `idx_parent_account_id` (`parent_account_id`),
  KEY `idx_linked_product_id` (`linked_product_id`),
  CONSTRAINT `chk_virtual_subtype` CHECK (
    (`account_kind` = 'VIRTUAL' AND `virtual_subtype` IS NOT NULL) OR 
    (`account_kind` = 'REAL' AND `virtual_subtype` IS NULL)
  ),
  FOREIGN KEY (`parent_account_id`) REFERENCES `accounts`(`id`) ON DELETE SET NULL,
  FOREIGN KEY (`linked_product_id`) REFERENCES `product_master`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='账户表';

-- 2.3.1 broker_fee_config - 券商费率配置表
CREATE TABLE `broker_fee_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '费率配置ID',
  `account_id` BIGINT NOT NULL COMMENT '券商账户ID（外键accounts.id，account_type必须为BROKER）',
  `fee_rule_type` ENUM('STOCK', 'ETF', 'LOF', 'LOF_SUBSCRIPTION', 'CONVERTIBLE_BOND_SH', 'CONVERTIBLE_BOND_SZ', 'BOND_REPO', 'FUND_OTC', 'DEFAULT') NOT NULL COMMENT '费率规则类型（STOCK=A股，ETF=ETF，LOF=LOF场内交易，LOF_SUBSCRIPTION=LOF场内申购，CONVERTIBLE_BOND_SH=上海可转债，CONVERTIBLE_BOND_SZ=深圳可转债，BOND_REPO=逆回购，FUND_OTC=场外基金，DEFAULT=默认规则）',
  `buy_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '买入费率（如0.0001154表示万1.154）',
  `sell_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '卖出费率',
  `buy_min_fee` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '买入最低手续费（起收金额，如2.00表示2元起收）',
  `sell_min_fee` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '卖出最低手续费',
  `subscription_discount_rate` DECIMAL(10, 7) NULL COMMENT '申购折扣率（如0.1表示一折，仅用于LOF_SUBSCRIPTION）',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `note` VARCHAR(500) NULL COMMENT '备注',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_fee_rule` (`account_id`, `fee_rule_type`),
  KEY `idx_account_id` (`account_id`),
  KEY `idx_fee_rule_type` (`fee_rule_type`),
  KEY `idx_is_active` (`is_active`),
  CONSTRAINT `fk_broker_fee_account` FOREIGN KEY (`account_id`) REFERENCES `accounts`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='券商费率配置表';

-- 2.3.2 fund_sell_fee_tier - 场外基金卖出费率分段表
CREATE TABLE `fund_sell_fee_tier` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '费率分段ID',
  `product_id` BIGINT NOT NULL COMMENT '产品ID（外键product_master.id）',
  `min_days` INT NOT NULL COMMENT '最小持有天数（包含，如0表示持有0天及以上）',
  `max_days` INT NULL COMMENT '最大持有天数（不包含，如7表示持有7天以下，NULL表示无上限）',
  `sell_fee_rate` DECIMAL(10, 7) NOT NULL DEFAULT 0.0000000 COMMENT '卖出费率（如0.0015表示0.15%）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序顺序（数字越小越靠前，用于确定分段优先级）',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `note` VARCHAR(500) NULL COMMENT '备注（如"持有0-7天"）',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_min_days` (`min_days`),
  KEY `idx_is_active` (`is_active`),
  KEY `idx_product_sort` (`product_id`, `sort_order`),
  CONSTRAINT `fk_fund_sell_fee_product` FOREIGN KEY (`product_id`) REFERENCES `product_master`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='场外基金卖出费率分段表';

-- 父子账户约束说明（应用层必须校验，MySQL不支持在CHECK约束中引用外键列）：
-- 1. VIRTUAL账户不允许设置parent_account_id（应用层校验）
-- 2. 子账户必须是REAL（应用层校验）
-- 3. 只有CASH类型的REAL账户允许形成父子层级（应用层校验）
-- 4. ledger_posting.account_id 只允许引用叶子账户（应用层校验）

-- 2.4 debt_contract - 分期合同/借款合同表
CREATE TABLE `debt_contract` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '合同ID',
  `contract_code` VARCHAR(64) NOT NULL COMMENT '合同代码（唯一标识）',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `family_id` BIGINT NULL COMMENT '家庭ID',
  `account_id` BIGINT NOT NULL COMMENT '负债账户ID（外键accounts.id，account_type为CREDIT_CARD/HUABEI/BAITIAO/LOAN）',
  `contract_name` VARCHAR(128) NOT NULL COMMENT '合同名称（如"花呗分期-2024-01"）',
  `principal` DECIMAL(18, 2) NOT NULL COMMENT '本金总额',
  `fee_total` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '手续费/利息总额',
  `term_months` INT NOT NULL COMMENT '分期期数',
  `start_date` DATE NOT NULL COMMENT '合同开始日期',
  `repayment_rule` VARCHAR(64) NULL COMMENT '还款日规则（如"每月15日"或JSON格式）',
  `status` ENUM('ACTIVE', 'FINISHED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE' COMMENT '合同状态',
  `note` VARCHAR(500) NULL COMMENT '备注',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_code` (`contract_code`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_account_id` (`account_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分期合同/借款合同表';

-- 2.5 debt_installment - 分期计划表
CREATE TABLE `debt_installment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分期计划ID',
  `contract_id` BIGINT NOT NULL COMMENT '合同ID（外键debt_contract.id）',
  `period_no` INT NOT NULL COMMENT '期数（1, 2, 3, ...）',
  `due_date` DATE NOT NULL COMMENT '到期日',
  `principal_due` DECIMAL(18, 2) NOT NULL COMMENT '应还本金',
  `fee_due` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '应还手续费/利息',
  `total_due` DECIMAL(18, 2) NOT NULL COMMENT '应还总额（principal_due + fee_due）',
  `paid_amount` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '已还金额',
  `paid_date` DATE NULL COMMENT '还款日期',
  `status` ENUM('DUE', 'PAID', 'OVERDUE', 'CANCELLED') NOT NULL DEFAULT 'DUE' COMMENT '状态',
  `note` VARCHAR(500) NULL COMMENT '备注',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_period` (`contract_id`, `period_no`),
  KEY `idx_contract_id` (`contract_id`),
  KEY `idx_due_date` (`due_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分期计划表';

-- ============================================
-- 3. 交易流水表（核心，修订：ledger_txn降级冗余字段）
-- ============================================

-- 3.1 ledger_txn - 交易事件表（修订：移除amount/shares/nav/fee，仅作为事件头）
CREATE TABLE `ledger_txn` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '交易事件ID',
  `txn_id` VARCHAR(64) NOT NULL COMMENT '交易事件唯一标识（业务单号）',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `family_id` BIGINT NULL COMMENT '家庭ID（用于家庭汇总）',
  `txn_type` ENUM(
    'BUY', 'SELL',                    -- 投资交易（场内买入/卖出）
    'SUBSCRIPTION', 'REDEMPTION',     -- 申购/赎回（场外基金买入/卖出）
    'CUSTODY_TRANSFER',                -- 转托管（持仓从A账户迁到B账户，无现金流）
    'BOND_REPO',                       -- 国债逆回购（现金增强，不生成持仓）
    'DIVIDEND_CASH', 'DIVIDEND_REINVEST', 'DIVIDEND_EX_DATE', 'DIVIDEND_PAY_DATE', 'INTEREST',  -- 收益类
    'FEE', 'TAX',                     -- 费用类
    'TRANSFER_OUT', 'TRANSFER_IN',    -- 转账（成对出现）
    'EXPENSE', 'INCOME',              -- 消费/收入
    'ADJUST',                         -- 余额调整
    'REIMBURSE_IN', 'REIMBURSE_OUT',  -- 退款/报销
    'DEFER'                           -- 跨期结算
  ) NOT NULL COMMENT '交易类型',
  `biz_group_key` VARCHAR(64) NULL COMMENT '业务分组键（用于UI合并展示，如order_id）',
  `product_id` BIGINT NULL COMMENT '关联产品ID（投资类交易）',
  `order_id` VARCHAR(64) NULL COMMENT '关联订单ID（如有）',
  `related_txn_id` VARCHAR(64) NULL COMMENT '关联的原交易txn_id（退款/报销等，指向原交易）',
  `related_order_id` VARCHAR(64) NULL COMMENT '关联的原订单号（可选，用于订单级退款/撤单）',
  `relation_type` ENUM('NONE','TRANSFER_PAIR','REFUND','REFUND_OF','REIMBURSE','REIMBURSEMENT_OF','REVERSAL','CUSTODY_TRANSFER_OF') NOT NULL DEFAULT 'NONE' COMMENT '关联类型（NONE=无关联，TRANSFER_PAIR=转账成对，REFUND=退款，REFUND_OF=退款属于原交易，REIMBURSE=报销，REIMBURSEMENT_OF=报销属于原支出，REVERSAL=撤销，CUSTODY_TRANSFER_OF=转托管属于原事件）',
  `requested_at` DATETIME NOT NULL COMMENT '发起时间',
  `trade_date` DATE NULL COMMENT '交易归属日（由requested_at+cutoff+交易日历推导）',
  `nav_date` DATE NULL COMMENT '使用的净值日期（计算份额/到账金额时采用的净值日期）',
  `confirm_date` DATE NULL COMMENT '到账/确认日期（份额到账或金额到账的日期）',
  `fetch_date` DATE NULL COMMENT '采集日（用于快照与看板"今日资产/今日盈亏"）',
  `status` ENUM('PENDING', 'CONFIRMED', 'CANCELLED', 'REVERSED') NOT NULL DEFAULT 'CONFIRMED' COMMENT '状态（PENDING用于T+N待结算）',
  `note` VARCHAR(500) NULL COMMENT '备注',
  `is_reversed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已撤销（用于纠错）',
  `reversed_by_txn_id` VARCHAR(64) NULL COMMENT '撤销此交易的交易ID',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_txn_id` (`txn_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_family_id` (`family_id`),
  KEY `idx_txn_type` (`txn_type`),
  KEY `idx_biz_group_key` (`biz_group_key`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_related_txn_id` (`related_txn_id`),
  KEY `idx_related_order_id` (`related_order_id`),
  KEY `idx_relation_type` (`relation_type`),
  KEY `idx_trade_date` (`trade_date`),
  KEY `idx_confirm_date` (`confirm_date`),
  KEY `idx_fetch_date` (`fetch_date`),
  KEY `idx_requested_at` (`requested_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='交易事件表';

-- 3.2 ledger_posting - 分录表（修订：明确借贷方向规则，amount永远为正数，增加历史余额字段）
CREATE TABLE `ledger_posting` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分录ID',
  `txn_id` VARCHAR(64) NOT NULL COMMENT '交易事件ID（外键ledger_txn.txn_id）',
  `posting_type` ENUM('DEBIT', 'CREDIT') NOT NULL COMMENT '借贷方向（amount永远为正数，方向由posting_type决定）',
  `account_id` BIGINT NOT NULL COMMENT '账户ID（外键accounts.id）',
  `account_type` ENUM('CASH', 'POSITION', 'FEE', 'INCOME', 'EXPENSE', 'LIABILITY', 'RECEIVABLE') NOT NULL COMMENT '账户类型',
  `amount` DECIMAL(18, 2) NOT NULL COMMENT '金额（永远为正数，方向由posting_type决定）',
  `account_balance_after` DECIMAL(18, 2) NULL COMMENT '该分录发生后的账户余额（用于显示历史余额）',
  `parent_account_balance_after` DECIMAL(18, 2) NULL COMMENT '该分录发生后的父账户余额（用于显示历史余额）',
  `shares` DECIMAL(20, 6) NULL COMMENT '份额（持仓类分录，永远为正数，方向由posting_type决定）',
  `currency` ENUM('CNY', 'USD', 'HKD') NOT NULL DEFAULT 'CNY' COMMENT '货币',
  `note` VARCHAR(255) NULL COMMENT '分录备注',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_txn_id` (`txn_id`),
  KEY `idx_account_id` (`account_id`),
  KEY `idx_posting_type` (`posting_type`),
  KEY `idx_account_type` (`account_type`),
  CONSTRAINT `chk_amount_positive` CHECK (`amount` > 0),
  CONSTRAINT `chk_shares_non_negative` CHECK (`shares` IS NULL OR `shares` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分录表';

-- ============================================
-- 4. 订单与结算表
-- ============================================

-- 4.1 orders - 订单表
CREATE TABLE `orders` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_id` VARCHAR(64) NOT NULL COMMENT '订单唯一标识（业务单号）',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `order_type` ENUM('BUY', 'SELL', 'SUBSCRIPTION', 'REDEMPTION') NOT NULL COMMENT '订单类型',
  `amount` DECIMAL(18, 2) NULL COMMENT '订单金额（买入/申购时）',
  `shares` DECIMAL(20, 6) NULL COMMENT '订单份额（卖出/赎回时）',
  `requested_at` DATETIME NOT NULL COMMENT '发起时间',
  `trade_date` DATE NULL COMMENT '交易归属日（由requested_at+cutoff+交易日历推导）',
  `expected_nav_date` DATE NULL COMMENT '预期净值日期（用于计算预期份额/金额）',
  `expected_confirm_date` DATE NULL COMMENT '预期确认日期（trade_date + T+N）',
  `status` ENUM('PENDING', 'CONFIRMED', 'CANCELLED', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '订单状态',
  `fee_estimate` DECIMAL(18, 2) NULL COMMENT '预估手续费',
  `note` VARCHAR(500) NULL COMMENT '备注',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_status` (`status`),
  KEY `idx_expected_confirm_date` (`expected_confirm_date`),
  KEY `idx_requested_at` (`requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单表';

-- 4.2 settlement_confirm - 结算确认表
CREATE TABLE `settlement_confirm` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '结算确认ID',
  `order_id` VARCHAR(64) NOT NULL COMMENT '订单ID（外键orders.order_id）',
  `confirm_date` DATE NOT NULL COMMENT '实际确认日期（可人工覆盖）',
  `confirm_datetime` DATETIME NULL COMMENT '实际确认时间（精确到秒）',
  `nav_date` DATE NOT NULL COMMENT '实际使用的净值日期（可人工覆盖）',
  `confirm_nav` DECIMAL(18, 6) NOT NULL COMMENT '实际确认净值（可人工覆盖）',
  `confirm_shares` DECIMAL(20, 6) NULL COMMENT '实际确认份额（买入/申购时）',
  `confirm_amount` DECIMAL(18, 2) NULL COMMENT '实际确认金额（卖出/赎回时）',
  `confirm_fee` DECIMAL(18, 2) NULL COMMENT '实际手续费',
  `is_manual_override` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否人工覆盖（用于标记用户手动修正）',
  `confirmed_by_user_id` BIGINT NULL COMMENT '确认人用户ID',
  `confirmed_at` DATETIME NOT NULL COMMENT '确认时间',
  `note` VARCHAR(500) NULL COMMENT '备注',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_confirm_date` (`confirm_date`),
  KEY `idx_nav_date` (`nav_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='结算确认表';

-- 4.3 order_funding_line - 订单资金来源拆分表
CREATE TABLE `order_funding_line` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资金来源行ID',
  `order_id` VARCHAR(64) NOT NULL COMMENT '订单ID（外键orders.order_id）',
  `line_no` INT NOT NULL COMMENT '行号（同一订单内从1开始递增）',
  `account_id` BIGINT NOT NULL COMMENT '资金来源账户ID（外键accounts.id，必须是叶子账户）',
  `amount` DECIMAL(18, 2) NOT NULL COMMENT '出资金额',
  `currency` ENUM('CNY', 'USD', 'HKD') NOT NULL DEFAULT 'CNY' COMMENT '货币',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_line` (`order_id`, `line_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_account_id` (`account_id`),
  CONSTRAINT `fk_funding_line_order` FOREIGN KEY (`order_id`) REFERENCES `orders`(`order_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_funding_line_account` FOREIGN KEY (`account_id`) REFERENCES `accounts`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单资金来源拆分表';

-- ============================================
-- 5. 持仓与快照表（修订：增加dirty标记和收益拆分）
-- ============================================

-- 5.1 holdings_snapshot - 持仓快照表
CREATE TABLE `holdings_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `snapshot_date` DATE NOT NULL COMMENT '快照日期',
  `shares` DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '持仓份额（由流水推导）',
  `cost` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '持仓成本（由流水推导，默认加权平均）',
  `cost_method` ENUM('AVERAGE', 'FIFO') NOT NULL DEFAULT 'AVERAGE' COMMENT '成本核算方法',
  `nav` DECIMAL(18, 6) NULL COMMENT '当日净值/价格',
  `nav_date` DATE NULL COMMENT '净值日期（可能滞后，如QDII）',
  `market_value` DECIMAL(18, 2) NULL COMMENT '持仓市值',
  `unrealized_pnl` DECIMAL(18, 2) NULL COMMENT '浮动盈亏',
  `return_rate` DECIMAL(10, 6) NULL COMMENT '收益率',
  `fetch_date` DATE NOT NULL COMMENT '采集日（用于看板"今日资产"）',
  `is_dirty` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否脏数据（历史修正后需重算）',
  `dirty_from_date` DATE NULL COMMENT '脏数据起始日期（从该日期起需重算）',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product_date` (`user_id`, `product_id`, `snapshot_date`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_snapshot_date` (`snapshot_date`),
  KEY `idx_fetch_date` (`fetch_date`),
  KEY `idx_is_dirty` (`is_dirty`, `dirty_from_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='持仓快照表';

-- 5.2 net_worth_snapshot - 净资产快照表
CREATE TABLE `net_worth_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `family_id` BIGINT NULL COMMENT '家庭ID（用于家庭汇总）',
  `snapshot_date` DATE NOT NULL COMMENT '快照日期',
  `total_assets` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '总资产',
  `total_liabilities` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '总负债',
  `net_worth` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '净资产',
  `cash_balance` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '现金余额',
  `position_value` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '持仓市值',
  `realized_pnl` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '已实现收益（卖出/赎回实现）',
  `unrealized_pnl` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '未实现收益（持有浮动）',
  `income_pnl` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '收入收益（分红/利息）',
  `fetch_date` DATE NOT NULL COMMENT '采集日',
  `is_dirty` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否脏数据（历史修正后需重算）',
  `dirty_from_date` DATE NULL COMMENT '脏数据起始日期（从该日期起需重算）',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `snapshot_date`),
  KEY `idx_family_id` (`family_id`),
  KEY `idx_snapshot_date` (`snapshot_date`),
  KEY `idx_fetch_date` (`fetch_date`),
  KEY `idx_is_dirty` (`is_dirty`, `dirty_from_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='净资产快照表';

-- ============================================
-- 6. 行情与指标表
-- ============================================

-- 6.1 market_bar_daily - 日线行情表（长期保存）
CREATE TABLE `market_bar_daily` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `trade_date` DATE NOT NULL COMMENT '交易日期',
  `open_price` DECIMAL(18, 6) NULL COMMENT '开盘价',
  `high_price` DECIMAL(18, 6) NULL COMMENT '最高价',
  `low_price` DECIMAL(18, 6) NULL COMMENT '最低价',
  `close_price` DECIMAL(18, 6) NOT NULL COMMENT '收盘价',
  `volume` DECIMAL(20, 2) NULL COMMENT '成交量',
  `amount` DECIMAL(20, 2) NULL COMMENT '成交额',
  `prev_close` DECIMAL(18, 6) NULL COMMENT '昨收价',
  `source` VARCHAR(32) NOT NULL DEFAULT 'AKSHARE' COMMENT '数据源',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_date_source` (`product_id`, `trade_date`, `source`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日线行情表（长期保存）';

-- 6.2 market_quote_realtime - 实时行情表（短期缓存，TTL 7-30天）
CREATE TABLE `market_quote_realtime` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `quote_time` DATETIME NOT NULL COMMENT '行情时间（精确到秒）',
  `price` DECIMAL(18, 6) NOT NULL COMMENT '当前价格',
  `prev_close` DECIMAL(18, 6) NULL COMMENT '昨收价',
  `pct_chg` DECIMAL(10, 6) NULL COMMENT '涨跌幅（%）',
  `volume` DECIMAL(20, 2) NULL COMMENT '成交量',
  `amount` DECIMAL(20, 2) NULL COMMENT '成交额',
  `iopv` DECIMAL(18, 6) NULL COMMENT 'IOPV实时估值（基金份额参考净值）',
  `premium_rate` DECIMAL(10, 6) NULL COMMENT '溢价率',
  `open_price` DECIMAL(18, 6) NULL COMMENT '开盘价',
  `high_price` DECIMAL(18, 6) NULL COMMENT '最高价',
  `low_price` DECIMAL(18, 6) NULL COMMENT '最低价',
  `source` VARCHAR(32) NOT NULL DEFAULT 'AKSHARE' COMMENT '数据源',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_time_source` (`product_id`, `quote_time`, `source`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_quote_time` (`quote_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='实时行情表（短期缓存）';

-- 6.3 nav - 净值表（长期保存）
CREATE TABLE `nav` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `nav_date` DATE NOT NULL COMMENT '净值日期',
  `nav` DECIMAL(18, 6) NOT NULL COMMENT '单位净值',
  `acc_nav` DECIMAL(18, 6) NULL COMMENT '累计净值',
  `daily_return` DECIMAL(10, 6) NULL COMMENT '日收益率',
  `dividend` DECIMAL(18, 2) NULL COMMENT '分红',
  `source` VARCHAR(32) NOT NULL DEFAULT 'FUND' COMMENT '数据源',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_date` (`product_id`, `nav_date`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_nav_date` (`nav_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='净值表（长期保存）';

-- 6.4 indicator_daily - 日更指标表
CREATE TABLE `indicator_daily` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `trade_date` DATE NOT NULL COMMENT '指标日期',
  `window_days` INT NOT NULL COMMENT '窗口天数（如20/60）',
  `pct_rank` DECIMAL(9, 6) NULL COMMENT '分位0~1',
  `q_buy_price` DECIMAL(18, 6) NULL COMMENT '买入分位对应的价格阈值',
  `q_mid_price` DECIMAL(18, 6) NULL COMMENT '50%分位价格',
  `q_high_price` DECIMAL(18, 6) NULL COMMENT '80%分位价格',
  `peak_close` DECIMAL(18, 6) NULL COMMENT '滚动窗口内峰值close',
  `drawdown_from_peak` DECIMAL(9, 6) NULL COMMENT '回撤比例',
  `ma20` DECIMAL(18, 6) NULL COMMENT '20日均线',
  `ma60` DECIMAL(18, 6) NULL COMMENT '60日均线',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_date_window` (`product_id`, `trade_date`, `window_days`),
  KEY `idx_trade_date` (`trade_date`),
  KEY `idx_product_date` (`product_id`, `trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日更指标表';

-- ============================================
-- 7. 策略与建议表（修订：suggestions去重键修正）
-- ============================================

-- 7.1 strategy_config - 策略配置表
CREATE TABLE `strategy_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `strategy_key` VARCHAR(64) NOT NULL COMMENT '策略标识（如percentile/profit_recycle/drawdown）',
  `strategy_version` VARCHAR(32) NOT NULL DEFAULT 'default' COMMENT '策略版本',
  `param_set_id` VARCHAR(64) NOT NULL COMMENT '参数组合ID',
  `param_json` TEXT NOT NULL COMMENT '参数JSON',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_strategy_param` (`strategy_key`, `strategy_version`, `param_set_id`),
  KEY `idx_strategy_key` (`strategy_key`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='策略配置表';

-- 7.2 product_strategy_bind - 产品策略绑定表
CREATE TABLE `product_strategy_bind` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `strategy_key` VARCHAR(64) NOT NULL COMMENT '策略标识',
  `param_set_id` VARCHAR(64) NOT NULL COMMENT '参数集ID',
  `strategy_type` ENUM('VETO', 'TRIGGER', 'SCORE') NOT NULL DEFAULT 'TRIGGER' COMMENT '策略类型',
  `priority` INT NOT NULL DEFAULT 0 COMMENT '优先级',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product_strategy` (`user_id`, `product_id`, `strategy_key`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_strategy_key` (`strategy_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='产品策略绑定表';

-- 7.3 suggestions - 建议表（修订：去重键使用suggestion_day + user_product_id）
CREATE TABLE `suggestions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `suggestion_id` VARCHAR(64) NOT NULL COMMENT '建议唯一标识',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `user_product_id` BIGINT NOT NULL COMMENT '用户产品ID（外键user_product.id）',
  `product_id` BIGINT NOT NULL COMMENT '产品ID（冗余，便于查询）',
  `suggestion_day` DATE NOT NULL COMMENT '建议日期（由generated_at截断，用于去重）',
  `suggestion_type` ENUM('BUY', 'SELL', 'HOLD', 'WAIT', 'SKIP', 'WARNING') NOT NULL COMMENT '建议类型',
  `reason` TEXT NOT NULL COMMENT '触发理由（可解释中文）',
  `reason_payload` JSON NULL COMMENT '触发理由与指标快照（JSON，用于复盘）',
  `action_payload` JSON NULL COMMENT '建议动作参数（JSON：金额/目标仓位/阈值等）',
  `priority` ENUM('HIGH', 'MEDIUM', 'LOW') NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级',
  `confidence` DECIMAL(5, 2) NULL COMMENT '信心度（0-100）',
  `strategy_code` VARCHAR(64) NOT NULL COMMENT '策略代码',
  `strategy_version` VARCHAR(32) NOT NULL DEFAULT 'default' COMMENT '策略版本',
  `generated_at` DATETIME NOT NULL COMMENT '生成时间',
  `expires_at` DATETIME NULL COMMENT '过期时间',
  `status` ENUM('NEW', 'DONE', 'SNOOZE', 'IGNORE', 'EXPIRED') NOT NULL DEFAULT 'NEW' COMMENT '状态',
  `executed_at` DATETIME NULL COMMENT '执行时间（用户标记已执行）',
  `executed_order_id` VARCHAR(64) NULL COMMENT '关联订单ID（如已执行）',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_suggestion_id` (`suggestion_id`),
  UNIQUE KEY `uk_user_product_day_strategy` (`user_product_id`, `strategy_version`, `suggestion_day`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_product_id` (`user_product_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_status` (`status`),
  KEY `idx_suggestion_day` (`suggestion_day`),
  KEY `idx_generated_at` (`generated_at`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='建议表';

-- ============================================
-- 8. 基本面三层表（可选扩展）
-- ============================================

-- 8.1 product_tags - 产品标签表（层1）
CREATE TABLE `product_tags` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `tag_type` ENUM('WHITELIST', 'BLACKLIST', 'RISK', 'CORE') NOT NULL COMMENT '标签类型',
  `tag_value` VARCHAR(64) NULL COMMENT '标签值（如风险等级、核心等级）',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product_tag` (`user_id`, `product_id`, `tag_type`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='产品标签表（层1：长期策略层）';

-- 8.2 portfolio_targets - 组合目标表（层2）
CREATE TABLE `portfolio_targets` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `target_weight` DECIMAL(10, 6) NOT NULL COMMENT '目标权重（0-1）',
  `min_weight` DECIMAL(10, 6) NULL COMMENT '最小权重',
  `max_weight` DECIMAL(10, 6) NULL COMMENT '最大权重',
  `rebalance_threshold` DECIMAL(10, 6) NULL COMMENT '再平衡阈值',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='组合目标表（层2：资产配置层）';

-- 8.3 trading_gears - 交易档位表（层3）
CREATE TABLE `trading_gears` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `gear_level` ENUM('OFF', 'LOW', 'MEDIUM', 'HIGH') NOT NULL DEFAULT 'MEDIUM' COMMENT '档位：OFF=关闭，LOW=低档，MEDIUM=中档，HIGH=高档',
  `gear_reason` VARCHAR(255) NULL COMMENT '档位原因（基本面影响）',
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='交易档位表（层3：执行层）';

-- ============================================
-- 9. 回测实验室表（一等模块）
-- ============================================

-- 9.1 backtest_plan - 回测计划表
CREATE TABLE `backtest_plan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '计划ID',
  `plan_code` VARCHAR(64) NOT NULL COMMENT '计划代码（唯一标识）',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `plan_name` VARCHAR(128) NOT NULL COMMENT '计划名称',
  `product_ids` JSON NOT NULL COMMENT '产品ID列表（JSON数组）',
  `strategy_key` VARCHAR(64) NOT NULL COMMENT '策略标识',
  `strategy_version` VARCHAR(32) NOT NULL DEFAULT 'default' COMMENT '策略版本',
  `param_space_json` TEXT NOT NULL COMMENT '参数空间（JSON，定义参数范围/枚举值）',
  `start_date` DATE NOT NULL COMMENT '回测开始日期',
  `end_date` DATE NOT NULL COMMENT '回测结束日期',
  `initial_cash` DECIMAL(20, 2) NOT NULL COMMENT '初始资金',
  `cost_method` ENUM('AVERAGE', 'FIFO') NOT NULL DEFAULT 'AVERAGE' COMMENT '成本核算方法',
  `fee_model_json` TEXT NULL COMMENT '手续费模型（JSON：费率/最低手续费等）',
  `data_source` VARCHAR(32) NOT NULL DEFAULT 'AKSHARE' COMMENT '数据源',
  `status` ENUM('DRAFT', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED') NOT NULL DEFAULT 'DRAFT' COMMENT '计划状态',
  `note` VARCHAR(500) NULL COMMENT '备注',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_code` (`plan_code`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_strategy` (`strategy_key`, `strategy_version`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='回测计划表';

-- 9.2 backtest_run - 回测运行表
CREATE TABLE `backtest_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '运行ID',
  `run_code` VARCHAR(64) NOT NULL COMMENT '运行代码（唯一标识）',
  `plan_id` BIGINT NOT NULL COMMENT '计划ID（外键backtest_plan.id）',
  `param_set_id` VARCHAR(64) NOT NULL COMMENT '参数集ID（来自param_space的某个组合）',
  `param_json` TEXT NOT NULL COMMENT '参数JSON（实际使用的参数值）',
  `status` ENUM('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '运行状态',
  `started_at` DATETIME NULL COMMENT '开始时间',
  `completed_at` DATETIME NULL COMMENT '完成时间',
  `initial_cash` DECIMAL(20, 2) NOT NULL COMMENT '初始资金',
  `final_value` DECIMAL(20, 2) NULL COMMENT '最终总资产',
  `total_return` DECIMAL(10, 6) NULL COMMENT '总收益率',
  `annual_return` DECIMAL(10, 6) NULL COMMENT '年化收益率',
  `max_drawdown` DECIMAL(10, 6) NULL COMMENT '最大回撤',
  `sharpe_ratio` DECIMAL(10, 6) NULL COMMENT '夏普比率',
  `trade_count` INT NULL COMMENT '成交次数',
  `win_rate` DECIMAL(10, 6) NULL COMMENT '胜率',
  `total_fees` DECIMAL(20, 2) NULL COMMENT '手续费总额',
  `fee_ratio` DECIMAL(10, 6) NULL COMMENT '手续费占收益比例',
  `artifact_path` VARCHAR(512) NULL COMMENT '结果文件路径（csv.gz/parquet，每日曲线/交易明细）',
  `error_message` TEXT NULL COMMENT '错误信息（如失败）',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_code` (`run_code`),
  KEY `idx_plan_id` (`plan_id`),
  KEY `idx_param_set_id` (`param_set_id`),
  KEY `idx_status` (`status`),
  KEY `idx_total_return` (`total_return`),
  KEY `idx_annual_return` (`annual_return`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='回测运行表';

-- 回测实验室设计说明：
-- 1. 回测与生产隔离：回测只读行情与历史数据，不写生产账本真相表
-- 2. 结果存储：摘要指标存DB，明细数据存文件（csv.gz/parquet）
-- 3. 参数推广：将最佳参数集写回strategy_config + product_strategy_bind（需二次确认）
-- 4. 清理策略：结果文件可配置TTL（如保留90天），过期自动清理

-- ============================================
-- 10. Python任务跟踪表
-- ============================================

-- 10.1 py_job - Python任务跟踪表
CREATE TABLE `py_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_id` VARCHAR(64) NOT NULL COMMENT '任务唯一标识',
  `job_type` ENUM('BACKTEST', 'MARKET_SYNC', 'INDICATOR_CALC', 'STRATEGY_EXEC', 'REPORT_EXPORT', 'SNAPSHOT_RECALC') NOT NULL COMMENT '任务类型',
  `status` ENUM('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
  `progress` INT NOT NULL DEFAULT 0 COMMENT '进度（0-100）',
  `input_json` TEXT NULL COMMENT '输入参数（JSON）',
  `output_json` TEXT NULL COMMENT '输出结果（JSON，摘要数据）',
  `artifact_path` VARCHAR(512) NULL COMMENT '结果文件路径（csv.gz/parquet/pdf等）',
  `error` TEXT NULL COMMENT '错误信息（如失败）',
  `started_at` DATETIME NULL COMMENT '开始时间',
  `completed_at` DATETIME NULL COMMENT '完成时间',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_id` (`job_id`),
  KEY `idx_job_type` (`job_type`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Python任务跟踪表';

-- Python任务跟踪表设计说明：
-- 1. 异步任务跟踪：Java启动Python脚本后，通过此表跟踪任务状态
-- 2. 前端轮询：前端通过 /api/jobs/{job_id} 轮询任务状态和进度
-- 3. 结果存储：摘要数据存output_json，大文件存artifact_path
-- 4. 清理策略：已完成/失败的任务可配置TTL（如保留30天）

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 关键说明与视图
-- ============================================

-- 关键设计说明：
-- 1. 借贷方向规则（全局统一）：
--    - amount永远为正数，posting_type决定方向
--    - 资产类账户（CASH/POSITION/RECEIVABLE）：
--      DEBIT=增加，CREDIT=减少
--      余额公式：balance = initial_balance + SUM(DEBIT) - SUM(CREDIT)
--    - 负债类账户（LIABILITY）：
--      DEBIT=减少，CREDIT=增加
--      余额公式：balance = initial_balance + SUM(CREDIT) - SUM(DEBIT)
--    - 费用类账户（FEE/EXPENSE）：
--      DEBIT=费用发生，CREDIT=费用冲减
--      余额公式：balance = SUM(DEBIT) - SUM(CREDIT)
--    - 收入类账户（INCOME）：
--      DEBIT=收入冲减，CREDIT=收入发生
--      余额公式：balance = SUM(CREDIT) - SUM(DEBIT)
--
-- 2. 账户余额口径：
--    - balance: 账面余额（由流水推导）
--    - reserved_amount: 占用/冻结金额（下单占用，结算确认后释放）
--    - available_for_trade: 可用购买力 = balance - reserved_amount
--    - available_for_withdraw: 可取资金 = balance - 未交收卖出款 - reserved_amount
--
-- 3. 下单占用机制（场外T+N）：
--    - 下单阶段：reserved_amount += 订单金额（不生成流水，不扣款）
--    - 结算确认：reserved_amount -= 订单金额，生成真实流水（扣款+持仓增加）
--    - 取消订单：reserved_amount -= 订单金额（不生成流水）
--
-- 4. 三日期模型：
--    - requested_at: 发起时间
--    - nav_date: 使用的净值日期（可不同于trade_date）
--    - confirm_date: 到账/确认日期（可人工覆盖）
--
-- 5. 快照脏数据标记：
--    - 历史修正后：标记is_dirty=1，设置dirty_from_date
--    - 后台任务：从dirty_from_date起逐日链式重建
--
-- 6. 父子账户/资金分区（信封系统）：
--    - 父账户（ROOT）= 平台容器/分组节点（余利宝、稳利宝、华宝证券等），用于UI聚合与筛选
--    - 子账户（BUCKET/叶子账户）= 资金信封（生活费/房租/项目/理财金/安全金/待分配等），真实金额只存在子账户
--    - 父账户不参与任何记账分录，禁止余额编辑，balance字段不作为真实余额来源（保持0或不可编辑）
--    - 父账户展示余额 = Σ(子账户叶子余额)（仅展示层聚合）
--    - ledger_posting.account_id 只允许引用【叶子账户】（即不存在任何child的账户）。父账户禁止出现在任何记账分录中
--    - fund_usage约束范围：只对account_kind=REAL且account_type=CASH且为叶子账户的场景做约束校验
--    - 每个平台父账户必须至少有一个默认子账户：xxx_unallocated（待分配/自由资金）
--
-- 7. 国债逆回购（BOND_REPO）：
--    - 华宝证券账户内所有资金都允许做1天期国债逆回购（包括RESERVED专款）
--    - 记账方式：占用/释放 + 利息入账，不生成持仓POSITION
--    - 下单日：创建REPO订单（PENDING），仅增加source_account.reserved_amount（锁定本金）
--    - 到期日：确认订单（CONFIRMED），必须按顺序：1) 校验订单到期；2) 释放占用；3) 生成利息ledger_txn
--    - 看板计算：repo_locked_amount必须用订单汇总计算（Σ(所有REPO订单的principal)），不能用reserved_amount的一部分

-- ============================================
-- 辅助视图（可选，用于快速查询）
-- ============================================

-- 视图：账户余额计算（资产类账户）
CREATE OR REPLACE VIEW `v_account_balance_asset` AS
SELECT 
  a.id,
  a.account_code,
  a.account_name,
  a.initial_balance,
  a.balance AS current_balance,
  a.reserved_amount,
  a.initial_balance + COALESCE(SUM(
    CASE 
      WHEN lp.posting_type = 'DEBIT' THEN lp.amount
      WHEN lp.posting_type = 'CREDIT' THEN -lp.amount
      ELSE 0
    END
  ), 0) AS calculated_balance,
  a.balance - a.reserved_amount AS available_for_trade
FROM accounts a
LEFT JOIN ledger_posting lp ON a.id = lp.account_id 
  AND lp.account_type IN ('CASH', 'POSITION', 'RECEIVABLE')
WHERE a.account_kind = 'REAL' 
  AND a.account_type NOT IN ('CREDIT_CARD', 'HUABEI', 'BAITIAO', 'LOAN')
GROUP BY a.id, a.account_code, a.account_name, a.initial_balance, a.balance, a.reserved_amount;

-- 视图：账户余额计算（负债类账户）
CREATE OR REPLACE VIEW `v_account_balance_liability` AS
SELECT 
  a.id,
  a.account_code,
  a.account_name,
  a.initial_balance,
  a.balance AS current_balance,
  a.initial_balance + COALESCE(SUM(
    CASE 
      WHEN lp.posting_type = 'CREDIT' THEN lp.amount
      WHEN lp.posting_type = 'DEBIT' THEN -lp.amount
      ELSE 0
    END
  ), 0) AS calculated_balance
FROM accounts a
LEFT JOIN ledger_posting lp ON a.id = lp.account_id 
  AND lp.account_type = 'LIABILITY'
WHERE a.account_kind = 'REAL' 
  AND a.account_type IN ('CREDIT_CARD', 'HUABEI', 'BAITIAO', 'LOAN')
GROUP BY a.id, a.account_code, a.account_name, a.initial_balance, a.balance;

-- 视图：待结算清单（用于首页展示）
CREATE OR REPLACE VIEW `v_pending_settlements` AS
SELECT 
  o.id,
  o.order_id,
  o.user_id,
  o.product_id,
  pm.product_name,
  pm.product_code,
  o.order_type,
  o.amount,
  o.shares,
  o.requested_at,
  o.trade_date,
  o.expected_nav_date,
  o.expected_confirm_date,
  o.status,
  CASE 
    WHEN o.order_type IN ('BUY', 'SUBSCRIPTION') THEN o.amount / COALESCE(n.nav, 1.0)
    WHEN o.order_type IN ('SELL', 'REDEMPTION') THEN o.shares * COALESCE(n.nav, 1.0)
    ELSE NULL
  END AS preview_amount_or_shares
FROM orders o
INNER JOIN product_master pm ON o.product_id = pm.id
LEFT JOIN nav n ON pm.id = n.product_id 
  AND n.nav_date = o.expected_nav_date
WHERE o.status = 'PENDING'
ORDER BY o.expected_confirm_date ASC;

-- 视图：今日建议清单（用于首页展示）
CREATE OR REPLACE VIEW `v_today_suggestions` AS
SELECT 
  s.id,
  s.suggestion_id,
  s.user_id,
  s.product_id,
  pm.product_name,
  pm.product_code,
  s.suggestion_type,
  s.reason,
  s.priority,
  s.confidence,
  s.strategy_code,
  s.generated_at,
  s.expires_at,
  s.status
FROM suggestions s
INNER JOIN product_master pm ON s.product_id = pm.id
WHERE s.status = 'NEW'
  AND s.suggestion_day = CURDATE()
  AND (s.expires_at IS NULL OR s.expires_at >= NOW())
ORDER BY 
  CASE s.priority 
    WHEN 'HIGH' THEN 1 
    WHEN 'MEDIUM' THEN 2 
    WHEN 'LOW' THEN 3 
  END,
  s.generated_at DESC;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- DDL脚本完成
-- ============================================
-- 
-- 后续步骤：
-- 1. 执行 DML.sql 初始化虚拟账户和管理员用户
-- 2. 如需更新现有数据库，请查看 updatesql 目录下的补丁脚本
-- ============================================