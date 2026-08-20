package com.eventpass.booking;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BookingRepository extends JpaRepository<Booking, UUID> { Optional<Booking> findByIdempotencyKey(String key); List<Booking> findAllByUserIdOrderByCreatedAtDesc(UUID userId); }
