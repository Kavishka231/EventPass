package com.eventpass.booking;

import com.eventpass.common.error.ApiException;
import com.eventpass.common.outbox.OutboxService;
import com.eventpass.event.Event;
import com.eventpass.event.EventRepository;
import com.eventpass.payment.*;
import com.eventpass.seat.*;
import com.eventpass.ticket.*;
import com.eventpass.user.User;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
  private static final String BOOKING_TOPIC = "booking.events";
  private static final String PAYMENT_TOPIC = "payment.events";
  private static final String TICKET_TOPIC = "ticket.events";

  private final BookingRepository bookings;
  private final EventRepository events;
  private final EventSeatRepository seats;
  private final SeatLockService locks;
  private final PaymentProvider paymentProvider;
  private final PaymentRepository payments;
  private final TicketRepository tickets;
  private final OutboxService outbox;
  private final Duration holdTtl;
  private final Counter bookingAttempts;
  private final Counter successfulBookings;
  private final Counter failedBookings;
  private final Counter paymentFailures;
  private final SecureRandom random = new SecureRandom();

  public BookingService(
      BookingRepository bookings,
      EventRepository events,
      EventSeatRepository seats,
      SeatLockService locks,
      PaymentProvider paymentProvider,
      PaymentRepository payments,
      TicketRepository tickets,
      OutboxService outbox,
      MeterRegistry meterRegistry,
      @Value("${eventpass.booking.hold-ttl}") Duration holdTtl) {
    this.bookings = bookings;
    this.events = events;
    this.seats = seats;
    this.locks = locks;
    this.paymentProvider = paymentProvider;
    this.payments = payments;
    this.tickets = tickets;
    this.outbox = outbox;
    this.holdTtl = holdTtl;
    this.bookingAttempts = meterRegistry.counter("eventpass.booking.attempts");
    this.successfulBookings = meterRegistry.counter("eventpass.booking.successes");
    this.failedBookings = meterRegistry.counter("eventpass.booking.failures");
    this.paymentFailures = meterRegistry.counter("eventpass.payment.failures");
  }

  @Transactional(noRollbackFor = ApiException.class)
  public BookingController.BookingResponse create(
      BookingController.CreateBookingRequest request, String idempotencyKey, User user) {
    bookingAttempts.increment();
    Optional<Booking> previous = bookings.findByIdempotencyKey(idempotencyKey);
    if (previous.isPresent()) {
      Booking booking = previous.get();
      if (!booking.getUser().getId().equals(user.getId())) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "IDEMPOTENCY_KEY_REUSED",
            "Idempotency key belongs to another request.");
      }
      if (booking.getStatus() == Booking.Status.FAILED) {
        throw paymentFailed();
      }
      return response(booking);
    }

    Event event =
        events
            .findById(request.eventId())
            .filter(
                value ->
                    value.getStatus() == Event.Status.PUBLISHED
                        && value.getStartDateTime().isAfter(Instant.now()))
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "EVENT_NOT_BOOKABLE",
                        "Event is not available for booking."));

    List<UUID> requestedSeats = request.eventSeatIds().stream().distinct().sorted().toList();
    if (requestedSeats.size() != request.eventSeatIds().size()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "DUPLICATE_SEAT", "A seat was selected more than once.");
    }

    String lockOwner = UUID.randomUUID().toString();
    List<UUID> acquired = new ArrayList<>();
    try {
      acquireLocks(event.getId(), requestedSeats, lockOwner, acquired);
      List<EventSeat> inventory = seats.lockForBooking(event.getId(), requestedSeats);
      validateAvailability(requestedSeats, inventory);

      Booking booking = newBooking(event, user, idempotencyKey, inventory);
      bookings.save(booking);
      outbox.record(
          BOOKING_TOPIC,
          "BOOKING_CREATED",
          booking.getId(),
          Map.of("bookingId", booking.getId(), "reference", booking.getBookingReference()));

      PaymentProvider.PaymentResult result =
          paymentProvider.charge(
              request.paymentToken(),
              booking.getTotalAmount(),
              booking.getCurrency(),
              idempotencyKey);
      savePayment(booking, result);
      if (!result.successful()) {
        failedBookings.increment();
        paymentFailures.increment();
        booking.setStatus(Booking.Status.FAILED);
        inventory.forEach(seat -> seat.setStatus(EventSeat.Status.AVAILABLE));
        outbox.record(
            PAYMENT_TOPIC,
            "PAYMENT_FAILED",
            booking.getId(),
            Map.of("bookingId", booking.getId(), "paymentReference", result.reference()));
        throw paymentFailed();
      }

      booking.setStatus(Booking.Status.CONFIRMED);
      inventory.forEach(seat -> seat.setStatus(EventSeat.Status.SOLD));
      outbox.record(
          PAYMENT_TOPIC,
          "PAYMENT_COMPLETED",
          booking.getId(),
          Map.of("bookingId", booking.getId(), "paymentReference", result.reference()));
      issueTickets(booking, inventory);
      successfulBookings.increment();
      return response(booking);
    } finally {
      acquired.forEach(seatId -> locks.release(event.getId(), seatId, lockOwner));
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
    expired.forEach(
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
    return expired.size();
  }

  private void acquireLocks(UUID eventId, List<UUID> seatIds, String owner, List<UUID> acquired) {
    for (UUID seatId : seatIds) {
      if (!locks.acquire(eventId, seatId, owner)) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "SEAT_UNAVAILABLE",
            "One or more selected seats are currently held.");
      }
      acquired.add(seatId);
    }
  }

  private void validateAvailability(List<UUID> requested, List<EventSeat> inventory) {
    if (inventory.size() != requested.size()
        || inventory.stream().anyMatch(seat -> seat.getStatus() != EventSeat.Status.AVAILABLE)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "SEAT_UNAVAILABLE",
          "One or more selected seats are no longer available.");
    }
  }

  private Booking newBooking(
      Event event, User user, String idempotencyKey, List<EventSeat> inventory) {
    Booking booking = new Booking();
    booking.setBookingReference(
        "EVP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    booking.setUser(user);
    booking.setEvent(event);
    booking.setCurrency("LKR");
    booking.setExpiresAt(Instant.now().plus(holdTtl));
    booking.setIdempotencyKey(idempotencyKey);
    booking.setTotalAmount(
        inventory.stream().map(EventSeat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add));
    inventory.forEach(
        seat -> {
          seat.setStatus(EventSeat.Status.HELD);
          BookingItem item = new BookingItem();
          item.setBooking(booking);
          item.setEventSeat(seat);
          item.setUnitPrice(seat.getPrice());
          booking.getItems().add(item);
        });
    return booking;
  }

  private void savePayment(Booking booking, PaymentProvider.PaymentResult result) {
    Payment payment = new Payment();
    payment.setBooking(booking);
    payment.setPaymentReference(result.reference());
    payment.setAmount(booking.getTotalAmount());
    payment.setCurrency(booking.getCurrency());
    payment.setProvider("MOCK");
    payment.setStatus(result.successful() ? Payment.Status.SUCCESS : Payment.Status.FAILED);
    payments.save(payment);
  }

  private void issueTickets(Booking booking, List<EventSeat> inventory) {
    for (EventSeat seat : inventory) {
      Ticket ticket = new Ticket();
      ticket.setTicketNumber("TKT-" + UUID.randomUUID());
      ticket.setBooking(booking);
      ticket.setEventSeat(seat);
      byte[] token = new byte[32];
      random.nextBytes(token);
      ticket.setQrToken(Base64.getUrlEncoder().withoutPadding().encodeToString(token));
      ticket.setIssuedAt(Instant.now());
      tickets.save(ticket);
      outbox.record(
          TICKET_TOPIC,
          "TICKET_GENERATED",
          booking.getId(),
          Map.of(
              "bookingId", booking.getId(),
              "ticketId", ticket.getId(),
              "eventSeatId", seat.getId()));
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

  private ApiException paymentFailed() {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_FAILED", "Mock payment was declined.");
  }
}
