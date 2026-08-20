package com.eventpass.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MockPaymentProviderTest {
  private final MockPaymentProvider provider = new MockPaymentProvider();

  @Test
  void acceptsSandboxToken() {
    assertThat(provider.charge("tok_success", BigDecimal.TEN, "LKR", "key").successful()).isTrue();
  }

  @Test
  void rejectsFailureToken() {
    assertThat(provider.charge("tok_fail", BigDecimal.TEN, "LKR", "key").successful()).isFalse();
  }

  @Test
  void refundsSuccessfulSandboxPayment() {
    assertThat(provider.refund("mock_payment", BigDecimal.TEN, "LKR")).isTrue();
  }
}
