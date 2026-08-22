package com.eventpass.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingListRow(
    UUID id,
    String reference,
    UUID eventId,
    Booking.Status status,
    BigDecimal totalAmount,
    String currency,
    Instant createdAt) {}
