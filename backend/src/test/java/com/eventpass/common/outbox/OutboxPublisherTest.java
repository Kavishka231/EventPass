package com.eventpass.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxPublisherTest {
  @Test
  void failedKafkaDeliveryIsRetriedAndThenMarkedPublished() {
    OutboxEventRepository repository = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    OutboxEvent event = pendingEvent();
    when(repository.claimPendingBatch(10, 100))
        .thenReturn(List.of(event))
        .thenReturn(List.of(event));
    when(kafka.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")))
        .thenReturn(CompletableFuture.completedFuture(null));
    OutboxPublisher publisher = new OutboxPublisher(repository, kafka);

    publisher.publish();

    assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
    assertThat(event.getAttempts()).isEqualTo(1);
    assertThat(event.getLastError()).contains("Kafka unavailable");
    assertThat(event.getNextAttemptAt()).isAfter(event.getOccurredAt());

    publisher.publish();

    assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
    assertThat(event.getPublishedAt()).isNotNull();
    assertThat(event.getLastError()).isNull();
    verify(kafka, org.mockito.Mockito.times(2))
        .send(event.getTopic(), event.getAggregateId().toString(), event.getPayload());
  }

  private OutboxEvent pendingEvent() {
    Instant now = Instant.now();
    OutboxEvent event = new OutboxEvent();
    event.setId(UUID.randomUUID());
    event.setAggregateType("BOOKING");
    event.setAggregateId(UUID.randomUUID());
    event.setEventType("BOOKING_CONFIRMED");
    event.setTopic("booking.events");
    event.setPayload("{}");
    event.setOccurredAt(now);
    event.setNextAttemptAt(now);
    return event;
  }
}
