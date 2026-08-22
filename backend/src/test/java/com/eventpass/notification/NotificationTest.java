package com.eventpass.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationTest {
  @Test
  void markingReadIsIdempotent() {
    Notification notification = new Notification();
    Instant firstRead = Instant.parse("2026-01-01T00:00:00Z");

    notification.markRead(firstRead);
    notification.markRead(firstRead.plusSeconds(30));

    assertThat(notification.getReadAt()).isEqualTo(firstRead);
  }

  @Test
  void persistenceLifecycleInitializesCreationTimeOnce() {
    Notification notification = new Notification();

    notification.initializeCreatedAt();
    Instant createdAt = notification.getCreatedAt();
    notification.initializeCreatedAt();

    assertThat(createdAt).isNotNull();
    assertThat(notification.getCreatedAt()).isEqualTo(createdAt);
    assertThat(notification.getNextDeliveryAt()).isEqualTo(createdAt);
  }

  @Test
  void successfulDeliveryUsesGuardedStateTransitions() {
    Notification notification = pendingNotification();
    Instant attempt = Instant.parse("2026-01-01T00:00:01Z");

    notification.beginDelivery(attempt);
    notification.markDelivered(attempt.plusSeconds(1));

    assertThat(notification.getDeliveryStatus()).isEqualTo(Notification.DeliveryStatus.DELIVERED);
    assertThat(notification.getDeliveryAttempts()).isEqualTo(1);
    assertThat(notification.getLastDeliveryAttemptAt()).isEqualTo(attempt);
    assertThat(notification.getDeliveredAt()).isEqualTo(attempt.plusSeconds(1));
  }

  @Test
  void failuresBackOffThenBecomeOperationallyFailedAndCanBeRetried() {
    Notification notification = pendingNotification();
    Instant firstAttempt = Instant.parse("2026-01-01T00:00:01Z");

    notification.beginDelivery(firstAttempt);
    notification.recordDeliveryFailure("provider unavailable", firstAttempt, 2, 5, 60);

    assertThat(notification.getDeliveryStatus()).isEqualTo(Notification.DeliveryStatus.PENDING);
    assertThat(notification.getNextDeliveryAt()).isEqualTo(firstAttempt.plusSeconds(5));
    assertThatThrownBy(() -> notification.beginDelivery(firstAttempt.plusSeconds(4)))
        .isInstanceOf(IllegalStateException.class);

    Instant secondAttempt = firstAttempt.plusSeconds(5);
    notification.beginDelivery(secondAttempt);
    notification.recordDeliveryFailure("still unavailable", secondAttempt, 2, 5, 60);
    assertThat(notification.getDeliveryStatus()).isEqualTo(Notification.DeliveryStatus.FAILED);
    assertThat(notification.getDeliveryError()).isEqualTo("still unavailable");

    Instant recovery = secondAttempt.plusSeconds(30);
    notification.retryFailedDelivery(recovery);
    assertThat(notification.getDeliveryStatus()).isEqualTo(Notification.DeliveryStatus.PENDING);
    assertThat(notification.getDeliveryAttempts()).isZero();
    assertThat(notification.getNextDeliveryAt()).isEqualTo(recovery);
    assertThat(notification.getDeliveryError()).isNull();
  }

  private Notification pendingNotification() {
    Notification notification = new Notification();
    notification.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    notification.initializeCreatedAt();
    return notification;
  }
}
