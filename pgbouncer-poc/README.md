# pgBouncer Connection Pooling POC

A self-contained demo that shows why shared PostgreSQL clusters saturate under scale, and how pgBouncer overcomes the limit by multiplexing many client connections onto a small pool of real server connections.

---

## The problem: connections scale multiplicatively

Without a connection pooler every service holds its own connections directly against the database. The total count is:

```
total connections = services × pods × min_idle
```

A concrete example with typical microservice defaults:

| Variable | Value |
|---|---|
| Services sharing a cluster | 9 |
| Pods per service | 6 (3 live + 3 non-live) |
| min-idle per pool | 15 |
| **Total baseline connections** | **9 × 6 × 15 = 810** |
| Cluster limit (`db.r6g.large`) | 1 000 |
| Headroom | 190 |

Under zero load 810 connections are open and idle. Scaling to 12 pods per service (common during traffic spikes or deployments) pushes the total to 1 620 — 62 % over the hard limit. New connections are rejected with:

```
FATAL: remaining connection slots are reserved for non-replication superuser connections
FATAL: sorry, too many clients already
```

The same formula applies even after right-sizing individual pool settings. Because every service counts independently, adding one more service or one more pod immediately increases cluster-wide pressure.

---

## How pgBouncer solves it

pgBouncer sits between the services and Postgres. Services connect to pgBouncer (not Postgres directly). pgBouncer maintains a **small pool of real server connections** and assigns them to client transactions on demand.

```
┌──────────────────────────────────────────────────────────────────────────┐
│  WITHOUT pgBouncer                                                        │
│                                                                           │
│  svc-payment   ──────────────────────────── payments_db  (5 conns)       │
│  svc-payment   ──────────────────────────── ach_db       (5 conns)       │
│  svc-payment   ──────────────────────────── scheduling_db(5 conns)       │
│  svc-ach       ──────────────────────────── payments_db  (5 conns)       │
│  svc-ach       ──────────────────────────── ach_db       (5 conns)       │
│  svc-ach       ──────────────────────────── scheduling_db(5 conns)       │
│  svc-scheduling ─────────────────────────── payments_db  (5 conns)       │
│  svc-scheduling ─────────────────────────── ach_db       (5 conns)       │
│  svc-scheduling ─────────────────────────── scheduling_db(5 conns)       │
│                                                                           │
│  9 pools × 5 idle = 45 real DB connections  →  cluster saturated         │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│  WITH pgBouncer (transaction mode, pool_size = 3)                        │
│                                                                           │
│  svc-payment   ──┐                                                        │
│  svc-ach       ──┼──▶  pgBouncer  ──▶  payments_db   (3 real conns)     │
│  svc-scheduling──┘         │       ──▶  ach_db        (3 real conns)     │
│                            └       ──▶  scheduling_db (3 real conns)     │
│                                                                           │
│  45 client-side connections → 9 real DB connections                      │
│  Cluster headroom is preserved regardless of service or pod count        │
└──────────────────────────────────────────────────────────────────────────┘
```

The key insight: with pgBouncer the formula changes from

```
services × pods × pool_size
```

to

```
databases × server_pool_size   (independent of service or pod count)
```

---

## Pool modes

pgBouncer offers three pool modes. The choice determines what SQL features you can use.

| Mode | When a server connection is assigned | Efficiency | Limitations |
|---|---|---|---|
| **session** | For the entire client session | Low | None — full SQL compatibility |
| **transaction** | Per transaction | High | No `PREPARE`/`EXECUTE`, no `SET` session vars, no advisory locks, no `LISTEN`/`NOTIFY`, no cursors without `WITH HOLD` |
| **statement** | Per SQL statement | Highest | All of the above + multi-statement transactions break |

This POC uses **transaction mode** because it gives the greatest connection reduction. Before adopting it in production, audit your services for the features listed above.

---

## Project structure

```
pgbouncer-poc/
├── docker-compose.yml          # orchestrates postgres, pgbouncer, simulator
├── .env                        # port and credential overrides
│
├── postgres/
│   └── init-dbs.sql            # creates 3 databases + appuser on first start
│
├── pgbouncer/
│   ├── Dockerfile              # alpine + pgbouncer, generates userlist.txt at startup
│   ├── pgbouncer.ini           # pool config: transaction mode, pool_size=3 per db
│   └── entrypoint.sh           # generates md5 userlist.txt from env vars, then starts pgbouncer
│
├── simulator/
│   ├── Dockerfile              # python:3.12-slim
│   ├── requirements.txt        # psycopg2-binary
│   └── simulate.py             # two-scenario demo (direct vs via pgBouncer)
│
├── scripts/
│   ├── demo.sh                 # one-command demo runner
│   ├── check-connections.sh    # live pg_stat_activity query
│   └── show-pgbouncer-stats.sh # pgBouncer SHOW POOLS / SHOW SERVERS
│
└── logs/
    ├── simulate.log            # written by simulate.py (gitignored)
    └── pgbouncer.log           # written by pgBouncer    (gitignored)
```

