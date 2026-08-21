package com.eventpass.payment;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
  Optional<Payment> findByBookingId(UUID bookingId);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.booking.id = :bookingId")
  Optional<Payment> lockByBookingId(@Param("bookingId") UUID bookingId);

  List<Payment> findAllByReconciliationStatus(Payment.ReconciliationStatus reconciliationStatus);
}
