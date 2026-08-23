FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:24-jre-alpine
RUN addgroup -S eventpass && adduser -S eventpass -G eventpass
WORKDIR /app
COPY --from=build /workspace/target/eventpass-backend-*.jar app.jar
USER eventpass:eventpass
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]
