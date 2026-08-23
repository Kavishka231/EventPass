# EventPass

EventPass is organized as a monorepo with separate backend and frontend workspaces. The Java 21/Spring Boot backend supports registration, JWT login/refresh/logout, published-event browsing, event seat inventory, idempotent booking, sandbox payment, cancellation, and digital ticket retrieval. The frontend workspace is reserved for the upcoming web application.

## Backend milestone status

The backend now includes:

- secure customer registration, short-lived issuer/audience/type-scoped JWT access tokens, rotating hashed refresh-token families with reuse detection, logout, account-state enforcement, and environment-only administrator bootstrap;
- administrator user/role/statistics APIs with self-change and last-active-admin protection, venue management, physical seat creation with capacity checks, organizer-owned events, immutable published schedules/venues, guarded resumable cancellation with immediate ticket invalidation, automatic booking refunds and inventory release, and per-event pricing/blocking;
- Redis TTL locks plus PostgreSQL row and advisory locks, optimistic versions, server-side pricing, user-scoped request-bound booking idempotency, provider-enforced same-key charge/refund replay, durable payment and refund lifecycles with explicit reconciliation of ambiguous provider or database-finalization outcomes, concurrency-safe cancellation, multi-worker-safe expiration claiming, seat resale, and cryptographically secure tickets;
- versioned event envelopes, transactional outbox events with PostgreSQL `SKIP LOCKED` claiming, persisted exponential retries, explicitly provisioned Kafka topics, bounded consumer retries with dead-letter topics, atomic idempotent customer-notification creation, durable delivery state, and authenticated read APIs;
- a uniform API error contract, explicit CORS and security headers, request/correlation IDs, production JSON logs, fail-open Redis rate limits for transient Redis data-access failures, permissioned Prometheus metrics for booking/payment/refund outcomes, cancellation, expiration, outbox backlog, Kafka publication failures, and notification failures, plus PostgreSQL/Redis/Kafka readiness indicators;
- bounded, sortable pagination for event, booking, ticket, administrator-user, and notification collections;
- projection-based booking and ticket history queries with bulk seat lookup and matching PostgreSQL indexes, avoiding page-size-dependent lazy-loading queries;
- HTTP contract integration coverage for booking response bodies and statuses, validation, authorization, bounded pagination, and standardized errors;
- authentication API integration coverage for registration, duplicate email handling, login, inactive accounts, refresh rotation, logout, and token replay;
- event and inventory integration coverage for venues, capacity, physical seats, organizer ownership, publication, pricing, and blocking;
- expanded booking lifecycle coverage for success, durable payment failure, expiration, cancellation, seat resale, ticket generation, and idempotency;
- an end-to-end register/login/event-browse/seat-selection/booking/payment/ticket retrieval test through the public HTTP API;
- Java 21 formatting/build CI plus dependency-change review, CodeQL analysis, Trivy dependency/configuration/secret and container scanning, downloadable CycloneDX image SBOMs, and weekly Maven/Actions/base-image update checks;
- production startup enforcement for authenticated TLS Redis/Kafka connections, bounded client timeouts/backoff, and security-aware Kafka readiness checks;
- PostgreSQL custom-format backup, checksum/full-restore verification, guarded transactional restore, and prefix-scoped retention tooling with an operator recovery runbook;
- k6 performance scenarios for authentication, event/seat browsing, concurrent customer booking, and controlled seat contention with throughput, latency, conflict, and error measurements;
- PostgreSQL/Redis Testcontainers coverage for authorization, suspended/invalid sessions, seat contention, competing expiration workers, financial/admin races, competing outbox publishers, duplicate event delivery, resumable event cancellation, and the full active-event cancellation chain.

## Repository structure

```text
eventpass/
├── backend/       Spring Boot API, tests, Dockerfile, and backend environment example
├── frontend/      Frontend workspace
├── .github/       Shared CI workflows
├── ops/           Production backup, recovery, and operational tooling
├── performance/   k6 load scenarios and performance-test runbooks
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

The `backend-security` GitHub Actions workflow runs on feature branches, pull requests, `main`, a weekly schedule, and manual dispatch. High or critical findings fail the relevant dependency, repository, or container gate; each container run retains a CycloneDX SBOM artifact for 30 days. CodeQL result upload requires GitHub code scanning to be available for the repository.

Users register as `CUSTOMER`. Organizer/admin promotion is deliberately an administrative database/bootstrap concern; public self-registration can never elevate roles.

See [ARCHITECTURE.md](ARCHITECTURE.md), [API.md](API.md), [DATABASE.md](DATABASE.md), [SECURITY.md](SECURITY.md), [TESTING.md](TESTING.md), and [DEPLOYMENT.md](DEPLOYMENT.md).
