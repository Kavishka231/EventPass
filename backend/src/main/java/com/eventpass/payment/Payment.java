package com.eventpass.payment;

import com.eventpass.common.persistence.BaseEntity;
import com.eventpass.booking.Booking;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Entity @Table(name = "payments") @Getter @Setter @NoArgsConstructor
public class Payment extends BaseEntity {
  @Id @GeneratedValue private UUID id;
  @OneToOne(fetch = FetchType.LAZY, optional = false) private Booking booking;
  @Column(name = "payment_reference", nullable = false, unique = true) private String paymentReference;
  @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
  @Column(nullable = false, length = 3) private String currency;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
  @Column(nullable = false) private String provider;
  public enum Status { PENDING, SUCCESS, FAILED, REFUNDED }
}
