package com.eventpass.event;

import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface EventRepository
    extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {}
