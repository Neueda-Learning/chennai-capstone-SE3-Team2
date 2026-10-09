import { DOCUMENT } from '@angular/common';
import { Injectable, InjectionToken, effect, inject, signal, untracked } from '@angular/core';
import { Quote } from '../../../generated/extensions';
import { MarketDataApi } from '../api/market-data-api';

/**
 * How often prices on screen are re-read, and the seams the specs replace.
 *
 * Every 15 seconds while the tab is visible. The server keeps a stock's price
 * for a minute and a fund's NAV for half an hour, so re-reading more often
 * would only re-read the cache; a hidden tab re-reads nothing.
 */
export interface LivePricesPolicy {
  readonly intervalMs: number;
  /** Runs `task` every `ms`; returns a cancel function. */
  readonly every: (task: () => void, ms: number) => () => void;
  readonly visible: () => boolean;
}

export const LIVE_PRICES_POLICY = new InjectionToken<LivePricesPolicy>('LIVE_PRICES_POLICY', {
  providedIn: 'root',
  factory: () => {
    const document = inject(DOCUMENT);
    return {
      intervalMs: 15_000,
      every: (task, ms) => {
        const id = setInterval(task, ms);
        return () => clearInterval(id);
      },
      visible: () => document.visibilityState !== 'hidden',
    };
  },
});

/** The most symbols one quotes request takes. */
const QUOTES_LIMIT = 50;

/**
 * The latest price of every instrument on screen, shared by the market watch,
 * the instrument page and the order window. A screen says what it shows
 * (watch) and reads prices back (quote); one timer re-reads the lot.
 */
@Injectable({ providedIn: 'root' })
export class LivePrices {
  private readonly api = inject(MarketDataApi);
  private readonly policy = inject(LIVE_PRICES_POLICY);
  private readonly prices = signal<ReadonlyMap<string, Quote>>(new Map());
  private readonly interests = new Map<number, readonly string[]>();
  /** Being read now: not asked for again until the answer is in. */
  private readonly pending = new Set<string>();
  private nextInterest = 0;
  private stopTicking: (() => void) | null = null;

  /** Why the last read failed; the last prices stay up. null otherwise. */
  readonly error = signal<unknown>(null);

  constructor() {
    // Back on the tab: bring every price up to date at once.
    inject(DOCUMENT).addEventListener('visibilitychange', () => {
      if (this.policy.visible() && this.interests.size > 0) {
        void this.read(this.watched());
      }
    });
  }

  quote(symbol: string): Quote | undefined {
    return this.prices().get(symbol);
  }

  /** Keeps these symbols priced until the returned function is called. */
  watch(symbols: readonly string[]): () => void {
    const id = this.nextInterest++;
    this.interests.set(id, symbols);
    const unpriced = [...new Set(symbols)].filter((symbol) => !this.prices().has(symbol) && !this.pending.has(symbol));
    if (unpriced.length > 0) {
      void this.read(unpriced);
    }
    this.stopTicking ??= this.policy.every(() => this.tick(), this.policy.intervalMs);
    return () => {
      this.interests.delete(id);
      if (this.interests.size === 0) {
        this.stopTicking?.();
        this.stopTicking = null;
      }
    };
  }

  private watched(): string[] {
    return [...new Set([...this.interests.values()].flat())];
  }

  private tick(): void {
    if (this.policy.visible()) {
      void this.read(this.watched());
    }
  }

  /** All chunks at once. A failed chunk keeps its last prices. */
  private async read(symbols: readonly string[]): Promise<void> {
    const chunks: string[][] = [];
    for (let start = 0; start < symbols.length; start += QUOTES_LIMIT) {
      chunks.push(symbols.slice(start, start + QUOTES_LIMIT));
    }
    await Promise.all(
      chunks.map(async (chunk) => {
        chunk.forEach((symbol) => this.pending.add(symbol));
        try {
          const quotes = await this.api.quotes(chunk);
          this.prices.update((prices) => new Map([...prices, ...quotes.map((q) => [q.symbol, q] as const)]));
          this.error.set(null);
        } catch (failure) {
          this.error.set(failure);
        } finally {
          chunk.forEach((symbol) => this.pending.delete(symbol));
        }
      }),
    );
  }
}

/**
 * Keeps the symbols a component shows priced while it is on screen. Call in
 * a constructor; the watch follows the symbols as they change and ends with
 * the component.
 */
export function watchPrices(symbols: () => readonly string[]): LivePrices {
  const live = inject(LivePrices);
  effect((onCleanup) => {
    const list = symbols();
    if (list.length > 0) {
      onCleanup(untracked(() => live.watch(list)));
    }
  });
  return live;
}
