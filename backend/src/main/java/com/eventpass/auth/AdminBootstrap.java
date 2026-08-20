package com.eventpass.auth;

import com.eventpass.user.User;
import com.eventpass.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final String email;
  private final String password;

  public AdminBootstrap(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      @Value("${eventpass.bootstrap-admin.email:}") String email,
      @Value("${eventpass.bootstrap-admin.password:}") String password) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.email = email;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (email.isBlank() || password.isBlank() || users.existsByEmailIgnoreCase(email)) {
      return;
    }
    if (password.length() < 12) {
      throw new IllegalStateException(
          "Bootstrap administrator password must contain at least 12 characters");
    }
    User admin = new User();
    admin.setEmail(email.trim().toLowerCase());
    admin.setPasswordHash(passwordEncoder.encode(password));
    admin.setFirstName("System");
    admin.setLastName("Administrator");
    admin.setRole(User.Role.ADMIN);
    users.save(admin);
  }
}
