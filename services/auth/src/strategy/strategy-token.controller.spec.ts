import { INestApplication } from '@nestjs/common';
import { JwtModule, JwtService } from '@nestjs/jwt';
import { Test } from '@nestjs/testing';
import { configureApp } from '../app-setup';
import { InternalSecretGuard } from '../activation/internal-secret.guard';
import { Env } from '../config/env';
import { Credential, CredentialRepository } from '../credentials/credential.repository';
import { AccessTokenService } from '../tokens/access-token.service';
import { STRATEGY_TOKEN_SECONDS } from '../tokens/claims';
import { StrategyTokenController } from './strategy-token.controller';

const SECRET = 'an-internal-test-secret-of-32-plus-bytes';
const JWT_SECRET = 'a-jwt-test-secret-that-is-32-bytes-or-more';

const ROHAN: Credential = {
  id: '3f2a1c9e-0b7d-4e5f-8a6b-1c2d3e4f5a6b',
  username: 'rohan.nair',
  passwordHash: 'not-used-here',
  accountId: 3,
  roles: ['CUSTOMER'],
};

describe('POST /internal/strategy-tokens', () => {
  let app: INestApplication;
  let url: string;
  let jwt: JwtService;

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      imports: [JwtModule.register({})],
      controllers: [StrategyTokenController],
      providers: [
        AccessTokenService,
        InternalSecretGuard,
        {
          provide: CredentialRepository,
          useValue: { findByAccountId: async (accountId: number) => (accountId === 3 ? ROHAN : null) },
        },
        { provide: Env, useValue: { activationInternalSecret: SECRET, jwtSecret: JWT_SECRET, jwtIssuer: 'auth-service' } },
      ],
    }).compile();

    app = module.createNestApplication({ logger: false });
    configureApp(app);
    await app.listen(0, '127.0.0.1');
    url = `${await app.getUrl()}/internal/strategy-tokens`;
    jwt = module.get(JwtService);
  });

  afterEach(() => app.close());

  const call = (headers: Record<string, string>, body: unknown = { accountId: 3 }) =>
    fetch(url, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });

  it('mints an access token minutes long for the account, for a caller holding the secret', async () => {
    const res = await call({ 'x-internal-secret': SECRET });

    expect(res.status).toBe(201);
    const body = (await res.json()) as any;
    expect(body.tokenType).toBe('Bearer');
    expect(body.expiresIn).toBe(STRATEGY_TOKEN_SECONDS);
    const claims = await jwt.verifyAsync(body.accessToken, { secret: JWT_SECRET, algorithms: ['HS256'] });
    expect(claims.accountId).toBe(3);
    expect(claims.sub).toBe(ROHAN.id);
    expect(claims.iss).toBe('auth-service');
    expect(claims.exp - claims.iat).toBe(STRATEGY_TOKEN_SECONDS);
  });

  it('says on the token that a strategy holds it, beside the customer roles', async () => {
    const body = (await (await call({ 'x-internal-secret': SECRET })).json()) as any;

    const claims = await jwt.verifyAsync(body.accessToken, { secret: JWT_SECRET });
    expect(claims.roles).toEqual(['CUSTOMER', 'STRATEGY']);
  });

  it('lives five minutes: a strategy order is placed at once, so a longer credential only widens a leak', () => {
    expect(STRATEGY_TOKEN_SECONDS).toBe(300);
  });

  it('refuses a caller without the secret, or with the wrong one, identically', async () => {
    for (const headers of [{}, { 'x-internal-secret': 'guess' }] as Record<string, string>[]) {
      const res = await call(headers);
      expect(res.status).toBe(401);
      expect(await res.json()).toEqual({ errorCode: 'AUTH-401', message: 'Unauthorised' });
    }
  });

  it('refuses an account with no login: there is nobody to act for', async () => {
    const res = await call({ 'x-internal-secret': SECRET }, { accountId: 99 });

    expect(res.status).toBe(404);
    expect(((await res.json()) as any).errorCode).toBe('ACT-404');
  });

  it('refuses an account number that is not one', async () => {
    const res = await call({ 'x-internal-secret': SECRET }, { accountId: 'three' });

    expect(res.status).toBe(422);
  });
});
