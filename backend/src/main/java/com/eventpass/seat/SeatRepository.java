package com.eventpass.seat;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
  long countByVenueId(UUID venueId);

  List<Seat> findAllByVenueIdOrderBySectionAscRowNumberAscSeatNumberAsc(UUID venueId);

  Page<Seat> findAllByVenueId(UUID venueId, Pageable pageable);
}
