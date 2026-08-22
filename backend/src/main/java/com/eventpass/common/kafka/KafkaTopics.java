package com.eventpass.common.kafka;

import java.util.List;

public final class KafkaTopics {
  public static final String BOOKING_EVENTS = "booking.events";
  public static final String PAYMENT_EVENTS = "payment.events";
  public static final String TICKET_EVENTS = "ticket.events";
  public static final String EVENT_EVENTS = "event.events";
  public static final String NOTIFICATION_EVENTS = "notification.events";
  public static final List<String> DOMAIN_TOPICS =
      List.of(BOOKING_EVENTS, PAYMENT_EVENTS, TICKET_EVENTS, EVENT_EVENTS, NOTIFICATION_EVENTS);
  public static final List<String> CONSUMED_TOPICS =
      List.of(BOOKING_EVENTS, PAYMENT_EVENTS, TICKET_EVENTS);

  private KafkaTopics() {}

  public static String deadLetter(String topic) {
    return topic + ".DLT";
  }
}
