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

  @Query(
      value =
          """
          select new com.eventpass.booking.EventBookingReportRow(
            booking.id,
            booking.bookingReference,
            booking.user.id,
            booking.user.email,
            booking.user.firstName,
            booking.user.lastName,
            booking.status,
            booking.totalAmount,
            booking.currency,
            booking.createdAt)
          from Booking booking
          where booking.event.id = :eventId
          """,
      countQuery = "select count(booking) from Booking booking where booking.event.id = :eventId")
  Page<EventBookingReportRow> findReportRowsByEventId(
      @Param("eventId") UUID eventId, Pageable pageable);

  @Query(
      value =
          """
          select new com.eventpass.booking.AdminBookingRow(
            booking.id,
            booking.bookingReference,
            booking.event.id,
            booking.event.name,
            booking.user.id,
            booking.user.email,
            booking.status,
            booking.totalAmount,
            booking.currency,
            booking.createdAt)
          from Booking booking
          where (:eventId is null or booking.event.id = :eventId)
            and (:status is null or booking.status = :status)
          """,
      countQuery =
          """
          select count(booking)
          from Booking booking
          where (:eventId is null or booking.event.id = :eventId)
            and (:status is null or booking.status = :status)
          """)
  Page<AdminBookingRow> findManagementRows(
      @Param("eventId") UUID eventId, @Param("status") Booking.Status status, Pageable pageable);

  @Query(
      """
      select new com.eventpass.booking.AdminBookingRow(
        booking.id,
        booking.bookingReference,
        booking.event.id,
        booking.event.name,
        booking.user.id,
        booking.user.email,
        booking.status,
        booking.totalAmount,
        booking.currency,
        booking.createdAt)
      from Booking booking
      where booking.id = :id
      """)
  Optional<AdminBookingRow> findManagementRowById(@Param("id") UUID id);

  long countByStatus(Booking.Status status);

  boolean existsByEventIdAndStatus(UUID eventId, Booking.Status status);

  List<Booking> findAllByEventIdAndStatus(UUID eventId, Booking.Status status);

  List<Booking> findTop100ByStatusAndExpiresAtBefore(Booking.Status status, Instant expiresAt);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Booking b where b.id = :id")
  Optional<Booking> lockById(@Param("id") UUID id);
}
