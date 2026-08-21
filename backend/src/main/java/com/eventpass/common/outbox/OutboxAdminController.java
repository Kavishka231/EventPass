package com.eventpass.common.outbox;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/outbox")
@PreAuthorize("hasRole('ADMIN')")
public class OutboxAdminController {
  private final OutboxRecoveryService recovery;

  public OutboxAdminController(OutboxRecoveryService recovery) {
    this.recovery = recovery;
  }

  @PostMapping("/{id}/retry")
  public ResponseEntity<Void> retry(@PathVariable UUID id) {
    recovery.retry(id);
    return ResponseEntity.accepted().build();
  }
}
