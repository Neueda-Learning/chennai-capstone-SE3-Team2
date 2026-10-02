import { ExecutionContext } from '@nestjs/common';
import RedisMock from 'ioredis-mock';
import { LoginThrottleGuard } from './login-throttle.guard';
import { LoginAttempts, MAX_ATTEMPTS } from './login-attempts';
import { InMemoryAttemptStore } from './attempt-store';
import { RedisAttemptStore } from './redis-attempt-store';

describe('LoginThrottleGuard', () => {
  const contextFor = (ip: string) =>
    ({ switchToHttp: () => ({ getRequest: () => ({ ip }) }) }) as ExecutionContext;

  const build = () => {
    const attempts = new LoginAttempts(new InMemoryAttemptStore());
    return { attempts, guard: new LoginThrottleGuard(attempts) };
  };

  it('allows attempts up to the limit', async () => {
    const { attempts, guard } = build();
    for (let i = 0; i < MAX_ATTEMPTS; i++) {
      await expect(guard.canActivate(contextFor('10.0.0.1'))).resolves.toBe(true);
      await attempts.recordFailure('10.0.0.1');
    }
  });

  it('refuses the next attempt once the limit is reached', async () => {
    const { attempts, guard } = build();
    for (let i = 0; i < MAX_ATTEMPTS; i++) await attempts.recordFailure('10.0.0.1');

    await expect(guard.canActivate(contextFor('10.0.0.1'))).rejects.toMatchObject({ status: 429 });
  });

  it('throttles one caller without touching another', async () => {
    const { attempts, guard } = build();
    for (let i = 0; i < MAX_ATTEMPTS; i++) await attempts.recordFailure('10.0.0.1');

    await expect(guard.canActivate(contextFor('10.0.0.1'))).rejects.toMatchObject({ status: 429 });
    await expect(guard.canActivate(contextFor('10.0.0.2'))).resolves.toBe(true);
  });

  it('forgets a caller after a successful login', async () => {
    const { attempts, guard } = build();
    for (let i = 0; i < MAX_ATTEMPTS; i++) await attempts.recordFailure('10.0.0.1');
    await attempts.recordSuccess('10.0.0.1');

    await expect(guard.canActivate(contextFor('10.0.0.1'))).resolves.toBe(true);
  });

  it('lets a caller back in once the cooldown has passed', async () => {
    jest.useFakeTimers({ now: Date.parse('2026-10-02T09:00:00Z') });
    try {
      const { attempts, guard } = build();
      for (let i = 0; i < MAX_ATTEMPTS; i++) await attempts.recordFailure('10.0.0.1');
      await expect(guard.canActivate(contextFor('10.0.0.1'))).rejects.toMatchObject({ status: 429 });

      jest.setSystemTime(Date.now() + 60_000);

      await expect(guard.canActivate(contextFor('10.0.0.1'))).resolves.toBe(true);
    } finally {
      jest.useRealTimers();
    }
  });

  // The bug this file did not catch: the guard and the route each held their
  // own counter, so nothing the route recorded was ever visible to the guard.
  it('counts what the route records, through shared state', async () => {
    const attempts = new LoginAttempts(new InMemoryAttemptStore());
    const guard = new LoginThrottleGuard(attempts);

    // the route's side of the transaction
    for (let i = 0; i < MAX_ATTEMPTS; i++) await attempts.recordFailure('10.0.0.9');

    // the guard's side, which must see them
    await expect(guard.canActivate(contextFor('10.0.0.9'))).rejects.toMatchObject({ status: 429 });
  });

  describe('across instances', () => {
    /** One login attempt through one instance: the guard, then a failed login recorded. */
    const failedLogin = async (instance: { guard: LoginThrottleGuard; attempts: LoginAttempts }, ip: string) => {
      await instance.guard.canActivate(contextFor(ip));
      await instance.attempts.recordFailure(ip);
    };

    const instanceOn = (store: InMemoryAttemptStore | RedisAttemptStore) => {
      const attempts = new LoginAttempts(store);
      return { attempts, guard: new LoginThrottleGuard(attempts) };
    };

    let redis: InstanceType<typeof RedisMock>;
    beforeEach(async () => {
      redis = new RedisMock();
      await redis.flushall();
    });
    afterEach(() => redis.disconnect());

    // What A07 asked for: two instances behind a load balancer, the caller's
    // attempts landing on each in turn, and the limit still five in total.
    it('blocks the sixth failed attempt when two instances share one store', async () => {
      const store = new RedisAttemptStore(redis);
      const a = instanceOn(store);
      const b = instanceOn(store);

      // five failures, alternating a, b, a, b, a -- three on one, two on the other
      for (let i = 0; i < MAX_ATTEMPTS; i++) await failedLogin(i % 2 === 0 ? a : b, '10.0.0.7');

      // the sixth is refused, whichever instance it reaches
      await expect(b.guard.canActivate(contextFor('10.0.0.7'))).rejects.toMatchObject({ status: 429 });
      await expect(a.guard.canActivate(contextFor('10.0.0.7'))).rejects.toMatchObject({ status: 429 });
    });

    it('a success on one instance clears the count on the other', async () => {
      const store = new RedisAttemptStore(redis);
      const a = instanceOn(store);
      const b = instanceOn(store);
      for (let i = 0; i < MAX_ATTEMPTS; i++) await a.attempts.recordFailure('10.0.0.7');

      await b.attempts.recordSuccess('10.0.0.7');

      await expect(a.guard.canActivate(contextFor('10.0.0.7'))).resolves.toBe(true);
    });

    // The old behaviour, kept as the contrast: a store each, and the same five
    // failures leave both instances still letting the caller in.
    it('with a store per instance, the same five failures block nobody', async () => {
      const a = instanceOn(new InMemoryAttemptStore());
      const b = instanceOn(new InMemoryAttemptStore());

      for (let i = 0; i < MAX_ATTEMPTS; i++) await failedLogin(i % 2 === 0 ? a : b, '10.0.0.7');

      await expect(a.guard.canActivate(contextFor('10.0.0.7'))).resolves.toBe(true);
      await expect(b.guard.canActivate(contextFor('10.0.0.7'))).resolves.toBe(true);
    });
  });
});
