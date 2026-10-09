import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { AUTH_POOL } from '../database/database.module';

export type MintOutcome = 'minted' | 'not-provisioned' | 'already-claimed';

@Injectable()
export class ActivationTokenRepository {
  constructor(@Inject(AUTH_POOL) private readonly pool: Pool) {}

  /**
   * Stores a new token for a client and revokes any earlier unused one, in one
   * transaction, so exactly one link is live per client at a time.
   */
  async replaceFor(clientId: number, tokenHash: string, expiresAt: Date): Promise<MintOutcome> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');

      // Locked, so a registration cannot claim the account between this check
      // and the insert below.
      const { rows } = await client.query(
        `SELECT claimed_by FROM provisioned_account WHERE account_id = $1 FOR UPDATE`,
        [clientId],
      );
      if (!rows.length) {
        await client.query('ROLLBACK');
        return 'not-provisioned';
      }
      if (rows[0].claimed_by !== null) {
        await client.query('ROLLBACK');
        return 'already-claimed';
      }

      await client.query(
        `UPDATE activation_token SET revoked_at = now()
          WHERE client_id = $1 AND used_at IS NULL AND revoked_at IS NULL`,
        [clientId],
      );
      await client.query(
        `INSERT INTO activation_token (token_hash, client_id, expires_at) VALUES ($1, $2, $3)`,
        [tokenHash, clientId, expiresAt],
      );

      await client.query('COMMIT');
      return 'minted';
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  /** True when the token exists and is unused, unrevoked and unexpired. Consumes nothing. */
  async isUsable(tokenHash: string): Promise<boolean> {
    const { rowCount } = await this.pool.query(
      `SELECT 1 FROM activation_token
        WHERE token_hash = $1 AND used_at IS NULL AND revoked_at IS NULL AND expires_at > now()`,
      [tokenHash],
    );
    return rowCount === 1;
  }
}
