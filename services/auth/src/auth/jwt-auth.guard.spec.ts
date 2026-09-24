import { ExecutionContext } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { JwtAuthGuard, AUTHENTICATED_USER } from './jwt-auth.guard';
import { AccessTokenService } from '../tokens/access-token.service';
import { Env } from '../config/env';
import { Credential } from '../credentials/credential.repository';

describe('JwtAuthGuard', () => {
  const SECRET = 'a-test-secret-of-at-least-32-bytes-length';
  const OTHER_KEY = 'a-different-secret-of-at-least-32-bytes';

  const jwt = new JwtService();
  const env = { jwtSecret: SECRET, jwtIssuer: 'auth-service' } as Env;
  const accessTokens = new AccessTokenService(jwt, env);
  const guard = new JwtAuthGuard(accessTokens);

  const credential: Credential = {
    id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
    username: 'priya.menon',
    passwordHash: '$argon2id$irrelevant',
    accountId: 3,
    roles: ['CUSTOMER'],
  };

  const contextWith = (authorization?: string) => {
    const request: Record<string, any> = { headers: authorization ? { authorization } : {} };
    return {
      request,
      context: { switchToHttp: () => ({ getRequest: () => request }) } as ExecutionContext,
    };
  };

  it('accepts a valid token and exposes its claims to the route', async () => {
    const token = await accessTokens.issue(credential);
    const { context, request } = contextWith(`Bearer ${token}`);

    await expect(guard.canActivate(context)).resolves.toBe(true);
    expect(request[AUTHENTICATED_USER]).toMatchObject({ sub: credential.id, accountId: 3 });
  });

  it('refuses an expired token', async () => {
    // A GENUINE token, signed by us, whose exp has passed. Corrupting the
    // payload instead would be refused by the signature check first, and would
    // therefore pass against a guard with no expiry check at all.
    const expired = await jwt.signAsync(
      { sub: credential.id, accountId: 3, roles: ['CUSTOMER'] },
      { secret: SECRET, algorithm: 'HS256', issuer: 'auth-service', expiresIn: -60 },
    );
    const { context } = contextWith(`Bearer ${expired}`);

    await expect(guard.canActivate(context)).rejects.toMatchObject({
      response: { errorCode: 'AUTH-401', message: 'Unauthorised' },
    });
  });

  it('refuses a token with a wrong signature before any claim is read', async () => {
    // Well-formed, unexpired, and signed with a key we do not trust. This is
    // the case that catches a verifier that decoded first and verified after.
    const forged = await jwt.signAsync(
      { sub: 'somebody-else', accountId: 999, roles: ['ADMIN'] },
      { secret: OTHER_KEY, algorithm: 'HS256', issuer: 'auth-service', expiresIn: 900 },
    );
    const { context, request } = contextWith(`Bearer ${forged}`);

    await expect(guard.canActivate(context)).rejects.toMatchObject({
      response: { errorCode: 'AUTH-401' },
    });
    // Nothing was trusted: no claims reached the request.
    expect(request[AUTHENTICATED_USER]).toBeUndefined();
  });

  it('refuses a malformed authorization header', async () => {
    for (const header of ['Bearer not.a.token', 'Basic dXNlcjpwYXNz', 'Bearer', 'eyJhbGciOiJIUzI1NiJ9']) {
      const { context } = contextWith(header);
      await expect(guard.canActivate(context)).rejects.toMatchObject({
        response: { errorCode: 'AUTH-401' },
      });
    }
  });

  it('refuses a request with no authorization header at all', async () => {
    const { context } = contextWith(undefined);
    await expect(guard.canActivate(context)).rejects.toMatchObject({
      response: { errorCode: 'AUTH-401' },
    });
  });

  it('answers identically whatever the reason, so none of them is an oracle', async () => {
    const expired = await jwt.signAsync({ sub: 'x' }, { secret: SECRET, expiresIn: -60 });
    const forged = await jwt.signAsync({ sub: 'x' }, { secret: OTHER_KEY, expiresIn: 900 });

    const bodies: unknown[] = [];
    for (const header of [`Bearer ${expired}`, `Bearer ${forged}`, 'Bearer rubbish', undefined]) {
      const { context } = contextWith(header);
      await guard.canActivate(context).catch((e) => bodies.push(e.response));
    }

    expect(bodies).toHaveLength(4);
    expect(new Set(bodies.map((b) => JSON.stringify(b))).size).toBe(1);
  });
});
