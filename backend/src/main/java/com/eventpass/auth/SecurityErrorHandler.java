package com.eventpass.auth;

import com.eventpass.common.error.ErrorResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final ErrorResponseWriter errors;

  public SecurityErrorHandler(ErrorResponseWriter errors) {
    this.errors = errors;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    errors.write(
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
    errors.write(
        request,
        response,
        HttpServletResponse.SC_FORBIDDEN,
        "FORBIDDEN",
        "You are not authorized to perform this operation.");
  }
}
