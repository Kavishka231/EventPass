package com.eventpass.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eventpass.common.error.ApiException;
import com.eventpass.common.outbox.OutboxEvent;
import com.eventpass.common.outbox.OutboxEventRepository;
import com.eventpass.event.*;
import com.eventpass.payment.Payment;
import com.eventpass.payment.PaymentProvider;
import com.eventpass.payment.PaymentRepository;
import com.eventpass.payment.Refund;
import com.eventpass.payment.RefundRepository;
import com.eventpass.seat.*;
import com.eventpass.ticket.TicketRepository;
import com.eventpass.user.*;
import com.eventpass.venue.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentBookingIntegrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired BookingService service;
  @Autowired UserRepository users;
  @Autowired VenueRepository venues;
  @Autowired EventRepository events;
  @Autowired EventService eventService;
  @Autowired SeatRepository seats;
  @Autowired EventSeatRepository inventory;
  @Autowired InventoryService inventoryService;
  @Autowired BookingRepository bookings;
  @Autowired PaymentRepository payments;
  @Autowired PaymentProvider paymentProvider;
  @Autowired RefundRepository refunds;
  @Autowired TicketRepository tickets;
  @Autowired OutboxEventRepository outboxEvents;

  @Test
  void exactlyOneOfTwentyCustomersCanBuyTheSameSeat() throws Exception {
    long bookingsBefore = bookings.count();
    long paymentsBefore = payments.count();
    long ticketsBefore = tickets.count();
    User organizer = user("organizer@example.com", User.Role.ORGANIZER);
    Venue v = new Venue();
    v.setName("Test Arena");
    v.setAddress("1 Test Road");
    v.setCity("Colombo");
    v.setCapacity(1);
    venues.save(v);
    Seat seat = new Seat();
    seat.setVenue(v);
    seat.setSection("A");
    seat.setRowNumber("1");
    seat.setSeatNumber("10");
    seat.setSeatType(Seat.Type.REGULAR);
    seats.save(seat);
    Event event = new Event();
    event.setName("Concurrent Event");
    event.setDescription("test");
    event.setCategory("TEST");
    event.setStartDateTime(Instant.now().plusSeconds(86400));
    event.setEndDateTime(Instant.now().plusSeconds(90000));
    event.setVenue(v);
    event.setOrganizer(organizer);
    event.setStatus(Event.Status.PUBLISHED);
    events.save(event);
    EventSeat es = new EventSeat();
    es.setEvent(event);
    es.setSeat(seat);
    es.setPrice(new BigDecimal("100.00"));
    inventory.save(es);
    List<User> customers = new ArrayList<>();
    for (int i = 0; i < 20; i++)
      customers.add(user("customer" + i + "@example.com", User.Role.CUSTOMER));
    var ready = new CountDownLatch(20);
    var start = new CountDownLatch(1);
    var successes = new AtomicInteger();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < 20; i++) {
        int n = i;
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  try {
                    service.create(
                        new BookingController.CreateBookingRequest(
                            event.getId(), List.of(es.getId()), "tok_success"),
                        "attempt-" + n,
                        customers.get(n));
                    successes.incrementAndGet();
                  } catch (RuntimeException ignored) {
                  }
                  return null;
                }));
      }
      ready.await();
      start.countDown();
      for (var future : futures) future.get();
    }
    assertThat(successes).hasValue(1);
    assertThat(inventory.findById(es.getId()).orElseThrow().getStatus())
        .isEqualTo(EventSeat.Status.SOLD);
    assertThat(bookings.count()).isEqualTo(bookingsBefore + 1);
    assertThat(payments.count()).isEqualTo(paymentsBefore + 1);
    assertThat(tickets.count()).isEqualTo(ticketsBefore + 1);
  }

  @Test
  void sameIdempotencyKeyAndRequestReturnsTheOriginalBooking() {
    BookingFixture fixture = fixture();
    long bookingsBefore = bookings.count();
    BookingController.CreateBookingRequest request = fixture.request();

    BookingController.BookingResponse first =
        service.create(request, "same-request-key", fixture.customer());
    BookingController.BookingResponse replay =
        service.create(request, "same-request-key", fixture.customer());

    assertThat(replay.id()).isEqualTo(first.id());
    assertThat(replay.reference()).isEqualTo(first.reference());
    assertThat(bookings.count()).isEqualTo(bookingsBefore + 1);
  }

  @Test
  void sameIdempotencyKeyWithDifferentPayloadIsRejected() {
    BookingFixture firstFixture = fixture();
    BookingFixture differentFixture = fixture();
    String key = "payload-mismatch-key";
    service.create(firstFixture.request(), key, firstFixture.customer());

    assertThatThrownBy(
            () -> service.create(differentFixture.request(), key, firstFixture.customer()))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo("IDEMPOTENCY_PAYLOAD_MISMATCH"));
  }

  @Test
  void simultaneousSameKeyRequestsCreateOneBookingAndReturnOneResult() throws Exception {
    BookingFixture fixture = fixture();
    long bookingsBefore = bookings.count();
    long paymentsBefore = payments.count();
    String key = "simultaneous-key-" + UUID.randomUUID();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    Queue<UUID> bookingIds = new ConcurrentLinkedQueue<>();
    Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> futures = new ArrayList<>();
      for (int attempt = 0; attempt < 2; attempt++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  try {
                    bookingIds.add(service.create(fixture.request(), key, fixture.customer()).id());
                  } catch (Throwable throwable) {
                    failures.add(throwable);
                  }
                  return null;
                }));
      }
      ready.await();
      start.countDown();
      for (Future<?> future : futures) future.get();
    }

    assertThat(failures).isEmpty();
    assertThat(bookingIds).hasSize(2).containsOnly(bookingIds.peek());
    assertThat(bookings.count()).isEqualTo(bookingsBefore + 1);
    assertThat(payments.count()).isEqualTo(paymentsBefore + 1);
  }

  @Test
  void oversizedIdempotencyKeyIsRejectedBeforeBookingCreation() {
    BookingFixture fixture = fixture();
    long bookingsBefore = bookings.count();

    assertThatThrownBy(() -> service.create(fixture.request(), "x".repeat(101), fixture.customer()))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo("INVALID_IDEMPOTENCY_KEY"));
    assertThat(bookings.count()).isEqualTo(bookingsBefore);
  }

  @Test
  void successfulPaymentPersistsAttemptProviderReferenceAndCompletion() {
    BookingFixture fixture = fixture();
    BookingController.BookingResponse response =
        service.create(
            fixture.request("tok_success"),
            "payment-success-" + UUID.randomUUID(),
            fixture.customer());

    Payment payment = payments.findByBookingId(response.id()).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(Payment.Status.SUCCESS);
    assertThat(payment.getAttemptedAt()).isNotNull();
    assertThat(payment.getCompletedAt()).isNotNull();
    assertThat(payment.getPaymentReference()).startsWith("mock_");
    assertThat(payment.getReconciliationStatus())
        .isEqualTo(Payment.ReconciliationStatus.NOT_REQUIRED);
  }

  @Test
  void declinedPaymentIsDurablyRecordedAndReleasesInventory() {
    BookingFixture fixture = fixture();
    String key = "payment-decline-" + UUID.randomUUID();

    assertThatThrownBy(() -> service.create(fixture.request("tok_fail"), key, fixture.customer()))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo("PAYMENT_FAILED"));

    Booking booking =
        bookings
            .findByUserIdAndIdempotencyOperationAndIdempotencyKey(
                fixture.customer().getId(), "BOOKING_CREATE", key)
            .orElseThrow();
    Payment payment = payments.findByBookingId(booking.getId()).orElseThrow();
    assertThat(booking.getStatus()).isEqualTo(Booking.Status.FAILED);
    assertThat(payment.getStatus()).isEqualTo(Payment.Status.FAILED);
    assertThat(payment.getAttemptedAt()).isNotNull();
    assertThat(payment.getCompletedAt()).isNotNull();
    assertThat(payment.getFailureCode()).isEqualTo("MOCK_PAYMENT_DECLINED");
    assertThat(payment.getPaymentReference()).startsWith("mock_");
    assertThat(fixture.eventSeat().getId())
        .satisfies(
            id ->
                assertThat(inventory.findById(id).orElseThrow().getStatus())
                    .isEqualTo(EventSeat.Status.AVAILABLE));
  }

  @Test
  void unknownProviderOutcomeIsFlaggedForReconciliationAndKeepsSeatHeld() {
    BookingFixture fixture = fixture();
    String key = "payment-unknown-" + UUID.randomUUID();

    assertThatThrownBy(
            () -> service.create(fixture.request("tok_unknown"), key, fixture.customer()))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo("PAYMENT_OUTCOME_UNKNOWN"));

    Booking booking =
        bookings
            .findByUserIdAndIdempotencyOperationAndIdempotencyKey(
                fixture.customer().getId(), "BOOKING_CREATE", key)
            .orElseThrow();
    Payment payment = payments.findByBookingId(booking.getId()).orElseThrow();
    assertThat(booking.getStatus()).isEqualTo(Booking.Status.PENDING);
    assertThat(payment.getStatus()).isEqualTo(Payment.Status.UNKNOWN);
    assertThat(payment.getAttemptedAt()).isNotNull();
    assertThat(payment.getCompletedAt()).isNull();
    assertThat(payment.getPaymentReference()).isNull();
    assertThat(payment.getReconciliationStatus()).isEqualTo(Payment.ReconciliationStatus.PENDING);
    assertThat(payment.getLastError()).isNotBlank();
    assertThat(inventory.findById(fixture.eventSeat().getId()).orElseThrow().getStatus())
        .isEqualTo(EventSeat.Status.HELD);
    assertThat(payments.findAllByReconciliationStatus(Payment.ReconciliationStatus.PENDING))
        .extracting(Payment::getId)
        .contains(payment.getId());
  }

  @Test
  void cancellationPersistsCompletedRefundBeforeReleasingTheSeat() {
    long refundsBefore = refunds.count();
    BookingFixture fixture = cancellableFixture();
    BookingController.BookingResponse created =
        service.create(
            fixture.request(), "refundable-booking-" + UUID.randomUUID(), fixture.customer());

    service.cancel(created.id(), fixture.customer());

    Booking booking = bookings.findById(created.id()).orElseThrow();
    Payment payment = payments.findByBookingId(created.id()).orElseThrow();
    Refund refund = refunds.findByPaymentId(payment.getId()).orElseThrow();
    assertThat(refunds.count()).isEqualTo(refundsBefore + 1);
    assertThat(refund.getPayment().getId()).isEqualTo(payment.getId());
    assertThat(refund.getBooking().getId()).isEqualTo(booking.getId());
    assertThat(refund.getAmount()).isEqualByComparingTo(payment.getAmount());
    assertThat(refund.getCurrency()).isEqualTo(payment.getCurrency());
    assertThat(refund.getStatus()).isEqualTo(Refund.Status.SUCCESS);
    assertThat(refund.getProviderReference()).startsWith("mock_refund_");
    assertThat(refund.getIdempotencyKey()).isEqualTo("booking-refund:" + booking.getId());
    assertThat(refund.getAttemptedAt()).isNotNull();
    assertThat(refund.getCompletedAt()).isNotNull();
    assertThat(refund.getFailureCode()).isNull();
    assertThat(refund.getLastError()).isNull();
    assertThat(booking.getStatus()).isEqualTo(Booking.Status.CANCELLED);
    assertThat(payment.getStatus()).isEqualTo(Payment.Status.REFUNDED);
    assertThat(inventory.findById(fixture.eventSeat().getId()).orElseThrow().getStatus())
        .isEqualTo(EventSeat.Status.AVAILABLE);
    assertThat(tickets.findAllByBookingId(booking.getId()))
        .allMatch(ticket -> ticket.getStatus() == com.eventpass.ticket.Ticket.Status.CANCELLED);
  }

  @Test
  void simultaneousCancellationsCreateExactlyOneRefund() throws Exception {
    long refundsBefore = refunds.count();
    BookingFixture fixture = cancellableFixture();
    BookingController.BookingResponse created =
        service.create(
            fixture.request(), "concurrent-refund-" + UUID.randomUUID(), fixture.customer());
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> calls = new ArrayList<>();
      for (int request = 0; request < 2; request++) {
        calls.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  try {
                    service.cancel(created.id(), fixture.customer());
                  } catch (Throwable throwable) {
                    failures.add(throwable);
                  }
                  return null;
                }));
      }
      ready.await();
      start.countDown();
      for (Future<?> call : calls) call.get();
    }

    Payment payment = payments.findByBookingId(created.id()).orElseThrow();
    assertThat(refunds.count()).isEqualTo(refundsBefore + 1);
    assertThat(refunds.findByPaymentId(payment.getId()))
        .get()
        .extracting(Refund::getStatus)
        .isEqualTo(Refund.Status.SUCCESS);
    assertThat(failures)
        .allMatch(
            throwable ->
                throwable instanceof ApiException exception
                    && exception.code().equals("REFUND_PENDING"));
  }

  @Test
  void repeatedCancellationReplaysOneDurableRefund() {
    long refundsBefore = refunds.count();
    BookingFixture fixture = cancellableFixture();
    BookingController.BookingResponse created =
        service.create(
            fixture.request(), "duplicate-refund-" + UUID.randomUUID(), fixture.customer());

    service.cancel(created.id(), fixture.customer());
    service.cancel(created.id(), fixture.customer());

    Payment payment = payments.findByBookingId(created.id()).orElseThrow();
    assertThat(refunds.count()).isEqualTo(refundsBefore + 1);
    assertThat(refunds.findByPaymentId(payment.getId()))
        .get()
        .satisfies(
            refund -> {
              assertThat(refund.getStatus()).isEqualTo(Refund.Status.SUCCESS);
              assertThat(refund.getProviderReference()).startsWith("mock_refund_");
            });
  }

  @Test
  void concurrentProviderCallsWithOneKeyReplayOneFinancialResult() throws Exception {
    String key = "provider-race-" + UUID.randomUUID();
    CountDownLatch ready = new CountDownLatch(3);
    CountDownLatch start = new CountDownLatch(1);
    Queue<PaymentProvider.PaymentResult> results = new ConcurrentLinkedQueue<>();

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> calls = new ArrayList<>();
      for (int request = 0; request < 3; request++) {
        calls.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  results.add(paymentProvider.charge("tok_success", BigDecimal.TEN, "LKR", key));
                  return null;
                }));
      }
      ready.await();
      start.countDown();
      for (Future<?> call : calls) call.get();
    }

    assertThat(results)
        .hasSize(3)
        .extracting(PaymentProvider.PaymentResult::reference)
        .containsOnly(results.peek().reference());
  }

  @Test
  void cancelledEventRejectsBookingsInventoryChangesAndRepublication() {
    BookingFixture fixture = fixture();
    long bookingsBefore = bookings.count();
    long paymentsBefore = payments.count();

    eventService.cancel(fixture.event().getId(), fixture.event().getOrganizer());

    assertThat(events.findById(fixture.event().getId()).orElseThrow().getStatus())
        .isEqualTo(Event.Status.CANCELLED);
    assertThatThrownBy(
            () ->
                service.create(
                    fixture.request(),
                    "cancelled-event-booking-" + UUID.randomUUID(),
                    fixture.customer()))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo("EVENT_NOT_BOOKABLE"));
    assertThatThrownBy(
            () ->
                inventoryService.configureEvent(
                    fixture.event().getId(),
                    List.of(
                        new InventoryController.EventSeatRequest(
                            fixture.eventSeat().getSeat().getId(),
                            new BigDecimal("125.00"),
                            false)),
                    fixture.event().getOrganizer()))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo("INVENTORY_LOCKED"));
    Event event = fixture.event();
    EventController.EventRequest republication =
        new EventController.EventRequest(
            event.getName(),
            event.getDescription(),
            event.getCategory(),
            event.getStartDateTime(),
            event.getEndDateTime(),
            event.getVenue().getId(),
            Event.Status.PUBLISHED);
    assertThatThrownBy(
            () -> eventService.update(event.getId(), republication, event.getOrganizer()))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo("INVALID_EVENT_TRANSITION"));
    assertThat(bookings.count()).isEqualTo(bookingsBefore);
    assertThat(payments.count()).isEqualTo(paymentsBefore);
    assertThat(inventory.findById(fixture.eventSeat().getId()).orElseThrow().getStatus())
        .isEqualTo(EventSeat.Status.AVAILABLE);
    assertThat(outboxEvents.findAll())
        .filteredOn(eventEnvelope -> eventEnvelope.getEventType().equals("EVENT_CANCELLED"))
        .extracting(OutboxEvent::getAggregateId)
        .contains(event.getId());
  }

  @Test
  void eventCancellationRefundsConfirmedBookingsAndReleasesInventory() {
    long refundsBefore = refunds.count();
    BookingFixture fixture = fixture();
    fixture.event().getVenue().setCapacity(2);
    venues.save(fixture.event().getVenue());
    Seat secondSeat = new Seat();
    secondSeat.setVenue(fixture.event().getVenue());
    secondSeat.setSection("A");
    secondSeat.setRowNumber("1");
    secondSeat.setSeatNumber("2");
    secondSeat.setSeatType(Seat.Type.REGULAR);
    seats.save(secondSeat);
    EventSeat secondEventSeat = new EventSeat();
    secondEventSeat.setEvent(fixture.event());
    secondEventSeat.setSeat(secondSeat);
    secondEventSeat.setPrice(new BigDecimal("150.00"));
    inventory.save(secondEventSeat);
    User secondCustomer =
        user("event-refund-" + UUID.randomUUID() + "@example.com", User.Role.CUSTOMER);
    BookingController.BookingResponse first =
        service.create(
            fixture.request(), "event-cancel-first-" + UUID.randomUUID(), fixture.customer());
    BookingController.BookingResponse second =
        service.create(
            new BookingController.CreateBookingRequest(
                fixture.event().getId(), List.of(secondEventSeat.getId()), "tok_success"),
            "event-cancel-second-" + UUID.randomUUID(),
            secondCustomer);

    eventService.cancel(fixture.event().getId(), fixture.event().getOrganizer());

    assertThat(bookings.findAllById(List.of(first.id(), second.id())))
        .extracting(Booking::getStatus)
        .containsOnly(Booking.Status.CANCELLED);
    List<Payment> eventPayments =
        List.of(
            payments.findByBookingId(first.id()).orElseThrow(),
            payments.findByBookingId(second.id()).orElseThrow());
    assertThat(eventPayments).extracting(Payment::getStatus).containsOnly(Payment.Status.REFUNDED);
    assertThat(refunds.count()).isEqualTo(refundsBefore + 2);
    assertThat(eventPayments)
        .map(payment -> refunds.findByPaymentId(payment.getId()).orElseThrow())
        .extracting(Refund::getStatus)
        .containsOnly(Refund.Status.SUCCESS);
    assertThat(inventory.findAllById(List.of(fixture.eventSeat().getId(), secondEventSeat.getId())))
        .extracting(EventSeat::getStatus)
        .containsOnly(EventSeat.Status.AVAILABLE);
    assertThat(tickets.findAllByBookingId(first.id()))
        .allMatch(ticket -> ticket.getStatus() == com.eventpass.ticket.Ticket.Status.CANCELLED);
    assertThat(tickets.findAllByBookingId(second.id()))
        .allMatch(ticket -> ticket.getStatus() == com.eventpass.ticket.Ticket.Status.CANCELLED);
  }

  @Test
  void cancelledEventInvalidatesTicketsEvenWhenRefundFails() {
    BookingFixture fixture = fixture();
    BookingController.BookingResponse created =
        service.create(
            fixture.request(), "event-ticket-failure-" + UUID.randomUUID(), fixture.customer());
    Payment payment = payments.findByBookingId(created.id()).orElseThrow();
    payment.setPaymentReference("mock_refund_fail");
    payments.save(payment);

    eventService.cancel(fixture.event().getId(), fixture.event().getOrganizer());

    Refund refund = refunds.findByPaymentId(payment.getId()).orElseThrow();
    assertThat(events.findById(fixture.event().getId()).orElseThrow().getStatus())
        .isEqualTo(Event.Status.CANCELLED);
    assertThat(refund.getStatus()).isEqualTo(Refund.Status.FAILED);
    assertThat(refund.getFailureCode()).isEqualTo("MOCK_REFUND_FAILED");
    assertThat(bookings.findById(created.id()).orElseThrow().getStatus())
        .isEqualTo(Booking.Status.CONFIRMED);
    assertThat(tickets.findAllByBookingId(created.id()))
        .isNotEmpty()
        .allMatch(ticket -> ticket.getStatus() == com.eventpass.ticket.Ticket.Status.CANCELLED);
    assertThat(inventory.findById(fixture.eventSeat().getId()).orElseThrow().getStatus())
        .isEqualTo(EventSeat.Status.SOLD);
    assertThat(outboxEvents.findAll())
        .filteredOn(envelope -> envelope.getEventType().equals("EVENT_TICKETS_CANCELLED"))
        .extracting(OutboxEvent::getAggregateId)
        .contains(fixture.event().getId());
  }

  private BookingFixture fixture() {
    String suffix = UUID.randomUUID().toString();
    User organizer = user("organizer-" + suffix + "@example.com", User.Role.ORGANIZER);
    User customer = user("customer-" + suffix + "@example.com", User.Role.CUSTOMER);
    Venue venue = new Venue();
    venue.setName("Arena " + suffix);
    venue.setAddress("1 Test Road");
    venue.setCity("Colombo");
    venue.setCapacity(1);
    venues.save(venue);
    Seat seat = new Seat();
    seat.setVenue(venue);
    seat.setSection("A");
    seat.setRowNumber("1");
    seat.setSeatNumber("1");
    seat.setSeatType(Seat.Type.REGULAR);
    seats.save(seat);
    Event event = new Event();
    event.setName("Event " + suffix);
    event.setDescription("Idempotency integration test");
    event.setCategory("TEST");
    event.setStartDateTime(Instant.now().plusSeconds(86400));
    event.setEndDateTime(Instant.now().plusSeconds(90000));
    event.setVenue(venue);
    event.setOrganizer(organizer);
    event.setStatus(Event.Status.PUBLISHED);
    events.save(event);
    EventSeat eventSeat = new EventSeat();
    eventSeat.setEvent(event);
    eventSeat.setSeat(seat);
    eventSeat.setPrice(new BigDecimal("100.00"));
    inventory.save(eventSeat);
    return new BookingFixture(event, eventSeat, customer);
  }

  private BookingFixture cancellableFixture() {
    BookingFixture fixture = fixture();
    fixture.event().setStartDateTime(Instant.now().plusSeconds(172800));
    fixture.event().setEndDateTime(Instant.now().plusSeconds(176400));
    events.save(fixture.event());
    return fixture;
  }

  private record BookingFixture(Event event, EventSeat eventSeat, User customer) {
    BookingController.CreateBookingRequest request() {
      return request("tok_success");
    }

    BookingController.CreateBookingRequest request(String paymentToken) {
      return new BookingController.CreateBookingRequest(
          event.getId(), List.of(eventSeat.getId()), paymentToken);
    }
  }

  private User user(String email, User.Role role) {
    User u = new User();
    u.setEmail(email);
    u.setPasswordHash("not-used-in-service-test");
    u.setFirstName("Test");
    u.setLastName("User");
    u.setRole(role);
    return users.save(u);
  }
}
