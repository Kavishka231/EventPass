package com.eventpass.notification;

import com.eventpass.booking.Booking;
import com.eventpass.booking.BookingRepository;
import com.eventpass.common.outbox.EventEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationEventService {
  private static final String CONSUMER = "notification-consumer";
  private static final Map<String, Content> CONTENT =
      Map.of(
          "BOOKING_CREATED",
          new Content("Booking received", "Your booking has been received and is being processed."),
          "BOOKING_CANCELLED",
          new Content(
              "Booking cancelled", "Your booking was cancelled and its seats were released."),
          "BOOKING_EXPIRED",
          new Content(
              "Booking expired", "Your unpaid booking expired and its seats were released."),
          "PAYMENT_COMPLETED",
          new Content("Payment completed", "Your payment succeeded and your booking is confirmed."),
          "PAYMENT_FAILED",
          new Content("Payment failed", "Your payment failed and no booking charge was completed."),
          "PAYMENT_RECONCILIATION_REQUIRED",
          new Content(
              "Payment under review",
              "Your payment outcome is being reconciled. No action is required."),
          "TICKET_GENERATED",
          new Content("Ticket available", "A digital ticket is now available for your booking."));

  private final ProcessedEventService processedEvents;
  private final BookingRepository bookings;
  private final NotificationRepository notifications;

  public NotificationEventService(
      ProcessedEventService processedEvents,
      BookingRepository bookings,
      NotificationRepository notifications) {
    this.processedEvents = processedEvents;
    this.bookings = bookings;
    this.notifications = notifications;
  }

  @Transactional
  public boolean handle(EventEnvelope<JsonNode> event) {
    if (!processedEvents.claim(CONSUMER, event.eventId())) return false;
    Content content = CONTENT.get(event.eventType());
    if (content == null) return true;

    Booking booking = bookings.findById(event.aggregateId()).orElseThrow();
    Notification notification = new Notification();
    notification.setUser(booking.getUser());
    notification.setSourceEventId(event.eventId());
    notification.setType(event.eventType());
    notification.setTitle(content.title());
    notification.setMessage(content.message());
    notifications.save(notification);
    return true;
  }

  private record Content(String title, String message) {}
}
