import { Test } from '@nestjs/testing';
import { JwtService } from '@nestjs/jwt';
import { AuthService } from './auth.service';
import { LoginFailure } from './login-failure';
import { CredentialRepository } from '../credentials/credential.repository';
import { PasswordHasher } from '../credentials/password-hasher';
import { AccessTokenService } from '../tokens/access-token.service';
import { RefreshTokenService } from '../tokens/refresh-token.service';
import { Env } from '../config/env';

describe('the failed login answers identically however it failed', () => {
  const PASSWORD = 'correct horse battery staple';
  let service: AuthService;
  let credentials: { findByUsername: jest.Mock; findById: jest.Mock; claim: jest.Mock };

  beforeEach(async () => {
    credentials = { findByUsername: jest.fn(), findById: jest.fn(), claim: jest.fn() };

    const module = await Test.createTestingModule({
      providers: [
        AuthService, LoginFailure, PasswordHasher, JwtService, AccessTokenService,
        { provide: CredentialRepository, useValue: credentials },
        { provide: RefreshTokenService, useValue: { issue: jest.fn(), rotate: jest.fn() } },
        { provide: Env, useValue: { jwtSecret: 'a-test-secret-of-at-least-32-bytes-length', jwtIssuer: 'auth-service' } as Env },
      ],
    }).compile();

    await module.init();
    service = module.get(AuthService);
  });

  const known = async () => ({
    id: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
    username: 'priya.menon',
    passwordHash: await new PasswordHasher().hash(PASSWORD),
    accountId: 3,
    roles: ['CUSTOMER'],
  });

  const failureFrom = async (): Promise<{ body: unknown; status: number; ms: number }> => {
    const started = process.hrtime.bigint();
    try {
      await service.login({ username: 'someone', password: 'a wrong password entirely' });
      throw new Error('login should have failed');
    } catch (error: any) {
      return {
        body: error.response,
        status: error.status,
        ms: Number(process.hrtime.bigint() - started) / 1e6,
      };
    }
  };

  it('an unknown user and a wrong password return the same status and the same body', async () => {
    credentials.findByUsername.mockResolvedValue(null);
    const unknownUser = await failureFrom();

    credentials.findByUsername.mockResolvedValue(await known());
    const wrongPassword = await failureFrom();

    expect(unknownUser.status).toBe(401);
    expect(wrongPassword.status).toBe(401);
    expect(unknownUser.body).toEqual({ errorCode: 'AUTH-401', message: 'Unauthorised' });
    expect(unknownUser.body).toEqual(wrongPassword.body);
  });

  it('neither path returns before doing comparable work', async () => {
    const RUNS = 5;
    const time = async (setup: () => Promise<unknown>) => {
      await setup();
      const samples: number[] = [];
      for (let i = 0; i < RUNS; i++) samples.push((await failureFrom()).ms);
      return samples.sort((a, b) => a - b)[Math.floor(RUNS / 2)]; // median
    };

    const unknown = await time(async () => credentials.findByUsername.mockResolvedValue(null));
    const wrong = await time(async () => credentials.findByUsername.mockResolvedValue(await known()));

    // Judge the shape, not the size. An early return shows up as one path
    // being a fraction of the other; a laptop under load moves both by tens of
    // milliseconds, so the threshold is generous on purpose.
    const ratio = Math.max(unknown, wrong) / Math.min(unknown, wrong);
    expect(ratio).toBeLessThan(2);

    // And neither is instant: both actually verified a hash.
    expect(Math.min(unknown, wrong)).toBeGreaterThan(20);
  });
});
