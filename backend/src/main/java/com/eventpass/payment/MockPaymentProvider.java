package com.eventpass.payment;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component
public class MockPaymentProvider implements PaymentProvider {
  public PaymentResult charge(String token,BigDecimal amount,String currency,String key){return new PaymentResult(!"tok_fail".equals(token),"mock_"+UUID.randomUUID());}
}
