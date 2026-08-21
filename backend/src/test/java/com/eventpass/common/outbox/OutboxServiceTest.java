package com.eventpass.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxServiceTest {
  @Test
  void recordsStableVersionedEventEnvelope() throws Exception {
    OutboxEventRepository repository = mock(OutboxEventRepository.class);
    ObjectMapper objectMapper =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    OutboxService service = new OutboxService(repository, objectMapper);
    UUID aggregateId = UUID.randomUUID();

    service.record(
        "booking.events", "BOOKING_CONFIRMED", aggregateId, Map.of("bookingId", aggregateId));

    ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repository).save(eventCaptor.capture());
    OutboxEvent stored = eventCaptor.getValue();
    JsonNode envelope = objectMapper.readTree(stored.getPayload());
    assertThat(envelope.size()).isEqualTo(6);
    assertThat(envelope.path("eventId").asText()).isEqualTo(stored.getId().toString());
    assertThat(envelope.path("eventType").asText()).isEqualTo("BOOKING_CONFIRMED");
    assertThat(envelope.path("version").asInt()).isEqualTo(1);
    assertThat(envelope.path("timestamp").asText()).isEqualTo(stored.getOccurredAt().toString());
    assertThat(envelope.path("aggregateId").asText()).isEqualTo(aggregateId.toString());
    assertThat(envelope.path("payload").path("bookingId").asText())
        .isEqualTo(aggregateId.toString());
    assertThat(envelope.has("data")).isFalse();
    assertThat(envelope.has("occurredAt")).isFalse();
  }
}
