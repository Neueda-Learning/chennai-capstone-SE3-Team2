#!/usr/bin/env bash
# Healthy only when the server accepts TCP connections AND initialisation ran
# to the end.
#
# The second half matters. If an init script fails, the container exits; the
# restart policy brings it back, the data directory now exists, and Postgres
# skips initialisation and starts normally -- with no roles, no auth database
# and no tables. Without the marker check that broken server would report
# healthy and every service would start against it.
#
# TCP rather than the socket because the init scripts run against a temporary
# server that listens on the socket only.
set -eu

pg_isready -q -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"

marker="$(psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
    "SELECT shobj_description(oid, 'pg_database') FROM pg_database WHERE datname = '$AUTH_DB_NAME'")"
[ "$marker" = "fauxnance: init complete" ]
