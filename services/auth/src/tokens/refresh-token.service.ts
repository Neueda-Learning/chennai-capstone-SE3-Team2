import { Injectable, Logger } from '@nestjs/common';
import { createHash, randomBytes } from 'node:crypto';
import { RefreshTokenRepository } from './refresh-token.repository';
import { PlatformError } from '../common/platform-error';

export const REFRESH_TOKEN_DAYS = 7;

@Injectable()
export class RefreshTokenService {
  private readonly log = new Logger(RefreshTokenService.name);

  constructor(private readonly store: RefreshTokenRepository) {}

  /** The value the client gets, and the hash we keep, are never the same thing. */
  async issue(credentialId: string): Promise<string> {
    const token = randomBytes(32).toString('hex');
    const expiresAt = new Date(Date.now() + REFRESH_TOKEN_DAYS * 24 * 60 * 60 * 1000);
    await this.store.store(this.fingerprint(token), credentialId, expiresAt);
    return token;
  }

  /**
   * Exchanges a refresh token for a new one, and returns whose it was.
   *
   * We revoke the presented token as well as reissuing. Reissue alone defends
   * against a token stolen and used once, but the old value still works, so
   * both parties end up with live sessions and nothing can tell there are two.
   */
  async rotate(presented: string): Promise<string> {
    const hash = this.fingerprint(presented);
    const stored = await this.store.find(hash);

    if (!stored || stored.expiresAt.getTime() < Date.now()) {
      throw PlatformError.unauthorised();
    }

    if (stored.exchangedAt) {
      // Already exchanged. Either a client repeated a request or a token was
      // stolen, and we cannot tell which, so we treat it as theft.
      const revoked = await this.store.revokeAllFor(stored.credentialId);
      this.log.warn(
        `replayed refresh token for credential ${stored.credentialId}: ` +
        `revoked ${revoked} live token(s)`,
      );
      throw PlatformError.unauthorised();
    }

    if (!(await this.store.markExchanged(hash))) {
      // Lost the race with a concurrent refresh of the same token.
      throw PlatformError.unauthorised();
    }

    return stored.credentialId;
  }

  /** SHA-256 is right here and wrong for passwords: this value is already 256 bits of entropy. */
  private fingerprint(token: string): string {
    return createHash('sha256').update(token).digest('hex');
  }
}
