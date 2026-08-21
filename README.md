# EventPass

EventPass is organized as a monorepo with separate backend and frontend workspaces. The Java 21/Spring Boot backend supports registration, JWT login/refresh/logout, published-event browsing, event seat inventory, idempotent booking, sandbox payment, cancellation, and digital ticket retrieval. The frontend workspace is reserved for the upcoming web application.

## Backend milestone status

The backend now includes:

- secure customer registration, short-lived JWT access tokens, rotating hashed refresh tokens, logout, account-state enforcement, and environment-only administrator bootstrap;
- administrator user/role/statistics APIs, venue management, physical seat creation with capacity checks, organizer-owned events, guarded publication/cancellation with immediate ticket invalidation, automatic booking refunds and inventory release, and per-event pricing/blocking;
- Redis TTL locks plus PostgreSQL row and advisory locks, optimistic versions, server-side pricing, user-scoped request-bound booking idempotency, provider-enforced same-key charge/refund replay, durable payment and refund lifecycles with guarded transitions, concurrency-safe cancellation, seat resale, expiration, and cryptographically secure tickets;
- transactional outbox events, retrying Kafka publication, idempotent notification consumption, and Flyway-managed PostgreSQL schema evolution;
- request/correlation IDs, production JSON logs, Redis rate limits, Prometheus business metrics, and PostgreSQL/Redis/Kafka readiness indicators;
- Java 21 formatting/build CI and PostgreSQL/Redis Testcontainers coverage for seat contention, financial races, and the full active-event cancellation chain through booking reconciliation, refunds, ticket invalidation, and inventory release.

## Repository structure

```text
eventpass/
├── backend/       Spring Boot API, tests, Dockerfile, and backend environment example
├── frontend/      Frontend workspace
├── .github/       Shared CI workflows
├── compose.yml    Shared local infrastructure and application orchestration
└── *.md           Architecture, API, database, security, testing, and deployment docs
```

## Run locally

1. Install Java 21 and Docker.
2. Copy `backend/.env.example` to `.env` in the repository root and replace `JWT_SECRET` with at least 32 random characters.
3. Start infrastructure: `docker compose up -d postgres redis kafka`.
4. Enter `backend/` and run the API: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.
5. Open Swagger UI at `http://localhost:8080/swagger-ui.html`.

To create the first administrator, set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` before the first startup. Remove those values after the account has been created.

Run backend formatting and tests from `backend/` with `mvn spotless:check verify`. Integration tests use Testcontainers and skip only when Docker is unavailable.

Users register as `CUSTOMER`. Organizer/admin promotion is deliberately an administrative database/bootstrap concern; public self-registration can never elevate roles.

See [ARCHITECTURE.md](ARCHITECTURE.md), [API.md](API.md), [DATABASE.md](DATABASE.md), [SECURITY.md](SECURITY.md), [TESTING.md](TESTING.md), and [DEPLOYMENT.md](DEPLOYMENT.md).
