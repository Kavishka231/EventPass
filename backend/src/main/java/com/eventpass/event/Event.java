package com.eventpass.event;

import com.eventpass.common.persistence.BaseEntity;
import com.eventpass.user.User;
import com.eventpass.venue.Venue;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity @Table(name = "events") @Getter @Setter @NoArgsConstructor
public class Event extends BaseEntity {
  @Id @GeneratedValue private UUID id;
  @Column(nullable = false) private String name;
  @Column(nullable = false, length = 4000) private String description;
  @Column(nullable = false) private String category;
  @Column(name = "start_date_time", nullable = false) private Instant startDateTime;
  @Column(name = "end_date_time", nullable = false) private Instant endDateTime;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) private Venue venue;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) private User organizer;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.DRAFT;
  public enum Status { DRAFT, PUBLISHED, CANCELLED, COMPLETED }
}
