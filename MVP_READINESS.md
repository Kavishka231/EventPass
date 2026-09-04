# MVP readiness verification

Verified on 2026-09-05 from `release/mvp-readiness`.

## Automated verification

- `mvn -B clean spotless:check verify`: 101 backend tests passed across 20 suites. Coverage includes the PostgreSQL/Redis/Kafka Testcontainers paths for the HTTP customer journey, authentication and refresh rotation, booking/payment/refund lifecycle, idempotency, seat-lock expiration and contention, Flyway migrations, authorization and organizer ownership, QR validation/redemption, notifications, and outbox retry/deduplication.
- `npm test`: 66 frontend tests passed across 11 files.
- `npm run test:coverage`: 66 tests passed; 74.36% statement, 63.91% branch, 74.38% function, and 76.42% line coverage.
- `npm run lint`, `npm run typecheck`, and `npm run build`: passed.
- `npm run test:e2e`: 5 Chromium journeys passed after correcting a stale organizer-dashboard heading assertion.

## Container verification

`docker compose -p eventpass-mvp-readiness --profile application up -d --build --wait` was run with a temporary JWT secret and a new PostgreSQL volume. PostgreSQL, Redis, Kafka, and the backend became healthy. Application liveness and readiness both returned `UP`; Flyway reached migration 11; Redis returned `PONG`; and Kafka provisioned the booking, event, notification, payment, ticket, and configured dead-letter topics.

The first Compose run exposed an invalid Kafka advertised listener for container-to-container traffic. Compose now separates the `INTERNAL` listener at `kafka:29092` from the host `EXTERNAL` listener at `localhost:9092`, waits for broker health, and starts the application only after Kafka is healthy. A clean second run contained no topic-provisioning, unknown-topic, or broker-connectivity errors. The temporary containers, network, and PostgreSQL volume were removed after verification.

No load-test performance result is claimed; the k6 suite requires a separately seeded, production-like performance environment.
