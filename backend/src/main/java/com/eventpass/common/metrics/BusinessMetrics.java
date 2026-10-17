package com.eventpass.common.metrics;

import com.eventpass.common.outbox.OutboxEvent;
import com.eventpass.common.outbox.OutboxEventRepository;
import com.eventpass.payment.Payment;
import com.eventpass.payment.PaymentRepository;
import com.eventpass.payment.RefundRepository;
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
  private final MeterRegistry registry;

  public BusinessMetrics(
      MeterRegistry registry,
      OutboxEventRepository outboxEvents,
      PaymentRepository payments,
      RefundRepository refunds) {
    this.registry = registry;
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
    reconciliationGauges(registry, payments, refunds);
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

  public void reconciliationSucceeded(String type) {
    registry.counter("eventpass.reconciliation.successes", "type", type).increment();
  }

  public void reconciliationFailed(String type) {
    registry.counter("eventpass.reconciliation.failures", "type", type).increment();
  }

  private void reconciliationGauges(
      MeterRegistry registry, PaymentRepository payments, RefundRepository refunds) {
    Gauge.builder(
            "eventpass.reconciliation.backlog",
            payments,
            repository ->
                repository.countByReconciliationStatusIn(
                    java.util.EnumSet.of(
                        Payment.ReconciliationStatus.PENDING,
                        Payment.ReconciliationStatus.IN_PROGRESS)))
        .tag("type", "payment")
        .tag("status", "pending")
        .register(registry);
    Gauge.builder(
            "eventpass.reconciliation.backlog",
            refunds,
            repository ->
                repository.countByReconciliationStatusIn(
                    java.util.EnumSet.of(
                        Payment.ReconciliationStatus.PENDING,
                        Payment.ReconciliationStatus.IN_PROGRESS)))
        .tag("type", "refund")
        .tag("status", "pending")
        .register(registry);
    Gauge.builder(
            "eventpass.reconciliation.backlog",
            payments,
            repository ->
                repository.countByReconciliationStatus(Payment.ReconciliationStatus.MANUAL_REVIEW))
        .tag("type", "payment")
        .tag("status", "manual_review")
        .register(registry);
    Gauge.builder(
            "eventpass.reconciliation.backlog",
            refunds,
            repository ->
                repository.countByReconciliationStatus(Payment.ReconciliationStatus.MANUAL_REVIEW))
        .tag("type", "refund")
        .tag("status", "manual_review")
        .register(registry);
  }
}
