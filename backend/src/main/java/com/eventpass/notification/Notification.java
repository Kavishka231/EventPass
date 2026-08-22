package com.eventpass.notification;

import com.eventpass.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {
  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User user;

  @Column(name = "source_event_id", nullable = false)
  private UUID sourceEventId;

  @Column(nullable = false, length = 100)
  private String type;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 1000)
  private String message;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "read_at")
  private Instant readAt;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(name = "delivery_status", nullable = false)
  private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

  @Column(name = "delivery_attempts", nullable = false)
  private int deliveryAttempts;

  @Column(name = "next_delivery_at", nullable = false)
  private Instant nextDeliveryAt;

  @Column(name = "last_delivery_attempt_at")
  private Instant lastDeliveryAttemptAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "delivery_error", length = 500)
  private String deliveryError;

  @Version
  @Column(name = "delivery_version", nullable = false)
  private long deliveryVersion;

  @PrePersist
  void initializeCreatedAt() {
    if (createdAt == null) createdAt = Instant.now();
    if (nextDeliveryAt == null) nextDeliveryAt = createdAt;
  }

  public void markRead(Instant now) {
    if (readAt == null) readAt = now;
  }

  public void beginDelivery(Instant now) {
    if (deliveryStatus != DeliveryStatus.PENDING || nextDeliveryAt.isAfter(now)) {
      throw new IllegalStateException("Notification is not ready for delivery.");
    }
    deliveryStatus = DeliveryStatus.PROCESSING;
    deliveryAttempts++;
    lastDeliveryAttemptAt = now;
    deliveryError = null;
  }

  public void markDelivered(Instant now) {
    requireProcessing();
    deliveryStatus = DeliveryStatus.DELIVERED;
    deliveredAt = now;
    nextDeliveryAt = now;
    deliveryError = null;
  }

  public void recordDeliveryFailure(
      String error,
      Instant now,
      int maximumAttempts,
      long initialBackoffSeconds,
      long maximumBackoffSeconds) {
    requireProcessing();
    deliveryError = safeError(error);
    if (deliveryAttempts >= maximumAttempts) {
      deliveryStatus = DeliveryStatus.FAILED;
      nextDeliveryAt = now;
      return;
    }
    long multiplier = 1L << Math.min(deliveryAttempts - 1, 30);
    long delay = Math.min(maximumBackoffSeconds, initialBackoffSeconds * multiplier);
    deliveryStatus = DeliveryStatus.PENDING;
    nextDeliveryAt = now.plusSeconds(delay);
  }

  public void retryFailedDelivery(Instant now) {
    if (deliveryStatus != DeliveryStatus.FAILED) {
      throw new IllegalStateException("Only failed notifications can be retried.");
    }
    deliveryStatus = DeliveryStatus.PENDING;
    deliveryAttempts = 0;
    nextDeliveryAt = now;
    lastDeliveryAttemptAt = null;
    deliveredAt = null;
    deliveryError = null;
  }

  private void requireProcessing() {
    if (deliveryStatus != DeliveryStatus.PROCESSING) {
      throw new IllegalStateException("Notification delivery is not processing.");
    }
  }

  private String safeError(String error) {
    if (error == null || error.isBlank()) return "Notification delivery failed.";
    return error.substring(0, Math.min(error.length(), 500));
  }

  public enum DeliveryStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED
  }
}
