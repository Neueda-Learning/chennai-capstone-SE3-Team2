import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { InstrumentResponse, Quote } from '../../../generated/extensions';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { LIVE_PRICES_POLICY } from '../../core/market/live-prices';
import { Session } from '../../core/session/session';
import { Holdings } from './holdings';

const TRADE = 'http://trade.test';
const LISTED: InstrumentResponse[] = [
  { symbol: 'SBIN.NS', name: 'State Bank of India', type: 'STOCK', exchange: 'NSE', tradable: true },
  { symbol: '122639', name: 'Parag Parikh Flexi Cap Fund - Direct Plan - Growth', type: 'MF', exchange: null, tradable: true },
  { symbol: 'MERSTL', name: 'Meridian Steel Ltd', type: 'STOCK', exchange: 'NSE', tradable: false },
];
const QUOTES: Quote[] = [
  { symbol: 'SBIN.NS', price: 950, change: -5, changePercent: -0.52, currency: 'INR', stale: false },
  { symbol: '122639', price: 88.762, currency: 'INR', stale: false },
  { symbol: 'MERSTL', price: null, currency: 'INR', stale: true },
];
/** As GET /api/v1/portfolio/3/positions prices them: MERSTL could not be priced. */
const PRICED = [
  { accountId: 3, symbol: 'SBIN.NS', quantity: 10, averageCost: 900, costBasis: 9000, lastPrice: 950, marketValue: 9500,
    unrealisedPnl: 500, unrealisedPnlPercent: 5.56, currency: 'INR', priceAsOf: '2026-10-07T04:00:00Z', stale: false },
  { accountId: 3, symbol: '122639', quantity: 56, averageCost: 88, costBasis: 4928, lastPrice: 88.762, marketValue: 4970.67,
    unrealisedPnl: 42.67, unrealisedPnlPercent: 0.87, currency: 'INR', priceAsOf: '2026-10-06T18:30:00Z', stale: false },
  { accountId: 3, symbol: 'MERSTL', quantity: 500, averageCost: 84.3, costBasis: 42150, lastPrice: null, marketValue: null,
    unrealisedPnl: null, unrealisedPnlPercent: null, currency: 'INR', priceAsOf: null, stale: true },
];
const SUMMARY = {
  accountId: 3, baseCurrency: 'INR', cashBalance: 750000, marketValue: 14470.67, costBasis: 13928, unrealisedPnl: 542.67,
  unrealisedPnlPercent: 3.9, realisedPnl: 1145.5, totalValue: 764470.67, positionCount: 3, partial: true, asOf: '2026-10-07T04:00:00Z',
};
const POSITIONS = [
  { accountId: 3, symbol: 'SBIN.NS', quantity: 10, averageCost: 900 },
  { accountId: 3, symbol: 'MERSTL', quantity: 500, averageCost: 84.3 },
];

