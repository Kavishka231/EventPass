package com.eventpass.payment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconciliationJob {
  private final PaymentReconciliationService service;

  public PaymentReconciliationJob(PaymentReconciliationService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${eventpass.reconciliation.scan-delay:PT15S}")
  public void reconcile() {
    service.reconcileDue();
  }
}
