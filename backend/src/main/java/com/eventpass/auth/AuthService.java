package com.eventpass.auth;

import com.eventpass.common.error.ApiException;
import com.eventpass.user.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserRepository users;
  private final RefreshTokenRepository tokens;
  private final PasswordEncoder passwords;
  private final JwtService jwt;
  private final Duration refreshTtl;
  private final SecureRandom random = new SecureRandom();

  public AuthService(
      UserRepository users,
      RefreshTokenRepository tokens,
      PasswordEncoder passwords,
      JwtService jwt,
      @Value("${eventpass.jwt.refresh-ttl}") Duration refreshTtl) {
    this.users = users;
    this.tokens = tokens;
    this.passwords = passwords;
    this.jwt = jwt;
    this.refreshTtl = refreshTtl;
  }

  @Transactional
  public AuthController.AuthResponse register(AuthController.RegisterRequest r, String deviceInfo) {
    if (users.existsByEmailIgnoreCase(r.email()))
      throw new ApiException(
          HttpStatus.CONFLICT, "EMAIL_EXISTS", "An account already exists for this email.");
    User u = new User();
    u.setEmail(r.email().trim().toLowerCase());
    u.setPasswordHash(passwords.encode(r.password()));
    u.setFirstName(r.firstName().trim());
    u.setLastName(r.lastName().trim());
    return issue(users.save(u), UUID.randomUUID(), null, deviceInfo);
  }

  @Transactional
  public AuthController.AuthResponse login(AuthController.LoginRequest r, String deviceInfo) {
    User u =
        users
            .findByEmailIgnoreCase(r.email())
            .filter(x -> x.getStatus() == User.Status.ACTIVE)
            .filter(x -> passwords.matches(r.password(), x.getPasswordHash()))
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS",
                        "Email or password is incorrect."));
    return issue(u, UUID.randomUUID(), null, deviceInfo);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public AuthController.AuthResponse refresh(String raw, String deviceInfo) {
    Instant now = Instant.now();
    RefreshToken old =
        tokens
            .lockByTokenHash(hash(raw))
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_REFRESH_TOKEN",
                        "Refresh token is invalid or expired."));
    if (old.isRevoked()) {
      tokens.revokeFamily(old.getFamilyId(), now, "REUSE_DETECTED");
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "REFRESH_TOKEN_REUSE_DETECTED",
          "Refresh token reuse was detected; the session has been revoked.");
    }
    if (!old.getExpiresAt().isAfter(now) || old.getUser().getStatus() != User.Status.ACTIVE) {
      old.revoke(now, old.getExpiresAt().isAfter(now) ? "ACCOUNT_INACTIVE" : "EXPIRED");
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired.");
    }
    old.setLastUsedAt(now);
    old.revoke(now, "ROTATED");
    return issue(old.getUser(), old.getFamilyId(), old, deviceInfo);
  }

  @Transactional
  public void logout(String raw) {
    tokens
        .lockByTokenHash(hash(raw))
        .ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), Instant.now(), "LOGOUT"));
  }

  private AuthController.AuthResponse issue(
      User u, UUID familyId, RefreshToken parentToken, String deviceInfo) {
    Instant now = Instant.now();
    byte[] b = new byte[48];
    random.nextBytes(b);
    String raw = HexFormat.of().formatHex(b);
    RefreshToken t = new RefreshToken();
    t.setUser(u);
    t.setTokenHash(hash(raw));
    t.setExpiresAt(now.plus(refreshTtl));
    t.setFamilyId(familyId);
    t.setParentToken(parentToken);
    t.setCreatedAt(now);
    t.setDeviceInfo(safeDeviceInfo(deviceInfo));
    tokens.save(t);
    return new AuthController.AuthResponse(jwt.create(u), raw, "Bearer", u.getRole().name());
  }

  private String safeDeviceInfo(String deviceInfo) {
    if (deviceInfo == null || deviceInfo.isBlank()) return "unknown";
    String normalized = deviceInfo.replaceAll("[\\r\\n\\t]", " ").trim();
    return normalized.substring(0, Math.min(normalized.length(), 255));
  }

  private String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
