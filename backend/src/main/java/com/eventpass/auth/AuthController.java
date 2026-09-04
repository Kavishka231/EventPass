package com.eventpass.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;
  private final Duration refreshTtl;
  private final boolean secureCookie;
  private final String sameSite;
  private static final String REFRESH_COOKIE = "eventpass_refresh";

  public AuthController(
      AuthService service,
      @Value("${eventpass.jwt.refresh-ttl}") Duration refreshTtl,
      @Value("${eventpass.security.cookies.secure:false}") boolean secureCookie,
      @Value("${eventpass.security.cookies.same-site:Lax}") String sameSite) {
    this.service = service;
    this.refreshTtl = refreshTtl;
    this.secureCookie = secureCookie;
    this.sameSite = validateSameSite(sameSite);
    if (this.sameSite.equals("None") && !secureCookie) {
      throw new IllegalArgumentException("SameSite=None cookies must be Secure.");
    }
  }

  public record RegisterRequest(
      @NotBlank @Email String email,
      @NotBlank @Size(min = 10, max = 72) String password,
      @NotBlank @Size(max = 100) String firstName,
      @NotBlank @Size(max = 100) String lastName) {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  public record AuthResponse(
      String accessToken,
      @com.fasterxml.jackson.annotation.JsonIgnore String refreshToken,
      String tokenType,
      String role) {}

  @GetMapping("/csrf")
  ResponseEntity<Void> csrf(CsrfToken token) {
    token.getToken();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/register")
  ResponseEntity<AuthResponse> register(
      @Valid @RequestBody RegisterRequest r,
      @RequestHeader(value = "User-Agent", required = false) String deviceInfo) {
    AuthResponse response = service.register(r, deviceInfo);
    return authenticated(response, HttpStatus.CREATED);
  }

  @PostMapping("/login")
  ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest r,
      @RequestHeader(value = "User-Agent", required = false) String deviceInfo) {
    return authenticated(service.login(r, deviceInfo), HttpStatus.OK);
  }

  @PostMapping("/refresh")
  ResponseEntity<AuthResponse> refresh(
      @CookieValue(name = REFRESH_COOKIE) String refreshToken,
      @RequestHeader(value = "User-Agent", required = false) String deviceInfo) {
    return authenticated(service.refresh(refreshToken, deviceInfo), HttpStatus.OK);
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
    if (refreshToken != null && !refreshToken.isBlank()) service.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
        .build();
  }

  private ResponseEntity<AuthResponse> authenticated(AuthResponse response, HttpStatus status) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, refreshCookie(response.refreshToken()).toString())
        .body(response);
  }

  private ResponseCookie refreshCookie(String token) {
    return ResponseCookie.from(REFRESH_COOKIE, token)
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite(sameSite)
        .path("/api/v1/auth")
        .maxAge(refreshTtl)
        .build();
  }

  private ResponseCookie clearedCookie() {
    return ResponseCookie.from(REFRESH_COOKIE, "")
        .httpOnly(true)
        .secure(secureCookie)
        .sameSite(sameSite)
        .path("/api/v1/auth")
        .maxAge(Duration.ZERO)
        .build();
  }

  private static String validateSameSite(String value) {
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "strict" -> "Strict";
      case "lax" -> "Lax";
      case "none" -> "None";
      default ->
          throw new IllegalArgumentException("Cookie SameSite must be Strict, Lax, or None.");
    };
  }
}
