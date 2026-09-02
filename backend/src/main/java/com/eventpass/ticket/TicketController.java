package com.eventpass.ticket;

import com.eventpass.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
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
      Instant issuedAt,
      Instant usedAt,
      String bookingReference,
      EventSummary event,
      VenueSummary venue,
      SeatSummary seat) {}

  public record EventSummary(UUID id, String name, Instant startDateTime, Instant endDateTime) {}

  public record VenueSummary(UUID id, String name, String address, String city) {}

  public record SeatSummary(
      UUID id, String section, String row, String number, com.eventpass.seat.Seat.Type type) {}

  public record ValidateTicketRequest(
      @NotBlank @Size(max = 128) String qrToken, @NotNull UUID eventId) {}

  public record ValidationResponse(
      UUID ticketId,
      String ticketNumber,
      UUID eventId,
      UUID eventSeatId,
      Ticket.Status status,
      String eventName,
      Instant eventStartDateTime) {}

  public record RedemptionResponse(
      UUID ticketId, String ticketNumber, UUID eventId, Ticket.Status status, Instant usedAt) {}

  @GetMapping
  Page<TicketResponse> list(
      @AuthenticationPrincipal User u,
      @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.list(u, pageable);
  }

  @GetMapping("/{id}")
  TicketResponse get(@PathVariable UUID id, @AuthenticationPrincipal User u) {
    return service.get(id, u);
  }

  @PostMapping("/validate")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  ValidationResponse validate(
      @Valid @RequestBody ValidateTicketRequest request, @AuthenticationPrincipal User actor) {
    return service.validate(request, actor);
  }

  @PostMapping("/redeem")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  RedemptionResponse redeem(
      @Valid @RequestBody ValidateTicketRequest request, @AuthenticationPrincipal User actor) {
    return service.redeem(request, actor);
  }
}
