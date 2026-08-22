ALTER TABLE notifications
    ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN delivery_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_delivery_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN last_delivery_attempt_at TIMESTAMPTZ,
    ADD COLUMN delivered_at TIMESTAMPTZ,
    ADD COLUMN delivery_error VARCHAR(500),
    ADD COLUMN delivery_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_notification_delivery_status
        CHECK (delivery_status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED')),
    ADD CONSTRAINT chk_notification_delivery_attempts
        CHECK (delivery_attempts >= 0);

CREATE INDEX idx_notifications_due_delivery
    ON notifications(next_delivery_at, created_at)
    WHERE delivery_status = 'PENDING';

CREATE INDEX idx_notifications_failed_delivery
    ON notifications(last_delivery_attempt_at)
    WHERE delivery_status = 'FAILED';
