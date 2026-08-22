package com.eventpass.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ErrorResponse> constraintValidation(
      ConstraintViolationException e, HttpServletRequest r) {
    String message =
        e.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.joining(", "));
    return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, r);
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MissingRequestHeaderException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  ResponseEntity<ErrorResponse> malformedRequest(Exception e, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        "MALFORMED_REQUEST",
        "The request is missing required values or contains malformed data.",
        request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> routeNotFound(
      NoResourceFoundException e, HttpServletRequest request) {
    return response(
        HttpStatus.NOT_FOUND,
        "RESOURCE_NOT_FOUND",
        "The requested resource was not found.",
        request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorResponse> dataIntegrity(
      DataIntegrityViolationException e, HttpServletRequest request) {
    log.warn("Database constraint rejected request path={}", request.getRequestURI());
    return response(
        HttpStatus.CONFLICT,
        "DATA_INTEGRITY_CONFLICT",
        "The request conflicts with existing data.",
        request);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ErrorResponse> optimisticLock(
      OptimisticLockingFailureException e, HttpServletRequest request) {
    return response(
        HttpStatus.CONFLICT,
        "CONCURRENT_MODIFICATION",
        "The resource changed concurrently. Reload it and retry.",
        request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorResponse> methodNotAllowed(
      HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
    return response(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "The HTTP method is not supported for this resource.",
        request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> mediaTypeNotSupported(
      HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
    return response(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "The request content type is not supported.",
        request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException e, HttpServletRequest request) {
    return response(
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        "You are not authorized to perform this operation.",
        request);
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
        .body(new ErrorResponse(Instant.now(), s.value(), c, m, r.getRequestURI(), requestId(r)));
  }

  private String requestId(HttpServletRequest request) {
    Object generated = request.getAttribute("requestId");
    return generated == null ? request.getHeader("X-Request-Id") : generated.toString();
  }
}
