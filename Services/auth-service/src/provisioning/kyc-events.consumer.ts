import { Inject, Injectable, Logger, OnApplicationBootstrap, OnApplicationShutdown } from '@nestjs/common';
import { Consumer, EachMessagePayload, Kafka } from 'kafkajs';
import { KAFKA, KafkaPublisher } from '../messaging/kafka-publisher';
import { KYC_CONSUMER_GROUP, Topics } from '../messaging/topics';
import { interpretKycMessage } from './kyc-message';
import { ProvisioningService } from './provisioning.service';

const RECONNECT_MS = 10_000;

/**
 * Consumes kyc-events and provisions each verified customer.
 *
 * Offsets commit only after the handler returns. A database failure throws,
 * so kafkajs retries the same message rather than losing a verification. A
 * message auth cannot read goes to kyc-events.DLT on the first attempt, with
 * the reason in its headers, and the partition moves on.
 */
@Injectable()
export class KycEventsConsumer implements OnApplicationBootstrap, OnApplicationShutdown {
  private readonly log = new Logger(KycEventsConsumer.name);
  private consumer: Consumer | null = null;
  private retryTimer: NodeJS.Timeout | null = null;
  private stopped = false;

  constructor(
    @Inject(KAFKA) private readonly kafka: Kafka,
    private readonly provisioning: ProvisioningService,
    private readonly publisher: KafkaPublisher,
  ) {}

  onApplicationBootstrap(): void {
    // Not awaited: the HTTP routes must come up whether or not the broker is there.
    void this.start();
  }

  async onApplicationShutdown(): Promise<void> {
    this.stopped = true;
    if (this.retryTimer) clearTimeout(this.retryTimer);
    await this.consumer?.disconnect().catch(() => undefined);
  }

  async handle({ topic, partition, message }: EachMessagePayload): Promise<void> {
    const decision = interpretKycMessage(message.value);

    switch (decision.kind) {
      case 'provision':
        await this.provisioning.provision(decision.clientId);
        return;
      case 'ignore':
        this.log.debug(`ignored ${decision.eventType} on ${topic}`);
        return;
      case 'poison':
        this.log.warn(`dead-lettering ${topic}-${partition}@${message.offset}: ${decision.reason}`);
        await this.publisher.sendRaw(Topics.KYC_EVENTS_DLT, message.key, message.value, {
          'x-failure-reason': decision.reason,
          'x-failure-class': 'POISON',
          'x-original-topic': topic,
          'x-original-partition': String(partition),
          'x-original-offset': message.offset,
          'x-attempt-count': '1',
          'x-failed-at': new Date().toISOString(),
        });
        return;
    }
  }

  private async start(): Promise<void> {
    if (this.stopped) return;
    const consumer = this.kafka.consumer({ groupId: KYC_CONSUMER_GROUP });
    try {
      await consumer.connect();
      // A new group reads the backlog: a customer verified while auth was down
      // is still a customer waiting for an email.
      await consumer.subscribe({ topic: Topics.KYC_EVENTS, fromBeginning: true });
      await consumer.run({ autoCommit: true, eachMessage: (payload) => this.handle(payload) });
      this.consumer = consumer;
      consumer.on(consumer.events.CRASH, (event) => {
        if (!event.payload.restart) this.scheduleRestart(`consumer crashed: ${event.payload.error.message}`);
      });
    } catch (error) {
      await consumer.disconnect().catch(() => undefined);
      this.scheduleRestart(error instanceof Error ? error.message : String(error));
    }
  }

  private scheduleRestart(reason: string): void {
    if (this.stopped) return;
    this.consumer = null;
    this.log.warn(`kyc-events consumer not running (${reason}); retrying in ${RECONNECT_MS / 1000}s`);
    this.retryTimer = setTimeout(() => void this.start(), RECONNECT_MS);
  }
}
