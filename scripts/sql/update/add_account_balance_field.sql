-- ============================================================
-- 添加账户余额字段
-- ============================================================
-- 在accounts表中添加balance字段，用于存储账户当前余额
-- 每次记账时自动更新，避免每次都从ledger计算

-- 检查字段是否存在，如果不存在则添加
SET @dbname = DATABASE();
SET @tablename = 'accounts';
SET @columnname = 'balance';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' DECIMAL(18, 2) DEFAULT 0.00 COMMENT ''账户当前余额（从初始余额+所有ledger记录计算）'' AFTER `note`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_balance ON accounts(balance);

