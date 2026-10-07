import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { Session } from '../../core/session/session';
import { ALERTS_POLL_MS, Alerts } from './alerts';

const ALERTS = 'http://trade.test/api/v1/accounts/3/alerts';
const WAITING = { id: 5, symbol: 'ITC.NS', direction: 'ABOVE', threshold: 270, status: 'ACTIVE', createdAt: '2026-10-06T04:00:00Z' };
const FIRED = {
  id: 4, symbol: 'SBIN.NS', direction: 'BELOW', threshold: 780, status: 'TRIGGERED', createdAt: '2026-10-05T04:00:00Z',
  triggeredAt: '2026-10-06T04:35:00Z', triggeredPrice: 779.5, notificationId: '0b1d7c9e-1f2a-4b3c-8d4e-5f6a7b8c9d0e',
};
const CANCELLED = { ...WAITING, id: 3, status: 'CANCELLED' };

describe('Alerts', () => {
  let fixture: ComponentFixture<Alerts>;
  let page: HTMLElement;
  let http: HttpTestingController;

  async function settle(): Promise<void> {
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  async function render(answer: object[]): Promise<void> {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' }),
        { provide: ALERTS_POLL_MS, useValue: 3_600_000 },
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Alerts);
    page = fixture.nativeElement as HTMLElement;
    await settle();
    http.expectOne(ALERTS).flush(answer);
    await settle();
  }

  afterEach(() => http.verify());

  const texts = (id: string) => [...page.querySelectorAll(`[data-testid="${id}"]`)].map((e) => e.textContent?.replace(/\s+/g, ' ').trim());

  it('shows each alert with its condition and its state, a fired one with the price that crossed it', async () => {
    await render([WAITING, FIRED, CANCELLED]);

    expect(texts('alert-condition')).toEqual(['Rises to ₹270.00', 'Falls to ₹780.00', 'Rises to ₹270.00']);
    expect(texts('alert-status')).toEqual(['Waiting', 'Fired at ₹779.50, 6 Oct, 10:05', 'Cancelled']);
    expect(page.querySelector('[data-testid="alert-notification"]')?.getAttribute('href')).toBe('/notifications');
  });

  it('offers to cancel one waiting, and to re-arm one that fired or was cancelled', async () => {
    await render([WAITING, FIRED, CANCELLED]);

    expect(page.querySelectorAll('[data-testid="alert-cancel"]').length).toBe(1);
    expect(page.querySelectorAll('[data-testid="alert-rearm"]').length).toBe(2);
  });

  it('re-arms one, and reads the list again', async () => {
    await render([FIRED]);

    page.querySelector<HTMLButtonElement>('[data-testid="alert-rearm"]')!.click();
    await settle();
    const rearm = http.expectOne(`${ALERTS}/4/rearm`);
    expect(rearm.request.method).toBe('POST');
    rearm.flush({ ...FIRED, status: 'ACTIVE', triggeredAt: null, triggeredPrice: null, notificationId: null });
    await settle();
    http.expectOne(ALERTS).flush([{ ...FIRED, status: 'ACTIVE', triggeredAt: null, triggeredPrice: null, notificationId: null }]);
    await settle();

    expect(texts('alert-status')).toEqual(['Waiting']);
  });

  it('says why when a re-arm is refused at the cap', async () => {
    await render([CANCELLED]);

    page.querySelector<HTMLButtonElement>('[data-testid="alert-rearm"]')!.click();
    await settle();
    http.expectOne(`${ALERTS}/3/rearm`).flush({ errorCode: 'LIM-409', message: 'x' }, { status: 409, statusText: 'Conflict' });
    await settle();

    expect(page.textContent).toContain('20 alerts');
  });

  it('cancels one with a DELETE', async () => {
    await render([WAITING]);

    page.querySelector<HTMLButtonElement>('[data-testid="alert-cancel"]')!.click();
    await settle();
    const cancel = http.expectOne(`${ALERTS}/5`);
    expect(cancel.request.method).toBe('DELETE');
    cancel.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    http.expectOne(ALERTS).flush([CANCELLED]);
    await settle();

    expect(texts('alert-status')).toEqual(['Cancelled']);
  });

  it('says where to set one when there are none', async () => {
    await render([]);

    expect(page.querySelector('[data-testid="alerts-empty"]')?.textContent).toContain("any stock's page");
  });
});
