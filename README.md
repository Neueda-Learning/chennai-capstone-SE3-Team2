# Yellow Trade - Enterprise Trading Platform

A retail trading platform for the Indian market: open an account (KYC, then an
activation email), add money, buy and sell NSE stocks and mutual funds, and get
notifications, watchlists and price alerts, a portfolio with profit and loss,
trade signals and automated strategies.

Everything below is what someone who has never seen the project needs to clone,
configure, build and run it.

## Repository structure

| Folder | What it is |
|---|---|
| `Config/application.yml` | Shared configuration: every service's URL and port, the database and Kafka. Read by all services. |
| `Contracts/API Schemas/` | The REST contracts (OpenAPI), the Kafka event contract, and every deliberate deviation. |
| `Contracts/Analytics Schemas/` | The DuckDB star schema the ETL builds. |
| `Databases/PostgreSQL/` | `schema.sql`, `seed_data.sql`, `reset.sql`, and the `migrations/` they are built from. |
| `Databases/DuckDB/analytics/` | Where the analytics ETL writes `analytics.duckdb`. |
| `ETL Layer/` | The analytics ETL (Python): PostgreSQL to the DuckDB star schema. |
| `Frontend/` | The trading UI (Angular), port 4200. |
| `Infrastructure/Kafka/` | Topic creation and the topic list. The broker itself is in `docker-compose.yml`. |
| `Services/auth-service/` | Login, tokens, account activation (NestJS), port 3000. |
| `Services/order-service/` | The Trade REST API: orders, accounts, KYC, payments, and the Sprint 10 modules (Spring Boot), port 8081. |
| `Services/executor-service/` | The Trade Executor: prices and fills orders, publishes market data (Spring Boot), port 8082. |
| `Services/domain-library/` | The shared trading rules both Java services build on (Java library). |
| `docker-compose.yml` | Starts PostgreSQL, Kafka and the three services with one command. |
| `docs/`, `scripts/` | Design documents, runbooks, decision logs; helper scripts. |

## Services and ports

| Service | URL | Port |
|---|---|---|
| Frontend application | http://localhost:4200 | 4200 |
| auth-service | http://localhost:3000 | 3000 |
| order-service (Trade REST API) | http://localhost:8081 | 8081 |
| executor-service | http://localhost:8082 | 8082 |
| PostgreSQL | localhost | 5432 |
| Kafka | localhost | 9092 |

These are the values in `Config/application.yml`.

## Prerequisites

To run it (the usual way):

- Docker with Docker Compose v2 (`docker compose version`)
- Node.js 22 or later, with npm (for the Frontend)
- Ports 3000, 4200, 5432, 8081, 8082 and 9092 free

To build and test outside Docker, also:

- Java 21 and Maven 3.9 or later
- Python 3.12 or later (the analytics ETL only)

Accounts and keys, which go in `.env` (never in git):

- A Fauxnance API key (stock prices), and the MF NAV service key (fund NAVs)
- A Gmail address with an app password (activation and notification emails)
- A Redis URL (the shared failed-login counter; optional outside Docker)

## Run it

Compose runs whatever `COMPOSE_PROFILES` in `.env` lists:

| Profile | What runs |
|---|---|
| `localdb` | The bundled PostgreSQL container. Leave it out to use Amazon RDS |
| `platform` | Kafka (topics created automatically), auth-service, order-service, executor-service |
| `frontend` | The UI, served by nginx, with the APIs behind it on the same address |
| `etl` | The analytics ETL, one run: `docker compose run --rm etl` |

### On your machine

```bash
git clone https://github.com/Neueda-Learning/chennai-capstone-SE3-Team2.git
cd chennai-capstone-SE3-Team2

# 1. Configuration: copy the example and fill in every value marked REQUIRED.
cp .env.example .env          # COMPOSE_PROFILES=localdb,platform

# 2. PostgreSQL, Kafka (topics included), auth-service, order-service, executor-service.
docker compose up -d --build
docker compose ps             # wait until every service is healthy

# 3. The Frontend, in development mode.
cd Frontend
npm ci
npm start                     # http://localhost:4200
```

Or the whole thing in containers, UI included: `COMPOSE_PROFILES=localdb,platform,frontend`,
then `docker compose up -d --build` and open http://localhost:4200.

The first start builds the database from `Databases/PostgreSQL/schema.sql` and
`seed_data.sql`, and creates every Kafka topic, before any service starts.

Check it is up:

```bash
curl -s http://localhost:8081/actuator/health      # {"status":"UP",...}
curl -s http://localhost:8082/actuator/health      # {"status":"UP",...}
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/auth/me   # 401: up, and asking for a token
```

Then in the browser: **New customer? Open an account**, with a real email you can
read. The activation email arrives about 40 seconds later; set a password, sign
in, add cash, and place an order.

Stop everything with `docker compose down`. Add `-v` to also delete the database
and Kafka data and start from nothing next time.

### On a server: EC2 with Amazon RDS

The browser reaches ONE address, the frontend container. nginx serves the UI
and forwards `/api/...` to order-service and `/auth/...`, `/activate` to
auth-service, so the browser never makes a cross-origin request, and only
one port has to be open.

1. **Database, once.** From the EC2 box (`sudo apt-get install -y postgresql-client`),
   as the RDS master user, with the three passwords you will put in `.env`:
   ```bash
   psql -h <RDS-ENDPOINT> -U postgres -d postgres -v ON_ERROR_STOP=1 \
     -v trading_pw=<DB_PASSWORD> -v analytics_pw=<ANALYTICS_DB_PASSWORD> -v auth_pw=<AUTH_DB_PASSWORD> \
     -f Databases/PostgreSQL/local/setup.sql
   ```
