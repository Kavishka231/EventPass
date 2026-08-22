package com.eventpass.notification;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  Page<Notification> findAllByUserId(UUID userId, Pageable pageable);

  java.util.Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

  long countByUserIdAndReadAtIsNull(UUID userId);
}
