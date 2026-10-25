CREATE TABLE rise_processed_events (
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_rise_processed_events_processed ON rise_processed_events (processed_at);

CREATE TABLE rise_evidence_hashes (
    event_id VARCHAR(64) NOT NULL,
    evidence_id VARCHAR(36) NOT NULL,
    file_hash CHAR(64) NOT NULL,
    milestone_id VARCHAR(36) NOT NULL,
    campaign_id VARCHAR(36) NULL,
    occurred_at TIMESTAMP NOT NULL,
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_rise_evidence_hashes_hash ON rise_evidence_hashes (file_hash);

CREATE TABLE rise_disbursement_events (
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    milestone_id VARCHAR(36) NOT NULL,
    campaign_id VARCHAR(36) NULL,
    amount DECIMAL(19, 2) NULL,
    occurred_at TIMESTAMP NOT NULL,
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_rise_disbursement_events_milestone ON rise_disbursement_events (milestone_id, event_type, occurred_at);
