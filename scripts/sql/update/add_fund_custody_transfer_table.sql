-- ============================================================
-- 场外基金转托管到场内（LOF）记录表
-- ============================================================
-- 用于记录基金从场外渠道(OTC)转入场内渠道(EXCHANGE)的历史事件，
-- 目前仅做记录，不直接参与持仓计算，后续可在持仓/快照计算中引用。

CREATE TABLE IF NOT EXISTS fund_custody_transfer (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  product_code VARCHAR(32) NOT NULL COMMENT '基金代码，例如 163406',
  from_channel VARCHAR(16) NOT NULL DEFAULT 'OTC' COMMENT '转出渠道，通常为 OTC',
  to_channel   VARCHAR(16) NOT NULL DEFAULT 'EXCHANGE' COMMENT '转入渠道，通常为 EXCHANGE',
  transfer_date DATE NOT NULL COMMENT '转托管生效日期（按交易日）',
  transfer_shares DECIMAL(24,6) NOT NULL COMMENT '转托管份额',
  note VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_fct_product_date (product_code, transfer_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场外基金转托管到场内记录表';


