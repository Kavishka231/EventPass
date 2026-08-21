package com.eventpass.common.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {
  @Id private UUID id;

  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(nullable = false)
  private String topic;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.PENDING;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error", length = 500)
  private String lastError;

  public void markPublished(Instant now) {
    status = Status.PUBLISHED;
    publishedAt = now;
    nextAttemptAt = now;
    lastError = null;
  }

  public void recordFailure(
      String error, Instant now, int maximumAttempts, long initialBackoffSeconds, long capSeconds) {
    attempts++;
    lastError = error;
    if (attempts >= maximumAttempts) {
      status = Status.FAILED;
      nextAttemptAt = now;
      return;
    }
    long multiplier = 1L << Math.min(attempts - 1, 30);
    long delaySeconds = Math.min(capSeconds, Math.multiplyExact(initialBackoffSeconds, multiplier));
    status = Status.PENDING;
    nextAttemptAt = now.plusSeconds(delaySeconds);
  }

  public void recover(Instant now) {
    if (status != Status.FAILED) {
      throw new IllegalStateException("Only failed outbox events can be recovered.");
    }
    status = Status.PENDING;
    attempts = 0;
    nextAttemptAt = now;
    publishedAt = null;
    lastError = null;
  }

  public enum Status {
    PENDING,
    PUBLISHED,
    FAILED
  }
}
