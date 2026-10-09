import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../../generated/auth';
import { OnboardingService, PaymentsService } from '../../../generated/extensions';
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

  it('sends the generated extensions client to the Trade REST API', () => {
    TestBed.inject(PaymentsService).getTransfers(3).subscribe();
    TestBed.inject(OnboardingService)
      .applyForAccount({ name: 'Priya Menon', dob: '1990-05-17', email: 'p@example.com', phoneNumber: '+919812345611',
        pan: 'ABCPM1234Q', bankAccountNumber: '509876543210', ifsc: 'DEMO0000001' })
      .subscribe();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne('http://trade.test/api/v1/accounts/3/transfers');
    expect(http.expectOne('http://trade.test/onboarding/applications').request.method).toBe('POST');
  });
});

