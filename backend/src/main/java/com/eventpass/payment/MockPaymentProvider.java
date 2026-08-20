package com.eventpass.payment;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentProvider implements PaymentProvider {
  public PaymentResult charge(String token, BigDecimal amount, String currency, String key) {
    if ("tok_unknown".equals(token)) {
      throw new PaymentProviderException("Mock provider did not return a definitive outcome.");
    }
    boolean successful = !"tok_fail".equals(token);
    return new PaymentResult(
        successful, "mock_" + UUID.randomUUID(), successful ? null : "MOCK_PAYMENT_DECLINED");
  }

  public boolean refund(String paymentReference, BigDecimal amount, String currency) {
    return true;
  }
}
