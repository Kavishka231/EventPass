package com.eventpass.booking;

import com.eventpass.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

  public record CustomerBookingSummaryResponse(
      UUID id,
      String reference,
      Booking.Status status,
      BigDecimal totalAmount,
      String currency,
      long seatCount,
      Instant createdAt,
      EventSummary event,
      VenueSummary venue) {}

  public record EventSummary(UUID id, String name, Instant startDateTime, Instant endDateTime) {}

  public record VenueSummary(UUID id, String name, String address, String city) {}

  public record SeatSummary(
      UUID eventSeatId,
      UUID seatId,
      String section,
      String row,
      String number,
      com.eventpass.seat.Seat.Type type,
      BigDecimal unitPrice) {}

  public record PaymentSummary(
      com.eventpass.payment.Payment.Status status, Instant attemptedAt, Instant completedAt) {}

  public record RefundSummary(
      com.eventpass.payment.Refund.Status status,
      BigDecimal amount,
      Instant attemptedAt,
      Instant completedAt) {}

  public record CustomerBookingDetailResponse(
      UUID id,
      String reference,
      Booking.Status status,
      BigDecimal totalAmount,
      String currency,
      Instant createdAt,
      Instant updatedAt,
      Instant expiresAt,
      EventSummary event,
      VenueSummary venue,
      List<SeatSummary> seats,
      PaymentSummary payment,
      RefundSummary refund) {}

  @PostMapping
  ResponseEntity<BookingResponse> create(
      @Valid @RequestBody CreateBookingRequest r,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String key,
      @AuthenticationPrincipal User u) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r, key, u));
  }

  @GetMapping
  Page<CustomerBookingSummaryResponse> list(
      @AuthenticationPrincipal User u,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.list(u, pageable);
  }

  @GetMapping("/{id}")
  CustomerBookingDetailResponse get(@PathVariable UUID id, @AuthenticationPrincipal User u) {
    return service.get(id, u);
  }

  @PostMapping("/{id}/cancel")
  ResponseEntity<Void> cancel(@PathVariable UUID id, @AuthenticationPrincipal User u) {
    service.cancel(id, u);
    return ResponseEntity.noContent().build();
  }
}
