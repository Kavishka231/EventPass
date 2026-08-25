package com.eventpass.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventBookingReportRow(
    UUID id,
    String reference,
    UUID customerId,
    String customerEmail,
    String customerFirstName,
    String customerLastName,
    Booking.Status status,
    BigDecimal totalAmount,
    String currency,
    Instant createdAt) {}
