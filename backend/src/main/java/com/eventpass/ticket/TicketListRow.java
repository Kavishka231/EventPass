package com.eventpass.ticket;

import com.eventpass.seat.Seat;
import java.time.Instant;
import java.util.UUID;

public record TicketListRow(
    UUID id,
    String ticketNumber,
    UUID bookingId,
    UUID eventSeatId,
    String qrToken,
    Ticket.Status status,
    Instant issuedAt,
    Instant usedAt,
    String bookingReference,
    UUID eventId,
    String eventName,
    Instant eventStartDateTime,
    Instant eventEndDateTime,
    UUID venueId,
    String venueName,
    String venueAddress,
    String venueCity,
    UUID seatId,
    String section,
    String rowNumber,
    String seatNumber,
    Seat.Type seatType) {}
