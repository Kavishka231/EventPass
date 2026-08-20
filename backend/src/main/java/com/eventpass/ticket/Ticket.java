package com.eventpass.ticket;

import com.eventpass.booking.Booking;
import com.eventpass.seat.EventSeat;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {
  @Id @GeneratedValue private UUID id;

  @Column(name = "ticket_number", nullable = false, unique = true)
  private String ticketNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Booking booking;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private EventSeat eventSeat;

  @Column(name = "qr_token", nullable = false, unique = true, length = 128)
  private String qrToken;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.ACTIVE;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "used_at")
  private Instant usedAt;

  public enum Status {
    ACTIVE,
    USED,
    CANCELLED
  }
}
