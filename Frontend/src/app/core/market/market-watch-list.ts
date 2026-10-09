import { Injectable, InjectionToken, computed, effect, inject, signal, untracked } from '@angular/core';
import { Watchlist, WatchlistItem } from '../../../generated/watchlists';
import { WatchlistsApi } from '../api/watchlists-api';
import { Session } from '../session/session';

/**
 * Where the market watch was kept before Sprint 10, in this browser. Read
 * once, to start a customer's first server-side watchlist from it, then
 * cleared. null when the browser refuses storage.
 */
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

/** What a first watchlist starts with when this browser kept nothing: well-known stocks and two funds. */
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

/** The watchlists module's caps: entries in one watchlist, and watchlists an account. */
export const MARKET_WATCH_LIMIT = 50;
export const WATCHLISTS_LIMIT = 5;

/**
 * The market watch, kept on the server by the Sprint 10 watchlists module: up
 * to five watchlists an account, one shown at a time. A change shows at once
 * and is sent; if the server refuses it, it is put back and the reason kept
 * in `error`.
 */
@Injectable({ providedIn: 'root' })
export class MarketWatchList {
  private readonly api = inject(WatchlistsApi);
  private readonly storage = inject(MARKET_WATCH_STORAGE);
  private readonly accountId = inject(Session).accountId;
  private readonly lists = signal<readonly Watchlist[]>([]);
  private readonly selected = signal<number | null>(null);

  readonly watchlists = this.lists.asReadonly();
  /** Why the last read or change failed; null otherwise. */
  readonly error = signal<unknown>(null);
  /** The watchlist shown: the one picked, else the first. None until the server has answered, or if there are none. */
  readonly active = computed<Watchlist | null>(
    () => this.lists().find((list) => list.id === this.selected()) ?? this.lists().at(0) ?? null,
  );
  readonly symbols = computed(() => this.active()?.items.map((item) => item.symbol) ?? []);

  constructor() {
    effect(() => {
      const accountId = this.accountId();
      untracked(() => {
        this.lists.set([]);
        this.selected.set(null);
        if (accountId !== null) {
          void this.load(accountId);
        }
      });
    });
  }

  /** The entry for a symbol on the watchlist shown, with the stream's latest price for it. */
  item(symbol: string): WatchlistItem | undefined {
    return this.active()?.items.find((item) => item.symbol === symbol);
  }

  has(symbol: string): boolean {
    return this.symbols().includes(symbol);
  }

  get full(): boolean {
    return this.symbols().length >= MARKET_WATCH_LIMIT;
  }

  get canCreate(): boolean {
    return this.lists().length < WATCHLISTS_LIMIT;
  }

  select(watchlistId: number): void {
    this.selected.set(watchlistId);
  }

  /** Reads the watchlists again, for the prices the stream has brought since. */
  async refresh(): Promise<void> {
    const accountId = this.accountId();
    if (accountId === null) {
      return;
    }
    try {
      const lists = await this.api.list(accountId);
      if (this.accountId() === accountId) {
        this.lists.set(lists);
        this.error.set(null);
      }
    } catch (failure) {
      this.error.set(failure);
    }
  }

  /** A new, empty watchlist, shown at once. */
  async create(): Promise<void> {
    const accountId = this.accountId();
    if (accountId === null || !this.canCreate) {
      return;
    }
    try {
      const created = await this.api.create(accountId, `Watchlist ${this.lists().length + 1}`);
      this.lists.update((lists) => [...lists, created]);
      this.selected.set(created.id);
      this.error.set(null);
    } catch (failure) {
      this.error.set(failure);
    }
  }

  /** Adds to the end of the watchlist shown. False when it is already there or the watchlist is full. */
  add(symbol: string): boolean {
    const accountId = this.accountId();
    const list = this.active();
    if (accountId === null || list === null || this.has(symbol) || this.full) {
      return false;
    }
    const position = (list.items.at(-1)?.position ?? 0) + 1;
    this.change(list.id, (items) => [
      ...items,
      { symbol, position, lastPrice: null, changePercent: null, priceAsOf: null, stale: false },
    ]);
    this.api.addItem(accountId, list.id, symbol).then(
      () => this.error.set(null),
      (failure: unknown) => {
        this.change(list.id, (items) => items.filter((item) => item.symbol !== symbol));
        this.error.set(failure);
      },
    );
    return true;
  }

  remove(symbol: string): void {
    const accountId = this.accountId();
    const list = this.active();
    if (accountId === null || list === null) {
      return;
    }
    this.change(list.id, (items) => items.filter((item) => item.symbol !== symbol));
    this.api.removeItem(accountId, list.id, symbol).then(
      () => this.error.set(null),
      (failure: unknown) => {
        this.error.set(failure);
        void this.refresh();
      },
    );
  }

  private change(watchlistId: number, update: (items: WatchlistItem[]) => WatchlistItem[]): void {
    this.lists.update((lists) => lists.map((list) => (list.id === watchlistId ? { ...list, items: update(list.items) } : list)));
  }

  private async load(accountId: number): Promise<void> {
    try {
      let lists = await this.api.list(accountId);
      if (lists.length === 0) {
        lists = [await this.firstWatchlist(accountId)];
      }
      if (this.accountId() === accountId) {
        this.lists.set(lists);
        this.error.set(null);
      }
    } catch (failure) {
      this.error.set(failure);
    }
  }

  /**
   * A customer's first visit since the market watch moved to the server:
   * the first watchlist starts from what this browser kept, or the default.
   * One the server no longer lists is left out.
   */
  private async firstWatchlist(accountId: number): Promise<Watchlist> {
    const created = await this.api.create(accountId, 'Watchlist 1');
    const items: WatchlistItem[] = [];
    for (const symbol of this.kept(accountId)) {
      try {
        await this.api.addItem(accountId, created.id, symbol);
        items.push({ symbol, position: items.length + 1, lastPrice: null, changePercent: null, priceAsOf: null, stale: false });
      } catch {
        // Not listed any more: left out of the new watchlist.
      }
    }
    try {
      this.storage?.removeItem(this.key(accountId));
    } catch {
      // Refused: it is never read again anyway, once a watchlist exists.
    }
    return { ...created, items };
  }

  private key(accountId: number): string {
    return `yellow.market-watch.${accountId}`;
  }

  private kept(accountId: number): readonly string[] {
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
}
