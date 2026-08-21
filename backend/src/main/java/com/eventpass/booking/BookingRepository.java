package com.eventpass.booking;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
  Optional<Booking> findByUserIdAndIdempotencyOperationAndIdempotencyKey(
      UUID userId, String operation, String key);

  @Query(value = "SELECT pg_advisory_xact_lock(:lockId)", nativeQuery = true)
  void acquireIdempotencyLock(@Param("lockId") long lockId);

  List<Booking> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

  long countByStatus(Booking.Status status);

  boolean existsByEventIdAndStatus(UUID eventId, Booking.Status status);

  List<Booking> findTop100ByStatusAndExpiresAtBefore(Booking.Status status, Instant expiresAt);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Booking b where b.id = :id")
  Optional<Booking> lockById(@Param("id") UUID id);
}
