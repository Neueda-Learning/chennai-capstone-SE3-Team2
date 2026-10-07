import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { Session } from '../../core/session/session';
import { NOTIFICATIONS_POLL_MS, Notifications } from './notifications';

const TRADE = 'http://trade.test';
const HISTORY = `${TRADE}/api/v1/accounts/3/notifications?limit=50`;

const FILLED = {
  id: 'n-1', kind: 'ORDER_FILLED', subject: 'Bought 2 ITC.NS at ₹399.50', body: 'Your order to buy 2 ITC.NS was executed.',
  channel: 'EMAIL', destination: 'r•••@example.com', status: 'SENT', createdAt: '2026-10-06T04:30:00Z', sentAt: '2026-10-06T04:30:02Z', readAt: null,
};
const REJECTED = {
  id: 'n-2', kind: 'ORDER_REJECTED', subject: 'Order rejected: buy 2 ITC.NS', body: 'Your order to buy 2 ITC.NS was rejected.',
  channel: 'IN_APP', destination: null, status: 'SENT', createdAt: '2026-10-06T04:20:00Z', sentAt: '2026-10-06T04:20:01Z', readAt: '2026-10-06T04:21:00Z',
};
const QUEUED = { ...REJECTED, id: 'n-3', channel: null, status: 'QUEUED', sentAt: null, readAt: null };
const FAILED = { ...FILLED, id: 'n-4', status: 'FAILED', sentAt: null };

describe('Notifications', () => {
  let fixture: ComponentFixture<Notifications>;
  let page: HTMLElement;
  let http: HttpTestingController;

  async function settle(): Promise<void> {
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  async function render(answer: object[] | 'error'): Promise<void> {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
        { provide: NOTIFICATIONS_POLL_MS, useValue: 3_600_000 },
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Notifications);
    page = fixture.nativeElement as HTMLElement;
    await settle();
    const request = http.expectOne(HISTORY);
    if (answer === 'error') {
      request.flush({ errorCode: 'ACC-403', message: 'Account not accessible' }, { status: 403, statusText: 'Forbidden' });
    } else {
      request.flush(answer);
    }
    await settle();
  }

  afterEach(() => http.verify());

  const all = (id: string) => Array.from(page.querySelectorAll(`[data-testid="${id}"]`)).map((e) => e.textContent?.trim());

  it('leads to Settings with a button, to change how notifications reach you', async () => {
    await render([]);

    const how = page.querySelector<HTMLAnchorElement>('a[data-testid="notifications-settings"]')!;
    expect(how.getAttribute('href')).toBe('/settings');
    expect(how.classList).toContain('button');
  });

  it('shows every notification newest first, with where each one went', async () => {
    await render([FILLED, REJECTED]);

    expect(all('notification-subject')).toEqual(['Bought 2 ITC.NS at ₹399.50', 'Order rejected: buy 2 ITC.NS']);
    expect(all('notification-delivery')).toEqual(['Emailed to r•••@example.com', 'In the app']);
  });

  it('says plainly when one is still waiting, and when one could not be sent', async () => {
    await render([QUEUED, FAILED]);

    expect(all('notification-delivery')).toEqual(['Waiting to be sent', 'Could not be emailed to r•••@example.com; kept here']);
  });

  it('offers to mark only the unread ones read', async () => {
    await render([FILLED, REJECTED]);

    expect(page.querySelectorAll('[data-testid="notification-read"]').length).toBe(1);
    expect(page.querySelectorAll('.item.unread').length).toBe(1);
  });

  it('marks one read for the token account, and asks the bell to count again', async () => {
    await render([FILLED]);

    page.querySelector<HTMLButtonElement>('[data-testid="notification-read"]')!.click();
    await settle();
    const read = http.expectOne(`${TRADE}/api/v1/accounts/3/notifications/n-1/read`);
    expect(read.request.method).toBe('POST');
    read.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    http.expectOne(`${TRADE}/api/v1/accounts/3/notifications/unread`).flush({ unread: 0 });
    await settle();

    expect(page.querySelector('[data-testid="notification-read"]')).toBeNull();
    expect(page.querySelectorAll('.item.unread').length).toBe(0);
  });

  it('says what will land here when nothing has yet', async () => {
    await render([]);

    expect(page.querySelector('[data-testid="notifications-empty"]')?.textContent).toContain('executed, rejected or cancelled');
  });

  it('says why when the history cannot be read', async () => {
    await render('error');

    expect(page.querySelector('[data-testid="notifications-list"]')).toBeNull();
    expect(page.textContent).toContain("isn't the account you signed in with");
  });
});