2. **`.env`**, from `.env.example`, with these differences:
   ```ini
   COMPOSE_PROFILES=platform,frontend          # no localdb: the database is RDS
   CONTAINER_DB_HOST=<RDS-ENDPOINT>
   DB_SSLMODE=require
   CORS_ALLOWED_ORIGINS=http://<EC2-PUBLIC-IP>:4200
   ACTIVATION_LINK_BASE_URL=http://<EC2-PUBLIC-IP>:4200/activate
   ACTIVATION_HOME_URL=http://<EC2-PUBLIC-IP>:4200/
   ```
3. **Start:** `docker compose up -d --build`, then `docker compose ps`.
4. **Open** `http://<EC2-PUBLIC-IP>:4200`. The EC2 security group needs port 4200
   inbound; the RDS security group needs 5432 from the EC2 instance.

Docker on Ubuntu needs the Compose and Buildx plugins:
`sudo apt-get install -y docker-compose-v2 docker-buildx`. A build "Killed"
on a small instance is memory: use 2 GB or more, or add swap.

## Configuration

Two places, and nothing hard-coded in the code:

- **`Config/application.yml`**: where everything is, as hosts and ports. The
  Java services import it, auth-service reads it at start-up, and
  `Frontend/src/environments/environment.ts` carries the same two API URLs for
  the browser.
- **`.env`**: every value, secrets included. `.env.example` lists every
  variable the application reads, with an example value and a comment. Copy it
  to `.env`; `.env` is in `.gitignore` and must never be committed.

An environment variable always wins over `Config/application.yml`. That is how
`docker-compose.yml` points the containers at each other by service name.

## Databases

One database, `trading_system_db`, with two schemas:

- `public`: accounts, orders, positions, and every module's tables (24).
- `auth`: logins and tokens (5).

Each service connects with its own limited role, and the schemas keep them
apart: `trading_app` (read/write) and `analytics_ro` (read-only) may use
`public` only; `auth_app` may use `auth` only. A trading connection cannot
read a password hash: it is refused with *permission denied for schema auth*.
`postgres` only creates the database.

| File | What it does |
|---|---|
| `Databases/PostgreSQL/schema.sql` | Every table, constraint and index of `trading_system_db`: `public`, then `auth` |
| `Databases/PostgreSQL/seed_data.sql` | Reference data, demo customers, and every NSE stock and Direct Growth fund |
| `Databases/PostgreSQL/reset.sql` | Drops the database and the three roles, to start again |
| `Databases/PostgreSQL/migrations/` | The 16 migrations `schema.sql` is built from (auth's 4 are in `Services/auth-service/migrations/`) |
| `Databases/PostgreSQL/local/setup.sql` | Builds the roles, the database and both schemas on a PostgreSQL you installed yourself, or on RDS |

`schema.sql` and `seed_data.sql` are generated from `base/`, `migrations/`,
`indexes/`, auth's migrations and `seed/` by `scripts/build-db-scripts.sh`. Run it after adding a
migration, and commit both files.

Without Docker, on a local PostgreSQL 16 or on RDS, as the superuser:

```bash
psql -U postgres -v ON_ERROR_STOP=1 \
  -v trading_pw=<DB_PASSWORD> -v analytics_pw=<ANALYTICS_DB_PASSWORD> -v auth_pw=<AUTH_DB_PASSWORD> \
  -f Databases/PostgreSQL/local/setup.sql

# To start again from nothing (deletes the database):
psql -U postgres -v ON_ERROR_STOP=1 -f Databases/PostgreSQL/reset.sql
```

`setup.sql` is the roles and permissions, then exactly these two files. Into a
database you created yourself, with your own roles, they are:

```bash
psql -U postgres -d trading_system_db -v ON_ERROR_STOP=1 -f Databases/PostgreSQL/schema.sql
psql -U postgres -d trading_system_db -v ON_ERROR_STOP=1 -f Databases/PostgreSQL/seed_data.sql
```

## Build and test

```bash
# Java: domain-library, order-service, executor-service.
# Database tests use Testcontainers (Docker running), or set IT_DB_URL.
mvn -B verify

# auth-service
cd Services/auth-service && npm ci && npm test && npm run build

# Frontend: unit tests, the API-client check, and a production build
cd Frontend && npm ci && npm test && npm run check:api && npm run build

# Analytics ETL
cd "ETL Layer" && pip install -e ".[dev]" && pytest
```

## Analytics

The ETL reads `trading_system_db` as `analytics_ro` and loads the star schema in
`Contracts/Analytics Schemas/star_schema.sql` into
`Databases/DuckDB/analytics/analytics.duckdb`:

```bash
cd "ETL Layer" && python -m etl.pipeline run
```

## When something is wrong

| You see | Fix |
|---|---|
| `... must be set in .env` from `docker compose` | A REQUIRED value is missing from `.env`: compare it with `.env.example` |
| `port is already allocated` | Something else holds the port, often a PostgreSQL installed on 5432. Stop it, or set `DB_PUBLISHED_PORT` in `.env` |
| A service cannot find `trading_system_db`, or auth cannot find its tables | The PostgreSQL data volume predates them: `docker compose down -v`, then `up` again |
| Stock orders all `REJECTED` | No price: check `FAUXNANCE_API_KEY`; the key allows 2,000 requests a day |
| Fund orders all `REJECTED` | No NAV: check `MF_NAV_API_KEY` |
| A service waits forever on Kafka | `docker compose logs kafka-init` shows whether the topics were created |

More: `docs/runbooks/` (activation, payments, the event backbone, a Windows
setup), `docs/architecture/`, and the decision logs in `docs/sprints/`.
