package com.eventpass.notification;

import static org.assertj.core.api.Assertions.assertThat;

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
  }
}
