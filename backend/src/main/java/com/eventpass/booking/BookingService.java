package com.eventpass.booking;

import com.eventpass.common.error.ApiException;
import com.eventpass.common.metrics.BusinessMetrics;
import com.eventpass.common.outbox.OutboxService;
import com.eventpass.payment.*;
import com.eventpass.seat.*;
import com.eventpass.ticket.*;
import com.eventpass.user.User;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
  private static final Logger log = LoggerFactory.getLogger(BookingService.class);
  private static final String BOOKING_TOPIC = "booking.events";
  private final BookingRepository bookings;
  private final BookingItemRepository bookingItems;
  private final SeatLockService locks;
  private final PaymentProvider paymentProvider;
  private final BookingPaymentTransactions paymentTransactions;
  private final RefundTransactions refundTransactions;
  private final PaymentRepository payments;
  private final OutboxService outbox;
  private final BusinessMetrics metrics;

  public BookingService(
      BookingRepository bookings,
      BookingItemRepository bookingItems,
      SeatLockService locks,
      PaymentProvider paymentProvider,
      BookingPaymentTransactions paymentTransactions,
      RefundTransactions refundTransactions,
      PaymentRepository payments,
      OutboxService outbox,
      BusinessMetrics metrics) {
    this.bookings = bookings;
    this.bookingItems = bookingItems;
    this.locks = locks;
    this.paymentProvider = paymentProvider;
    this.paymentTransactions = paymentTransactions;
    this.refundTransactions = refundTransactions;
    this.payments = payments;
    this.outbox = outbox;
    this.metrics = metrics;
  }

  public BookingController.BookingResponse create(
      BookingController.CreateBookingRequest request, String idempotencyKey, User user) {
    metrics.bookingAttempted();
    BookingPaymentTransactions.PreparedBooking prepared =
        paymentTransactions.prepare(request, idempotencyKey, user);
    if (!prepared.requiresCharge()) return prepared.response();
    try {
      paymentTransactions.markAttempted(prepared.bookingId());
      PaymentProvider.PaymentResult result;
      try {
        result =
            paymentProvider.charge(
                request.paymentToken(),
                prepared.response().totalAmount(),
                prepared.response().currency(),
                idempotencyKey);
      } catch (RuntimeException exception) {
        metrics.paymentFailed();
        paymentTransactions.markOutcomeUnknown(prepared.bookingId(), exception);
        throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "PAYMENT_OUTCOME_UNKNOWN",
            "The payment outcome is being reconciled; do not retry with a new key.");
      }
      BookingPaymentTransactions.Completion completion;
      try {
        completion = paymentTransactions.complete(prepared.bookingId(), result);
      } catch (RuntimeException exception) {
        metrics.paymentFailed();
        recordUnknownPayment(prepared.bookingId(), exception);
        throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "PAYMENT_FINALIZATION_UNKNOWN",
            "The provider returned a payment result, but booking finalization requires reconciliation.");
      }
      if (!completion.successful()) {
        metrics.bookingFailed();
        metrics.paymentFailed();
        throw paymentFailed();
      }
      metrics.paymentSucceeded();
      metrics.bookingSucceeded();
      return completion.response();
    } finally {
      prepared
          .seatIds()
          .forEach(seatId -> locks.release(prepared.eventId(), seatId, prepared.lockOwner()));
    }
  }

  @Transactional(readOnly = true)
  public Page<BookingController.BookingResponse> list(User user, Pageable pageable) {
    Page<BookingListRow> page = bookings.findListRowsByUserId(user.getId(), pageable);
    List<UUID> bookingIds = page.getContent().stream().map(BookingListRow::id).toList();
    if (bookingIds.isEmpty()) return page.map(row -> response(row, List.of()));
    Map<UUID, List<UUID>> seatIdsByBooking =
        bookingItems.findSeatRowsByBookingIds(bookingIds).stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    BookingSeatRow::bookingId,
                    LinkedHashMap::new,
                    java.util.stream.Collectors.mapping(
                        BookingSeatRow::eventSeatId, java.util.stream.Collectors.toList())));
    return page.map(row -> response(row, seatIdsByBooking.getOrDefault(row.id(), List.of())));
  }

  @Transactional(readOnly = true)
  public BookingController.BookingResponse get(UUID id, User user) {
    return response(owned(id, user));
  }

  public void cancel(UUID id, User user) {
    refund(refundTransactions.prepare(id, user));
  }

  public void cancelForEvent(UUID bookingId) {
    refund(refundTransactions.prepareForEventCancellation(bookingId));
  }

  private void refund(RefundTransactions.PreparedRefund refund) {
    if (!refund.requiresProviderCall()) return;
    refundTransactions.markAttempted(refund.refundId());
    PaymentProvider.RefundResult result;
    try {
      result =
          paymentProvider.refund(
              refund.paymentReference(),
              refund.amount(),
              refund.currency(),
              refund.idempotencyKey());
    } catch (RuntimeException exception) {
      metrics.refundFailed();
      refundTransactions.markOutcomeUnknown(refund.refundId(), exception);
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "REFUND_OUTCOME_UNKNOWN",
          "The refund outcome is being reconciled; do not submit another cancellation.");
    }
    boolean completed;
    try {
      completed = refundTransactions.complete(refund.refundId(), result);
    } catch (RuntimeException exception) {
      metrics.refundFailed();
      recordUnknownRefund(refund.refundId(), exception);
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "REFUND_FINALIZATION_UNKNOWN",
          "The provider returned a refund result, but cancellation finalization requires reconciliation.");
    }
    if (!completed) {
      metrics.refundFailed();
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "REFUND_FAILED",
          "The payment provider could not complete the refund.");
    }
    metrics.refundSucceeded();
    metrics.bookingCancelled();
  }

  @Transactional
  public int expirePendingBookings() {
    List<Booking> expired = bookings.claimExpiredBatch(Instant.now(), 100);
    List<Booking> safeToExpire =
        expired.stream()
            .filter(
                booking ->
                    payments
                        .findByBookingId(booking.getId())
                        .map(
                            payment ->
                                payment.getStatus() != Payment.Status.PROCESSING
                                    && payment.getStatus() != Payment.Status.UNKNOWN
                                    && payment.getReconciliationStatus()
                                        != Payment.ReconciliationStatus.PENDING)
                        .orElse(true))
            .toList();
    safeToExpire.forEach(
        booking -> {
          booking.setStatus(Booking.Status.EXPIRED);
          booking
              .getItems()
              .forEach(item -> item.getEventSeat().setStatus(EventSeat.Status.AVAILABLE));
          outbox.record(
              BOOKING_TOPIC,
              "BOOKING_EXPIRED",
              booking.getId(),
              Map.of("bookingId", booking.getId()));
        });
    return safeToExpire.size();
  }

  private void recordUnknownPayment(UUID bookingId, RuntimeException finalizationFailure) {
    try {
      paymentTransactions.markOutcomeUnknown(bookingId, finalizationFailure);
    } catch (RuntimeException reconciliationFailure) {
      finalizationFailure.addSuppressed(reconciliationFailure);
      log.error(
          "Payment finalization and reconciliation persistence failed bookingId={}",
          bookingId,
          finalizationFailure);
    }
  }

  private void recordUnknownRefund(UUID refundId, RuntimeException finalizationFailure) {
    try {
      refundTransactions.markOutcomeUnknown(refundId, finalizationFailure);
    } catch (RuntimeException reconciliationFailure) {
      finalizationFailure.addSuppressed(reconciliationFailure);
      log.error(
          "Refund finalization and reconciliation persistence failed refundId={}",
          refundId,
          finalizationFailure);
    }
  }

  private Booking owned(UUID id, User user) {
    return bookings
        .findById(id)
        .filter(
            booking ->
                user.getRole() == User.Role.ADMIN || booking.getUser().getId().equals(user.getId()))
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking was not found."));
  }

  private BookingController.BookingResponse response(Booking booking) {
    return new BookingController.BookingResponse(
        booking.getId(),
        booking.getBookingReference(),
        booking.getEvent().getId(),
        booking.getStatus(),
        booking.getTotalAmount(),
        booking.getCurrency(),
        booking.getItems().stream().map(item -> item.getEventSeat().getId()).toList(),
        booking.getCreatedAt());
  }

  private BookingController.BookingResponse response(BookingListRow booking, List<UUID> seatIds) {
    return new BookingController.BookingResponse(
        booking.id(),
        booking.reference(),
        booking.eventId(),
        booking.status(),
        booking.totalAmount(),
        booking.currency(),
        seatIds,
        booking.createdAt());
  }

  private ApiException paymentFailed() {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_FAILED", "Mock payment was declined.");
  }
}
