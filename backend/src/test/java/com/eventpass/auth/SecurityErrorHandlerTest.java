package com.eventpass.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventpass.common.error.ErrorResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class SecurityErrorHandlerTest {
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final SecurityErrorHandler handler =
      new SecurityErrorHandler(new ErrorResponseWriter(objectMapper));

  @Test
  void writesConsistentUnauthorizedResponse() throws Exception {
    MockHttpServletRequest request = request();
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.commence(request, response, new BadCredentialsException("not exposed"));

    assertResponse(response, 401, "UNAUTHORIZED");
  }

  @Test
  void writesConsistentForbiddenResponse() throws Exception {
    MockHttpServletRequest request = request();
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.handle(request, response, new AccessDeniedException("not exposed"));

    assertResponse(response, 403, "FORBIDDEN");
  }

  private MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bookings");
    request.setAttribute("requestId", "request-123");
    return request;
  }

  private void assertResponse(MockHttpServletResponse response, int status, String code)
      throws Exception {
    var body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(response.getStatus()).isEqualTo(status);
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(body.path("status").asInt()).isEqualTo(status);
    assertThat(body.path("code").asText()).isEqualTo(code);
    assertThat(body.path("path").asText()).isEqualTo("/api/v1/bookings");
    assertThat(body.path("requestId").asText()).isEqualTo("request-123");
    assertThat(body.hasNonNull("timestamp")).isTrue();
  }
}
