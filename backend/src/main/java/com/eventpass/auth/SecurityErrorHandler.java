package com.eventpass.auth;

import com.eventpass.common.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final ObjectMapper objectMapper;

  public SecurityErrorHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    write(
        request,
        response,
        HttpServletResponse.SC_UNAUTHORIZED,
        "UNAUTHORIZED",
        "Authentication is required.");
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException, ServletException {
    write(
        request,
        response,
        HttpServletResponse.SC_FORBIDDEN,
        "FORBIDDEN",
        "You are not authorized to perform this operation.");
  }

  private void write(
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    Object requestId = request.getAttribute("requestId");
    objectMapper.writeValue(
        response.getOutputStream(),
        new ErrorResponse(
            Instant.now(),
            status,
            code,
            message,
            request.getRequestURI(),
            requestId == null ? null : requestId.toString()));
  }
}
