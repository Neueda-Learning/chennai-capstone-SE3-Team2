import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideClients } from './provide-clients';
import { WatchlistsApi } from './watchlists-api';

const ACCOUNT = 'http://trade.test/api/v1/accounts/3';

describe('WatchlistsApi', () => {
  let api: WatchlistsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' })],
    });
    api = TestBed.inject(WatchlistsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('adds an instrument with a PUT, its symbol escaped as a path segment', async () => {
    const added = api.addItem(3, 1, 'M&M.NS');
    const request = http.expectOne(`${ACCOUNT}/watchlists/1/items/M%26M.NS`);
    expect(request.request.method).toBe('PUT');
    request.flush(null, { status: 204, statusText: 'No Content' });

    await added;
  });

  it('sets an alert with a POST of the symbol, direction and threshold', async () => {
    const set = api.setAlert(3, { symbol: 'ITC.NS', direction: 'ABOVE', threshold: 270 });
    const request = http.expectOne(`${ACCOUNT}/alerts`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ symbol: 'ITC.NS', direction: 'ABOVE', threshold: 270 });
    request.flush({ id: 5, symbol: 'ITC.NS', direction: 'ABOVE', threshold: 270, status: 'ACTIVE', createdAt: '2026-10-06T10:00:00Z' });

    expect((await set).status).toBe('ACTIVE');
  });

  it('re-arms with a POST to the alert', async () => {
    const rearmed = api.rearmAlert(3, 5);
    const request = http.expectOne(`${ACCOUNT}/alerts/5/rearm`);
    expect(request.request.method).toBe('POST');
    request.flush({ id: 5, symbol: 'ITC.NS', direction: 'ABOVE', threshold: 270, status: 'ACTIVE', createdAt: '2026-10-06T10:00:00Z' });

    await rearmed;
  });
});
