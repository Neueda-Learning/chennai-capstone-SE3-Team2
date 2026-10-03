import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../../generated/auth';
import { AccountsService } from '../../../generated/trade';
import { provideClients } from './provide-clients';

describe('provideClients', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' }),
      ],
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('sends the generated trade client to the Trade REST API', () => {
    TestBed.inject(AccountsService).getOrders(3).subscribe();

    TestBed.inject(HttpTestingController).expectOne('http://trade.test/api/v1/accounts/3/orders');
  });

  it('sends the generated auth client to the Auth service', () => {
    TestBed.inject(AuthService).login({ username: 'priya', password: 'secret' }).subscribe();

    const request = TestBed.inject(HttpTestingController).expectOne('http://auth.test/auth/login');
    expect(request.request.method).toBe('POST');
  });
});
