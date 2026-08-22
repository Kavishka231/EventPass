package com.eventpass.auth;

import com.eventpass.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {
  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private boolean revoked;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_token_id")
  private RefreshToken parentToken;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revocation_reason", length = 50)
  private String revocationReason;

  @Column(name = "device_info", length = 255)
  private String deviceInfo;

  public void revoke(Instant now, String reason) {
    revoked = true;
    revokedAt = now;
    revocationReason = reason;
  }
}
