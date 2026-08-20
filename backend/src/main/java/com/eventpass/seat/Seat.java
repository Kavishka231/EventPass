package com.eventpass.seat;

import com.eventpass.venue.Venue;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity @Table(name = "seats", uniqueConstraints = @UniqueConstraint(columnNames = {"venue_id", "section", "row_number", "seat_number"})) @Getter @Setter @NoArgsConstructor
public class Seat {
  @Id @GeneratedValue private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) private Venue venue;
  @Column(nullable = false) private String section;
  @Column(name = "row_number", nullable = false) private String rowNumber;
  @Column(name = "seat_number", nullable = false) private String seatNumber;
  @Enumerated(EnumType.STRING) @Column(name = "seat_type", nullable = false) private Type seatType;
  public enum Type { REGULAR, PREMIUM, VIP }
}
