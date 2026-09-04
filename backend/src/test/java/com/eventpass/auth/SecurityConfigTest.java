package com.eventpass.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigTest {
  private final SecurityConfig configuration = new SecurityConfig();

  @Test
  void corsUsesExplicitCredentialedAllowlist() {
    var source =
        configuration.corsConfigurationSource(
            List.of("https://customer.eventpass.example", "https://admin.eventpass.example"));

    var cors = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/events"));

    assertThat(cors).isNotNull();
    assertThat(cors.getAllowedOrigins())
        .containsExactly("https://customer.eventpass.example", "https://admin.eventpass.example");
    assertThat(cors.getAllowCredentials()).isTrue();
    assertThat(cors.getAllowedOrigins()).doesNotContain("*");
    assertThat(cors.getAllowedHeaders())
        .contains("Authorization", "Idempotency-Key", "X-XSRF-TOKEN");
  }

  @Test
  void corsRejectsWildcardOrigins() {
    assertThatThrownBy(() -> configuration.corsConfigurationSource(List.of("*")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("explicit");
  }
}
