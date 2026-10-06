# Development on Windows, with Kafka on EC2

Only Kafka runs in a container, on an EC2 instance. PostgreSQL, auth,
trade-api, the executor and the UI run on the Windows laptop. One SSH
connection carries Kafka's port to the laptop, and one `.env` file at the
repository root holds every service's settings.

`docker-compose.yml` still starts the whole platform with one command; this is
a second way to run it, for development.

## What runs where

| Piece | Where | Address |
|---|---|---|
| Kafka | EC2, the `kafka` container | `localhost:9092`, through the SSH forward |
| PostgreSQL 16 | Windows, installed | `localhost:5432`, databases `trading` and `auth` |
| auth | Windows | `localhost:3000` |
| trade-api | Windows (IntelliJ) | `localhost:8085` |
| trade-executor | Windows (IntelliJ) | `localhost:8081` |
| UI | Windows | `localhost:4200` |

**Why an SSH forward and not the EC2 IP.** A Kafka client reconnects to the
address the broker *advertises*, and it advertises `localhost:9092`. With
`-L 9092:localhost:9092`, `localhost:9092` on the laptop *is* Kafka on EC2. The
IP route would also need port 9092 open to the internet, and our Kafka has no
authentication. Never open 9092 in the security group.

## 1. On EC2: Kafka only

```bash
git clone https://github.com/Neueda-Learning/chennai-capstone-SE3-Team2.git
cd chennai-capstone-SE3-Team2
git checkout kafka-container
cp .env.example .env            # the placeholders are enough: Kafka reads none of them
docker compose --profile platform up -d kafka
docker exec -i -e BROKER=localhost:29092 -e KAFKA_TOPICS_BIN=/opt/kafka/bin/kafka-topics.sh \
  fauxnance-kafka bash -s < scripts/create-topics.sh
docker ps --format '{{.Names}} {{.Status}}'      # fauxnance-kafka ... (healthy)
```

The topic script is safe to re-run. `docker compose --profile platform down -v`
clears Kafka's data; create the topics again after it.

## 2. On Windows: the SSH connection

Keep this PowerShell window open while anything runs: closing it cuts Kafka off.

```powershell
ssh -i <key.pem> -L 9092:localhost:9092 <user>@<ec2-host>
Test-NetConnection localhost -Port 9092      # in another window: TcpTestSucceeded : True
```

## 3. On Windows: the .env, once

```powershell
git clone https://github.com/Neueda-Learning/chennai-capstone-SE3-Team2.git
cd chennai-capstone-SE3-Team2
git checkout kafka-container
copy .env.example .env
```

Fill every value marked `REQUIRED` in `.env`:

- `POSTGRES_SUPERUSER_PASSWORD`: the password you gave PostgreSQL when installing it.
- `DB_PASSWORD`, `ANALYTICS_DB_PASSWORD`, `AUTH_DB_PASSWORD`: choose them
  (`openssl rand -hex 24`, or any letters and digits). Step 4 creates the
  roles with these.
- `JWT_SECRET`, `ACTIVATION_INTERNAL_SECRET`: `openssl rand -hex 32` each.
- From the team, privately, never in a group chat: `FAUXNANCE_API_KEY`,
  `MF_NAV_API_KEY`, `REDIS_URL`, `SMTP_USERNAME`, `SMTP_PASSWORD`,
  `ACTIVATION_MAIL_FROM`.

Everything else is already right for this arrangement. No quotes, no comments
after a value, no `$` in values.

## 4. On Windows: PostgreSQL, once

`psql --version` should say 16.x. It lives in `C:\Program Files\PostgreSQL\16\bin`;
add that to `PATH` or call it by its full path. From the repository root, with
the three passwords from your `.env` (it asks for the `postgres` password):

```powershell
psql -U postgres -v ON_ERROR_STOP=1 -v trading_pw=<DB_PASSWORD> -v analytics_pw=<ANALYTICS_DB_PASSWORD> -v auth_pw=<AUTH_DB_PASSWORD> -f data/db/local/setup.sql
```

`data/db/local/setup.sql` does what the container's init does, in the same
order: the roles `trading_app`, `analytics_ro` and `auth_app`; the databases
`trading` and `auth` on the one instance; each role allowed into its own
database only; every schema, migration (001 to 010), index and seed file
(001 to 007); then auth's migrations.

It stops at the first error, so a second run stops at `role "trading_app"
already exists` and changes nothing. To start again from nothing -- this
**deletes both databases**:

