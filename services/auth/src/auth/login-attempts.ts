import { Injectable } from '@nestjs/common';
import { Request } from 'express';

/** Five failures from one address, then a one minute cooldown. */
export const MAX_ATTEMPTS = 5;
export const COOLDOWN_MS = 60_000;

interface Attempts {
  count: number;
  firstAt: number;
}

/**
 * The counter behind the login throttle.
 *
 * It lives here rather than on the guard because Nest instantiates a guard
 * passed to @UseGuards separately from the copy injected into a controller.
 * Two instances, two maps, and a throttle that never fires. Sharing the state
 * rather than the instance is what makes it work.
 *
 * In-memory, so it is per-process. Behind more than one instance this needs a
 * shared store -- recorded in the security review under A07.
 */
@Injectable()
export class LoginAttempts {
  private readonly attempts = new Map<string, Attempts>();

  static callerKey(request: Request): string {
    return request.ip ?? 'unknown';
  }

  isBlocked(caller: string): boolean {
    const seen = this.attempts.get(caller);
    if (!seen) return false;
    return Date.now() - seen.firstAt < COOLDOWN_MS && seen.count >= MAX_ATTEMPTS;
  }

  recordFailure(caller: string): void {
    const now = Date.now();
    const seen = this.attempts.get(caller);
    if (!seen || now - seen.firstAt >= COOLDOWN_MS) {
      this.attempts.set(caller, { count: 1, firstAt: now });
      return;
    }
    seen.count += 1;
  }

  /** A success clears the count. */
  recordSuccess(caller: string): void {
    this.attempts.delete(caller);
  }
}
