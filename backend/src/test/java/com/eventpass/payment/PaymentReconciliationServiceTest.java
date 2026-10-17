package com.eventpass.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.eventpass.booking.Booking;
import com.eventpass.booking.BookingPaymentTransactions;
import com.eventpass.common.metrics.BusinessMetrics;
import com.eventpass.common.outbox.OutboxService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class PaymentReconciliationServiceTest {
  private final PaymentRepository payments = mock(PaymentRepository.class);
  private final RefundRepository refunds = mock(RefundRepository.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final PaymentReconciliationService service =
      new PaymentReconciliationService(
          payments,
          refunds,
          mock(PaymentProvider.class),
          mock(BookingPaymentTransactions.class),
          mock(RefundTransactions.class),
          mock(BusinessMetrics.class),
          outbox,
          mock(PlatformTransactionManager.class),
          25,
          3,
          Duration.ofSeconds(30),
          Duration.ofMinutes(2));

  @Test
  void unresolvedLookupUsesExponentialBackoff() {
    Payment payment = payment(2);
    when(payments.lockById(payment.getId())).thenReturn(Optional.of(payment));
    Instant before = Instant.now();

    service.deferPayment(payment.getId(), null);

    assertThat(payment.getReconciliationStatus()).isEqualTo(Payment.ReconciliationStatus.PENDING);
    assertThat(payment.getNextReconciliationAt()).isAfterOrEqualTo(before.plusSeconds(60));
    assertThat(payment.getLastReconciledAt()).isNotNull();
  }

  @Test
  void exhaustedLookupMovesToManualReviewAndPublishesEvent() {
    Payment payment = payment(3);
    when(payments.lockById(payment.getId())).thenReturn(Optional.of(payment));

    service.deferPayment(payment.getId(), new PaymentProviderException("provider offline"));

    assertThat(payment.getReconciliationStatus())
        .isEqualTo(Payment.ReconciliationStatus.MANUAL_REVIEW);
    assertThat(payment.getNextReconciliationAt()).isNull();
    assertThat(payment.getLastError()).isEqualTo("provider offline");
    verify(outbox)
        .record(
            eq("payment.events"),
            eq("PAYMENT_RECONCILIATION_MANUAL_REVIEW"),
            eq(payment.getBooking().getId()),
            eq(Map.of("bookingId", payment.getBooking().getId(), "paymentId", payment.getId())));
  }

  private Payment payment(int attempts) {
    Booking booking = new Booking();
    booking.setId(UUID.randomUUID());
    Payment payment = new Payment();
    payment.setId(UUID.randomUUID());
    payment.setBooking(booking);
    payment.setStatus(Payment.Status.UNKNOWN);
    payment.setReconciliationStatus(Payment.ReconciliationStatus.IN_PROGRESS);
    payment.setReconciliationAttempts(attempts);
    return payment;
  }
}
