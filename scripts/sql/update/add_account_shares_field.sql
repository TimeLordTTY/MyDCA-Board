-- ============================================================
-- 添加账户份额字段
-- ============================================================
-- 在accounts表中添加shares字段，用于存储子账户在产品中的份额
-- 购买确认时增加份额，赎回确认时减少份额

-- 检查字段是否存在，如果不存在则添加
SET @dbname = DATABASE();
SET @tablename = 'accounts';
SET @columnname = 'shares';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' DECIMAL(20, 6) DEFAULT 0.000000 COMMENT ''账户在产品中的份额（仅用于PRODUCT_SUB类型账户）'' AFTER `balance`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 添加索引
CREATE INDEX idx_shares ON accounts(shares);

