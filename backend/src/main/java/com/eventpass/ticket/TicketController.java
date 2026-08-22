package com.eventpass.ticket;

import com.eventpass.user.User;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
  private final TicketRepository tickets;

  public TicketController(TicketRepository tickets) {
    this.tickets = tickets;
  }

  public record TicketResponse(
      UUID id,
      String ticketNumber,
      UUID bookingId,
      UUID eventSeatId,
      String qrToken,
      Ticket.Status status,
      Instant issuedAt) {}

  @GetMapping
  @Transactional(readOnly = true)
  Page<TicketResponse> list(
      @AuthenticationPrincipal User u,
      @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return tickets
        .findAllByBookingUserId(u.getId(), pageable)
        .map(
            t ->
                new TicketResponse(
                    t.getId(),
                    t.getTicketNumber(),
                    t.getBooking().getId(),
                    t.getEventSeat().getId(),
                    t.getQrToken(),
                    t.getStatus(),
                    t.getIssuedAt()));
  }
}
