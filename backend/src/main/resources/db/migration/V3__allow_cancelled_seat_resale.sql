ALTER TABLE tickets DROP CONSTRAINT IF EXISTS tickets_event_seat_id_key;
CREATE INDEX idx_tickets_event_seat ON tickets(event_seat_id);
