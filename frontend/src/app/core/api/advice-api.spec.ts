import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdviceApi } from './advice-api';
import { provideClients } from './provide-clients';

describe('AdviceApi', () => {
  it('reads a stock signal from the Trade REST API, the symbol escaped as a path segment', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' })],
    });
    const http = TestBed.inject(HttpTestingController);

    const read = TestBed.inject(AdviceApi).signal('M&M.NS');
    http.expectOne('http://trade.test/api/v1/advice/M%26M.NS').flush({ symbol: 'M&M.NS', direction: 'HOLD' });

    expect((await read).direction).toBe('HOLD');
    http.verify();
  });
});
