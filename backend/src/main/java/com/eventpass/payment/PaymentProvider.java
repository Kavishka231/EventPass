package com.eventpass.payment;
import java.math.BigDecimal;
public interface PaymentProvider {
  PaymentResult charge(String providerToken,BigDecimal amount,String currency,String idempotencyKey);
  record PaymentResult(boolean successful,String reference){}
}
