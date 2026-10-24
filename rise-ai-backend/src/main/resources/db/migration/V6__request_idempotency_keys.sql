ALTER TABLE disbursement_requests
    ADD COLUMN idempotency_key VARCHAR(100) NULL AFTER request_reason,
    ADD CONSTRAINT uk_disbursements_requester_idempotency UNIQUE (requested_by, idempotency_key);

ALTER TABLE refund_requests
    ADD COLUMN idempotency_key VARCHAR(100) NULL AFTER reason,
    ADD CONSTRAINT uk_refunds_requester_idempotency UNIQUE (requested_by, idempotency_key);
