# Kafka

One broker, `apache/kafka:3.8.0` in KRaft mode, defined as the `kafka` service in
the root `docker-compose.yml`. From your machine it is `localhost:9092`
(`Config/application.yml`, `KAFKA_BOOTSTRAP_SERVERS`).

The broker never creates a topic on its own. `create-topics.sh` creates every
topic, and is safe to run again. Compose runs it for you as the one-shot
`kafka-init` service, and every application service waits for it to finish.

| Topic | Partitions | Key | Retention | Producer | Consumer groups |
|---|---|---|---|---|---|
| `orders` | 3 | accountId | 7 days | order-service | `trade-executor` |
| `trade-events` | 3 | accountId | 30 days | executor-service; order-service (cancellations) | `notification-service`, `portfolio-service`, `strategy-service` |
| `market-data` | 6 | symbol | 1 day | executor-service (poller) | `watchlist-service`, `advice-service`, `strategy-service` |
| `kyc-events` | 3 | clientId | 7 days | order-service (KYC) | `auth-provisioning` |
| `account-provisioning` | 3 | clientId | 7 days | auth-service | `activation-mailer` |
| `*.DLT` | 1 each | as the parent | as the parent | the consumer that gave up | none: for investigation |

To create the topics by hand against the running broker:

```bash
docker exec -i -e BROKER=localhost:29092 -e KAFKA_TOPICS_BIN=/opt/kafka/bin/kafka-topics.sh \
  fauxnance-kafka bash -s < Infrastructure/Kafka/create-topics.sh
```

(Inside its own container the broker listens on 29092; 9092 is the port it
publishes to your machine.)

The event contract every topic follows is `Contracts/API Schemas/kafka-topics.md`;
the design reasons are in `docs/architecture/kafka.md`.
