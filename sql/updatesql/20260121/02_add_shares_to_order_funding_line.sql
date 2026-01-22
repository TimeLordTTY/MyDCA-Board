-- 为order_funding_line表添加shares字段，用于卖出时记录每个子账户的卖出份额
-- 同时修改amount字段为可空（因为卖出时不需要amount）
ALTER TABLE `order_funding_line`
  MODIFY COLUMN `amount` DECIMAL(18, 2) NULL COMMENT '出资金额（买入/申购时使用，卖出/赎回时为NULL）',
  ADD COLUMN `shares` DECIMAL(18, 6) NULL COMMENT '卖出份额（卖出/赎回时使用，买入/申购时为NULL）' AFTER `amount`,
  ADD CONSTRAINT `chk_funding_line_amount_or_shares` CHECK (
    (`amount` IS NOT NULL AND `shares` IS NULL) OR 
    (`amount` IS NULL AND `shares` IS NOT NULL)
  );
