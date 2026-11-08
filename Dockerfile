# syntax=docker/dockerfile:1

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 1001 --create-home crawler \
 && mkdir -p /app/data /app/logs \
 && chown -R crawler:crawler /app
WORKDIR /app
COPY --from=build --chown=crawler:crawler /build/target/contacts-crawler.jar app.jar
USER crawler

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
VOLUME ["/app/data", "/app/logs"]

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fs http://localhost:8080/actuator/health || exit 1

# exec-форма: java — PID 1 и получает SIGTERM (graceful shutdown)
ENTRYPOINT ["java", "-jar", "app.jar"]
