#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load repository configuration
if [[ -f "$ROOT_DIR/.env" ]]; then
    set -a
    source "$ROOT_DIR/.env"
    set +a
fi

# TARGET_DATABASE takes priority over POSTGRES_DB
DATABASE="${TARGET_DATABASE:-${POSTGRES_DB:-}}"

if [[ -z "$DATABASE" ]]; then
    echo "ERROR: TARGET_DATABASE or POSTGRES_DB must be set" >&2
    exit 1
fi

: "${POSTGRES_USER:?POSTGRES_USER must be set}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"

export PGPASSWORD="$POSTGRES_PASSWORD"

PSQL=(
    psql
    -h "$PGHOST"
    -p "$PGPORT"
    -U "$POSTGRES_USER"
    -d "$DATABASE"
    -v ON_ERROR_STOP=1
)

echo "Applying migrations..."

for file in "$ROOT_DIR"/migrations/*.sql; do
    [[ -e "$file" ]] || continue

    echo "  Applying $(basename "$file")"
    "${PSQL[@]}" -f "$file"
done

echo "Loading seed data..."

for file in "$ROOT_DIR"/seed/*.sql; do
    [[ -e "$file" ]] || continue

    echo "  Loading $(basename "$file")"
    "${PSQL[@]}" -f "$file"
done

echo "Database successfully migrated and seeded."