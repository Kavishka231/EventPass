package com.eventpass.auth;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t where t.tokenHash=:hash")
  Optional<RefreshToken> lockByTokenHash(String hash);

  @Modifying
  @Query("update RefreshToken t set t.revoked=true where t.user.id=:userId")
  int revokeAll(UUID userId);

  @Modifying
  @Query(
      "update RefreshToken t set t.revoked=true, t.revokedAt=:revokedAt, t.revocationReason=:reason where t.familyId=:familyId and t.revoked=false")
  int revokeFamily(UUID familyId, java.time.Instant revokedAt, String reason);
}
