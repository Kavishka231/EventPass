# EventPass

EventPass is organized as a monorepo with separate backend and frontend workspaces. The Java 21/Spring Boot backend supports registration, JWT login/refresh/logout, published-event browsing, event seat inventory, idempotent booking, sandbox payment, cancellation, and digital ticket retrieval. The frontend workspace is reserved for the upcoming web application.

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

Run backend formatting and tests from `backend/` with `mvn spotless:check verify`. Integration tests use Testcontainers and skip only when Docker is unavailable.

Users register as `CUSTOMER`. Organizer/admin promotion is deliberately an administrative database/bootstrap concern; public self-registration can never elevate roles.

See [ARCHITECTURE.md](ARCHITECTURE.md), [API.md](API.md), [DATABASE.md](DATABASE.md), [SECURITY.md](SECURITY.md), [TESTING.md](TESTING.md), and [DEPLOYMENT.md](DEPLOYMENT.md).
