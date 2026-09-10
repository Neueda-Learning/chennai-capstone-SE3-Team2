#!/usr/bin/env bash
# =====================================================================
# apply.sh - build the trading platform database from scratch
#
# Phases, in order:
#   1. base/       CREATE TABLE statements
#   2. migrations/ ALTER statements, applied in filename order
#   3. indexes/    unique constraints, then performance indexes
#   4. seeds/      reference and test data
#
# Every phase runs with ON_ERROR_STOP=1, so psql aborts on the first
# error instead of carrying on and exiting zero. Every phase also runs
# inside a transaction, so a failure leaves nothing half-applied.
#
# Usage:
#   ./apply.sh                    # uses defaults / environment
#   PGDATABASE=trading ./apply.sh
#   ./apply.sh --dry-run          # list what would run, touch nothing
#
# Connection is standard libpq environment variables:
#   PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE
# =====================================================================

set -euo pipefail

# ---------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-postgres}"
export PGDATABASE="${PGDATABASE:-postgres}"

DRY_RUN=0
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=1

# ---------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------
if [[ -t 1 ]]; then
    BOLD=$'\033[1m'; RED=$'\033[31m'; GREEN=$'\033[32m'
    YELLOW=$'\033[33m'; DIM=$'\033[2m'; RESET=$'\033[0m'
else
    BOLD=""; RED=""; GREEN=""; YELLOW=""; DIM=""; RESET=""
fi

info()  { printf '%s\n' "${BOLD}$*${RESET}"; }
ok()    { printf '%s\n' "  ${GREEN}OK${RESET}  $*"; }
warn()  { printf '%s\n' "  ${YELLOW}--${RESET}  $*"; }
die()   { printf '%s\n' "${RED}FAILED${RESET}  $*" >&2; exit 1; }

# ---------------------------------------------------------------------
# run_phase <label> <directory> <mode>
#
# mode = per-file   each file is its own transaction. Used for
#                   migrations, so a failure tells you exactly which
#                   migration broke and earlier ones stay applied.
#
# mode = combined   all files in ONE transaction across the whole phase.
#                   Used for seeds, because rows in later files carry
#                   foreign keys to rows in earlier ones - a partial
#                   seed is a broken seed. Also used for base and
#                   indexes, which are each a single logical unit.
#
# Note: the SQL files deliberately contain no BEGIN/COMMIT. psql's
# --single-transaction supplies the wrapper. Having both would nest,
# and the file's own COMMIT would end the outer transaction early.
# ---------------------------------------------------------------------
run_phase() {
    local label="$1" dir="$2" mode="$3"
    local path="$ROOT/$dir"

    info "$label"

    if [[ ! -d "$path" ]]; then
        warn "no $dir/ directory - skipping"
        return 0
    fi

    # Sorted so numeric prefixes control order. LC_ALL=C for a stable,
    # locale-independent sort.
    local files=()
    while IFS= read -r f; do files+=("$f"); done \
        < <(LC_ALL=C find "$path" -maxdepth 1 -name '*.sql' -type f | LC_ALL=C sort)

    if [[ ${#files[@]} -eq 0 ]]; then
        warn "no .sql files in $dir/ - skipping"
        return 0
    fi

    if [[ $DRY_RUN -eq 1 ]]; then
        for f in "${files[@]}"; do
            printf '%s\n' "  ${DIM}would run${RESET}  $dir/$(basename "$f")"
        done
        return 0
    fi

    if [[ "$mode" == "combined" ]]; then
        # One psql invocation, many -f flags, one transaction around all.
        local args=()
        for f in "${files[@]}"; do args+=(-f "$f"); done
        psql -v ON_ERROR_STOP=1 --single-transaction --quiet "${args[@]}" \
            || die "$label - rolled back, database unchanged by this phase"
        for f in "${files[@]}"; do ok "$dir/$(basename "$f")"; done
    else
        for f in "${files[@]}"; do
            psql -v ON_ERROR_STOP=1 --single-transaction --quiet -f "$f" \
                || die "$dir/$(basename "$f") - rolled back"
            ok "$dir/$(basename "$f")"
        done
    fi
}

# ---------------------------------------------------------------------
# Preflight
# ---------------------------------------------------------------------
command -v psql >/dev/null 2>&1 || die "psql not found on PATH"

info "Target: $PGUSER@$PGHOST:$PGPORT/$PGDATABASE"

if [[ $DRY_RUN -eq 0 ]]; then
    psql -v ON_ERROR_STOP=1 --quiet -c 'SELECT 1' >/dev/null 2>&1 \
        || die "cannot connect to $PGDATABASE at $PGHOST:$PGPORT as $PGUSER"
fi
echo

# ---------------------------------------------------------------------
# Phases
# ---------------------------------------------------------------------
run_phase "1/4  Base schema"  base       combined
echo
run_phase "2/4  Migrations"   migrations per-file
echo
run_phase "3/4  Indexes"      indexes    combined
echo
run_phase "4/4  Seed data"    seed       combined
echo

if [[ $DRY_RUN -eq 1 ]]; then
    info "Dry run complete - nothing was executed."
else
    printf '%s\n' "${GREEN}${BOLD}Database ready.${RESET}"
fi
