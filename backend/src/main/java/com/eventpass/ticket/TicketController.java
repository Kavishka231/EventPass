package com.eventpass.ticket;

import com.eventpass.user.User;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
  private final TicketService service;

  public TicketController(TicketService service) {
    this.service = service;
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
  Page<TicketResponse> list(
      @AuthenticationPrincipal User u,
      @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.list(u, pageable);
  }
}
