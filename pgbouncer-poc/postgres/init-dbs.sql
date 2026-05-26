-- =============================================================================
--  Cluster initialisation script
--  Runs once when the postgres container is first created.
--
--  Creates:
--    - appuser  : non-superuser used by the simulated services + pgBouncer
--    - 3 databases on the same cluster, each with a small seed table
--
--  This mirrors a typical shared-cluster topology where multiple services
--  (payments, ach, scheduling) share one PostgreSQL instance.
-- =============================================================================

-- ── App user ─────────────────────────────────────────────────────────────────
CREATE USER appuser WITH PASSWORD 'apppassword';

-- ── Databases ─────────────────────────────────────────────────────────────────
CREATE DATABASE payments_db    OWNER appuser;
CREATE DATABASE ach_db         OWNER appuser;
CREATE DATABASE scheduling_db  OWNER appuser;

-- ── payments_db schema ───────────────────────────────────────────────────────
\c payments_db

CREATE TABLE transactions (
    id         BIGSERIAL PRIMARY KEY,
    amount     NUMERIC(15, 2) NOT NULL,
    currency   CHAR(3)        NOT NULL DEFAULT 'USD',
    status     VARCHAR(20)    NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

INSERT INTO transactions (amount, currency, status) VALUES
    (100.00, 'USD', 'settled'),
    (250.50, 'USD', 'pending'),
    (75.00,  'GBP', 'settled');

GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public TO appuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO appuser;

-- ── ach_db schema ─────────────────────────────────────────────────────────────
\c ach_db

CREATE TABLE ach_files (
    id           BIGSERIAL PRIMARY KEY,
    filename     VARCHAR(255) NOT NULL,
    record_count INT          NOT NULL DEFAULT 0,
    processed_at TIMESTAMPTZ
);

INSERT INTO ach_files (filename, record_count, processed_at) VALUES
    ('ACH_20260101_001.txt', 1500, now()),
    ('ACH_20260102_001.txt', 2300, now());

GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public TO appuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO appuser;

-- ── scheduling_db schema ──────────────────────────────────────────────────────
\c scheduling_db

CREATE TABLE schedules (
    id          BIGSERIAL PRIMARY KEY,
    job_name    VARCHAR(255) NOT NULL,
    cron_expr   VARCHAR(100),
    next_run    TIMESTAMPTZ,
    last_status VARCHAR(20)  DEFAULT 'pending',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO schedules (job_name, cron_expr, next_run) VALUES
    ('daily-reconciliation', '0 2 * * *', now() + INTERVAL '1 day'),
    ('hourly-sweep',         '0 * * * *', now() + INTERVAL '1 hour');

GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public TO appuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO appuser;
