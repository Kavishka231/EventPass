package com.eventpass.venue;

import com.eventpass.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity @Table(name = "venues") @Getter @Setter @NoArgsConstructor
public class Venue extends BaseEntity {
  @Id @GeneratedValue private UUID id;
  @Column(nullable = false) private String name;
  @Column(nullable = false) private String address;
  @Column(nullable = false) private String city;
  @Column(nullable = false) private int capacity;
}