```powershell
psql -U postgres -v ON_ERROR_STOP=1 -f data/db/local/reset.sql
```

**Set up from the old `feature/windows-dev-setup` branch already?** That one
stopped at migration 008. Either reset and run `setup.sql` again, or add the
rest, keeping your data:

```powershell
psql -U postgres -d trading -v ON_ERROR_STOP=1 -f data/db/migrations/009_bank_account.sql
psql -U postgres -d trading -v ON_ERROR_STOP=1 -f data/db/migrations/010_payments.sql
psql -U postgres -d trading -v ON_ERROR_STOP=1 -f data/db/seed/006_bank_accounts.sql
psql -U postgres -d trading -v ON_ERROR_STOP=1 -f data/db/seed/007_mf_nav_funds.sql
```

## 5. On Windows: the services

Every service reads the repository-root `.env` itself. Nothing is typed into a
run configuration -- and **delete any environment variables an older run
configuration still sets**: a variable set there wins over `.env`.

Start them in this order, each in its own window:

1. **auth** -- in `services/auth`: `npm ci` once, then `npm run start:dev`.
2. **trade-api** -- IntelliJ: run `com.yellow.trade.TradeApiApplication`
   (module `services/trade-api`).
3. **trade-executor** -- IntelliJ: run `com.yellow.executor.ExecutorApplication`
   (module `services/trade-executor`).
4. **UI** -- in `frontend`: `npm ci` once, then `npm start`.

The Java services look for `.env` in their working directory and two levels up,
so either the repository root or the module folder works as the working
directory. PostgreSQL runs as a Windows service and is already up.

## 6. Check it works

```powershell
curl.exe -s http://localhost:8085/actuator/health     # {"status":"UP",...
curl.exe -s http://localhost:8081/actuator/health     # {"status":"UP",...
```

Then in the browser, `http://localhost:4200`:

1. **New customer? Open an account** -- a real email you can read, a new PAN,
   a bank account of 9 to 18 digits not ending in 0000, IFSC `DEMO0000001`.
2. The email arrives about 40 seconds later -- KYC passed, and Kafka carried
   it to auth and back. Open the link, choose a username and password.
3. **Cash** -- add 2,00,000 twice. Each shows PROCESSING, then SUCCESS.
4. **Place an order** -- `MRF.NS`, Buy 1, limit 130000. The blotter shows
   FILLED once the executor has read it from Kafka.
5. **Place an order** -- a fund, e.g. `120716` UTI Nifty 50 Index Fund, Buy
   10, limit 200. It fills at the day's NAV.
6. **Sell** from the holdings, then **Cash** -- **Withdraw**.

## When something is wrong

| You see | Why | Fix |
|---|---|---|
| `Connection to node -1 (localhost/127.0.0.1:9092) could not be established` (Java) or `Connection error` from `[Kafka]` (auth) | The SSH window is closed, or opened without `-L 9092:localhost:9092` | Reconnect with the forward; `Test-NetConnection localhost -Port 9092` |
| `password authentication failed for user "trading_app"` (or `auth_app`) | The `.env` password is not the one given to `setup.sql` | Make them match, or as `postgres`: `ALTER ROLE trading_app PASSWORD '<new>';` |
| A setting in `.env` seems ignored | An IntelliJ run configuration still sets that variable, and it wins | Remove it from **Run > Edit Configurations > Environment variables** |
| `JWT_SECRET is not set` (auth) or `Could not resolve placeholder` (Java) | `.env` is missing, or not at the repository root | `copy .env.example .env` at the root and fill it |
| `database "trading" does not exist` | `setup.sql` has not been run | Run it (step 4) |
| `relation "bank_account" does not exist`, or KYC never passes | The database stops at migration 008 | The four commands in step 4 |
| `Port 8085 was already in use` (Java) or `EADDRINUSE` (auth) | Something else holds the port, often a Docker stack also running | Stop it: one arrangement at a time |
| trade-api health says `DOWN`, but the API answers | Its health check logs in to the mail server and the SMTP values are wrong | Fix `SMTP_USERNAME` / `SMTP_PASSWORD` |
| Stock orders all `REJECTED` | No price at all: a wrong `FAUXNANCE_API_KEY`, or the day's quota is spent | Check the key and `GET /usage`. A stale quote is not the cause: it is used |
| Fund orders all `REJECTED` | No NAV: `MF_NAV_API_KEY` is empty or wrong, or the fund is one of the three fictional ones | Set the key; use a real fund (`120586`, `122639`, `120716`, `118989`) |
