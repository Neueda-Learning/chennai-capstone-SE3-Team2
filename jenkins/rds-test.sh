#!/usr/bin/env bash
# Used by JenkinsfileCD: can a pod in the cluster reach the database in DB_HOST?
# A short-lived PostgreSQL client pod runs pg_isready and is deleted afterwards.
# Reads DB_HOST, DB_PORT, DB_NAME, DB_USER from the environment, else from k8s/base/config.env.
set -uo pipefail

CFG="$(dirname "$0")/../k8s/base/config.env"
get() { awk -F= -v k="$1" '$1 == k { v = substr($0, length(k) + 2) } END { print v }' "$CFG"; }
DB_HOST="${DB_HOST:-$(get DB_HOST)}"
DB_PORT="${DB_PORT:-$(get DB_PORT)}"
DB_NAME="${DB_NAME:-$(get DB_NAME)}"
DB_USER="${DB_USER:-$(get DB_USER)}"

echo "pg_isready -h $DB_HOST -p $DB_PORT -d $DB_NAME -U $DB_USER (from inside the cluster)"
kubectl -n default delete pod jenkins-rds-test --ignore-not-found >/dev/null 2>&1
kubectl -n default run jenkins-rds-test --rm -i --restart=Never --quiet \
  --image=postgres:16-alpine \
  --env="DB_HOST=$DB_HOST" --env="DB_PORT=$DB_PORT" --env="DB_NAME=$DB_NAME" --env="DB_USER=$DB_USER" \
  --command -- sh -c 'pg_isready -t 10 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME"'
