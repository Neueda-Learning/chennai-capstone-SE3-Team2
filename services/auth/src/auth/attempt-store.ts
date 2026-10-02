/** Injection token for the store behind LoginAttempts. */
export const ATTEMPT_STORE = 'ATTEMPT_STORE';

/**
 * Where failed logins are counted. A key's window opens on its first failure
 * and is not extended by later ones, so a caller who keeps failing is locked
 * out for the rest of that window rather than forever.
 */
export interface AttemptStore {
  /** Count one failure; the first in a window starts it. Returns the count so far. */
  increment(key: string, windowMs: number): Promise<number>;

  /** Failures in the open window, or 0 once it has closed. */
  count(key: string): Promise<number>;

  /** Forget the key. */
  clear(key: string): Promise<void>;
}

/** Per-process. The fallback when Redis is down, and the store in unit tests. */
export class InMemoryAttemptStore implements AttemptStore {
  private readonly attempts = new Map<string, { count: number; expiresAt: number }>();

  async increment(key: string, windowMs: number): Promise<number> {
    const now = Date.now();
    const seen = this.attempts.get(key);
    if (!seen || now >= seen.expiresAt) {
      this.attempts.set(key, { count: 1, expiresAt: now + windowMs });
      return 1;
    }
    seen.count += 1;
    return seen.count;
  }

  async count(key: string): Promise<number> {
    const seen = this.attempts.get(key);
    if (!seen) return 0;
    if (Date.now() >= seen.expiresAt) {
      this.attempts.delete(key);
      return 0;
    }
    return seen.count;
  }

  async clear(key: string): Promise<void> {
    this.attempts.delete(key);
  }
}
