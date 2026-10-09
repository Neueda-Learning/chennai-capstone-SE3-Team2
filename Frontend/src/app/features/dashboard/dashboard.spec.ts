import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { InstrumentResponse, Quote } from '../../../generated/extensions';
import { OrderHistoryEntry } from '../../../generated/trade';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { LIVE_PRICES_POLICY } from '../../core/market/live-prices';
import { Session } from '../../core/session/session';
import { Dashboard } from './dashboard';

const TRADE = 'http://trade.test';
const AUTH = 'http://auth.test';
const LISTED: InstrumentResponse[] = [
  { symbol: 'TCS.NS', name: 'Tata Consultancy Services Ltd', type: 'STOCK', exchange: 'NSE', tradable: true },
  { symbol: '120503', name: 'Bluechip Equity Fund', type: 'MF', exchange: null, tradable: true },
];
const QUOTES: Quote[] = [
  { symbol: 'TCS.NS', price: 3600, change: 10, changePercent: 0.28, currency: 'INR', stale: false },
  { symbol: '120503', price: 45, currency: 'INR', stale: false },
];

const order = (id: string, status: OrderHistoryEntry['status'], createdOn: string, symbol = 'TCS.NS'): OrderHistoryEntry => ({
  orderId: `ORD-${id}`, accountId: 3, symbol, side: 'BUY', quantity: 1, price: 3500, status, createdOn,
});

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let page: HTMLElement;
  let http: HttpTestingController;

  async function settle(): Promise<void> {
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  function configure(): void {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: AUTH }),
        { provide: LIVE_PRICES_POLICY, useValue: { intervalMs: 15000, every: () => () => undefined, visible: () => true } },
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Dashboard);
    page = fixture.nativeElement as HTMLElement;
  }

  /** What the portfolio module answers for these priced positions. */
  const summaryOf = (positions: { marketValue: number; costBasis: number; unrealisedPnl: number }[]) => ({
    accountId: 3, baseCurrency: 'INR', cashBalance: 750000,
    marketValue: positions.reduce((sum, p) => sum + p.marketValue, 0),
    costBasis: positions.reduce((sum, p) => sum + p.costBasis, 0),
    unrealisedPnl: positions.reduce((sum, p) => sum + p.unrealisedPnl, 0),
    unrealisedPnlPercent: null, realisedPnl: 312.4,
    totalValue: 750000 + positions.reduce((sum, p) => sum + p.marketValue, 0),
    positionCount: positions.length, partial: false, asOf: '2026-10-07T04:00:00Z',
  });

  async function render(positions: ReturnType<typeof position>[], orders: OrderHistoryEntry[] = []): Promise<void> {
    configure();
    await settle();
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush({
      id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance: 750000, status: 'ACTIVE', version: 7, lastUpdated: '2026-10-02T09:00:00Z',
    });
    http.expectOne(`${AUTH}/auth/me`).flush({ id: 'u-1', username: 'rohan.nair', accountId: 3, roles: ['CUSTOMER'] });
    http.expectOne(`${TRADE}/api/v1/accounts/3/orders`).flush(orders);
    await settle();
    http.expectOne(`${TRADE}/api/v1/portfolio/3/positions`).flush(positions);
    http.expectOne(`${TRADE}/api/v1/portfolio/3`).flush(summaryOf(positions));
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
  /** A card's action: where it goes, what it says, and whether it looks like a button rather than a bare link. */
  const action = (id: string) => {
    const link = page.querySelector<HTMLAnchorElement>(`a[data-testid="${id}"]`);
    return link && { href: link.getAttribute('href'), text: link.textContent?.trim(), button: link.classList.contains('button') };
  };
  /** A holding as the portfolio module prices it, at the fixture's quote. */
  const position = (symbol: string, quantity: number, averageCost: number) => {
    const lastPrice = QUOTES.find((q) => q.symbol === symbol)!.price!;
    return {
      accountId: 3, symbol, quantity, averageCost, costBasis: quantity * averageCost, lastPrice,
      marketValue: quantity * lastPrice, unrealisedPnl: quantity * (lastPrice - averageCost),
      unrealisedPnlPercent: ((lastPrice - averageCost) / averageCost) * 100, currency: 'INR',
      priceAsOf: '2026-10-07T04:00:00Z', stale: false,
    };
  };

  it('greets the customer by first name, with the cash available and the account\'s state; the number is the header\'s', async () => {
    await render([]);

    expect(text('dashboard-greeting')).toBe('Hi, Rohan');
    expect(text('cash-balance')).toContain('750,000.00');
    expect(text('account-status')).toBe('ACTIVE');
    // The header already names the account: the card does not repeat its number.
    expect(page.querySelector('[data-testid="account-ref"]')).toBeNull();
    expect(text('signed-in-as')).toContain('rohan.nair');
    expect(action('dashboard-cash')).toEqual({ href: '/funds', text: 'Add or withdraw funds', button: true });
  });

  it('shows the holdings at their live value and P&L, and leads to Holdings', async () => {
    await render([position('TCS.NS', 4, 3500), position('120503', 100, 41.2)]);

    // 4 x 3,600 + 100 x 45 = 18,900 now; 14,000 + 4,120 cost.
    expect(text('dashboard-current')).toContain('18,900.00');
    expect(text('dashboard-pnl')).toContain('+₹780.00');
    expect(page.querySelector('[data-testid="dashboard-pnl"]')?.classList).toContain('up');
    expect(text('dashboard-holdings')).toContain('Holdings (2)');
    expect(action('dashboard-view-holdings')).toEqual({ href: '/holdings', text: 'View holdings', button: true });
  });

  it("shows the P&L realised by sales and the total with cash, as the portfolio module counts them", async () => {
    await render([position('TCS.NS', 4, 3500)]);

    expect(text('dashboard-realised')).toContain('+₹312.40');
    expect(text('dashboard-total')).toContain('₹764,400.00');
  });

  it('says so when nothing is held', async () => {
    await render([]);

    expect(text('dashboard-holdings')).toContain('Nothing held yet');
  });

  it('lists the five latest orders, newest first, and how many are still open', async () => {
    await render([], [
      order('a', 'FILLED', '2026-10-01T09:00:00Z'),
      order('b', 'NEW', '2026-10-06T09:00:00Z'),
      order('c', 'REJECTED', '2026-10-02T09:00:00Z'),
      order('d', 'FILLED', '2026-10-03T09:00:00Z'),
      order('e', 'CANCELLED', '2026-10-04T09:00:00Z'),
      order('f', 'NEW', '2026-10-05T09:00:00Z', '120503'),
    ]);

    const rows = [...page.querySelectorAll('[data-testid="dashboard-order"]')];
    expect(rows).toHaveLength(5);
    expect(rows[0].textContent).toContain('TCS.NS');
    expect(rows[1].textContent).toContain('Bluechip Equity Fund');
    expect(text('dashboard-open')).toBe('2 open');
    // Never the order id: it is the platform's.
    expect(text('dashboard-orders')).not.toContain('ORD-');
    expect(action('dashboard-view-orders')).toEqual({ href: '/orders', text: 'View all orders', button: true });
  });

  it('with no orders yet, offers a button to place the first one', async () => {
    await render([]);

    expect(text('dashboard-orders')).toContain('No orders yet.');
    expect(action('dashboard-first-order')).toEqual({ href: '/trade', text: 'Place your first order', button: true });
  });

  it('says what went wrong when the account cannot be read', async () => {
    configure();
    await settle();
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush({ errorCode: 'ACC-403', message: 'x' }, { status: 403, statusText: 'Forbidden' });
    http.expectOne(`${AUTH}/auth/me`).flush({ id: 'u-1', username: 'rohan.nair', accountId: 3, roles: ['CUSTOMER'] });
    http.expectOne(`${TRADE}/api/v1/accounts/3/orders`).flush([]);
    await settle();
    http.expectOne(`${TRADE}/api/v1/portfolio/3/positions`).flush([]);
    http.expectOne(`${TRADE}/api/v1/portfolio/3`).flush(summaryOf([]));
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain("This account can't place orders right now.");
  });
});
