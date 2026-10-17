package com.eventpass.payment;

import com.eventpass.booking.BookingPaymentTransactions;
import com.eventpass.common.error.ApiException;
import com.eventpass.common.metrics.BusinessMetrics;
import com.eventpass.common.outbox.OutboxService;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentReconciliationService {
  private final PaymentRepository payments;
  private final RefundRepository refunds;
  private final PaymentProvider provider;
  private final BookingPaymentTransactions bookingTransactions;
  private final RefundTransactions refundTransactions;
  private final BusinessMetrics metrics;
  private final OutboxService outbox;
  private final int batchSize;
  private final int maximumAttempts;
  private final Duration baseDelay;
  private final Duration claimLease;
  private final TransactionTemplate transactionTemplate;

  public PaymentReconciliationService(
      PaymentRepository payments,
      RefundRepository refunds,
      PaymentProvider provider,
      BookingPaymentTransactions bookingTransactions,
      RefundTransactions refundTransactions,
      BusinessMetrics metrics,
      OutboxService outbox,
      PlatformTransactionManager transactionManager,
      @Value("${eventpass.reconciliation.batch-size:25}") int batchSize,
      @Value("${eventpass.reconciliation.maximum-attempts:8}") int maximumAttempts,
      @Value("${eventpass.reconciliation.base-delay:PT30S}") Duration baseDelay,
      @Value("${eventpass.reconciliation.claim-lease:PT2M}") Duration claimLease) {
    this.payments = payments;
    this.refunds = refunds;
    this.provider = provider;
    this.bookingTransactions = bookingTransactions;
    this.refundTransactions = refundTransactions;
    this.metrics = metrics;
    this.outbox = outbox;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.batchSize = batchSize;
    this.maximumAttempts = maximumAttempts;
    this.baseDelay = baseDelay;
    this.claimLease = claimLease;
  }

  public void reconcileDue() {
    List<PaymentWork> paymentWork =
        Objects.requireNonNull(transactionTemplate.execute(status -> claimPayments()));
    paymentWork.forEach(this::reconcilePayment);
    List<RefundWork> refundWork =
        Objects.requireNonNull(transactionTemplate.execute(status -> claimRefunds()));
    refundWork.forEach(this::reconcileRefund);
  }

  @Transactional
  public List<PaymentWork> claimPayments() {
    Instant now = Instant.now();
    return payments.lockDueForReconciliation(now, batchSize).stream()
        .map(payments::getReferenceById)
        .map(
            payment -> {
              claim(payment, now);
              return new PaymentWork(
                  payment.getId(),
                  payment.getBooking().getId(),
                  payment.getBooking().getIdempotencyKey());
            })
        .toList();
  }

  @Transactional
  public List<RefundWork> claimRefunds() {
    Instant now = Instant.now();
    return refunds.lockDueForReconciliation(now, batchSize).stream()
        .map(refunds::getReferenceById)
        .map(
            refund -> {
              claim(refund, now);
              return new RefundWork(refund.getId(), refund.getIdempotencyKey());
            })
        .toList();
  }

  public void reconcilePayment(PaymentWork work) {
    try {
      PaymentProvider.ReconciliationResult result = provider.lookupCharge(work.idempotencyKey());
      if (result.outcome() == PaymentProvider.ReconciliationResult.Outcome.PENDING) {
        transactionTemplate.executeWithoutResult(status -> deferPayment(work.paymentId(), null));
      } else if (bookingTransactions.reconcile(work.bookingId(), result)) {
        metrics.reconciliationSucceeded("payment");
      }
    } catch (RuntimeException exception) {
      metrics.reconciliationFailed("payment");
      transactionTemplate.executeWithoutResult(status -> deferPayment(work.paymentId(), exception));
    }
  }

  public void reconcileRefund(RefundWork work) {
    try {
      PaymentProvider.ReconciliationResult result = provider.lookupRefund(work.idempotencyKey());
      if (result.outcome() == PaymentProvider.ReconciliationResult.Outcome.PENDING) {
        transactionTemplate.executeWithoutResult(status -> deferRefund(work.refundId(), null));
      } else if (refundTransactions.reconcile(work.refundId(), result)) {
        metrics.reconciliationSucceeded("refund");
      }
    } catch (RuntimeException exception) {
      metrics.reconciliationFailed("refund");
      transactionTemplate.executeWithoutResult(status -> deferRefund(work.refundId(), exception));
    }
  }

  @Transactional
  public void deferPayment(UUID id, RuntimeException error) {
    Payment payment = payments.lockById(id).orElseThrow();
    defer(payment, error, Instant.now());
  }

  @Transactional
  public void deferRefund(UUID id, RuntimeException error) {
    Refund refund = refunds.lockById(id).orElseThrow();
    defer(refund, error, Instant.now());
  }

  @Transactional(readOnly = true)
  public Page<ReconciliationResponse> payments(Pageable pageable) {
    return payments
        .findByReconciliationStatusIn(unresolvedStatuses(), pageable)
        .map(this::response);
  }

  @Transactional(readOnly = true)
  public Page<ReconciliationResponse> refunds(Pageable pageable) {
    return refunds.findByReconciliationStatusIn(unresolvedStatuses(), pageable).map(this::response);
  }

  @Transactional
  public void retry(Type type, UUID id) {
    if (type == Type.PAYMENT) {
      Payment payment = payments.lockById(id).orElseThrow(() -> notFound(type));
      retry(payment);
    } else {
      Refund refund = refunds.lockById(id).orElseThrow(() -> notFound(type));
      retry(refund);
    }
  }

  private void claim(Payment payment, Instant now) {
    payment.setReconciliationStatus(Payment.ReconciliationStatus.IN_PROGRESS);
    payment.setReconciliationAttempts(payment.getReconciliationAttempts() + 1);
    payment.setLastReconciledAt(now);
    payment.setNextReconciliationAt(now.plus(claimLease));
  }

  private void claim(Refund refund, Instant now) {
    refund.setReconciliationStatus(Payment.ReconciliationStatus.IN_PROGRESS);
    refund.setReconciliationAttempts(refund.getReconciliationAttempts() + 1);
    refund.setLastReconciledAt(now);
    refund.setNextReconciliationAt(now.plus(claimLease));
  }

  private void defer(Payment payment, RuntimeException error, Instant now) {
    if (error != null) payment.setLastError(safeMessage(error));
    payment.setLastReconciledAt(now);
    if (payment.getReconciliationAttempts() >= maximumAttempts) {
      payment.setReconciliationStatus(Payment.ReconciliationStatus.MANUAL_REVIEW);
      payment.setNextReconciliationAt(null);
      outbox.record(
          "payment.events",
          "PAYMENT_RECONCILIATION_MANUAL_REVIEW",
          payment.getBooking().getId(),
          Map.of("bookingId", payment.getBooking().getId(), "paymentId", payment.getId()));
      return;
    }
    payment.setReconciliationStatus(Payment.ReconciliationStatus.PENDING);
    payment.setNextReconciliationAt(now.plus(backoff(payment.getReconciliationAttempts())));
  }

  private void defer(Refund refund, RuntimeException error, Instant now) {
    if (error != null) refund.setLastError(safeMessage(error));
    refund.setLastReconciledAt(now);
    if (refund.getReconciliationAttempts() >= maximumAttempts) {
      refund.setReconciliationStatus(Payment.ReconciliationStatus.MANUAL_REVIEW);
      refund.setNextReconciliationAt(null);
      outbox.record(
          "payment.events",
          "REFUND_RECONCILIATION_MANUAL_REVIEW",
          refund.getBooking().getId(),
          Map.of("bookingId", refund.getBooking().getId(), "refundId", refund.getId()));
      return;
    }
    refund.setReconciliationStatus(Payment.ReconciliationStatus.PENDING);
    refund.setNextReconciliationAt(now.plus(backoff(refund.getReconciliationAttempts())));
  }

  private Duration backoff(int attempts) {
    long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 10);
    return baseDelay.multipliedBy(multiplier);
  }

  private void retry(Payment payment) {
    requireRetryable(payment.getReconciliationStatus());
    payment.setReconciliationStatus(Payment.ReconciliationStatus.PENDING);
    payment.setNextReconciliationAt(Instant.now());
  }

  private void retry(Refund refund) {
    requireRetryable(refund.getReconciliationStatus());
    refund.setReconciliationStatus(Payment.ReconciliationStatus.PENDING);
    refund.setNextReconciliationAt(Instant.now());
  }

  private void requireRetryable(Payment.ReconciliationStatus status) {
    if (status != Payment.ReconciliationStatus.PENDING
        && status != Payment.ReconciliationStatus.MANUAL_REVIEW) {
      throw new ApiException(
          HttpStatus.CONFLICT, "RECONCILIATION_NOT_RETRYABLE", "Reconciliation cannot be retried.");
    }
  }

  private ApiException notFound(Type type) {
    return new ApiException(
        HttpStatus.NOT_FOUND, "RECONCILIATION_NOT_FOUND", type + " was not found.");
  }

  private String safeMessage(RuntimeException error) {
    String value = error.getMessage();
    if (value == null || value.isBlank()) value = "Provider lookup failed.";
    return value.substring(0, Math.min(500, value.length()));
  }

  private Set<Payment.ReconciliationStatus> unresolvedStatuses() {
    return EnumSet.of(
        Payment.ReconciliationStatus.PENDING,
        Payment.ReconciliationStatus.IN_PROGRESS,
        Payment.ReconciliationStatus.MANUAL_REVIEW);
  }

  private ReconciliationResponse response(Payment payment) {
    return new ReconciliationResponse(
        Type.PAYMENT,
        payment.getId(),
        payment.getBooking().getId(),
        payment.getStatus().name(),
        payment.getReconciliationStatus(),
        payment.getReconciliationAttempts(),
        payment.getNextReconciliationAt(),
        payment.getLastReconciledAt(),
        payment.getLastError());
  }

  private ReconciliationResponse response(Refund refund) {
    return new ReconciliationResponse(
        Type.REFUND,
        refund.getId(),
        refund.getBooking().getId(),
        refund.getStatus().name(),
        refund.getReconciliationStatus(),
        refund.getReconciliationAttempts(),
        refund.getNextReconciliationAt(),
        refund.getLastReconciledAt(),
        refund.getLastError());
  }

  public enum Type {
    PAYMENT,
    REFUND
  }

  public record PaymentWork(UUID paymentId, UUID bookingId, String idempotencyKey) {}

  public record RefundWork(UUID refundId, String idempotencyKey) {}

  public record ReconciliationResponse(
      Type type,
      UUID id,
      UUID bookingId,
      String operationStatus,
      Payment.ReconciliationStatus reconciliationStatus,
      int attempts,
      Instant nextReconciliationAt,
      Instant lastReconciledAt,
      String lastError) {}
}
