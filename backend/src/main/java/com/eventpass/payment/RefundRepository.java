package com.eventpass.payment;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
  Optional<Refund> findByPaymentId(UUID paymentId);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Refund r where r.id = :id")
  Optional<Refund> lockById(@Param("id") UUID id);

  List<Refund> findAllByReconciliationStatus(Payment.ReconciliationStatus reconciliationStatus);
}
