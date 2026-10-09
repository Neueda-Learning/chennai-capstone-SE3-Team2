import { Inject, Injectable, Logger } from '@nestjs/common';
import { Pool } from 'pg';
import { AUTH_POOL } from '../database/database.module';
import { accountProvisioned } from '../messaging/envelope';
import { Topics } from '../messaging/topics';
import { OutboxRepository } from './outbox.repository';

/**
 * Makes an account claimable, and announces it.
 *
 * The row and its announcement commit together or not at all. The event is not
 * sent from here: it is written to the outbox in the same transaction, and the
 * relay publishes it only once that transaction has committed. So an event can
 * never announce an account that rolled back, and a broker outage can never
 * fail a provisioning.
 */
@Injectable()
export class ProvisioningService {
  private readonly log = new Logger(ProvisioningService.name);

  constructor(
    @Inject(AUTH_POOL) private readonly pool: Pool,
    private readonly outbox: OutboxRepository,
  ) {}

  /** @returns true when the account was newly provisioned; false when it already was. */
  async provision(clientId: number): Promise<boolean> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');

      // A redelivered KYC event, or a second verification, inserts nothing and
      // therefore announces nothing: one account, one activation email.
      const inserted = await client.query(
        `INSERT INTO provisioned_account (account_id) VALUES ($1)
         ON CONFLICT (account_id) DO NOTHING`,
        [clientId],
      );
      if (inserted.rowCount !== 1) {
        await client.query('ROLLBACK');
        this.log.log(`account ${clientId} already provisioned; nothing published`);
        return false;
      }

      await this.outbox.add(client, Topics.ACCOUNT_PROVISIONING, String(clientId), accountProvisioned(clientId));

      await client.query('COMMIT');
      this.log.log(`account ${clientId} provisioned; ACCOUNT_PROVISIONED queued`);
      return true;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }
}
