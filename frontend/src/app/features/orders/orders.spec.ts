import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { InstrumentResponse } from '../../../generated/extensions';
import { OrderHistoryEntry } from '../../../generated/trade';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { Session } from '../../core/session/session';
import { REREAD_POLICY } from '../../shared/reread/reread-policy';
import { Orders } from './orders';

const TRADE = 'http://trade.test';
const LISTED: InstrumentResponse[] = [
  { symbol: 'INFY.NS', name: 'Infosys Ltd', type: 'STOCK', exchange: 'NSE', tradable: true },
  { symbol: '120716', name: 'UTI Nifty 50 Index Fund', type: 'MF', exchange: null, tradable: true },
];
const order = (id: string, status: OrderHistoryEntry['status'], symbol = 'INFY.NS'): OrderHistoryEntry => ({
  orderId: `ORD-${id}`, accountId: 3, symbol, side: 'BUY', quantity: 10, price: 1450, status, createdOn: `2026-10-0${id}T09:00:00Z`,
});

describe('Orders', () => {
  let fixture: ComponentFixture<Orders>;
  let page: HTMLElement;
  let http: HttpTestingController;

  async function settle(): Promise<void> {
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  async function render(orders: OrderHistoryEntry[]): Promise<void> {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
        { provide: REREAD_POLICY, useValue: { intervalMs: 3000, maxRereads: 10, schedule: () => () => undefined } },
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Orders);
    page = fixture.nativeElement as HTMLElement;
    await settle();
    // Who is signed in: the page refreshes their cash when an order settles.
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush({
      id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance: 1, status: 'ACTIVE', version: 1, lastUpdated: '2026-10-05T00:00:00Z',
    });
    http.expectOne(`${TRADE}/api/v1/accounts/3/orders`).flush(orders);
    await settle();
    for (const lookup of http.match((r) => r.url === `${TRADE}/api/v1/instruments`)) {
      const symbols = lookup.request.params.get('symbols')!.split(',');
      lookup.flush(LISTED.filter((i) => symbols.includes(i.symbol)));
    }
    await settle();
  }

  afterEach(() => http.verify());

  const statuses = (testid: string) =>
    [...page.querySelectorAll<HTMLElement>(`[data-testid="${testid}"] [data-testid="blotter-row"]`)].map((row) => row.dataset['status']);

  it('puts orders still working above the executed ones, each with its count', async () => {
    await render([order('1', 'FILLED'), order('2', 'NEW'), order('3', 'REJECTED'), order('4', 'CANCELLED', '120716')]);

    expect(page.querySelector('[data-testid="orders-open-title"]')?.textContent).toContain('Open orders (1)');
    expect(statuses('orders-open')).toEqual(['NEW']);
    expect(page.querySelector('[data-testid="orders-executed-title"]')?.textContent).toContain('Executed orders (3)');
    expect(statuses('orders-executed')).toEqual(['CANCELLED', 'REJECTED', 'FILLED']);
    // Cancel is only for what is still working.
    expect(page.querySelectorAll('[data-testid="orders-open"] [data-testid="blotter-cancel"]')).toHaveLength(1);
    expect(page.querySelectorAll('[data-testid="orders-executed"] [data-testid="blotter-cancel"]')).toHaveLength(0);
  });

  it('shows only stocks, or only funds, when asked', async () => {
    await render([order('1', 'FILLED'), order('4', 'CANCELLED', '120716')]);

    page.querySelector<HTMLButtonElement>('[data-testid="orders-filter-mutual-funds"]')!.click();
    await settle();

    expect(statuses('orders-executed')).toEqual(['CANCELLED']);
    expect(page.querySelector('[data-testid="orders-executed"]')?.textContent).toContain('UTI Nifty 50 Index Fund');
  });

  it('says so when nothing is open, or nothing executed yet', async () => {
    await render([]);

    expect(page.querySelector('[data-testid="orders-open-empty"]')).not.toBeNull();
    expect(page.querySelector('[data-testid="orders-executed-empty"]')).not.toBeNull();
  });
});
