package com.eventpass.booking;

import com.eventpass.common.error.ApiException;
import com.eventpass.common.outbox.OutboxService;
import com.eventpass.payment.*;
import com.eventpass.seat.*;
import com.eventpass.ticket.*;
import com.eventpass.user.User;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
  private static final String BOOKING_TOPIC = "booking.events";
  private final BookingRepository bookings;
  private final SeatLockService locks;
  private final PaymentProvider paymentProvider;
  private final BookingPaymentTransactions paymentTransactions;
  private final PaymentRepository payments;
  private final TicketRepository tickets;
  private final OutboxService outbox;
  private final Counter bookingAttempts;
  private final Counter successfulBookings;
  private final Counter failedBookings;
  private final Counter paymentFailures;

  public BookingService(
      BookingRepository bookings,
      SeatLockService locks,
      PaymentProvider paymentProvider,
      BookingPaymentTransactions paymentTransactions,
      PaymentRepository payments,
      TicketRepository tickets,
      OutboxService outbox,
      MeterRegistry meterRegistry) {
    this.bookings = bookings;
    this.locks = locks;
    this.paymentProvider = paymentProvider;
    this.paymentTransactions = paymentTransactions;
    this.payments = payments;
    this.tickets = tickets;
    this.outbox = outbox;
    this.bookingAttempts = meterRegistry.counter("eventpass.booking.attempts");
    this.successfulBookings = meterRegistry.counter("eventpass.booking.successes");
    this.failedBookings = meterRegistry.counter("eventpass.booking.failures");
    this.paymentFailures = meterRegistry.counter("eventpass.payment.failures");
  }

  public BookingController.BookingResponse create(
      BookingController.CreateBookingRequest request, String idempotencyKey, User user) {
    bookingAttempts.increment();
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
        paymentFailures.increment();
        paymentTransactions.markOutcomeUnknown(prepared.bookingId(), exception);
        throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "PAYMENT_OUTCOME_UNKNOWN",
            "The payment outcome is being reconciled; do not retry with a new key.");
      }
      BookingPaymentTransactions.Completion completion =
          paymentTransactions.complete(prepared.bookingId(), result);
      if (!completion.successful()) {
        failedBookings.increment();
        paymentFailures.increment();
        throw paymentFailed();
      }
      successfulBookings.increment();
      return completion.response();
    } finally {
      prepared
          .seatIds()
          .forEach(seatId -> locks.release(prepared.eventId(), seatId, prepared.lockOwner()));
    }
  }

  @Transactional(readOnly = true)
  public List<BookingController.BookingResponse> list(User user) {
    return bookings.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
        .map(this::response)
        .toList();
  }

  @Transactional(readOnly = true)
  public BookingController.BookingResponse get(UUID id, User user) {
    return response(owned(id, user));
  }

  @Transactional
  public void cancel(UUID id, User user) {
    Booking booking = owned(id, user);
    if (booking.getStatus() != Booking.Status.CONFIRMED
        || !booking
            .getEvent()
            .getStartDateTime()
            .isAfter(Instant.now().plus(Duration.ofHours(24)))) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "BOOKING_NOT_CANCELLABLE",
          "Booking is no longer eligible for cancellation.");
    }
    Payment payment =
        payments
            .findByBookingId(id)
            .filter(value -> value.getStatus() == Payment.Status.SUCCESS)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.CONFLICT,
                        "PAYMENT_NOT_REFUNDABLE",
                        "No successful payment is available to refund."));
    if (!paymentProvider.refund(
        payment.getPaymentReference(), payment.getAmount(), payment.getCurrency())) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "REFUND_FAILED",
          "The payment provider could not complete the refund.");
    }
    payment.setStatus(Payment.Status.REFUNDED);
    booking.setStatus(Booking.Status.CANCELLED);
    booking.getItems().forEach(item -> item.getEventSeat().setStatus(EventSeat.Status.AVAILABLE));
    tickets.findAllByBookingId(id).forEach(ticket -> ticket.setStatus(Ticket.Status.CANCELLED));
    outbox.record(
        BOOKING_TOPIC,
        "BOOKING_CANCELLED",
        booking.getId(),
        Map.of("bookingId", booking.getId(), "paymentReference", payment.getPaymentReference()));
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

  private ApiException paymentFailed() {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_FAILED", "Mock payment was declined.");
  }
}
