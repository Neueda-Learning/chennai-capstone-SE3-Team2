import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { Session } from '../../core/session/session';
import { AdviceSignal } from './advice-signal';

const ADVICE = 'http://trade.test/api/v1/advice/ITC.NS';
const BUY = {
  symbol: 'ITC.NS', direction: 'BUY', strength: 81, methodology: '20/50-day moving average crossover, confirmed by RSI(14)',
  reason: 'The 20-day average is 4.9% above the 50-day and RSI is 60, so the trend is up.',
  figures: { sma20: 127.8953, sma50: 121.9153, rsi14: 60.41, lastPrice: 266.7, priceAsOf: '2026-10-07T03:59:58Z', days: 81 },
  computedAt: '2026-10-07T04:00:00Z',
  disclaimer: 'Information, not advice. Computed from delayed educational data; past prices do not predict future ones.',
};

describe('AdviceSignal', () => {
  let fixture: ComponentFixture<AdviceSignal>;
  let page: HTMLElement;
  let http: HttpTestingController;

  async function settle(): Promise<void> {
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' })],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdviceSignal);
    fixture.componentRef.setInput('symbol', 'ITC.NS');
    page = fixture.nativeElement as HTMLElement;
    await settle();
  });

  afterEach(() => http.verify());

  const text = (id: string) => page.querySelector(`[data-testid="${id}"]`)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

  it('asks for nothing until the customer asks, and says what it is not, from the start', () => {
    http.expectNone(ADVICE);
    expect(text('advice-disclaimer')).toContain('Information, not advice');
  });

  it('reads the signal when asked: the direction, its strength, the sentence and the figures', async () => {
    page.querySelector<HTMLButtonElement>('[data-testid="advice-read"]')!.click();
    await settle();
    http.expectOne(ADVICE).flush(BUY);
    await settle();

    expect(text('advice-direction')).toBe('BUY');
    expect(text('advice-strength')).toBe('strength 81 of 100');
    expect(text('advice-reason')).toContain('so the trend is up');
    expect(page.textContent).toContain('127.90');
    expect(text('advice-disclaimer')).toContain('Information, not advice');
  });

  it('a HOLD shows no strength', async () => {
    page.querySelector<HTMLButtonElement>('[data-testid="advice-read"]')!.click();
    await settle();
    http.expectOne(ADVICE).flush({ ...BUY, direction: 'HOLD', strength: 0, reason: 'Only 32 days of prices: a 50-day average needs 50, so there is no signal yet.' });
    await settle();

    expect(text('advice-direction')).toBe('HOLD');
    expect(page.querySelector('[data-testid="advice-strength"]')).toBeNull();
  });

  it('says why when there is no signal to be had', async () => {
    page.querySelector<HTMLButtonElement>('[data-testid="advice-read"]')!.click();
    await settle();
    http.expectOne(ADVICE).flush({ errorCode: 'MKT-503', message: 'Pricing unavailable' }, { status: 503, statusText: 'Unavailable' });
    await settle();

    expect(page.textContent).toContain("Prices can't be fetched right now");
  });
});
