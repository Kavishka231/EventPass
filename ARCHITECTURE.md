# Architecture

The application is a modular monolith organized by business capability (`auth`, `event`, `seat`, `booking`, `payment`, and `ticket`). Controllers translate HTTP requests, services own state transitions and transaction boundaries, and repositories isolate persistence.

## Booking consistency

Booking uses defense in depth:

1. A Redis `SET NX` lock named `seat-lock:{eventId}:{eventSeatId}` rejects competing temporary holds and expires after five minutes.
2. Locks contain an opaque owner token and are released through a compare-and-delete Lua script.
3. PostgreSQL event-seat rows are pessimistically locked in sorted order within the booking transaction.
4. Availability is revalidated from the database; prices are read from event inventory, never accepted from clients.
5. Payment succeeds before `HELD` becomes `SOLD`; transaction rollback prevents partial bookings.
6. Unique booking references, ticket tokens, payment references, and idempotency keys provide durable duplication barriers.

Kafka dependencies and topic configuration are present for the next milestone. Domain event publication should use a transactional outbox so database commits cannot be separated from event delivery.
