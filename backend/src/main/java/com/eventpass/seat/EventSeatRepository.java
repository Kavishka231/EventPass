package com.eventpass.seat;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EventSeatRepository extends JpaRepository<EventSeat, UUID> {
  List<EventSeat> findAllByEventId(UUID eventId);

  Optional<EventSeat> findByEventIdAndSeatId(UUID eventId, UUID seatId);

  long countByEventId(UUID eventId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select es from EventSeat es join fetch es.seat where es.event.id = :eventId and es.id in :ids order by es.id")
  List<EventSeat> lockForBooking(
      @Param("eventId") UUID eventId, @Param("ids") Collection<UUID> ids);
}
