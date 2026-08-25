# EventPass backend

This workspace contains the Java 21 Spring Boot API, Flyway migrations, unit and integration tests, Maven build, backend environment template, and container image definition.

## Implemented capabilities

- Authentication: BCrypt passwords, strictly issuer/audience/type-scoped HS256 JWT access tokens, rotating hashed refresh-token families with reuse detection and family revocation, logout, active-account enforcement, customer registration, and optional first-admin bootstrap.
- Management: protected administrator user/role lifecycle and statistics, venue/seat definitions, organizer-owned draft events, event-specific priced inventory, and a locked cancellation workflow that blocks new sales, immediately invalidates every event ticket, records outbox events, durably refunds confirmed bookings, and releases inventory after successful refunds.
- Booking: Redis holds, deterministic PostgreSQL row locking, server-calculated totals, user/operation-scoped idempotency keys with request fingerprints and same-key serialization, secure digital tickets, owner-scoped organizer/admin QR validation, and pessimistically serialized `ACTIVE` to `USED` admission redemption.
- Payments: pending charge and refund attempts are committed before provider calls; success, decline, provider references, timestamps, failure details, and ambiguous outcomes requiring reconciliation are durable. Refund transitions are guarded, and a pessimistic booking lock serializes concurrent cancellation requests before refund creation. The provider abstraction serializes and replays same-key financial operations, rejects changed details, and forwards each key through provider adapters. Expiry and cancellation/resale remain supported.
- Messaging: stable versioned envelopes (`eventId`, `eventType`, `version`, `timestamp`, `aggregateId`, `payload`), transactional outbox persistence, PostgreSQL `FOR UPDATE SKIP LOCKED` claiming, capped exponential backoff, explicit primary/dead-letter topic provisioning, idempotent producers, read-committed consumers, and atomic idempotent creation of durable customer notifications with guarded delivery, retry, failure, listing, read, and unread-count support.
- Operations: Flyway validation, production-enforced Redis password/TLS and Kafka SASL/TLS with bounded client timeouts and security-aware readiness, a uniform `{timestamp,status,code,message,path,requestId}` error contract, non-production OpenAPI, permissioned Actuator probes and low-cardinality booking/payment/refund, cancellation/expiration, outbox, Kafka, and notification metrics, explicit CORS, hardened security headers, structured production logs, request tracing, rate limits, Docker, and CI.
- API collections: event, booking, ticket, administrator-user, and notification lists expose sortable page envelopes with a default size of 20 and a maximum size of 100.
- Organizer reporting: organizers receive paginated customer, status, value, and seat details only for bookings attached to events they own.
- Administrator booking operations: administrators can inspect all bookings with event/status filters and invoke the existing locked, durable refund-backed cancellation workflow.
- Query efficiency: customer booking pages use a scalar page projection and one bulk seat lookup, while ticket pages use direct projections; composite PostgreSQL indexes support their ownership filters and newest-first ordering.
- Verification: Spotless and Maven verification pass; Testcontainers covers the HTTP authorization matrix, seat/financial/admin races, competing outbox publishers, duplicate event delivery, and the complete active-event cancellation orchestration through refunded payments, invalid tickets, released inventory, and outbox events.
- HTTP contracts: MockMvc integration coverage verifies success statuses and payloads, authenticated validation, authorization failures, pagination bounds, idempotency conflicts, and every field in the standard error envelope.
- Authentication tests: PostgreSQL-backed MockMvc flows cover registration, duplicate emails, login, inactive accounts, refresh rotation, logout, and refresh-token replay revocation.
- Event tests: MockMvc flows cover venue and physical-seat creation, capacity limits, organizer ownership, publication prerequisites, inventory pricing/blocking, and immutable published inventory.
- Booking tests: PostgreSQL/Redis integration flows cover payment success/failure, expiration and cancellation release, seat resale, ticket replacement, and idempotent side effects.
- End-to-end test: a full MockMvc customer journey covers registration, login, published-event discovery, seat selection, successful booking/payment, and active ticket retrieval.
- Delivery: the backend image uses a dependency-cache-mounted Java 21 build stage, excludes local artifacts, and runs as a non-root JRE user; CI verifies tests before building the image, reviews dependency changes, performs CodeQL and Trivy scans, and publishes a CycloneDX image SBOM artifact.
- Recovery: root-level PostgreSQL tooling creates atomic custom-format backups with checksums and retention, verifies them through disposable-database restores, and requires exact target confirmation before transactional recovery.
- Performance: root-level k6 scenarios measure authentication, event and seat browsing, concurrent booking, expected seat contention, throughput, p95/p99 latency, and unexpected errors against an isolated production-like environment.

From this directory:

```shell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn spotless:check verify
```

Start PostgreSQL, Redis, and Kafka from the repository root with `docker compose up -d postgres redis kafka`.

Copy `.env.example` to the repository root as `.env`. Production requires `JWT_SECRET`; first startup can additionally use `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`.
