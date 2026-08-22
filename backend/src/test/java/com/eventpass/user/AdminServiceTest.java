package com.eventpass.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventpass.booking.BookingRepository;
import com.eventpass.common.error.ApiException;
import com.eventpass.event.EventRepository;
import com.eventpass.venue.VenueRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminServiceTest {
  private final UserRepository users = mock(UserRepository.class);
  private final AdminService service =
      new AdminService(
          users,
          mock(EventRepository.class),
          mock(VenueRepository.class),
          mock(BookingRepository.class));

  @Test
  void administratorCannotDemoteOrSuspendSelf() {
    User actor = admin();

    assertCode(
        () ->
            service.update(
                actor.getId(),
                new AdminController.UpdateUserRequest(User.Role.CUSTOMER, User.Status.ACTIVE),
                actor),
        "ADMIN_SELF_LIFECYCLE_CHANGE");
    assertCode(
        () ->
            service.update(
                actor.getId(),
                new AdminController.UpdateUserRequest(User.Role.ADMIN, User.Status.SUSPENDED),
                actor),
        "ADMIN_SELF_LIFECYCLE_CHANGE");
  }

  @Test
  void lastActiveAdministratorCannotBeRemoved() {
    User actor = admin();
    User target = admin();
    when(users.findById(target.getId())).thenReturn(Optional.of(target));
    when(users.countByRoleAndStatus(User.Role.ADMIN, User.Status.ACTIVE)).thenReturn(1L);

    assertCode(
        () ->
            service.update(
                target.getId(),
                new AdminController.UpdateUserRequest(User.Role.ADMIN, User.Status.DISABLED),
                actor),
        "LAST_ACTIVE_ADMIN_REQUIRED");
    verify(users).acquireAdministratorLifecycleLock();
    assertThat(target.getStatus()).isEqualTo(User.Status.ACTIVE);
  }

  @Test
  void anotherAdministratorCanBeChangedWhenAnActiveAdministratorRemains() {
    User actor = admin();
    User target = admin();
    when(users.findById(target.getId())).thenReturn(Optional.of(target));
    when(users.countByRoleAndStatus(User.Role.ADMIN, User.Status.ACTIVE)).thenReturn(2L);

    service.update(
        target.getId(),
        new AdminController.UpdateUserRequest(User.Role.ORGANIZER, User.Status.ACTIVE),
        actor);

    verify(users).acquireAdministratorLifecycleLock();
    assertThat(target.getRole()).isEqualTo(User.Role.ORGANIZER);
  }

  private void assertCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
    assertThatThrownBy(action)
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).code())
        .isEqualTo(code);
  }

  private User admin() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setRole(User.Role.ADMIN);
    user.setStatus(User.Status.ACTIVE);
    return user;
  }
}
