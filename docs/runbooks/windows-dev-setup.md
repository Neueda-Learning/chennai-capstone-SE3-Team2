# Development on Windows, with Kafka on EC2

Only Kafka runs in a container, on an EC2 instance. PostgreSQL, auth,
trade-api, the executor and the UI run on a Windows laptop, from IntelliJ and
VS Code. One SSH connection carries Kafka's port to the laptop, so every
service keeps its default address and no code or configuration changes.

`infra/README.md` sets default addresses "unless your team documents another
arrangement"; this is that document. `docker-compose.yml` is unchanged and still
starts the whole platform with one command, as Sprints 6 and 7 require -- this
is a second way to run it for development, not a replacement.

## What runs where

| Piece | Where | The others reach it at |
|---|---|---|
| Kafka 3.8 | EC2, the `kafka` container | `localhost:9092`, through the SSH forward |
| PostgreSQL 16 | Windows, installed | `localhost:5432` |
| auth | Windows (IntelliJ or VS Code) | `localhost:3000` |
| trade-api | Windows (IntelliJ) | `localhost:8080` |
| trade-executor | Windows (IntelliJ) | `localhost:8081` |
| UI | Windows (VS Code) | `localhost:4200` |

**Why an SSH forward and not the EC2 IP.** A Kafka client connects to the
address you give it, then reconnects to the address the broker *advertises* --
and compose advertises `localhost:9092`. Given the EC2 IP, a client on Windows
would be told to reconnect to `localhost:9092`, which is the laptop itself.
With `-L 9092:localhost:9092`, `localhost:9092` on the laptop *is* Kafka on
EC2: the advertised address is right as it stands. The IP route would also need
port 9092 open in the security group, and our Kafka has no authentication: anyone
reaching it could read orders and publish `KYC_VERIFIED` (security review, A08).

## On EC2: Kafka only

```bash
git clone https://github.com/Neueda-Learning/chennai-capstone-SE3-Team2.git
cd chennai-capstone-SE3-Team2
git checkout release/sprint9
cp .env.example .env            # placeholders are enough: Kafka reads none of them
docker compose --profile platform up -d kafka
docker exec -i -e BROKER=localhost:29092 -e KAFKA_TOPICS_BIN=/opt/kafka/bin/kafka-topics.sh \
  fauxnance-kafka bash -s < scripts/create-topics.sh
docker ps --format '{{.Names}} {{.Status}}'      # fauxnance-kafka ... (healthy)
```

Compose reads the whole file even to start one service, so `.env` must exist;
`.env.example`'s placeholders satisfy it. The topic script is safe to re-run.
`docker compose --profile platform down -v` clears Kafka's data; create the
topics again after it. Never open port 9092 in the security group.

## On Windows: the SSH connection

```powershell
ssh -i <key.pem> -L 9092:localhost:9092 <user>@<ec2-host>
```

Or add `LocalForward 9092 localhost:9092` to the host's entry in
`C:\Users\<you>\.ssh\config`. Keep that window open while the services run:
closing it cuts Kafka off. Check it from PowerShell:

```powershell
Test-NetConnection localhost -Port 9092      # TcpTestSucceeded : True
```

## On Windows: PostgreSQL, once

The platform targets PostgreSQL 16: `psql --version` should say 16.x. `psql`
lives in `C:\Program Files\PostgreSQL\16\bin`; add that to `PATH` or call it by
its full path.

Choose the three application passwords (`openssl rand -hex 24`, or any letters
and digits), put them in your `.env` as `DB_PASSWORD`, `ANALYTICS_DB_PASSWORD`
and `AUTH_DB_PASSWORD`, then from the repository root, as the `postgres`
superuser (it asks for that password):

```powershell
psql -U postgres -v ON_ERROR_STOP=1 -v trading_pw=<DB_PASSWORD> -v analytics_pw=<ANALYTICS_DB_PASSWORD> -v auth_pw=<AUTH_DB_PASSWORD> -f data/db/local/setup.sql
```

`data/db/local/setup.sql` does what the container's init does, in the same
order: the roles `trading_app`, `analytics_ro` and `auth_app`; the databases
`trading` and `auth`, in UTF8; each role allowed into its own database only;
every schema, migration, index and seed file in the order `docker-compose.yml`
mounts them; then auth's migrations. Checked against a container-built database:
identical schemas, identical seed rows (build timestamps aside), identical grants
and connection rights. One deliberate difference: the container also stops
ordinary roles connecting to the `postgres` maintenance database; on your own
install this leaves that alone.

It stops at the first error, so a second run stops at `role "trading_app"
already exists` and changes nothing. To start again from nothing -- this
**deletes both databases**:

```powershell
psql -U postgres -v ON_ERROR_STOP=1 -f data/db/local/reset.sql
```

A password with `$` or a quote in it needs single quotes in PowerShell
(`-v 'trading_pw=...'`); hex passwords avoid the question.

## On Windows: the services

Every value comes from your `.env`. Spring Boot does not read `.env` itself, so
the two Java services take theirs as run-configuration environment variables
(IntelliJ: **Run > Edit Configurations > Environment variables**, entered as
`NAME=value;NAME=value`).

