import { LoggerService } from '@nestjs/common';
import RedisMock from 'ioredis-mock';
import { AttemptStore, InMemoryAttemptStore } from './attempt-store';
import { RedisAttemptStore } from './redis-attempt-store';
import { attemptStoreFor, FallbackAttemptStore } from './fallback-attempt-store';

const logger = (): jest.Mocked<LoggerService> => ({ log: jest.fn(), warn: jest.fn(), error: jest.fn() });

describe('RedisAttemptStore', () => {
  let redis: InstanceType<typeof RedisMock>;
  let store: RedisAttemptStore;

  beforeEach(async () => {
    redis = new RedisMock();
    await redis.flushall();
    store = new RedisAttemptStore(redis);
  });
  afterEach(() => redis.disconnect());

  it('keeps one key per caller address, counted with INCR', async () => {
    await store.increment('10.0.0.1', 60_000);
    await store.increment('10.0.0.1', 60_000);
    await store.increment('10.0.0.2', 60_000);

    expect(await redis.get('login-attempts:10.0.0.1')).toBe('2');
    expect(await redis.get('login-attempts:10.0.0.2')).toBe('1');
    expect(await store.count('10.0.0.1')).toBe(2);
  });

  it('sets a 60 second expiry on the first failure', async () => {
    await store.increment('10.0.0.1', 60_000);

    const ttl = await redis.pttl('login-attempts:10.0.0.1');
    expect(ttl).toBeGreaterThan(59_000);
    expect(ttl).toBeLessThanOrEqual(60_000);
  });

  it('does not extend the expiry on later failures', async () => {
    await store.increment('10.0.0.1', 60_000);
    await redis.pexpire('login-attempts:10.0.0.1', 10_000); // as if 50 seconds had passed

    await store.increment('10.0.0.1', 60_000);

    expect(await redis.pttl('login-attempts:10.0.0.1')).toBeLessThanOrEqual(10_000);
  });

  it('deletes the key on success', async () => {
    await store.increment('10.0.0.1', 60_000);

    await store.clear('10.0.0.1');

    expect(await redis.exists('login-attempts:10.0.0.1')).toBe(0);
    expect(await store.count('10.0.0.1')).toBe(0);
  });

  it('reads an unknown caller as zero', async () => {
    expect(await store.count('10.9.9.9')).toBe(0);
  });
});

describe('FallbackAttemptStore', () => {
  const unreachable = (): AttemptStore => {
    const down = () => Promise.reject(new Error('connect ECONNREFUSED 127.0.0.1:6379'));
    return { increment: down, count: down, clear: down };
  };

  it('counts in memory while Redis is unreachable, and logs a warning', async () => {
    const log = logger();
    const store = new FallbackAttemptStore(unreachable(), new InMemoryAttemptStore(), log);

    await store.increment('10.0.0.1', 60_000);
    await store.increment('10.0.0.1', 60_000);

    expect(await store.count('10.0.0.1')).toBe(2);
    expect(log.warn).toHaveBeenCalledWith(expect.stringContaining('Redis unreachable (connect ECONNREFUSED'));
  });

  it('warns once per outage, not once per request', async () => {
    const log = logger();
    const store = new FallbackAttemptStore(unreachable(), new InMemoryAttemptStore(), log);

    for (let i = 0; i < 10; i++) await store.increment('10.0.0.1', 60_000);

    expect(log.warn).toHaveBeenCalledTimes(1);
  });

  it('goes back to Redis when it answers again, and says so', async () => {
    const log = logger();
    const redis = new RedisMock();
    await redis.flushall();
    const shared = new RedisAttemptStore(redis);
    let up = false;
    const flaky: AttemptStore = {
      increment: (k, w) => (up ? shared.increment(k, w) : Promise.reject(new Error('down'))),
      count: (k) => (up ? shared.count(k) : Promise.reject(new Error('down'))),
      clear: (k) => (up ? shared.clear(k) : Promise.reject(new Error('down'))),
    };
    const store = new FallbackAttemptStore(flaky, new InMemoryAttemptStore(), log);

    await store.increment('10.0.0.1', 60_000);
    up = true;
    await store.increment('10.0.0.1', 60_000);

    expect(await redis.get('login-attempts:10.0.0.1')).toBe('1');
    expect(log.log).toHaveBeenCalledWith(expect.stringContaining('Redis reachable again'));
    redis.disconnect();
  });

  it('does not stop the service starting when Redis cannot be reached', async () => {
    const log = logger();
    const store = new FallbackAttemptStore(
      { ...unreachable(), connect: () => Promise.reject(new Error('connect ECONNREFUSED')) },
      new InMemoryAttemptStore(),
      log,
    );

    await expect(store.onModuleInit()).resolves.toBeUndefined();
    expect(log.warn).toHaveBeenCalledTimes(1);
  });
});

describe('attemptStoreFor', () => {
  it('without REDIS_URL, counts in memory and warns that the limit is per instance', () => {
    const log = logger();

    expect(attemptStoreFor(undefined, log)).toBeInstanceOf(InMemoryAttemptStore);
    expect(log.warn).toHaveBeenCalledWith(expect.stringContaining('REDIS_URL is not set'));
  });

  it('with REDIS_URL, uses Redis behind the fallback', () => {
    expect(attemptStoreFor('redis://localhost:6379', logger())).toBeInstanceOf(FallbackAttemptStore);
  });
});
