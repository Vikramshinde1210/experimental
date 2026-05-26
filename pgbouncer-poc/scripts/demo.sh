#!/usr/bin/env bash
# =============================================================================
#  demo.sh — runs the full pgBouncer POC simulation
#
#  Usage (from the pgbouncer-poc directory):
#    ./scripts/demo.sh
#
#  What it does:
#    1. Verifies all containers are running
#    2. Runs simulate.py inside the simulator container
#    3. Output is printed to stdout AND written to ./logs/simulate.log
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "======================================================================"
echo "  pgBouncer POC — Connection Pooling Demo"
echo "======================================================================"
echo ""

# Check containers are up
if ! docker compose ps --status running | grep -q "pgbouncer-poc-postgres"; then
    echo "ERROR: Containers are not running. Start them first:"
    echo "  docker compose up -d"
    exit 1
fi

# Wait for pgbouncer to be ready (it depends on postgres being healthy)
echo "Waiting for pgBouncer to accept connections..."
for i in $(seq 1 20); do
    if docker compose exec -T pgbouncer sh -c \
        'PGPASSWORD=$PGBOUNCER_PASSWORD psql -h 127.0.0.1 -p 6432 -U $PGBOUNCER_USER -d payments_db -c "SELECT 1" > /dev/null 2>&1'; then
        echo "pgBouncer is ready."
        break
    fi
    sleep 2
done

echo ""
echo "Running simulation (output also saved to ./logs/simulate.log)..."
echo "----------------------------------------------------------------------"
echo ""

docker compose exec -T simulator python /app/simulate.py

echo ""
echo "----------------------------------------------------------------------"
echo "Simulation complete."
echo ""
echo "Useful follow-up commands:"
echo "  ./scripts/check-connections.sh          — live connection counts from Postgres"
echo "  ./scripts/show-pgbouncer-stats.sh       — pgBouncer pool stats (SHOW POOLS)"
echo "  cat ./logs/simulate.log                 — full simulation log"
echo "  cat ./logs/pgbouncer.log                — pgBouncer connection log"
