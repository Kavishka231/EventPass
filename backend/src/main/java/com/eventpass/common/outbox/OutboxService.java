package com.eventpass.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {
  private final OutboxEventRepository events;
  private final ObjectMapper objectMapper;

  public OutboxService(OutboxEventRepository events, ObjectMapper objectMapper) {
    this.events = events;
    this.objectMapper = objectMapper;
  }

  public void record(String topic, String eventType, UUID aggregateId, Map<String, ?> payload) {
    OutboxEvent event = new OutboxEvent();
    UUID eventId = UUID.randomUUID();
    Instant occurredAt = Instant.now();
    event.setId(eventId);
    event.setAggregateType("BOOKING");
    event.setAggregateId(aggregateId);
    event.setEventType(eventType);
    event.setTopic(topic);
    event.setOccurredAt(occurredAt);
    event.setNextAttemptAt(occurredAt);
    try {
      event.setPayload(
          objectMapper.writeValueAsString(
              EventEnvelope.versionOne(eventId, eventType, occurredAt, aggregateId, payload)));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not serialize domain event", exception);
    }
    events.save(event);
  }
}