---

## Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin)
- Ports `5432` and `6432` free on the host (override in `.env` if needed)

> **WSL / existing Postgres**: if you already have a Postgres container running in WSL Docker on port 5432, change `POSTGRES_PORT=5433` in `.env` before starting.

---

## Quick start

```bash
# 1. Clone / navigate to this directory
cd POC/pgbouncer-poc

# 2. Start all services in the background
docker compose up -d

# 3. Wait for postgres and pgbouncer to become healthy (~15 s)
docker compose ps

# 4. Run the demo
./scripts/demo.sh
```

To watch connections change in real time, open a second terminal and run:

```bash
watch -n 2 ./scripts/check-connections.sh
```

---

## What the demo does

`simulate.py` runs two scenarios back-to-back:

### Scenario 1 — Direct connections (the problem)

- Opens 9 connection pools (3 services × 3 databases) pointed directly at Postgres
- Each pool pre-creates 5 connections on start (`minconn=5`)
- Connections needed: 45 | Available (non-superuser): 22
- Expected: pool creation fails partway through with a `too many clients` error

### Scenario 2 — Via pgBouncer (the solution)

- Opens the same 9 pools with the same config — but pointed at pgBouncer:6432
- pgBouncer accepts all 45 client connections
- pgBouncer creates at most 3 real server connections per database = 9 total
- Fires a round of concurrent queries and shows that multiple client pools share the same small set of server PIDs

---

## Expected output (abridged)

```
════════════════════════════════════════════════════════════════
  SCENARIO 1 — Direct connections to PostgreSQL  (the problem)
════════════════════════════════════════════════════════════════

  Postgres  max_connections     = 25
  Connections required          = 45  (9 × 5)
  Available for non-superusers  = 22  (25 − 3 reserved)
  Expected outcome              = SATURATION after ~4 pools

  [baseline — before any pools]  real server connections: 0  |  slots remaining: 24

  ✓  svc-payment → postgres:5432/payments_db
  [after opening pool #1]  real server connections: 5   |  slots remaining: 19
  ✓  svc-payment → postgres:5432/ach_db
  [after opening pool #2]  real server connections: 10  |  slots remaining: 14
  ✓  svc-payment → postgres:5432/scheduling_db
  [after opening pool #3]  real server connections: 15  |  slots remaining: 9
  ✓  svc-ach → postgres:5432/payments_db
  [after opening pool #4]  real server connections: 20  |  slots remaining: 4
  ✗  svc-ach → postgres:5432/ach_db
     Error: FATAL:  remaining connection slots are reserved for non-replication superuser connections
  ✗  svc-ach → postgres:5432/scheduling_db
     Error: FATAL:  sorry, too many clients already
  ✗  svc-scheduling → postgres:5432/payments_db
  ...

  Pools opened successfully : 4/9
  Pools failed              : 5/9

════════════════════════════════════════════════════════════════
  SCENARIO 2 — Via pgBouncer  (the solution)
════════════════════════════════════════════════════════════════

  pgBouncer  pool_mode          = transaction
  pgBouncer  pool_size per db   = 3
  Max real server connections    = 9   (3 databases × 3)

  ✓  svc-payment    → pgbouncer:6432/payments_db
  ✓  svc-payment    → pgbouncer:6432/ach_db
  ✓  svc-payment    → pgbouncer:6432/scheduling_db
  ✓  svc-ach        → pgbouncer:6432/payments_db
  ... (all 9 succeed)

  Pools opened successfully : 9/9

  [after opening all client pools]  real server connections: 3  |  slots remaining: 21
  ↑ Only 3 real server connections despite 9 client pools (45 client-side connections).

  Firing 9 concurrent queries (one per pool)...
  Queries completed : 9
  Unique server PIDs: 3  ← server connections shared across all client pools
```

---

## Inspecting pgBouncer directly

