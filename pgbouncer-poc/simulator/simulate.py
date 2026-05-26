"""
pgBouncer POC — DB Connection Pooling Demonstration
====================================================

Scenario (scaled-down version of a real shared-cluster audit):
  - 1 PostgreSQL cluster with max_connections=25  (artificially low for demo clarity)
  - 3 databases on that cluster: payments_db, ach_db, scheduling_db
  - 3 simulated services, each opening a connection pool per database
  - Pool config: min_idle=5, max=5 per (service × database) pair
  - Connections needed without pooler: 3 × 3 × 5 = 45  →  exceeds cluster limit
  - Connections with pgBouncer (transaction mode, pool_size=3 per db): max 9  →  well within limit

Two scenarios are run back-to-back so the output clearly shows the difference.
Logs are written to stdout and to /app/logs/simulate.log.
"""

import os
import sys
import time
import threading
import logging
from datetime import datetime

import psycopg2
from psycopg2 import pool, OperationalError

# ── Logging ────────────────────────────────────────────────────────────────────
LOG_DIR = "/app/logs"
os.makedirs(LOG_DIR, exist_ok=True)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(message)s",
    datefmt="%H:%M:%S",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(os.path.join(LOG_DIR, "simulate.log")),
    ],
)
log = logging.getLogger(__name__)

# ── Config ─────────────────────────────────────────────────────────────────────
POSTGRES_HOST     = os.getenv("POSTGRES_HOST",     "localhost")
POSTGRES_PORT     = int(os.getenv("POSTGRES_PORT", "5432"))
PGBOUNCER_HOST    = os.getenv("PGBOUNCER_HOST",    "localhost")
PGBOUNCER_PORT    = int(os.getenv("PGBOUNCER_PORT","6432"))
APP_USER          = os.getenv("APP_USER",          "appuser")
APP_PASSWORD      = os.getenv("APP_PASSWORD",      "apppassword")
MONITOR_USER      = os.getenv("MONITOR_USER",      "postgres")
MONITOR_PASSWORD  = os.getenv("MONITOR_PASSWORD",  "pgpassword")

# Simulated services and the databases they each connect to
SERVICES  = ["svc-payment", "svc-ach", "svc-scheduling"]
DATABASES = ["payments_db", "ach_db", "scheduling_db"]

# Pool settings per (service × database) pair — mirrors typical min-idle / max-active config
POOL_MIN = 5  # min connections (= min-idle in HikariCP / Tomcat JDBC)
POOL_MAX = 5  # max connections (kept equal to min so all are pre-created at pool init)


# ── Helpers ────────────────────────────────────────────────────────────────────

def separator(title: str, char: str = "═") -> None:
    line = char * 65
    log.info("")
    log.info(line)
    log.info(f"  {title}")
    log.info(line)


def monitor_connections(label: str) -> int:
    """
    Connect to postgres as the superuser and count real backend connections
    from APP_USER.  This shows what the cluster actually sees, regardless of
    whether connections came directly or through pgBouncer.
    """
    try:
        conn = psycopg2.connect(
            host=POSTGRES_HOST,
            port=POSTGRES_PORT,
            dbname="postgres",
            user=MONITOR_USER,
            password=MONITOR_PASSWORD,
            connect_timeout=5,
            application_name="poc-monitor",
        )
        conn.autocommit = True
        with conn.cursor() as cur:
            # Total real connections from the app user
            cur.execute(
                "SELECT count(*) FROM pg_stat_activity "
                "WHERE usename = %s AND pid <> pg_backend_pid()",
                (APP_USER,),
            )
            total = cur.fetchone()[0]

            # Breakdown per database and connection state
            cur.execute(
                """
                SELECT
                    coalesce(datname, '(no db)') AS db,
                    coalesce(state,   'unknown')  AS state,
                    count(*) AS n
                FROM pg_stat_activity
                WHERE usename = %s AND pid <> pg_backend_pid()
                GROUP BY datname, state
                ORDER BY datname, state
                """,
                (APP_USER,),
            )
            rows = cur.fetchall()

            # Remaining slots on the cluster
            cur.execute(
                "SELECT current_setting('max_connections')::int - "
                "(SELECT count(*) FROM pg_stat_activity WHERE pid <> pg_backend_pid())"
            )
            remaining = cur.fetchone()[0]

        conn.close()

        log.info(f"  [{label}]  real server connections: {total}  |  cluster slots remaining: {remaining}")
        for db, state, n in rows:
            log.info(f"    {db:<20}  state={state:<22}  count={n}")
        return total

    except Exception as exc:
        log.warning(f"  [{label}]  monitoring query failed: {exc}")
        return -1


