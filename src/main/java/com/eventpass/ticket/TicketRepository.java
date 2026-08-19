package com.eventpass.ticket;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TicketRepository extends JpaRepository<Ticket, UUID> { List<Ticket> findAllByBookingUserId(UUID userId); }
