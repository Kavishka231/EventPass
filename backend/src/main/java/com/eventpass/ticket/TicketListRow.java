package com.eventpass.ticket;

import java.time.Instant;
import java.util.UUID;

public record TicketListRow(
    UUID id,
    String ticketNumber,
    UUID bookingId,
    UUID eventSeatId,
    String qrToken,
    Ticket.Status status,
    Instant issuedAt) {}
