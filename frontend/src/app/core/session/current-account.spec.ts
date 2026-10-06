import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../api/provide-clients';
import { CurrentAccount } from './current-account';
import { Session } from './session';

const TRADE = 'http://trade.test';

describe('CurrentAccount', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' })],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const settle = async () => {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
  };

  it("reads the signed-in customer's name and account reference once a session starts", async () => {
    const current = TestBed.inject(CurrentAccount);
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    TestBed.tick();

    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush({
      id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance: 1, status: 'ACTIVE', version: 1, lastUpdated: '2026-10-05T00:00:00Z',
    });
    await settle();

    expect(current.account()?.accountId).toBe('ACC-000003');
    expect(current.account()?.holderName).toBe('Rohan Nair');
  });

  it('reads the account again when asked, after an order or a transfer moved its cash', async () => {
    const current = TestBed.inject(CurrentAccount);
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    TestBed.tick();
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush({
      id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance: 1000, status: 'ACTIVE', version: 1, lastUpdated: '2026-10-05T00:00:00Z',
    });
    await settle();

    const refreshed = current.refresh();
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush({
      id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance: 400, status: 'ACTIVE', version: 2, lastUpdated: '2026-10-05T00:01:00Z',
    });
    await refreshed;

    expect(current.account()?.cashBalance).toBe(400);
  });

  it('shares a read already on its way rather than sending a second', async () => {
    const current = TestBed.inject(CurrentAccount);
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    void current.refresh();
    TestBed.tick();

    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush({
      id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance: 1, status: 'ACTIVE', version: 1, lastUpdated: '2026-10-05T00:00:00Z',
    });
    await settle();
    expect(current.account()?.accountId).toBe('ACC-000003');
  });

  it('forgets the account on sign-out', async () => {
    const current = TestBed.inject(CurrentAccount);
    const session = TestBed.inject(Session);
    session.start(testToken({ accountId: 3 }));
    TestBed.tick();
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush({
      id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance: 1, status: 'ACTIVE', version: 1, lastUpdated: '2026-10-05T00:00:00Z',
    });
    await settle();

    session.end();
    TestBed.tick();

    expect(current.account()).toBeNull();
  });
});
