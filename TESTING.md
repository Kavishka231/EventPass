# Testing

From `backend/`, run `mvn spotless:check verify` with Java 21. Unit tests cover mock financial outcomes and provider idempotency. Testcontainers applies all Flyway migrations to PostgreSQL and starts Redis. Integration coverage verifies booking/payment/refund races, event cancellation orchestration, and two concurrent outbox transactions competing for one row: the first holds its claim while `SKIP LOCKED` makes the second receive no row. Docker must be running for infrastructure tests. Testcontainers 1.21.4 is required for current Docker Engine API compatibility.

No performance figures are claimed until k6 scenarios are executed in a representative environment.
