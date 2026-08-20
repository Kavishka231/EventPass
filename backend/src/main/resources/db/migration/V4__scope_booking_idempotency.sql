ALTER TABLE bookings
    DROP CONSTRAINT IF EXISTS bookings_idempotency_key_key;

ALTER TABLE bookings
    ADD COLUMN idempotency_operation VARCHAR(40),
    ADD COLUMN idempotency_request_hash VARCHAR(64);

UPDATE bookings
SET idempotency_operation = 'BOOKING_CREATE',
    idempotency_request_hash = encode(sha256(('legacy:' || id::text)::bytea), 'hex');

ALTER TABLE bookings
    ALTER COLUMN idempotency_operation SET NOT NULL,
    ALTER COLUMN idempotency_request_hash SET NOT NULL;

ALTER TABLE bookings
    ADD CONSTRAINT uk_booking_idempotency_scope
        UNIQUE (user_id, idempotency_operation, idempotency_key);
