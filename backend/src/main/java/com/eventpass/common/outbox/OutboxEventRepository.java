package com.eventpass.common.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
  long countByStatus(OutboxEvent.Status status);

  @Query(
      value =
          """
          SELECT *
          FROM outbox_events
          WHERE status = 'PENDING'
            AND next_attempt_at <= CURRENT_TIMESTAMP
            AND attempts < :maximumAttempts
          ORDER BY occurred_at
          LIMIT :batchSize
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<OutboxEvent> claimPendingBatch(
      @Param("maximumAttempts") int maximumAttempts, @Param("batchSize") int batchSize);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from OutboxEvent e where e.id = :id")
  java.util.Optional<OutboxEvent> lockById(@Param("id") UUID id);
}
