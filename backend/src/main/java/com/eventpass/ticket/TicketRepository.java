package com.eventpass.ticket;

import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
  Page<Ticket> findAllByBookingUserId(UUID userId, Pageable pageable);

  List<Ticket> findAllByBookingId(UUID bookingId);

  List<Ticket> findAllByBookingEventId(UUID eventId);
}
