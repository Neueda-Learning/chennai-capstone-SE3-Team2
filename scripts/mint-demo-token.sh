#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Mint an HS256 bearer token for the story 612 demonstration.
#
# The trade-api verifies signature, expiry, algorithm and issuer, then
# reads the `accountId` claim. This mint reproduces exactly what
# PostgresSupport.tokenFor() does in the integration tests -- same
# issuer ("auth-service"), same claim shape -- so a token minted here
# is indistinguishable from one minted by a real auth service sharing
# JWT_SECRET.
#
# Reads JWT_SECRET from ../.env by default. Prints the token to
# stdout so the caller can bind it into an Authorization header.
#
# Usage:
#   ./mint-demo-token.sh                # accountId=3, 10-minute ttl
#   ACCOUNT_ID=5 TTL_SECONDS=1800 ./mint-demo-token.sh
#
# Requires openssl and printf. No jq: the payload is small and we
# construct it directly to keep the dependency list short.
# ---------------------------------------------------------------------
set -euo pipefail

ACCOUNT_ID="${ACCOUNT_ID:-3}"
TTL_SECONDS="${TTL_SECONDS:-600}"
ISSUER="${JWT_ISSUER:-auth-service}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-${SCRIPT_DIR}/../.env}"

if [ -z "${JWT_SECRET:-}" ] && [ -f "${ENV_FILE}" ]; then
  # Reads only JWT_SECRET from .env; does not export the rest.
  JWT_SECRET="$(grep -E '^JWT_SECRET=' "${ENV_FILE}" | head -n1 | cut -d= -f2-)"
fi

if [ -z "${JWT_SECRET:-}" ]; then
  echo "mint-demo-token: JWT_SECRET not set and ${ENV_FILE} did not carry one." >&2
  echo "Copy .env.example to .env and set JWT_SECRET, then retry." >&2
  exit 2
fi

# Base64url = base64, +/ -> -_, strip = padding. Applied to bytes on
# stdin so the same function works for JSON strings and for the raw
# HMAC output.
b64url() {
  openssl base64 -e -A | tr '+/' '-_' | tr -d '='
}

NOW="$(date -u +%s)"
EXP="$(( NOW + TTL_SECONDS ))"

# Fields as PostgresSupport.tokenFor mints them: subject as a bare
# UUID, roles=CUSTOMER, numeric accountId. iat is one second in the
# past so a clock skew of a fraction of a second does not reject the
# token as "used before issued".
SUBJECT="$(cat /proc/sys/kernel/random/uuid 2>/dev/null \
        || uuidgen 2>/dev/null \
        || openssl rand -hex 16)"

HEADER='{"alg":"HS256","typ":"JWT"}'
PAYLOAD=$(printf '{"sub":"%s","iss":"%s","accountId":%s,"roles":["CUSTOMER"],"iat":%s,"exp":%s}' \
  "${SUBJECT}" "${ISSUER}" "${ACCOUNT_ID}" "$(( NOW - 1 ))" "${EXP}")

HEADER_B64="$(printf '%s' "${HEADER}" | b64url)"
PAYLOAD_B64="$(printf '%s' "${PAYLOAD}" | b64url)"

SIGNATURE="$(printf '%s.%s' "${HEADER_B64}" "${PAYLOAD_B64}" \
  | openssl dgst -sha256 -hmac "${JWT_SECRET}" -binary \
  | b64url)"

printf '%s.%s.%s\n' "${HEADER_B64}" "${PAYLOAD_B64}" "${SIGNATURE}"
