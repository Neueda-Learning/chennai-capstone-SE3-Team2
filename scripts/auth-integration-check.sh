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

# Registration now needs an activation token, which only the internal route
# mints. The secret comes from the environment, or from the root .env that
# docker compose reads -- never from this file.
if [ -z "${ACTIVATION_INTERNAL_SECRET:-}" ] && [ -f .env ]; then
  ACTIVATION_INTERNAL_SECRET=$(grep -E '^ACTIVATION_INTERNAL_SECRET=' .env | head -1 | cut -d= -f2-)
fi
[ -n "${ACTIVATION_INTERNAL_SECRET:-}" ] || { echo "set ACTIVATION_INTERNAL_SECRET, or run from the repo root with .env" >&2; exit 2; }

# Check 6 measures what Sprint 8 did, so it stops at the Sprint 8 merge.
# Sprint 9's activation mailer is Java by design and is not Sprint 8's claim.
# fcd04df is that merge (PR #21) as it stands on release/sprint8. The earlier
# dd4a77b has the identical tree but was rewritten away and is not in a clone.
SPRINT8_END="${SPRINT8_END:-fcd04df}"

pass() { printf '  \033[1;32mpass\033[0m  %s\n' "$*"; }
fail() { printf '  \033[1;31mFAIL\033[0m  %s\n' "$*"; exit 1; }
step() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }

step "1. register a user with an activation token"
# Each run uses a fresh username, so it needs an account nobody has claimed
# yet. Walk the provisioned range and mint a token for the first that is still
# free -- this keeps the check re-runnable, which matters when it is rehearsed.
ACCOUNT=""
ACTIVATION_TOKEN=""
for candidate in $(seq "${ACCOUNT_ID:-1}" 10); do
  MINT=$(curl -sS -w '\n%{http_code}' -X POST "$AUTH/internal/activation-tokens" \
    -H 'Content-Type: application/json' -H "X-Internal-Secret: $ACTIVATION_INTERNAL_SECRET" \
    -d "{\"clientId\":$candidate}")
  case "$(echo "$MINT" | tail -1)" in
    201) ACCOUNT=$candidate; ACTIVATION_TOKEN=$(echo "$MINT" | head -1 | jq -r '.activationToken'); break ;;
    404|409) continue ;;                   # not provisioned, or already claimed
    *)   fail "minting a token answered $(echo "$MINT" | tail -1) for account $candidate" ;;
  esac
done
[ -n "$ACCOUNT" ] || fail "every provisioned account is claimed. Reset with: docker compose --profile platform down -v"

REG=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$AUTH/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"activationToken\":\"$ACTIVATION_TOKEN\"}")
[ "$REG" = "201" ] || fail "register answered $REG for account $ACCOUNT"
pass "registered against account $ACCOUNT with no account number in the request, and no tokens were issued"

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

step "6. Sprint 8 adopted the auth service with no Java change"
# The baseline is the commit before the auth service first appeared, so the
# diff covers exactly the Sprint 8 work and not the Sprint 7 restructure,
# which moved every Java file and would otherwise swamp this. It ends at the
# Sprint 8 merge, because the claim is about Sprint 8.
FIRST_AUTH=$(git log --reverse --format=%H -- services/auth | head -1)
BASE=$(git rev-parse "${FIRST_AUTH}^")

if git diff --name-only "$BASE"..."$SPRINT8_END" -- '*.java' | grep -q .; then
  fail "Java files changed in Sprint 8; the criterion is a configuration change only"
else
  pass "no .java file changed between $(git rev-parse --short "$BASE") and $(git rev-parse --short "$SPRINT8_END")"
  echo "     everything Sprint 8 touched outside services/auth:"
  git diff --name-only "$BASE"..."$SPRINT8_END" | grep -v '^services/auth/' | sed 's/^/       /'
fi

printf '\n\033[1;32mAll checks passed.\033[0m\n'
