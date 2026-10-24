ALTER TABLE escrow_accounts
    ADD COLUMN total_refund_reserved DECIMAL(19,2) NOT NULL DEFAULT 0 AFTER total_refunded,
    ADD COLUMN total_adjustment_credits DECIMAL(19,2) NOT NULL DEFAULT 0 AFTER total_refund_reserved,
    ADD COLUMN total_adjustment_debits DECIMAL(19,2) NOT NULL DEFAULT 0 AFTER total_adjustment_credits;

ALTER TABLE escrow_accounts DROP CHECK ck_escrow_non_negative;
ALTER TABLE escrow_accounts DROP CHECK ck_escrow_balance_identity;

ALTER TABLE escrow_accounts
    ADD CONSTRAINT ck_escrow_non_negative CHECK (total_confirmed_contributions >= 0 AND total_reserved >= 0 AND total_disbursed >= 0
        AND total_refunded >= 0 AND total_refund_reserved >= 0 AND total_adjustment_credits >= 0 AND total_adjustment_debits >= 0
        AND available_balance >= 0),
    ADD CONSTRAINT ck_escrow_balance_identity CHECK (available_balance = total_confirmed_contributions + total_adjustment_credits
        - total_adjustment_debits - total_reserved - total_refund_reserved - total_disbursed - total_refunded);

ALTER TABLE refund_requests
    ADD COLUMN funds_reserved BOOLEAN NOT NULL DEFAULT FALSE AFTER currency;
