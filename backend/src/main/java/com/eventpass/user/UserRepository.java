package com.eventpass.user;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  long countByRoleAndStatus(User.Role role, User.Status status);

  @Query(value = "SELECT pg_advisory_xact_lock(1635784961)", nativeQuery = true)
  void acquireAdministratorLifecycleLock();
}
