package com.eventpass.notification;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessedEventService {
  private final JdbcTemplate jdbc;

  public ProcessedEventService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public boolean claim(String consumerName, UUID eventId) {
    return jdbc.update(
            "INSERT INTO processed_events(consumer_name,event_id,processed_at) VALUES (?,?,CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING",
            consumerName,
            eventId)
        == 1;
  }
}
