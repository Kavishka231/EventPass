package com.eventpass.auth;

import com.eventpass.user.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private static final String ACCESS_TOKEN_TYPE = "access";
  private static final String JWT_HEADER_TYPE = "JWT";
  private static final String SIGNING_ALGORITHM = "HS256";
  private final SecretKey key;
  private final Duration accessTtl;
  private final String issuer;
  private final String audience;
  private final String signingKeyId;

  public JwtService(
      @Value("${eventpass.jwt.secret}") String secret,
      @Value("${eventpass.jwt.access-ttl}") Duration accessTtl,
      @Value("${eventpass.jwt.issuer}") String issuer,
      @Value("${eventpass.jwt.audience}") String audience,
      @Value("${eventpass.jwt.signing-key-id}") String signingKeyId) {
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalArgumentException("JWT signing secret must contain at least 32 bytes.");
    }
    if (issuer.isBlank() || audience.isBlank() || signingKeyId.isBlank()) {
      throw new IllegalArgumentException("JWT issuer, audience, and signing key ID are required.");
    }
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.accessTtl = accessTtl;
    this.issuer = issuer;
    this.audience = audience;
    this.signingKeyId = signingKeyId;
  }

  public String create(User u) {
    Instant now = Instant.now();
    return Jwts.builder()
        .header()
        .type(JWT_HEADER_TYPE)
        .keyId(signingKeyId)
        .and()
        .issuer(issuer)
        .audience()
        .add(audience)
        .and()
        .subject(u.getId().toString())
        .claim("role", u.getRole().name())
        .claim("token_type", ACCESS_TOKEN_TYPE)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(accessTtl)))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  public Claims parse(String token) {
    Jws<Claims> signed =
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer(issuer)
            .requireAudience(audience)
            .require("token_type", ACCESS_TOKEN_TYPE)
            .build()
            .parseSignedClaims(token);
    if (!JWT_HEADER_TYPE.equals(signed.getHeader().getType())
        || !SIGNING_ALGORITHM.equals(signed.getHeader().getAlgorithm())
        || !signingKeyId.equals(signed.getHeader().getKeyId())) {
      throw new JwtException("JWT signing headers are invalid.");
    }
    return signed.getPayload();
  }
}
