# EventPass backend

This workspace contains the Java 21 Spring Boot API, Flyway migrations, unit and integration tests, Maven build, backend environment template, and container image definition.

## Implemented capabilities

- Authentication: BCrypt passwords, JWT access tokens, rotating hashed refresh tokens, logout, active-account enforcement, customer registration, and optional first-admin bootstrap.
- Management: admin users/roles/statistics and venue/seat definitions; organizer-owned draft events and event-specific priced inventory.
- Booking: Redis holds, deterministic PostgreSQL row locking, server-calculated totals, user/operation-scoped idempotency keys with request fingerprints and same-key serialization, and secure digital tickets.
- Payments: pending attempts are committed before provider calls; success, decline, provider reference, timestamps, failure codes, and ambiguous outcomes requiring reconciliation are durable. Mock refunds, expiry, and cancellation/resale remain supported.
- Messaging: transactional booking/payment/ticket outbox, retrying Kafka publisher, and an idempotent notification consumer backed by `processed_events`.
- Operations: Flyway validation, OpenAPI, Actuator probes, Prometheus business metrics, structured production logs, request tracing, rate limits, Docker, and CI.
- Verification: Spotless and Maven verification pass; Testcontainers proves 20 concurrent buyers yield one booking, one payment, one sold seat, and one ticket.
- Delivery: the backend image uses a dependency-cache-mounted Java 21 build stage, excludes local artifacts, and runs as a non-root JRE user; CI verifies tests before building the image.

From this directory:

```shell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn spotless:check verify
```

Start PostgreSQL, Redis, and Kafka from the repository root with `docker compose up -d postgres redis kafka`.

Copy `.env.example` to the repository root as `.env`. Production requires `JWT_SECRET`; first startup can additionally use `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`.
