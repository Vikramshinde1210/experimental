# Redis Bloom Filter POC

A Spring Boot proof-of-concept that uses **RedisBloom** (`BF.*` commands) as a fast pre-filter before hitting an H2 database. The goal is to show how a Bloom filter speeds up "does this user exist?" checks, how false positives behave, and how to inspect filter stats.

---

## What is a Bloom Filter?

A Bloom filter is a **space-efficient probabilistic data structure** that answers one question:

> *"Might this item be in the set?"*

It uses a fixed-size bit array and multiple hash functions. When you insert an item, it sets several bits. When you check an item, it verifies those same bits.

### Guarantees

| Result | Meaning |
|--------|---------|
| **Not present** | Item is **definitely NOT** in the set (100% certain) |
| **Maybe present** | Item **might** be in the set — could be a **false positive** |

There are **no false negatives**: if an item was inserted, the filter will always say "maybe present."

### How it works (simplified)

```
Insert "user1@gmail.com"
  → hash1 → bit 3 = 1
  → hash2 → bit 17 = 1
  → hash3 → bit 42 = 1

Check "user1@gmail.com"
  → all bits (3, 17, 42) are 1 → MAYBE PRESENT ✓

Check "unknown@gmail.com"
  → bit 9 is 0 → DEFINITELY NOT PRESENT ✓
```

### Where Bloom Filters are useful

- **Database query avoidance** — skip expensive lookups when an email/username definitely doesn't exist (this POC)
- **Web crawlers** — track visited URLs without storing every URL in memory
- **Cache filtering** — avoid cache misses for keys that were never stored
- **Distributed systems** — CDNs, ad-tech, fraud detection, spell-check dictionaries
- **Message queues** — deduplication at scale (e.g. Kafka, Redis streams)

### Trade-offs

| Pros | Cons |
|------|------|
| Very fast O(k) lookups | Cannot delete items (in basic form) |
| Tiny memory footprint | False positives possible (tunable) |
| No false negatives | Cannot list all items |
| Scales to billions of items | Needs a backing store to confirm positives |

---

## Architecture

```
Client Request
      │
      ▼
┌─────────────────┐
│  UserController │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     BF.EXISTS      ┌──────────────┐
│   UserService   │ ─────────────────► │ Redis Bloom  │
└────────┬────────┘                    │ (usersBloom) │
         │                              └──────────────┘
         │  if "maybe present"
         ▼
┌─────────────────┐
│   H2 Database   │  ← confirm true match or detect false positive
└─────────────────┘
```

---

## Prerequisites

- Java 17+
- Docker (for Redis with Bloom module)

> **Important:** You must use **Redis Stack** (includes RedisBloom). Plain `redis:latest` does **not** support `BF.*` commands.

---

## Quick Start

### 1. Start Redis with Bloom support

```cmd
docker run -d --name redis-bloom -p 6379:6379 redis/redis-stack-server:latest
```

Verify Bloom commands work:

```cmd
docker exec redis-bloom redis-cli BF.RESERVE test 0.01 1000
docker exec redis-bloom redis-cli BF.ADD test "hello"
docker exec redis-bloom redis-cli BF.EXISTS test "hello"
```

Expected output: `1` (maybe present).

### 2. Run the application

```cmd
gradlew bootRun
```

On startup, the app:
1. Creates/resets the `usersBloom` filter in Redis
2. Loads all existing users from H2 into the Bloom filter

### 3. Load sample data

```cmd
curl -X POST "http://localhost:8080/users/load?count=100"
```

### 4. Search for a user

Use the **query parameter** endpoint (avoids `@` encoding issues in URLs):

```cmd
curl "http://localhost:8080/users/search?email=user1@gmail.com"
```

Example response:

```json
{
  "email": "user1@gmail.com",
  "bloomSaysPresent": true,
  "result": "FOUND_IN_DB",
  "dbChecked": true,
  "message": "Email exists in database"
}
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/users/search?email=` | Look up user via Bloom filter + DB |
| `POST` | `/users` | Create a user (saves to DB + Bloom filter) |
| `POST` | `/users/load?count=100` | Load sample users (clears DB first) |
| `GET` | `/users/bloom/stats` | Bloom filter statistics (`BF.INFO`) |
| `POST` | `/users/bloom/sync` | Re-sync Bloom filter from H2 database |
| `GET` | `/users/bloom/benchmark?iterations=10000` | Compare Bloom vs DB lookup speed |
| `GET` | `/users/bloom/false-positives?samples=10000` | Measure false positive rate |

---

## How to Check Speed

```cmd
curl "http://localhost:8080/users/bloom/benchmark?iterations=10000"
```

Example response:

```json
{
  "iterations": 10000,
  "bloomTotalMs": 245.3,
  "bloomAvgMicros": 24.5,
  "dbTotalMs": 1820.7,
  "dbAvgMicros": 182.0,
  "speedupFactor": 7.4
}
```

Bloom lookups are typically **5–20× faster** than DB queries because they are in-memory bit checks with no disk I/O.

---

## How to See False Positives

False positives occur when the Bloom filter says "maybe present" but the item is **not** in the database.

### Option 1 — API (recommended)

```cmd
curl "http://localhost:8080/users/bloom/false-positives?samples=10000"
```

Example response:

```json
{
  "samplesTested": 10000,
  "falsePositives": 98,
  "observedFalsePositiveRate": 0.0098,
  "configuredErrorRate": 0.01,
  "exampleFalsePositives": ["fp-123456789@not-in-db.com"],
  "note": "False positives are expected. Bloom filter never produces false negatives for inserted items."
}
```

