# EventPass backend

EventPass is a Java 21/Spring Boot modular monolith for secure, concurrency-safe event ticket booking. The first milestone supports registration, JWT login/refresh/logout, published-event browsing, event seat inventory, idempotent booking, sandbox payment, cancellation, and digital ticket retrieval.

## Run locally

1. Install Java 21 and Docker.
2. Copy `.env.example` to `.env` and replace `JWT_SECRET` with at least 32 random characters.
3. Start infrastructure: `docker compose up -d postgres redis kafka`.
4. Run the API: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.
5. Open Swagger UI at `http://localhost:8080/swagger-ui.html`.

Run formatting and tests with `mvn spotless:check verify`. Integration tests use Testcontainers and skip only when Docker is unavailable.

Users register as `CUSTOMER`. Organizer/admin promotion is deliberately an administrative database/bootstrap concern; public self-registration can never elevate roles.

See [ARCHITECTURE.md](ARCHITECTURE.md), [API.md](API.md), [DATABASE.md](DATABASE.md), [SECURITY.md](SECURITY.md), [TESTING.md](TESTING.md), and [DEPLOYMENT.md](DEPLOYMENT.md).
