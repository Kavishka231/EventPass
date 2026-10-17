package com.eventpass.payment;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reconciliations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReconciliationController {
  private final PaymentReconciliationService service;

  public AdminReconciliationController(PaymentReconciliationService service) {
    this.service = service;
  }

  @GetMapping("/payments")
  Page<PaymentReconciliationService.ReconciliationResponse> payments(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.payments(pageable);
  }

  @GetMapping("/refunds")
  Page<PaymentReconciliationService.ReconciliationResponse> refunds(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.refunds(pageable);
  }

  @PostMapping("/{type}/{id}/retry")
  ResponseEntity<Void> retry(
      @PathVariable PaymentReconciliationService.Type type, @PathVariable UUID id) {
    service.retry(type, id);
    return ResponseEntity.noContent().build();
  }
}
