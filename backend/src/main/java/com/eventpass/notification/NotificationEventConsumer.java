package com.eventpass.notification;

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

  @KafkaListener(topics = {"booking.events", "payment.events", "ticket.events"})
  public void consume(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic)
      throws Exception {
    var event = objectMapper.readTree(payload);
    UUID eventId = UUID.fromString(event.path("eventId").asText());
    if (!processedEvents.claim(CONSUMER, eventId)) {
      return;
    }
    log.info(
        "notification_event_received topic={} eventType={} eventId={}",
        topic,
        event.path("eventType").asText(),
        eventId);
  }
}