**trade-api** -- run `com.yellow.trade.TradeApiApplication` (module
`services/trade-api`):

```
DB_HOST=localhost;DB_PORT=5432;DB_NAME=trading;DB_USER=trading_app;DB_PASSWORD=<DB_PASSWORD>;JWT_SECRET=<JWT_SECRET>;ACTIVATION_INTERNAL_SECRET=<ACTIVATION_INTERNAL_SECRET>;ACTIVATION_MAIL_FROM=<ACTIVATION_MAIL_FROM>;SMTP_USERNAME=<SMTP_USERNAME>;SMTP_PASSWORD=<SMTP_PASSWORD>
```

**trade-executor** -- run `com.yellow.executor.ExecutorApplication` (module
`services/trade-executor`):

```
DB_HOST=localhost;DB_PORT=5432;DB_NAME=trading;DB_USER=trading_app;DB_PASSWORD=<DB_PASSWORD>;FAUXNANCE_API_KEY=<FAUXNANCE_API_KEY>
```

Their defaults are already right for this arrangement and need not be set:
`KAFKA_BOOTSTRAP_SERVERS=localhost:9092`, `AUTH_INTERNAL_URL=http://localhost:3000`,
`CORS_ALLOWED_ORIGINS=http://localhost:4200`, and ports 8080 and 8081.

**auth** -- reads `services/auth/.env` (git ignores it). Create it with:

```
PORT=3000
JWT_SECRET=<the same JWT_SECRET as trade-api>
AUTH_DATABASE_URL=postgresql://auth_app:<AUTH_DB_PASSWORD>@localhost:5432/auth
KAFKA_BROKERS=localhost:9092
ACTIVATION_INTERNAL_SECRET=<the same as trade-api>
REDIS_URL=<REDIS_URL>
```

then, in `services/auth`: `npm ci` once, and `npm run start:dev`.

**UI** -- in `frontend`: `npm ci` once, then `npm start`, and open
`http://localhost:4200`.

Start order: the SSH connection, then auth, trade-api, the executor, the UI.
PostgreSQL runs as a Windows service and is already up.

## Check it works

```powershell
curl.exe -s http://localhost:8080/actuator/health     # {"status":"UP",...
curl.exe -s http://localhost:8081/actuator/health     # {"status":"UP",...
```

A login, on seeded account 3 (it has cash and passed KYC):

```powershell
$act = (Invoke-RestMethod -Method Post -Uri http://localhost:3000/internal/activation-tokens -ContentType 'application/json' -Headers @{ 'X-Internal-Secret' = '<ACTIVATION_INTERNAL_SECRET>' } -Body '{"clientId":3}').activationToken
Invoke-RestMethod -Method Post -Uri http://localhost:3000/auth/register -ContentType 'application/json' -Body (@{ username = 'win.test'; password = 'MyTestPassword123'; activationToken = $act } | ConvertTo-Json)
```

Sign in at `http://localhost:4200` and place an order. It comes back `NEW`; the
blotter then shows `FILLED` or `REJECTED` once the executor has read it from
Kafka -- which proves the link to EC2 works. (`REJECTED` with "no price" means
`FAUXNANCE_API_KEY` is not a valid gateway key; everything else worked.)

The Playwright journeys run the same way, with `E2E_BASE_URL`,
`E2E_TRADE_API` and `E2E_AUTH_API` pointing at `localhost`; see
`frontend/README.md`.

## When something is wrong

| You see | Why | Fix |
|---|---|---|
| `Connection to node -1 (localhost/127.0.0.1:9092) could not be established` (Java) or `Connection error` from `[Kafka]` (auth) | The SSH window is closed, or was opened without `-L 9092:localhost:9092` | Reconnect with the forward; `Test-NetConnection localhost -Port 9092` |
| One `[Kafka] ... GroupCoordinator` error from auth just after it starts | A freshly started broker is still choosing its group coordinator; auth retries | Nothing, unless it repeats |
| `password authentication failed for user "trading_app"` | `DB_PASSWORD` is not the `trading_pw` given to `setup.sql` | Use the same value, or as `postgres`: `ALTER ROLE trading_app PASSWORD '<new>';` |
| `database "trading" does not exist` | `setup.sql` has not been run | Run it |
| `role "trading_app" already exists` from `setup.sql` | It has run before | Keep the databases, or `reset.sql` then `setup.sql` (deletes them) |
| `psql` is not recognised | Not on `PATH` | Add `C:\Program Files\PostgreSQL\16\bin`, or use the full path |
| trade-api health says `DOWN`, but the API answers | Its health check logs in to the mail server and the SMTP values are wrong | Fix `SMTP_USERNAME` / `SMTP_PASSWORD`; nothing else depends on it |
| `Port 8080 was already in use` (Java) or `EADDRINUSE` (auth) | Something else holds the port, often a Docker stack also running | Stop it: one arrangement at a time |
| Every order comes back `REJECTED` | The executor cannot get prices | A valid `FAUXNANCE_API_KEY` |