```bash
# Pool stats (server conns, client conns, queue depth per database)
./scripts/show-pgbouncer-stats.sh

# Or connect to the admin console manually
docker compose exec pgbouncer \
  sh -c 'PGPASSWORD=$PGBOUNCER_PASSWORD psql -h 127.0.0.1 -p 6432 -U $PGBOUNCER_USER pgbouncer'

# Inside the console:
pgbouncer=# SHOW POOLS;
pgbouncer=# SHOW SERVERS;
pgbouncer=# SHOW CLIENTS;
pgbouncer=# SHOW STATS;
```

Key columns in `SHOW POOLS`:

| Column | Meaning |
|---|---|
| `cl_active` | Client connections currently assigned a server connection |
| `cl_waiting` | Client connections waiting for a free server slot |
| `sv_active` | Server connections currently in use |
| `sv_idle` | Server connections sitting in the pool, ready to be used |
| `sv_used` | Server connections recently released, not yet checked |
| `maxwait` | Time the longest-waiting client has been queued |

---

## pgBouncer trade-offs

### What works in transaction mode

- Regular `SELECT`, `INSERT`, `UPDATE`, `DELETE`
- Explicit transactions (`BEGIN` / `COMMIT` / `ROLLBACK`)
- Standard driver connection pools (HikariCP, Tomcat JDBC, psycopg2)

### What breaks in transaction mode

| Feature | Why it breaks |
|---|---|
| `PREPARE` / `EXECUTE` (SQL-level) | Prepared statements are tied to a server session; the next transaction may land on a different server connection |
| `SET search_path = ...` or any `SET` command | Session-level settings are reset when the server connection is returned to the pool |
| `SELECT pg_advisory_lock(...)` | Advisory locks are session-scoped; pooling a session across clients releases the lock unexpectedly |
| `LISTEN` / `NOTIFY` | `LISTEN` registers on a specific server connection; notifications go to that connection, not the client |
| `DECLARE CURSOR` without `WITH HOLD` | Cursors exist on the server session; not available after the transaction ends |

### Mitigation options

- **Session mode**: zero breakage, but connection savings are much lower (one server connection is held for the entire client session lifetime)
- **Disable prepared statements at the driver level**: HikariCP `prepareThreshold=0`, psycopg2 doesn't use protocol-level prepared statements by default — no change needed
- **Avoid `SET` in application code**: pass settings in the JDBC/DSN URL or connection startup parameters (pgBouncer passes startup parameters through)

---

## Adjusting pool sizes

All pool settings are in `pgbouncer/pgbouncer.ini`.  The key knobs:

```ini
default_pool_size = 3    ; real server connections per (database, user) pair
min_pool_size     = 1    ; keep this many connections open even when idle
reserve_pool_size = 1    ; extra connections allowed during burst
max_client_conn   = 200  ; total client connections pgBouncer accepts
```

Rule of thumb for sizing `default_pool_size`:

```
pool_size ≈ (target_tps × avg_query_time_seconds) + headroom
```

For most OLTP workloads a pool of 10–25 server connections per database handles hundreds of concurrent clients.

---

## Connecting your own WSL Postgres

If you want to point pgBouncer at an existing Postgres running in WSL Docker instead of the one in this compose file:

1. In `.env`, set:
   ```
   POSTGRES_PORT=5433   # avoid port clash with WSL postgres on 5432
   ```
2. In `pgbouncer/pgbouncer.ini`, change the `host` in each `[databases]` entry to the WSL host IP (typically `172.x.x.x` — check with `ip route` inside WSL).
3. Rebuild: `docker compose build pgbouncer && docker compose up -d pgbouncer`

---

## Teardown

```bash
# Stop containers, remove network
docker compose down

# Also remove the postgres data volume (fresh start next time)
docker compose down --volumes
```

---

## When to use pgBouncer vs alternatives

| Approach | Best for | Notes |
|---|---|---|
| **Right-size pool config** (reduce min-idle) | Quick win, no new infra | Still scales with services × pods; doesn't help as cluster grows |
| **pgBouncer** | Enforcing a hard connection cap at the cluster level | Ops overhead; transaction mode has SQL limitations |
| **AWS RDS Proxy** | AWS RDS / Aurora deployments | Managed, no ops, supports IAM auth; costs extra |
| **HikariCP tuning** (maxPoolSize, idleTimeout) | App-level reduction, fine-grained per service | Each team must act; no cluster-level enforcement |

The most robust outcome is **right-sizing + pgBouncer** together: right-sizing gives teams control over their own footprint; pgBouncer provides a cluster-level safety net so that misconfigured or new services cannot saturate the shared cluster.
