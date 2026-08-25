package com.eventpass.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminBookingRow(
    UUID id,
    String reference,
    UUID eventId,
    String eventName,
    UUID customerId,
    String customerEmail,
    Booking.Status status,
    BigDecimal totalAmount,
    String currency,
    Instant createdAt) {}
