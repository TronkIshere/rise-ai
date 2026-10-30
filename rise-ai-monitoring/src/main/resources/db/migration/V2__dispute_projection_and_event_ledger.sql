CREATE TABLE rise_dispute_events (
    event_id VARCHAR(64) NOT NULL,
    dispute_id VARCHAR(80) NOT NULL,
    milestone_id VARCHAR(80) NOT NULL,
    campaign_id VARCHAR(36) NULL,
    occurred_at TIMESTAMP NOT NULL,
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_rise_dispute_events_milestone ON rise_dispute_events (milestone_id, occurred_at);

CREATE TABLE monitoring_event_ledger (
    source VARCHAR(100) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    registered_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    result_json CLOB NULL,
    PRIMARY KEY (source, event_id)
);

CREATE INDEX idx_monitoring_event_ledger_registered ON monitoring_event_ledger (registered_at);
