# Debezium CDC Lab — PostgreSQL → Kafka

A self-contained Docker lab that streams PostgreSQL row-level changes to Kafka using [Debezium](https://debezium.io/) and the `pgoutput` logical replication plugin.

Inspired by:

- [Real-Time CDC from PostgreSQL to Kafka using Debezium](https://medium.com/towards-data-engineering/real-time-change-data-capture-from-postgresql-to-kafka-using-debezium-35c31b704621)
- [Beyond the Basics of Debezium for PostgreSQL — Part 1](https://medium.com/@arijit.mazumdar/beyond-the-basics-of-debezium-for-postgresql-part-1-d1c6952ae110)

For a full Medium-ready write-up (including the dual-write problem, WAL deep dive, and publication/slot setup), see [docs/MEDIUM_BLOG.md](./docs/MEDIUM_BLOG.md).

## Architecture

```
PostgreSQL (WAL + logical replication)
        │
        ▼
Debezium Connect (Kafka Connect)
        │
        ▼
Apache Kafka topics  ──►  Kafka UI
        │
   pgAdmin (optional DB UI)
```

| Service | URL / Port | Purpose |
|---|---|---|
| PostgreSQL | `localhost:5432` | Source database |
| pgAdmin | http://localhost:5050 | DB management |
| Debezium Connect | http://localhost:8083 | Kafka Connect REST API |
| Kafka UI | http://localhost:8080 | Topics and message inspection |
| Kafka broker | `localhost:9092` | Event streaming |

## Prerequisites

- Docker Desktop
- PowerShell (Windows) or curl/bash (Linux/macOS)
- ~4 GB free RAM

## Quick start

```powershell
# 1. Start the stack
.\scripts\start.ps1

# 2. Register the Debezium connector
.\scripts\register-connector.ps1

# 3. Verify connector status
.\scripts\verify.ps1
```

Or manually:

```powershell
docker compose up -d
Invoke-RestMethod `
    -Uri http://localhost:8083/connectors `
    -Method Post `
    -ContentType "application/json" `
    -InFile connector/postgres-connector.json
```

Expected connector status:

```json
{
  "connector": { "state": "RUNNING" },
  "tasks": [{ "state": "RUNNING" }]
}
```

If the connector is not running:

```powershell
docker logs debezium
```

## Credentials

| Service | User | Password | Database |
|---|---|---|---|
| PostgreSQL | `postgres` | `postgres123` | `test` |
| pgAdmin | `admin@example.com` | `admin123` | — |

## Folder structure

```
debezium-cdc-lab/
├── docker-compose.yml
├── connector/
│   └── postgres-connector.json
├── postgres/
│   ├── init.sql              # runs on first Postgres start
│   └── sample.sql            # optional seed data
├── scripts/
│   ├── start.ps1
│   ├── register-connector.ps1
│   └── verify.ps1
└── docs/
    ├── MEDIUM_BLOG.md
    └── images/               # add your lab screenshots here
```

## Hands-on: CDC walkthrough

### Step 1 — Verify the connector is running

```powershell
Invoke-RestMethod `
    -Method GET `
    -Uri "http://localhost:8083/connectors/postgres-connector/status"
```

![Connector running](docs/images/02-connector-running.png)

### Step 2 — Create or use the sample table

On first startup, `postgres/init.sql` creates a `customers` table. To connect manually:

```powershell
docker exec -it postgresdb psql -U postgres -d test
```

```sql
CREATE TABLE customers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

\dt
```

![pgAdmin customers table](docs/images/05-pgadmin-customers-table.png)

### Step 3 — Insert initial data

```sql
INSERT INTO customers (name, email) VALUES
    ('John Doe', 'john@test.com'),
    ('Jane Doe', 'jane@test.com');

SELECT * FROM customers;
```

Expected output:

```
 id |   name   |     email
----+----------+---------------
  1 | John Doe | john@test.com
  2 | Jane Doe | jane@test.com
```

Or run the seed script:

```powershell
docker exec -i postgresdb psql -U postgres -d test < postgres/sample.sql
```

### Step 4 — Check Kafka topics

Open Kafka UI: http://localhost:8080

Go to **Topics**. You should see:

```
postgres.public.customers
```

Topic naming rule:

```
<topic.prefix>.<schema>.<table>
```

With `topic.prefix=postgres`, the topic becomes `postgres.public.customers`.

![Kafka UI topics](docs/images/03-kafka-ui-topics.png)

### Step 5 — Verify INSERT CDC event

In Kafka UI:

```
Topics → postgres.public.customers → Messages
```

Example event:

```json
{
  "before": null,
  "after": {
    "id": 1,
    "name": "John Doe",
    "email": "john@test.com",
    "created_at": 1730000000000
  },
  "source": {
    "db": "test",
    "table": "customers"
  },
  "op": "c"
}
```

| `op` | Meaning |
|---|---|
| `c` | CREATE / INSERT |
| `u` | UPDATE |
| `d` | DELETE |
| `r` | READ (snapshot) |

![INSERT event in Kafka UI](docs/images/04-kafka-ui-insert-event.png)

### Step 6 — UPDATE and DELETE events

```sql
UPDATE customers SET email = 'john.doe@new.com' WHERE id = 1;
DELETE FROM customers WHERE id = 2;
```

Watch Kafka UI for events with `"op": "u"` and `"op": "d"`.

![UPDATE and DELETE events](docs/images/06-update-delete-events.png)

## Under the hood (PostgreSQL side)

From [Arijit Mazumdar's deep dive](https://medium.com/@arijit.mazumdar/beyond-the-basics-of-debezium-for-postgresql-part-1-d1c6952ae110):

- **WAL** — all changes are written to the Write-Ahead Log before commit
- **LSN** — position in WAL, similar to a Kafka consumer offset
- **Publication** — which tables/schemas to stream (`debezium_publication`)
- **Replication slot** — guarantees no change is lost if Debezium restarts (`debezium_slot`)

Monitor in `psql`:

```sql
SELECT slot_name, active, confirmed_flush_lsn
FROM pg_replication_slots;

SELECT * FROM pg_publication_tables
WHERE pubname = 'debezium_publication';
```

## Connector config highlights

```json
{
  "plugin.name": "pgoutput",
  "slot.name": "debezium_slot",
  "publication.name": "debezium_publication",
  "publication.autocreate.mode": "filtered",
  "schema.include.list": "public",
  "table.include.list": "public.*",
  "topic.prefix": "postgres"
}
```

Postgres starts with:

```
wal_level=logical
max_replication_slots=10
max_wal_senders=10
```

## Screenshots

Copy your lab screenshots into `docs/images/` using these names:

| File | What to capture |
|---|---|
| `01-docker-containers.png` | Docker Desktop with all 5 containers running |
| `02-connector-running.png` | Connector status showing `RUNNING` |
| `03-kafka-ui-topics.png` | Kafka UI topic list |
| `04-kafka-ui-insert-event.png` | INSERT event payload in Kafka UI |
| `05-pgadmin-customers-table.png` | pgAdmin or psql showing `customers` rows |
| `06-update-delete-events.png` | UPDATE and DELETE events in Kafka UI |

## Troubleshooting

| Symptom | Fix |
|---|---|
| Connector `FAILED` | Run `docker logs debezium` and verify Postgres credentials |
| No topic created | Table must exist after connector starts, or restart the connector |
| Slot inactive | Re-register connector; inspect `pg_replication_slots` |
| WAL disk growth | Ensure Debezium is running; stale slots retain WAL |

## Cleanup

```powershell
docker compose down -v
```

## Related projects in this repo

- [kafka-demo](../kafka-demo) — Spring Kafka producer/consumer
- [spring-security](../spring-security) — JWT, OAuth2, and OIDC patterns
