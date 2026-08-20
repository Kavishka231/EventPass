package com.eventpass.booking;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
  Optional<Booking> findByIdempotencyKey(String key);

  List<Booking> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

  long countByStatus(Booking.Status status);

  List<Booking> findTop100ByStatusAndExpiresAtBefore(Booking.Status status, Instant expiresAt);
}
