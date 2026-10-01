import { Inject, Injectable, Logger, OnModuleDestroy } from '@nestjs/common';
import { IHeaders, Kafka, Producer } from 'kafkajs';

export const KAFKA = 'KAFKA';

/**
 * One idempotent producer for the service, connected on first use.
 *
 * Connecting lazily is what lets the service start, and keep serving logins,
 * while the broker is down: nothing on the request path waits for Kafka.
 */
@Injectable()
export class KafkaPublisher implements OnModuleDestroy {
  private readonly log = new Logger(KafkaPublisher.name);
  private producer: Producer | null = null;
  private connecting: Promise<Producer> | null = null;

  constructor(@Inject(KAFKA) private readonly kafka: Kafka) {}

  async send(topic: string, key: string, value: unknown, headers?: IHeaders): Promise<void> {
    await this.sendRaw(topic, key, JSON.stringify(value), headers);
  }

  /** The bytes as given: a dead-lettered message keeps its original value. */
  async sendRaw(topic: string, key: Buffer | string | null, value: Buffer | string | null, headers?: IHeaders): Promise<void> {
    const producer = await this.connected();
    try {
      await producer.send({
        topic,
        // acks=-1 is the idempotent producer's requirement and kafkajs's default for it.
        acks: -1,
        messages: [{ key, value, headers }],
      });
    } catch (error) {
      // A failed send may leave the connection unusable; reconnect on the next call.
      await this.reset();
      throw error;
    }
  }

  async onModuleDestroy(): Promise<void> {
    await this.reset();
  }

  private async connected(): Promise<Producer> {
    if (this.producer) return this.producer;
    if (!this.connecting) {
      const producer = this.kafka.producer({ idempotent: true, maxInFlightRequests: 5 });
      this.connecting = producer
        .connect()
        .then(() => {
          this.producer = producer;
          return producer;
        })
        .finally(() => {
          this.connecting = null;
        });
    }
    return this.connecting;
  }

  private async reset(): Promise<void> {
    const producer = this.producer;
    this.producer = null;
    if (producer) {
      await producer.disconnect().catch((e: Error) => this.log.warn(`producer disconnect failed: ${e.message}`));
    }
  }
}
