import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificationsApi } from './notifications-api';
import { provideClients } from './provide-clients';

const TRADE = 'http://trade.test';

describe('NotificationsApi', () => {
  let api: NotificationsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' })],
    });
    api = TestBed.inject(NotificationsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the history of the token account from the Trade REST API', async () => {
    const history = api.history(3);
    const request = http.expectOne(`${TRADE}/api/v1/accounts/3/notifications?limit=50`);
    expect(request.request.method).toBe('GET');
    request.flush([]);

    expect(await history).toEqual([]);
  });

  it('reads the unread count as a number', async () => {
    const unread = api.unread(3);
    http.expectOne(`${TRADE}/api/v1/accounts/3/notifications/unread`).flush({ unread: 4 });

    expect(await unread).toBe(4);
  });

  it('marks one read with a POST', async () => {
    const read = api.markRead(3, 'abc');
    const request = http.expectOne(`${TRADE}/api/v1/accounts/3/notifications/abc/read`);
    expect(request.request.method).toBe('POST');
    request.flush(null, { status: 204, statusText: 'No Content' });

    await read;
  });
});
