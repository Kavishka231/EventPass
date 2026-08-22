CREATE INDEX idx_bookings_user_created
    ON bookings(user_id, created_at DESC, id);

CREATE INDEX idx_tickets_booking_issued
    ON tickets(booking_id, issued_at DESC, id);
