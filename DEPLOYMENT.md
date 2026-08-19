# Deployment

The multi-stage Dockerfile builds on JDK 21 and runs on a non-root JRE 21 Alpine user with a liveness health check. `compose.yml` provides PostgreSQL, Redis, Apache Kafka, and an optional application profile.

Set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `KAFKA_BOOTSTRAP_SERVERS`, and a strong `JWT_SECRET`. Readiness includes dependency indicators; liveness uses only application process state. Persist PostgreSQL independently and terminate TLS at the ingress/load balancer.
