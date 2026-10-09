import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { Session } from '../../core/session/session';
import { INBOX_POLL_MS, NotificationBell } from './notification-bell';

const UNREAD = 'http://trade.test/api/v1/accounts/3/notifications/unread';

describe('NotificationBell', () => {
  let fixture: ComponentFixture<NotificationBell>;
  let http: HttpTestingController;

  async function settle(): Promise<void> {
    for (let i = 0; i < 4; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  async function render(unread: number): Promise<HTMLElement> {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' }),
        { provide: INBOX_POLL_MS, useValue: 3_600_000 },
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(NotificationBell);
    await settle();
    http.expectOne(UNREAD).flush({ unread });
    await settle();
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => http.verify());

  it('shows how many are unread and links to the inbox', async () => {
    const bell = await render(3);

    expect(bell.querySelector('[data-testid="bell-count"]')?.textContent?.trim()).toBe('3');
    expect(bell.querySelector('a')?.getAttribute('href')).toBe('/notifications');
    expect(bell.querySelector('a')?.getAttribute('aria-label')).toBe('Notifications, 3 unread');
  });

  it('shows no number when nothing is unread', async () => {
    const bell = await render(0);

    expect(bell.querySelector('[data-testid="bell-count"]')).toBeNull();
    expect(bell.querySelector('a')?.getAttribute('aria-label')).toBe('Notifications, none unread');
  });

  it('caps the number it shows', async () => {
    const bell = await render(140);

    expect(bell.querySelector('[data-testid="bell-count"]')?.textContent?.trim()).toBe('99+');
  });
});
