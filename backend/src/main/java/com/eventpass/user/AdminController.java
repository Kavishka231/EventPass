package com.eventpass.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
  private final AdminService service;

  public AdminController(AdminService service) {
    this.service = service;
  }

  public record UpdateUserRequest(@NotNull User.Role role, @NotNull User.Status status) {}

  public record UserResponse(
      UUID id,
      String email,
      String firstName,
      String lastName,
      User.Role role,
      User.Status status,
      Instant createdAt) {}

  public record Statistics(
      long users, long events, long venues, long bookings, long confirmedBookings) {}

  @GetMapping("/users")
  public Page<UserResponse> users(
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return service.users(pageable);
  }

  @PutMapping("/users/{id}")
  public UserResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUserRequest request,
      @AuthenticationPrincipal User actor) {
    return service.update(id, request, actor);
  }

  @GetMapping("/statistics")
  public Statistics statistics() {
    return service.statistics();
  }
}
