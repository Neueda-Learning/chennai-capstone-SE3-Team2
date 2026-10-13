#!/usr/bin/env bash
# Runs once, on an empty volume, as the LAST init step: after the roles
# (00-roles.sh), the whole schema -- trading's in public, auth's in the auth
# schema -- (01-schema.sql) and the seed data (02-seed-data.sql).
#
# It only records that initialisation ran to the end. healthcheck.sh requires
# this marker, so a server whose init failed partway never reports healthy.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --no-password --dbname "$POSTGRES_DB" \
    -c "COMMENT ON DATABASE \"$POSTGRES_DB\" IS 'fauxnance: init complete'"
echo "initialisation complete"
