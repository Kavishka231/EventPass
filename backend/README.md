# EventPass backend

This workspace contains the Java 21 Spring Boot API, Flyway migrations, unit and integration tests, Maven build, backend environment template, and container image definition.

## Implemented capabilities

- Authentication: BCrypt passwords, JWT access tokens, rotating hashed refresh tokens, logout, active-account enforcement, customer registration, and optional first-admin bootstrap.
- Management: admin users/roles/statistics and venue/seat definitions; organizer-owned draft events, event-specific priced inventory, and a locked cancellation workflow that blocks new sales, immediately invalidates every event ticket, records outbox events, durably refunds confirmed bookings, and releases inventory after successful refunds.
- Booking: Redis holds, deterministic PostgreSQL row locking, server-calculated totals, user/operation-scoped idempotency keys with request fingerprints and same-key serialization, and secure digital tickets.
- Payments: pending charge and refund attempts are committed before provider calls; success, decline, provider references, timestamps, failure details, and ambiguous outcomes requiring reconciliation are durable. Refund transitions are guarded, and a pessimistic booking lock serializes concurrent cancellation requests before refund creation. The provider abstraction serializes and replays same-key financial operations, rejects changed details, and forwards each key through provider adapters. Expiry and cancellation/resale remain supported.
- Messaging: stable versioned envelopes (`eventId`, `eventType`, `version`, `timestamp`, `aggregateId`, `payload`), transactional outbox persistence, PostgreSQL `FOR UPDATE SKIP LOCKED` claiming, capped exponential backoff, explicit primary/dead-letter topic provisioning, idempotent producers, read-committed consumers, idempotent consumption, and durable per-customer notifications with guarded delivery, retry, failure, listing, read, and unread-count support.
- Operations: Flyway validation, OpenAPI, Actuator probes, Prometheus business metrics, structured production logs, request tracing, rate limits, Docker, and CI.
- Verification: Spotless and Maven verification pass; Testcontainers covers seat/financial races, competing outbox publishers, duplicate event delivery, and the complete active-event cancellation orchestration through refunded payments, invalid tickets, released inventory, and outbox events.
- Delivery: the backend image uses a dependency-cache-mounted Java 21 build stage, excludes local artifacts, and runs as a non-root JRE user; CI verifies tests before building the image.

From this directory:

```shell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn spotless:check verify
```

Start PostgreSQL, Redis, and Kafka from the repository root with `docker compose up -d postgres redis kafka`.

Copy `.env.example` to the repository root as `.env`. Production requires `JWT_SECRET`; first startup can additionally use `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`.
