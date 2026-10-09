import { JwtService } from '@nestjs/jwt';
import { AccessTokenService } from './access-token.service';
import { CONTRACT_CLAIMS, ACCESS_TOKEN_SECONDS } from './claims';
import { Credential } from '../credentials/credential.repository';
import { Env } from '../config/env';

describe('AccessTokenService', () => {
  const SECRET = 'a-test-secret-of-at-least-32-bytes-length';
  const env = { jwtSecret: SECRET, jwtIssuer: 'auth-service' } as Env;
  const jwt = new JwtService();
  const service = new AccessTokenService(jwt, env);

  const credential: Credential = {
    id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
    username: 'priya.menon',
    passwordHash: '$argon2id$irrelevant',
    accountId: 3,
    roles: ['CUSTOMER'],
  };

  it('issues a token with the contract claims and a fifteen minute expiry', async () => {
    const claims = jwt.decode(await service.issue(credential)) as Record<string, any>;

    expect(claims.sub).toBe(credential.id);
    expect(claims.accountId).toBe(3);
    expect(claims.roles).toEqual(['CUSTOMER']);
    expect(claims.iss).toBe('auth-service');
    expect(claims.exp - claims.iat).toBe(ACCESS_TOKEN_SECONDS);
  });

  it('signs with the service key, so the signature verifies', async () => {
    const token = await service.issue(credential);
    await expect(service.verify(token)).resolves.toMatchObject({ sub: credential.id });
  });

  it('carries no claim outside the contract set', async () => {
    const claims = jwt.decode(await service.issue(credential)) as Record<string, unknown>;

    // Assert on the key set, not on individual claims: that is what catches
    // a username or an email quietly added later.
    expect(Object.keys(claims).sort()).toEqual([...CONTRACT_CLAIMS].sort());
  });

  it('publishes no username or email, because the payload is base64 and not encryption', async () => {
    const payload = Buffer.from((await service.issue(credential)).split('.')[1], 'base64').toString();

    expect(payload).not.toContain('priya.menon');
    expect(payload).not.toContain('@');
  });

  it('refuses a token signed with a different key', async () => {
    const forged = await new JwtService().signAsync(
      { sub: credential.id }, { secret: 'a-different-secret-of-at-least-32-bytes', expiresIn: 900 },
    );
    await expect(service.verify(forged)).rejects.toThrow();
  });

  it('refuses a token whose expiry has passed', async () => {
    const expired = await jwt.signAsync(
      { sub: credential.id }, { secret: SECRET, expiresIn: -10 },
    );
    await expect(service.verify(expired)).rejects.toThrow();
  });

  it('uses HS256, not none and not an asymmetric algorithm', async () => {
    const header = JSON.parse(
      Buffer.from((await service.issue(credential)).split('.')[0], 'base64').toString(),
    );
    expect(header.alg).toBe('HS256');
  });
});
