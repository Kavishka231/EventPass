package com.eventpass.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
    assertThat(provider.refund("mock_payment", BigDecimal.TEN, "LKR", "refund-key").successful())
        .isTrue();
  }

  @Test
  void repeatedChargeKeyExecutesExactlyOneFinancialCharge() throws Exception {
    AtomicInteger financialCharges = new AtomicInteger();
    MockPaymentProvider idempotentProvider =
        new MockPaymentProvider(
            (token, amount, currency, key) -> {
              financialCharges.incrementAndGet();
              return new PaymentProvider.PaymentResult(true, "provider-reference", null);
            });

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<PaymentProvider.PaymentResult>> calls =
          List.of(
              executor.submit(
                  () -> idempotentProvider.charge("tok_success", BigDecimal.TEN, "LKR", "A")),
              executor.submit(
                  () -> idempotentProvider.charge("tok_success", BigDecimal.TEN, "LKR", "A")),
              executor.submit(
                  () -> idempotentProvider.charge("tok_success", BigDecimal.TEN, "LKR", "A")));

      assertThat(calls)
          .extracting(Future::get)
          .extracting(PaymentProvider.PaymentResult::reference)
          .containsOnly("provider-reference");
    }
    assertThat(financialCharges).hasValue(1);
  }

  @Test
  void reusedKeyWithDifferentChargeDetailsIsRejected() {
    provider.charge("tok_success", BigDecimal.TEN, "LKR", "payload-key");

    assertThatThrownBy(() -> provider.charge("tok_success", BigDecimal.ONE, "LKR", "payload-key"))
        .isInstanceOf(PaymentIdempotencyException.class);
  }
}
