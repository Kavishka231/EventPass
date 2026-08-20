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
  private final SecretKey key; private final Duration accessTtl;
  public JwtService(@Value("${eventpass.jwt.secret}") String secret,@Value("${eventpass.jwt.access-ttl}") Duration accessTtl){this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));this.accessTtl=accessTtl;}
  public String create(User u){Instant now=Instant.now();return Jwts.builder().subject(u.getId().toString()).claim("role",u.getRole().name()).issuedAt(Date.from(now)).expiration(Date.from(now.plus(accessTtl))).signWith(key).compact();}
  public Claims parse(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();}
}
