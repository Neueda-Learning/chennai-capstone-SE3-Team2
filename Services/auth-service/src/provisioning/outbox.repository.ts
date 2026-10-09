import { Inject, Injectable } from '@nestjs/common';
import { Pool, PoolClient } from 'pg';
import { AUTH_POOL } from '../database/database.module';
import { Envelope } from '../messaging/envelope';

export interface OutboxRow {
  eventId: string;
  topic: string;
  messageKey: string;
  envelope: Envelope<unknown>;
  attempts: number;
}

@Injectable()
export class OutboxRepository {
  constructor(@Inject(AUTH_POOL) private readonly pool: Pool) {}

  /** Takes the caller's client so the row joins the caller's transaction. */
  async add(client: PoolClient, topic: string, messageKey: string, envelope: Envelope<unknown>): Promise<void> {
    await client.query(
      `INSERT INTO outbox_event (event_id, topic, message_key, envelope) VALUES ($1, $2, $3, $4)`,
      [envelope.eventId, topic, messageKey, JSON.stringify(envelope)],
    );
  }

  /**
   * Oldest unpublished rows, locked for this transaction. SKIP LOCKED lets a
   * second instance relay a different batch rather than wait on this one.
   */
  async lockBatch(client: PoolClient, limit: number): Promise<OutboxRow[]> {
    const { rows } = await client.query(
      `SELECT event_id, topic, message_key, envelope, attempts
         FROM outbox_event
        WHERE published_at IS NULL
        ORDER BY created_at
        LIMIT $1
          FOR UPDATE SKIP LOCKED`,
      [limit],
    );
    return rows.map((r) => ({
      eventId: r.event_id,
      topic: r.topic,
      messageKey: r.message_key,
      envelope: r.envelope,
      attempts: r.attempts,
    }));
  }

  async markPublished(client: PoolClient, eventId: string): Promise<void> {
    await client.query(`UPDATE outbox_event SET published_at = now(), last_error = NULL WHERE event_id = $1`, [eventId]);
  }

  async recordFailure(client: PoolClient, eventId: string, error: string): Promise<void> {
    await client.query(
      `UPDATE outbox_event SET attempts = attempts + 1, last_error = $2 WHERE event_id = $1`,
      [eventId, error.slice(0, 500)],
    );
  }

  connect(): Promise<PoolClient> {
    return this.pool.connect();
  }
}
