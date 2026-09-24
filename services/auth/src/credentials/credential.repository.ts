import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { randomUUID } from 'node:crypto';
import { AUTH_POOL } from '../database/database.module';

export interface Credential {
  id: string;
  username: string;
  passwordHash: string;
  accountId: number;
  roles: string[];
}

@Injectable()
export class CredentialRepository {
  constructor(@Inject(AUTH_POOL) private readonly pool: Pool) {}

  async findByUsername(username: string): Promise<Credential | null> {
    const { rows } = await this.pool.query(
      `SELECT id, username, password_hash, account_id, roles
         FROM credential WHERE username = $1`,
      [username],
    );
    return rows.length ? this.toCredential(rows[0]) : null;
  }

  async findById(id: string): Promise<Credential | null> {
    const { rows } = await this.pool.query(
      `SELECT id, username, password_hash, account_id, roles
         FROM credential WHERE id = $1`,
      [id],
    );
    return rows.length ? this.toCredential(rows[0]) : null;
  }

  /**
   * Claims a provisioned account and creates the credential, in one transaction.
   * Returns null when the account is unknown or already claimed.
   */
  async claim(
    username: string,
    passwordHash: string,
    accountId: number,
    roles: string[],
  ): Promise<Credential | null> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');

      // Conditional, so two simultaneous registrations cannot both claim it.
      const claimed = await client.query(
        `UPDATE provisioned_account
            SET claimed_by = $1, claimed_at = now()
          WHERE account_id = $2 AND claimed_by IS NULL`,
        [null, accountId],
      );
      if (claimed.rowCount === 0) {
        await client.query('ROLLBACK');
        return null;
      }

      const id = randomUUID();
      const { rows } = await client.query(
        `INSERT INTO credential (id, username, password_hash, account_id, roles)
              VALUES ($1, $2, $3, $4, $5)
           RETURNING id, username, password_hash, account_id, roles`,
        [id, username, passwordHash, accountId, roles],
      );
      await client.query(`UPDATE provisioned_account SET claimed_by = $1 WHERE account_id = $2`,
        [id, accountId]);

      await client.query('COMMIT');
      return this.toCredential(rows[0]);
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  private toCredential(row: any): Credential {
    return {
      id: row.id,
      username: row.username,
      passwordHash: row.password_hash,
      accountId: Number(row.account_id),
      roles: row.roles,
    };
  }
}
