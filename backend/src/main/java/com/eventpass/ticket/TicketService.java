package com.eventpass.ticket;

import com.eventpass.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
}
