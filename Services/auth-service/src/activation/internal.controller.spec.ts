import { INestApplication } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import { createHash } from 'node:crypto';
import { configureApp } from '../app-setup';
import { Env } from '../config/env';
import { ActivationTokenRepository, MintOutcome } from './activation-token.repository';
import { ACTIVATION_TOKEN_HOURS, ActivationTokenService } from './activation-token.service';
import { InternalController } from './internal.controller';
import { InternalSecretGuard } from './internal-secret.guard';

const SECRET = 'an-internal-test-secret-of-32-plus-bytes';

/** Stands in for the table: what was stored, and for which client. */
class RecordingStore {
  stored: { clientId: number; tokenHash: string; expiresAt: Date }[] = [];
  outcome: MintOutcome = 'minted';

  async replaceFor(clientId: number, tokenHash: string, expiresAt: Date): Promise<MintOutcome> {
    if (this.outcome === 'minted') this.stored.push({ clientId, tokenHash, expiresAt });
    return this.outcome;
  }
}

describe('POST /internal/activation-tokens', () => {
  let app: INestApplication;
  let url: string;
  let store: RecordingStore;

  beforeEach(async () => {
    store = new RecordingStore();
    const module = await Test.createTestingModule({
      controllers: [InternalController],
      providers: [
        ActivationTokenService,
        InternalSecretGuard,
        { provide: ActivationTokenRepository, useValue: store },
        { provide: Env, useValue: { activationInternalSecret: SECRET } },
      ],
    }).compile();

    app = module.createNestApplication({ logger: false });
    configureApp(app);
    await app.listen(0, '127.0.0.1');
    url = `${await app.getUrl()}/internal/activation-tokens`;
  });

  afterEach(() => app.close());

  const call = (headers: Record<string, string>, body: unknown = { clientId: 7 }) =>
    fetch(url, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });

  it('mints a token for a caller holding the secret, and returns it once with its expiry', async () => {
    const before = Date.now();
    const res = await call({ 'x-internal-secret': SECRET });

    expect(res.status).toBe(201);
    const body = (await res.json()) as any;
    expect(Object.keys(body).sort()).toEqual(['activationToken', 'expiresAt']);
    expect(body.activationToken).toMatch(/^[0-9a-f]{64}$/);

    const lifetime = Date.parse(body.expiresAt) - before;
    expect(lifetime).toBeGreaterThan(ACTIVATION_TOKEN_HOURS * 3600_000 - 5_000);
    expect(lifetime).toBeLessThanOrEqual(ACTIVATION_TOKEN_HOURS * 3600_000 + 5_000);
  });

  it('stores a 64-character SHA-256 of the token, and never the token itself', async () => {
    const { activationToken } = (await (await call({ 'x-internal-secret': SECRET })).json()) as any;

    expect(store.stored).toHaveLength(1);
    const { tokenHash, clientId } = store.stored[0];
    expect(clientId).toBe(7);
    expect(tokenHash).toHaveLength(64);
    expect(tokenHash).toBe(createHash('sha256').update(activationToken).digest('hex'));
    expect(JSON.stringify(store.stored)).not.toContain(activationToken);
  });

  it('mints a different token on a second call for the same client', async () => {
    const first = (await (await call({ 'x-internal-secret': SECRET })).json()) as any;
    const second = (await (await call({ 'x-internal-secret': SECRET })).json()) as any;

    expect(second.activationToken).not.toBe(first.activationToken);
    expect(store.stored.map((s) => s.clientId)).toEqual([7, 7]);
  });

  it('answers a missing secret and a wrong secret with a byte-identical 401', async () => {
    const missing = await call({});
    const wrong = await call({ 'x-internal-secret': 'not-the-secret' });
    const nearly = await call({ 'x-internal-secret': SECRET.slice(0, -1) + 'X' });

    const bodies = await Promise.all([missing.text(), wrong.text(), nearly.text()]);
    expect([missing.status, wrong.status, nearly.status]).toEqual([401, 401, 401]);
    expect(bodies[0]).toBe('{"errorCode":"AUTH-401","message":"Unauthorised"}');
    expect(bodies[1]).toBe(bodies[0]);
    expect(bodies[2]).toBe(bodies[0]);
    expect(store.stored).toHaveLength(0);
  });

  it('checks the secret before the body, so an unauthenticated caller learns nothing about the shape', async () => {
    const res = await call({ 'x-internal-secret': 'wrong' }, { nonsense: true });
    expect(res.status).toBe(401);
  });

  it('refuses an unprovisioned client with ACT-404 and an already-claimed one with ACT-409', async () => {
    store.outcome = 'not-provisioned';
    const notProvisioned = await call({ 'x-internal-secret': SECRET });
    store.outcome = 'already-claimed';
    const claimed = await call({ 'x-internal-secret': SECRET });

    expect(notProvisioned.status).toBe(404);
    expect(((await notProvisioned.json()) as any).errorCode).toBe('ACT-404');
    expect(claimed.status).toBe(409);
    expect(((await claimed.json()) as any).errorCode).toBe('ACT-409');
  });

  it('refuses a body that is not a positive integer client id', async () => {
    const res = await call({ 'x-internal-secret': SECRET }, { clientId: '7' });
    expect(res.status).toBe(422);
  });
});

describe('InternalSecretGuard', () => {
  it('refuses to be built without ACTIVATION_INTERNAL_SECRET, naming it', () => {
    const env = new Env({ get: () => undefined } as unknown as ConfigService);
    expect(() => new InternalSecretGuard(env)).toThrow('ACTIVATION_INTERNAL_SECRET is not set');
  });
});
