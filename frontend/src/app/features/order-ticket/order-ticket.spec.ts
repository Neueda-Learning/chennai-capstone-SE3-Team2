import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { ERROR_MESSAGES, UNREACHABLE_MESSAGE } from '../../core/errors/error-messages';
import { Session } from '../../core/session/session';
import { OrderTicket } from './order-ticket';

const TRADE = 'http://trade.test';
const ORDERS = `${TRADE}/api/v1/orders`;
const INSTRUMENTS = [
  { symbol: 'INFY.NS', name: 'Infosys Ltd', type: 'STOCK', exchange: 'NSE' },
  { symbol: 'TCS.NS', name: 'Tata Consultancy Services Ltd', type: 'STOCK', exchange: 'NSE' },
  { symbol: 'NIFTYBEES', name: 'Nifty 50 ETF', type: 'ETF', exchange: 'NSE' },
  { symbol: '120503', name: 'Bluechip Equity Fund', type: 'MF', exchange: null },
  { symbol: '130000', name: 'Axis Liquid Fund', type: 'MF', exchange: null },
];

describe('OrderTicket', () => {
  let fixture: ComponentFixture<OrderTicket>;
  let page: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrderTicket);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    // Who is signed in, as the customer knows the account.
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush({
      id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance: 1, status: 'ACTIVE', version: 1, lastUpdated: '2026-10-05T00:00:00Z',
    });
    await settle();
  });

  /** Answers the ticket's read of the tradable instruments. */
  async function listInstruments(): Promise<void> {
    http.expectOne(`${TRADE}/api/v1/instruments`).flush(INSTRUMENTS);
    await settle();
  }

  afterEach(() => http.verify());

  const field = (id: string) => page.querySelector<HTMLInputElement & HTMLSelectElement>(`[data-testid="${id}"]`)!;

  function fill(order: { symbol?: string; side?: string; quantity?: string; price?: string }): void {
    field('ticket-symbol').value = order.symbol ?? 'INFY.NS';
    field('ticket-symbol').dispatchEvent(new Event('change'));
    for (const [id, value] of Object.entries({
      'ticket-quantity': order.quantity ?? '10',
      'ticket-price': order.price ?? '1450.50',
    })) {
      field(id).value = value;
      field(id).dispatchEvent(new Event('input'));
    }
    if (order.side) {
      field('ticket-side').value = order.side;
      field('ticket-side').dispatchEvent(new Event('change'));
    }
  }

  async function submit(): Promise<void> {
    field('ticket-submit').click();
    await fixture.whenStable();
  }

  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  function respond(request: TestRequest, status = 'NEW'): void {
    const body = request.request.body;
    request.flush({ orderId: 'ORD-6f2b1c2a', status, message: 'Order accepted', symbol: body.symbol, side: body.side, quantity: body.quantity, price: body.price });
  }

  it('submits a valid order and shows the status the API returned, including NEW', async () => {
    await listInstruments();
    fill({ symbol: 'INFY.NS', side: 'SELL' });
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

    expect(field('ticket-status').textContent).toBe('NEW');
    const result = page.querySelector('[data-testid="ticket-result"]')!;
    expect(result.textContent).toContain('still working');
    expect(result.textContent).toContain('Your order to sell 10 INFY.NS');
    // The order id is the platform's, not the customer's.
    expect(result.textContent).not.toContain('ORD-');
    expect(result.querySelector('a')?.getAttribute('href')).toBe('/stocks');
  });

  it("after a fund order, sends the customer to the mutual funds dashboard, and names the fund", async () => {
    await listInstruments();
    fill({ symbol: '120503', quantity: '10', price: '50' });
    await submit();
    respond(http.expectOne(ORDERS));
    await settle();

    const result = page.querySelector('[data-testid="ticket-result"]')!;
    expect(result.textContent).toContain('Bluechip Equity Fund');
    expect(result.textContent).not.toContain('120503');
    expect(result.querySelector('a')?.getAttribute('href')).toBe('/mutual-funds');
  });

  it('blocks an invalid quantity before submission', async () => {
    await listInstruments();
    for (const quantity of ['0', '-5', '1.5', 'ten']) {
      fill({ quantity });
      await submit();

      http.expectNone(ORDERS);
      expect(page.textContent, quantity).toContain('Enter a whole number of units, 1 or more.');
    }
  });

  it('blocks a price with more than two decimal places, or not above zero, before submission', async () => {
    await listInstruments();
    for (const price of ['10.555', '0', '0.00']) {
      fill({ price });
      await submit();

      http.expectNone(ORDERS);
      expect(page.textContent, price).toContain('Enter a price above zero, with at most two decimal places.');
    }
  });

  it('offers only the tradable instruments, grouped by type, and sends nothing until one is chosen', async () => {
    await listInstruments();

    const groups = [...field('ticket-symbol').querySelectorAll('optgroup')].map((group) => group.label);
    expect(groups).toEqual(['Stocks', 'ETFs', 'Mutual funds']);
    const options = [...field('ticket-symbol').querySelectorAll<HTMLOptionElement>('optgroup option')];
    expect(options.map((option) => option.value)).toEqual(['INFY.NS', 'TCS.NS', 'NIFTYBEES', '130000', '120503']);
    // A stock or ETF by its ticker, a fund by its name: nothing else. Funds
    // in name order, since a customer looks a fund up by its name.
    expect(options.map((option) => option.textContent?.trim())).toEqual([
      'INFY.NS',
      'TCS.NS',
      'NIFTYBEES',
      'Axis Liquid Fund',
      'Bluechip Equity Fund',
    ]);

    field('ticket-quantity').value = '10';
    field('ticket-quantity').dispatchEvent(new Event('input'));
    field('ticket-price').value = '100';
    field('ticket-price').dispatchEvent(new Event('input'));
    await submit();

    http.expectNone(ORDERS);
    expect(page.textContent).toContain('Choose an instrument from the list.');
  });

  it("opens filled in from a holding's Sell link, once the list shows the symbol is tradable", async () => {
    fixture.componentRef.setInput('symbol', 'tcs.ns');
    fixture.componentRef.setInput('side', 'SELL');
    await listInstruments();

    expect(field('ticket-symbol').value).toBe('TCS.NS');
    expect(field('ticket-side').value).toBe('SELL');
  });

  it('ignores a linked symbol that is not tradable, and a side that is neither BUY nor SELL', async () => {
    fixture.componentRef.setInput('symbol', 'NOPE.NS');
    fixture.componentRef.setInput('side', 'SHORT');
    await listInstruments();

    expect(field('ticket-symbol').value).toBe('');
    expect(field('ticket-side').value).toBe('BUY');
  });

  it('says so when the instruments cannot be read', async () => {
    http.expectOne(`${TRADE}/api/v1/instruments`).flush({ errorCode: 'AUTH-401', message: 'x' }, { status: 401, statusText: 'Unauthorized' });
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Your session has expired');
  });

  it('shows the mapped message for a business-rule rejection, not the API text', async () => {
    await listInstruments();
    fill({});
    await submit();

    http.expectOne(ORDERS).flush({ errorCode: 'ORD-400', message: 'Insufficient funds' }, { status: 400, statusText: 'Bad Request' });
    await settle();

    const alert = page.querySelector('[role="alert"]')?.textContent ?? '';
    expect(alert).toContain(ERROR_MESSAGES['ORD-400']);
    expect(alert).not.toContain('Insufficient funds');
  });

  it("shows the account by the reference the customer knows, read-only, and sends the token's account", async () => {
    await listInstruments();
    expect(field('ticket-account').value).toBe('ACC-000003');
    expect(field('ticket-account').readOnly).toBe(true);

    fill({});
    await submit();

    const request = http.expectOne(ORDERS);
    expect(request.request.body.accountId).toBe(3);
    respond(request);
    await settle();
  });

  it('reuses the idempotency key when retrying an order whose response never arrived', async () => {
    await listInstruments();
    fill({});
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

    fill({ quantity: '5' });
    await submit();
    const next = http.expectOne(ORDERS);
    expect(next.request.body.idempotencyKey).not.toBe(key);
    respond(next);
    await settle();
  });
});
