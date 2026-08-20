package com.eventpass.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class BaseEntity {
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @PrePersist
  void createTimestamps() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void updateTimestamp() {
    updatedAt = Instant.now();
  }
}
