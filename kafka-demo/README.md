# Kafka Producer/Consumer Demo

A minimal Spring Boot demo showing Kafka producer and consumer using Spring Kafka.

## Stack

| Component | Version |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.5 |
| Spring Kafka | (managed by Spring Boot) |
| Kafka broker | Bitnami Kafka 3.7 (external) |

## Running locally

### 1. Start Kafka

You need a running Kafka broker on `localhost:9093`. A Docker Compose setup for a two-broker Kafka cluster is available separately. Once Kafka is up:

```bash
# Verify broker is reachable
docker exec -it kafka1 kafka-topics.sh --bootstrap-server localhost:9092 --list
```

### 2. Start the app

```bash
./gradlew bootRun
```

The app starts on `http://localhost:8080`.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/kafka/send/{message}` | Publishes `message` to `test-topic` |

```bash
# Send a message
curl -X POST http://localhost:8080/kafka/send/hello-world

# Response: ✅ Message sent: hello-world
```

The consumer listens on `test-topic` and prints received messages to stdout:

```
📥 Received: hello-world
```

## How it works

```
POST /kafka/send/{message}
        │
   KafkaController
        │
   KafkaProducer ──── KafkaTemplate ──► Kafka broker (test-topic)
                                                │
                                        KafkaConsumer (@KafkaListener)
                                                │
                                        stdout: "📥 Received: …"
```

## Configuration

All Kafka settings are in [`src/main/resources/application.yaml`](src/main/resources/application.yaml):

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9093
    consumer:
      group-id: demo-group
      auto-offset-reset: earliest
```

Change `bootstrap-servers` to point at your broker if needed.
