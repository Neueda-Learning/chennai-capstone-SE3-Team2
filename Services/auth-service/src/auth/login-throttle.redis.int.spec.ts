import { INestApplication } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { Test } from '@nestjs/testing';
import { randomUUID } from 'node:crypto';
import { configureApp } from '../app-setup';
import { Env } from '../config/env';
import { CredentialRepository } from '../credentials/credential.repository';
import { PasswordHasher } from '../credentials/password-hasher';
import { AccessTokenService } from '../tokens/access-token.service';
import { RefreshTokenService } from '../tokens/refresh-token.service';
import { ATTEMPT_STORE } from './attempt-store';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { JwtAuthGuard } from './jwt-auth.guard';
import { LoginAttempts, MAX_ATTEMPTS } from './login-attempts';
import { LoginFailure } from './login-failure';
import { LoginThrottleGuard } from './login-throttle.guard';
import { createRedisClient, RedisAttemptStore } from './redis-attempt-store';

/**
 * Two auth instances, real HTTP, a real Redis -- no Docker, no Postgres, no
 * Kafka. Only the credential store is stood in, as in register.http.spec.
 *
 * Opt-in: it talks to whatever REDIS_TEST_URL names, so it never runs by
 * accident. Each run counts under its own key prefix and deletes it after,
 * so it is safe against a shared database.
 *
 *   REDIS_TEST_URL=redis://... npx jest login-throttle.redis
 */
const url = process.env.REDIS_TEST_URL;

(url ? describe : describe.skip)('login throttle across two instances, on a real Redis', () => {
  const prefix = `login-attempts-test:${randomUUID()}:`;
  const instances: { app: INestApplication; base: string; store: RedisAttemptStore }[] = [];
  let inspector: ReturnType<typeof createRedisClient>;

  const startInstance = async () => {
    const store = new RedisAttemptStore(createRedisClient(url!), prefix);
    await store.connect();

    const module = await Test.createTestingModule({
      controllers: [AuthController],
      providers: [
        AuthService, LoginFailure, LoginAttempts, LoginThrottleGuard, JwtAuthGuard, JwtService, AccessTokenService,
        { provide: ATTEMPT_STORE, useValue: store },
        // Nobody exists, so every login is the unknown-user 401.
        { provide: CredentialRepository, useValue: { findByUsername: async () => null, findById: async () => null } },
        { provide: PasswordHasher, useValue: { hash: async (p: string) => `h:${p}`, verify: async () => false } },
        { provide: RefreshTokenService, useValue: { issue: jest.fn(), rotate: jest.fn() } },
        { provide: Env, useValue: { jwtSecret: 'a-test-secret-of-at-least-32-bytes-length', jwtIssuer: 'auth-service' } },
      ],
    }).compile();

    const app = module.createNestApplication({ logger: false });
    configureApp(app);
    await app.listen(0, '127.0.0.1');
    instances.push({ app, base: await app.getUrl(), store });
  };

  const login = (base: string) =>
    fetch(`${base}/auth/login`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ username: 'nobody', password: 'wrong' }),
    }).then((res) => res.status);

  beforeAll(async () => {
    inspector = createRedisClient(url!);
    await inspector.connect();
    await startInstance();
    await startInstance();
  });

  afterAll(async () => {
    const keys = await inspector.keys(`${prefix}*`);
    if (keys.length) await inspector.del(...keys);
    inspector.disconnect();
    for (const { app, store } of instances) {
      await app.close();
      await store.close();
    }
  });

  it('refuses the sixth attempt on both instances after five failures split between them', async () => {
    const [a, b] = instances;

    // a, b, a, b, a: neither instance sees all five
    const statuses: number[] = [];
    for (let i = 0; i < MAX_ATTEMPTS; i++) statuses.push(await login(i % 2 === 0 ? a.base : b.base));
    expect(statuses).toEqual([401, 401, 401, 401, 401]);

    expect(await login(b.base)).toBe(429);
    expect(await login(a.base)).toBe(429);

    // and the count is in Redis, one key for the caller, expiring within the minute
    const [key] = await inspector.keys(`${prefix}*`);
    expect(key).toBe(`${prefix}127.0.0.1`);
    expect(await inspector.get(key)).toBe(String(MAX_ATTEMPTS));
    const ttl = await inspector.pttl(key);
    expect(ttl).toBeGreaterThan(0);
    expect(ttl).toBeLessThanOrEqual(60_000);
  });
});
