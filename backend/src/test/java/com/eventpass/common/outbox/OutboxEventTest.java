package com.eventpass.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxEventTest {
  @Test
  void failuresUseCappedExponentialBackoffThenBecomeOperationallyFailed() {
    OutboxEvent event = new OutboxEvent();
    Instant firstFailure = Instant.parse("2026-01-01T00:00:00Z");

    event.recordFailure("first", firstFailure, 3, 2, 3);
    assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
    assertThat(event.getNextAttemptAt()).isEqualTo(firstFailure.plusSeconds(2));

    Instant secondFailure = firstFailure.plusSeconds(2);
    event.recordFailure("second", secondFailure, 3, 2, 3);
    assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
    assertThat(event.getNextAttemptAt()).isEqualTo(secondFailure.plusSeconds(3));

    event.recordFailure("third", secondFailure.plusSeconds(3), 3, 2, 3);
    assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
    assertThat(event.getAttempts()).isEqualTo(3);
    assertThat(event.getLastError()).isEqualTo("third");
  }

  @Test
  void recoveryResetsFailedEventForImmediateRetry() {
    OutboxEvent event = new OutboxEvent();
    Instant failureTime = Instant.parse("2026-01-01T00:00:00Z");
    event.recordFailure("failed", failureTime, 1, 2, 900);
    Instant recoveryTime = failureTime.plusSeconds(30);

    event.recover(recoveryTime);

    assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
    assertThat(event.getAttempts()).isZero();
    assertThat(event.getNextAttemptAt()).isEqualTo(recoveryTime);
    assertThat(event.getLastError()).isNull();
  }
}
