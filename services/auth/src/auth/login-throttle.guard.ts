import { CanActivate, ExecutionContext, HttpStatus, Injectable } from '@nestjs/common';
import { Request } from 'express';
import { PlatformError } from '../common/platform-error';
import { LoginAttempts } from './login-attempts';

export { MAX_ATTEMPTS, COOLDOWN_MS } from './login-attempts';

/**
 * Limits how fast one caller can use the login route. It does not close a
 * disclosure, it only slows its use -- the uniform failure behind it is what
 * closes it.
 */
@Injectable()
export class LoginThrottleGuard implements CanActivate {
  constructor(private readonly attempts: LoginAttempts) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request>();
    if (this.attempts.isBlocked(LoginAttempts.callerKey(request))) {
      throw new PlatformError('AUTH-429', 'Too many attempts', HttpStatus.TOO_MANY_REQUESTS);
    }
    return true;
  }
}
