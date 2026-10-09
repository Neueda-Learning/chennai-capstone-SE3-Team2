import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../api/provide-clients';
import { Session } from '../session/session';
import { Inbox } from './inbox';

const UNREAD = 'http://trade.test/api/v1/accounts/3/notifications/unread';

describe('Inbox', () => {
  let inbox: Inbox;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' })],
    });
    inbox = TestBed.inject(Inbox);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('counts what is unread for the signed-in account', async () => {
    TestBed.inject(Session).start(testToken({ accountId: 3 }));

    const refreshed = inbox.refresh();
    http.expectOne(UNREAD).flush({ unread: 2 });
    await refreshed;

    expect(inbox.unread()).toBe(2);
  });

  it('keeps its last count when the service does not answer: the bell never shows an error', async () => {
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    const first = inbox.refresh();
    http.expectOne(UNREAD).flush({ unread: 2 });
    await first;

    const second = inbox.refresh();
    http.expectOne(UNREAD).flush({ errorCode: 'SRV-500', message: 'x' }, { status: 500, statusText: 'Error' });
    await second;

    expect(inbox.unread()).toBe(2);
  });

  it('is zero, and asks nothing, when nobody is signed in', async () => {
    await inbox.refresh();

    expect(inbox.unread()).toBe(0);
  });
});
