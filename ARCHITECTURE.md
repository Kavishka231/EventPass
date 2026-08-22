# Architecture

The application is a modular monolith organized by business capability (`auth`, `event`, `seat`, `booking`, `payment`, and `ticket`). Controllers translate HTTP requests, services own state transitions and transaction boundaries, and repositories isolate persistence.

Event publication and cancellation are terminally guarded state transitions. Event cancellation, booking preparation, and inventory configuration share a pessimistic event lock; cancellation waits for transient pending bookings, atomically invalidates all event tickets, records `EVENT_CANCELLED` and `EVENT_TICKETS_CANCELLED` in the outbox, and prevents subsequent booking, pricing, blocking, or republication operations. After that transaction commits, each confirmed booking enters the durable refund workflow; successful refunds cancel the booking and release its event-seat inventory, while provider failures remain recorded for follow-up without making tickets valid or stopping other affected bookings.

## Booking consistency

Booking uses defense in depth:

1. A Redis `SET NX` lock named `seat-lock:{eventId}:{eventSeatId}` rejects competing temporary holds and expires after five minutes.
2. Locks contain an opaque owner token and are released through a compare-and-delete Lua script.
3. PostgreSQL event-seat rows are pessimistically locked in sorted order within the booking transaction.
4. Availability is revalidated from the database; prices are read from event inventory, never accepted from clients.
5. A short transaction commits the `PENDING` booking, `HELD` inventory, and `PENDING` payment before the provider is called without an open database transaction.
6. Separate locked transactions record an attempt and finalize success or decline. Provider adapters receive the booking idempotency key; the sandbox base implementation atomically replays its first definitive same-key result and rejects changed charge details. Ambiguous provider errors leave inventory held and mark the payment `UNKNOWN` with reconciliation `PENDING`; expiration cannot release those seats.
7. Unique booking references, ticket tokens, payment references, and scoped idempotency keys provide durable duplication barriers.

Cancellation uses the same boundary: a `PENDING` refund linked to both payment and booking is committed before the provider call. A locked finalization transaction records the provider result and only then marks the payment refunded, cancels tickets and booking, and releases inventory. Unknown outcomes remain reconciliation-pending without releasing seats.

## Event delivery

Business transactions write version-1 event envelopes and domain state to PostgreSQL atomically. Every envelope has exactly `eventId`, `eventType`, `version`, `timestamp`, `aggregateId`, and `payload`; consumers deserialize this shared contract, while aliases allow already-persisted legacy envelopes to drain safely. Each publisher claims due rows with PostgreSQL `FOR UPDATE SKIP LOCKED`. Failures use persisted capped exponential schedules, explicit failed states, and administrator recovery. Kafka primary topics are provisioned with explicit partition, retention, replication, and minimum in-sync replica settings; automatic topic creation is disabled. Consumers use read-committed isolation and bounded exponential retries before publishing the unchanged record to a same-partition `.DLT`. The notification consumer resolves a booking event to its customer and commits the processed-event claim with the durable notification in one transaction. Delivery remains at least once, with consumer idempotency keyed by envelope event ID and reinforced by the notification source-event constraint.

## Operational boundaries

Request and correlation filters propagate safe identifiers into structured production logs. Redis rate limits sensitive write endpoints. Actuator readiness evaluates PostgreSQL, Redis, and Kafka, while liveness remains tied to application process state. Prometheus exports HTTP metrics and explicit booking, payment-failure, and seat-lock counters.
