CREATE TABLE alert_notifications (
    id CHAR(36) NOT NULL,
    kind VARCHAR(60) NOT NULL,
    details JSON NOT NULL,
    status VARCHAR(10) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    last_error VARCHAR(200) NULL,
    failed_at DATETIME(6) NULL,
    delivered_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_alert_notifications_status CHECK (status IN ('PENDING', 'RETRYING', 'SENT', 'FAILED')),
    CONSTRAINT ck_alert_notifications_attempts CHECK (attempts >= 0),
    INDEX idx_alert_notifications_due (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
