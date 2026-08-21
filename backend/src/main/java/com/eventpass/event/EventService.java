package com.eventpass.event;

import com.eventpass.booking.Booking;
import com.eventpass.booking.BookingRepository;
import com.eventpass.common.error.ApiException;
import com.eventpass.common.outbox.OutboxService;
import com.eventpass.seat.EventSeatRepository;
import com.eventpass.user.User;
import com.eventpass.venue.VenueRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
  private final EventRepository events;
  private final VenueRepository venues;
  private final EventSeatRepository inventory;
  private final BookingRepository bookings;
  private final OutboxService outbox;

  public EventService(
      EventRepository events,
      VenueRepository venues,
      EventSeatRepository inventory,
      BookingRepository bookings,
      OutboxService outbox) {
    this.events = events;
    this.venues = venues;
    this.inventory = inventory;
    this.bookings = bookings;
    this.outbox = outbox;
  }

  @Transactional(readOnly = true)
  public Page<EventController.EventResponse> search(
      String category, String city, Instant from, Instant to, Pageable pageable) {
    Specification<Event> s = (r, q, b) -> b.conjunction();
    if (category != null) s = s.and((r, q, b) -> b.equal(r.get("category"), category));
    if (city != null) s = s.and((r, q, b) -> b.equal(r.get("venue").get("city"), city));
    if (from != null) s = s.and((r, q, b) -> b.greaterThanOrEqualTo(r.get("startDateTime"), from));
    if (to != null) s = s.and((r, q, b) -> b.lessThanOrEqualTo(r.get("startDateTime"), to));
    s = s.and((r, q, b) -> b.equal(r.get("status"), Event.Status.PUBLISHED));
    return events.findAll(s, pageable).map(this::response);
  }

  @Transactional(readOnly = true)
  public EventController.EventResponse get(UUID id) {
    Event e =
        events
            .findById(id)
            .filter(x -> x.getStatus() == Event.Status.PUBLISHED)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event was not found."));
    return response(e);
  }

  @Transactional
  public EventController.EventResponse create(EventController.EventRequest r, User organizer) {
    if (r.status() != Event.Status.DRAFT) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "INVALID_EVENT_TRANSITION",
          "New events must be created as drafts before inventory is configured.");
    }
    Event e = new Event();
    apply(e, r);
    e.setOrganizer(organizer);
    return response(events.save(e));
  }

  @Transactional
  public EventController.EventResponse update(UUID id, EventController.EventRequest r, User actor) {
    Event e = ownedForUpdate(id, actor);
    validateTransition(e, r.status());
    apply(e, r);
    return response(e);
  }

  @Transactional
  public void cancel(UUID id, User actor) {
    Event event = ownedForUpdate(id, actor);
    if (event.getStatus() != Event.Status.DRAFT && event.getStatus() != Event.Status.PUBLISHED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "INVALID_EVENT_TRANSITION",
          "Event cannot be cancelled from its current state.");
    }
    if (bookings.existsByEventIdAndStatus(event.getId(), Booking.Status.PENDING)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "EVENT_HAS_PENDING_BOOKINGS",
          "Event cancellation must wait for pending booking payments to finish.");
    }
    event.setStatus(Event.Status.CANCELLED);
    outbox.record(
        "event.events",
        "EVENT_CANCELLED",
        event.getId(),
        Map.of("eventId", event.getId(), "cancelledBy", actor.getId()));
  }

  private Event ownedForUpdate(UUID id, User user) {
    Event event =
        events
            .lockById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event was not found."));
    if (user.getRole() != User.Role.ADMIN && !event.getOrganizer().getId().equals(user.getId())) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "EVENT_FORBIDDEN", "You cannot manage this event.");
    }
    return event;
  }

  private void apply(Event e, EventController.EventRequest r) {
    if (!r.endDateTime().isAfter(r.startDateTime()))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_EVENT_DATES", "End time must be after start time.");
    e.setName(r.name());
    e.setDescription(r.description());
    e.setCategory(r.category());
    e.setStartDateTime(r.startDateTime());
    e.setEndDateTime(r.endDateTime());
    e.setStatus(r.status());
    e.setVenue(
        venues
            .findById(r.venueId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "VENUE_NOT_FOUND", "Venue was not found.")));
  }

  private void validateTransition(Event event, Event.Status requested) {
    if (event.getStatus() == Event.Status.DRAFT
        && requested == Event.Status.PUBLISHED
        && inventory.countByEventId(event.getId()) == 0) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "EVENT_INVENTORY_REQUIRED",
          "An event must have priced seat inventory before publication.");
    }
    boolean allowed =
        (event.getStatus() == Event.Status.DRAFT
                && (requested == Event.Status.DRAFT || requested == Event.Status.PUBLISHED))
            || (event.getStatus() == Event.Status.PUBLISHED && requested == Event.Status.PUBLISHED);
    if (!allowed) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "INVALID_EVENT_TRANSITION",
          "Requested event state transition is not allowed.");
    }
  }

  private EventController.EventResponse response(Event e) {
    return new EventController.EventResponse(
        e.getId(),
        e.getName(),
        e.getDescription(),
        e.getCategory(),
        e.getStartDateTime(),
        e.getEndDateTime(),
        e.getStatus(),
        e.getVenue().getId(),
        e.getVenue().getName(),
        e.getVenue().getCity());
  }
}
