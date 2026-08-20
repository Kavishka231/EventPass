package com.eventpass.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  record ErrorResponse(
      Instant timestamp, int status, String error, String message, String path, String requestId) {}

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorResponse> api(ApiException e, HttpServletRequest r) {
    return response(e.status(), e.code(), e.getMessage(), r);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> validation(
      MethodArgumentNotValidException e, HttpServletRequest r) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(x -> x.getField() + ": " + x.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, r);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unknown(Exception e, HttpServletRequest r) {
    log.error("Unhandled request failure", e);
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", r);
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus s, String c, String m, HttpServletRequest r) {
    return ResponseEntity.status(s)
        .body(
            new ErrorResponse(
                Instant.now(), s.value(), c, m, r.getRequestURI(), r.getHeader("X-Request-Id")));
  }
}
