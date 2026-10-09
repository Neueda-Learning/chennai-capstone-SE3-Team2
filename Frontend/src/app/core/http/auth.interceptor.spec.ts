import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AccountsService } from '../../../generated/trade';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../api/provide-clients';
import { API_CONFIG } from '../config/api-config';
import { Session } from '../session/session';
import { authInterceptor } from './auth.interceptor';

const TRADE = 'http://localhost:8080';
const AUTH = 'http://localhost:3000';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let token: string;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { tradeApiUrl: TRADE, authApiUrl: AUTH } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    token = testToken();
    TestBed.inject(Session).start(token);
  });

  afterEach(() => backend.verify());

  /** The Authorization header the request actually went out with, or null. */
  function authorizationSentTo(url: string, method: 'GET' | 'POST' = 'GET'): string | null {
    (method === 'GET' ? http.get(url) : http.post(url, {})).subscribe();
    const request = backend.expectOne(url);
    request.flush({});
    return request.request.headers.get('Authorization');
  }

  it('attaches the bearer token to a request to the Trade REST API', () => {
    expect(authorizationSentTo(`${TRADE}/api/v1/accounts/3/orders`)).toBe(`Bearer ${token}`);
  });

  it('does not attach the bearer token to a request to a third-party origin', () => {
    expect(authorizationSentTo('https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1/quotes/AAPL')).toBeNull();
    expect(authorizationSentTo('https://analytics.example.com/collect', 'POST')).toBeNull();
  });

  it('attaches the bearer token to the protected Auth route', () => {
    expect(authorizationSentTo(`${AUTH}/auth/me`)).toBe(`Bearer ${token}`);
  });

  it('calls the unauthenticated auth routes without a header', () => {
    for (const path of ['/auth/login', '/auth/register', '/auth/refresh']) {
      expect(authorizationSentTo(`${AUTH}${path}`, 'POST'), path).toBeNull();
    }
  });

  it('does not attach the token to a lookalike of a platform origin', () => {
    expect(authorizationSentTo('http://localhost:8080.evil.example/api/v1/orders')).toBeNull();
    expect(authorizationSentTo('https://localhost:8080/api/v1/orders')).toBeNull();
    expect(authorizationSentTo('http://localhost:8081/api/v1/orders')).toBeNull();
  });

  it('does not attach the token to a platform origin outside its protected paths', () => {
    expect(authorizationSentTo(`${TRADE}/onboarding/applications`, 'POST')).toBeNull();
    expect(authorizationSentTo(`${AUTH}/internal/activation-tokens`, 'POST')).toBeNull();
  });

  it("does not attach the token to this application's own origin", () => {
    expect(authorizationSentTo('/assets/config.json')).toBeNull();
  });

  it('sends no header at all when signed out', () => {
    TestBed.inject(Session).end();

    expect(authorizationSentTo(`${TRADE}/api/v1/accounts/3`)).toBeNull();
  });
});

describe('the generated clients, without the interceptor', () => {
  it('set no Authorization header of their own, so the interceptor is the only source', () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: AUTH }),
      ],
    });
    TestBed.inject(Session).start(testToken());

    TestBed.inject(AccountsService).getOrders(3).subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne(`${TRADE}/api/v1/accounts/3/orders`);

    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush([]);
  });
});
