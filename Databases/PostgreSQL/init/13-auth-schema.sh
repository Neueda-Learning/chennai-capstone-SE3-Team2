#!/usr/bin/env bash
# Runs once, on an empty volume, after the trading schema and seed.
#
# Applies the auth service's migrations to the auth database. They are mounted
# outside /docker-entrypoint-initdb.d on purpose: the image runs every .sql in
# that folder against the default database, which is trading, and auth's
# tables do not belong there.
set -euo pipefail

: "${AUTH_DB_NAME:?}"

shopt -s nullglob
migrations=(/auth-migrations/*.sql)
if [ ${#migrations[@]} -eq 0 ]; then
    echo "no auth migrations found in /auth-migrations" >&2
    exit 1
fi

for file in "${migrations[@]}"; do
    echo "auth: applying $(basename "$file")"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --no-password \
        --dbname "$AUTH_DB_NAME" --single-transaction --quiet -f "$file"
done

# The last thing initialisation does. healthcheck.sh requires this marker, so
# a server whose init failed partway never reports healthy.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --no-password --dbname "$POSTGRES_DB" \
    -c "COMMENT ON DATABASE \"$AUTH_DB_NAME\" IS 'fauxnance: init complete'"
echo "initialisation complete"
