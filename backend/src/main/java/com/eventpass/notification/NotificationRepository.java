package com.eventpass.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  List<Notification> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

  long countByUserIdAndReadAtIsNull(UUID userId);
}
