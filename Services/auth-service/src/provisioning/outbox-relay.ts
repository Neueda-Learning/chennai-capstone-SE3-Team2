import { Injectable, Logger, OnApplicationBootstrap, OnApplicationShutdown } from '@nestjs/common';
import { Env } from '../config/env';
import { KafkaPublisher } from '../messaging/kafka-publisher';
import { OutboxRepository } from './outbox.repository';

/** The log line to alert on: an announcement that the broker has not yet taken. */
export const OUTBOX_PUBLISH_FAILED = 'OUTBOX_PUBLISH_FAILED';

const BATCH_SIZE = 20;

/**
 * Publishes committed outbox rows, oldest first.
 *
 * It only ever sees committed rows, which is what makes every event "after the
 * commit". A row is marked published only once the broker has acknowledged it,
 * so a crash between the two re-sends it: consumers are idempotent on eventId.
 */
@Injectable()
export class OutboxRelay implements OnApplicationBootstrap, OnApplicationShutdown {
  private readonly log = new Logger(OutboxRelay.name);
  private timer: NodeJS.Timeout | null = null;
  private running = false;

  constructor(
    private readonly outbox: OutboxRepository,
    private readonly publisher: KafkaPublisher,
    private readonly env: Env,
  ) {}

  onApplicationBootstrap(): void {
    this.timer = setInterval(() => void this.relayOnce(), this.env.outboxPollMs);
  }

  onApplicationShutdown(): void {
    if (this.timer) clearInterval(this.timer);
  }

  /** @returns how many rows were published this tick. */
  async relayOnce(): Promise<number> {
    // A slow broker must not stack ticks on top of each other.
    if (this.running) return 0;
    this.running = true;

    let published = 0;
    let client;
    try {
      client = await this.outbox.connect();
      await client.query('BEGIN');
      const batch = await this.outbox.lockBatch(client, BATCH_SIZE);

      for (const row of batch) {
        try {
          await this.publisher.send(row.topic, row.messageKey, row.envelope);
          await this.outbox.markPublished(client, row.eventId);
          published++;
        } catch (error) {
          const message = error instanceof Error ? error.message : String(error);
          await this.outbox.recordFailure(client, row.eventId, message);
          this.log.warn(
            `${OUTBOX_PUBLISH_FAILED} event=${row.eventId} topic=${row.topic} attempts=${row.attempts + 1} reason=${message}`,
          );
          // The broker is the likely cause; the rest of the batch would fail the same way.
          break;
        }
      }

      await client.query('COMMIT');
    } catch (error) {
      if (client) await client.query('ROLLBACK').catch(() => undefined);
      this.log.error(`outbox relay tick failed: ${error instanceof Error ? error.message : String(error)}`);
    } finally {
      client?.release();
      this.running = false;
    }
    return published;
  }
}
