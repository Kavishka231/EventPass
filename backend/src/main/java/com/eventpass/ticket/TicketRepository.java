package com.eventpass.ticket;

import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
            ticket.issuedAt)
          from Ticket ticket
          where ticket.booking.user.id = :userId
          """,
      countQuery = "select count(ticket) from Ticket ticket where ticket.booking.user.id = :userId")
  Page<TicketListRow> findListRowsByUserId(@Param("userId") UUID userId, Pageable pageable);

  List<Ticket> findAllByBookingId(UUID bookingId);

  List<Ticket> findAllByBookingEventId(UUID eventId);

  @EntityGraph(attributePaths = {"booking.event.organizer", "eventSeat.event"})
  Optional<Ticket> findByQrToken(String qrToken);
}
