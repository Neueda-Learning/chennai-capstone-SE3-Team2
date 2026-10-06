import { provideHttpClient } from '@angular/common/http';
import { HttpRequest } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { InstrumentResponse, Quote } from '../../../generated/extensions';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { ERROR_MESSAGES, UNREACHABLE_MESSAGE } from '../../core/errors/error-messages';
import { LIVE_PRICES_POLICY } from '../../core/market/live-prices';
import { Session } from '../../core/session/session';
import { SEARCH_DELAY_MS } from '../../shared/instrument-search/instrument-search';
import { OrderTicket } from './order-ticket';

const TRADE = 'http://trade.test';
const ORDERS = `${TRADE}/api/v1/orders`;
const INSTRUMENTS_URL = `${TRADE}/api/v1/instruments`;
const INSTRUMENTS: InstrumentResponse[] = [
  { symbol: 'INFY.NS', name: 'Infosys Ltd', type: 'STOCK', exchange: 'NSE', tradable: true },
  { symbol: 'TCS.NS', name: 'Tata Consultancy Services Ltd', type: 'STOCK', exchange: 'NSE', tradable: true },
  { symbol: 'NIFTYBEES', name: 'Nifty 50 ETF', type: 'ETF', exchange: 'NSE', tradable: true },
  { symbol: '120503', name: 'Bluechip Equity Fund', type: 'MF', exchange: null, tradable: true },
];
const QUOTES: Record<string, Quote> = {
  'INFY.NS': { symbol: 'INFY.NS', price: 1450, bid: 1449.5, ask: 1450.5, change: 7, changePercent: 0.48, currency: 'INR', stale: false },
  'TCS.NS': { symbol: 'TCS.NS', price: 3500, bid: 3499, ask: 3501, change: -1, changePercent: -0.03, currency: 'INR', stale: false },
  NIFTYBEES: { symbol: 'NIFTYBEES', price: 250, bid: 249.9, ask: 250.1, currency: 'INR', stale: false },
  '120503': { symbol: '120503', price: 50.1234, currency: 'INR', stale: false, asOf: '2026-10-04T18:30:00Z' },
};

