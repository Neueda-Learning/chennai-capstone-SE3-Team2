import { Inject, Injectable } from '@nestjs/common';
import { Pool } from 'pg';
import { AUTH_POOL } from '../database/database.module';

export interface StoredRefreshToken {
  tokenHash: string;
  credentialId: string;
  expiresAt: Date;
  exchangedAt: Date | null;
}

@Injectable()
export class RefreshTokenRepository {
  constructor(@Inject(AUTH_POOL) private readonly pool: Pool) {}

  async store(tokenHash: string, credentialId: string, expiresAt: Date): Promise<void> {
    await this.pool.query(
      `INSERT INTO refresh_token (token_hash, credential_id, expires_at) VALUES ($1, $2, $3)`,
      [tokenHash, credentialId, expiresAt],
    );
  }

  async find(tokenHash: string): Promise<StoredRefreshToken | null> {
    const { rows } = await this.pool.query(
      `SELECT token_hash, credential_id, expires_at, exchanged_at
         FROM refresh_token WHERE token_hash = $1`,
      [tokenHash],
    );
    if (!rows.length) return null;
    return {
      tokenHash: rows[0].token_hash,
      credentialId: rows[0].credential_id,
      expiresAt: rows[0].expires_at,
      exchangedAt: rows[0].exchanged_at,
    };
  }

  /**
   * Marks the token exchanged, conditional on it not already being exchanged.
   * Zero rows means somebody got there first, which the caller treats as theft.
   */
  async markExchanged(tokenHash: string): Promise<boolean> {
    const { rowCount } = await this.pool.query(
      `UPDATE refresh_token SET exchanged_at = now()
        WHERE token_hash = $1 AND exchanged_at IS NULL`,
      [tokenHash],
    );
    return rowCount === 1;
  }

  /** Every live token for this user, when a replay says one of them is loose. */
  async revokeAllFor(credentialId: string): Promise<number> {
    const { rowCount } = await this.pool.query(
      `UPDATE refresh_token SET exchanged_at = now()
        WHERE credential_id = $1 AND exchanged_at IS NULL`,
      [credentialId],
    );
    return rowCount ?? 0;
  }
}
