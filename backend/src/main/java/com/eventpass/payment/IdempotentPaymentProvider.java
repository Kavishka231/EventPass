package com.eventpass.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces process-local provider idempotency for sandbox implementations. Production adapters must
 * additionally forward the same key to the external provider for cross-instance durability.
 */
public abstract class IdempotentPaymentProvider implements PaymentProvider {
  private final ConcurrentHashMap<String, StoredCharge> charges = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, StoredRefund> refunds = new ConcurrentHashMap<>();

  @Override
  public final PaymentResult charge(
      String providerToken, BigDecimal amount, String currency, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Payment idempotency key must not be blank.");
    }
    String requestHash = requestHash(providerToken, amount, currency);
    StoredCharge stored =
        charges.compute(
            idempotencyKey,
            (key, existing) -> {
              if (existing == null) {
                return new StoredCharge(
                    requestHash, performCharge(providerToken, amount, currency, key));
              }
              if (!existing.requestHash().equals(requestHash)) {
                throw new PaymentIdempotencyException(
                    "Payment idempotency key was reused with different charge details.");
              }
              return existing;
            });
    return stored.result();
  }

  protected abstract PaymentResult performCharge(
      String providerToken, BigDecimal amount, String currency, String idempotencyKey);

  @Override
  public final RefundResult refund(
      String paymentReference, BigDecimal amount, String currency, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Refund idempotency key must not be blank.");
    }
    String requestHash = requestHash(paymentReference, amount, currency);
    StoredRefund stored =
        refunds.compute(
            idempotencyKey,
            (key, existing) -> {
              if (existing == null) {
                return new StoredRefund(
                    requestHash, performRefund(paymentReference, amount, currency, key));
              }
              if (!existing.requestHash().equals(requestHash)) {
                throw new PaymentIdempotencyException(
                    "Refund idempotency key was reused with different refund details.");
              }
              return existing;
            });
    return stored.result();
  }

  protected abstract RefundResult performRefund(
      String paymentReference, BigDecimal amount, String currency, String idempotencyKey);

  private String requestHash(String providerToken, BigDecimal amount, String currency) {
    String canonical =
        providerToken.length()
            + ":"
            + providerToken
            + "|"
            + amount.stripTrailingZeros().toPlainString()
            + "|"
            + currency;
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private record StoredCharge(String requestHash, PaymentResult result) {}

  private record StoredRefund(String requestHash, RefundResult result) {}
}
