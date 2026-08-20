package com.eventpass.event;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {}
