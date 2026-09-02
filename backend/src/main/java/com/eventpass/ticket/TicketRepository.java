package com.eventpass.ticket;

import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
  @Query(
      value =
          """
          select new com.eventpass.ticket.TicketListRow(
            ticket.id,
            ticket.ticketNumber,
            ticket.booking.id,
            ticket.eventSeat.id,
            ticket.qrToken,
            ticket.status,
            ticket.issuedAt,
            ticket.usedAt,
            ticket.booking.bookingReference,
            ticket.booking.event.id,
            ticket.booking.event.name,
            ticket.booking.event.startDateTime,
            ticket.booking.event.endDateTime,
            ticket.booking.event.venue.id,
            ticket.booking.event.venue.name,
            ticket.booking.event.venue.address,
            ticket.booking.event.venue.city,
            ticket.eventSeat.seat.id,
            ticket.eventSeat.seat.section,
            ticket.eventSeat.seat.rowNumber,
            ticket.eventSeat.seat.seatNumber,
            ticket.eventSeat.seat.seatType)
          from Ticket ticket
          where ticket.booking.user.id = :userId
          """,
      countQuery = "select count(ticket) from Ticket ticket where ticket.booking.user.id = :userId")
  Page<TicketListRow> findListRowsByUserId(@Param("userId") UUID userId, Pageable pageable);

  @Query(
      """
      select new com.eventpass.ticket.TicketListRow(
        ticket.id, ticket.ticketNumber, ticket.booking.id, ticket.eventSeat.id,
        ticket.qrToken, ticket.status, ticket.issuedAt, ticket.usedAt,
        ticket.booking.bookingReference, ticket.booking.event.id, ticket.booking.event.name,
        ticket.booking.event.startDateTime, ticket.booking.event.endDateTime,
        ticket.booking.event.venue.id, ticket.booking.event.venue.name,
        ticket.booking.event.venue.address, ticket.booking.event.venue.city,
        ticket.eventSeat.seat.id, ticket.eventSeat.seat.section,
        ticket.eventSeat.seat.rowNumber, ticket.eventSeat.seat.seatNumber,
        ticket.eventSeat.seat.seatType)
      from Ticket ticket
      where ticket.id = :id and ticket.booking.user.id = :userId
      """)
  Optional<TicketListRow> findCustomerRowById(@Param("id") UUID id, @Param("userId") UUID userId);

  List<Ticket> findAllByBookingId(UUID bookingId);

  List<Ticket> findAllByBookingEventId(UUID eventId);

  @EntityGraph(attributePaths = {"booking.event.organizer", "eventSeat.event"})
  Optional<Ticket> findByQrToken(String qrToken);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select ticket from Ticket ticket where ticket.qrToken = :qrToken")
  Optional<Ticket> lockByQrToken(@Param("qrToken") String qrToken);
}
