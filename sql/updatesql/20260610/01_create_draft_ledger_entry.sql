-- Phase3 草稿流水表首版。
-- 该表只保存待确认候选记录，不参与正式账本、账户余额、资产净值或持仓成本计算。

CREATE TABLE IF NOT EXISTS draft_ledger_entry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '草稿主键，用于定位待确认的自动记账候选记录',
    owner_user_id BIGINT NOT NULL COMMENT '草稿归属用户ID，限制个人视角只能处理自己的草稿',
    owner_family_id BIGINT NULL COMMENT '草稿归属家庭ID，用于家庭视角共享待确认草稿',
    source_type VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT '草稿来源类型，例如manual、wechat、import、ocr',
    source_ref VARCHAR(128) NULL COMMENT '外部来源引用号，例如消息ID、导入批次号或截图编号',
    raw_input TEXT NULL COMMENT '原始输入文本或结构化来源摘要，用于人工复核识别结果',
    parsed_payload_json JSON NULL COMMENT '自动解析后的候选记账JSON，不代表正式流水或分录',
    preview_payload_json JSON NULL COMMENT '服务端确认预览JSON，用于展示确认前将生成的记账摘要',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿状态：DRAFT待确认、CONFIRMED已转正式流水、IGNORED已忽略',
    confidence DECIMAL(5,4) NULL COMMENT '解析置信度，仅用于排序和复核提示',
    missing_fields_json JSON NULL COMMENT '缺失字段JSON，用于提示前端或人工补齐信息',
    confirm_txn_id VARCHAR(64) NULL COMMENT '确认后关联的正式流水txn_id，首版快速收支确认会写入该字段',
    confirm_order_id VARCHAR(64) NULL COMMENT '确认后关联的订单order_id，首版暂不自动生成订单但预留追踪字段',
    ignore_reason VARCHAR(255) NULL COMMENT '忽略原因，记录人工为什么不把该草稿转为正式流水',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '草稿创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '草稿最近更新时间',
    confirmed_at DATETIME NULL COMMENT '草稿确认时间，仅状态进入CONFIRMED后写入',
    ignored_at DATETIME NULL COMMENT '草稿忽略时间，仅状态进入IGNORED后写入',
    CONSTRAINT ck_draft_ledger_entry_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'IGNORED')),
    INDEX idx_draft_ledger_owner_user_status (owner_user_id, status, updated_at),
    INDEX idx_draft_ledger_owner_family_status (owner_family_id, status, updated_at),
    INDEX idx_draft_ledger_source (source_type, source_ref),
    INDEX idx_draft_ledger_confirm_txn (confirm_txn_id)
) COMMENT='Phase3 草稿流水表：保存自动记账候选记录，确认前不进入正式账本';
