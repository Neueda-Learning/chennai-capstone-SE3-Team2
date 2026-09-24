import { Test } from '@nestjs/testing';
import { JwtModule, JwtService } from '@nestjs/jwt';
import * as argon2 from 'argon2';
import { AuthService } from './auth.service';
import { CredentialRepository } from '../credentials/credential.repository';
import { Env } from '../config/env';

describe('AuthService', () => {
  const SECRET = 'a-test-secret-of-at-least-32-bytes-length';
  let service: AuthService;
  let credentials: { findByUsername: jest.Mock; findById: jest.Mock; claim: jest.Mock };

  const stored = async () => ({
    id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
    username: 'priya.menon',
    passwordHash: await argon2.hash('correct horse battery staple', { type: argon2.argon2id }),
    accountId: 3,
    roles: ['CUSTOMER'],
  });

  beforeEach(async () => {
    credentials = { findByUsername: jest.fn(), findById: jest.fn(), claim: jest.fn() };
    const env = { jwtSecret: SECRET, jwtIssuer: 'auth-service' } as Env;

    const module = await Test.createTestingModule({
      imports: [JwtModule.register({})],
      providers: [
        AuthService,
        { provide: CredentialRepository, useValue: credentials },
        { provide: Env, useValue: env },
      ],
    }).compile();

    service = module.get(AuthService);
  });

  describe('register', () => {
    it('creates no trading account and issues no tokens', async () => {
      credentials.findByUsername.mockResolvedValue(null);
      credentials.claim.mockResolvedValue({ ...(await stored()) });

      const result = await service.register({
        username: 'priya.menon',
        password: 'correct horse battery staple',
        accountId: 3,
      });

      expect(result).toEqual({
        id: expect.any(String),
        username: 'priya.menon',
        accountId: 3,
        roles: ['CUSTOMER'],
      });
      expect(result).not.toHaveProperty('accessToken');
      expect(result).not.toHaveProperty('refreshToken');
    });

    it('refuses a username that is already registered with AUTH-409', async () => {
      credentials.findByUsername.mockResolvedValue(await stored());

      await expect(
        service.register({ username: 'priya.menon', password: 'correct horse battery staple', accountId: 3 }),
      ).rejects.toMatchObject({ response: { errorCode: 'AUTH-409' } });
    });

    it('refuses an account that was never provisioned', async () => {
      credentials.findByUsername.mockResolvedValue(null);
      credentials.claim.mockResolvedValue(null);

      await expect(
        service.register({ username: 'new.user', password: 'correct horse battery staple', accountId: 9999 }),
      ).rejects.toMatchObject({ response: { errorCode: 'AUTH-401' } });
    });

    it('refuses an account somebody has already claimed', async () => {
      credentials.findByUsername.mockResolvedValue(null);
      credentials.claim.mockResolvedValue(null);

      await expect(
        service.register({ username: 'second.user', password: 'correct horse battery staple', accountId: 3 }),
      ).rejects.toMatchObject({ response: { errorCode: 'AUTH-401' } });
    });
  });

  describe('login', () => {
    it('returns a token pair for the right password', async () => {
      credentials.findByUsername.mockResolvedValue(await stored());

      const tokens = await service.login({ username: 'priya.menon', password: 'correct horse battery staple' });

      expect(tokens.tokenType).toBe('Bearer');
      expect(tokens.expiresIn).toBe(900);
      expect(tokens.accessToken).toMatch(/^eyJ/);
      expect(tokens.refreshToken).toHaveLength(64);
    });

    it('carries the account from the stored credential, not from the request', async () => {
      credentials.findByUsername.mockResolvedValue(await stored());

      const { accessToken } = await service.login({ username: 'priya.menon', password: 'correct horse battery staple' });
      const claims = new JwtService().decode(accessToken) as Record<string, unknown>;

      expect(claims.accountId).toBe(3);
    });

    it('refuses a wrong password with AUTH-401', async () => {
      credentials.findByUsername.mockResolvedValue(await stored());

      await expect(service.login({ username: 'priya.menon', password: 'not the password' }))
        .rejects.toMatchObject({ response: { errorCode: 'AUTH-401', message: 'Unauthorised' } });
    });

    it('refuses an unknown user with the identical body', async () => {
      credentials.findByUsername.mockResolvedValue(null);

      await expect(service.login({ username: 'nobody', password: 'correct horse battery staple' }))
        .rejects.toMatchObject({ response: { errorCode: 'AUTH-401', message: 'Unauthorised' } });
    });
  });
});
