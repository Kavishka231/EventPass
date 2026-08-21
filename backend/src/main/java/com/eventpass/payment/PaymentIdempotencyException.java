package com.eventpass.payment;

public class PaymentIdempotencyException extends RuntimeException {
  public PaymentIdempotencyException(String message) {
    super(message);
  }
}
