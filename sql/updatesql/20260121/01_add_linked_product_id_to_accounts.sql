-- 2026-01-21
-- 为 accounts 表增加 linked_product_id 字段，用于账户绑定具体产品（如稳利宝、小荷包等）

ALTER TABLE `accounts`
  ADD COLUMN `linked_product_id` BIGINT NULL COMMENT '关联产品ID（如稳利宝、小荷包等与具体理财/基金产品绑定的账户，可用于初始化持仓）' AFTER `parent_account_id`,
  ADD KEY `idx_linked_product_id` (`linked_product_id`),
  ADD CONSTRAINT `fk_accounts_linked_product`
    FOREIGN KEY (`linked_product_id`) REFERENCES `product_master`(`id`) ON DELETE SET NULL;

