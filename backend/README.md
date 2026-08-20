# EventPass backend

This workspace contains the Java 21 Spring Boot API, Flyway migrations, unit and integration tests, Maven build, backend environment template, and container image definition.

From this directory:

```shell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn spotless:check verify
```

Start PostgreSQL, Redis, and Kafka from the repository root with `docker compose up -d postgres redis kafka`.
