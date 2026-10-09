import { provideHttpClient } from '@angular/common/http';
import { HttpRequest } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { InstrumentResponse, Quote } from '../../../generated/extensions';
import { Watchlist, WatchlistItem } from '../../../generated/watchlists';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { LIVE_PRICES_POLICY } from '../../core/market/live-prices';
import { MARKET_WATCH_STORAGE } from '../../core/market/market-watch-list';
import { Session } from '../../core/session/session';
import { SEARCH_DELAY_MS } from '../../shared/instrument-search/instrument-search';
import { MarketWatch, trendOf } from './market-watch';

const TRADE = 'http://trade.test';
const INSTRUMENTS = `${TRADE}/api/v1/instruments`;
const WATCHLISTS = `${TRADE}/api/v1/accounts/3/watchlists`;
const LISTED: InstrumentResponse[] = [
  { symbol: 'MRF.NS', name: 'MRF Limited', type: 'STOCK', exchange: 'NSE', tradable: true },
  { symbol: 'ITC.NS', name: 'ITC Limited', type: 'STOCK', exchange: 'NSE', tradable: true },
  { symbol: '122639', name: 'Parag Parikh Flexi Cap Fund - Direct Plan - Growth', type: 'MF', exchange: null, tradable: true },
];
const QUOTES: Quote[] = [
  { symbol: 'MRF.NS', price: 123525, change: -1755, changePercent: -1.4, currency: 'INR', stale: true, asOf: '2026-10-01T09:59:51Z' },
  { symbol: 'ITC.NS', price: 266.65, change: 2.25, changePercent: 0.85, currency: 'INR', stale: false, asOf: '2026-10-06T05:00:00Z' },
  { symbol: '122639', price: 88.762, currency: 'INR', stale: false, asOf: '2026-10-04T18:30:00Z' },
];

/** sessionStorage-like, in memory. */
const memory = (initial: Record<string, string> = {}): Storage => {
  const items = new Map(Object.entries(initial));
  return {
    get length() {
      return items.size;
    },
    clear: () => items.clear(),
    getItem: (key) => items.get(key) ?? null,
    key: (index) => [...items.keys()][index] ?? null,
    removeItem: (key) => void items.delete(key),
    setItem: (key, value) => void items.set(key, value),
  };
};

describe('trendOf', () => {
  it('is up or down by the day change, and flat without one', () => {
    expect(trendOf(QUOTES[1])).toBe('up');
    expect(trendOf(QUOTES[0])).toBe('down');
    expect(trendOf(QUOTES[2])).toBe('flat');
    expect(trendOf(undefined)).toBe('flat');
  });
});

