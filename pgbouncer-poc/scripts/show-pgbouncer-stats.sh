#!/usr/bin/env bash
# =============================================================================
#  show-pgbouncer-stats.sh
#
#  Connects to the pgBouncer admin console and shows pool + stats information.
#
#  pgBouncer exposes a virtual database called "pgbouncer" on its own port.
#  You can run SHOW commands there to inspect pool state.
#
#  Usage:
#    ./scripts/show-pgbouncer-stats.sh
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$(dirname "$SCRIPT_DIR")"

echo "=== pgBouncer admin console — $(date '+%H:%M:%S') ==="
echo ""

docker compose exec -T pgbouncer \
    sh -c 'PGPASSWORD=$PGBOUNCER_PASSWORD psql -h 127.0.0.1 -p 6432 -U $PGBOUNCER_USER pgbouncer' <<'SQL'

\echo '--- SHOW POOLS (real server connections per database) ---'
SHOW POOLS;

\echo ''
\echo '--- SHOW CLIENTS (client connections accepted by pgBouncer) ---'
SHOW CLIENTS;

\echo ''
\echo '--- SHOW SERVERS (real backend connections to Postgres) ---'
SHOW SERVERS;

\echo ''
\echo '--- SHOW STATS (request rates) ---'
SHOW STATS;
SQL
