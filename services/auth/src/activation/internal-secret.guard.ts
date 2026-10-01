import { CanActivate, ExecutionContext, Injectable } from '@nestjs/common';
import { Request } from 'express';
import { createHash, timingSafeEqual } from 'node:crypto';
import { Env } from '../config/env';
import { PlatformError } from '../common/platform-error';

export const INTERNAL_SECRET_HEADER = 'x-internal-secret';

/**
 * Keeps the token-minting route to callers holding the shared secret.
 *
 * Both sides are hashed before comparing, so timingSafeEqual always compares
 * equal lengths and the time taken says nothing about the secret's length or
 * how much of a guess was right. Missing and wrong fail identically.
 */
@Injectable()
export class InternalSecretGuard implements CanActivate {
  private readonly expected: Buffer;

  constructor(env: Env) {
    // Read at construction: the service refuses to start without it.
    this.expected = digest(env.activationInternalSecret);
  }

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request>();
    const presented = request.headers[INTERNAL_SECRET_HEADER];

    const candidate = digest(typeof presented === 'string' ? presented : '');
    if (typeof presented !== 'string' || !timingSafeEqual(candidate, this.expected)) {
      throw PlatformError.unauthorised();
    }
    return true;
  }
}

function digest(value: string): Buffer {
  return createHash('sha256').update(value, 'utf8').digest();
}
