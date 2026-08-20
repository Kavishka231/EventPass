package com.eventpass.auth;

import com.eventpass.common.web.RateLimitFilter;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain filter(
      HttpSecurity http, JwtAuthenticationFilter jwt, RateLimitFilter rateLimit) throws Exception {
    return http.csrf(c -> c.disable())
        .cors(c -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/events/**",
                        "/api/v1/venues/**",
                        "/actuator/health/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'")))
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(rateLimit, JwtAuthenticationFilter.class)
        .build();
  }
}