describe('MarketWatch', () => {
  let fixture: ComponentFixture<MarketWatch>;
  let page: HTMLElement;
  let http: HttpTestingController;
  let storage: Storage;

  const entry = (symbol: string, position: number, lastPrice: number | null = null): WatchlistItem => ({
    symbol,
    position,
    lastPrice,
    changePercent: lastPrice === null ? null : 0.4,
    priceAsOf: lastPrice === null ? null : '2026-10-06T05:00:00Z',
    stale: false,
  });

  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  /** Signs in with this watchlist on the server, renders, and answers the lookup and the prices. */
  async function render(stored: string[], lists?: Watchlist[]): Promise<void> {
    sessionStorage.clear();
    storage = memory();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
        { provide: MARKET_WATCH_STORAGE, useValue: storage },
        { provide: SEARCH_DELAY_MS, useValue: 0 },
        { provide: LIVE_PRICES_POLICY, useValue: { intervalMs: 15000, every: () => () => undefined, visible: () => true } },
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MarketWatch);
    page = fixture.nativeElement as HTMLElement;
    await settle();
    http
      .expectOne(WATCHLISTS)
      .flush(lists ?? [{ id: 1, name: 'Watchlist 1', position: 1, items: stored.map((symbol, i) => entry(symbol, i + 1)) }]);
    await settle();
    answer();
    await settle();
  }

  /** Answers every pending lookup and price read from the fixtures. */
  function answer(): void {
    for (const lookup of http.match((r) => r.url === INSTRUMENTS && r.params.has('symbols'))) {
      const symbols = lookup.request.params.get('symbols')!.split(',');
      lookup.flush(LISTED.filter((i) => symbols.includes(i.symbol)));
    }
    for (const read of http.match((r) => r.url === `${TRADE}/api/v1/quotes`)) {
      const symbols = read.request.params.get('symbols')!.split(',');
      read.flush(QUOTES.filter((q) => symbols.includes(q.symbol)));
    }
  }

  afterEach(() => http.verify());

  const rows = () => [...page.querySelectorAll<HTMLElement>('[data-testid="watch-row"]')];

  it('shows each instrument with its price and day change; a stock by ticker, a fund by name', async () => {
    await render(['MRF.NS', 'ITC.NS', '122639']);

    expect(rows().map((r) => r.querySelector('.label')?.textContent?.trim())).toEqual([
      'MRF.NS',
      'ITC.NS',
      'Parag Parikh Flexi Cap Fund - Direct Plan - Growth',
    ]);
    const [mrf, itc, fund] = rows();
    expect(mrf.querySelector('[data-testid="watch-price"]')?.textContent).toContain('123,525.00');
    expect(mrf.querySelector('[data-testid="watch-change"]')?.textContent?.trim()).toBe('-1.40%');
    expect(mrf.classList).toContain('down');
    expect(itc.querySelector('[data-testid="watch-change"]')?.textContent?.trim()).toBe('+0.85%');
    expect(itc.classList).toContain('up');
    expect(fund.querySelector('[data-testid="watch-price"]')?.textContent).toContain('88.7620');
    expect(fund.textContent).toContain('NAV');
  });

  it('marks a price that could not be refreshed as delayed', async () => {
    await render(['MRF.NS', 'ITC.NS']);

    expect(rows()[0].querySelector('[data-testid="watch-stale"]')).not.toBeNull();
    expect(rows()[1].querySelector('[data-testid="watch-stale"]')).toBeNull();
  });

  it('leaves out a symbol nobody lists any more', async () => {
    await render(['MRF.NS', 'GONE.NS']);

    expect(rows().map((r) => r.dataset['symbol'])).toEqual(['MRF.NS']);
  });

  it('opens the chart from a row, and the order window from B and S', async () => {
    await render(['ITC.NS']);
    const row = rows()[0];

    expect(row.querySelector('[data-testid="watch-open"]')?.getAttribute('href')).toBe('/instrument/ITC.NS');
    expect(row.querySelector('[data-testid="watch-buy"]')?.getAttribute('href')).toBe('/trade?symbol=ITC.NS&side=BUY');
    expect(row.querySelector('[data-testid="watch-sell"]')?.getAttribute('href')).toBe('/trade?symbol=ITC.NS&side=SELL');
  });

  it('adds what a search picked to the end, and keeps it for the next visit', async () => {
    await render(['MRF.NS']);

    const box = page.querySelector<HTMLInputElement>('[data-testid="instrument-search"]')!;
    box.value = 'itc';
    box.dispatchEvent(new Event('input'));
    await settle();
    http.expectOne((r) => r.url === INSTRUMENTS && r.params.get('q') === 'itc').flush([LISTED[1]]);
    await settle();
    page.querySelector('[data-testid="instrument-search-option"]')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await settle();
    answer();
    await settle();

    expect(rows().map((r) => r.dataset['symbol'])).toEqual(['MRF.NS', 'ITC.NS']);
    const added = http.expectOne(`${WATCHLISTS}/1/items/ITC.NS`);
    expect(added.request.method).toBe('PUT');
    added.flush(null, { status: 204, statusText: 'No Content' });
    expect(page.querySelector('[data-testid="watch-count"]')?.textContent).toContain('2 / 50');
  });

  it('says so when a picked instrument is already there', async () => {
    await render(['ITC.NS']);

    const box = page.querySelector<HTMLInputElement>('[data-testid="instrument-search"]')!;
    box.value = 'itc';
    box.dispatchEvent(new Event('input'));
    await settle();
    http.expectOne((r) => r.url === INSTRUMENTS && r.params.get('q') === 'itc').flush([LISTED[1]]);
    await settle();
    page.querySelector('[data-testid="instrument-search-option"]')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await settle();

    expect(page.querySelector('[data-testid="watch-notice"]')?.textContent).toContain('ITC.NS is already in this watchlist');
    expect(rows()).toHaveLength(1);
  });

  it('removes a row, and remembers that', async () => {
    await render(['MRF.NS', 'ITC.NS']);

    rows()[0].querySelector<HTMLButtonElement>('[data-testid="watch-remove"]')!.click();
    await settle();

    expect(rows().map((r) => r.dataset['symbol'])).toEqual(['ITC.NS']);
    const removed = http.expectOne(`${WATCHLISTS}/1/items/MRF.NS`);
    expect(removed.request.method).toBe('DELETE');
    removed.flush(null, { status: 204, statusText: 'No Content' });
  });

  it("prices a stock from the stream when the watchlist has its latest quote, and asks the quotes route for nothing else", async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
        { provide: MARKET_WATCH_STORAGE, useValue: memory() },
        { provide: SEARCH_DELAY_MS, useValue: 0 },
        { provide: LIVE_PRICES_POLICY, useValue: { intervalMs: 15000, every: () => () => undefined, visible: () => true } },
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MarketWatch);
    page = fixture.nativeElement as HTMLElement;
    await settle();
    http.expectOne(WATCHLISTS).flush([{ id: 1, name: 'Watchlist 1', position: 1, items: [entry('ITC.NS', 1, 271.1), entry('122639', 2)] }]);
    await settle();
    const quotesAsked = http.match((r) => r.url === `${TRADE}/api/v1/quotes`).map((r) => r.request.params.get('symbols'));
    expect(quotesAsked).toEqual(['122639']);
    for (const lookup of http.match((r) => r.url === INSTRUMENTS)) {
      lookup.flush(LISTED);
    }
    await settle();

    const itc = rows().find((r) => r.dataset['symbol'] === 'ITC.NS')!;
    expect(itc.dataset['source']).toBe('stream');
    expect(itc.querySelector('[data-testid="watch-price"]')?.textContent).toContain('271.10');
    expect(itc.querySelector('[data-testid="watch-change"]')?.textContent?.trim()).toBe('+0.40%');
  });

  it('shows each watchlist as a tab, and picking one shows its entries', async () => {
    await render([], [
      { id: 1, name: 'Long term', position: 1, items: [entry('MRF.NS', 1)] },
      { id: 2, name: 'Banks', position: 2, items: [entry('ITC.NS', 1)] },
    ]);

    const tabs = [...page.querySelectorAll<HTMLButtonElement>('[data-testid="watch-tab"]')];
    expect(tabs.map((t) => t.getAttribute('aria-label'))).toEqual(['Long term', 'Banks']);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');

    tabs[1].click();
    await settle();
    answer();
    await settle();

    expect(rows().map((r) => r.dataset['symbol'])).toEqual(['ITC.NS']);
    expect(page.querySelector('[data-testid="watch-name"]')?.textContent).toContain('Banks');
    // The heading is the watchlist itself, nothing more.
    expect(page.querySelector('#watch-title')?.textContent?.trim()).toBe('Banks');
  });
});
