package com.eventpass.common.outbox;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope<T>(
    UUID eventId,
    String eventType,
    int version,
    @JsonAlias("occurredAt") Instant timestamp,
    UUID aggregateId,
    @JsonAlias("data") T payload) {
  public static final int CURRENT_VERSION = 1;

  public static <T> EventEnvelope<T> versionOne(
      UUID eventId, String eventType, Instant timestamp, UUID aggregateId, T payload) {
    return new EventEnvelope<>(
        eventId, eventType, CURRENT_VERSION, timestamp, aggregateId, payload);
  }
}
