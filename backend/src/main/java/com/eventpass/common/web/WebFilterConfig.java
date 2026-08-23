package com.eventpass.common.web;

import com.eventpass.common.error.ErrorResponseWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class WebFilterConfig {
  @Bean
  RateLimitFilter rateLimitFilter(StringRedisTemplate redis, ErrorResponseWriter errors) {
    return new RateLimitFilter(redis, errors);
  }

  @Bean
  FilterRegistrationBean<RateLimitFilter> disableAutomaticRateLimitRegistration(
      RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
