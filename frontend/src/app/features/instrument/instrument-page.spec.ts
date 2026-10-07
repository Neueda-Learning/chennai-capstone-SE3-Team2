import { provideHttpClient } from '@angular/common/http';
import { HttpRequest } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Candle, InstrumentResponse, Quote } from '../../../generated/extensions';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { LIVE_PRICES_POLICY } from '../../core/market/live-prices';
import { MARKET_WATCH_STORAGE } from '../../core/market/market-watch-list';
import { Session } from '../../core/session/session';
import { CHART_LIBRARY, ChartLibrary } from '../../shared/candle-chart/candle-chart';
import { InstrumentPage } from './instrument-page';

const TRADE = 'http://trade.test';
const SBIN: InstrumentResponse = { symbol: 'SBIN.NS', name: 'State Bank of India', type: 'STOCK', exchange: 'NSE', tradable: true };
const FUND: InstrumentResponse = {
  symbol: '122639', name: 'Parag Parikh Flexi Cap Fund - Direct Plan - Growth', type: 'MF', exchange: null, tradable: true,
};
const SBIN_QUOTE: Quote = {
  symbol: 'SBIN.NS', price: 951.85, change: -6.15, changePercent: -0.64, previousClose: 958, bid: 951.7, ask: 952,
  currency: 'INR', stale: false, asOf: '2026-10-06T06:50:49Z',
};
const day = (date: string, open: number, high: number, low: number, close: number, volume: number | null): Candle =>
  ({ date, open, high, low, close, volume });
const SIX_MONTHS: Candle[] = [day('2026-10-05', 960, 965, 950, 958, 9_000_000), day('2026-10-06', 959, 963, 951, 955, null)];
const ONE_YEAR: Candle[] = [day('2025-10-07', 800, 812, 790, 805, 1), ...SIX_MONTHS, day('2026-06-01', 1010, 1021.7, 1000, 1005, 1)];

const lookup = (r: HttpRequest<unknown>) => r.url === `${TRADE}/api/v1/instruments` && r.params.has('symbols');
const quotes = (r: HttpRequest<unknown>) => r.url === `${TRADE}/api/v1/quotes`;
const candles = (symbol: string, range: string) => (r: HttpRequest<unknown>) =>
  r.url === `${TRADE}/api/v1/instruments/${encodeURIComponent(symbol)}/candles` && r.params.get('range') === range;

/** A stored market watch with nothing in it. */
const emptyWatch = (): Storage => {
  const items = new Map([['yellow.market-watch.3', '[]']]);
  return {
    length: 1,
    clear: () => items.clear(),
    getItem: (key: string) => items.get(key) ?? null,
    key: () => null,
    removeItem: (key: string) => void items.delete(key),
    setItem: (key: string, value: string) => void items.set(key, value),
  };
};

const noChart = () =>
  Promise.resolve({
    CandlestickSeries: {},
    HistogramSeries: {},
    createChart: () => ({
      addSeries: () => ({ setData: () => undefined, priceScale: () => ({ applyOptions: () => undefined }) }),
      timeScale: () => ({ fitContent: () => undefined }),
      remove: () => undefined,
    }),
  } as unknown as ChartLibrary);

