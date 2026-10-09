import { CanActivate, ExecutionContext, Injectable } from '@nestjs/common';
import { Request } from 'express';
import { AccessTokenService } from '../tokens/access-token.service';
import { AccessTokenClaims } from '../tokens/claims';
import { PlatformError } from '../common/platform-error';

/** Where the verified claims are put for @CurrentUser to read. */
export const AUTHENTICATED_USER = 'authenticatedUser';

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(private readonly accessTokens: AccessTokenService) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<Request>();
    const header = request.headers.authorization;

    if (!header?.startsWith('Bearer ')) {
      throw PlatformError.unauthorised();
    }

    let claims: AccessTokenClaims;
    try {
      // Signature and clock, both, before a single claim is read. A guard that
      // checks the signature and forgets the clock accepts every token it ever
      // issued, for ever.
      claims = await this.accessTokens.verify(header.slice('Bearer '.length));
    } catch {
      // Expired, tampered, malformed: one answer, so none of them is an oracle.
      throw PlatformError.unauthorised();
    }

    (request as any)[AUTHENTICATED_USER] = claims;
    return true;
  }
}
