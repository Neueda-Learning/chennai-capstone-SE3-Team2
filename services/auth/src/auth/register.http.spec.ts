import { INestApplication } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { Test } from '@nestjs/testing';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { configureApp } from '../app-setup';
import { Env } from '../config/env';
import { Credential, CredentialRepository, RegistrationOutcome } from '../credentials/credential.repository';
import { PasswordHasher } from '../credentials/password-hasher';
import { CONTRACT_CLAIMS } from '../tokens/claims';
import { AccessTokenService } from '../tokens/access-token.service';
import { RefreshTokenService } from '../tokens/refresh-token.service';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { JwtAuthGuard } from './jwt-auth.guard';
import { LoginAttempts } from './login-attempts';
import { LoginFailure } from './login-failure';
import { LoginThrottleGuard } from './login-throttle.guard';

const sha256 = (v: string) => createHash('sha256').update(v).digest('hex');

interface TokenRow { clientId: number; expiresAt: Date; usedAt?: Date; revokedAt?: Date }

/**
 * The credential store with the same rules the SQL enforces: a token is
 * consumed only if it is unused, unrevoked and unexpired AND its account is
 * unclaimed, and any refusal leaves everything as it was.
 */
class InMemoryStore {
  tokens = new Map<string, TokenRow>();
  claimed = new Map<number, string>();
  credentials: Credential[] = [];

  issue(clientId: number, expiresAt = new Date(Date.now() + 3600_000)): string {
    const token = randomBytes(32).toString('hex');
    this.tokens.set(sha256(token), { clientId, expiresAt });
    return token;
  }

  async registerWithActivationToken(username: string, passwordHash: string, tokenHash: string): Promise<RegistrationOutcome> {
    const row = this.tokens.get(tokenHash);
    if (!row || row.usedAt || row.revokedAt || row.expiresAt.getTime() <= Date.now()) return { kind: 'invalid-token' };
    if (this.claimed.has(row.clientId)) return { kind: 'invalid-token' };
    if (this.credentials.some((c) => c.username === username)) return { kind: 'username-taken' };

    const credential = { id: randomUUID(), username, passwordHash, accountId: row.clientId, roles: ['CUSTOMER'] };
    row.usedAt = new Date();
    this.claimed.set(row.clientId, credential.id);
    this.credentials.push(credential);
    return { kind: 'registered', credential };
  }

  async findByUsername(username: string) { return this.credentials.find((c) => c.username === username) ?? null; }
  async findById(id: string) { return this.credentials.find((c) => c.id === id) ?? null; }
}

/** Fast stand-in: the argon2 cost is tested in password-hasher.spec. */
const fastHasher = {
  hash: async (p: string) => `$argon2id$test$${p}`,
  verify: async (hash: string, p: string) => hash === `$argon2id$test$${p}`,
};

describe('POST /auth/register with an activation token', () => {
  let app: INestApplication;
  let base: string;
  let store: InMemoryStore;

  beforeEach(async () => {
    store = new InMemoryStore();
    const module = await Test.createTestingModule({
      controllers: [AuthController],
      providers: [
        AuthService,
        { provide: CredentialRepository, useValue: store },
        { provide: PasswordHasher, useValue: fastHasher },
        JwtService,
        AccessTokenService,
        { provide: RefreshTokenService, useValue: { issue: async () => 'r'.repeat(64), rotate: jest.fn() } },
        LoginFailure,
        LoginAttempts,
        LoginThrottleGuard,
        JwtAuthGuard,
        { provide: Env, useValue: { jwtSecret: 'a-test-secret-of-at-least-32-bytes-length', jwtIssuer: 'auth-service' } },
      ],
    }).compile();

    app = module.createNestApplication({ logger: false });
    configureApp(app);
    await app.listen(0, '127.0.0.1');
    base = await app.getUrl();
  });

  afterEach(() => app.close());

  const post = (path: string, body: unknown) =>
    fetch(`${base}${path}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });

  const register = (activationToken: string, username = 'priya.menon') =>
    post('/auth/register', { username, password: 'correct horse battery staple', activationToken });

  it('registers from a valid token with no account number in the request', async () => {
    const token = store.issue(7);

    const res = await register(token);

    expect(res.status).toBe(201);
    expect(await res.json()).toEqual({ id: expect.any(String), username: 'priya.menon', accountId: 7, roles: ['CUSTOMER'] });
  });

  it('no longer accepts an account number, or a self-declared role', async () => {
    const token = store.issue(7);

    const withAccount = await post('/auth/register', {
      username: 'priya.menon', password: 'correct horse battery staple', activationToken: token, accountId: 3,
    });
    const withRoles = await post('/auth/register', {
      username: 'priya.menon', password: 'correct horse battery staple', activationToken: token, roles: ['ADMIN'],
    });

    expect(withAccount.status).toBe(422);
    expect(withRoles.status).toBe(422);
    expect(store.credentials).toHaveLength(0);
  });

  it('refuses the same token a second time, and the second attempt creates nothing', async () => {
    const token = store.issue(7);
    await register(token, 'first.user');

    const second = await register(token, 'second.user');

    expect(second.status).toBe(401);
    expect(store.credentials.map((c) => c.username)).toEqual(['first.user']);
  });

  it('answers a used, an expired and an unknown token with an identical status and body', async () => {
    const used = store.issue(7);
    await register(used, 'first.user');
    const expired = store.issue(8, new Date(Date.now() - 1000));
    const unknown = randomBytes(32).toString('hex');

    const responses = [await register(used, 'x.one'), await register(expired, 'x.two'), await register(unknown, 'x.three')];
    const bodies = await Promise.all(responses.map((r) => r.text()));

    expect(responses.map((r) => r.status)).toEqual([401, 401, 401]);
    expect(bodies[0]).toBe('{"errorCode":"AUTH-401","message":"Unauthorised"}');
    expect(new Set(bodies).size).toBe(1);
  });

  it('refuses a token whose account is already claimed, and does not use the token up', async () => {
    const token = store.issue(7);
    store.claimed.set(7, randomUUID());

    const res = await register(token);

    expect(res.status).toBe(401);
    expect(store.tokens.get(sha256(token))?.usedAt).toBeUndefined();
    expect(store.credentials).toHaveLength(0);
  });

  it('refuses a taken username with AUTH-409 and leaves the token usable', async () => {
    await register(store.issue(7), 'priya.menon');
    const token = store.issue(8);

    const res = await register(token, 'priya.menon');

    expect(res.status).toBe(409);
    expect(store.tokens.get(sha256(token))?.usedAt).toBeUndefined();
  });

  it('then logs in to a JWT carrying exactly the six contract claims, with a numeric accountId', async () => {
    await register(store.issue(7));

    const login = await post('/auth/login', { username: 'priya.menon', password: 'correct horse battery staple' });
    const { accessToken } = (await login.json()) as { accessToken: string };
    const claims = new JwtService().decode(accessToken) as Record<string, unknown>;

    expect(login.status).toBe(200);
    expect(Object.keys(claims).sort()).toEqual([...CONTRACT_CLAIMS].sort());
    expect(claims.accountId).toBe(7);

    const me = await fetch(`${base}/auth/me`, { headers: { authorization: `Bearer ${accessToken}` } });
    expect(await me.json()).toEqual({ id: expect.any(String), username: 'priya.menon', accountId: 7, roles: ['CUSTOMER'] });
  });
});
