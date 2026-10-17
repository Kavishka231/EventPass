ALTER TABLE payments
    ADD COLUMN reconciliation_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_reconciliation_at TIMESTAMPTZ,
    ADD COLUMN last_reconciled_at TIMESTAMPTZ;

ALTER TABLE refunds
    ADD COLUMN reconciliation_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_reconciliation_at TIMESTAMPTZ,
    ADD COLUMN last_reconciled_at TIMESTAMPTZ;

UPDATE payments SET next_reconciliation_at = CURRENT_TIMESTAMP
WHERE reconciliation_status = 'PENDING';
UPDATE refunds SET next_reconciliation_at = CURRENT_TIMESTAMP
WHERE reconciliation_status = 'PENDING';

DROP INDEX idx_payments_reconciliation_status;
DROP INDEX idx_refunds_reconciliation_status;

CREATE INDEX idx_payments_reconciliation_due
    ON payments (next_reconciliation_at, created_at)
    WHERE reconciliation_status IN ('PENDING', 'IN_PROGRESS');
CREATE INDEX idx_refunds_reconciliation_due
    ON refunds (next_reconciliation_at, created_at)
    WHERE reconciliation_status IN ('PENDING', 'IN_PROGRESS');

ALTER TABLE payments ADD CONSTRAINT chk_payments_reconciliation_attempts
    CHECK (reconciliation_attempts >= 0);
ALTER TABLE refunds ADD CONSTRAINT chk_refunds_reconciliation_attempts
    CHECK (reconciliation_attempts >= 0);
