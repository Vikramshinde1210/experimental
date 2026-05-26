#!/usr/bin/env bash
# =============================================================================
#  check-connections.sh
#
#  Queries pg_stat_activity on the running postgres container to show real
#  server-side connection counts.  Run this in a separate terminal while the
#  demo is running to watch connections change in real time.
#
#  Usage:
#    ./scripts/check-connections.sh           — one-shot snapshot
#    watch -n 2 ./scripts/check-connections.sh — refresh every 2 seconds
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$(dirname "$SCRIPT_DIR")"

echo "=== pg_stat_activity — $(date '+%H:%M:%S') ==="
echo ""

docker compose exec -T postgres psql -U postgres -d postgres <<'SQL'

-- Total connections by user
SELECT
    usename                          AS "user",
    count(*)                         AS "total_connections",
    count(*) FILTER (WHERE state = 'active')  AS "active",
    count(*) FILTER (WHERE state = 'idle')    AS "idle",
    count(*) FILTER (WHERE state = 'idle in transaction') AS "idle_in_tx"
FROM pg_stat_activity
WHERE pid <> pg_backend_pid()
  AND usename IS NOT NULL
GROUP BY usename
ORDER BY total_connections DESC;

\echo ''
\echo '--- Breakdown by database + state ---'

SELECT
    coalesce(datname, '(no database)') AS "database",
    usename                             AS "user",
    state,
    count(*)                            AS "connections",
    min(now() - state_change)::text     AS "oldest_in_state"
FROM pg_stat_activity
WHERE pid <> pg_backend_pid()
  AND usename IS NOT NULL
GROUP BY datname, usename, state
ORDER BY datname, state;

\echo ''
\echo '--- Cluster summary ---'

SELECT
    (SELECT count(*) FROM pg_stat_activity WHERE pid <> pg_backend_pid())
        AS "total_backend_connections",
    current_setting('max_connections')::int
        AS "max_connections",
    current_setting('max_connections')::int -
        (SELECT count(*) FROM pg_stat_activity WHERE pid <> pg_backend_pid())
        AS "remaining_slots";
SQL
