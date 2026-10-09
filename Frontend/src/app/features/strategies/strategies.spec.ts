import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { Session } from '../../core/session/session';
import { InstrumentSearch } from '../../shared/instrument-search/instrument-search';
import { STRATEGIES_POLL_MS, Strategies } from './strategies';

const STRATEGIES = 'http://trade.test/api/v1/accounts/3/strategies';
const ARMED = {
  id: 7, symbol: 'ITC.NS', side: 'BUY', quantity: 2, trigger: 'FALLS_THROUGH', triggerPrice: 250, maxSpend: 600,
  maxPosition: 20, enabled: true, status: 'ARMED', failures: 0, createdAt: '2026-10-07T04:00:00Z', lastFiredAt: null,
};
const OFF = { ...ARMED, id: 6, enabled: false };
const FIRED = { ...ARMED, id: 5, symbol: 'SBIN.NS', side: 'SELL', quantity: 5, trigger: 'RISES_THROUGH', triggerPrice: 812.5,
  status: 'FIRED', lastFiredAt: '2026-10-07T04:35:00Z' };
const STOPPED = { ...ARMED, id: 4, status: 'STOPPED', failures: 3 };
/** The indicator triggers: figures from the last quote seen, or none yet. */
const FIGURES = { price: 259.5, asOf: '2026-10-07T04:00:00Z', days: 80, shortAverage: 255.5, longAverage: 256, lowerBand: 240, upperBand: 270 };
const CROSSOVER = { ...ARMED, id: 9, trigger: 'MA_CROSSOVER', triggerPrice: null, indicator: FIGURES };
const BAND_SELL = { ...ARMED, id: 10, side: 'SELL', quantity: 5, trigger: 'BOLLINGER', triggerPrice: null, indicator: FIGURES };
const BAND_WAITING = { ...ARMED, id: 11, trigger: 'BOLLINGER', triggerPrice: null, indicator: null };
const ITC = { symbol: 'ITC.NS', name: 'ITC Ltd', type: 'STOCK', exchange: 'NSE' };

