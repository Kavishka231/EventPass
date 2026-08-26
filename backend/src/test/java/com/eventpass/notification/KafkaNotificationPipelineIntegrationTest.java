package com.eventpass.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventpass.booking.BookingController;
import com.eventpass.booking.BookingService;
import com.eventpass.common.kafka.KafkaTopics;
import com.eventpass.common.outbox.OutboxEvent;
import com.eventpass.common.outbox.OutboxEventRepository;
import com.eventpass.common.outbox.OutboxPublisher;
import com.eventpass.common.outbox.OutboxService;
import com.eventpass.event.Event;
import com.eventpass.event.EventRepository;
import com.eventpass.seat.EventSeat;
import com.eventpass.seat.EventSeatRepository;
import com.eventpass.seat.Seat;
import com.eventpass.seat.SeatRepository;
import com.eventpass.user.User;
import com.eventpass.user.UserRepository;
import com.eventpass.venue.Venue;
import com.eventpass.venue.VenueRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class KafkaNotificationPipelineIntegrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @Container
  static KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    registry.add("spring.kafka.consumer.group-id", () -> "notification-pipeline-test");
    registry.add("spring.kafka.listener.auto-startup", () -> true);
    registry.add("eventpass.kafka.provisioning-enabled", () -> true);
    registry.add("eventpass.kafka.topics.partitions", () -> 1);
    registry.add("eventpass.outbox.enabled", () -> true);
    registry.add("eventpass.outbox.publish-delay", () -> "PT1H");
  }

  @Autowired BookingService bookingService;
  @Autowired UserRepository users;
  @Autowired VenueRepository venues;
  @Autowired SeatRepository seats;
  @Autowired EventRepository events;
  @Autowired EventSeatRepository inventory;
  @Autowired OutboxEventRepository outboxEvents;
  @Autowired OutboxPublisher outboxPublisher;
  @Autowired OutboxService outboxService;
  @Autowired NotificationRepository notifications;
  @Autowired KafkaTemplate<String, String> kafkaTemplate;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void publishesBusinessOutboxEventsIdempotentlyAndRetriesFailuresToTheDeadLetterTopic()
      throws Exception {
    BookingController.BookingResponse booking = completeBooking();
    List<OutboxEvent> businessEvents =
        outboxEvents.findAll().stream()
            .filter(event -> event.getAggregateId().equals(booking.id()))
            .toList();
    assertThat(businessEvents)
        .isNotEmpty()
        .allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING));

    outboxPublisher.publish();

    await(
        Duration.ofSeconds(20),
        () ->
            notifications.count() == businessEvents.size()
                && businessEvents.stream()
                    .map(event -> outboxEvents.findById(event.getId()).orElseThrow())
                    .allMatch(event -> event.getStatus() == OutboxEvent.Status.PUBLISHED));
    assertThat(notifications.count()).isEqualTo(businessEvents.size());

    OutboxEvent duplicate = businessEvents.getFirst();
    kafkaTemplate
        .send(duplicate.getTopic(), duplicate.getAggregateId().toString(), duplicate.getPayload())
        .get();
    Thread.sleep(2_000);
    assertThat(notifications.count()).isEqualTo(businessEvents.size());

    UUID missingBookingId = UUID.randomUUID();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            ignored ->
                outboxService.record(
                    KafkaTopics.BOOKING_EVENTS,
                    "BOOKING_CREATED",
                    missingBookingId,
                    Map.of("bookingId", missingBookingId)));
    OutboxEvent poison =
        outboxEvents.findAll().stream()
            .filter(event -> event.getAggregateId().equals(missingBookingId))
            .findFirst()
            .orElseThrow();
    UUID poisonEventId = poison.getId();

    Instant publishedAt = Instant.now();
    try (KafkaConsumer<String, String> deadLetters = deadLetterConsumer()) {
      deadLetters.subscribe(List.of(KafkaTopics.deadLetter(KafkaTopics.BOOKING_EVENTS)));
      outboxPublisher.publish();
      ConsumerRecord<String, String> deadLetter = pollOne(deadLetters, Duration.ofSeconds(35));

      assertThat(deadLetter.value()).isEqualTo(poison.getPayload());
      assertThat(deadLetter.headers().lastHeader("kafka_dlt-original-topic")).isNotNull();
      assertThat(Duration.between(publishedAt, Instant.now()))
          .isGreaterThan(Duration.ofSeconds(10));
    }
    assertThat(notifications.count()).isEqualTo(businessEvents.size());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                Long.class,
                poisonEventId))
        .isZero();
  }

  private BookingController.BookingResponse completeBooking() {
    String suffix = UUID.randomUUID().toString();
    User organizer = user("organizer-" + suffix + "@example.com", User.Role.ORGANIZER);
    User customer = user("customer-" + suffix + "@example.com", User.Role.CUSTOMER);
    Venue venue = new Venue();
    venue.setName("Kafka Arena " + suffix);
    venue.setAddress("1 Pipeline Road");
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
    event.setName("Kafka Pipeline Event " + suffix);
    event.setDescription("Real broker integration test");
    event.setCategory("TEST");
    event.setStartDateTime(Instant.now().plusSeconds(86_400));
    event.setEndDateTime(Instant.now().plusSeconds(90_000));
    event.setVenue(venue);
    event.setOrganizer(organizer);
    event.setStatus(Event.Status.PUBLISHED);
    events.save(event);
    EventSeat eventSeat = new EventSeat();
    eventSeat.setEvent(event);
    eventSeat.setSeat(seat);
    eventSeat.setPrice(new BigDecimal("100.00"));
    inventory.save(eventSeat);
    return bookingService.create(
        new BookingController.CreateBookingRequest(
            event.getId(), List.of(eventSeat.getId()), "tok_success"),
        "kafka-pipeline-" + suffix,
        customer);
  }

  private User user(String email, User.Role role) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash("not-used-by-this-test");
    user.setFirstName("Kafka");
    user.setLastName("Tester");
    user.setRole(role);
    user.setStatus(User.Status.ACTIVE);
    return users.save(user);
  }

  private KafkaConsumer<String, String> deadLetterConsumer() {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-verifier-" + UUID.randomUUID());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new KafkaConsumer<>(properties);
  }

  private ConsumerRecord<String, String> pollOne(
      KafkaConsumer<String, String> consumer, Duration timeout) {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      var records = consumer.poll(Duration.ofMillis(500));
      if (!records.isEmpty()) return records.iterator().next();
    }
    throw new AssertionError("No dead-letter record was received before the timeout.");
  }

  private void await(Duration timeout, BooleanSupplier condition) throws InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      if (condition.getAsBoolean()) return;
      Thread.sleep(100);
    }
    throw new AssertionError("The Kafka pipeline did not reach the expected state before timeout.");
  }
}
