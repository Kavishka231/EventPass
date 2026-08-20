package com.eventpass.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventpass.event.*;
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
    assertThat(bookings.count()).isEqualTo(1);
    assertThat(payments.count()).isEqualTo(1);
    assertThat(tickets.count()).isEqualTo(1);
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
