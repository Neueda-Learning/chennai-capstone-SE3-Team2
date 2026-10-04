import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { Session } from '../../core/session/session';
import { REREAD_POLICY } from '../../shared/reread/reread-policy';
import { Dashboard } from './dashboard';

const TRADE = 'http://trade.test';
const AUTH = 'http://auth.test';
const INSTRUMENTS = [
  { symbol: 'TCS.NS', name: 'Tata Consultancy Services Ltd', type: 'STOCK', exchange: 'NSE' },
  { symbol: '120503', name: 'Bluechip Equity Fund', type: 'MF', exchange: null },
  { symbol: 'SCH100001', name: 'Bluechip Growth Fund', type: 'MF', exchange: null },
];

describe('Dashboard', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: AUTH }),
        { provide: REREAD_POLICY, useValue: { intervalMs: 3000, maxRereads: 10, schedule: () => () => undefined } },
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(segment?: 'stocks' | 'mutual-funds') {
    const fixture = TestBed.createComponent(Dashboard);
    if (segment) {
      fixture.componentRef.setInput('segment', segment);
    }
    await fixture.whenStable();
    // What each instrument is called, and which dashboard it belongs on.
    http.expectOne(`${TRADE}/api/v1/instruments`).flush(INSTRUMENTS);
    return fixture;
  }

  async function settle(fixture: Awaited<ReturnType<typeof render>>): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  it("shows the token's account, who is signed in, and the account's orders", async () => {
    const fixture = await render();

    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush({
      id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance: 750000, status: 'ACTIVE', version: 7, lastUpdated: '2026-10-02T09:00:00Z',
    });
    http.expectOne(`${AUTH}/auth/me`).flush({ id: 'u-1', username: 'rohan.nair', accountId: 3, roles: ['CUSTOMER'] });
    http.expectOne(`${TRADE}/api/v1/accounts/3/positions`).flush([]);
    http.expectOne(`${TRADE}/api/v1/accounts/3/orders`).flush([]);
    await settle(fixture);

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[data-testid="account-ref"]')?.textContent).toBe('ACC-000003');
    expect(page.querySelector('[data-testid="cash-balance"]')?.textContent).toContain('750,000.00');
    expect(page.querySelector('[data-testid="signed-in-as"]')?.textContent).toContain('rohan.nair');
    expect(page.querySelector('app-blotter')).not.toBeNull();
  });

  it('says what went wrong when the account cannot be read', async () => {
    const fixture = await render();

    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush({ errorCode: 'ACC-403', message: 'x' }, { status: 403, statusText: 'Forbidden' });
    http.expectOne(`${AUTH}/auth/me`).flush({ id: 'u-1', username: 'rohan.nair', accountId: 3, roles: ['CUSTOMER'] });
    http.expectOne(`${TRADE}/api/v1/accounts/3/positions`).flush([]);
    http.expectOne(`${TRADE}/api/v1/accounts/3/orders`).flush([]);
    await settle(fixture);

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent).toContain(
      "This account can't place orders right now.",
    );
  });

  const account = (cashBalance: number) => ({
    id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance, status: 'ACTIVE', version: 7, lastUpdated: '2026-10-02T09:00:00Z',
  });
  const position = (symbol: string, quantity: number, averageCost: number) => ({ accountId: 3, symbol, quantity, averageCost });

  it('shows what the account holds, at its cost, each with a link to sell it', async () => {
    const fixture = await render();
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush(account(750000));
    http.expectOne(`${AUTH}/auth/me`).flush({ id: 'u-1', username: 'rohan.nair', accountId: 3, roles: ['CUSTOMER'] });
    http.expectOne(`${TRADE}/api/v1/accounts/3/positions`).flush([position('TCS.NS', 4, 3500.25), position('120503', 12.5, 41.2)]);
    http.expectOne(`${TRADE}/api/v1/accounts/3/orders`).flush([]);
    await settle(fixture);

    const page = fixture.nativeElement as HTMLElement;
    const rows = [...page.querySelectorAll('[data-testid="holdings-row"]')];
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('TCS.NS');
    expect(rows[0].textContent).toContain('3,500.25');
    expect(rows[0].textContent).toContain('14,001.00');
    // A fund by its name, never its scheme code.
    expect(rows[1].textContent).toContain('Bluechip Equity Fund');
    expect(rows[1].textContent).not.toContain('120503');
    expect(rows[1].textContent).toContain('12.5');
    expect(rows[0].querySelector('[data-testid="holdings-sell"]')?.getAttribute('href')).toBe('/trade?symbol=TCS.NS&side=SELL');
  });

  it('offers no Sell on a holding that can no longer be traded', async () => {
    const fixture = await render();
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush(account(750000));
    http.expectOne(`${AUTH}/auth/me`).flush({ id: 'u-1', username: 'rohan.nair', accountId: 3, roles: ['CUSTOMER'] });
    http.expectOne(`${TRADE}/api/v1/accounts/3/positions`).flush([position('MERSTL', 500, 84.3)]);
    http.expectOne(`${TRADE}/api/v1/accounts/3/orders`).flush([]);
    await settle(fixture);

    const row = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="holdings-row"]')!;
    expect(row.textContent).toContain('MERSTL');
    expect(row.querySelector('[data-testid="holdings-sell"]')).toBeNull();
  });

  it('on the mutual funds dashboard, shows funds by their type, and no second set of dashboard links', async () => {
    const fixture = await render('mutual-funds');
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush(account(750000));
    http.expectOne(`${AUTH}/auth/me`).flush({ id: 'u-1', username: 'rohan.nair', accountId: 3, roles: ['CUSTOMER'] });
    http.expectOne(`${TRADE}/api/v1/accounts/3/positions`).flush([position('TCS.NS', 4, 3500.25), position('SCH100001', 3, 10)]);
    http.expectOne(`${TRADE}/api/v1/accounts/3/orders`).flush([]);
    await settle(fixture);

    const page = fixture.nativeElement as HTMLElement;
    const rows = [...page.querySelectorAll('[data-testid="holdings-row"]')];
    expect(rows.map((row) => row.querySelector('td')?.textContent?.trim())).toEqual(['Bluechip Growth Fund']);
    expect(page.querySelector('h1')?.textContent).toContain('Mutual funds');
    // The header already links the two dashboards.
    expect(page.querySelector('.segment-nav')).toBeNull();
  });

  it('calls the cash what it is: available, not the raw balance', async () => {
    const fixture = await render();
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush(account(750000));
    http.expectOne(`${AUTH}/auth/me`).flush({ id: 'u-1', username: 'rohan.nair', accountId: 3, roles: ['CUSTOMER'] });
    http.expectOne(`${TRADE}/api/v1/accounts/3/positions`).flush([]);
    http.expectOne(`${TRADE}/api/v1/accounts/3/orders`).flush([]);
    await settle(fixture);

    const labels = [...(fixture.nativeElement as HTMLElement).querySelectorAll('.summary dt')].map((dt) => dt.textContent?.trim());
    expect(labels).toContain('Available cash');
  });

  it('says so when the account holds nothing', async () => {
    const fixture = await render();
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush(account(750000));
    http.expectOne(`${AUTH}/auth/me`).flush({ id: 'u-1', username: 'rohan.nair', accountId: 3, roles: ['CUSTOMER'] });
    http.expectOne(`${TRADE}/api/v1/accounts/3/positions`).flush([]);
    http.expectOne(`${TRADE}/api/v1/accounts/3/orders`).flush([]);
    await settle(fixture);

    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="holdings-empty"]')).not.toBeNull();
  });

  it('reads the cash and the holdings again when an order settles', async () => {
    const fixture = await render();
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush(account(750000));
    http.expectOne(`${AUTH}/auth/me`).flush({ id: 'u-1', username: 'rohan.nair', accountId: 3, roles: ['CUSTOMER'] });
    http.expectOne(`${TRADE}/api/v1/accounts/3/positions`).flush([]);
    http.expectOne(`${TRADE}/api/v1/accounts/3/orders`).flush([]);
    await settle(fixture);

    fixture.debugElement.query((el) => el.name === 'app-blotter').componentInstance.settled.emit();
    await fixture.whenStable();
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush(account(735998.99));
    http.expectOne(`${TRADE}/api/v1/accounts/3/positions`).flush([position('TCS.NS', 4, 3500.25)]);
    http.expectNone(`${AUTH}/auth/me`);
    await settle(fixture);

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('[data-testid="cash-balance"]')?.textContent).toContain('735,998.99');
    expect(page.querySelectorAll('[data-testid="holdings-row"]')).toHaveLength(1);
  });
});
