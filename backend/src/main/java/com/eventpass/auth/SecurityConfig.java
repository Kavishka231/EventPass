package com.eventpass.auth;

import com.eventpass.common.web.RateLimitFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain filter(
      HttpSecurity http,
      JwtAuthenticationFilter jwt,
      RateLimitFilter rateLimit,
      SecurityErrorHandler errors)
      throws Exception {
    return http.csrf(c -> c.disable())
        .cors(c -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/events/**",
                        "/api/v1/venues/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
        .headers(
            headers -> {
              headers.contentSecurityPolicy(
                  policy ->
                      policy.policyDirectives(
                          "default-src 'none'; base-uri 'none'; frame-ancestors 'none'"));
              headers.frameOptions(frame -> frame.deny());
              headers.referrerPolicy(
                  referrer ->
                      referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
              headers.permissionsPolicy(
                  permissions ->
                      permissions.policy("camera=(), microphone=(), geolocation=(), payment=()"));
              headers.httpStrictTransportSecurity(
                  hsts -> hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(31_536_000));
            })
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(rateLimit, JwtAuthenticationFilter.class)
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${eventpass.security.cors.allowed-origins:http://localhost:3000}")
          List<String> allowedOrigins) {
    if (allowedOrigins.isEmpty()
        || allowedOrigins.stream().anyMatch(origin -> origin.isBlank() || origin.equals("*"))) {
      throw new IllegalArgumentException("CORS allowed origins must be explicit.");
    }
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins.stream().map(String::trim).toList());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "Idempotency-Key",
            "X-Request-Id",
            "X-Correlation-Id"));
    configuration.setExposedHeaders(List.of("X-Request-Id", "X-Correlation-Id"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
