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

export type RegistrationOutcome =
  | { kind: 'registered'; credential: Credential }
  | { kind: 'invalid-token' }
  | { kind: 'username-taken' };

/** Postgres unique_violation. */
const UNIQUE_VIOLATION = '23505';

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

  /** The login bound to an account, for a strategy acting for it (Sprint 10). */
  async findByAccountId(accountId: number): Promise<Credential | null> {
    const { rows } = await this.pool.query(
      `SELECT id, username, password_hash, account_id, roles
         FROM credential WHERE account_id = $1`,
      [accountId],
    );
    return rows.length ? this.toCredential(rows[0]) : null;
  }

  /**
   * Consumes the activation token, claims its account and creates the
   * credential, in one transaction. A token consumed without a credential
   * would be an account nobody could ever claim, so any refusal rolls all
   * three back and the token stays usable.
   */
  async registerWithActivationToken(
    username: string,
    passwordHash: string,
    tokenHash: string,
  ): Promise<RegistrationOutcome> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');

      // Conditional, so two registrations presenting the same token cannot both
      // consume it. Absent, expired, used and revoked all land here alike.
      const consumed = await client.query(
        `UPDATE activation_token SET used_at = now()
          WHERE token_hash = $1 AND used_at IS NULL AND revoked_at IS NULL AND expires_at > now()
         RETURNING client_id`,
        [tokenHash],
      );
      if (consumed.rowCount !== 1) {
        await client.query('ROLLBACK');
        return { kind: 'invalid-token' };
      }
      const accountId = Number(consumed.rows[0].client_id);
      const id = randomUUID();

      // The same guarded update as before: a claim is once-only.
      const claimed = await client.query(
        `UPDATE provisioned_account SET claimed_by = $1, claimed_at = now()
          WHERE account_id = $2 AND claimed_by IS NULL`,
        [id, accountId],
      );
      if (claimed.rowCount !== 1) {
        await client.query('ROLLBACK');
        return { kind: 'invalid-token' };
      }

      const { rows } = await client.query(
        `INSERT INTO credential (id, username, password_hash, account_id, roles)
              VALUES ($1, $2, $3, $4, $5)
           RETURNING id, username, password_hash, account_id, roles`,
        [id, username, passwordHash, accountId, ['CUSTOMER']],
      );

      await client.query('COMMIT');
      return { kind: 'registered', credential: this.toCredential(rows[0]) };
    } catch (error) {
      await client.query('ROLLBACK');
      if ((error as { code?: string }).code === UNIQUE_VIOLATION) {
        return { kind: 'username-taken' };
      }
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
