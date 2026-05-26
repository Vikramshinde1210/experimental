FROM alpine:3.19

RUN apk add --no-cache pgbouncer su-exec && \
    addgroup -S pgbouncer 2>/dev/null || true && \
    adduser  -S -G pgbouncer -H pgbouncer 2>/dev/null || true && \
    mkdir -p /var/log/pgbouncer && \
    chown pgbouncer:pgbouncer /var/log/pgbouncer

COPY pgbouncer.ini  /etc/pgbouncer/pgbouncer.ini
COPY entrypoint.sh  /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 6432

ENTRYPOINT ["/entrypoint.sh"]
