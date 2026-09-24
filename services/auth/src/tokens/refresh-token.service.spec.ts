import { createHash } from 'node:crypto';
import { RefreshTokenService, REFRESH_TOKEN_DAYS } from './refresh-token.service';
import { RefreshTokenRepository, StoredRefreshToken } from './refresh-token.repository';

describe('RefreshTokenService', () => {
  const CREDENTIAL = '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f';
  const hashOf = (token: string) => createHash('sha256').update(token).digest('hex');

  let store: {
    store: jest.Mock; find: jest.Mock; markExchanged: jest.Mock; revokeAllFor: jest.Mock;
  };
  let service: RefreshTokenService;

  const live = (overrides: Partial<StoredRefreshToken> = {}): StoredRefreshToken => ({
    tokenHash: 'irrelevant',
    credentialId: CREDENTIAL,
    expiresAt: new Date(Date.now() + 86_400_000),
    exchangedAt: null,
    ...overrides,
  });

  beforeEach(() => {
    store = {
      store: jest.fn().mockResolvedValue(undefined),
      find: jest.fn(),
      markExchanged: jest.fn().mockResolvedValue(true),
      revokeAllFor: jest.fn().mockResolvedValue(2),
    };
    service = new RefreshTokenService(store as unknown as RefreshTokenRepository);
  });

  it('stores a hash, never the token it hands out', async () => {
    const token = await service.issue(CREDENTIAL);

    const [storedHash] = store.store.mock.calls[0];
    expect(storedHash).toBe(hashOf(token));
    expect(storedHash).not.toBe(token);
    // Read access to the database is not session takeover.
    expect(store.store.mock.calls[0]).not.toContain(token);
  });

  it('issues a token that expires in seven days', async () => {
    await service.issue(CREDENTIAL);
    const [, , expiresAt] = store.store.mock.calls[0];

    const days = (expiresAt.getTime() - Date.now()) / 86_400_000;
    expect(Math.round(days)).toBe(REFRESH_TOKEN_DAYS);
  });

  it('every refresh issues a NEW token, and the new one works', async () => {
    const first = await service.issue(CREDENTIAL);
    store.find.mockResolvedValue(live());

    const owner = await service.rotate(first);
    const second = await service.issue(owner);

    expect(second).not.toBe(first);
    expect(store.store).toHaveBeenLastCalledWith(hashOf(second), CREDENTIAL, expect.any(Date));
  });

  it('revokes the presented token, so a second presentation is refused', async () => {
    store.find.mockResolvedValue(live());
    await service.rotate('some-token');

    expect(store.markExchanged).toHaveBeenCalledWith(hashOf('some-token'));
  });

  it('treats a replayed token as theft and revokes every live token for that user', async () => {
    store.find.mockResolvedValue(live({ exchangedAt: new Date() }));

    await expect(service.rotate('already-used')).rejects.toMatchObject({
      response: { errorCode: 'AUTH-401', message: 'Unauthorised' },
    });
    // Either a client repeated a request or a token was stolen, and the
    // service cannot tell which, so it assumes the worse one.
    expect(store.revokeAllFor).toHaveBeenCalledWith(CREDENTIAL);
  });

  it('refuses an expired refresh token', async () => {
    store.find.mockResolvedValue(live({ expiresAt: new Date(Date.now() - 1000) }));

    await expect(service.rotate('stale')).rejects.toMatchObject({ response: { errorCode: 'AUTH-401' } });
    expect(store.markExchanged).not.toHaveBeenCalled();
  });

  it('refuses a token it never issued', async () => {
    store.find.mockResolvedValue(null);

    await expect(service.rotate('invented')).rejects.toMatchObject({ response: { errorCode: 'AUTH-401' } });
  });

  it('refuses the loser of two concurrent refreshes of the same token', async () => {
    store.find.mockResolvedValue(live());
    store.markExchanged.mockResolvedValue(false);

    await expect(service.rotate('contested')).rejects.toMatchObject({ response: { errorCode: 'AUTH-401' } });
  });
});
