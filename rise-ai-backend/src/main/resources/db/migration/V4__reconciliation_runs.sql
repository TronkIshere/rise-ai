CREATE TABLE reconciliation_runs (
    id CHAR(36) NOT NULL,
    run_code VARCHAR(30) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    triggered_by CHAR(36) NULL,
    run_status VARCHAR(20) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NOT NULL,
    accounts_checked INT NOT NULL,
    inconsistent_accounts INT NOT NULL,
    findings JSON NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reconciliation_runs_code UNIQUE (run_code),
    CONSTRAINT fk_reconciliation_runs_trigger FOREIGN KEY (triggered_by) REFERENCES users (id),
    CONSTRAINT ck_reconciliation_runs_counts CHECK (accounts_checked >= 0 AND inconsistent_accounts >= 0 AND inconsistent_accounts <= accounts_checked),
    INDEX idx_reconciliation_runs_started (started_at),
    INDEX idx_reconciliation_runs_status (run_status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TRIGGER trg_reconciliation_runs_no_update BEFORE UPDATE ON reconciliation_runs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reconciliation_runs is append-only';
CREATE TRIGGER trg_reconciliation_runs_no_delete BEFORE DELETE ON reconciliation_runs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reconciliation_runs is append-only';
