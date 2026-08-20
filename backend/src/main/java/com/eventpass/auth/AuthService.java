package com.eventpass.auth;

import com.eventpass.common.error.ApiException;
import com.eventpass.user.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserRepository users; private final RefreshTokenRepository tokens; private final PasswordEncoder passwords; private final JwtService jwt; private final Duration refreshTtl; private final SecureRandom random=new SecureRandom();
  public AuthService(UserRepository users,RefreshTokenRepository tokens,PasswordEncoder passwords,JwtService jwt,@Value("${eventpass.jwt.refresh-ttl}") Duration refreshTtl){this.users=users;this.tokens=tokens;this.passwords=passwords;this.jwt=jwt;this.refreshTtl=refreshTtl;}
  @Transactional public AuthController.AuthResponse register(AuthController.RegisterRequest r){if(users.existsByEmailIgnoreCase(r.email()))throw new ApiException(HttpStatus.CONFLICT,"EMAIL_EXISTS","An account already exists for this email.");User u=new User();u.setEmail(r.email().trim().toLowerCase());u.setPasswordHash(passwords.encode(r.password()));u.setFirstName(r.firstName().trim());u.setLastName(r.lastName().trim());return issue(users.save(u));}
  @Transactional public AuthController.AuthResponse login(AuthController.LoginRequest r){User u=users.findByEmailIgnoreCase(r.email()).filter(x->passwords.matches(r.password(),x.getPasswordHash())).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Email or password is incorrect."));return issue(u);}
  @Transactional public AuthController.AuthResponse refresh(String raw){RefreshToken old=tokens.findByTokenHashAndRevokedFalse(hash(raw)).filter(t->t.getExpiresAt().isAfter(Instant.now())).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Refresh token is invalid or expired."));old.setRevoked(true);return issue(old.getUser());}
  @Transactional public void logout(String raw){tokens.findByTokenHashAndRevokedFalse(hash(raw)).ifPresent(t->t.setRevoked(true));}
  private AuthController.AuthResponse issue(User u){byte[] b=new byte[48];random.nextBytes(b);String raw=HexFormat.of().formatHex(b);RefreshToken t=new RefreshToken();t.setUser(u);t.setTokenHash(hash(raw));t.setExpiresAt(Instant.now().plus(refreshTtl));tokens.save(t);return new AuthController.AuthResponse(jwt.create(u),raw,"Bearer",u.getRole().name());}
  private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
