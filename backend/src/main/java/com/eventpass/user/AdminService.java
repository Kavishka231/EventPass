package com.eventpass.user;

import com.eventpass.booking.BookingRepository;
import com.eventpass.common.error.ApiException;
import com.eventpass.event.EventRepository;
import com.eventpass.venue.VenueRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
  private final UserRepository users;
  private final EventRepository events;
  private final VenueRepository venues;
  private final BookingRepository bookings;

  public AdminService(
      UserRepository users,
      EventRepository events,
      VenueRepository venues,
      BookingRepository bookings) {
    this.users = users;
    this.events = events;
    this.venues = venues;
    this.bookings = bookings;
  }

  @Transactional(readOnly = true)
  public Page<AdminController.UserResponse> users(Pageable pageable) {
    return users.findAll(pageable).map(this::response);
  }

  @Transactional
  public AdminController.UserResponse update(
      UUID id, AdminController.UpdateUserRequest request, User actor) {
    if (id.equals(actor.getId())
        && (request.role() != User.Role.ADMIN || request.status() != User.Status.ACTIVE)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "ADMIN_SELF_LIFECYCLE_CHANGE",
          "Administrators cannot demote or suspend their own account.");
    }
    User user =
        users
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found."));
    boolean removesActiveAdministrator =
        user.getRole() == User.Role.ADMIN
            && user.getStatus() == User.Status.ACTIVE
            && (request.role() != User.Role.ADMIN || request.status() != User.Status.ACTIVE);
    if (removesActiveAdministrator) {
      users.acquireAdministratorLifecycleLock();
      if (users.countByRoleAndStatus(User.Role.ADMIN, User.Status.ACTIVE) <= 1) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "LAST_ACTIVE_ADMIN_REQUIRED",
            "The last active administrator cannot be demoted or suspended.");
      }
    }
    user.setRole(request.role());
    user.setStatus(request.status());
    return response(user);
  }

  @Transactional(readOnly = true)
  public AdminController.Statistics statistics() {
    return new AdminController.Statistics(
        users.count(),
        events.count(),
        venues.count(),
        bookings.count(),
        bookings.countByStatus(com.eventpass.booking.Booking.Status.CONFIRMED));
  }

  private AdminController.UserResponse response(User user) {
    return new AdminController.UserResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getRole(),
        user.getStatus(),
        user.getCreatedAt());
  }
}
