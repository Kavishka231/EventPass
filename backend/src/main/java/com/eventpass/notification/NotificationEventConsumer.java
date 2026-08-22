package com.eventpass.notification;

import com.eventpass.common.kafka.KafkaTopics;
import com.eventpass.common.outbox.EventEnvelope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {
  private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);
  private final ObjectMapper objectMapper;
  private final NotificationEventService notificationEvents;

  public NotificationEventConsumer(
      ObjectMapper objectMapper, NotificationEventService notificationEvents) {
    this.objectMapper = objectMapper;
    this.notificationEvents = notificationEvents;
  }

  @KafkaListener(
      topics = {KafkaTopics.BOOKING_EVENTS, KafkaTopics.PAYMENT_EVENTS, KafkaTopics.TICKET_EVENTS})
  public void consume(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic)
      throws Exception {
    EventEnvelope<JsonNode> event =
        objectMapper.readValue(payload, new TypeReference<EventEnvelope<JsonNode>>() {});
    if (!notificationEvents.handle(event)) return;
    log.info(
        "notification_event_received topic={} eventType={} version={} eventId={}",
        topic,
        event.eventType(),
        event.version(),
        event.eventId());
  }
}
