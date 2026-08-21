package com.eventpass.payment;

import com.eventpass.booking.Booking;
import com.eventpass.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
public class Refund extends BaseEntity {
  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Payment payment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Booking booking;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.PENDING;

  @Column(name = "provider_reference", unique = true)
  private String providerReference;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
  private String idempotencyKey;

  @Column(name = "attempted_at")
  private Instant attemptedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "failure_code", length = 100)
  private String failureCode;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Enumerated(EnumType.STRING)
  @Column(name = "reconciliation_status", nullable = false)
  private Payment.ReconciliationStatus reconciliationStatus =
      Payment.ReconciliationStatus.NOT_REQUIRED;

  public enum Status {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    UNKNOWN
  }
}
