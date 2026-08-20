package com.eventpass.common.web;

import com.eventpass.user.User;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {
  private static final DefaultRedisScript<Long> INCREMENT =
      new DefaultRedisScript<>(
          "local n=redis.call('incr',KEYS[1]); if n==1 then redis.call('expire',KEYS[1],ARGV[1]) end; return n",
          Long.class);
  private final StringRedisTemplate redis;

  public RateLimitFilter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!"POST".equals(request.getMethod())) return true;
    String path = request.getRequestURI();
    return !(path.equals("/api/v1/auth/login")
        || path.equals("/api/v1/auth/register")
        || path.equals("/api/v1/bookings"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    int limit = request.getRequestURI().startsWith("/api/v1/auth/") ? 10 : 30;
    String identity = identity(request);
    long window = Instant.now().getEpochSecond() / 60;
    String key = "rate-limit:" + request.getRequestURI() + ":" + identity + ":" + window;
    try {
      Long count = redis.execute(INCREMENT, List.of(key), "60");
      response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
      response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(0, limit - count)));
      if (count > limit) {
        response.setStatus(429);
        response.setContentType("application/json");
        response
            .getWriter()
            .write(
                "{\"status\":429,\"error\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests. Try again later.\"}");
        return;
      }
    } catch (RedisConnectionFailureException ignored) {
      // Database locking and authentication controls remain authoritative if Redis is unavailable.
    }
    chain.doFilter(request, response);
  }

  private String identity(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      if (authentication.getPrincipal() instanceof User user) {
        return user.getId().toString();
      }
      return authentication.getName();
    }
    return request.getRemoteAddr().replace(':', '_');
  }
}
