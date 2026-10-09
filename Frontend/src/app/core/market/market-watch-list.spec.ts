import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { expectTypeOf } from 'vitest';
import { Watchlist } from '../../../generated/watchlists';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../api/provide-clients';
import { Session } from '../session/session';
import { DEFAULT_MARKET_WATCH, MARKET_WATCH_LIMIT, MARKET_WATCH_STORAGE, MarketWatchList } from './market-watch-list';

const ACCOUNT = 'http://trade.test/api/v1/accounts/3';

/** A browser's localStorage, kept in memory. */
class MemoryStorage implements Storage {
  private readonly items = new Map<string, string>();
  get length(): number {
    return this.items.size;
  }
  clear(): void {
    this.items.clear();
  }
  getItem(key: string): string | null {
    return this.items.get(key) ?? null;
  }
  key(index: number): string | null {
    return [...this.items.keys()][index] ?? null;
  }
  removeItem(key: string): void {
    this.items.delete(key);
  }
  setItem(key: string, value: string): void {
    this.items.set(key, value);
  }
}

const watchlist = (id: number, symbols: string[], name = `Watchlist ${id}`): Watchlist => ({
  id,
  name,
  position: id,
  items: symbols.map((symbol, i) => ({ symbol, position: i + 1, lastPrice: null, changePercent: null, priceAsOf: null, stale: false })),
});

describe('MarketWatchList', () => {
  let storage: MemoryStorage;
  let http: HttpTestingController;

  async function settle(): Promise<void> {
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
  }

  beforeEach(() => {
    sessionStorage.clear();
    storage = new MemoryStorage();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' }),
        { provide: MARKET_WATCH_STORAGE, useValue: storage },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function signedIn(lists: Watchlist[]): Promise<MarketWatchList> {
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    const list = TestBed.inject(MarketWatchList);
    TestBed.tick();
    http.expectOne(`${ACCOUNT}/watchlists`).flush(lists);
    await settle();
    return list;
  }

  it('shows the first watchlist the server keeps for the account', async () => {
    const list = await signedIn([watchlist(1, ['ITC.NS', 'MRF.NS']), watchlist(2, ['SBIN.NS'])]);

    expect(list.symbols()).toEqual(['ITC.NS', 'MRF.NS']);
    expect(list.watchlists().length).toBe(2);
  });

  it('shows no watchlist before the server has answered, and its type says there may be none', () => {
    const list = TestBed.inject(MarketWatchList);

    // A template reading list.active()?.name is right to guard: the type must say so.
    expectTypeOf(list.active).returns.toEqualTypeOf<Watchlist | null>();
    expect(list.active()).toBeNull();
    expect(list.symbols()).toEqual([]);
  });

  it('shows another watchlist when one is picked', async () => {
    const list = await signedIn([watchlist(1, ['ITC.NS']), watchlist(2, ['SBIN.NS'])]);

    list.select(2);

    expect(list.symbols()).toEqual(['SBIN.NS']);
  });

  it('on a first visit, starts the first watchlist from what this browser kept, then forgets it', async () => {
    storage.setItem('yellow.market-watch.3', JSON.stringify(['TATASTEEL.NS', 'ITC.NS']));
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    const list = TestBed.inject(MarketWatchList);
    TestBed.tick();

    http.expectOne(`${ACCOUNT}/watchlists`).flush([]);
    await settle();
    const created = http.expectOne((r) => r.url === `${ACCOUNT}/watchlists` && r.method === 'POST');
    expect(created.request.body).toEqual({ name: 'Watchlist 1' });
    created.flush(watchlist(9, []));
    for (const symbol of ['TATASTEEL.NS', 'ITC.NS']) {
      await settle();
      http.expectOne(`${ACCOUNT}/watchlists/9/items/${symbol}`).flush(null, { status: 204, statusText: 'No Content' });
    }
    await settle();

    expect(list.symbols()).toEqual(['TATASTEEL.NS', 'ITC.NS']);
    expect(storage.getItem('yellow.market-watch.3')).toBeNull();
  });

  it('on a first visit with nothing kept, starts from the default list', async () => {
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    const list = TestBed.inject(MarketWatchList);
    TestBed.tick();
    http.expectOne(`${ACCOUNT}/watchlists`).flush([]);
    await settle();
    http.expectOne((r) => r.method === 'POST').flush(watchlist(9, []));
    for (const symbol of DEFAULT_MARKET_WATCH) {
      await settle();
      http.expectOne(`${ACCOUNT}/watchlists/9/items/${symbol}`).flush(null, { status: 204, statusText: 'No Content' });
    }
    await settle();

    expect(list.symbols()).toEqual(DEFAULT_MARKET_WATCH);
  });

  it('adds to the end at once and sends it; refused, it is put back and the reason kept', async () => {
    const list = await signedIn([watchlist(1, ['ITC.NS'])]);

    expect(list.add('SBIN.NS')).toBe(true);
    expect(list.add('SBIN.NS')).toBe(false);
    expect(list.symbols()).toEqual(['ITC.NS', 'SBIN.NS']);
    http
      .expectOne((r) => r.method === 'PUT' && r.url === `${ACCOUNT}/watchlists/1/items/SBIN.NS`)
      .flush({ errorCode: 'LIM-409', message: 'At most 50 instruments a watchlist' }, { status: 409, statusText: 'Conflict' });
    await settle();

    expect(list.symbols()).toEqual(['ITC.NS']);
    expect(list.error()).not.toBeNull();
  });

  it('removes with a DELETE to the watchlist shown', async () => {
    const list = await signedIn([watchlist(1, ['ITC.NS', 'MRF.NS'])]);

    list.remove('MRF.NS');

    expect(list.symbols()).toEqual(['ITC.NS']);
    const removed = http.expectOne(`${ACCOUNT}/watchlists/1/items/MRF.NS`);
    expect(removed.request.method).toBe('DELETE');
    removed.flush(null, { status: 204, statusText: 'No Content' });
  });

  it(`holds at most ${MARKET_WATCH_LIMIT} in one watchlist`, async () => {
    const full = Array.from({ length: MARKET_WATCH_LIMIT }, (_, i) => `S${i}.NS`);
    const list = await signedIn([watchlist(1, full)]);

    expect(list.full).toBe(true);
    expect(list.add('ONEMORE.NS')).toBe(false);
  });

  it('creates a new watchlist and shows it, up to five', async () => {
    const list = await signedIn([watchlist(1, ['ITC.NS'])]);

    const creating = list.create();
    const request = http.expectOne((r) => r.method === 'POST' && r.url === `${ACCOUNT}/watchlists`);
    expect(request.request.body).toEqual({ name: 'Watchlist 2' });
    request.flush(watchlist(2, []));
    await creating;

    expect(list.symbols()).toEqual([]);
    expect(list.watchlists().length).toBe(2);
    expect(list.canCreate).toBe(true);
  });
});