describe('OrderTicket', () => {
  let fixture: ComponentFixture<OrderTicket>;
  let page: HTMLElement;
  let http: HttpTestingController;

  /** The cash on the account, and what it holds: 20 INFY.NS. */
  async function render(cashBalance = 100000): Promise<void> {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
        { provide: SEARCH_DELAY_MS, useValue: 0 },
        { provide: LIVE_PRICES_POLICY, useValue: { intervalMs: 15000, every: () => () => undefined, visible: () => true } },
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrderTicket);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    // Who is signed in, as the customer knows the account, and its cash.
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush(account(cashBalance));
    http.expectOne(`${TRADE}/api/v1/accounts/3/positions`).flush([{ accountId: 3, symbol: 'INFY.NS', quantity: 20, averageCost: 1400 }]);
    await settle();
  }

  const account = (cashBalance: number) => ({
    id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance, status: 'ACTIVE', version: 1, lastUpdated: '2026-10-05T00:00:00Z',
  });

  afterEach(() => {
    // The account and holdings re-read after an order are not what these tests are about.
    http
      .match((r) => r.url.startsWith(`${TRADE}/api/v1/accounts/3`))
      .forEach((r) => r.flush(r.request.url.endsWith('positions') ? [] : account(1)));
    http.verify();
  });

  const field = (id: string) => page.querySelector<HTMLInputElement & HTMLButtonElement>(`[data-testid="${id}"]`)!;
  const text = (id: string) => page.querySelector(`[data-testid="${id}"], #${id}`)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const quotesRead = (r: HttpRequest<unknown>) => r.url === `${TRADE}/api/v1/quotes`;

  async function settle(): Promise<void> {
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  /** Searches for the symbol, picks it, and answers its live price (an unpriced one when `quote` is null). */
  async function pick(symbol: string, quote: Quote | null = QUOTES[symbol]): Promise<void> {
    if (page.querySelector('[data-testid="ticket-change-instrument"]')) {
      field('ticket-change-instrument').click();
      await settle();
    }
    const box = field('instrument-search');
    box.value = symbol;
    box.dispatchEvent(new Event('input'));
    await settle();
    http
      .expectOne((r) => r.url === INSTRUMENTS_URL && r.params.get('q') === symbol)
      .flush(INSTRUMENTS.filter((instrument) => instrument.symbol === symbol));
    await settle();
    page.querySelector('[data-testid="instrument-search-option"]')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await settle();
    const answer = quote ?? { ...QUOTES[symbol], price: null, bid: null, ask: null, change: null, changePercent: null, stale: true };
    http.match(quotesRead).forEach((r) => r.flush([answer]));
    await settle();
  }

  function type(id: string, value: string): void {
    field(id).value = value;
    field(id).dispatchEvent(new Event('input'));
  }

  async function click(id: string): Promise<void> {
    field(id).click();
    await settle();
  }

  /** A limit order, as the old ticket placed one. */
  async function fill(order: { symbol?: string; side?: 'BUY' | 'SELL'; quantity?: string; price?: string }): Promise<void> {
    const symbol = order.symbol ?? 'INFY.NS';
    if (!text('ticket-instrument').includes(symbol)) {
      await pick(symbol);
    }
    if (order.side === 'SELL') {
      await click('ticket-side-sell');
    }
    await click('ticket-type-limit');
    type('ticket-quantity', order.quantity ?? '10');
    type('ticket-price', order.price ?? '1450.50');
    await settle();
  }

  async function submit(): Promise<void> {
    field('ticket-submit').click();
    await settle();
  }

  function respond(request: TestRequest, status = 'NEW'): void {
    const body = request.request.body;
    request.flush({ orderId: 'ORD-6f2b1c2a', status, message: 'Order accepted', symbol: body.symbol, side: body.side, quantity: body.quantity, price: body.price });
  }

  describe('a limit order', () => {
    beforeEach(() => render());

    it('submits a valid one and shows the status the API returned, including NEW', async () => {
      await fill({ symbol: 'INFY.NS', side: 'SELL' });
      await submit();

      const request = http.expectOne(ORDERS);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        accountId: 3,
        symbol: 'INFY.NS',
        side: 'SELL',
        quantity: 10,
        price: 1450.5,
        idempotencyKey: expect.stringMatching(/^[0-9a-f-]{36}$/),
      });
      respond(request);
      await settle();

      expect(text('ticket-status')).toBe('NEW');
      const result = text('ticket-result');
      expect(result).toContain('still working');
      expect(result).toContain('Your order to sell 10 INFY.NS');
      // The order id is the platform's, not the customer's.
      expect(result).not.toContain('ORD-');
      expect(field('ticket-result').querySelector('a')?.getAttribute('href')).toBe('/orders');
    });

    it('blocks an invalid quantity before submission', async () => {
      for (const quantity of ['0', '-5', '1.5', 'ten']) {
        await fill({ quantity });
        await submit();

        http.expectNone(ORDERS);
        expect(page.textContent, quantity).toContain('Enter a whole number of units, 1 or more.');
      }
    });

    it('blocks a price with more than two decimal places, or not above zero, before submission', async () => {
      for (const price of ['10.555', '0', '0.00']) {
        await fill({ price });
        await submit();

        http.expectNone(ORDERS);
        expect(page.textContent, price).toContain('Enter a price above zero, with at most two decimal places.');
      }
    });

    it('sends nothing until an instrument is picked', async () => {
      await click('ticket-type-limit');
      type('ticket-quantity', '10');
      type('ticket-price', '100');
      await submit();

      http.expectNone(ORDERS);
      expect(page.textContent).toContain('Search for an instrument and pick it from the list.');
    });

    it('shows the picked instrument in place of the search, and Change goes back to it', async () => {
      await pick('120503');

      expect(text('ticket-instrument')).toContain('Bluechip Equity Fund');
      expect(text('ticket-instrument')).not.toContain('120503');
      await click('ticket-change-instrument');
      expect(page.querySelector('[data-testid="ticket-instrument"]')).toBeNull();
      expect(field('instrument-search')).not.toBeNull();
    });

    it('shows the mapped message for a business-rule rejection, not the API text', async () => {
      await fill({});
      await submit();

      http.expectOne(ORDERS).flush({ errorCode: 'ORD-400', message: 'Insufficient funds' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      const alert = page.querySelector('[role="alert"]')?.textContent ?? '';
      expect(alert).toContain(ERROR_MESSAGES['ORD-400']);
      expect(alert).not.toContain('Insufficient funds');
    });

    it("shows the account by the reference the customer knows, read-only, and sends the token's account", async () => {
      expect(field('ticket-account').value).toBe('ACC-000003');
      expect((field('ticket-account') as HTMLInputElement).readOnly).toBe(true);

      await fill({});
      await submit();

      const request = http.expectOne(ORDERS);
      expect(request.request.body.accountId).toBe(3);
      respond(request);
      await settle();
    });

    it('reuses the idempotency key when retrying an order whose response never arrived', async () => {
      await fill({});
      await submit();
      const first = http.expectOne(ORDERS);
      const key = first.request.body.idempotencyKey;
      first.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
      await settle();
      expect(page.querySelector('[role="alert"]')?.textContent).toContain(UNREACHABLE_MESSAGE);

      await submit();
      const retry = http.expectOne(ORDERS);
      expect(retry.request.body.idempotencyKey).toBe(key);
      respond(retry);
      await settle();

      await fill({ quantity: '5' });
      await submit();
      const next = http.expectOne(ORDERS);
      expect(next.request.body.idempotencyKey).not.toBe(key);
      respond(next);
      await settle();
    });
  });

  describe('opened from a link', () => {
    beforeEach(() => render());

    it('opens filled in from a Sell link, once a lookup shows the symbol is tradable', async () => {
      fixture.componentRef.setInput('symbol', 'tcs.ns');
      fixture.componentRef.setInput('side', 'SELL');
      await settle();
      http.expectOne((r) => r.url === INSTRUMENTS_URL && r.params.get('symbols') === 'TCS.NS').flush([INSTRUMENTS[1]]);
      await settle();
      http.match(quotesRead).forEach((r) => r.flush([QUOTES['TCS.NS']]));
      await settle();

      expect(text('ticket-instrument')).toContain('TCS.NS');
      expect(field('ticket-side-sell').checked).toBe(true);
      expect(text('ticket-title')).toContain('Sell TCS.NS');
    });

    it('ignores a linked symbol that is not tradable, and a side that is neither BUY nor SELL', async () => {
      fixture.componentRef.setInput('symbol', 'MERSTL');
      fixture.componentRef.setInput('side', 'SHORT');
      await settle();
      http
        .expectOne((r) => r.url === INSTRUMENTS_URL && r.params.get('symbols') === 'MERSTL')
        .flush([{ symbol: 'MERSTL', name: 'Meridian Steel Ltd', type: 'STOCK', exchange: 'NSE', tradable: false }]);
      await settle();

      expect(page.querySelector('[data-testid="ticket-instrument"]')).toBeNull();
      expect(field('ticket-side-buy').checked).toBe(true);
    });

    it('says so when a linked symbol cannot be looked up', async () => {
      fixture.componentRef.setInput('symbol', 'TCS.NS');
      await settle();
      http
        .expectOne((r) => r.url === INSTRUMENTS_URL)
        .flush({ errorCode: 'AUTH-401', message: 'x' }, { status: 401, statusText: 'Unauthorized' });
      await settle();

      expect(page.querySelector('[role="alert"]')?.textContent).toContain('Your session has expired');
    });
  });

  describe('with the live price', () => {
    it('shows the picked instrument at its live price, at market, with the limit box filled from it', async () => {
      await render();
      await pick('INFY.NS');

      expect(text('ticket-live-price')).toContain('1,450.00');
      expect(text('ticket-live-price')).toContain('+0.48%');
      expect(field('ticket-type-market').checked).toBe(true);
      await click('ticket-type-limit');
      expect(field('ticket-price').value).toBe('1450.00');
    });

    it('buys at market with half a per cent of room above the ask, and says what is set aside', async () => {
      await render();
      await pick('INFY.NS');
      type('ticket-quantity', '10');
      await settle();

      // 10 at the ask, 1,450.50; the limit 1,450.50 x 1.005 = 1,457.76.
      expect(text('ticket-cost')).toContain('14,505.00');
      expect(page.textContent).toContain('14,577.60 is set aside');
      await submit();

      const request = http.expectOne(ORDERS);
      expect(request.request.body).toMatchObject({ symbol: 'INFY.NS', side: 'BUY', quantity: 10, price: 1457.76 });
      respond(request);
      await settle();
    });

    it('sells at market with the same room below the bid', async () => {
      await render();
      await pick('INFY.NS');
      await click('ticket-side-sell');
      type('ticket-quantity', '5');
      await settle();
      await submit();

      // 1,449.50 x 0.995 = 1,442.25
      const request = http.expectOne(ORDERS);
      expect(request.request.body).toMatchObject({ side: 'SELL', quantity: 5, price: 1442.25 });
      respond(request);
      await settle();
    });

    it('offers only a limit when there is no live price', async () => {
      await render();
      await pick('TCS.NS', null);

      expect(field('ticket-type-market').disabled).toBe(true);
      expect(field('ticket-type-limit').checked).toBe(true);
      expect(text('ticket-live-price')).toContain('No live price right now');
    });

    it('shows the cash left after a buy, and stops one the available cash cannot cover', async () => {
      await render(10000);
      await pick('INFY.NS');
      await click('ticket-type-limit');
      type('ticket-quantity', '5');
      type('ticket-price', '1000');
      await settle();

      expect(text('ticket-cash')).toContain('10,000.00');
      expect(text('ticket-cash-after')).toContain('5,000.00');

      type('ticket-quantity', '11');
      await settle();
      expect(text('ticket-shortfall')).toContain('Not enough cash: this needs ₹11,000.00');
      expect(field('ticket-submit').disabled).toBe(true);
      await submit();
      http.expectNone(ORDERS);
    });

    it('shows what is held when selling, and stops a sale of more', async () => {
      await render();
      await pick('INFY.NS');
      await click('ticket-side-sell');

      expect(text('ticket-holding')).toBe('20');
      type('ticket-quantity', '21');
      await settle();
      expect(text('ticket-shortfall')).toContain("You hold 20, so you can't sell 21");
      expect(field('ticket-submit').disabled).toBe(true);
    });

    it('buys a fund by amount: the whole units it covers at the NAV', async () => {
      await render();
      await pick('120503');

      expect(page.querySelector('[data-testid="ticket-quantity"]')).toBeNull();
      type('ticket-amount', '5000');
      await settle();
      // 5,000 / 50.1234 = 99.75: 99 units.
      expect(text('ticket-units')).toContain('99 units at the NAV');
      await submit();

      const request = http.expectOne(ORDERS);
      expect(request.request.body).toMatchObject({ symbol: '120503', side: 'BUY', quantity: 99, price: 50.13 });
      respond(request);
      await settle();
      expect(text('ticket-result')).toContain('Bluechip Equity Fund');
      expect(field('ticket-result').querySelector('a')?.getAttribute('href')).toBe('/orders');
    });

    it('stops an amount too small for one unit', async () => {
      await render();
      await pick('120503');
      type('ticket-amount', '20');
      await settle();
      await submit();

      http.expectNone(ORDERS);
      expect(page.textContent).toContain('Enter an amount that buys at least one unit at the NAV.');
    });

    it('retries a market order under the same key', async () => {
      await render();
      await pick('INFY.NS');
      type('ticket-quantity', '1');
      await settle();
      await submit();
      const first = http.expectOne(ORDERS);
      const key = first.request.body.idempotencyKey;
      first.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
      await settle();

      await submit();
      const retry = http.expectOne(ORDERS);
      expect(retry.request.body.idempotencyKey).toBe(key);
      respond(retry);
      await settle();
    });
  });
});
