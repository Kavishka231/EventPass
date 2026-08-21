package com.eventpass.common.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
  @Query(
      value =
          """
          SELECT *
          FROM outbox_events
          WHERE published_at IS NULL AND attempts < :maximumAttempts
          ORDER BY occurred_at
          LIMIT :batchSize
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<OutboxEvent> claimPendingBatch(
      @Param("maximumAttempts") int maximumAttempts, @Param("batchSize") int batchSize);
}
