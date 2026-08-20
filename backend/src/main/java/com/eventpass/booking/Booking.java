package com.eventpass.booking;

import com.eventpass.common.persistence.BaseEntity;
import com.eventpass.event.Event;
import com.eventpass.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(
    name = "bookings",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_booking_idempotency_scope",
            columnNames = {"user_id", "idempotency_operation", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
public class Booking extends BaseEntity {
  @Id @GeneratedValue private UUID id;

  @Column(name = "booking_reference", nullable = false, unique = true)
  private String bookingReference;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Event event;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.PENDING;

  @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalAmount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "idempotency_key", nullable = false, length = 100)
  private String idempotencyKey;

  @Column(name = "idempotency_operation", nullable = false, length = 40)
  private String idempotencyOperation;

  @Column(name = "idempotency_request_hash", nullable = false, length = 64)
  private String idempotencyRequestHash;

  @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<BookingItem> items = new ArrayList<>();

  public enum Status {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    FAILED
  }
}
