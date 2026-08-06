# experimental

A collection of self-contained proof-of-concept projects covering any technology, language, or stack. Each project lives in its own folder and can be run independently. This repo is language and framework agnostic.

## Projects

| Project | What it explores |
|---|---|
| [java-21-virtual-threads-poc](./java-21-virtual-threads-poc) | Project Loom virtual threads vs platform threads — performance comparison under concurrent I/O load using JMeter |
| [kafka-demo](./kafka-demo) | Kafka producer/consumer with Spring Kafka — minimal end-to-end message flow via REST endpoint |
| [otel-observability-poc](./otel-observability-poc) | Full 3-pillar observability (metrics, logs, traces) wired to the Grafana stack via OTel Java Agent, Prometheus, Loki, and Tempo |
| [gcp-tfinfra](./gcp-tfinfra) | Terraform files for creating VMs and network on GCP |
| [pgbouncer-poc](./pgbouncer-poc) | PgBouncer connection pooling with PostgreSQL |
| [custom-protocol](./custom-protocol) | Custom protocol over TCP in Java |
| [bloom-filter](./bloom-filter) | Bloom filter POC using Spring and Redis |
| [debezium-cdc-lab](./debezium-cdc-lab) | Real-time CDC from PostgreSQL to Kafka using Debezium — Docker Compose stack with pgAdmin and Kafka UI |
| [spring-security](./spring-security) | Multi-module Spring Security POC — Basic Auth, JWT/RBAC, OAuth2 Client, and OIDC (Google) |

## Structure

```
experimental/
├── java-21-virtual-threads-poc/   # Java 21, Spring Boot 4, Gradle
├── kafka-demo/                    # Java 17, Spring Boot 3, Spring Kafka
├── otel-observability-poc/        # Java 21, Spring Boot 4, Docker Compose stack
├── gcp-tfinfra/                   # Terraform, GCP
├── pgbouncer-poc/                 # PgBouncer + Postgres
├── custom-protocol/               # Java 17 + TCP
└── bloom-filter/                  # Java 17 + Spring + Redis Bloom Filter
├── debezium-cdc-lab/              # PostgreSQL CDC → Kafka (Debezium, Docker)
├── spring-security/               # Basic Auth, JWT, OAuth2, OIDC modules
```

Each project is independent — refer to the README inside each project for prerequisites and run instructions.
