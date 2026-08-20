# Deployment

The backend multi-stage Dockerfile at `backend/Dockerfile` builds on JDK 21 and runs on a non-root JRE 21 Alpine user with a liveness health check. Root-level `compose.yml` provides PostgreSQL, Redis, Apache Kafka, and an optional application profile using `backend/` as its build context.

Set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `KAFKA_BOOTSTRAP_SERVERS`, and a strong `JWT_SECRET`. Readiness includes dependency indicators; liveness uses only application process state. Persist PostgreSQL independently and terminate TLS at the ingress/load balancer.

Optional `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` values create the initial administrator only when the email does not exist; remove them afterward. The production profile has no JWT fallback. Outbox publication can be disabled temporarily with `OUTBOX_ENABLED=false`, but normal production operation requires Kafka and the default enabled publisher.