describe('Strategies', () => {
  let fixture: ComponentFixture<Strategies>;
  let page: HTMLElement;
  let http: HttpTestingController;

  async function settle(): Promise<void> {
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  async function render(answer: object[]): Promise<void> {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' }),
        { provide: STRATEGIES_POLL_MS, useValue: 3_600_000 },
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Strategies);
    page = fixture.nativeElement as HTMLElement;
    await settle();
    http.expectOne(STRATEGIES).flush(answer);
    await settle();
  }

  afterEach(() => http.verify());

  const texts = (id: string) => [...page.querySelectorAll(`[data-testid="${id}"]`)].map((e) => e.textContent?.replace(/\s+/g, ' ').trim());
  const click = (id: string, index = 0) => page.querySelectorAll<HTMLButtonElement>(`[data-testid="${id}"]`)[index].click();

  function type(id: string, value: string): void {
    const input = page.querySelector<HTMLInputElement | HTMLSelectElement>(`[data-testid="${id}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  function pick(instrument: object): void {
    fixture.debugElement.query(By.directive(InstrumentSearch)).componentInstance.picked.emit(instrument);
    fixture.detectChanges();
  }

  it('shows each strategy as a rule, with its bounds and its state', async () => {
    await render([ARMED, OFF, FIRED, STOPPED]);

    expect(texts('strategy-rule')).toEqual([
      'Buy 2 when the price falls to ₹250.00',
      'Buy 2 when the price falls to ₹250.00',
      'Sell 5 when the price rises to ₹812.50',
      'Buy 2 when the price falls to ₹250.00',
    ]);
    expect(texts('strategy-bounds')).toEqual([
      'At most ₹600.00 a firing, and 20 held',
      'At most ₹600.00 a firing, and 20 held',
      'At most ₹600.00 a firing, and 20 held',
    ]);
    expect(texts('strategy-status')).toEqual(['Armed', 'Off', 'Fired 7 Oct, 10:05', 'Stopped after three failures']);
    // Off: on. Armed: off. Fired or stopped: armed again, its failures forgotten.
    expect(texts('strategy-toggle')).toEqual(['Switch off', 'Switch on', 'Arm again', 'Arm again']);
  });

  it('shows an indicator strategy as a rule in words, with the figures it waits on', async () => {
    await render([CROSSOVER, BAND_SELL, BAND_WAITING]);

    expect(texts('strategy-rule')).toEqual([
      'Buy 2 when the 20-day average crosses above the 50-day',
      'Sell 5 when the price reaches the upper Bollinger band',
      'Buy 2 when the price reaches the lower Bollinger band',
    ]);
    expect(texts('strategy-indicator')).toEqual([
      '20-day ₹255.50, 50-day ₹256.00; price ₹259.50',
      'Band ₹240.00 to ₹270.00; price ₹259.50',
      'Waiting for the next price',
    ]);
  });

  it('a level strategy has no indicator figures', async () => {
    await render([ARMED]);

    expect(page.querySelector('[data-testid="strategy-indicator"]')).toBeNull();
  });

  it('says there are none yet', async () => {
    await render([]);

    expect(texts('strategies-empty')[0]).toContain('No strategies yet');
  });

  it('switches one on, and reads the list again', async () => {
    await render([OFF]);

    click('strategy-toggle');
    await settle();
    const put = http.expectOne(`${STRATEGIES}/6/enabled`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ enabled: true });
    put.flush({ ...OFF, enabled: true });
    await settle();
    http.expectOne(STRATEGIES).flush([{ ...OFF, enabled: true }]);
    await settle();

    expect(texts('strategy-status')).toEqual(['Armed']);
  });

  it('creates one from the form, off until it is switched on', async () => {
    await render([]);

    pick(ITC);
    type('strategy-side', 'BUY');
    type('strategy-quantity', '2');
    type('strategy-trigger', 'FALLS_THROUGH');
    type('strategy-price', '250');
    type('strategy-max-spend', '600');
    type('strategy-max-position', '20');
    page.querySelector<HTMLButtonElement>('[data-testid="strategy-submit"]')!.click();
    await settle();

    const post = http.expectOne(STRATEGIES);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({
      symbol: 'ITC.NS', side: 'BUY', quantity: 2, trigger: 'FALLS_THROUGH', triggerPrice: 250, maxSpend: 600, maxPosition: 20,
    });
    post.flush({ ...OFF, id: 8 });
    await settle();
    http.expectOne(STRATEGIES).flush([{ ...OFF, id: 8 }]);
    await settle();

    expect(texts('strategy-created')[0]).toContain('Created, switched off');
    expect(texts('strategy-status')).toEqual(['Off']);
  });

  it('creates a crossover with no price: an indicator fires on the averages, so no price is asked for', async () => {
    await render([]);

    pick(ITC);
    type('strategy-trigger', 'MA_CROSSOVER');
    await settle();
    expect(page.querySelector('[data-testid="strategy-price"]')).toBeNull();
    type('strategy-quantity', '2');
    type('strategy-max-spend', '600');
    type('strategy-max-position', '20');
    page.querySelector<HTMLButtonElement>('[data-testid="strategy-submit"]')!.click();
    await settle();

    const post = http.expectOne(STRATEGIES);
    expect(post.request.body).toEqual({
      symbol: 'ITC.NS', side: 'BUY', quantity: 2, trigger: 'MA_CROSSOVER', maxSpend: 600, maxPosition: 20,
    });
    post.flush({ ...CROSSOVER, enabled: false, indicator: null });
    await settle();
    http.expectOne(STRATEGIES).flush([{ ...CROSSOVER, enabled: false, indicator: null }]);
    await settle();

    expect(texts('strategy-created')[0]).toContain('Created, switched off');
  });

  it('sends nothing for a form that is not complete, and says what to fix', async () => {
    await render([]);

    type('strategy-quantity', '0');
    type('strategy-price', '250.005');
    page.querySelector<HTMLButtonElement>('[data-testid="strategy-submit"]')!.click();
    await settle();

    expect(texts('strategy-invalid')).toEqual([
      'Pick a stock. Enter a whole quantity of 1 or more. Enter a price above zero, to the paisa. '
        + 'Enter the most a firing may spend. Enter the most you would hold, 1 or more.',
    ]);
  });

  it('refuses a fund before anything is sent: a fund has no live price to watch', async () => {
    await render([]);

    pick({ symbol: '122639', name: 'Parag Parikh Flexi Cap', type: 'MF' });

    expect(texts('strategy-fund')[0]).toContain('A fund has no live price');
  });

  it('says why when the eleventh is refused', async () => {
    await render([]);

    pick(ITC);
    type('strategy-quantity', '2');
    type('strategy-price', '250');
    type('strategy-max-spend', '600');
    type('strategy-max-position', '20');
    page.querySelector<HTMLButtonElement>('[data-testid="strategy-submit"]')!.click();
    await settle();
    http.expectOne(STRATEGIES).flush({ errorCode: 'LIM-409', message: 'x' }, { status: 409, statusText: 'Conflict' });
    await settle();

    expect(texts('error-message')).toEqual(['An account can have 10 strategies. Delete one to add another.']);
  });

  it('shows what a strategy did, on request, newest first', async () => {
    await render([FIRED]);

    click('strategy-runs');
    await settle();
    http.expectOne(`${STRATEGIES}/5/runs`).flush([
      { id: 3, at: '2026-10-07T04:36:10Z', quotePrice: 813.4, outcome: 'FILLED', reason: null },
      { id: 2, at: '2026-10-07T04:35:00Z', quotePrice: 812.9, outcome: 'PLACED', reason: null },
      { id: 1, at: '2026-10-07T04:30:00Z', quotePrice: 812.6, outcome: 'FAILED', reason: 'ORD-409: Insufficient holdings' },
    ]);
    await settle();

    expect(texts('run')).toEqual([
      '7 Oct, 10:06:10 Filled at ₹813.40',
      '7 Oct, 10:05:00 Order placed at ₹812.90',
      '7 Oct, 10:00:00 Not placed at ₹812.60 ORD-409: Insufficient holdings',
    ]);
  });

  it('deletes one, and reads the list again', async () => {
    await render([ARMED]);

    click('strategy-delete');
    await settle();
    const deletion = http.expectOne(`${STRATEGIES}/7`);
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    http.expectOne(STRATEGIES).flush([]);
    await settle();

    expect(page.querySelectorAll('[data-testid="strategy"]').length).toBe(0);
  });
});
