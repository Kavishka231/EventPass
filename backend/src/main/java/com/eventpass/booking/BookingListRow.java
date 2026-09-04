package com.eventpass.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingListRow(
    UUID id,
    String reference,
    UUID eventId,
    String eventName,
    Instant eventStartDateTime,
    UUID venueId,
    String venueName,
    String venueCity,
    Booking.Status status,
    BigDecimal totalAmount,
    String currency,
    long seatCount,
    Instant createdAt) {}
