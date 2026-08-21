# Testing

From `backend/`, run `mvn spotless:check verify` with Java 21. Unit tests cover mock charge/refund outcomes, concurrent provider-key replay with exactly one financial execution, and changed-payload rejection. Testcontainers applies all Flyway migrations to PostgreSQL and starts Redis. Integration coverage verifies booking/payment/refund races, cancelled-event sales barriers, multi-booking refund reconciliation, and immediate ticket invalidation even when an event refund fails. Docker must be running for infrastructure tests. Testcontainers 1.21.4 is required for current Docker Engine API compatibility.

No performance figures are claimed until k6 scenarios are executed in a representative environment.
