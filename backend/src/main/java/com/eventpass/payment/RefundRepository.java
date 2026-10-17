package com.eventpass.payment;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
  Optional<Refund> findByPaymentId(UUID paymentId);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Refund r where r.id = :id")
  Optional<Refund> lockById(@Param("id") UUID id);

  @Query(
      value =
          """
          SELECT id FROM refunds
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

  Page<Refund> findByReconciliationStatusIn(
      Collection<Payment.ReconciliationStatus> statuses, Pageable pageable);

  List<Refund> findAllByReconciliationStatus(Payment.ReconciliationStatus reconciliationStatus);
}
