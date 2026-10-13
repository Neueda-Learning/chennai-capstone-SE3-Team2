# 0018 One database, two schemas: auth moves from its own database into a schema

| Field | Value |
|---|---|
| Status | proposed |
| Date | 2026-10-13 |
| Decided by | The team's lead, for the cloud deployment on one Amazon RDS instance; for the team to confirm at review |

## Context

Since Sprint 8 the platform ran two databases on one PostgreSQL instance: `trading_system_db`
for trading, `auth` for logins and tokens. Each service's role could connect to its own database
only, so a trading connection could not reach a password hash.

The cloud deployment has one RDS instance and tools that open one database at a time (the team
saw "only one schema" in an RDS explorer). Two databases also mean two sets of connection settings
in every deployment template, and a setup that RDS's own init never runs.

## Options considered

| Option | For | Against |
|---|---|---|
| Keep two databases | Isolation at the connection: auth_app cannot even connect to trading | Two connections per tool; templates that pass one database to every service need extra settings |
| One database, two schemas, isolated by schema permissions | One database to create, back up and inspect; one `schema.sql` builds all of it; one connection shows both | Isolation rests on grants instead of on the connection; a mistaken grant could expose a schema |
| One database, one schema | Simplest | Auth's tables beside trading's, readable by trading_app: loses the Sprint 8 isolation |

## Decision

One database, `trading_system_db`, with two schemas: `public` for trading (unchanged), `auth` for
the auth service. The isolation is kept, and enforced by the database:

- `REVOKE ALL ON SCHEMA ... FROM PUBLIC` on both schemas.
- `trading_app` and `analytics_ro` have `USAGE` on `public` only; `auth_app` on `auth` only.
- Default privileges are set per schema, before any table is created.
- `auth_app`'s `search_path` is `auth` (role default, and set on every connection), so auth's SQL
  needs no schema prefix and no code changed.

Checked on a fresh server: trading_app reading `auth.credential` and auth_app reading
`public.orders` are both refused with *permission denied for schema*.

## Consequences

- `Databases/PostgreSQL/schema.sql` now builds both schemas; `local/setup.sql` builds the roles, the
  database and both schemas on any PostgreSQL, RDS included, in one command.
- auth reads the database name from `DB_NAME` and its schema from `AUTH_DB_SCHEMA` (default
  `auth`); `AUTH_DB_NAME` remains only as an override for a deployment that keeps auth apart.
- A local stack created before this change still has the separate `auth` database: it needs
  `docker compose down -v` once. `reset.sql` drops the old `auth` database too.
- The Sprint 8 security review's isolation claim now reads "by schema permission" rather than "by
  database connection".
