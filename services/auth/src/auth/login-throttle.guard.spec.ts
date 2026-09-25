import { ExecutionContext } from '@nestjs/common';
import { LoginThrottleGuard, MAX_ATTEMPTS } from './login-throttle.guard';

describe('LoginThrottleGuard', () => {
  const contextFor = (ip: string) =>
    ({ switchToHttp: () => ({ getRequest: () => ({ ip }) }) }) as ExecutionContext;

  it('allows attempts up to the limit', () => {
    const guard = new LoginThrottleGuard();
    for (let i = 0; i < MAX_ATTEMPTS; i++) {
      expect(guard.canActivate(contextFor('10.0.0.1'))).toBe(true);
      guard.recordFailure('10.0.0.1');
    }
  });

  it('refuses the next attempt once the limit is reached', () => {
    const guard = new LoginThrottleGuard();
    for (let i = 0; i < MAX_ATTEMPTS; i++) guard.recordFailure('10.0.0.1');

    expect(() => guard.canActivate(contextFor('10.0.0.1'))).toThrow();
  });

  it('throttles one caller without touching another', () => {
    const guard = new LoginThrottleGuard();
    for (let i = 0; i < MAX_ATTEMPTS; i++) guard.recordFailure('10.0.0.1');

    expect(() => guard.canActivate(contextFor('10.0.0.1'))).toThrow();
    expect(guard.canActivate(contextFor('10.0.0.2'))).toBe(true);
  });

  it('forgets a caller after a successful login', () => {
    const guard = new LoginThrottleGuard();
    for (let i = 0; i < MAX_ATTEMPTS; i++) guard.recordFailure('10.0.0.1');
    guard.recordSuccess('10.0.0.1');

    expect(guard.canActivate(contextFor('10.0.0.1'))).toBe(true);
  });
});