### Option 2 — Manual search

Search for an email that was never inserted:

```cmd
curl "http://localhost:8080/users/search?email=never-added@example.com"
```

If `result` is `DEFINITELY_NOT_PRESENT`, the Bloom filter correctly skipped the DB.

If `result` is `FALSE_POSITIVE`, the Bloom filter said maybe but DB confirmed absence.

---

## How to Get Stats

```cmd
curl "http://localhost:8080/users/bloom/stats"
```

Example response:

```json
{
  "Capacity": "10000",
  "Size": "9586",
  "Number of filters": "1",
  "Number of items inserted": "100",
  "Expansion rate": "2",
  "databaseUserCount": "100",
  "bloomKeyPresent": "true"
}
```

| Field | Meaning |
|-------|---------|
| **Capacity** | Expected max items before expansion |
| **Size** | Memory used (bits) |
| **Number of items inserted** | Items added via `BF.ADD` |
| **databaseUserCount** | Users in H2 (should match inserted count) |

Directly via Redis CLI:

```cmd
docker exec redis-bloom redis-cli BF.INFO usersBloom
```

---

## Understanding Lookup Results

| `result` | Meaning | DB queried? |
|----------|---------|-------------|
| `DEFINITELY_NOT_PRESENT` | Bloom filter says no — skip DB | No |
| `FOUND_IN_DB` | Bloom said maybe, DB confirmed | Yes |
| `FALSE_POSITIVE` | Bloom said maybe, DB says no | Yes |

---

## Configuration

`application.properties`:

```properties
redis.url=redis://localhost:6379
bloom.error-rate=0.01
bloom.capacity=10000
```

| Property | Default | Description |
|----------|---------|-------------|
| `redis.url` | `redis://localhost:6379` | Redis connection URL |
| `bloom.error-rate` | `0.01` | Target false positive rate (1%) |
| `bloom.capacity` | `10000` | Expected number of items |

### Why specify the error rate?

The error rate is passed to `BF.RESERVE` when the Bloom filter is created. It tells Redis the **maximum false positive rate** you are willing to accept once the filter reaches its expected capacity.

```
BF.RESERVE usersBloom 0.01 10000
                      ↑         ↑
                 error rate   capacity
                 (1% FP)      (10k items)
```

A Bloom filter always has a trade-off between **memory** and **accuracy**:

| Error rate | False positives | Memory usage | Best for |
|------------|-----------------|--------------|----------|
| `0.001` (0.1%) | Very rare | Higher | Critical lookups where extra DB hits are costly |
| `0.01` (1%) | Low | Moderate | General-purpose pre-filtering (default in this POC) |
| `0.1` (10%) | Frequent | Lower | High-volume checks where memory is tight and DB can absorb extra queries |

**Why it matters in this POC:**

When the Bloom filter returns "maybe present" for an email that is not in the database, the app still queries H2 to confirm — that is a **false positive**. A higher error rate means more of these unnecessary DB lookups; a lower error rate means fewer, but the filter uses more Redis memory.

The error rate should be chosen based on:

1. **How costly a DB lookup is** — remote or slow databases benefit from a lower error rate
2. **How much Redis memory you can allocate** — lower error rates require a larger bit array
3. **Expected number of items** — set `bloom.capacity` close to your real user count so the error rate stays accurate

You can verify the configured rate against observed behaviour using:

```cmd
curl "http://localhost:8080/users/bloom/false-positives?samples=20000"
```

The response includes both `configuredErrorRate` and `observedFalsePositiveRate` so you can compare them (observed rate approaches configured rate as the filter fills up).

---

## Redis Bloom Commands Used

| Command | Purpose |
|---------|---------|
| `BF.RESERVE key error_rate capacity` | Create filter |
| `BF.ADD key item` | Insert item (returns 1 if new, 0 if already added) |
| `BF.EXISTS key item` | Check if item might exist (1 = maybe, 0 = definitely not) |
| `BF.INFO key` | Get filter statistics |

---

## Project Structure

```
src/main/java/com/example/bloom_filter/
├── BloomFilterApplication.java
├── config/
│   ├── BloomInitializer.java    # Syncs Bloom filter on startup
│   └── RedisConfig.java
├── controller/
│   └── UserController.java      # REST endpoints
├── entity/
│   └── User.java
├── repository/
│   └── UserRepository.java
└── service/
    ├── BloomFilterService.java  # All Redis Bloom logic
    ├── RedisBloomCommand.java   # Lettuce command wrapper
    └── UserService.java
```

---

## Tech Stack

- Spring Boot 4.1
- Lettuce (Redis client)
- Redis Stack / RedisBloom module
- H2 Database (file-based persistence)
- Lombok

---

## Tips for Testing

```cmd
REM Load 1000 users
curl -X POST "http://localhost:8080/users/load?count=1000"

REM Check existing user
curl "http://localhost:8080/users/search?email=user500@gmail.com"

REM Check non-existing user
curl "http://localhost:8080/users/search?email=ghost@nowhere.com"

REM Benchmark speed
curl "http://localhost:8080/users/bloom/benchmark?iterations=50000"

REM Measure false positives
curl "http://localhost:8080/users/bloom/false-positives?samples=50000"

REM View stats
curl "http://localhost:8080/users/bloom/stats"
```
