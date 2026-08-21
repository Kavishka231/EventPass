package com.eventpass.common.outbox;

import com.eventpass.common.error.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxRecoveryService {
  private final OutboxEventRepository events;

  public OutboxRecoveryService(OutboxEventRepository events) {
    this.events = events;
  }

  @Transactional
  public void retry(UUID eventId) {
    OutboxEvent event =
        events
            .lockById(eventId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "OUTBOX_EVENT_NOT_FOUND",
                        "Outbox event was not found."));
    if (event.getStatus() != OutboxEvent.Status.FAILED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "OUTBOX_EVENT_NOT_FAILED",
          "Only failed outbox events can be retried manually.");
    }
    event.recover(Instant.now());
  }
}
