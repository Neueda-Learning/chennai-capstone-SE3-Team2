import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import { AccessTokenClaims } from '../tokens/claims';
import { AUTHENTICATED_USER } from './jwt-auth.guard';

/**
 * The identity the guard verified. Never a route parameter or a body field:
 * reading either would let a caller name somebody else.
 */
export const CurrentUser = createParamDecorator(
  (_data: unknown, context: ExecutionContext): AccessTokenClaims =>
    context.switchToHttp().getRequest()[AUTHENTICATED_USER],
);
