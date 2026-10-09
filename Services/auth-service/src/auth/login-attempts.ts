import { Inject, Injectable } from '@nestjs/common';
import { Request } from 'express';
import { ATTEMPT_STORE, AttemptStore } from './attempt-store';

/** Five failures from one address, then a one minute cooldown. */
export const MAX_ATTEMPTS = 5;
export const COOLDOWN_MS = 60_000;

/**
 * The counter behind the login throttle.
 *
 * It lives here rather than on the guard because Nest instantiates a guard
 * passed to @UseGuards separately from the copy injected into a controller.
 * Two instances, two maps, and a throttle that never fires. Sharing the state
 * rather than the instance is what makes it work.
 *
 * The same holds across processes: the count lives in the AttemptStore, which
 * in a deployment is Redis, so every instance sees every failure (A07). If
 * Redis is down the store falls back to memory and the limit is per instance
 * until it returns -- see FallbackAttemptStore.
 */
@Injectable()
export class LoginAttempts {
  constructor(@Inject(ATTEMPT_STORE) private readonly store: AttemptStore) {}

  static callerKey(request: Request): string {
    return request.ip ?? 'unknown';
  }

  async isBlocked(caller: string): Promise<boolean> {
    return (await this.store.count(caller)) >= MAX_ATTEMPTS;
  }

  /** The first failure opens the window; later ones do not extend it. */
  async recordFailure(caller: string): Promise<void> {
    await this.store.increment(caller, COOLDOWN_MS);
  }

  /** A success clears the count. */
  async recordSuccess(caller: string): Promise<void> {
    await this.store.clear(caller);
  }
}
