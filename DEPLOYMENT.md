# Deployment

The backend multi-stage Dockerfile at `backend/Dockerfile` builds on JDK 21 and runs on a non-root JRE 21 Alpine user with a liveness health check. Root-level `compose.yml` provides PostgreSQL, Redis, Apache Kafka, and an optional application profile using `backend/` as its build context.

Set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `KAFKA_BOOTSTRAP_SERVERS`, and a cryptographically random `JWT_SECRET` of at least 32 bytes. Set stable deployment-specific `JWT_ISSUER`, `JWT_AUDIENCE`, and `JWT_SIGNING_KEY_ID` values; changing any of them invalidates existing access tokens. Readiness includes dependency indicators; liveness uses only application process state. Persist PostgreSQL independently and terminate TLS at the ingress/load balancer.

Production requires `CORS_ALLOWED_ORIGINS` as a comma-separated list of exact trusted browser origins. Wildcards are rejected because credentialed requests are enabled. Swagger UI and OpenAPI JSON are disabled in the production profile; expose them only through a separately secured non-production environment. Only health probes are anonymous, while other exposed Actuator endpoints require an administrator.

The application provisions its domain and dead-letter topics by default. Production defaults to three replicas; ensure the Kafka cluster has at least three brokers, or deliberately set `KAFKA_TOPIC_REPLICATION_FACTOR` for a smaller environment. Tune `KAFKA_TOPIC_PARTITIONS`, `KAFKA_TOPIC_RETENTION_MS`, `KAFKA_DLT_RETENTION_MS`, `KAFKA_CONSUMER_GROUP`, and `KAFKA_CONSUMER_CONCURRENCY` for workload and recovery requirements. Automatic broker topic creation is disabled so missing or invalid production topology fails startup visibly.

Optional `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` values create the initial administrator only when the email does not exist; remove them afterward. The production profile has no JWT fallback. Outbox publication can be disabled temporarily with `OUTBOX_ENABLED=false`, but normal production operation requires Kafka and the default enabled publisher.
