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
            (key, current) -> {
              if (current != null) {
                if (!current.requestHash().equals(requestHash)) {
                  throw new PaymentIdempotencyException(
                      "Payment idempotency key was reused with different charge details.");
                }
                return current;
              }
              try {
                return new StoredCharge(
                    requestHash, performCharge(providerToken, amount, currency, key));
              } catch (PaymentProviderException exception) {
                return new StoredCharge(requestHash, null);
              }
            });
    if (stored.result() == null) {
      throw new PaymentProviderException("Payment provider outcome remains unknown.");
    }
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
            (key, current) -> {
              if (current != null) {
                if (!current.requestHash().equals(requestHash)) {
                  throw new PaymentIdempotencyException(
                      "Refund idempotency key was reused with different refund details.");
                }
                return current;
              }
              try {
                return new StoredRefund(
                    requestHash, performRefund(paymentReference, amount, currency, key));
              } catch (PaymentProviderException exception) {
                return new StoredRefund(requestHash, null);
              }
            });
    if (stored.result() == null) {
      throw new PaymentProviderException("Refund provider outcome remains unknown.");
    }
    return stored.result();
  }

  @Override
  public final ReconciliationResult lookupCharge(String idempotencyKey) {
    StoredCharge stored = charges.get(idempotencyKey);
    if (stored == null) return pending();
    if (stored.result() != null) return reconciliation(stored.result());
    ReconciliationResult result = performChargeLookup(idempotencyKey);
    if (result.outcome() != ReconciliationResult.Outcome.PENDING) {
      charges.computeIfPresent(
          idempotencyKey,
          (key, value) ->
              new StoredCharge(
                  value.requestHash(),
                  new PaymentResult(
                      result.outcome() == ReconciliationResult.Outcome.SUCCESS,
                      result.reference(),
                      result.failureCode())));
    }
    return result;
  }

  @Override
  public final ReconciliationResult lookupRefund(String idempotencyKey) {
    StoredRefund stored = refunds.get(idempotencyKey);
    if (stored == null) return pending();
    if (stored.result() != null) return reconciliation(stored.result());
    ReconciliationResult result = performRefundLookup(idempotencyKey);
    if (result.outcome() != ReconciliationResult.Outcome.PENDING) {
      refunds.computeIfPresent(
          idempotencyKey,
          (key, value) ->
              new StoredRefund(
                  value.requestHash(),
                  new RefundResult(
                      result.outcome() == ReconciliationResult.Outcome.SUCCESS,
                      result.reference(),
                      result.failureCode())));
    }
    return result;
  }

  protected ReconciliationResult performChargeLookup(String idempotencyKey) {
    return pending();
  }

  protected ReconciliationResult performRefundLookup(String idempotencyKey) {
    return pending();
  }

  private ReconciliationResult reconciliation(PaymentResult result) {
    return new ReconciliationResult(
        result.successful()
            ? ReconciliationResult.Outcome.SUCCESS
            : ReconciliationResult.Outcome.FAILED,
        result.reference(),
        result.failureCode());
  }

  private ReconciliationResult reconciliation(RefundResult result) {
    return new ReconciliationResult(
        result.successful()
            ? ReconciliationResult.Outcome.SUCCESS
            : ReconciliationResult.Outcome.FAILED,
        result.reference(),
        result.failureCode());
  }

  private ReconciliationResult pending() {
    return new ReconciliationResult(ReconciliationResult.Outcome.PENDING, null, null);
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
