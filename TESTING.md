# Testing

From `backend/`, run `mvn spotless:check verify` with Java 21. Unit tests cover financial idempotency and outbox backoff/failure transitions. Testcontainers applies every Flyway migration to PostgreSQL and starts Redis. Integration coverage verifies domain races, event cancellation, concurrent outbox claiming, failed-row exclusion, and operational recovery making a row immediately claimable again. Docker must be running for infrastructure tests. Testcontainers 1.21.4 is required for current Docker Engine API compatibility.

No performance figures are claimed until k6 scenarios are executed in a representative environment.
