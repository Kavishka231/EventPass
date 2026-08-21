package com.eventpass.payment;

import java.math.BigDecimal;

public interface PaymentProvider {
  PaymentResult charge(
      String providerToken, BigDecimal amount, String currency, String idempotencyKey);

  RefundResult refund(
      String paymentReference, BigDecimal amount, String currency, String idempotencyKey);

  record PaymentResult(boolean successful, String reference, String failureCode) {}

  record RefundResult(boolean successful, String reference, String failureCode) {}
}
