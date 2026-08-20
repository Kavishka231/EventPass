ALTER TABLE payments ALTER COLUMN payment_reference DROP NOT NULL;

ALTER TABLE payments
    ADD COLUMN attempted_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN failure_code VARCHAR(100),
    ADD COLUMN last_error VARCHAR(500),
    ADD COLUMN reconciliation_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED';

UPDATE payments
SET attempted_at = created_at,
    completed_at = updated_at
WHERE status IN ('SUCCESS', 'FAILED', 'REFUNDED');

CREATE INDEX idx_payments_reconciliation_status
    ON payments (reconciliation_status)
    WHERE reconciliation_status = 'PENDING';
