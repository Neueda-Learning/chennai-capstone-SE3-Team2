import { Injectable, InjectionToken, effect, inject, signal, untracked } from '@angular/core';
import { Session } from '../session/session';

/** Where the list is kept. null when the browser refuses storage: the list then lasts the visit. */
export const MARKET_WATCH_STORAGE = new InjectionToken<Storage | null>('MARKET_WATCH_STORAGE', {
  providedIn: 'root',
  factory: () => {
    try {
      return localStorage;
    } catch {
      return null;
    }
  },
});

/** What a new market watch starts with: well-known stocks and two index-style funds. */
export const DEFAULT_MARKET_WATCH: readonly string[] = [
  'RELIANCE.NS',
  'HDFCBANK.NS',
  'TCS.NS',
  'INFY.NS',
  'ICICIBANK.NS',
  'SBIN.NS',
  'ITC.NS',
  'MRF.NS',
  '120716',
  '122639',
];

/** The quotes route prices at most 50 symbols a request; one list is one request. */
export const MARKET_WATCH_LIMIT = 50;

/**
 * The instruments a customer keeps an eye on, in their order. Kept in this
 * browser, one list per account, until the Sprint 10 watchlists module keeps
 * it on the server; nothing here is sent anywhere.
 */
@Injectable({ providedIn: 'root' })
export class MarketWatchList {
  private readonly storage = inject(MARKET_WATCH_STORAGE);
  private readonly accountId = inject(Session).accountId;
  private readonly list = signal<readonly string[]>([]);

  readonly symbols = this.list.asReadonly();

  constructor() {
    effect(() => {
      const accountId = this.accountId();
      untracked(() => this.list.set(accountId === null ? [] : this.read(accountId)));
    });
  }

  has(symbol: string): boolean {
    return this.list().includes(symbol);
  }

  get full(): boolean {
    return this.list().length >= MARKET_WATCH_LIMIT;
  }

  /** Adds to the end. False when it is already there or the list is full. */
  add(symbol: string): boolean {
    if (this.has(symbol) || this.full) {
      return false;
    }
    this.save([...this.list(), symbol]);
    return true;
  }

  remove(symbol: string): void {
    this.save(this.list().filter((s) => s !== symbol));
  }

  private key(accountId: number): string {
    return `yellow.market-watch.${accountId}`;
  }

  private read(accountId: number): readonly string[] {
    try {
      const stored = this.storage?.getItem(this.key(accountId));
      if (stored !== null && stored !== undefined) {
        const parsed: unknown = JSON.parse(stored);
        if (Array.isArray(parsed) && parsed.every((s) => typeof s === 'string')) {
          return parsed.slice(0, MARKET_WATCH_LIMIT);
        }
      }
    } catch {
      // Unreadable or refused: start from the default.
    }
    return DEFAULT_MARKET_WATCH;
  }

  private save(symbols: readonly string[]): void {
    this.list.set(symbols);
    const accountId = this.accountId();
    if (accountId === null) {
      return;
    }
    try {
      this.storage?.setItem(this.key(accountId), JSON.stringify(symbols));
    } catch {
      // Storage full or refused: the list still lasts the visit.
    }
  }
}
