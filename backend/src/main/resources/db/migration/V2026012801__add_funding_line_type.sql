-- 为 order_funding_line 表添加 line_type 字段
-- SOURCE: 出金账户（买入时扣款来源，卖出时份额来源）
-- TARGET: 到账账户（卖出/赎回时资金到账的账户）
ALTER TABLE order_funding_line 
ADD COLUMN line_type VARCHAR(20) DEFAULT 'SOURCE' COMMENT '行类型: SOURCE=出金来源, TARGET=到账目标';

-- 更新已有数据，默认为 SOURCE
UPDATE order_funding_line SET line_type = 'SOURCE' WHERE line_type IS NULL;
