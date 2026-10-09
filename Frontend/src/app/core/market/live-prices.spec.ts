import { provideHttpClient } from '@angular/common/http';
import { HttpRequest } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Quote } from '../../../generated/extensions';
import { provideClients } from '../api/provide-clients';
import { LIVE_PRICES_POLICY, LivePrices } from './live-prices';

const TRADE = 'http://trade.test';
const quotesFor = (symbols: string) => (request: HttpRequest<unknown>) =>
  request.url === `${TRADE}/api/v1/quotes` && request.params.get('symbols') === symbols;
const quote = (symbol: string, price: number): Quote => ({ symbol, price, currency: 'INR', stale: false, asOf: '2026-10-06T05:00:00Z' });

describe('LivePrices', () => {
  let prices: LivePrices;
  let http: HttpTestingController;
  let ticks: Array<() => void>;
  let visibility: DocumentVisibilityState;

  beforeEach(() => {
    ticks = [];
    visibility = 'visible';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
        {
          provide: LIVE_PRICES_POLICY,
          useValue: {
            intervalMs: 15000,
            every: (task: () => void) => {
              ticks.push(task);
              return () => ticks.splice(ticks.indexOf(task), 1);
            },
            visible: () => visibility === 'visible',
          },
        },
      ],
    });
    prices = TestBed.inject(LivePrices);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
  }

  it('prices what a screen watches at once, and keeps it for every screen to read', async () => {
    prices.watch(['MRF.NS', '122639']);
    http.expectOne(quotesFor('MRF.NS,122639')).flush([quote('MRF.NS', 123525), quote('122639', 88.76)]);
    await settle();

    expect(prices.quote('MRF.NS')?.price).toBe(123525);
    expect(prices.quote('122639')?.price).toBe(88.76);
  });

  it('asks only for symbols it has no price for yet', async () => {
    prices.watch(['MRF.NS']);
    http.expectOne(quotesFor('MRF.NS')).flush([quote('MRF.NS', 1)]);
    await settle();

    prices.watch(['MRF.NS', 'ITC.NS']);
    http.expectOne(quotesFor('ITC.NS')).flush([quote('ITC.NS', 2)]);
    await settle();
  });

  it('does not ask again for a price already on its way', async () => {
    prices.watch(['MRF.NS', 'ITC.NS']);
    prices.watch(['MRF.NS']);

    http.expectOne(quotesFor('MRF.NS,ITC.NS')).flush([quote('MRF.NS', 1), quote('ITC.NS', 2)]);
    await settle();
  });

  it('re-reads everything watched on each tick, in one request', async () => {
    prices.watch(['MRF.NS']);
    prices.watch(['ITC.NS']);
    http.expectOne(quotesFor('MRF.NS')).flush([quote('MRF.NS', 1)]);
    http.expectOne(quotesFor('ITC.NS')).flush([quote('ITC.NS', 2)]);
    await settle();
    expect(ticks).toHaveLength(1);

    ticks[0]();
    http.expectOne(quotesFor('MRF.NS,ITC.NS')).flush([quote('MRF.NS', 3), quote('ITC.NS', 4)]);
    await settle();
    expect(prices.quote('MRF.NS')?.price).toBe(3);
  });

  it('asks 50 at a time, the most one request takes', async () => {
    const symbols = Array.from({ length: 60 }, (_, i) => `S${i}.NS`);
    prices.watch(symbols);

    http.expectOne(quotesFor(symbols.slice(0, 50).join(','))).flush([]);
    http.expectOne(quotesFor(symbols.slice(50).join(','))).flush([]);
  });

  it('does not re-read while the tab is hidden', async () => {
    prices.watch(['MRF.NS']);
    http.expectOne(quotesFor('MRF.NS')).flush([quote('MRF.NS', 1)]);
    await settle();

    visibility = 'hidden';
    ticks[0]();
    http.expectNone(quotesFor('MRF.NS'));
  });

  it('stops ticking once nothing is watched', async () => {
    const stop = prices.watch(['MRF.NS']);
    http.expectOne(quotesFor('MRF.NS')).flush([quote('MRF.NS', 1)]);
    await settle();

    stop();
    expect(ticks).toHaveLength(0);
  });

  it('keeps the last prices when a re-read fails, and says why', async () => {
    prices.watch(['MRF.NS']);
    http.expectOne(quotesFor('MRF.NS')).flush([quote('MRF.NS', 1)]);
    await settle();

    ticks[0]();
    http.expectOne(quotesFor('MRF.NS')).flush({ errorCode: 'MKT-503', message: 'x' }, { status: 503, statusText: 'Unavailable' });
    await settle();

    expect(prices.quote('MRF.NS')?.price).toBe(1);
    expect(prices.error()).not.toBeNull();
  });
});
