package com.eventpass.seat;

import com.eventpass.common.error.ApiException;
import com.eventpass.event.Event;
import com.eventpass.event.EventRepository;
import com.eventpass.user.User;
import com.eventpass.venue.Venue;
import com.eventpass.venue.VenueRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
  private final VenueRepository venues;
  private final SeatRepository seats;
  private final EventRepository events;
  private final EventSeatRepository inventory;

  public InventoryService(
      VenueRepository venues,
      SeatRepository seats,
      EventRepository events,
      EventSeatRepository inventory) {
    this.venues = venues;
    this.seats = seats;
    this.events = events;
    this.inventory = inventory;
  }

  @Transactional
  public List<InventoryController.SeatDefinitionResponse> createSeats(
      UUID venueId, List<InventoryController.CreateSeatRequest> requests) {
    Venue venue =
        venues
            .findById(venueId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "VENUE_NOT_FOUND", "Venue was not found."));
    if (seats.countByVenueId(venueId) + requests.size() > venue.getCapacity()) {
      throw new ApiException(
          HttpStatus.CONFLICT, "VENUE_CAPACITY_EXCEEDED", "Seat count exceeds venue capacity.");
    }
    return requests.stream()
        .map(
            request -> {
              Seat seat = new Seat();
              seat.setVenue(venue);
              seat.setSection(request.section().trim());
              seat.setRowNumber(request.row().trim());
              seat.setSeatNumber(request.number().trim());
              seat.setSeatType(request.type());
              return definition(seats.save(seat));
            })
        .toList();
  }

  @Transactional
  public List<SeatController.SeatResponse> configureEvent(
      UUID eventId, List<InventoryController.EventSeatRequest> requests, User actor) {
    Event event = managedEvent(eventId, actor);
    if (event.getStatus() != Event.Status.DRAFT) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "INVENTORY_LOCKED",
          "Seat inventory can only be changed while the event is a draft.");
    }
    return requests.stream()
        .map(
            request -> {
              Seat seat =
                  seats
                      .findById(request.seatId())
                      .filter(value -> value.getVenue().getId().equals(event.getVenue().getId()))
                      .orElseThrow(
                          () ->
                              new ApiException(
                                  HttpStatus.UNPROCESSABLE_ENTITY,
                                  "INVALID_EVENT_SEAT",
                                  "Seat does not belong to the event venue."));
              EventSeat eventSeat =
                  inventory.findByEventIdAndSeatId(eventId, seat.getId()).orElseGet(EventSeat::new);
              eventSeat.setEvent(event);
              eventSeat.setSeat(seat);
              eventSeat.setPrice(request.price());
              eventSeat.setStatus(
                  request.blocked() ? EventSeat.Status.BLOCKED : EventSeat.Status.AVAILABLE);
              return SeatController.response(inventory.save(eventSeat));
            })
        .toList();
  }

  private Event managedEvent(UUID id, User actor) {
    Event event =
        events
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event was not found."));
    if (actor.getRole() != User.Role.ADMIN && !event.getOrganizer().getId().equals(actor.getId())) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "EVENT_FORBIDDEN", "You cannot manage this event.");
    }
    return event;
  }

  private InventoryController.SeatDefinitionResponse definition(Seat seat) {
    return new InventoryController.SeatDefinitionResponse(
        seat.getId(),
        seat.getVenue().getId(),
        seat.getSection(),
        seat.getRowNumber(),
        seat.getSeatNumber(),
        seat.getSeatType());
  }
}
