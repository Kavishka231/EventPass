package com.eventpass.booking;

import com.eventpass.seat.EventSeat;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Entity @Table(name = "booking_items", uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "event_seat_id"})) @Getter @Setter @NoArgsConstructor
public class BookingItem {
  @Id @GeneratedValue private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) private Booking booking;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) private EventSeat eventSeat;
  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2) private BigDecimal unitPrice;
}
