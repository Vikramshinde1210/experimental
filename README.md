# experimental

A monorepo of self-contained Java/Spring Boot proof-of-concept projects. Each project lives in its own folder, has its own Gradle build, and can be run independently.

## Projects

| Project | What it explores |
|---|---|
| [java-21-virtual-threads-poc](./java-21-virtual-threads-poc) | Project Loom virtual threads vs platform threads — performance comparison under concurrent I/O load using JMeter |
| [kafka-demo](./kafka-demo) | Kafka producer/consumer with Spring Kafka — minimal end-to-end message flow via REST endpoint |
| [otel-observability-poc](./otel-observability-poc) | Full 3-pillar observability (metrics, logs, traces) wired to the Grafana stack via OTel Java Agent, Prometheus, Loki, and Tempo |

## Structure

```
experimental/
├── java-21-virtual-threads-poc/   # Java 21, Spring Boot 4, Gradle
├── kafka-demo/                    # Java 17, Spring Boot 3, Spring Kafka
└── otel-observability-poc/        # Java 21, Spring Boot 4, Docker Compose stack
```

## Running a project

Each project is independent — `cd` into it and use its own Gradle wrapper:

```bash
cd java-21-virtual-threads-poc
./gradlew bootRun
```

Refer to the README inside each project for prerequisites (e.g. Kafka broker, Docker for the OTel stack).
