import { InjectionToken } from '@angular/core';

/**
 * How a screen brings something still in progress up to date: an order at
 * NEW on the blotter, a transfer at PENDING on the Cash page. Neither API
 * pushes to the browser, so while something is in progress the screen
 * re-reads -- never re-sends what it is waiting on.
 *
 * Every 3 seconds, at most 10 times: 30 seconds and 10 requests per burst.
 * An order or a transfer normally resolves within a few seconds, so the first
 * re-read or two usually ends it; 30 seconds covers a slow executor or gateway
 * without polling for as long as the tab is open. After that the screen says
 * it is still in progress, and Refresh starts a new burst.
 */
export interface RereadPolicy {
  readonly intervalMs: number;
  readonly maxRereads: number;
  /** Runs `task` after `ms`; returns a cancel function. A seam for the specs. */
  readonly schedule: (task: () => void, ms: number) => () => void;
}

export const REREAD_POLICY = new InjectionToken<RereadPolicy>('REREAD_POLICY', {
  providedIn: 'root',
  factory: () => ({
    intervalMs: 3000,
    maxRereads: 10,
    schedule: (task, ms) => {
      const id = setTimeout(task, ms);
      return () => clearTimeout(id);
    },
  }),
});
