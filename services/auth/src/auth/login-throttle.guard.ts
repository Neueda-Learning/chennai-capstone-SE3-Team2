import { CanActivate, ExecutionContext, HttpStatus, Injectable } from '@nestjs/common';
import { Request } from 'express';
import { PlatformError } from '../common/platform-error';

/** Five failures from one address, then a one minute cooldown. */
export const MAX_ATTEMPTS = 5;
export const COOLDOWN_MS = 60_000;

interface Attempts {
  count: number;
  firstAt: number;
}

/**
 * Limits how fast one caller can use the login route. It does not close a
 * disclosure, it only slows its use -- the uniform failure above is what
 * closes it.
 *
 * In-memory, so it is per-instance. One process is the whole deployment here;
 * behind more than one it would need a shared store.
 */
@Injectable()
export class LoginThrottleGuard implements CanActivate {
  private readonly attempts = new Map<string, Attempts>();

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request>();
    const caller = request.ip ?? 'unknown';
    const now = Date.now();

    const seen = this.attempts.get(caller);
    if (seen && now - seen.firstAt < COOLDOWN_MS && seen.count >= MAX_ATTEMPTS) {
      throw new PlatformError('AUTH-429', 'Too many attempts', HttpStatus.TOO_MANY_REQUESTS);
    }
    return true;
  }

  /** Called by the route when a login fails. A success clears the count. */
  recordFailure(caller: string): void {
    const now = Date.now();
    const seen = this.attempts.get(caller);
    if (!seen || now - seen.firstAt >= COOLDOWN_MS) {
      this.attempts.set(caller, { count: 1, firstAt: now });
      return;
    }
    seen.count += 1;
  }

  recordSuccess(caller: string): void {
    this.attempts.delete(caller);
  }
}
