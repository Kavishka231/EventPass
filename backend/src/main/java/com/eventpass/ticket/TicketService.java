package com.eventpass.ticket;

import com.eventpass.common.error.ApiException;
import com.eventpass.event.Event;
import com.eventpass.user.User;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {
  private final TicketRepository tickets;

  public TicketService(TicketRepository tickets) {
    this.tickets = tickets;
  }

  @Transactional(readOnly = true)
  public Page<TicketController.TicketResponse> list(User user, Pageable pageable) {
    return tickets
        .findListRowsByUserId(user.getId(), pageable)
        .map(
            ticket ->
                new TicketController.TicketResponse(
                    ticket.id(),
                    ticket.ticketNumber(),
                    ticket.bookingId(),
                    ticket.eventSeatId(),
                    ticket.qrToken(),
                    ticket.status(),
                    ticket.issuedAt()));
  }

  @Transactional(readOnly = true)
  public TicketController.ValidationResponse validate(
      TicketController.ValidateTicketRequest request, User actor) {
    Ticket ticket =
        tickets
            .findByQrToken(request.qrToken())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket was not found."));
    Event event = validForAdmission(ticket, request.eventId(), actor);
    return new TicketController.ValidationResponse(
        ticket.getId(),
        ticket.getTicketNumber(),
        event.getId(),
        ticket.getEventSeat().getId(),
        ticket.getStatus(),
        event.getName(),
        event.getStartDateTime());
  }

  @Transactional
  public TicketController.RedemptionResponse redeem(
      TicketController.ValidateTicketRequest request, User actor) {
    Ticket ticket =
        tickets
            .lockByQrToken(request.qrToken())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket was not found."));
    Event event = validForAdmission(ticket, request.eventId(), actor);
    Instant usedAt = Instant.now();
    ticket.setStatus(Ticket.Status.USED);
    ticket.setUsedAt(usedAt);
    return new TicketController.RedemptionResponse(
        ticket.getId(), ticket.getTicketNumber(), event.getId(), Ticket.Status.USED, usedAt);
  }

  private Event validForAdmission(Ticket ticket, java.util.UUID eventId, User actor) {
    Event event = ticket.getBooking().getEvent();
    requireEventAccess(event, actor);
    if (!event.getId().equals(eventId)
        || !ticket.getEventSeat().getEvent().getId().equals(eventId)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "TICKET_EVENT_MISMATCH",
          "Ticket does not belong to the requested event.");
    }
    if (ticket.getStatus() == Ticket.Status.CANCELLED) {
      throw new ApiException(
          HttpStatus.CONFLICT, "TICKET_CANCELLED", "Cancelled tickets are not valid for entry.");
    }
    if (ticket.getStatus() == Ticket.Status.USED || ticket.getUsedAt() != null) {
      throw new ApiException(
          HttpStatus.CONFLICT, "TICKET_ALREADY_USED", "Ticket has already been used.");
    }
    if (event.getStatus() != Event.Status.PUBLISHED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "EVENT_NOT_ADMITTING",
          "The event is not in a state that permits ticket admission.");
    }
    return event;
  }

  private void requireEventAccess(Event event, User actor) {
    boolean administrator = actor.getRole() == User.Role.ADMIN;
    boolean owner =
        actor.getRole() == User.Role.ORGANIZER
            && event.getOrganizer().getId().equals(actor.getId());
    if (!administrator && !owner) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "EVENT_ACCESS_DENIED",
          "You are not authorized to process tickets for this event.");
    }
  }
}
