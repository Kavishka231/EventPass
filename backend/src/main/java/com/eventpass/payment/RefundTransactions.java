package com.eventpass.payment;

import com.eventpass.booking.Booking;
import com.eventpass.booking.BookingRepository;
import com.eventpass.common.error.ApiException;
import com.eventpass.common.outbox.OutboxService;
import com.eventpass.seat.EventSeat;
import com.eventpass.seat.EventSeatRepository;
import com.eventpass.ticket.Ticket;
import com.eventpass.ticket.TicketRepository;
import com.eventpass.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundTransactions {
  private static final String BOOKING_TOPIC = "booking.events";

  private final BookingRepository bookings;
  private final PaymentRepository payments;
  private final RefundRepository refunds;
  private final EventSeatRepository seats;
  private final TicketRepository tickets;
  private final OutboxService outbox;

  public RefundTransactions(
      BookingRepository bookings,
      PaymentRepository payments,
      RefundRepository refunds,
      EventSeatRepository seats,
      TicketRepository tickets,
      OutboxService outbox) {
    this.bookings = bookings;
    this.payments = payments;
    this.refunds = refunds;
    this.seats = seats;
    this.tickets = tickets;
    this.outbox = outbox;
  }

  @Transactional
  public PreparedRefund prepare(UUID bookingId, User user) {
    Booking booking = ownedAndLocked(bookingId, user);
    Payment payment =
        payments
            .lockByBookingId(bookingId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.CONFLICT,
                        "PAYMENT_NOT_REFUNDABLE",
                        "No payment is available to refund."));
    Optional<Refund> existing = refunds.findByPaymentId(payment.getId());
    if (existing.isPresent()) return replay(existing.get(), payment);
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
    if (payment.getStatus() != Payment.Status.SUCCESS) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "PAYMENT_NOT_REFUNDABLE",
          "No successful payment is available to refund.");
    }
    Refund refund = new Refund();
    refund.setPayment(payment);
    refund.setBooking(booking);
    refund.setAmount(payment.getAmount());
    refund.setCurrency(payment.getCurrency());
    refund.setIdempotencyKey("booking-refund:" + booking.getId());
    refunds.save(refund);
    return new PreparedRefund(
        refund.getId(),
        payment.getPaymentReference(),
        refund.getAmount(),
        refund.getCurrency(),
        refund.getIdempotencyKey(),
        true);
  }

  @Transactional
  public void markAttempted(UUID refundId) {
    Refund refund = lockRefund(refundId);
    if (refund.getStatus() != Refund.Status.PENDING) return;
    refund.markProcessing(Instant.now());
  }

  @Transactional
  public boolean complete(UUID refundId, PaymentProvider.RefundResult result) {
    Refund refund = lockRefund(refundId);
    if (refund.getStatus() != Refund.Status.PROCESSING) {
      return refund.getStatus() == Refund.Status.SUCCESS;
    }
    if (!result.successful()) {
      refund.markFailed(result.reference(), result.failureCode(), Instant.now());
      return false;
    }
    Booking booking = bookings.lockById(refund.getBooking().getId()).orElseThrow();
    Payment payment = payments.lockByBookingId(booking.getId()).orElseThrow();
    List<UUID> seatIds =
        booking.getItems().stream().map(item -> item.getEventSeat().getId()).sorted().toList();
    List<EventSeat> inventory = seats.lockForBooking(booking.getEvent().getId(), seatIds);
    refund.markSuccessful(result.reference(), Instant.now());
    payment.setStatus(Payment.Status.REFUNDED);
    booking.setStatus(Booking.Status.CANCELLED);
    inventory.forEach(seat -> seat.setStatus(EventSeat.Status.AVAILABLE));
    tickets
        .findAllByBookingId(booking.getId())
        .forEach(ticket -> ticket.setStatus(Ticket.Status.CANCELLED));
    outbox.record(
        BOOKING_TOPIC,
        "BOOKING_CANCELLED",
        booking.getId(),
        Map.of(
            "bookingId", booking.getId(),
            "paymentReference", payment.getPaymentReference(),
            "refundReference", result.reference()));
    return true;
  }

  @Transactional
  public void markOutcomeUnknown(UUID refundId, RuntimeException providerError) {
    Refund refund = lockRefund(refundId);
    if (refund.getStatus() != Refund.Status.PROCESSING) return;
    String message = providerError.getMessage();
    if (message == null || message.isBlank()) message = "Refund provider outcome was unknown.";
    refund.markUnknown(message.substring(0, Math.min(message.length(), 500)));
  }

  private PreparedRefund replay(Refund refund, Payment payment) {
    if (refund.getStatus() == Refund.Status.SUCCESS) {
      return new PreparedRefund(
          refund.getId(),
          payment.getPaymentReference(),
          refund.getAmount(),
          refund.getCurrency(),
          refund.getIdempotencyKey(),
          false);
    }
    String code = refund.getStatus() == Refund.Status.FAILED ? "REFUND_FAILED" : "REFUND_PENDING";
    throw new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        code,
        "The existing refund has not completed successfully and requires review.");
  }

  private Booking ownedAndLocked(UUID id, User user) {
    return bookings
        .lockById(id)
        .filter(
            booking ->
                user.getRole() == User.Role.ADMIN || booking.getUser().getId().equals(user.getId()))
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking was not found."));
  }

  private Refund lockRefund(UUID refundId) {
    return refunds.lockById(refundId).orElseThrow();
  }

  public record PreparedRefund(
      UUID refundId,
      String paymentReference,
      java.math.BigDecimal amount,
      String currency,
      String idempotencyKey,
      boolean requiresProviderCall) {}
}
