import Redis, { Result } from 'ioredis';
import { AttemptStore } from './attempt-store';

declare module 'ioredis' {
  interface RedisCommander<Context> {
    countLoginFailure(key: string, windowMs: number): Result<number, Context>;
  }
}

/**
 * INCR, and on the first failure set the expiry, in one step. Done as two
 * separate commands, a crash between them leaves a key with no expiry and a
 * caller locked out until somebody deletes it by hand.
 */
const COUNT_FAILURE = `
local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return count
`;

/**
 * The shared counter: every instance of the service reads and writes the same
 * key for a caller, so the limit holds however many instances there are.
 * One key per caller address; it expires on its own when the window closes.
 */
export class RedisAttemptStore implements AttemptStore {
  constructor(
    private readonly redis: Redis,
    private readonly prefix = 'login-attempts:',
  ) {
    redis.defineCommand('countLoginFailure', { numberOfKeys: 1, lua: COUNT_FAILURE });
  }

  async increment(key: string, windowMs: number): Promise<number> {
    return Number(await this.redis.countLoginFailure(this.prefix + key, windowMs));
  }

  async count(key: string): Promise<number> {
    return Number((await this.redis.get(this.prefix + key)) ?? 0);
  }

  async clear(key: string): Promise<void> {
    await this.redis.del(this.prefix + key);
  }

  async connect(): Promise<void> {
    await this.redis.connect();
  }

  async close(): Promise<void> {
    this.redis.disconnect();
  }
}

/**
 * A client that fails fast. With ioredis's defaults a command issued while
 * Redis is down is queued and retried twenty times, and the login request
 * waits behind it; here it fails at once and the fallback takes over.
 */
export function createRedisClient(url: string): Redis {
  const redis = new Redis(url, {
    lazyConnect: true,
    enableOfflineQueue: false,
    maxRetriesPerRequest: 1,
    connectTimeout: 2_000,
    commandTimeout: 500,
  });
  // ioredis raises 'error' on every failed reconnect; unhandled, each one is
  // printed as a stack. The fallback store reports the outage instead.
  redis.on('error', () => undefined);
  return redis;
}
