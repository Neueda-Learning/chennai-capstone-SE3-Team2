import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideClients } from './provide-clients';
import { StrategiesApi } from './strategies-api';

const STRATEGIES = 'http://trade.test/api/v1/accounts/3/strategies';
const STRATEGY = {
  id: 7, symbol: 'ITC.NS', side: 'BUY', quantity: 2, trigger: 'FALLS_THROUGH', triggerPrice: 250, maxSpend: 600,
  maxPosition: 20, enabled: false, status: 'ARMED', failures: 0, createdAt: '2026-10-07T04:00:00Z', lastFiredAt: null,
};

describe('StrategiesApi', () => {
  let api: StrategiesApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' })],
    });
    api = TestBed.inject(StrategiesApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates one with a POST to the Trade REST API', async () => {
    const request = { symbol: 'ITC.NS', side: 'BUY', quantity: 2, trigger: 'FALLS_THROUGH', triggerPrice: 250, maxSpend: 600, maxPosition: 20 } as const;
    const created = api.create(3, request);
    const sent = http.expectOne(STRATEGIES);
    expect(sent.request.method).toBe('POST');
    expect(sent.request.body).toEqual(request);
    sent.flush(STRATEGY);

    expect((await created).enabled).toBe(false);
  });

  it('switches one on or off with a PUT of the flag', async () => {
    const on = api.setEnabled(3, 7, true);
    const sent = http.expectOne(`${STRATEGIES}/7/enabled`);
    expect(sent.request.method).toBe('PUT');
    expect(sent.request.body).toEqual({ enabled: true });
    sent.flush({ ...STRATEGY, enabled: true });

    expect((await on).enabled).toBe(true);
  });

  it('deletes with a DELETE, and reads the runs with a GET', async () => {
    const removed = api.remove(3, 7);
    const deletion = http.expectOne(`${STRATEGIES}/7`);
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null, { status: 204, statusText: 'No Content' });
    await removed;

    const runs = api.runs(3, 7);
    http.expectOne(`${STRATEGIES}/7/runs`).flush([{ id: 1, at: '2026-10-07T04:00:30Z', quotePrice: 249.8, outcome: 'PLACED', reason: null }]);
    expect((await runs)[0].outcome).toBe('PLACED');
  });
});
