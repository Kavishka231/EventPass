package com.eventpass.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventpass.common.error.ApiException;
import com.eventpass.user.User;
import com.eventpass.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
  private final UserRepository users = mock(UserRepository.class);
  private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
  private final PasswordEncoder passwords = mock(PasswordEncoder.class);
  private final JwtService jwt = mock(JwtService.class);
  private final AuthService service =
      new AuthService(users, tokens, passwords, jwt, Duration.ofDays(30));
  private final User customer = customer();

  @BeforeEach
  void assignTokenIds() {
    when(tokens.save(any(RefreshToken.class)))
        .thenAnswer(
            invocation -> {
              RefreshToken token = invocation.getArgument(0);
              if (token.getId() == null) token.setId(UUID.randomUUID());
              return token;
            });
    when(jwt.create(customer)).thenReturn("access-token");
  }

  @Test
  void loginCreatesADeviceBoundTokenFamily() {
    when(users.findByEmailIgnoreCase(customer.getEmail())).thenReturn(Optional.of(customer));
    when(passwords.matches("correct-password", customer.getPasswordHash())).thenReturn(true);

    service.login(
        new AuthController.LoginRequest(customer.getEmail(), "correct-password"),
        "Browser\r\nInjected");

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(tokens).save(captor.capture());
    RefreshToken issued = captor.getValue();
    assertThat(issued.getFamilyId()).isNotNull();
    assertThat(issued.getParentToken()).isNull();
    assertThat(issued.getDeviceInfo()).isEqualTo("Browser  Injected");
    assertThat(issued.getCreatedAt()).isNotNull();
  }

  @Test
  void rotationPreservesFamilyAndLinksTheReplacement() {
    RefreshToken old = activeToken();
    when(tokens.lockByTokenHash(any())).thenReturn(Optional.of(old));

    service.refresh("old-raw-token", "New browser");

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(tokens).save(captor.capture());
    assertThat(old.isRevoked()).isTrue();
    assertThat(old.getRevocationReason()).isEqualTo("ROTATED");
    assertThat(old.getLastUsedAt()).isNotNull();
    assertThat(captor.getValue().getFamilyId()).isEqualTo(old.getFamilyId());
    assertThat(captor.getValue().getParentToken()).isSameAs(old);
  }

  @Test
  void reuseRevokesTheEntireTokenFamily() {
    RefreshToken reused = activeToken();
    reused.revoke(Instant.now(), "ROTATED");
    when(tokens.lockByTokenHash(any())).thenReturn(Optional.of(reused));

    assertThatThrownBy(() -> service.refresh("reused-token", "Browser"))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).code())
        .isEqualTo("REFRESH_TOKEN_REUSE_DETECTED");
    verify(tokens)
        .revokeFamily(
            org.mockito.ArgumentMatchers.eq(reused.getFamilyId()),
            any(Instant.class),
            org.mockito.ArgumentMatchers.eq("REUSE_DETECTED"));
  }

  @Test
  void logoutRevokesTheWholeSessionFamily() {
    RefreshToken token = activeToken();
    when(tokens.lockByTokenHash(any())).thenReturn(Optional.of(token));

    service.logout("raw-token");

    verify(tokens)
        .revokeFamily(
            org.mockito.ArgumentMatchers.eq(token.getFamilyId()),
            any(Instant.class),
            org.mockito.ArgumentMatchers.eq("LOGOUT"));
  }

  @Test
  void expiredRefreshTokenIsRejectedAndRevoked() {
    RefreshToken expired = activeToken();
    expired.setExpiresAt(Instant.now().minusSeconds(1));
    when(tokens.lockByTokenHash(any())).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.refresh("expired-token", "Browser"))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).code())
        .isEqualTo("INVALID_REFRESH_TOKEN");
    assertThat(expired.isRevoked()).isTrue();
    assertThat(expired.getRevocationReason()).isEqualTo("EXPIRED");
  }

  @Test
  void unknownRefreshTokenIsRejected() {
    when(tokens.lockByTokenHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.refresh("unknown-token", "Browser"))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).code())
        .isEqualTo("INVALID_REFRESH_TOKEN");
  }

  private RefreshToken activeToken() {
    RefreshToken token = new RefreshToken();
    token.setId(UUID.randomUUID());
    token.setUser(customer);
    token.setFamilyId(UUID.randomUUID());
    token.setExpiresAt(Instant.now().plusSeconds(3600));
    token.setCreatedAt(Instant.now());
    token.setDeviceInfo("Browser");
    return token;
  }

  private User customer() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail("customer@example.com");
    user.setPasswordHash("encoded-password");
    user.setRole(User.Role.CUSTOMER);
    user.setStatus(User.Status.ACTIVE);
    return user;
  }
}
