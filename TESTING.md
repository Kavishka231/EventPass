# Testing

From `backend/`, run `mvn spotless:check verify` with Java 21. Unit tests cover financial idempotency, the exact six-field version-1 event envelope, and outbox backoff/failure transitions. Testcontainers applies every Flyway migration to PostgreSQL and verifies domain races, event cancellation, concurrent outbox claiming, and recovery. Docker must be running for infrastructure tests. Testcontainers 1.21.4 is required for current Docker Engine API compatibility.

No performance figures are claimed until k6 scenarios are executed in a representative environment.
