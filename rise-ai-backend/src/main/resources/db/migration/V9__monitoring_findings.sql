CREATE TABLE monitoring_findings (
    id CHAR(36) NOT NULL,
    finding_key CHAR(64) NOT NULL,
    contract_version VARCHAR(20) NOT NULL,
    detector_type VARCHAR(10) NOT NULL,
    rule_id VARCHAR(80) NOT NULL,
    rule_version VARCHAR(20) NOT NULL,
    category VARCHAR(40) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    subject_type VARCHAR(40) NOT NULL,
    subject_id VARCHAR(64) NOT NULL,
    campaign_id CHAR(36) NULL,
    milestone_id CHAR(36) NULL,
    disbursement_request_id CHAR(36) NULL,
    explanation VARCHAR(2000) NOT NULL,
    recommended_action VARCHAR(40) NOT NULL,
    facts JSON NULL,
    review_status VARCHAR(30) NOT NULL,
    occurrence_count INT NOT NULL DEFAULT 0,
    new_occurrences_since_review INT NOT NULL DEFAULT 0,
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    reviewed_by CHAR(36) NULL,
    reviewed_at DATETIME(6) NULL,
    review_note VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_monitoring_findings_key UNIQUE (finding_key),
    CONSTRAINT fk_monitoring_findings_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT ck_monitoring_findings_severity CHECK (severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_monitoring_findings_review CHECK (review_status IN ('OPEN', 'ACKNOWLEDGED', 'CONCERN_CONFIRMED', 'DISMISSED')),
    CONSTRAINT ck_monitoring_findings_counts CHECK (occurrence_count >= 0 AND new_occurrences_since_review >= 0),
    INDEX idx_monitoring_findings_queue (review_status, severity, last_seen_at),
    INDEX idx_monitoring_findings_campaign (campaign_id, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE monitoring_finding_occurrences (
    id CHAR(36) NOT NULL,
    finding_id CHAR(36) NOT NULL,
    source_event_id CHAR(36) NOT NULL,
    actor_id CHAR(36) NULL,
    severity VARCHAR(10) NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_finding_occurrences_event UNIQUE (finding_id, source_event_id),
    CONSTRAINT fk_finding_occurrences_finding FOREIGN KEY (finding_id) REFERENCES monitoring_findings (id),
    INDEX idx_finding_occurrences_finding (finding_id, observed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TRIGGER trg_finding_occurrences_no_update BEFORE UPDATE ON monitoring_finding_occurrences FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monitoring_finding_occurrences is append-only';
CREATE TRIGGER trg_finding_occurrences_no_delete BEFORE DELETE ON monitoring_finding_occurrences FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monitoring_finding_occurrences is append-only';
