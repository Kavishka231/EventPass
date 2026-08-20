package com.eventpass.payment;

import com.eventpass.booking.Booking;
import com.eventpass.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {
  @Id @GeneratedValue private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  private Booking booking;

  @Column(name = "payment_reference", unique = true)
  private String paymentReference;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(nullable = false)
  private String provider;

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
  private ReconciliationStatus reconciliationStatus = ReconciliationStatus.NOT_REQUIRED;

  public enum Status {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    UNKNOWN,
    REFUNDED
  }

  public enum ReconciliationStatus {
    NOT_REQUIRED,
    PENDING,
    RESOLVED
  }
}
