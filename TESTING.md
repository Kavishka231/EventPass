# Testing

From `backend/`, run `mvn spotless:check verify` with Java 21. Unit tests cover mock payment outcomes. The Testcontainers integration test starts PostgreSQL and Redis, releases 20 virtual-thread customers simultaneously against one event seat, and asserts exactly one booking, one sold seat, and no duplicates. Docker must be running for infrastructure tests.

No performance figures are claimed until k6 scenarios are executed in a representative environment.
