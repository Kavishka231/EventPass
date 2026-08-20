package com.eventpass.booking;

import com.eventpass.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@Validated
public class BookingController {
  private final BookingService service;

  public BookingController(BookingService service) {
    this.service = service;
  }

  public record CreateBookingRequest(
      @NotNull UUID eventId,
      @NotEmpty @Size(max = 10) List<UUID> eventSeatIds,
      @NotBlank @Size(max = 200) String paymentToken) {}

  public record BookingResponse(
      UUID id,
      String reference,
      UUID eventId,
      Booking.Status status,
      BigDecimal totalAmount,
      String currency,
      List<UUID> eventSeatIds,
      Instant createdAt) {}

  @PostMapping
  ResponseEntity<BookingResponse> create(
      @Valid @RequestBody CreateBookingRequest r,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String key,
      @AuthenticationPrincipal User u) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r, key, u));
  }

  @GetMapping
  List<BookingResponse> list(@AuthenticationPrincipal User u) {
    return service.list(u);
  }

  @GetMapping("/{id}")
  BookingResponse get(@PathVariable UUID id, @AuthenticationPrincipal User u) {
    return service.get(id, u);
  }

  @PostMapping("/{id}/cancel")
  ResponseEntity<Void> cancel(@PathVariable UUID id, @AuthenticationPrincipal User u) {
    service.cancel(id, u);
    return ResponseEntity.noContent().build();
  }
}
