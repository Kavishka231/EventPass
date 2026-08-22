# Testing

From `backend/`, run `mvn spotless:check verify` with Java 21. Unit tests cover financial idempotency, the exact six-field version-1 event envelope, outbox backoff/failure transitions, failed Kafka delivery, and successful retry. Testcontainers applies every Flyway migration to PostgreSQL and verifies domain races, event cancellation, competing outbox publishers, operational recovery, and duplicate consumer delivery. Docker must be running for infrastructure tests. Testcontainers 1.21.4 is required for current Docker Engine API compatibility.

No performance figures are claimed until k6 scenarios are executed in a representative environment.
