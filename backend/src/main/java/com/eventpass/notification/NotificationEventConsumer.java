package com.eventpass.notification;

import com.eventpass.common.kafka.KafkaTopics;
import com.eventpass.common.outbox.EventEnvelope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {
  private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);
  private static final String CONSUMER = "notification-consumer";
  private final ObjectMapper objectMapper;
  private final ProcessedEventService processedEvents;

  public NotificationEventConsumer(
      ObjectMapper objectMapper, ProcessedEventService processedEvents) {
    this.objectMapper = objectMapper;
    this.processedEvents = processedEvents;
  }

  @KafkaListener(
      topics = {KafkaTopics.BOOKING_EVENTS, KafkaTopics.PAYMENT_EVENTS, KafkaTopics.TICKET_EVENTS})
  public void consume(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic)
      throws Exception {
    EventEnvelope<JsonNode> event =
        objectMapper.readValue(payload, new TypeReference<EventEnvelope<JsonNode>>() {});
    UUID eventId = event.eventId();
    if (!processedEvents.claim(CONSUMER, eventId)) {
      return;
    }
    log.info(
        "notification_event_received topic={} eventType={} version={} eventId={}",
        topic,
        event.eventType(),
        event.version(),
        eventId);
  }
}
