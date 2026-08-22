package com.eventpass.notification;

import com.eventpass.user.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasRole('CUSTOMER')")
public class NotificationController {
  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  public record NotificationResponse(
      UUID id, String type, String title, String message, Instant createdAt, Instant readAt) {}

  public record UnreadCountResponse(long unreadCount) {}

  @GetMapping
  Page<NotificationResponse> list(
      @AuthenticationPrincipal User user,
      @PageableDefault(
              size = 20,
              sort = "createdAt",
              direction = org.springframework.data.domain.Sort.Direction.DESC)
          Pageable pageable) {
    return service.list(user, pageable);
  }

  @PatchMapping("/{id}/read")
  ResponseEntity<Void> markRead(@PathVariable UUID id, @AuthenticationPrincipal User user) {
    service.markRead(id, user);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/unread-count")
  UnreadCountResponse unreadCount(@AuthenticationPrincipal User user) {
    return new UnreadCountResponse(service.unreadCount(user));
  }
}
