package com.eventpass.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eventpass.common.error.ApiException;
import com.eventpass.event.*;
import com.eventpass.payment.Payment;
import com.eventpass.payment.PaymentRepository;
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
  @Autowired SeatRepository seats;
  @Autowired EventSeatRepository inventory;
  @Autowired BookingRepository bookings;
  @Autowired PaymentRepository payments;
  @Autowired TicketRepository tickets;

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
