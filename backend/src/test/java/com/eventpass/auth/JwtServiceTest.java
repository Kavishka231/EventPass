package com.eventpass.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eventpass.user.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  private static final String SECRET = "integration-test-secret-at-least-32-characters-long";
  private static final String ISSUER = "eventpass";
  private static final String AUDIENCE = "eventpass-api";
  private static final String KEY_ID = "eventpass-primary";
  private final JwtService service =
      new JwtService(SECRET, Duration.ofMinutes(15), ISSUER, AUDIENCE, KEY_ID);

  @Test
  void createsAndValidatesStrictlyScopedAccessToken() {
    User user = customer();

    var claims = service.parse(service.create(user));

    assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
    assertThat(claims.getIssuer()).isEqualTo(ISSUER);
    assertThat(claims.getAudience()).containsExactly(AUDIENCE);
    assertThat(claims.get("token_type", String.class)).isEqualTo("access");
    assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
  }

  @Test
  void rejectsWrongIssuerAudienceAndTokenType() {
    assertThatThrownBy(() -> service.parse(token("other-issuer", AUDIENCE, "access", KEY_ID)))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> service.parse(token(ISSUER, "other-api", "access", KEY_ID)))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> service.parse(token(ISSUER, AUDIENCE, "refresh", KEY_ID)))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsWrongTokenHeaderTypeAndSigningKeyId() {
    assertThatThrownBy(() -> service.parse(token(ISSUER, AUDIENCE, "access", "retired-key")))
        .isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> service.parse(tokenWithHeaderType("refresh+jwt")))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsInvalidSignatureAndShortSigningSecret() {
    SecretKey otherKey =
        Keys.hmacShaKeyFor(
            "another-signing-secret-that-is-at-least-thirty-two-bytes"
                .getBytes(StandardCharsets.UTF_8));
    String foreignToken = baseToken().signWith(otherKey, Jwts.SIG.HS256).compact();

    assertThatThrownBy(() -> service.parse(foreignToken)).isInstanceOf(JwtException.class);
    assertThatThrownBy(
            () -> new JwtService("too-short", Duration.ofMinutes(15), ISSUER, AUDIENCE, KEY_ID))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 32 bytes");
  }

  private String token(String issuer, String audience, String tokenType, String keyId) {
    return baseToken(issuer, audience, tokenType, keyId)
        .signWith(signingKey(), Jwts.SIG.HS256)
        .compact();
  }

  private String tokenWithHeaderType(String type) {
    return baseToken()
        .header()
        .type(type)
        .keyId(KEY_ID)
        .and()
        .signWith(signingKey(), Jwts.SIG.HS256)
        .compact();
  }

  private io.jsonwebtoken.JwtBuilder baseToken() {
    return baseToken(ISSUER, AUDIENCE, "access", KEY_ID);
  }

  private io.jsonwebtoken.JwtBuilder baseToken(
      String issuer, String audience, String tokenType, String keyId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .header()
        .type("JWT")
        .keyId(keyId)
        .and()
        .issuer(issuer)
        .audience()
        .add(audience)
        .and()
        .subject(UUID.randomUUID().toString())
        .claim("role", "CUSTOMER")
        .claim("token_type", tokenType)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(300)));
  }

  private SecretKey signingKey() {
    return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
  }

  private User customer() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setRole(User.Role.CUSTOMER);
    return user;
  }
}