describe('InstrumentPage', () => {
  let fixture: ComponentFixture<InstrumentPage>;
  let page: HTMLElement;
  let http: HttpTestingController;

  async function settle(): Promise<void> {
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  async function open(symbol: string): Promise<void> {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
        { provide: LIVE_PRICES_POLICY, useValue: { intervalMs: 15000, every: () => () => undefined, visible: () => true } },
        { provide: MARKET_WATCH_STORAGE, useValue: emptyWatch() },
        { provide: CHART_LIBRARY, useValue: noChart },
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InstrumentPage);
    fixture.componentRef.setInput('symbol', symbol);
    page = fixture.nativeElement as HTMLElement;
    await settle();
    // The market watch, on the server: one watchlist, empty.
    http.expectOne(`${TRADE}/api/v1/accounts/3/watchlists`).flush([{ id: 1, name: 'Watchlist 1', position: 1, items: [] }]);
    await settle();
  }

  afterEach(() => http.verify());

  const text = (id: string) => page.querySelector(`[data-testid="${id}"]`)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  /** The figure a stat's label introduces. */
  const stat = (label: string) =>
    [...page.querySelectorAll('[data-testid="instrument-stats"] dt')]
      .find((dt) => dt.textContent?.trim() === label)
      ?.nextElementSibling?.textContent?.trim();

  async function openSbin(): Promise<void> {
    await open('SBIN.NS');
    http.expectOne(lookup).flush([SBIN]);
    http.expectOne(quotes).flush([SBIN_QUOTE]);
    await settle();
    http.expectOne(candles('SBIN.NS', '6M')).flush({ symbol: 'SBIN.NS', range: '6M', candles: SIX_MONTHS });
    http.expectOne(candles('SBIN.NS', '1Y')).flush({ symbol: 'SBIN.NS', range: '1Y', candles: ONE_YEAR });
    await settle();
  }

  it('heads a stock with its ticker, company and exchange, its price and the day change', async () => {
    await openSbin();

    expect(text('instrument-title')).toBe('SBIN.NS');
    expect(page.textContent).toContain('State Bank of India');
    expect(text('instrument-price')).toContain('951.85');
    expect(text('instrument-change')).toBe('-6.15 (-0.64%)');
    expect(page.querySelector('[data-testid="instrument-change"]')?.classList).toContain('down');
  });

  it('shows six months by default, with the day and the 52-week range beneath', async () => {
    await openSbin();

    expect(page.querySelector('[data-testid="candle-chart"]')).not.toBeNull();
    expect(page.querySelector('[data-testid="range-6M"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(stat('Open')).toBe('959.00');
    expect(stat('High')).toBe('963.00');
    expect(stat('Low')).toBe('951.00');
    expect(stat('Prev. close')).toBe('958.00');
    expect(stat('52-week high')).toBe('1,021.70');
    expect(stat('52-week low')).toBe('790.00');
  });

  it('reads another range when one is chosen', async () => {
    await openSbin();

    page.querySelector<HTMLButtonElement>('[data-testid="range-1M"]')!.click();
    await settle();
    http.expectOne(candles('SBIN.NS', '1M')).flush({ symbol: 'SBIN.NS', range: '1M', candles: SIX_MONTHS.slice(1) });
    await settle();

    expect(page.querySelector('[data-testid="range-1M"]')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('links Buy and Sell to the order window, filled in', async () => {
    await openSbin();

    expect(page.querySelector('[data-testid="instrument-buy"]')?.getAttribute('href')).toBe('/trade?symbol=SBIN.NS&side=BUY');
    expect(page.querySelector('[data-testid="instrument-sell"]')?.getAttribute('href')).toBe('/trade?symbol=SBIN.NS&side=SELL');
  });

  it('adds the instrument to the watchlist, and says when it is there', async () => {
    await openSbin();
    const toggle = page.querySelector<HTMLButtonElement>('[data-testid="instrument-watch"]')!;
    expect(toggle.getAttribute('aria-pressed')).toBe('false');

    toggle.click();
    await settle();
    http.expectOne(`${TRADE}/api/v1/accounts/3/watchlists/1/items/SBIN.NS`).flush(null, { status: 204, statusText: 'No Content' });
    await settle();

    expect(toggle.getAttribute('aria-pressed')).toBe('true');
    expect(toggle.textContent).toContain('In watchlist');
  });

  it('sets a price alert on a stock, and says it will tell the customer once', async () => {
    await openSbin();
    page.querySelector<HTMLSelectElement>('[data-testid="alert-direction"]')!.value = 'BELOW';
    page.querySelector<HTMLSelectElement>('[data-testid="alert-direction"]')!.dispatchEvent(new Event('change'));
    const price = page.querySelector<HTMLInputElement>('[data-testid="alert-threshold"]')!;
    price.value = '780';
    price.dispatchEvent(new Event('input'));

    page.querySelector<HTMLFormElement>('[data-testid="alert-form"]')!.dispatchEvent(new Event('submit'));
    await settle();
    const set = http.expectOne(`${TRADE}/api/v1/accounts/3/alerts`);
    expect(set.request.body).toEqual({ symbol: 'SBIN.NS', direction: 'BELOW', threshold: 780 });
    set.flush({ id: 5, symbol: 'SBIN.NS', direction: 'BELOW', threshold: 780, status: 'ACTIVE', createdAt: '2026-10-06T10:00:00Z' });
    await settle();

    expect(text('alert-set')).toContain('you will be told once when SBIN.NS falls to ₹780.00');
  });

  it('sends no alert without a price above zero', async () => {
    await openSbin();

    page.querySelector<HTMLFormElement>('[data-testid="alert-form"]')!.dispatchEvent(new Event('submit'));
    await settle();

    expect(text('alert-invalid')).toContain('Enter a price above zero');
  });

  it('says a price is delayed when it could not be refreshed', async () => {
    await open('SBIN.NS');
    http.expectOne(lookup).flush([SBIN]);
    http.expectOne(quotes).flush([{ ...SBIN_QUOTE, stale: true }]);
    await settle();
    http.expectOne(candles('SBIN.NS', '6M')).flush({ symbol: 'SBIN.NS', range: '6M', candles: [] });
    http.expectOne(candles('SBIN.NS', '1Y')).flush({ symbol: 'SBIN.NS', range: '1Y', candles: [] });
    await settle();

    expect(text('instrument-stale')).toContain('Delayed');
    expect(page.textContent).toContain('No price history');
  });

  it('heads a fund with its name and NAV, and draws no chart and offers no alert', async () => {
    await open('122639');
    http.expectOne(lookup).flush([FUND]);
    http.expectOne(quotes).flush([{ symbol: '122639', price: 88.762, currency: 'INR', stale: false, asOf: '2026-10-04T18:30:00Z' }]);
    await settle();

    expect(text('instrument-title')).toBe('Parag Parikh Flexi Cap Fund - Direct Plan - Growth');
    expect(text('instrument-price')).toContain('88.7620');
    expect(text('instrument-price')).toContain('NAV of 5 Oct 2026');
    expect(page.querySelector('[data-testid="candle-chart"]')).toBeNull();
    expect(page.querySelector('[data-testid="alert-form"]')).toBeNull();
    http.expectNone((r) => r.url.includes('/candles'));
  });

  it('says so when nobody lists the symbol', async () => {
    await open('NOPE.NS');
    http.expectOne(lookup).flush([]);
    http.match(quotes).forEach((r) => r.flush([]));
    await settle();

    expect(page.textContent).toContain("We couldn't find that instrument");
    http.expectNone((r) => r.url.includes('/candles'));
  });

  it('says so when the chart cannot be read, and still shows the price', async () => {
    await open('SBIN.NS');
    http.expectOne(lookup).flush([SBIN]);
    http.expectOne(quotes).flush([SBIN_QUOTE]);
    await settle();
    http.expectOne(candles('SBIN.NS', '6M')).flush({ errorCode: 'MKT-503', message: 'x' }, { status: 503, statusText: 'Unavailable' });
    http.expectOne(candles('SBIN.NS', '1Y')).flush({ errorCode: 'MKT-503', message: 'x' }, { status: 503, statusText: 'Unavailable' });
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain("Prices can't be fetched right now");
    expect(text('instrument-price')).toContain('951.85');
  });
});
