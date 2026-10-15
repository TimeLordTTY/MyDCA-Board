-- Apply after the base DDL, on one explicitly selected schema.
-- MySQL 5.7/8: no ADD COLUMN IF NOT EXISTS, no historical data backfill.
-- SOURCE/TARGET fit both accepted nullable contracts: legacy VARCHAR(16)/NULL
-- and the new canonical VARCHAR(20)/SOURCE definition.
-- Existing compatible columns and all existing NULL values stay unchanged.
-- No line_type index: uk_order_line(order_id,line_no) supports order lookups.
DELIMITER $$
DROP PROCEDURE IF EXISTS mydca_20261015_line_type$$
CREATE PROCEDURE mydca_20261015_line_type()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_funding_line'
          AND TABLE_TYPE = 'BASE TABLE' AND ENGINE = 'InnoDB'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'B_DDL_001: order_funding_line InnoDB base table required';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_funding_line'
          AND COLUMN_NAME = 'line_type'
    ) THEN
        ALTER TABLE order_funding_line
            ADD COLUMN line_type VARCHAR(20) NULL DEFAULT 'SOURCE'
            COMMENT 'SOURCE: funding source; TARGET: settlement destination';
    ELSEIF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_funding_line'
          AND COLUMN_NAME = 'line_type'
          AND IS_NULLABLE = 'YES' AND EXTRA = ''
          AND ((COLUMN_TYPE = 'varchar(16)' AND COLUMN_DEFAULT IS NULL)
            OR (COLUMN_TYPE = 'varchar(20)' AND BINARY COLUMN_DEFAULT = BINARY 'SOURCE'))
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'B_DDL_001: incompatible existing line_type; manual review required';
    END IF;
END$$
CALL mydca_20261015_line_type()$$
DROP PROCEDURE mydca_20261015_line_type$$
DELIMITER ;
-- A failed CALL deliberately stops the client. The helper may remain after
-- failure; fix the schema under separate authorization, then rerun this script.
