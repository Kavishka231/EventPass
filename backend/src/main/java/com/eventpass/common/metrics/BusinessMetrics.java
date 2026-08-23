package com.eventpass.common.metrics;

import com.eventpass.common.outbox.OutboxEvent;
import com.eventpass.common.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {
  private final Counter bookingAttempts;
  private final Counter bookingSuccesses;
  private final Counter bookingFailures;
  private final Counter paymentSuccesses;
  private final Counter paymentFailures;
  private final Counter refundSuccesses;
  private final Counter refundFailures;
  private final Counter cancellations;
  private final Counter expirations;
  private final Counter kafkaFailures;
  private final Counter notificationFailures;

  public BusinessMetrics(MeterRegistry registry, OutboxEventRepository outboxEvents) {
    bookingAttempts = registry.counter("eventpass.booking.attempts");
    bookingSuccesses = registry.counter("eventpass.booking.successes");
    bookingFailures = registry.counter("eventpass.booking.failures");
    paymentSuccesses = registry.counter("eventpass.payment.successes");
    paymentFailures = registry.counter("eventpass.payment.failures");
    refundSuccesses = registry.counter("eventpass.refund.successes");
    refundFailures = registry.counter("eventpass.refund.failures");
    cancellations = registry.counter("eventpass.booking.cancellations");
    expirations = registry.counter("eventpass.booking.expirations");
    kafkaFailures = registry.counter("eventpass.kafka.publish.failures");
    notificationFailures = registry.counter("eventpass.notification.failures");
    Gauge.builder(
            "eventpass.outbox.backlog",
            outboxEvents,
            repository -> repository.countByStatus(OutboxEvent.Status.PENDING))
        .description("Number of outbox events waiting for publication")
        .tag("status", "pending")
        .register(registry);
    Gauge.builder(
            "eventpass.outbox.backlog",
            outboxEvents,
            repository -> repository.countByStatus(OutboxEvent.Status.FAILED))
        .description("Number of outbox events requiring operational recovery")
        .tag("status", "failed")
        .register(registry);
  }

  public void bookingAttempted() {
    bookingAttempts.increment();
  }

  public void bookingSucceeded() {
    bookingSuccesses.increment();
  }

  public void bookingFailed() {
    bookingFailures.increment();
  }

  public void paymentSucceeded() {
    paymentSuccesses.increment();
  }

  public void paymentFailed() {
    paymentFailures.increment();
  }

  public void refundSucceeded() {
    refundSuccesses.increment();
  }

  public void refundFailed() {
    refundFailures.increment();
  }

  public void bookingCancelled() {
    cancellations.increment();
  }

  public void bookingsExpired(int count) {
    if (count > 0) expirations.increment(count);
  }

  public void kafkaPublishFailed() {
    kafkaFailures.increment();
  }

  public void notificationFailed() {
    notificationFailures.increment();
  }
}
