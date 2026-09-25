import { ExecutionContext } from '@nestjs/common';
import { LoginThrottleGuard } from './login-throttle.guard';
import { LoginAttempts, MAX_ATTEMPTS } from './login-attempts';

describe('LoginThrottleGuard', () => {
  const contextFor = (ip: string) =>
    ({ switchToHttp: () => ({ getRequest: () => ({ ip }) }) }) as ExecutionContext;

  const build = () => {
    const attempts = new LoginAttempts();
    return { attempts, guard: new LoginThrottleGuard(attempts) };
  };

  it('allows attempts up to the limit', () => {
    const { attempts, guard } = build();
    for (let i = 0; i < MAX_ATTEMPTS; i++) {
      expect(guard.canActivate(contextFor('10.0.0.1'))).toBe(true);
      attempts.recordFailure('10.0.0.1');
    }
  });

  it('refuses the next attempt once the limit is reached', () => {
    const { attempts, guard } = build();
    for (let i = 0; i < MAX_ATTEMPTS; i++) attempts.recordFailure('10.0.0.1');

    expect(() => guard.canActivate(contextFor('10.0.0.1'))).toThrow();
  });

  it('throttles one caller without touching another', () => {
    const { attempts, guard } = build();
    for (let i = 0; i < MAX_ATTEMPTS; i++) attempts.recordFailure('10.0.0.1');

    expect(() => guard.canActivate(contextFor('10.0.0.1'))).toThrow();
    expect(guard.canActivate(contextFor('10.0.0.2'))).toBe(true);
  });

  it('forgets a caller after a successful login', () => {
    const { attempts, guard } = build();
    for (let i = 0; i < MAX_ATTEMPTS; i++) attempts.recordFailure('10.0.0.1');
    attempts.recordSuccess('10.0.0.1');

    expect(guard.canActivate(contextFor('10.0.0.1'))).toBe(true);
  });

  // The bug this file did not catch: the guard and the route each held their
  // own counter, so nothing the route recorded was ever visible to the guard.
  it('counts what the route records, through shared state', () => {
    const attempts = new LoginAttempts();
    const guard = new LoginThrottleGuard(attempts);

    // the route's side of the transaction
    for (let i = 0; i < MAX_ATTEMPTS; i++) attempts.recordFailure('10.0.0.9');

    // the guard's side, which must see them
    expect(() => guard.canActivate(contextFor('10.0.0.9'))).toThrow();
  });
});
