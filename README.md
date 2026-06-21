# experimental

A collection of self-contained proof-of-concept projects covering any technology, language or stac. Each project lives in its own folder and can be run idependently. This repo is language and framework agnostic
## Projects

| Project | What it explores |
|---|---|
| [java-21-virtual-threads-poc](./java-21-virtual-threads-poc) | Project Loom virtual threads vs platform threads — performance comparison under concurrent I/O load using JMeter |
| [kafka-demo](./kafka-demo) | Kafka producer/consumer with Spring Kafka — minimal end-to-end message flow via REST endpoint |
| [otel-observability-poc](./otel-observability-poc) | Full 3-pillar observability (metrics, logs, traces) wired to the Grafana stack via OTel Java Agent, Prometheus, Loki, and Tempo |
| [gcp-tfinfra Terraform Project](./gcp-tfinfra) | Terraform files for creating VM's and network on GCP |
| [pgbpuncer-poc](./pgbpuncer-poc) | PgBouncer connection pooling with PostgreSQL |
| [custom-protocol](./custom-protocol) | Custom protocol over TCP in java |
| [bloom-filter](./bloom-filter) | Bloom filter POC using spring and redis |

## Structure

```
experimental/
├── java-21-virtual-threads-poc/   # Java 21, Spring Boot 4, Gradle
├── kafka-demo/                    # Java 17, Spring Boot 3, Spring Kafka
└── otel-observability-poc/        # Java 21, Spring Boot 4, Docker Compose stack
└── gcp-tfinfra/                   # Terraform, GCP
└── pgbpuncer-poc/                 # PgBouncer + Postgres
└── custom-protocol/               # Java 17 + TCP
└── bloom-filter/                  # Java 17 + Spring + Redis Bloom Filter

```


Each project is independent - refer to the README inside each project for prerequisites and run intructions
