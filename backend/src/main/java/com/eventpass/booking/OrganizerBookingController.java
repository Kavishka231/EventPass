package com.eventpass.booking;

import com.eventpass.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizer/events/{eventId}/bookings")
@PreAuthorize("hasRole('ORGANIZER')")
public class OrganizerBookingController {
  private final OrganizerBookingService service;

  public OrganizerBookingController(OrganizerBookingService service) {
    this.service = service;
  }

  public record EventBookingResponse(
      UUID id,
      String reference,
      UUID eventId,
      UUID customerId,
      String customerEmail,
      String customerFirstName,
      String customerLastName,
      Booking.Status status,
      BigDecimal totalAmount,
      String currency,
      List<UUID> eventSeatIds,
      Instant createdAt) {}

  @GetMapping
  Page<EventBookingResponse> report(
      @PathVariable UUID eventId,
      @AuthenticationPrincipal User organizer,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.report(eventId, organizer, pageable);
  }
}
