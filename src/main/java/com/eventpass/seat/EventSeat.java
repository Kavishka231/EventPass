package com.eventpass.seat;

import com.eventpass.event.Event;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Entity @Table(name = "event_seats", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_id"})) @Getter @Setter @NoArgsConstructor
public class EventSeat {
  @Id @GeneratedValue private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) private Event event;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) private Seat seat;
  @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.AVAILABLE;
  @Version private long version;
  public enum Status { AVAILABLE, HELD, SOLD, BLOCKED }
}
