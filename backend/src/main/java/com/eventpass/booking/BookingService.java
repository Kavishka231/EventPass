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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
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
      BookingPaymentTransactions.Completion completion =
          paymentTransactions.complete(prepared.bookingId(), result);
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
  public Page<BookingController.CustomerBookingSummaryResponse> list(User user, Pageable pageable) {
    Page<BookingListRow> page = bookings.findListRowsByUserId(user.getId(), pageable);
    return page.map(this::summaryResponse);
  }

  @Transactional(readOnly = true)
  public BookingController.CustomerBookingDetailResponse get(UUID id, User user) {
    List<BookingDetailRow> rows = bookings.findCustomerDetailRows(id, user.getId());
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking was not found.");
    }
    BookingDetailRow first = rows.getFirst();
    return new BookingController.CustomerBookingDetailResponse(
        first.id(),
        first.reference(),
        first.status(),
        first.totalAmount(),
        first.currency(),
        first.createdAt(),
        first.updatedAt(),
        first.expiresAt(),
        new BookingController.EventSummary(
            first.eventId(),
            first.eventName(),
            first.eventStartDateTime(),
            first.eventEndDateTime()),
        new BookingController.VenueSummary(
            first.venueId(), first.venueName(), first.venueAddress(), first.venueCity()),
        rows.stream()
            .map(
                row ->
                    new BookingController.SeatSummary(
                        row.eventSeatId(),
                        row.seatId(),
                        row.section(),
                        row.rowNumber(),
                        row.seatNumber(),
                        row.seatType(),
                        row.unitPrice()))
            .toList(),
        first.paymentStatus() == null
            ? null
            : new BookingController.PaymentSummary(
                first.paymentStatus(), first.paymentAttemptedAt(), first.paymentCompletedAt()),
        first.refundStatus() == null
            ? null
            : new BookingController.RefundSummary(
                first.refundStatus(),
                first.refundAmount(),
                first.refundAttemptedAt(),
                first.refundCompletedAt()));
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
    if (!refundTransactions.complete(refund.refundId(), result)) {
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
    List<Booking> expired =
        bookings.findTop100ByStatusAndExpiresAtBefore(Booking.Status.PENDING, Instant.now());
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

  private BookingController.CustomerBookingSummaryResponse summaryResponse(BookingListRow booking) {
    return new BookingController.CustomerBookingSummaryResponse(
        booking.id(),
        booking.reference(),
        booking.status(),
        booking.totalAmount(),
        booking.currency(),
        booking.seatCount(),
        booking.createdAt(),
        new BookingController.EventSummary(
            booking.eventId(), booking.eventName(), booking.eventStartDateTime(), null),
        new BookingController.VenueSummary(
            booking.venueId(), booking.venueName(), null, booking.venueCity()));
  }

  private ApiException paymentFailed() {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_FAILED", "Mock payment was declined.");
  }
}