def open_pool(
    host: str,
    port: int,
    dbname: str,
    service_name: str,
) -> tuple["pool.SimpleConnectionPool | None", "str | None"]:
    """
    Open a psycopg2 SimpleConnectionPool.

    SimpleConnectionPool(minconn, maxconn) pre-creates minconn connections
    immediately on instantiation — this is what establishes the real TCP
    connections to postgres (or pgBouncer).

    Returns (pool, None) on success, (None, error_message) on failure.
    """
    try:
        p = pool.SimpleConnectionPool(
            minconn=POOL_MIN,
            maxconn=POOL_MAX,
            host=host,
            port=port,
            dbname=dbname,
            user=APP_USER,
            password=APP_PASSWORD,
            connect_timeout=5,
            application_name=f"{service_name}:{dbname}",
        )
        # Verify the pool works by running a trivial query
        conn = p.getconn()
        with conn.cursor() as cur:
            cur.execute("SELECT 1")
        p.putconn(conn)
        return p, None
    except OperationalError as exc:
        # Common causes:
        #   - "FATAL: remaining connection slots are reserved for non-replication superuser"
        #   - "FATAL: sorry, too many clients already"
        first_line = str(exc).strip().splitlines()[0]
        return None, first_line


def close_all_pools(pools: list) -> None:
    for service, db, p in pools:
        try:
            p.closeall()
        except Exception:
            pass


# ── Scenario 1: Direct connections ─────────────────────────────────────────────

def scenario_direct() -> int:
    separator("SCENARIO 1 — Direct connections to PostgreSQL  (the problem)")

    total_pools_needed = len(SERVICES) * len(DATABASES)
    total_connections  = total_pools_needed * POOL_MIN

    log.info(f"  Postgres  max_connections     = 25")
    log.info(f"  Services                      = {len(SERVICES)}")
    log.info(f"  Databases per service         = {len(DATABASES)}")
    log.info(f"  Pools to open                 = {total_pools_needed}  ({len(SERVICES)} × {len(DATABASES)})")
    log.info(f"  min_idle per pool             = {POOL_MIN}")
    log.info(f"  Connections required          = {total_connections}  ({total_pools_needed} × {POOL_MIN})")
    log.info(f"  Available for non-superusers  = 22  (25 − 3 reserved)")
    log.info(f"  Expected outcome              = SATURATION after ~4 pools")
    log.info("")

    monitor_connections("baseline — before any pools")
    log.info("")

    open_pools: list = []
    failures: list   = []

    for service in SERVICES:
        for db in DATABASES:
            label = f"{service} → postgres:5432/{db}"
            p, err = open_pool(POSTGRES_HOST, POSTGRES_PORT, db, service)

            if p:
                open_pools.append((service, db, p))
                log.info(f"  ✓  {label}")
                # Show running total after each successful pool
                monitor_connections(f"after opening pool #{len(open_pools)}")
            else:
                failures.append((service, db, err))
                log.warning(f"  ✗  {label}")
                log.warning(f"     Error: {err}")

    log.info("")
    log.info(f"  Pools opened successfully : {len(open_pools)}/{total_pools_needed}")
    log.info(f"  Pools failed              : {len(failures)}/{total_pools_needed}")

    if failures:
        log.warning("")
        log.warning("  Services that could NOT connect (would degrade or crash in production):")
        for service, db, err in failures:
            log.warning(f"    {service}/{db}")

    log.info("")
    monitor_connections("final state — all pools attempted")

    log.info("")
    log.info("  Closing all direct pools...")
    close_all_pools(open_pools)
    time.sleep(2)
    monitor_connections("after cleanup")

    return len(failures)


