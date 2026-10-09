import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PreferencesApi } from './preferences-api';
import { provideClients } from './provide-clients';

const TRADE = 'http://trade.test';

describe('PreferencesApi', () => {
  let api: PreferencesApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' })],
    });
    api = TestBed.inject(PreferencesApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('opens sign-in on the stored landing screen', async () => {
    const landing = api.landingUrl(3);
    http.expectOne(`${TRADE}/api/v1/accounts/3/preferences`).flush({
      accountId: 3, defaultAccountId: 3, landingScreen: 'market-watch', alertChannel: 'EMAIL', contact: 'r•••@example.com', stored: true,
    });

    expect(await landing).toBe('/market-watch');
  });

  it('goes home when the preferences cannot be read: a preference never stops a sign-in', async () => {
    const landing = api.landingUrl(3);
    http.expectOne(`${TRADE}/api/v1/accounts/3/preferences`).flush({ errorCode: 'SRV-500', message: 'x' }, { status: 500, statusText: 'Error' });

    expect(await landing).toBe('/');
  });
});
