import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { Session } from '../../core/session/session';
import { Signals } from './signals';

const ADVICE = 'http://trade.test/api/v1/accounts/3/advice';
const INSTRUMENTS = 'http://trade.test/api/v1/instruments';
const DISCLAIMER = 'Information, not advice. Computed from delayed educational data; past prices do not predict future ones.';
const METHOD = '20/50-day moving average crossover, confirmed by RSI(14)';
const figures = { sma20: 102, sma50: 100, rsi14: 61, lastPrice: 103, priceAsOf: '2026-10-07T04:00:00Z', days: 80 };
const signal = (symbol: string, direction: string | null, strength: number | null, reason: string, withFigures = true) => ({
  symbol, direction, strength, methodology: METHOD, reason, figures: withFigures ? figures : null,
  computedAt: '2026-10-07T04:00:00Z', disclaimer: DISCLAIMER,
});
const ITEMS = [
  { symbol: 'ITC.NS', held: true, watched: true,
    signal: signal('ITC.NS', 'BUY', 64, 'The 20-day average is 2.0% above the 50-day and RSI is 61, so the trend is up.') },
  { symbol: 'SBIN.NS', held: false, watched: true,
    signal: signal('SBIN.NS', 'SELL', 48, 'The 20-day average is 1.6% below the 50-day and RSI is 43, so the trend is down.') },
  { symbol: 'TCS.NS', held: true, watched: false,
    signal: signal('TCS.NS', 'HOLD', 0, 'The 20-day and 50-day averages are level, within 0.1%, so there is no trend to follow.') },
  { symbol: '122639', held: true, watched: false,
    signal: signal('122639', null, null, 'A fund is priced once a day at its NAV, and this method needs daily candles, so there is no signal.', false) },
];

describe('Signals', () => {
  let fixture: ComponentFixture<Signals>;
  let page: HTMLElement;
  let http: HttpTestingController;

  async function settle(): Promise<void> {
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  async function render(answer: object | 'error'): Promise<void> {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' })],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Signals);
    page = fixture.nativeElement as HTMLElement;
    await settle();
    const request = http.expectOne(ADVICE);
    if (answer === 'error') {
      request.flush({ errorCode: 'MKT-503', message: 'x' }, { status: 503, statusText: 'Service Unavailable' });
    } else {
      request.flush(answer);
    }
    await settle();
    // A fund is shown by its name, looked up once.
    for (const lookup of http.match((r) => r.url === INSTRUMENTS)) {
      lookup.flush([{ symbol: '122639', name: 'Parag Parikh Flexi Cap Fund', type: 'MF', exchange: null, tradable: true }]);
    }
    await settle();
  }

  afterEach(() => http.verify());

  const all = (id: string) => [...page.querySelectorAll(`[data-testid="${id}"]`)].map((e) => e.textContent?.replace(/\s+/g, ' ').trim());

  it('lists every stock held or watched with its view, how strong, and the reason behind it', async () => {
    await render({ accountId: 3, items: ITEMS, truncated: false, disclaimer: DISCLAIMER });

    expect(all('signal-name')).toEqual(['ITC.NS', 'SBIN.NS', 'TCS.NS', 'Parag Parikh Flexi Cap Fund']);
    expect(all('signal-direction')).toEqual(['BUY', 'SELL', 'HOLD', 'No signal']);
    expect(all('signal-strength')).toEqual(['64 of 100', '48 of 100']);
    expect(all('signal-reason')[0]).toBe('The 20-day average is 2.0% above the 50-day and RSI is 61, so the trend is up.');
    expect(all('signal-from')).toEqual(['Held · Watched', 'Watched', 'Held', 'Held']);
  });

  it('a stock with no signal says why, rather than showing a view it does not have', async () => {
    await render({ accountId: 3, items: ITEMS, truncated: false, disclaimer: DISCLAIMER });

    const fund = page.querySelector('[data-testid="signal-row"][data-symbol="122639"]')!;
    expect(fund.getAttribute('data-direction')).toBe('NONE');
    expect(fund.querySelector('[data-testid="signal-reason"]')?.textContent).toContain('so there is no signal');
  });

  it('says it is information, not advice, and which method produced every view', async () => {
    await render({ accountId: 3, items: ITEMS, truncated: false, disclaimer: DISCLAIMER });

    expect(all('signals-disclaimer')[0]).toBe(DISCLAIMER);
    expect(all('signals-method')[0]).toContain(METHOD);
  });

  it('says when the list stops at 30', async () => {
    await render({ accountId: 3, items: ITEMS, truncated: true, disclaimer: DISCLAIMER });

    expect(all('signals-truncated')[0]).toContain('first 30');
  });

  it('says so when nothing is held or watched yet', async () => {
    await render({ accountId: 3, items: [], truncated: false, disclaimer: DISCLAIMER });

    expect(all('signals-empty')[0]).toContain('Nothing held or watched yet');
  });

  it('says what went wrong when the signals cannot be read', async () => {
    await render('error');

    expect(page.querySelector('[role="alert"]')?.textContent).toContain("Prices can't be fetched right now");
  });
});