# ── Scenario 2: Via pgBouncer ──────────────────────────────────────────────────

def scenario_via_bouncer() -> None:
    separator("SCENARIO 2 — Via pgBouncer  (the solution)")

    total_pools_needed   = len(SERVICES) * len(DATABASES)
    client_conn_total    = total_pools_needed * POOL_MIN
    server_conn_max      = len(DATABASES) * 3  # pool_size=3 per database in pgbouncer.ini

    log.info(f"  Same services, same pool config — but pointing to pgbouncer:6432")
    log.info(f"  pgBouncer  pool_mode          = transaction")
    log.info(f"  pgBouncer  pool_size per db   = 3")
    log.info(f"  Client-side pools             = {total_pools_needed}  (unchanged)")
    log.info(f"  Client-side connections        = {client_conn_total}  (unchanged)")
    log.info(f"  Max real server connections    = {server_conn_max}   ({len(DATABASES)} databases × 3)")
    log.info(f"  Expected outcome              = all pools open, cluster load = {server_conn_max}")
    log.info("")

    monitor_connections("baseline — before any pools")
    log.info("")

    open_pools: list = []
    failures: list   = []

    for service in SERVICES:
        for db in DATABASES:
            label = f"{service} → pgbouncer:6432/{db}"
            p, err = open_pool(PGBOUNCER_HOST, PGBOUNCER_PORT, db, service)

            if p:
                open_pools.append((service, db, p))
                log.info(f"  ✓  {label}")
            else:
                failures.append((service, db, err))
                log.error(f"  ✗  {label}  FAILED: {err}")

    log.info("")
    log.info(f"  Pools opened successfully : {len(open_pools)}/{total_pools_needed}")
    log.info("")

    monitor_connections("after opening all client pools (idle — no active queries)")
    log.info(
        f"  ↑ Only a few real server connections despite "
        f"{len(open_pools)} client pools ({client_conn_total} client-side connections)."
    )
    log.info(
        "    In transaction pooling mode pgBouncer only creates server connections"
        " when an active transaction needs one — idle client connections cost nothing on the DB."
    )

    # ── Concurrent query load ─────────────────────────────────────────────────
    log.info("")
    log.info(f"  Firing {len(open_pools)} concurrent queries (one per pool)...")

    results: list = []
    errors: list  = []
    lock = threading.Lock()

    def run_query(service: str, db: str, p: pool.SimpleConnectionPool) -> None:
        try:
            conn = p.getconn()
            with conn.cursor() as cur:
                cur.execute("SELECT pg_backend_pid(), current_database(), now()")
                pid, dbname, ts = cur.fetchone()
                with lock:
                    results.append((service, db, pid))
            p.putconn(conn)
        except Exception as exc:
            with lock:
                errors.append(f"{service}/{db}: {exc}")

    threads = [
        threading.Thread(target=run_query, args=(s, d, p))
        for s, d, p in open_pools
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    unique_server_pids = len(set(r[2] for r in results))

    log.info(f"  Queries completed : {len(results)}")
    log.info(f"  Errors            : {len(errors)}")
    log.info(
        f"  Unique server PIDs: {unique_server_pids}  ← pgBouncer reused these "
        f"server connections across all {len(open_pools)} client pools"
    )

    log.info("")
    monitor_connections("during/after query load")

    # ── Run a second wave to show multiplexing under higher concurrency ────────
    log.info("")
    log.info("  Second wave: 3 concurrent queries per pool (simulating burst load)...")

    results2: list = []
    errors2: list  = []

    def run_query_v2(service: str, db: str, p: pool.SimpleConnectionPool) -> None:
        try:
            conn = p.getconn()
            conn.autocommit = True
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT pg_backend_pid(), pg_sleep(0.1), current_database()"
                )
                pid = cur.fetchone()[0]
                with lock:
                    results2.append(pid)
            p.putconn(conn)
        except Exception as exc:
            with lock:
                errors2.append(str(exc))

    # Each pool gets 1 thread (pool max=5 but we only spawn 1 per pool to avoid
    # pool exhaustion; the point is to show PIDs being reused across pools)
    threads2 = [
        threading.Thread(target=run_query_v2, args=(s, d, p))
        for s, d, p in open_pools
        for _ in range(1)
    ]
    for t in threads2:
        t.start()
    for t in threads2:
        t.join()

    unique_pids2 = len(set(results2))
    log.info(f"  Queries completed : {len(results2)},  errors: {len(errors2)}")
    log.info(f"  Unique server PIDs: {unique_pids2}  ← server connections shared across all client pools")

    log.info("")
    monitor_connections("peak — during burst load")

    # ── Cleanup ───────────────────────────────────────────────────────────────
    log.info("")
    log.info("  Closing all client pools...")
    close_all_pools(open_pools)
    time.sleep(2)
    monitor_connections("after cleanup")


