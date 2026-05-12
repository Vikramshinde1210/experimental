# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM gradle:8.14-jdk21 AS builder
WORKDIR /app

# Copy build files first — Docker caches this layer until build.gradle changes.
# 'copyOtelAgent' runs as part of 'build' and downloads the ~200 MB agent jar.
COPY build.gradle settings.gradle ./
COPY src ./src/
RUN gradle build -x test --no-daemon --parallel

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
FROM amazoncorretto:21-alpine
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar              app.jar
# The agent jar is placed next to the app jar.
# It is loaded by the JVM before the application starts via -javaagent.
COPY --from=builder /app/build/agent/opentelemetry-javaagent.jar otel-agent.jar

EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/app/otel-agent.jar", "-jar", "app.jar"]
