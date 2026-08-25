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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/bookings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {
  private final AdminBookingService service;

  public AdminBookingController(AdminBookingService service) {
    this.service = service;
  }

  public record AdminBookingResponse(
      UUID id,
      String reference,
      UUID eventId,
      String eventName,
      UUID customerId,
      String customerEmail,
      Booking.Status status,
      BigDecimal totalAmount,
      String currency,
      List<UUID> eventSeatIds,
      Instant createdAt) {}

  @GetMapping
  Page<AdminBookingResponse> list(
      @RequestParam(required = false) UUID eventId,
      @RequestParam(required = false) Booking.Status status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.list(eventId, status, pageable);
  }

  @GetMapping("/{id}")
  AdminBookingResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/cancel")
  ResponseEntity<Void> cancel(@PathVariable UUID id, @AuthenticationPrincipal User administrator) {
    service.cancel(id, administrator);
    return ResponseEntity.noContent().build();
  }
}