describe('Holdings', () => {
  let fixture: ComponentFixture<Holdings>;
  let page: HTMLElement;
  let http: HttpTestingController;

  async function settle(): Promise<void> {
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  async function render(priced: unknown[] | 'unavailable' = PRICED, summary: object = SUMMARY): Promise<void> {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
        { provide: LIVE_PRICES_POLICY, useValue: { intervalMs: 15000, every: () => () => undefined, visible: () => true } },
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Holdings);
    page = fixture.nativeElement as HTMLElement;
    await settle();
    if (priced === 'unavailable') {
      const unavailable = { errorCode: 'MKT-503', message: 'Pricing unavailable' };
      http.expectOne(`${TRADE}/api/v1/portfolio/3/positions`).flush(unavailable, { status: 503, statusText: 'Unavailable' });
      http.expectOne(`${TRADE}/api/v1/portfolio/3`).flush(unavailable, { status: 503, statusText: 'Unavailable' });
      await settle();
      http.expectOne(`${TRADE}/api/v1/accounts/3/positions`).flush(POSITIONS);
    } else {
      http.expectOne(`${TRADE}/api/v1/portfolio/3/positions`).flush(priced);
      http.expectOne(`${TRADE}/api/v1/portfolio/3`).flush(summary);
    }
    await settle();
    for (const lookup of http.match((r) => r.url === `${TRADE}/api/v1/instruments`)) {
      const symbols = lookup.request.params.get('symbols')!.split(',');
      lookup.flush(LISTED.filter((i) => symbols.includes(i.symbol)));
    }
    for (const read of http.match((r) => r.url === `${TRADE}/api/v1/quotes`)) {
      const symbols = read.request.params.get('symbols')!.split(',');
      read.flush(QUOTES.filter((q) => symbols.includes(q.symbol)));
    }
    await settle();
  }

  afterEach(() => http.verify());

  const text = (id: string) => page.querySelector(`[data-testid="${id}"]`)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const row = (symbol: string) => page.querySelector<HTMLElement>(`[data-testid="holdings-row"][data-symbol="${symbol}"]`)!;
  const cells = (symbol: string) => [...row(symbol).querySelectorAll('td')].map((td) => td.textContent?.replace(/\s+/g, ' ').trim());

  it('shows each holding as the portfolio module priced it: LTP, value, P&L; the day change from the quote; a fund by name', async () => {
    await render();

    const [name, quantity, cost, ltp, current, pnl, net, day] = cells('SBIN.NS');
    expect([name, quantity, cost, ltp, current, pnl, net, day]).toEqual([
      'SBIN.NS', '10', '900.00', '950.00', '9,500.00', '+500.00', '+5.56%', '-50.00',
    ]);
    expect(row('SBIN.NS').querySelector('[data-testid="holdings-row-pnl"]')?.classList).toContain('up');
    expect(cells('122639')[0]).toBe('Parag Parikh Flexi Cap Fund - Direct Plan - Growth');
    expect(cells('122639')[3]).toBe('88.7620');
    // A NAV comes without a day change.
    expect(cells('122639')[7]).toBe('—');
  });

  it('totals what is priced, and says the totals leave out what is not', async () => {
    await render();

    // 9,000 + 4,928 + 42,150 invested; 9,500 + 4,970.67 priced.
    expect(text('holdings-invested')).toContain('56,078.00');
    expect(text('holdings-current')).toContain('14,470.67');
    expect(text('holdings-pnl')).toContain('+₹542.67');
    expect(text('holdings-day')).toContain('-₹50.00');
    expect(text('holdings-partial')).toContain('Some holdings have no price right now');
    expect(cells('MERSTL')[3]).toBe('—');
    // No price at all is not a delayed one: no dot.
    expect(row('MERSTL').querySelector('.stale')).toBeNull();
  });

  it('opens the instrument from its name, and buys more or sells from the row; a delisted one cannot trade', async () => {
    await render();

    expect(row('SBIN.NS').querySelector('td.instrument a')?.getAttribute('href')).toBe('/instrument/SBIN.NS');
    expect(row('SBIN.NS').querySelector('[data-testid="holdings-sell"]')?.getAttribute('href')).toBe('/trade?symbol=SBIN.NS&side=SELL');
    expect(row('SBIN.NS').querySelector('[data-testid="holdings-buy"]')?.getAttribute('href')).toBe('/trade?symbol=SBIN.NS&side=BUY');
    expect(row('MERSTL').querySelector('[data-testid="holdings-sell"]')).toBeNull();
  });

  it('shows only stocks, or only funds, when asked', async () => {
    await render();

    page.querySelector<HTMLButtonElement>('[data-testid="holdings-filter-mutual-funds"]')!.click();
    await settle();
    expect([...page.querySelectorAll<HTMLElement>('[data-testid="holdings-row"]')].map((r) => r.dataset['symbol'])).toEqual(['122639']);
    expect(text('holdings-invested')).toContain('4,928.00');

    page.querySelector<HTMLButtonElement>('[data-testid="holdings-filter-stocks"]')!.click();
    await settle();
    expect([...page.querySelectorAll<HTMLElement>('[data-testid="holdings-row"]')].map((r) => r.dataset['symbol'])).toEqual([
      'SBIN.NS',
      'MERSTL',
    ]);
  });

  it('shows the P&L already realised by sales, as the portfolio module booked it', async () => {
    await render();

    expect(text('holdings-realised')).toContain('+₹1,145.50');
  });

  it('when nothing can be priced (MKT-503), still shows every holding, at cost and unpriced, and says why', async () => {
    await render('unavailable');

    expect([...page.querySelectorAll<HTMLElement>('[data-testid="holdings-row"]')].map((r) => r.dataset['symbol'])).toEqual([
      'SBIN.NS',
      'MERSTL',
    ]);
    expect(text('holdings-invested')).toContain('51,150.00');
    expect(cells('SBIN.NS')[3]).toBe('—');
    expect(text('holdings-unpriced')).toContain("Prices can't be fetched right now");
    expect(page.querySelector('[data-testid="holdings-partial"]')).toBeNull();
  });

  it('says so when the account holds nothing', async () => {
    await render([], { ...SUMMARY, marketValue: 0, costBasis: 0, unrealisedPnl: 0, positionCount: 0, partial: false });

    expect(page.querySelector('[data-testid="holdings-empty"]')).not.toBeNull();
  });
});
