#!/usr/bin/env bash
# Run before every apply:   bash k8s/prepare.sh
#
# 1. Copies the repository files the manifests use into the (gitignored) generated/
#    folders, so the cluster always gets the repository's current versions:
#      Config/application.yml                  -> base/generated/application.yml
#      Infrastructure/Kafka/create-topics.sh   -> base/generated/create-topics.sh
#      Databases/PostgreSQL/...                -> optional/postgres/generated/
# 2. Checks what docker compose would refuse to start without: every secret filled
#    in, no <placeholder> left, no Windows line endings. It prints key names only,
#    never a value.
# 3. Renders both overlays with kubectl, when kubectl is installed.
set -euo pipefail

K8S="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(dirname "$K8S")"
errors=0
fail() { echo "  ERROR: $*" >&2; errors=$((errors + 1)); }

echo "== copying from $REPO"
for f in Config/application.yml Infrastructure/Kafka/create-topics.sh \
         Databases/PostgreSQL/init/00-roles.sh Databases/PostgreSQL/schema.sql \
         Databases/PostgreSQL/seed_data.sql Databases/PostgreSQL/init/13-init-complete.sh \
         Databases/PostgreSQL/init/healthcheck.sh; do
  [ -f "$REPO/$f" ] || { echo "ERROR: $REPO/$f not found. k8s/ must sit at the repository root." >&2; exit 1; }
done

mkdir -p "$K8S/base/generated" "$K8S/optional/postgres/generated"
cp "$REPO/Config/application.yml" "$K8S/base/generated/application.yml"
cp "$REPO/Infrastructure/Kafka/create-topics.sh" "$K8S/base/generated/create-topics.sh"
PG="$K8S/optional/postgres/generated"
cp "$REPO/Databases/PostgreSQL/init/00-roles.sh" "$PG/00-roles.sh"
gzip -9 -n -c "$REPO/Databases/PostgreSQL/schema.sql" > "$PG/01-schema.sql.gz"
gzip -9 -n -c "$REPO/Databases/PostgreSQL/seed_data.sql" > "$PG/02-seed-data.sql.gz"
cp "$REPO/Databases/PostgreSQL/init/13-init-complete.sh" "$PG/13-init-complete.sh"
cp "$REPO/Databases/PostgreSQL/init/healthcheck.sh" "$PG/healthcheck.sh"
echo "  done"

echo "== checking settings"
value() { awk -v k="$1" 'index($0, k "=") == 1 { v = substr($0, length(k) + 2) } END { print v }' "$2"; }
has_key() { grep -q "^$1=" "$2"; }

with_postgres=0
grep -qE '^components:' "$K8S"/overlays/*/kustomization.yaml && with_postgres=1

for f in base/config.env base/secrets.env overlays/frontend-lb/urls.env overlays/ingress/urls.env; do
  [ -f "$K8S/$f" ] || continue
  if grep -q $'\r' "$K8S/$f"; then fail "$f has Windows line endings (CRLF). Fix: sed -i 's/\r\$//' k8s/$f"; fi
done

CFG="$K8S/base/config.env"
if [ "$with_postgres" = 0 ] && value DB_HOST "$CFG" | grep -q '<'; then
  fail "base/config.env: DB_HOST is still <RDS-ENDPOINT> (or uncomment components: in an overlay)"
fi

if grep -q '<REGISTRY>' "$K8S/base/kustomization.yaml"; then
  fail "base/kustomization.yaml: images still say <REGISTRY> (README step 5)"
fi

SEC="$K8S/base/secrets.env"
if [ ! -f "$SEC" ]; then
  fail "base/secrets.env is missing: cp k8s/base/secrets.env.example k8s/base/secrets.env, then fill it in"
else
  required=(DB_PASSWORD AUTH_DB_PASSWORD JWT_SECRET ACTIVATION_INTERNAL_SECRET SMTP_USERNAME
            SMTP_PASSWORD ACTIVATION_MAIL_FROM REDIS_URL FAUXNANCE_API_KEY)
  [ "$with_postgres" = 1 ] && required+=(POSTGRES_SUPERUSER_PASSWORD ANALYTICS_DB_PASSWORD)
  # Every key must exist (a missing key stops the pod), even the ones allowed to be empty.
  for key in $(grep -oE '^[A-Z_]+=' "$K8S/base/secrets.env.example" | tr -d =); do
    has_key "$key" "$SEC" || fail "base/secrets.env: $key is missing (keep the line, even empty)"
  done
  for key in "${required[@]}"; do
    v="$(value "$key" "$SEC")"
    if [ -z "$v" ]; then fail "base/secrets.env: $key is empty"
    elif [[ "$v" == \"* || "$v" == \'* ]]; then fail "base/secrets.env: $key is quoted; write the value without quotes"
    elif [[ "$v" == *'<'* ]]; then fail "base/secrets.env: $key is still a <placeholder>"
    fi
  done
fi

for overlay in frontend-lb ingress; do
  if grep -q 'pending.invalid' "$K8S/overlays/$overlay/urls.env"; then
    echo "  note: overlays/$overlay/urls.env still has the placeholder address (fine for the first apply)"
  fi
done

if [ "$errors" = 0 ] && command -v kubectl >/dev/null 2>&1; then
  echo "== rendering"
  for overlay in frontend-lb ingress; do
    if out="$(kubectl kustomize "$K8S/overlays/$overlay" 2>&1 >/dev/null)"; then
      echo "  overlays/$overlay renders"
    else
      fail "overlays/$overlay does not render: $out"
    fi
  done
fi

if [ "$errors" -gt 0 ]; then
  echo "$errors problem(s) above: fix them, then run this again." >&2
  exit 1
fi
echo "ready: kubectl apply -k k8s/overlays/frontend-lb"
