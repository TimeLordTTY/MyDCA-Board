-- Deploy manually after review. This file is never run by the agent.
ALTER TABLE settlement_confirm
    ADD COLUMN preview_digest CHAR(64) NULL COMMENT 'Display-only SHA-256 preview digest, never a confirm credential',
    ADD COLUMN ledger_txn_id VARCHAR(32) NULL COMMENT 'Exact settlement ledger transaction ID';
