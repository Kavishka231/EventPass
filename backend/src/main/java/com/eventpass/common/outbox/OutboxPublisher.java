package com.eventpass.common.outbox;

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
  private final OutboxEventRepository events;
  private final KafkaTemplate<String, String> kafka;

  public OutboxPublisher(OutboxEventRepository events, KafkaTemplate<String, String> kafka) {
    this.events = events;
    this.kafka = kafka;
  }

  @Scheduled(fixedDelayString = "${eventpass.outbox.publish-delay:PT2S}")
  @Transactional
  public void publish() {
    for (OutboxEvent event :
        events.findTop100ByPublishedAtIsNullAndAttemptsLessThanOrderByOccurredAt(10)) {
      try {
        kafka
            .send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
            .get(5, TimeUnit.SECONDS);
        event.setPublishedAt(Instant.now());
        event.setLastError(null);
      } catch (Exception exception) {
        event.setAttempts(event.getAttempts() + 1);
        String message = exception.getMessage();
        event.setLastError(
            message == null
                ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(500, message.length())));
      }
    }
  }
}
