CREATE TABLE chain_anchors (
    id CHAR(36) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    record_count BIGINT NOT NULL,
    root VARCHAR(66) NOT NULL,
    previous_root VARCHAR(66) NOT NULL,
    status VARCHAR(12) NOT NULL,
    tx_hash VARCHAR(66) NULL,
    last_error VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_chain_anchors_status CHECK (status IN ('PENDING', 'SUBMITTED', 'CONFIRMED', 'FAILED')),
    CONSTRAINT ck_chain_anchors_kind CHECK (kind IN ('LEDGER', 'AUDIT', 'FINDINGS')),
    INDEX idx_chain_anchors_kind (kind, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
