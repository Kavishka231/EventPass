package com.eventpass.user;

import com.eventpass.common.error.ApiException;
import com.eventpass.event.Event;
import com.eventpass.event.EventController;
import com.eventpass.event.EventRepository;
import com.eventpass.seat.InventoryController;
import com.eventpass.seat.Seat;
import com.eventpass.seat.SeatRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminResourceController {
  private final EventRepository events;
  private final SeatRepository seats;

  public AdminResourceController(EventRepository events, SeatRepository seats) {
    this.events = events;
    this.seats = seats;
  }

  @GetMapping("/events")
  @Transactional(readOnly = true)
  public Page<EventController.EventResponse> events(
      @PageableDefault(size = 20, sort = "startDateTime", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return events.findAll(pageable).map(this::eventResponse);
  }

  @GetMapping("/events/{eventId}")
  @Transactional(readOnly = true)
  public EventController.EventResponse event(@PathVariable UUID eventId) {
    return eventResponse(
        events
            .findById(eventId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event was not found.")));
  }

  @GetMapping("/venues/{venueId}/seats")
  @Transactional(readOnly = true)
  public Page<InventoryController.SeatDefinitionResponse> seats(
      @PathVariable UUID venueId,
      @PageableDefault(
              size = 20,
              sort = {"section", "rowNumber", "seatNumber"})
          Pageable pageable) {
    return seats.findAllByVenueId(venueId, pageable).map(this::seatResponse);
  }

  private EventController.EventResponse eventResponse(Event event) {
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

  private InventoryController.SeatDefinitionResponse seatResponse(Seat seat) {
    return new InventoryController.SeatDefinitionResponse(
        seat.getId(),
        seat.getVenue().getId(),
        seat.getSection(),
        seat.getRowNumber(),
        seat.getSeatNumber(),
        seat.getSeatType());
  }
}
