package com.eventpass.common.outbox;

import com.eventpass.common.metrics.BusinessMetrics;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    name = "eventpass.outbox.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxPublisher {
  private static final int MAXIMUM_ATTEMPTS = 10;
  private static final long INITIAL_BACKOFF_SECONDS = 2;
  private static final long MAXIMUM_BACKOFF_SECONDS = 900;
  private final OutboxEventRepository events;
  private final KafkaTemplate<String, String> kafka;
  private final BusinessMetrics metrics;

  public OutboxPublisher(
      OutboxEventRepository events, KafkaTemplate<String, String> kafka, BusinessMetrics metrics) {
    this.events = events;
    this.kafka = kafka;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${eventpass.outbox.publish-delay:PT2S}")
  @Transactional
  public void publish() {
    for (OutboxEvent event : events.claimPendingBatch(MAXIMUM_ATTEMPTS, 100)) {
      try {
        kafka
            .send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
            .get(5, TimeUnit.SECONDS);
        event.markPublished(Instant.now());
      } catch (Exception exception) {
        metrics.kafkaPublishFailed();
        String message = exception.getMessage();
        String error =
            message == null
                ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(500, message.length()));
        event.recordFailure(
            error,
            Instant.now(),
            MAXIMUM_ATTEMPTS,
            INITIAL_BACKOFF_SECONDS,
            MAXIMUM_BACKOFF_SECONDS);
      }
    }
  }
}
