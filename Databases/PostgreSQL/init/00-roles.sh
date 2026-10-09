#!/usr/bin/env bash
# Runs once, on an empty volume, before any schema file.
#
# One Postgres instance holds two databases: trading and auth. No application
# connects as the superuser, because a superuser bypasses every permission
# check -- GRANT and REVOKE do not apply to it, so on a shared instance it
# could read auth's credentials. Each service gets its own role instead, able
# to connect to its own database only.
#
# Default privileges are set here, before the schema files run, so every table
# and sequence they create is granted to the right role without a GRANT in any
# migration.
set -euo pipefail

: "${TRADING_APP_USER:?}" "${TRADING_APP_PASSWORD:?}"
: "${ANALYTICS_RO_USER:?}" "${ANALYTICS_RO_PASSWORD:?}"
: "${AUTH_APP_USER:?}" "${AUTH_APP_PASSWORD:?}" "${AUTH_DB_NAME:?}"

# An application role named after the superuser would leave that service
# running as superuser. Refuse loudly rather than start insecure.
for role in "$TRADING_APP_USER" "$ANALYTICS_RO_USER" "$AUTH_APP_USER"; do
    if [ "$role" = "$POSTGRES_USER" ]; then
        echo "refusing to initialise: application role '$role' is the superuser." >&2
        echo "Set DB_USER, ANALYTICS_DB_USER and AUTH_DB_USER to non-superuser names in .env." >&2
        exit 1
    fi
done

psql_as_superuser() {
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --no-password \
        -v superuser="$POSTGRES_USER" \
        -v trading_db="$POSTGRES_DB" \
        -v trading_user="$TRADING_APP_USER" -v trading_pw="$TRADING_APP_PASSWORD" \
        -v analytics_user="$ANALYTICS_RO_USER" -v analytics_pw="$ANALYTICS_RO_PASSWORD" \
        -v auth_db="$AUTH_DB_NAME" \
        -v auth_user="$AUTH_APP_USER" -v auth_pw="$AUTH_APP_PASSWORD" \
        "$@"
}

# Roles, the auth database, and who may connect where. Postgres grants CONNECT
# to PUBLIC on every new database by default, so it is revoked explicitly.
psql_as_superuser --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE :"trading_user"   LOGIN PASSWORD :'trading_pw';
CREATE ROLE :"analytics_user" LOGIN PASSWORD :'analytics_pw';
CREATE ROLE :"auth_user"      LOGIN PASSWORD :'auth_pw';

CREATE DATABASE :"auth_db";

REVOKE CONNECT ON DATABASE postgres       FROM PUBLIC;
REVOKE CONNECT ON DATABASE :"trading_db"  FROM PUBLIC;
REVOKE CONNECT ON DATABASE :"auth_db"     FROM PUBLIC;

GRANT CONNECT ON DATABASE :"trading_db" TO :"trading_user", :"analytics_user";
GRANT CONNECT ON DATABASE :"auth_db"    TO :"auth_user";

-- trading: read/write for the services, read-only for the analytics extract.
GRANT USAGE ON SCHEMA public TO :"trading_user", :"analytics_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"trading_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO :"trading_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT SELECT ON TABLES TO :"analytics_user";
SQL

# auth: read/write for the auth service, and nobody else.
psql_as_superuser --dbname "$AUTH_DB_NAME" <<'SQL'
GRANT USAGE ON SCHEMA public TO :"auth_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"auth_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO :"auth_user";
SQL

echo "roles created: $TRADING_APP_USER, $ANALYTICS_RO_USER, $AUTH_APP_USER; database $AUTH_DB_NAME created"
