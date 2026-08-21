package com.eventpass.event;

import com.eventpass.booking.Booking;
import com.eventpass.booking.BookingRepository;
import com.eventpass.common.error.ApiException;
import com.eventpass.common.outbox.OutboxService;
import com.eventpass.ticket.Ticket;
import com.eventpass.ticket.TicketRepository;
import com.eventpass.user.User;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EventCancellationTransactions {
  private final EventRepository events;
  private final BookingRepository bookings;
  private final TicketRepository tickets;
  private final OutboxService outbox;

  EventCancellationTransactions(
      EventRepository events,
      BookingRepository bookings,
      TicketRepository tickets,
      OutboxService outbox) {
    this.events = events;
    this.bookings = bookings;
    this.tickets = tickets;
    this.outbox = outbox;
  }

  @Transactional
  List<UUID> cancel(UUID eventId, User actor) {
    Event event = ownedAndLocked(eventId, actor);
    if (event.getStatus() != Event.Status.DRAFT && event.getStatus() != Event.Status.PUBLISHED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "INVALID_EVENT_TRANSITION",
          "Event cannot be cancelled from its current state.");
    }
    if (bookings.existsByEventIdAndStatus(eventId, Booking.Status.PENDING)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "EVENT_HAS_PENDING_BOOKINGS",
          "Event cancellation must wait for pending booking payments to finish.");
    }
    List<UUID> affectedBookingIds =
        bookings.findAllByEventIdAndStatus(eventId, Booking.Status.CONFIRMED).stream()
            .map(Booking::getId)
            .toList();
    List<Ticket> affectedTickets = tickets.findAllByBookingEventId(eventId);
    affectedTickets.forEach(ticket -> ticket.setStatus(Ticket.Status.CANCELLED));
    event.setStatus(Event.Status.CANCELLED);
    outbox.record(
        "event.events",
        "EVENT_CANCELLED",
        eventId,
        Map.of(
            "eventId", eventId,
            "cancelledBy", actor.getId(),
            "affectedBookings", affectedBookingIds.size()));
    outbox.record(
        "ticket.events",
        "EVENT_TICKETS_CANCELLED",
        eventId,
        Map.of("eventId", eventId, "cancelledTickets", affectedTickets.size()));
    return affectedBookingIds;
  }

  private Event ownedAndLocked(UUID id, User user) {
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
}
