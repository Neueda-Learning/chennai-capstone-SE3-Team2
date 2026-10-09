import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PortfolioApi, isPricingUnavailable } from './portfolio-api';
import { provideClients } from './provide-clients';

const TRADE = 'http://trade.test';

describe('PortfolioApi', () => {
  let api: PortfolioApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' })],
    });
    api = TestBed.inject(PortfolioApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the contract routes on the Trade REST API', async () => {
    const summary = api.summary(3);
    const positions = api.positions(3);
    const pnl = api.pnl(3, true);
    http.expectOne(`${TRADE}/api/v1/portfolio/3`).flush({ accountId: 3 });
    http.expectOne(`${TRADE}/api/v1/portfolio/3/positions`).flush([]);
    http.expectOne(`${TRADE}/api/v1/portfolio/3/pnl?bySymbol=true`).flush({ accountId: 3 });

    await Promise.all([summary, positions, pnl]);
  });

  it("knows the contract's MKT-503 from any other failure", () => {
    const unavailable = new HttpErrorResponse({ status: 503, error: { errorCode: 'MKT-503', message: 'Pricing unavailable' } });
    const forbidden = new HttpErrorResponse({ status: 403, error: { errorCode: 'ACC-403', message: 'x' } });

    expect(isPricingUnavailable(unavailable)).toBe(true);
    expect(isPricingUnavailable(forbidden)).toBe(false);
    expect(isPricingUnavailable(new Error('x'))).toBe(false);
  });
});
