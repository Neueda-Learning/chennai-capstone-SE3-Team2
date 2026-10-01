import { Injectable } from '@nestjs/common';
import { createHash, randomBytes } from 'node:crypto';
import { PlatformError } from '../common/platform-error';
import { ActivationTokenRepository } from './activation-token.repository';

/**
 * Twenty-four hours: long enough for a customer who reads email once a day,
 * short enough that a link found in an old inbox is dead.
 */
export const ACTIVATION_TOKEN_HOURS = 24;

export interface MintedToken {
  activationToken: string;
  expiresAt: Date;
}

/** SHA-256 is right here, as for refresh tokens: the value is already 256 bits of random. */
export function fingerprint(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

@Injectable()
export class ActivationTokenService {
  constructor(private readonly store: ActivationTokenRepository) {}

  /**
   * Mints a token, stores only its hash, and returns the plaintext. This is
   * the only time the plaintext exists on this side; it cannot be read back.
   * A second call for the same client revokes the first token.
   */
  async mint(clientId: number, now: Date = new Date()): Promise<MintedToken> {
    const activationToken = randomBytes(32).toString('hex');
    const expiresAt = new Date(now.getTime() + ACTIVATION_TOKEN_HOURS * 60 * 60 * 1000);

    const outcome = await this.store.replaceFor(clientId, fingerprint(activationToken), expiresAt);
    if (outcome === 'not-provisioned') throw PlatformError.notProvisioned();
    if (outcome === 'already-claimed') throw PlatformError.alreadyClaimed();

    return { activationToken, expiresAt };
  }

  isUsable(activationToken: string): Promise<boolean> {
    return this.store.isUsable(fingerprint(activationToken));
  }
}
