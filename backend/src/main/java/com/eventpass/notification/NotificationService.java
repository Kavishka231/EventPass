package com.eventpass.notification;

import com.eventpass.common.error.ApiException;
import com.eventpass.user.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
  private final NotificationRepository notifications;

  public NotificationService(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  @Transactional(readOnly = true)
  public Page<NotificationController.NotificationResponse> list(User user, Pageable pageable) {
    return notifications.findAllByUserId(user.getId(), pageable).map(this::response);
  }

  @Transactional
  public void markRead(UUID notificationId, User user) {
    Notification notification =
        notifications
            .findByIdAndUserId(notificationId, user.getId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "NOTIFICATION_NOT_FOUND",
                        "Notification was not found."));
    notification.markRead(Instant.now());
  }

  @Transactional(readOnly = true)
  public long unreadCount(User user) {
    return notifications.countByUserIdAndReadAtIsNull(user.getId());
  }

  private NotificationController.NotificationResponse response(Notification notification) {
    return new NotificationController.NotificationResponse(
        notification.getId(),
        notification.getType(),
        notification.getTitle(),
        notification.getMessage(),
        notification.getCreatedAt(),
        notification.getReadAt());
  }
}
