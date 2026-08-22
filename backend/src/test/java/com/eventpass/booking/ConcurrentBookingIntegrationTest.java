package com.eventpass.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventpass.auth.AuthController;
import com.eventpass.auth.AuthService;
import com.eventpass.auth.JwtService;
import com.eventpass.common.error.ApiException;
import com.eventpass.common.outbox.OutboxEvent;
import com.eventpass.common.outbox.OutboxEventRepository;
import com.eventpass.common.outbox.OutboxRecoveryService;
import com.eventpass.event.*;
import com.eventpass.notification.NotificationEventConsumer;
import com.eventpass.notification.NotificationRepository;
import com.eventpass.notification.ProcessedEventService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
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
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired OutboxRecoveryService outboxRecovery;
  @Autowired AdminService adminService;
  @Autowired ProcessedEventService processedEvents;
  @Autowired NotificationEventConsumer notificationConsumer;
  @Autowired NotificationRepository notifications;
  @Autowired MockMvc mockMvc;
  @Autowired JwtService jwtService;
  @Autowired AuthService authService;

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
  void eventCancellationOrchestratesRefundsTicketInvalidationAndInventoryRelease() {
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

    assertThat(events.findById(fixture.event().getId()).orElseThrow().getStatus())
        .isEqualTo(Event.Status.PUBLISHED);
    assertThat(bookings.findAllById(List.of(first.id(), second.id())))
        .extracting(Booking::getStatus)
        .containsOnly(Booking.Status.CONFIRMED);
    assertThat(inventory.findAllById(List.of(fixture.eventSeat().getId(), secondEventSeat.getId())))
        .extracting(EventSeat::getStatus)
        .containsOnly(EventSeat.Status.SOLD);

    eventService.cancel(fixture.event().getId(), fixture.event().getOrganizer());

    assertThat(events.findById(fixture.event().getId()).orElseThrow().getStatus())
        .isEqualTo(Event.Status.CANCELLED);
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
    assertThat(outboxEvents.findAll())
        .filteredOn(envelope -> envelope.getAggregateId().equals(fixture.event().getId()))
        .extracting(OutboxEvent::getEventType)
        .contains("EVENT_CANCELLED", "EVENT_TICKETS_CANCELLED");
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

  @Test
  void competingOutboxPublishersDeliverTheClaimedRowOnlyOnce() throws Exception {
    outboxEvents.deleteAll();
    OutboxEvent pending = new OutboxEvent();
    pending.setId(UUID.randomUUID());
    pending.setAggregateType("BOOKING");
    pending.setAggregateId(UUID.randomUUID());
    pending.setEventType("CLAIM_RACE_TEST");
    pending.setTopic("booking.events");
    pending.setPayload("{}");
    pending.setOccurredAt(Instant.now());
    pending.setNextAttemptAt(Instant.now());
    outboxEvents.saveAndFlush(pending);
    CountDownLatch firstClaimed = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    Queue<UUID> firstWorkerClaims = new ConcurrentLinkedQueue<>();
    Queue<UUID> secondWorkerClaims = new ConcurrentLinkedQueue<>();
    AtomicInteger deliveries = new AtomicInteger();

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<?> first =
          executor.submit(
              () -> {
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          outboxEvents.claimPendingBatch(10, 1).stream()
                              .map(OutboxEvent::getId)
                              .forEach(
                                  id -> {
                                    firstWorkerClaims.add(id);
                                    deliveries.incrementAndGet();
                                  });
                          firstClaimed.countDown();
                          try {
                            releaseFirst.await();
                          } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                          }
                        });
                return null;
              });
      Future<?> second =
          executor.submit(
              () -> {
                firstClaimed.await();
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status ->
                            outboxEvents.claimPendingBatch(10, 1).stream()
                                .map(OutboxEvent::getId)
                                .forEach(
                                    id -> {
                                      secondWorkerClaims.add(id);
                                      deliveries.incrementAndGet();
                                    }));
                releaseFirst.countDown();
                return null;
              });
      first.get();
      second.get();
    }

    assertThat(firstWorkerClaims).containsExactly(pending.getId());
    assertThat(secondWorkerClaims).isEmpty();
    assertThat(deliveries).hasValue(1);
  }

  @Test
  void duplicateEventDeliveryIsClaimedOnlyOnceByAConsumer() {
    UUID eventId = UUID.randomUUID();

    boolean firstDelivery = processedEvents.claim("outbox-reliability-test", eventId);
    boolean duplicateDelivery = processedEvents.claim("outbox-reliability-test", eventId);

    assertThat(firstDelivery).isTrue();
    assertThat(duplicateDelivery).isFalse();
  }

  @Test
  void duplicateKafkaEventCreatesOneCustomerNotification() throws Exception {
    BookingFixture fixture = fixture();
    BookingController.BookingResponse booking =
        service.create(fixture.request(), "notification-" + UUID.randomUUID(), fixture.customer());
    OutboxEvent paymentCompleted =
        outboxEvents.findAll().stream()
            .filter(event -> event.getAggregateId().equals(booking.id()))
            .filter(event -> event.getEventType().equals("PAYMENT_COMPLETED"))
            .findFirst()
            .orElseThrow();

    notificationConsumer.consume(paymentCompleted.getPayload(), paymentCompleted.getTopic());
    notificationConsumer.consume(paymentCompleted.getPayload(), paymentCompleted.getTopic());

    var stored = notifications.findAllByUserId(fixture.customer().getId(), PageRequest.of(0, 20));
    assertThat(stored.getTotalElements()).isEqualTo(1);
    assertThat(stored.getContent().getFirst().getSourceEventId())
        .isEqualTo(paymentCompleted.getId());
    assertThat(stored.getContent().getFirst().getType()).isEqualTo("PAYMENT_COMPLETED");
  }

  @Test
  void concurrentAdministratorChangesPreserveOneActiveAdministrator() throws Exception {
    User first = user("admin-first-" + UUID.randomUUID() + "@example.com", User.Role.ADMIN);
    User second = user("admin-second-" + UUID.randomUUID() + "@example.com", User.Role.ADMIN);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger successfulChanges = new AtomicInteger();
    Queue<String> rejectedCodes = new ConcurrentLinkedQueue<>();

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<?> firstChange =
          executor.submit(
              () ->
                  changeAdministrator(
                      first, second, ready, start, successfulChanges, rejectedCodes));
      Future<?> secondChange =
          executor.submit(
              () ->
                  changeAdministrator(
                      second, first, ready, start, successfulChanges, rejectedCodes));
      ready.await();
      start.countDown();
      firstChange.get();
      secondChange.get();
    }

    assertThat(successfulChanges).hasValue(1);
    assertThat(rejectedCodes).containsExactly("LAST_ACTIVE_ADMIN_REQUIRED");
    assertThat(users.countByRoleAndStatus(User.Role.ADMIN, User.Status.ACTIVE)).isEqualTo(1);
  }

  @Test
  void customerCannotAccessAdministratorEndpoint() throws Exception {
    User customer =
        user("security-customer-" + UUID.randomUUID() + "@example.com", User.Role.CUSTOMER);

    mockMvc
        .perform(get("/api/v1/admin/statistics").header("Authorization", bearer(customer)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void organizerCannotCancelAnotherOrganizersEvent() throws Exception {
    BookingFixture owned = fixture();
    User otherOrganizer =
        user("other-organizer-" + UUID.randomUUID() + "@example.com", User.Role.ORGANIZER);

    mockMvc
        .perform(
            delete("/api/v1/events/{id}", owned.event().getId())
                .header("Authorization", bearer(otherOrganizer)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EVENT_FORBIDDEN"));
  }

  @Test
  void administratorCanAccessProtectedOperations() throws Exception {
    User administrator =
        user("authorized-admin-" + UUID.randomUUID() + "@example.com", User.Role.ADMIN);

    try {
      mockMvc
          .perform(get("/api/v1/admin/statistics").header("Authorization", bearer(administrator)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.users").isNumber());
    } finally {
      administrator.setStatus(User.Status.DISABLED);
      users.saveAndFlush(administrator);
    }
  }

  @Test
  void suspendedUserCannotAuthenticateWithPreviouslyIssuedJwt() throws Exception {
    User customer = user("suspended-" + UUID.randomUUID() + "@example.com", User.Role.CUSTOMER);
    String token = bearer(customer);
    customer.setStatus(User.Status.SUSPENDED);
    users.saveAndFlush(customer);

    mockMvc
        .perform(get("/api/v1/bookings").header("Authorization", token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void invalidAndExpiredJwtAreRejected() throws Exception {
    mockMvc
        .perform(get("/api/v1/bookings").header("Authorization", "Bearer invalid.jwt.token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    User customer = user("expired-" + UUID.randomUUID() + "@example.com", User.Role.CUSTOMER);
    JwtService expiredTokens =
        new JwtService(
            "integration-test-secret-at-least-32-characters-long",
            java.time.Duration.ofSeconds(-1),
            "eventpass",
            "eventpass-api",
            "eventpass-primary");
    mockMvc
        .perform(
            get("/api/v1/bookings")
                .header("Authorization", "Bearer " + expiredTokens.create(customer)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void reusedRefreshTokenRevokesItsSessionFamily() throws Exception {
    AuthController.AuthResponse authentication =
        authService.register(
            new AuthController.RegisterRequest(
                "refresh-reuse-" + UUID.randomUUID() + "@example.com",
                "strong-test-password",
                "Refresh",
                "Reuse"),
            "Integration test browser");
    String body = "{\"refreshToken\":\"" + authentication.refreshToken() + "\"}";

    mockMvc
        .perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSE_DETECTED"));
  }

  @Test
  void validationAndNotFoundErrorsUseTheStandardContract() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").isString())
        .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
        .andExpect(jsonPath("$.requestId").isString());

    mockMvc
        .perform(get("/api/v1/events/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
        .andExpect(jsonPath("$.status").value(404));
  }

  private String bearer(User user) {
    return "Bearer " + jwtService.create(user);
  }

  private void changeAdministrator(
      User actor,
      User target,
      CountDownLatch ready,
      CountDownLatch start,
      AtomicInteger successfulChanges,
      Queue<String> rejectedCodes) {
    ready.countDown();
    try {
      start.await();
      adminService.update(
          target.getId(),
          new AdminController.UpdateUserRequest(User.Role.ORGANIZER, User.Status.ACTIVE),
          actor);
      successfulChanges.incrementAndGet();
    } catch (ApiException exception) {
      rejectedCodes.add(exception.code());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  @Test
  void failedOutboxRowCanBeRecoveredForImmediateDelivery() {
    outboxEvents.deleteAll();
    OutboxEvent failed = new OutboxEvent();
    failed.setId(UUID.randomUUID());
    failed.setAggregateType("BOOKING");
    failed.setAggregateId(UUID.randomUUID());
    failed.setEventType("RECOVERY_TEST");
    failed.setTopic("booking.events");
    failed.setPayload("{}");
    failed.setOccurredAt(Instant.now());
    failed.setNextAttemptAt(Instant.now());
    failed.setAttempts(10);
    failed.setStatus(OutboxEvent.Status.FAILED);
    failed.setLastError("Kafka unavailable");
    outboxEvents.saveAndFlush(failed);

    assertThat(claimPendingOutboxIds()).isEmpty();
    outboxRecovery.retry(failed.getId());

    OutboxEvent recovered = outboxEvents.findById(failed.getId()).orElseThrow();
    assertThat(recovered.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
    assertThat(recovered.getAttempts()).isZero();
    assertThat(recovered.getLastError()).isNull();
    assertThat(claimPendingOutboxIds()).containsExactly(failed.getId());
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

  private List<UUID> claimPendingOutboxIds() {
    return new TransactionTemplate(transactionManager)
        .execute(
            status ->
                outboxEvents.claimPendingBatch(10, 100).stream().map(OutboxEvent::getId).toList());
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
