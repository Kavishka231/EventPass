package com.eventpass.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eventpass.common.error.ErrorResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {
  @Test
  void rateLimitUsesTheStandardErrorEnvelope() throws Exception {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.<Long>execute(any(), any(), any())).thenReturn(11L);
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    RateLimitFilter filter = new RateLimitFilter(redis, new ErrorResponseWriter(objectMapper));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.setAttribute("requestId", "rate-request");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, new MockFilterChain());

    var body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(body.path("code").asText()).isEqualTo("RATE_LIMIT_EXCEEDED");
    assertThat(body.path("path").asText()).isEqualTo("/api/v1/auth/login");
    assertThat(body.path("requestId").asText()).isEqualTo("rate-request");
    assertThat(body.hasNonNull("timestamp")).isTrue();
  }
}
