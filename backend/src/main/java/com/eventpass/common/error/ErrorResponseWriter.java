package com.eventpass.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ErrorResponseWriter {
  private final ObjectMapper objectMapper;

  public ErrorResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(),
        new ErrorResponse(
            Instant.now(), status, code, message, request.getRequestURI(), requestId(request)));
  }

  private String requestId(HttpServletRequest request) {
    Object generated = request.getAttribute("requestId");
    return generated == null ? request.getHeader("X-Request-Id") : generated.toString();
  }
}
