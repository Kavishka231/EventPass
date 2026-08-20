# Testing

From `backend/`, run `mvn spotless:check verify` with Java 21. Unit tests cover mock charge/refund outcomes. The Testcontainers integration test applies all Flyway migrations to PostgreSQL, starts Redis, releases 20 virtual-thread customers simultaneously against one event seat, and asserts exactly one booking, payment, sold seat, and ticket. Docker must be running for infrastructure tests. Testcontainers 1.21.4 is required for current Docker Engine API compatibility.

No performance figures are claimed until k6 scenarios are executed in a representative environment.
