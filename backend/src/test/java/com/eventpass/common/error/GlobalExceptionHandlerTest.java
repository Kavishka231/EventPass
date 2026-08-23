package com.eventpass.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void mapsDatabaseConstraintWithoutLeakingDatabaseDetails() {
    var response =
        handler.dataIntegrity(
            new DataIntegrityViolationException("duplicate key secret_table_detail"), request());

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody().code()).isEqualTo("DATA_INTEGRITY_CONFLICT");
    assertThat(response.getBody().message()).doesNotContain("secret_table_detail");
  }

  @Test
  void mapsOptimisticLockingToRetryableConflict() {
    var response =
        handler.optimisticLock(new OptimisticLockingFailureException("stale entity"), request());

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody().code()).isEqualTo("CONCURRENT_MODIFICATION");
    assertThat(response.getBody().message()).contains("retry");
  }

  private MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bookings");
    request.setAttribute("requestId", "request-123");
    return request;
  }
}
