package com.eventpass.event;

import com.eventpass.common.error.ApiException;
import com.eventpass.seat.EventSeat;
import com.eventpass.seat.EventSeatRepository;
import com.eventpass.seat.Seat;
import com.eventpass.seat.SeatRepository;
import com.eventpass.user.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizer/events")
@PreAuthorize("hasRole('ORGANIZER')")
public class OrganizerEventController {
  private final EventRepository events;
  private final SeatRepository seats;
  private final EventSeatRepository inventory;

  public OrganizerEventController(
      EventRepository events, SeatRepository seats, EventSeatRepository inventory) {
    this.events = events;
    this.seats = seats;
    this.inventory = inventory;
  }

  public record InventoryOption(
      UUID seatId,
      String section,
      String row,
      String number,
      Seat.Type type,
      BigDecimal price,
      boolean blocked,
      boolean configured) {}

  @GetMapping
  @Transactional(readOnly = true)
  public Page<EventController.EventResponse> list(
      @AuthenticationPrincipal User organizer,
      @PageableDefault(size = 20, sort = "startDateTime", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return events.findAllByOrganizerId(organizer.getId(), pageable).map(this::response);
  }

  @GetMapping("/{eventId}")
  @Transactional(readOnly = true)
  public EventController.EventResponse get(
      @PathVariable UUID eventId, @AuthenticationPrincipal User organizer) {
    return response(owned(eventId, organizer));
  }

  @GetMapping("/{eventId}/inventory")
  @Transactional(readOnly = true)
  public List<InventoryOption> inventory(
      @PathVariable UUID eventId, @AuthenticationPrincipal User organizer) {
    Event event = owned(eventId, organizer);
    return seats
        .findAllByVenueIdOrderBySectionAscRowNumberAscSeatNumberAsc(event.getVenue().getId())
        .stream()
        .map(
            seat -> {
              EventSeat configured =
                  inventory.findByEventIdAndSeatId(eventId, seat.getId()).orElse(null);
              return new InventoryOption(
                  seat.getId(),
                  seat.getSection(),
                  seat.getRowNumber(),
                  seat.getSeatNumber(),
                  seat.getSeatType(),
                  configured == null ? null : configured.getPrice(),
                  configured != null && configured.getStatus() == EventSeat.Status.BLOCKED,
                  configured != null);
            })
        .toList();
  }

  private Event owned(UUID id, User organizer) {
    return events
        .findById(id)
        .filter(event -> event.getOrganizer().getId().equals(organizer.getId()))
        .orElseThrow(
            () ->
                new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event was not found."));
  }

  private EventController.EventResponse response(Event event) {
    return new EventController.EventResponse(
        event.getId(),
        event.getName(),
        event.getDescription(),
        event.getCategory(),
        event.getStartDateTime(),
        event.getEndDateTime(),
        event.getStatus(),
        event.getVenue().getId(),
        event.getVenue().getName(),
        event.getVenue().getCity());
  }
}
