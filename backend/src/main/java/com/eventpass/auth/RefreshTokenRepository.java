package com.eventpass.auth;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
  Optional<RefreshToken> findByTokenHashAndRevokedFalse(String hash);

  @Modifying
  @Query("update RefreshToken t set t.revoked=true where t.user.id=:userId")
  int revokeAll(UUID userId);
}
