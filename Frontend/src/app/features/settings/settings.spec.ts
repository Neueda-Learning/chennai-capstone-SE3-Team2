import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { ERROR_MESSAGES } from '../../core/errors/error-messages';
import { Session } from '../../core/session/session';
import { Settings } from './settings';

const TRADE = 'http://trade.test';
const PREFERENCES = `${TRADE}/api/v1/accounts/3/preferences`;
const DEFAULTS = {
  accountId: 3, defaultAccountId: 3, landingScreen: 'dashboard', alertChannel: 'EMAIL', contact: 'r•••@example.com', stored: false, updatedAt: null,
};

describe('Settings', () => {
  let fixture: ComponentFixture<Settings>;
  let page: HTMLElement;
  let http: HttpTestingController;

  async function settle(): Promise<void> {
    for (let i = 0; i < 6; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  async function render(answer: object = DEFAULTS): Promise<void> {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: TRADE, authApiUrl: 'http://auth.test' }),
      ],
    });
    TestBed.inject(Session).start(testToken({ accountId: 3 }));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Settings);
    page = fixture.nativeElement as HTMLElement;
    await settle();
    http.expectOne(`${TRADE}/api/v1/accounts/3`).flush({
      id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance: 1, status: 'ACTIVE', version: 1, lastUpdated: '2026-10-05T00:00:00Z',
    });
    http.expectOne(PREFERENCES).flush(answer);
    await settle();
  }

  afterEach(() => http.verify());

  const field = (id: string) => page.querySelector<HTMLInputElement & HTMLSelectElement>(`[data-testid="${id}"]`)!;

  it('shows what is in force, and says when it is the default', async () => {
    await render();

    expect(field('settings-landing').value).toBe('dashboard');
    expect(field('settings-channel-email').checked).toBe(true);
    expect(page.textContent).toContain('r•••@example.com');
    expect(field('settings-account').textContent).toContain('ACC-000003');
    expect(page.querySelector('[data-testid="settings-defaults"]')).not.toBeNull();
  });

  it('saves the landing screen and the channel for the token account, and says where sign-in will open', async () => {
    await render();

    field('settings-landing').value = 'holdings';
    field('settings-landing').dispatchEvent(new Event('change'));
    field('settings-channel-inapp').click();
    await settle();
    field('settings-save').click();
    await settle();

    const save = http.expectOne(PREFERENCES);
    expect(save.request.method).toBe('PUT');
    expect(save.request.body).toEqual({ defaultAccountId: 3, landingScreen: 'holdings', alertChannel: 'IN_APP' });
    save.flush({ ...DEFAULTS, landingScreen: 'holdings', alertChannel: 'IN_APP', contact: null, stored: true, updatedAt: '2026-10-06T10:00:00Z' });
    await settle();

    expect(page.querySelector('[data-testid="settings-saved"]')?.textContent).toContain('Holdings');
    expect(page.querySelector('[data-testid="settings-defaults"]')).toBeNull();
  });

  it('shows a stored preference as stored', async () => {
    await render({ ...DEFAULTS, landingScreen: 'market-watch', alertChannel: 'IN_APP', contact: null, stored: true });

    expect(field('settings-landing').value).toBe('market-watch');
    expect(field('settings-channel-inapp').checked).toBe(true);
    expect(page.querySelector('[data-testid="settings-defaults"]')).toBeNull();
  });

  it('says why when saving is refused', async () => {
    await render();

    field('settings-save').click();
    await settle();
    http.expectOne(PREFERENCES).flush({ errorCode: 'ACC-403', message: 'Account not accessible' }, { status: 403, statusText: 'Forbidden' });
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain(ERROR_MESSAGES['ACC-403']);
  });
});
