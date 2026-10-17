package com.eventpass.payment;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
  Optional<Payment> findByBookingId(UUID bookingId);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.booking.id = :bookingId")
  Optional<Payment> lockByBookingId(@Param("bookingId") UUID bookingId);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.id = :id")
  Optional<Payment> lockById(@Param("id") UUID id);

  @Query(
      value =
          """
          SELECT id FROM payments
          WHERE reconciliation_status IN ('PENDING', 'IN_PROGRESS')
            AND next_reconciliation_at <= :now
          ORDER BY next_reconciliation_at, created_at
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<UUID> lockDueForReconciliation(@Param("now") Instant now, @Param("limit") int limit);

  long countByReconciliationStatus(Payment.ReconciliationStatus status);

  long countByReconciliationStatusIn(Collection<Payment.ReconciliationStatus> statuses);

  Page<Payment> findByReconciliationStatusIn(
      Collection<Payment.ReconciliationStatus> statuses, Pageable pageable);

  List<Payment> findAllByReconciliationStatus(Payment.ReconciliationStatus reconciliationStatus);
}
