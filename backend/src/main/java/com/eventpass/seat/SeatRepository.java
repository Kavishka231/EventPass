package com.eventpass.seat;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
  long countByVenueId(UUID venueId);
}
