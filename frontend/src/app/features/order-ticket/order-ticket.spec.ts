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
  });

  afterEach(() => http.verify());

  const field = (id: string) => page.querySelector<HTMLInputElement & HTMLSelectElement>(`[data-testid="${id}"]`)!;

  function fill(order: { symbol?: string; side?: string; quantity?: string; price?: string }): void {
    for (const [id, value] of Object.entries({
      'ticket-symbol': order.symbol ?? 'INFY.NS',
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
    fill({ symbol: 'infy.ns', side: 'SELL' });
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
    expect(page.querySelector('[data-testid="ticket-result"]')?.textContent).toContain('still working');
  });

  it('blocks an invalid quantity before submission', async () => {
    for (const quantity of ['0', '-5', '1.5', 'ten']) {
      fill({ quantity });
      await submit();

      http.expectNone(ORDERS);
      expect(page.textContent, quantity).toContain('Enter a whole number of units, 1 or more.');
    }
  });

  it('blocks a price with more than two decimal places, or not above zero, before submission', async () => {
    for (const price of ['10.555', '0', '0.00']) {
      fill({ price });
      await submit();

      http.expectNone(ORDERS);
      expect(page.textContent, price).toContain('Enter a price above zero, with at most two decimal places.');
    }
  });

  it('blocks a symbol the contract does not allow before submission', async () => {
    fill({ symbol: 'INFY.LN' });
    await submit();

    http.expectNone(ORDERS);
    expect(page.textContent).toContain('Enter a symbol such as AAPL');
  });

  it('shows the mapped message for a business-rule rejection, not the API text', async () => {
    fill({});
    await submit();

    http.expectOne(ORDERS).flush({ errorCode: 'ORD-400', message: 'Insufficient funds' }, { status: 400, statusText: 'Bad Request' });
    await settle();

    const alert = page.querySelector('[role="alert"]')?.textContent ?? '';
    expect(alert).toContain(ERROR_MESSAGES['ORD-400']);
    expect(alert).not.toContain('Insufficient funds');
  });

  it('renders the account from the token, read-only, and sends that account', async () => {
    expect(field('ticket-account').value).toBe('3');
    expect(field('ticket-account').readOnly).toBe(true);

    fill({});
    await submit();

    const request = http.expectOne(ORDERS);
    expect(request.request.body.accountId).toBe(3);
    respond(request);
    await settle();
  });

  it('reuses the idempotency key when retrying an order whose response never arrived', async () => {
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
