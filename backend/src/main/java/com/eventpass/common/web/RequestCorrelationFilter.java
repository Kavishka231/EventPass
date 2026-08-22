package com.eventpass.common.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = validId(request.getHeader("X-Request-Id"));
    String correlationId = validId(request.getHeader("X-Correlation-Id"));
    long started = System.nanoTime();
    MDC.put("requestId", requestId);
    MDC.put("correlationId", correlationId);
    request.setAttribute("requestId", requestId);
    request.setAttribute("correlationId", correlationId);
    response.setHeader("X-Request-Id", requestId);
    response.setHeader("X-Correlation-Id", correlationId);
    try {
      chain.doFilter(request, response);
    } finally {
      long latencyMs = (System.nanoTime() - started) / 1_000_000;
      log.info(
          "http_request method={} path={} status={} latencyMs={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          latencyMs);
      MDC.clear();
    }
  }

  private String validId(String candidate) {
    if (candidate == null || !candidate.matches("[A-Za-z0-9._-]{1,100}")) {
      return UUID.randomUUID().toString();
    }
    return candidate;
  }
}
