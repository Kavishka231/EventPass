package com.eventpass.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventpass.common.error.ApiException;
import com.eventpass.common.metrics.BusinessMetrics;
import com.eventpass.common.outbox.OutboxService;
import com.eventpass.payment.PaymentProvider;
import com.eventpass.payment.PaymentRepository;
import com.eventpass.payment.RefundTransactions;
import com.eventpass.seat.SeatLockService;
import com.eventpass.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingServiceFailureTest {
  private final BookingRepository bookings = mock(BookingRepository.class);
  private final BookingItemRepository bookingItems = mock(BookingItemRepository.class);
  private final SeatLockService locks = mock(SeatLockService.class);
  private final PaymentProvider provider = mock(PaymentProvider.class);
  private final BookingPaymentTransactions paymentTransactions =
      mock(BookingPaymentTransactions.class);
  private final RefundTransactions refundTransactions = mock(RefundTransactions.class);
  private final PaymentRepository payments = mock(PaymentRepository.class);
  private final OutboxService outbox = mock(OutboxService.class);
  private final BusinessMetrics metrics = mock(BusinessMetrics.class);
  private BookingService service;

  @BeforeEach
  void setUp() {
    service =
        new BookingService(
            bookings,
            bookingItems,
            locks,
            provider,
            paymentTransactions,
            refundTransactions,
            payments,
            outbox,
            metrics);
  }

  @Test
  void providerSuccessFollowedByPaymentFinalizationFailureRequiresReconciliation() {
    UUID bookingId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID seatId = UUID.randomUUID();
    User customer = new User();
    customer.setId(UUID.randomUUID());
    BookingController.CreateBookingRequest request =
        new BookingController.CreateBookingRequest(eventId, List.of(seatId), "tok_success");
    BookingController.BookingResponse response = response(bookingId, eventId, seatId);
    when(paymentTransactions.prepare(request, "payment-key", customer))
        .thenReturn(
            BookingPaymentTransactions.PreparedBooking.created(
                bookingId, eventId, List.of(seatId), "lock-owner", response));
    when(provider.charge("tok_success", response.totalAmount(), "LKR", "payment-key"))
        .thenReturn(new PaymentProvider.PaymentResult(true, "provider-payment", null));
    when(paymentTransactions.complete(any(), any()))
        .thenThrow(new IllegalStateException("database commit failed"));

    assertThatThrownBy(() -> service.create(request, "payment-key", customer))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo("PAYMENT_FINALIZATION_UNKNOWN"));

    verify(paymentTransactions)
        .markOutcomeUnknown(
            org.mockito.ArgumentMatchers.eq(bookingId), any(RuntimeException.class));
    verify(locks).release(eventId, seatId, "lock-owner");
    verify(metrics).paymentFailed();
  }

  @Test
  void providerSuccessFollowedByRefundFinalizationFailureRequiresReconciliation() {
    UUID bookingId = UUID.randomUUID();
    UUID refundId = UUID.randomUUID();
    User customer = new User();
    customer.setId(UUID.randomUUID());
    RefundTransactions.PreparedRefund refund =
        new RefundTransactions.PreparedRefund(
            refundId, "payment-reference", new BigDecimal("100.00"), "LKR", "refund-key", true);
    when(refundTransactions.prepare(bookingId, customer)).thenReturn(refund);
    when(provider.refund("payment-reference", refund.amount(), "LKR", "refund-key"))
        .thenReturn(new PaymentProvider.RefundResult(true, "provider-refund", null));
    when(refundTransactions.complete(any(), any()))
        .thenThrow(new IllegalStateException("database commit failed"));

    assertThatThrownBy(() -> service.cancel(bookingId, customer))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo("REFUND_FINALIZATION_UNKNOWN"));

    verify(refundTransactions).markOutcomeUnknown(any(), any());
    verify(metrics).refundFailed();
  }

  private BookingController.BookingResponse response(UUID bookingId, UUID eventId, UUID seatId) {
    return new BookingController.BookingResponse(
        bookingId,
        "EVP-TEST",
        eventId,
        Booking.Status.PENDING,
        new BigDecimal("100.00"),
        "LKR",
        List.of(seatId),
        Instant.now());
  }
}
