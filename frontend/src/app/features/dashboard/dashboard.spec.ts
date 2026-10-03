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

  async function render() {
    const fixture = TestBed.createComponent(Dashboard);
    await fixture.whenStable();
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
    http.expectOne(`${TRADE}/api/v1/accounts/3/orders`).flush([]);
    await settle(fixture);

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent).toContain(
      "This account can't place orders right now.",
    );
  });
});
