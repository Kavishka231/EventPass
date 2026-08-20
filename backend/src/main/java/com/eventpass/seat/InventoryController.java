package com.eventpass.seat;

import com.eventpass.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {
  private final InventoryService service;

  public InventoryController(InventoryService service) {
    this.service = service;
  }

  public record CreateSeatRequest(
      @NotBlank @Size(max = 80) String section,
      @NotBlank @Size(max = 20) String row,
      @NotBlank @Size(max = 20) String number,
      @NotNull Seat.Type type) {}

  public record EventSeatRequest(
      @NotNull UUID seatId,
      @NotNull @DecimalMin(value = "0.00") BigDecimal price,
      boolean blocked) {}

  public record SeatDefinitionResponse(
      UUID id, UUID venueId, String section, String row, String number, Seat.Type type) {}

  @PostMapping("/venues/{venueId}/seats")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<SeatDefinitionResponse>> createSeats(
      @PathVariable UUID venueId,
      @RequestBody @NotEmpty @Size(max = 500) List<@Valid CreateSeatRequest> requests) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createSeats(venueId, requests));
  }

  @PutMapping("/events/{eventId}/inventory")
  @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
  public List<SeatController.SeatResponse> configureEvent(
      @PathVariable UUID eventId,
      @RequestBody @NotEmpty @Size(max = 500) List<@Valid EventSeatRequest> requests,
      @AuthenticationPrincipal User actor) {
    return service.configureEvent(eventId, requests, actor);
  }
}
