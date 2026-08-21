package com.eventpass.event;

import com.eventpass.booking.BookingService;
import com.eventpass.common.error.ApiException;
import com.eventpass.seat.EventSeatRepository;
import com.eventpass.user.User;
import com.eventpass.venue.VenueRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
  private static final Logger log = LoggerFactory.getLogger(EventService.class);
  private final EventRepository events;
  private final VenueRepository venues;
  private final EventSeatRepository inventory;
  private final EventCancellationTransactions cancellationTransactions;
  private final BookingService bookingService;

  public EventService(
      EventRepository events,
      VenueRepository venues,
      EventSeatRepository inventory,
      EventCancellationTransactions cancellationTransactions,
      BookingService bookingService) {
    this.events = events;
    this.venues = venues;
    this.inventory = inventory;
    this.cancellationTransactions = cancellationTransactions;
    this.bookingService = bookingService;
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

  public void cancel(UUID id, User actor) {
    cancellationTransactions
        .cancel(id, actor)
        .forEach(
            bookingId -> {
              try {
                bookingService.cancelForEvent(bookingId);
              } catch (RuntimeException exception) {
                log.warn(
                    "Event cancellation refund requires follow-up for bookingId={}",
                    bookingId,
                    exception);
              }
            });
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
