package com.eventpass.booking;

import com.eventpass.payment.Payment;
import com.eventpass.payment.Refund;
import com.eventpass.seat.Seat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingDetailRow(
    UUID id,
    String reference,
    Booking.Status status,
    BigDecimal totalAmount,
    String currency,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt,
    UUID eventId,
    String eventName,
    Instant eventStartDateTime,
    Instant eventEndDateTime,
    UUID venueId,
    String venueName,
    String venueAddress,
    String venueCity,
    UUID eventSeatId,
    UUID seatId,
    String section,
    String rowNumber,
    String seatNumber,
    Seat.Type seatType,
    BigDecimal unitPrice,
    Payment.Status paymentStatus,
    Instant paymentAttemptedAt,
    Instant paymentCompletedAt,
    Refund.Status refundStatus,
    BigDecimal refundAmount,
    Instant refundAttemptedAt,
    Instant refundCompletedAt) {}
