ALTER TABLE outbox_events
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE outbox_events
SET status = CASE
        WHEN published_at IS NOT NULL THEN 'PUBLISHED'
        WHEN attempts >= 10 THEN 'FAILED'
        ELSE 'PENDING'
    END,
    next_attempt_at = COALESCE(published_at, occurred_at);

DROP INDEX idx_outbox_pending;

CREATE INDEX idx_outbox_delivery_ready
    ON outbox_events(next_attempt_at, occurred_at)
    WHERE status = 'PENDING';
