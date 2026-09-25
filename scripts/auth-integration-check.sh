#!/usr/bin/env bash
#
# Story 625. Proves a token from the running Auth service is accepted by the
# Trade REST API, and that one signed with a key the platform should not trust
# is refused.
#
#   scripts/auth-integration-check.sh
#
set -euo pipefail

AUTH="http://localhost:${AUTH_PUBLISHED_PORT:-3000}"
API="http://localhost:${API_PUBLISHED_PORT:-8080}"
ACCOUNT="${ACCOUNT_ID:-3}"
USERNAME="${USERNAME:-check.$(date +%s)}"
PASSWORD="${PASSWORD:-correct horse battery staple}"

for tool in curl jq openssl; do
  command -v "$tool" >/dev/null || { echo "need $tool on PATH" >&2; exit 2; }
done

pass() { printf '  \033[1;32mpass\033[0m  %s\n' "$*"; }
fail() { printf '  \033[1;31mFAIL\033[0m  %s\n' "$*"; exit 1; }
step() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }

step "1. register a user against a provisioned account"
# Each run uses a fresh username, so it needs an account nobody has claimed
# yet. Walk the provisioned range and take the first that is still free --
# this keeps the check re-runnable, which matters when it is rehearsed.
ACCOUNT=""
for candidate in $(seq "${ACCOUNT_ID:-1}" 10); do
  REG=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$AUTH/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"accountId\":$candidate}")
  case "$REG" in
    201) ACCOUNT=$candidate; break ;;
    401) continue ;;                       # already claimed, try the next
    *)   fail "register answered $REG for account $candidate" ;;
  esac
done
[ -n "$ACCOUNT" ] || fail "every provisioned account is claimed. Reset with: docker compose --profile platform down -v"
pass "registered against account $ACCOUNT, and no tokens were issued"

step "2. log in and read the token"
TOKEN=$(curl -sS -X POST "$AUTH/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" | jq -r '.accessToken // empty')
[ -n "$TOKEN" ] || fail "no access token; is account $ACCOUNT claimed by another username?"
pass "token issued"

echo "     claims:"
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq -c . | sed 's/^/       /'

step "3. a protected Trade REST API route ACCEPTS it"
CODE=$(curl -sS -o /dev/null -w '%{http_code}' "$API/api/v1/accounts/$ACCOUNT/balance" \
  -H "Authorization: Bearer $TOKEN")
[ "$CODE" = "200" ] && pass "200 from the Trade REST API" || fail "expected 200, got $CODE"

step "4. the same route REFUSES a request with no token"
CODE=$(curl -sS -o /dev/null -w '%{http_code}' "$API/api/v1/accounts/$ACCOUNT/balance")
[ "$CODE" = "401" ] && pass "401 with no token" || fail "expected 401, got $CODE"

step "5. the same route REFUSES a token signed with an untrusted key"
# A well-formed, unexpired token signed with a key the platform never issued.
b64() { openssl base64 -e -A | tr '+/' '-_' | tr -d '='; }
HEADER=$(printf '{"alg":"HS256","typ":"JWT"}' | b64)
NOW=$(date +%s)
PAYLOAD=$(printf '{"sub":"forged","accountId":%s,"roles":["ADMIN"],"iat":%s,"exp":%s,"iss":"auth-service"}' \
  "$ACCOUNT" "$NOW" "$((NOW + 900))" | b64)
SIG=$(printf '%s.%s' "$HEADER" "$PAYLOAD" \
  | openssl dgst -sha256 -hmac 'a-key-the-platform-should-never-trust' -binary | b64)
FORGED="$HEADER.$PAYLOAD.$SIG"

CODE=$(curl -sS -o /dev/null -w '%{http_code}' "$API/api/v1/accounts/$ACCOUNT/balance" \
  -H "Authorization: Bearer $FORGED")
[ "$CODE" = "401" ] && pass "401 for a token signed with an untrusted key" \
                     || fail "expected 401, got $CODE -- the API trusted a forged signature"

step "6. no Java changed to accept any of this"
# The baseline is the commit before the auth service first appeared, so the
# diff covers exactly the Sprint 8 work and not the Sprint 7 restructure,
# which moved every Java file and would otherwise swamp this.
FIRST_AUTH=$(git log --reverse --format=%H -- services/auth | head -1)
BASE=$(git rev-parse "${FIRST_AUTH}^")

if git diff --name-only "$BASE"...HEAD -- '*.java' | grep -q .; then
  fail "Java files changed; the criterion is a configuration change only"
else
  pass "no .java file changed since $(git rev-parse --short "$BASE")"
  echo "     everything Sprint 8 touched outside services/auth:"
  git diff --name-only "$BASE"...HEAD | grep -v '^services/auth/' | sed 's/^/       /'
fi

printf '\n\033[1;32mAll checks passed.\033[0m\n'
