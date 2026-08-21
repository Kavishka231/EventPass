CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE REFERENCES payments(id),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_reference VARCHAR(100) UNIQUE,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    attempted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failure_code VARCHAR(100),
    last_error VARCHAR(500),
    reconciliation_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refunds_booking ON refunds(booking_id);
CREATE INDEX idx_refunds_reconciliation_status
    ON refunds(reconciliation_status)
    WHERE reconciliation_status = 'PENDING';
