package com.eventpass.booking;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
  Optional<Booking> findByUserIdAndIdempotencyOperationAndIdempotencyKey(
      UUID userId, String operation, String key);

  @Query(value = "SELECT pg_advisory_xact_lock(:lockId)", nativeQuery = true)
  void acquireIdempotencyLock(@Param("lockId") long lockId);

  @Query(
      value =
          """
          select new com.eventpass.booking.BookingListRow(
            booking.id,
            booking.bookingReference,
            booking.event.id,
            booking.status,
            booking.totalAmount,
            booking.currency,
            booking.createdAt)
          from Booking booking
          where booking.user.id = :userId
          """,
      countQuery = "select count(booking) from Booking booking where booking.user.id = :userId")
  Page<BookingListRow> findListRowsByUserId(@Param("userId") UUID userId, Pageable pageable);

  long countByStatus(Booking.Status status);

  boolean existsByEventIdAndStatus(UUID eventId, Booking.Status status);

  List<Booking> findAllByEventIdAndStatus(UUID eventId, Booking.Status status);

  @Query(
      value =
          """
          SELECT *
          FROM bookings
          WHERE status = 'PENDING'
            AND expires_at < :expiresAt
          ORDER BY expires_at
          LIMIT :batchSize
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<Booking> claimExpiredBatch(
      @Param("expiresAt") Instant expiresAt, @Param("batchSize") int batchSize);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Booking b where b.id = :id")
  Optional<Booking> lockById(@Param("id") UUID id);
}
