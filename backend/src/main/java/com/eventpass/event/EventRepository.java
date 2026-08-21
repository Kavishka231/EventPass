package com.eventpass.event;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EventRepository
    extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Event e where e.id = :id")
  Optional<Event> lockById(@Param("id") UUID id);
}
