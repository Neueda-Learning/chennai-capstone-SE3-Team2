import { Logger, LoggerService, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { AttemptStore, InMemoryAttemptStore } from './attempt-store';
import { createRedisClient, RedisAttemptStore } from './redis-attempt-store';

interface Connectable {
  connect?(): Promise<void>;
  close?(): Promise<void>;
}

/**
 * Uses the shared store, and the in-memory one whenever the shared store fails.
 *
 * Logins keep working through a Redis outage, throttled per instance instead
 * of across all of them. The warning is logged once when the outage starts,
 * not on every request, and recovery is logged when Redis answers again.
 * Counts taken during the outage stay in memory and are not copied back.
 */
export class FallbackAttemptStore implements AttemptStore, OnModuleInit, OnModuleDestroy {
  private degraded = false;

  constructor(
    private readonly primary: AttemptStore & Connectable,
    private readonly fallback: AttemptStore = new InMemoryAttemptStore(),
    private readonly log: LoggerService = new Logger('LoginAttempts'),
  ) {}

  /** Start-up does not wait on Redis: unreachable, the fallback serves until it returns. */
  async onModuleInit(): Promise<void> {
    try {
      await this.primary.connect?.();
    } catch (error) {
      this.degrade(error);
    }
  }

  async onModuleDestroy(): Promise<void> {
    await this.primary.close?.();
  }

  increment(key: string, windowMs: number): Promise<number> {
    return this.run((store) => store.increment(key, windowMs));
  }

  count(key: string): Promise<number> {
    return this.run((store) => store.count(key));
  }

  clear(key: string): Promise<void> {
    return this.run((store) => store.clear(key));
  }

  private async run<T>(op: (store: AttemptStore) => Promise<T>): Promise<T> {
    try {
      const result = await op(this.primary);
      if (this.degraded) {
        this.degraded = false;
        this.log.log('Redis reachable again; the login throttle is shared across instances');
      }
      return result;
    } catch (error) {
      this.degrade(error);
      return op(this.fallback);
    }
  }

  private degrade(error: unknown): void {
    if (this.degraded) return;
    this.degraded = true;
    const reason = error instanceof Error ? error.message : String(error);
    this.log.warn(`Redis unreachable (${reason}); the login throttle has fallen back to an in-memory counter, per instance`);
  }
}

/** REDIS_URL set: Redis with the in-memory fallback. Unset: in-memory only, and say so. */
export function attemptStoreFor(redisUrl: string | undefined, log: LoggerService = new Logger('LoginAttempts')): AttemptStore {
  if (!redisUrl) {
    log.warn('REDIS_URL is not set; the login throttle is in-memory and per instance');
    return new InMemoryAttemptStore();
  }
  return new FallbackAttemptStore(new RedisAttemptStore(createRedisClient(redisUrl)), new InMemoryAttemptStore(), log);
}
