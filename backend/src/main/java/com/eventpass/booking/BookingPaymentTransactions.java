package com.eventpass.booking;

import com.eventpass.common.error.ApiException;
import com.eventpass.common.outbox.OutboxService;
import com.eventpass.event.Event;
import com.eventpass.event.EventRepository;
import com.eventpass.payment.Payment;
import com.eventpass.payment.PaymentProvider;
import com.eventpass.payment.PaymentRepository;
import com.eventpass.seat.EventSeat;
import com.eventpass.seat.EventSeatRepository;
import com.eventpass.seat.SeatLockService;
import com.eventpass.ticket.Ticket;
import com.eventpass.ticket.TicketRepository;
import com.eventpass.user.User;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BookingPaymentTransactions {
  private static final String BOOKING_TOPIC = "booking.events";
  private static final String PAYMENT_TOPIC = "payment.events";
  private static final String TICKET_TOPIC = "ticket.events";
  private static final String IDEMPOTENCY_OPERATION = "BOOKING_CREATE";
  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

  private final BookingRepository bookings;
  private final EventRepository events;
  private final EventSeatRepository seats;
  private final SeatLockService locks;
  private final PaymentRepository payments;
  private final TicketRepository tickets;
  private final OutboxService outbox;
  private final Duration holdTtl;
  private final SecureRandom random = new SecureRandom();

  BookingPaymentTransactions(
      BookingRepository bookings,
      EventRepository events,
      EventSeatRepository seats,
      SeatLockService locks,
      PaymentRepository payments,
      TicketRepository tickets,
      OutboxService outbox,
      @Value("${eventpass.booking.hold-ttl}") Duration holdTtl) {
    this.bookings = bookings;
    this.events = events;
    this.seats = seats;
    this.locks = locks;
    this.payments = payments;
    this.tickets = tickets;
    this.outbox = outbox;
    this.holdTtl = holdTtl;
  }

  @Transactional
  PreparedBooking prepare(
      BookingController.CreateBookingRequest request, String idempotencyKey, User user) {
    validateIdempotencyKey(idempotencyKey);
    String requestHash = requestHash(request);
    bookings.acquireIdempotencyLock(idempotencyLockId(user.getId(), idempotencyKey));
    Optional<Booking> previous =
        bookings.findByUserIdAndIdempotencyOperationAndIdempotencyKey(
            user.getId(), IDEMPOTENCY_OPERATION, idempotencyKey);
    if (previous.isPresent()) {
      Booking booking = previous.get();
      if (!booking.getIdempotencyRequestHash().equals(requestHash)) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "IDEMPOTENCY_PAYLOAD_MISMATCH",
            "Idempotency key was already used with a different booking request.");
      }
      if (booking.getStatus() == Booking.Status.FAILED) {
        throw paymentFailed();
      }
      return PreparedBooking.replay(response(booking));
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
      Booking booking = newBooking(event, user, idempotencyKey, requestHash, inventory);
      bookings.save(booking);
      Payment payment = new Payment();
      payment.setBooking(booking);
      payment.setAmount(booking.getTotalAmount());
      payment.setCurrency(booking.getCurrency());
      payment.setProvider("MOCK");
      payment.setStatus(Payment.Status.PENDING);
      payments.save(payment);
      outbox.record(
          BOOKING_TOPIC,
          "BOOKING_CREATED",
          booking.getId(),
          Map.of("bookingId", booking.getId(), "reference", booking.getBookingReference()));
      return PreparedBooking.created(
          booking.getId(), event.getId(), requestedSeats, lockOwner, response(booking));
    } catch (RuntimeException exception) {
      acquired.forEach(seatId -> locks.release(event.getId(), seatId, lockOwner));
      throw exception;
    }
  }

  @Transactional
  void markAttempted(UUID bookingId) {
    Payment payment = lockPayment(bookingId);
    if (payment.getStatus() != Payment.Status.PENDING) return;
    payment.setStatus(Payment.Status.PROCESSING);
    payment.setAttemptedAt(Instant.now());
  }

  @Transactional
  Completion complete(UUID bookingId, PaymentProvider.PaymentResult result) {
    Booking booking = lockBooking(bookingId);
    Payment payment = lockPayment(bookingId);
    if (payment.getStatus() != Payment.Status.PROCESSING) {
      return new Completion(response(booking), payment.getStatus() == Payment.Status.SUCCESS);
    }
    List<EventSeat> inventory = lockedInventory(booking);
    payment.setPaymentReference(result.reference());
    payment.setCompletedAt(Instant.now());
    payment.setReconciliationStatus(Payment.ReconciliationStatus.NOT_REQUIRED);
    if (!result.successful()) {
      payment.setStatus(Payment.Status.FAILED);
      payment.setFailureCode(result.failureCode());
      booking.setStatus(Booking.Status.FAILED);
      inventory.forEach(seat -> seat.setStatus(EventSeat.Status.AVAILABLE));
      outbox.record(
          PAYMENT_TOPIC,
          "PAYMENT_FAILED",
          booking.getId(),
          Map.of("bookingId", booking.getId(), "paymentReference", result.reference()));
      return new Completion(response(booking), false);
    }
    payment.setStatus(Payment.Status.SUCCESS);
    booking.setStatus(Booking.Status.CONFIRMED);
    inventory.forEach(seat -> seat.setStatus(EventSeat.Status.SOLD));
    outbox.record(
        PAYMENT_TOPIC,
        "PAYMENT_COMPLETED",
        booking.getId(),
        Map.of("bookingId", booking.getId(), "paymentReference", result.reference()));
    issueTickets(booking, inventory);
    return new Completion(response(booking), true);
  }

  @Transactional
  void markOutcomeUnknown(UUID bookingId, RuntimeException providerError) {
    Booking booking = lockBooking(bookingId);
    Payment payment = lockPayment(bookingId);
    if (payment.getStatus() != Payment.Status.PROCESSING) return;
    payment.setStatus(Payment.Status.UNKNOWN);
    payment.setReconciliationStatus(Payment.ReconciliationStatus.PENDING);
    payment.setLastError(safeError(providerError));
    outbox.record(
        PAYMENT_TOPIC,
        "PAYMENT_RECONCILIATION_REQUIRED",
        booking.getId(),
        Map.of("bookingId", booking.getId(), "paymentId", payment.getId()));
  }

  private String safeError(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) return "Payment provider outcome was unknown.";
    return message.substring(0, Math.min(message.length(), 500));
  }

  private Booking lockBooking(UUID bookingId) {
    return bookings.lockById(bookingId).orElseThrow();
  }

  private Payment lockPayment(UUID bookingId) {
    return payments.lockByBookingId(bookingId).orElseThrow();
  }

  private List<EventSeat> lockedInventory(Booking booking) {
    List<UUID> ids =
        booking.getItems().stream().map(i -> i.getEventSeat().getId()).sorted().toList();
    return seats.lockForBooking(booking.getEvent().getId(), ids);
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
      Event event, User user, String key, String requestHash, List<EventSeat> inventory) {
    Booking booking = new Booking();
    booking.setBookingReference(
        "EVP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    booking.setUser(user);
    booking.setEvent(event);
    booking.setCurrency("LKR");
    booking.setExpiresAt(Instant.now().plus(holdTtl));
    booking.setIdempotencyKey(key);
    booking.setIdempotencyOperation(IDEMPOTENCY_OPERATION);
    booking.setIdempotencyRequestHash(requestHash);
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

  private void validateIdempotencyKey(String key) {
    if (key == null || key.isBlank() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_IDEMPOTENCY_KEY",
          "Idempotency-Key must contain between 1 and 100 characters.");
    }
  }

  private String requestHash(BookingController.CreateBookingRequest request) {
    String canonical =
        IDEMPOTENCY_OPERATION
            + "|"
            + request.eventId()
            + "|"
            + request.eventSeatIds().stream().sorted().toList()
            + "|"
            + request.paymentToken();
    return HexFormat.of().formatHex(digest(canonical));
  }

  private long idempotencyLockId(UUID userId, String key) {
    return ByteBuffer.wrap(digest(userId + "|" + IDEMPOTENCY_OPERATION + "|" + key)).getLong();
  }

  private byte[] digest(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  record PreparedBooking(
      UUID bookingId,
      UUID eventId,
      List<UUID> seatIds,
      String lockOwner,
      boolean requiresCharge,
      BookingController.BookingResponse response) {
    static PreparedBooking replay(BookingController.BookingResponse response) {
      return new PreparedBooking(response.id(), null, List.of(), null, false, response);
    }

    static PreparedBooking created(
        UUID bookingId,
        UUID eventId,
        List<UUID> seatIds,
        String lockOwner,
        BookingController.BookingResponse response) {
      return new PreparedBooking(bookingId, eventId, seatIds, lockOwner, true, response);
    }
  }

  record Completion(BookingController.BookingResponse response, boolean successful) {}
}
