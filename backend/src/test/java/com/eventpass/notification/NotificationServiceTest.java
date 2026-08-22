package com.eventpass.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventpass.common.error.ApiException;
import com.eventpass.user.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class NotificationServiceTest {
  private final NotificationRepository repository = mock(NotificationRepository.class);
  private final NotificationService service = new NotificationService(repository);
  private final User customer = customer();

  @Test
  void listsOnlyTheAuthenticatedCustomersNotifications() {
    Notification notification = notification(customer);
    PageRequest page = PageRequest.of(0, 20);
    when(repository.findAllByUserId(customer.getId(), page))
        .thenReturn(new PageImpl<>(java.util.List.of(notification), page, 1));

    var result = service.list(customer, page);

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst().id()).isEqualTo(notification.getId());
    verify(repository).findAllByUserId(customer.getId(), page);
  }

  @Test
  void marksAnOwnedNotificationReadIdempotently() {
    Notification notification = notification(customer);
    when(repository.findByIdAndUserId(notification.getId(), customer.getId()))
        .thenReturn(Optional.of(notification));

    service.markRead(notification.getId(), customer);
    Instant firstRead = notification.getReadAt();
    service.markRead(notification.getId(), customer);

    assertThat(firstRead).isNotNull();
    assertThat(notification.getReadAt()).isEqualTo(firstRead);
  }

  @Test
  void hidesNotificationsOwnedByAnotherCustomer() {
    UUID notificationId = UUID.randomUUID();
    when(repository.findByIdAndUserId(notificationId, customer.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.markRead(notificationId, customer))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).code())
        .isEqualTo("NOTIFICATION_NOT_FOUND");
  }

  @Test
  void returnsTheCustomersUnreadCount() {
    when(repository.countByUserIdAndReadAtIsNull(customer.getId())).thenReturn(3L);

    assertThat(service.unreadCount(customer)).isEqualTo(3L);
  }

  private User customer() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setRole(User.Role.CUSTOMER);
    return user;
  }

  private Notification notification(User user) {
    Notification notification = new Notification();
    notification.setId(UUID.randomUUID());
    notification.setUser(user);
    notification.setType("BOOKING_CONFIRMED");
    notification.setTitle("Booking confirmed");
    notification.setMessage("Your booking is confirmed.");
    notification.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    return notification;
  }
}
