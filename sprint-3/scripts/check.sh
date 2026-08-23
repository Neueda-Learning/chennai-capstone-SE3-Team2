#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Load local configuration
if [[ -f "$ROOT_DIR/.env" ]]; then
    set -a
    source "$ROOT_DIR/.env"
    set +a
fi

# TARGET_DATABASE takes priority over POSTGRES_DB
DATABASE="${TARGET_DATABASE:-${POSTGRES_DB:-}}"

if [[ -z "$DATABASE" ]]; then
    echo "ERROR: TARGET_DATABASE or POSTGRES_DB must be set"
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
    -X
)

echo "Checking manifest..."

if [[ ! -f "$ROOT_DIR/manifest.env" ]]; then
    echo "ERROR: manifest.env not found"
    exit 1
fi

grep -q '^APPLY_COMMAND=' "$ROOT_DIR/manifest.env"
grep -q '^ACCOUNTS_TABLE=' "$ROOT_DIR/manifest.env"
grep -q '^ACCOUNT_STATE_COLUMN=' "$ROOT_DIR/manifest.env"
grep -q '^ACCOUNT_STATE_VALUES=' "$ROOT_DIR/manifest.env"
grep -q '^ORDERS_TABLE=' "$ROOT_DIR/manifest.env"
grep -q '^ORDER_IDEMPOTENCY_KEY_COLUMN=' "$ROOT_DIR/manifest.env"

echo "✓ Manifest contains required values"


echo "Checking tables..."

ACCOUNT_TABLE_EXISTS=$(
    "${PSQL[@]}" -At -c "
        SELECT EXISTS (
            SELECT 1
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name = 'account'
        );
    "
)

if [[ "$ACCOUNT_TABLE_EXISTS" != "t" ]]; then
    echo "ERROR: account table does not exist"
    exit 1
fi

ORDERS_TABLE_EXISTS=$(
    "${PSQL[@]}" -At -c "
        SELECT EXISTS (
            SELECT 1
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name = 'orders'
        );
    "
)

if [[ "$ORDERS_TABLE_EXISTS" != "t" ]]; then
    echo "ERROR: orders table does not exist"
    exit 1
fi

echo "✓ Required tables exist"


echo "Checking account states..."

STATE_CONSTRAINT_EXISTS=$(
    "${PSQL[@]}" -At -c "
        SELECT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conrelid = 'account'::regclass
              AND contype = 'c'
              AND pg_get_constraintdef(oid) ILIKE '%ACTIVE%'
              AND pg_get_constraintdef(oid) ILIKE '%SUSPENDED%'
              AND pg_get_constraintdef(oid) ILIKE '%CLOSED%'
        );
    "
)

if [[ "$STATE_CONSTRAINT_EXISTS" != "t" ]]; then
    echo "ERROR: Account state CHECK constraint not found"
    exit 1
fi

echo "✓ Account state constraint exists"


echo "Checking idempotency constraint..."

IDEMPOTENCY_CONSTRAINT_EXISTS=$(
    "${PSQL[@]}" -At -c "
        SELECT EXISTS (
            SELECT 1
            FROM pg_constraint c
            JOIN pg_attribute a
              ON a.attrelid = c.conrelid
             AND a.attnum = ANY(c.conkey)
            WHERE c.conrelid = 'orders'::regclass
              AND c.contype = 'u'
              AND a.attname = 'idempotency_key'
        );
    "
)

if [[ "$IDEMPOTENCY_CONSTRAINT_EXISTS" != "t" ]]; then
    echo "ERROR: Unique idempotency_key constraint not found"
    exit 1
fi

echo "✓ Idempotency constraint exists"


echo "Checking duplicate-idempotency probe..."

set +e

OUTPUT=$(
    "${PSQL[@]}" \
        -v VERBOSITY=verbose \
        -f "$ROOT_DIR/probes/duplicate-idempotency-key.sql" \
        2>&1
)

STATUS=$?

set -e

if [[ "$STATUS" -eq 0 ]]; then
    echo "ERROR: Duplicate idempotency key was accepted"
    exit 1
fi

if ! echo "$OUTPUT" | grep -q "23505"; then
    echo "ERROR: Expected SQLSTATE 23505"
    echo "$OUTPUT"
    exit 1
fi

echo "✓ Duplicate idempotency key rejected with SQLSTATE 23505"


echo "Checking orphan foreign-key probe..."

set +e

OUTPUT=$(
    "${PSQL[@]}" \
        -v VERBOSITY=verbose \
        -f "$ROOT_DIR/probes/orphan-foreign-key.sql" \
        2>&1
)

STATUS=$?

set -e

if [[ "$STATUS" -eq 0 ]]; then
    echo "ERROR: Orphan foreign key was accepted"
    exit 1
fi

if ! echo "$OUTPUT" | grep -q "23503"; then
    echo "ERROR: Expected SQLSTATE 23503"
    echo "$OUTPUT"
    exit 1
fi

echo "✓ Orphan foreign key rejected with SQLSTATE 23503"


echo
echo "================================="
echo "ALL CHECKS PASSED"
echo "================================="