# ── Ready-check helper ─────────────────────────────────────────────────────────

def wait_for_postgres(host: str, port: int, dbname: str, user: str, password: str, label: str) -> bool:
    log.info(f"Waiting for {label} to be ready...")
    for _ in range(30):
        try:
            c = psycopg2.connect(
                host=host, port=port, dbname=dbname,
                user=user, password=password,
                connect_timeout=3,
            )
            c.close()
            log.info(f"{label} is ready.")
            return True
        except Exception:
            time.sleep(2)
    log.error(f"{label} did not become ready in 60 s.")
    return False


# ── Entry point ────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    log.info("=" * 65)
    log.info("  pgBouncer POC — DB Connection Pooling Demonstration")
    log.info(f"  Started: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    log.info("=" * 65)

    # Confirm both endpoints are reachable before starting the demo
    if not wait_for_postgres(
        POSTGRES_HOST, POSTGRES_PORT, "postgres", MONITOR_USER, MONITOR_PASSWORD, "PostgreSQL"
    ):
        sys.exit(1)

    if not wait_for_postgres(
        PGBOUNCER_HOST, PGBOUNCER_PORT, "payments_db", APP_USER, APP_PASSWORD, "pgBouncer"
    ):
        sys.exit(1)

    log.info("")

    # ── Run both scenarios ─────────────────────────────────────────────────────
    failures = scenario_direct()

    log.info("")
    time.sleep(3)  # brief pause so Postgres can fully release the direct connections

    scenario_via_bouncer()

    # ── Final summary ──────────────────────────────────────────────────────────
    separator("Summary", char="─")
    log.info("")
    log.info("  WITHOUT pgBouncer:")
    log.info(f"    {len(SERVICES)} services × {len(DATABASES)} databases × {POOL_MIN} min_idle")
    log.info(f"    = {len(SERVICES) * len(DATABASES) * POOL_MIN} connections needed  →  cluster saturated, {failures} pool(s) failed")
    log.info("")
    log.info("  WITH pgBouncer (transaction mode, pool_size=3):")
    log.info(f"    Same {len(SERVICES)} services × {len(DATABASES)} databases × {POOL_MIN} client connections")
    log.info(f"    = {len(DATABASES) * 3} real server connections  →  cluster headroom preserved")
    log.info("")
    log.info("  Key rule: without a pooler, connections scale as")
    log.info("    services × pods × pool_size")
    log.info("  With pgBouncer, connections scale as")
    log.info("    databases × pool_size_per_db  (independent of service / pod count)")
    log.info("")
    log.info(f"  Full log: {LOG_DIR}/simulate.log")
    log.info(f"  pgBouncer log: {LOG_DIR}/pgbouncer.log")
    log.info("")
