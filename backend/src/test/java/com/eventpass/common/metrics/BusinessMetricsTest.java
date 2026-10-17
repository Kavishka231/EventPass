package com.eventpass.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eventpass.common.outbox.OutboxEvent;
import com.eventpass.common.outbox.OutboxEventRepository;
import com.eventpass.payment.Payment;
import com.eventpass.payment.PaymentRepository;
import com.eventpass.payment.RefundRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class BusinessMetricsTest {
  @Test
  void recordsBusinessOutcomesAndExposesOutboxBacklog() {
    OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    when(outbox.countByStatus(OutboxEvent.Status.PENDING)).thenReturn(7L);
    when(outbox.countByStatus(OutboxEvent.Status.FAILED)).thenReturn(2L);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PaymentRepository payments = mock(PaymentRepository.class);
    RefundRepository refunds = mock(RefundRepository.class);
    when(payments.countByReconciliationStatusIn(
            java.util.EnumSet.of(
                Payment.ReconciliationStatus.PENDING, Payment.ReconciliationStatus.IN_PROGRESS)))
        .thenReturn(4L);
    when(refunds.countByReconciliationStatus(Payment.ReconciliationStatus.MANUAL_REVIEW))
        .thenReturn(1L);
    BusinessMetrics metrics = new BusinessMetrics(registry, outbox, payments, refunds);

    metrics.bookingAttempted();
    metrics.bookingSucceeded();
    metrics.bookingFailed();
    metrics.paymentSucceeded();
    metrics.paymentFailed();
    metrics.refundSucceeded();
    metrics.refundFailed();
    metrics.bookingCancelled();
    metrics.bookingsExpired(3);
    metrics.kafkaPublishFailed();
    metrics.notificationFailed();
    metrics.reconciliationSucceeded("payment");
    metrics.reconciliationFailed("refund");

    assertCounter(registry, "eventpass.booking.attempts", 1);
    assertCounter(registry, "eventpass.booking.successes", 1);
    assertCounter(registry, "eventpass.booking.failures", 1);
    assertCounter(registry, "eventpass.payment.successes", 1);
    assertCounter(registry, "eventpass.payment.failures", 1);
    assertCounter(registry, "eventpass.refund.successes", 1);
    assertCounter(registry, "eventpass.refund.failures", 1);
    assertCounter(registry, "eventpass.booking.cancellations", 1);
    assertCounter(registry, "eventpass.booking.expirations", 3);
    assertCounter(registry, "eventpass.kafka.publish.failures", 1);
    assertCounter(registry, "eventpass.notification.failures", 1);
    assertThat(registry.get("eventpass.outbox.backlog").tag("status", "pending").gauge().value())
        .isEqualTo(7);
    assertThat(registry.get("eventpass.outbox.backlog").tag("status", "failed").gauge().value())
        .isEqualTo(2);
    assertThat(
            registry
                .get("eventpass.reconciliation.backlog")
                .tags("type", "payment", "status", "pending")
                .gauge()
                .value())
        .isEqualTo(4);
    assertThat(
            registry
                .get("eventpass.reconciliation.backlog")
                .tags("type", "refund", "status", "manual_review")
                .gauge()
                .value())
        .isEqualTo(1);
  }

  private void assertCounter(SimpleMeterRegistry registry, String name, double expected) {
    assertThat(registry.get(name).counter().count()).isEqualTo(expected);
  }
}
