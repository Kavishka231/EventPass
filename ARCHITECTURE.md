# Architecture

The application is a modular monolith organized by business capability (`auth`, `event`, `seat`, `booking`, `payment`, and `ticket`). Controllers translate HTTP requests, services own state transitions and transaction boundaries, and repositories isolate persistence.

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

Booking transactions write an event envelope and business state to PostgreSQL atomically. The scheduled outbox publisher retries pending rows to `booking.events`, `payment.events`, and `ticket.events`, recording bounded failures and publish timestamps. Delivery is at least once. The notification consumer claims each event ID in `processed_events`, so duplicate Kafka delivery does not repeat consumer work.

## Operational boundaries

Request and correlation filters propagate safe identifiers into structured production logs. Redis rate limits sensitive write endpoints. Actuator readiness evaluates PostgreSQL, Redis, and Kafka, while liveness remains tied to application process state. Prometheus exports HTTP metrics and explicit booking, payment-failure, and seat-lock counters.
