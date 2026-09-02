package com.eventpass.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuthControllerTest {
  private final AuthService service = mock(AuthService.class);
  private final AuthController controller =
      new AuthController(service, Duration.ofDays(30), true, "Strict");

  @Test
  void loginIssuesProductionSafeRefreshCookieWithoutJsonToken() throws Exception {
    var request = new AuthController.LoginRequest("customer@example.com", "password");
    var authentication =
        new AuthController.AuthResponse("access-token", "refresh-secret", "Bearer", "CUSTOMER");
    when(service.login(request, "Browser")).thenReturn(authentication);

    var response = controller.login(request, "Browser");

    assertThat(response.getHeaders().getFirst("Set-Cookie"))
        .contains(
            "eventpass_refresh=refresh-secret",
            "Path=/api/v1/auth",
            "Max-Age=2592000",
            "Secure",
            "HttpOnly",
            "SameSite=Strict");
    assertThat(new ObjectMapper().writeValueAsString(response.getBody()))
        .doesNotContain("refreshToken", "refresh-secret")
        .contains("access-token");
  }

  @Test
  void logoutRevokesCookieSessionAndExpiresBrowserCookie() {
    var response = controller.logout("refresh-secret");

    verify(service).logout("refresh-secret");
    assertThat(response.getHeaders().getFirst("Set-Cookie"))
        .contains("eventpass_refresh=", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Strict");
  }
}
