package com.eventpass.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.eventpass.booking.Booking;
import com.eventpass.common.error.ApiException;
import com.eventpass.event.Event;
import com.eventpass.seat.EventSeat;
import com.eventpass.user.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketServiceValidationTest {
  @Mock TicketRepository tickets;
  TicketService service;
  User organizer;
  Event event;
  Ticket ticket;

  @BeforeEach
  void setUp() {
    service = new TicketService(tickets);
    organizer = user(User.Role.ORGANIZER);
    event = new Event();
    event.setId(UUID.randomUUID());
    event.setName("Admission Event");
    event.setStartDateTime(Instant.now().plusSeconds(3_600));
    event.setStatus(Event.Status.PUBLISHED);
    event.setOrganizer(organizer);
    Booking booking = new Booking();
    booking.setEvent(event);
    EventSeat eventSeat = new EventSeat();
    eventSeat.setId(UUID.randomUUID());
    eventSeat.setEvent(event);
    ticket = new Ticket();
    ticket.setId(UUID.randomUUID());
    ticket.setTicketNumber("TKT-VALID");
    ticket.setQrToken("secure-qr-token");
    ticket.setBooking(booking);
    ticket.setEventSeat(eventSeat);
    ticket.setStatus(Ticket.Status.ACTIVE);
  }

  @Test
  void validatesAnActiveTicketForItsEventOwner() {
    when(tickets.findByQrToken(ticket.getQrToken())).thenReturn(Optional.of(ticket));

    TicketController.ValidationResponse response =
        service.validate(request(event.getId()), organizer);

    assertThat(response.ticketId()).isEqualTo(ticket.getId());
    assertThat(response.eventId()).isEqualTo(event.getId());
    assertThat(response.status()).isEqualTo(Ticket.Status.ACTIVE);
  }

  @Test
  void rejectsUnknownTokens() {
    when(tickets.findByQrToken("secure-qr-token")).thenReturn(Optional.empty());

    assertCode("TICKET_NOT_FOUND", () -> service.validate(request(event.getId()), organizer));
  }

  @Test
  void rejectsCancelledAndUsedTickets() {
    when(tickets.findByQrToken(ticket.getQrToken())).thenReturn(Optional.of(ticket));
    ticket.setStatus(Ticket.Status.CANCELLED);
    assertCode("TICKET_CANCELLED", () -> service.validate(request(event.getId()), organizer));

    ticket.setStatus(Ticket.Status.USED);
    ticket.setUsedAt(Instant.now());
    assertCode("TICKET_ALREADY_USED", () -> service.validate(request(event.getId()), organizer));
  }

  @Test
  void rejectsEventMismatchAndInvalidEventState() {
    when(tickets.findByQrToken(ticket.getQrToken())).thenReturn(Optional.of(ticket));
    assertCode(
        "TICKET_EVENT_MISMATCH", () -> service.validate(request(UUID.randomUUID()), organizer));

    event.setStatus(Event.Status.CANCELLED);
    assertCode("EVENT_NOT_ADMITTING", () -> service.validate(request(event.getId()), organizer));
  }

  @Test
  void restrictsOrganizersToTheirOwnEventsWhileAllowingAdministrators() {
    when(tickets.findByQrToken(ticket.getQrToken())).thenReturn(Optional.of(ticket));
    User otherOrganizer = user(User.Role.ORGANIZER);
    assertCode(
        "EVENT_ACCESS_DENIED", () -> service.validate(request(event.getId()), otherOrganizer));

    TicketController.ValidationResponse response =
        service.validate(request(event.getId()), user(User.Role.ADMIN));
    assertThat(response.ticketId()).isEqualTo(ticket.getId());
  }

  @Test
  void redeemsAnActiveTicketAndRecordsItsUseTime() {
    when(tickets.lockByQrToken(ticket.getQrToken())).thenReturn(Optional.of(ticket));

    TicketController.RedemptionResponse response =
        service.redeem(request(event.getId()), organizer);

    assertThat(response.status()).isEqualTo(Ticket.Status.USED);
    assertThat(response.usedAt()).isNotNull();
    assertThat(ticket.getStatus()).isEqualTo(Ticket.Status.USED);
    assertThat(ticket.getUsedAt()).isEqualTo(response.usedAt());
  }

  @Test
  void rejectsASecondRedemptionAfterTheTicketIsUsed() {
    when(tickets.lockByQrToken(ticket.getQrToken())).thenReturn(Optional.of(ticket));
    service.redeem(request(event.getId()), organizer);

    assertCode("TICKET_ALREADY_USED", () -> service.redeem(request(event.getId()), organizer));
  }

  private TicketController.ValidateTicketRequest request(UUID eventId) {
    return new TicketController.ValidateTicketRequest(ticket.getQrToken(), eventId);
  }

  private User user(User.Role role) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setRole(role);
    return user;
  }

  private void assertCode(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            ApiException.class, exception -> assertThat(exception.code()).isEqualTo(code));
  }
}
