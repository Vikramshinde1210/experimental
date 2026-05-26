#!/bin/sh
# =============================================================================
#  pgBouncer entrypoint
#
#  Generates userlist.txt at container start using PGBOUNCER_USER / PGBOUNCER_PASSWORD.
#
#  Passwords are stored in PLAINTEXT (not md5) because Postgres 16+ uses
#  scram-sha-256 by default.  pgBouncer needs the plaintext to compute the
#  scram handshake when connecting to the backend.
# =============================================================================
set -e

PGBOUNCER_USER="${PGBOUNCER_USER:-appuser}"
PGBOUNCER_PASSWORD="${PGBOUNCER_PASSWORD:-apppassword}"

echo "Generating userlist.txt for user: ${PGBOUNCER_USER}"

cat > /etc/pgbouncer/userlist.txt <<EOF
"${PGBOUNCER_USER}" "${PGBOUNCER_PASSWORD}"
EOF

chown pgbouncer:pgbouncer /etc/pgbouncer/userlist.txt
chmod 600 /etc/pgbouncer/userlist.txt

echo "pgBouncer starting on port 6432 (pool_mode=transaction, auth=scram-sha-256)"
exec su-exec pgbouncer pgbouncer /etc/pgbouncer/pgbouncer.ini
