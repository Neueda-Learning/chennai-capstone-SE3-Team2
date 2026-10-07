import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../../core/api/provide-clients';
import { Session } from '../../core/session/session';
import { SignIn } from './sign-in';

@Component({ template: '' })
class Landing {}

const AUTH = 'http://auth.test';
const TRADE = 'http://trade.test';

/** Answers the read of the customer's preferences with this landing screen. */
function landsOn(http: HttpTestingController, screen: string): void {
  http.expectOne(`${TRADE}/api/v1/accounts/3/preferences`).flush({
    accountId: 3, defaultAccountId: 3, landingScreen: screen, alertChannel: 'EMAIL', contact: 'r•••@example.com', stored: true,
  });
}

describe('SignIn', () => {
  let fixture: ComponentFixture<SignIn>;
  let page: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: '', component: Landing },
            { path: 'orders', component: Landing },
            { path: 'holdings', component: Landing },
            { path: 'dashboard', component: Landing },
            { path: 'sign-in', component: SignIn },
          ],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: AUTH }),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SignIn);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function type(testId: string, value: string): void {
    const input = page.querySelector<HTMLInputElement>(`[data-testid="${testId}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submit(): Promise<void> {
    page.querySelector<HTMLButtonElement>('[data-testid="sign-in-submit"]')!.click();
    await fixture.whenStable();
  }

  /** Lets the awaited login and navigation run, then re-renders. */
  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
  }

  it('signs in with valid credentials, keeps the token, and opens on the landing screen they chose', async () => {
    type('sign-in-username', 'priya.menon');
    type('sign-in-password', 'correct horse battery staple');
    await submit();

    const login = http.expectOne(`${AUTH}/auth/login`);
    expect(login.request.body).toEqual({ username: 'priya.menon', password: 'correct horse battery staple' });
    const token = testToken({ accountId: 3 });
    login.flush({ accessToken: token, refreshToken: 'r'.repeat(64), tokenType: 'Bearer', expiresIn: 900 });
    await settle();
    landsOn(http, 'holdings');
    await settle();

    const session = TestBed.inject(Session);
    expect(session.isSignedIn()).toBe(true);
    expect(session.accessToken()).toBe(token);
    expect(session.refreshToken()).toBe('r'.repeat(64));
    expect(TestBed.inject(Router).url).toBe('/holdings');
  });

  it('goes home when the preferences cannot be read: a preference never stops a sign-in', async () => {
    type('sign-in-username', 'priya.menon');
    type('sign-in-password', 'correct horse battery staple');
    await submit();
    http.expectOne(`${AUTH}/auth/login`).flush({ accessToken: testToken({ accountId: 3 }), refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900 });
    await settle();
    http.expectOne(`${TRADE}/api/v1/accounts/3/preferences`).flush({ errorCode: 'SRV-500', message: 'x' }, { status: 500, statusText: 'Error' });
    await settle();

    expect(TestBed.inject(Session).isSignedIn()).toBe(true);
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('shows a readable error for invalid credentials, never the developer message', async () => {
    type('sign-in-username', 'priya.menon');
    type('sign-in-password', 'wrong password');
    await submit();

    http
      .expectOne(`${AUTH}/auth/login`)
      .flush({ errorCode: 'AUTH-401', message: 'Unauthorised' }, { status: 401, statusText: 'Unauthorized' });
    await settle();

    const alert = page.querySelector('[role="alert"]')?.textContent ?? '';
    expect(alert).toContain("That username and password don't match.");
    expect(alert).not.toContain('Unauthorised');
    expect(TestBed.inject(Session).isSignedIn()).toBe(false);
    expect(page.querySelector<HTMLInputElement>('[data-testid="sign-in-password"]')!.value).toBe('');
  });

  it('blocks empty fields with validation, and sends nothing', async () => {
    await submit();

    http.expectNone(`${AUTH}/auth/login`);
    expect(page.textContent).toContain('Enter your username.');
    expect(page.textContent).toContain('Enter your password.');
  });

  it('gives the username, password and submit controls stable test identifiers', () => {
    for (const id of ['sign-in-username', 'sign-in-password', 'sign-in-submit']) {
      expect(page.querySelector(`[data-testid="${id}"]`), id).not.toBeNull();
    }
  });

  describe('with a return address', () => {
    async function signInFrom(url: string): Promise<void> {
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(url, SignIn);
      const form = harness.routeNativeElement!;
      expect(form.querySelector('[data-testid="sign-in-needed"]')).not.toBeNull();
      for (const [id, value] of [['sign-in-username', 'priya.menon'], ['sign-in-password', 'correct horse battery staple']]) {
        const input = form.querySelector<HTMLInputElement>(`[data-testid="${id}"]`)!;
        input.value = value;
        input.dispatchEvent(new Event('input'));
      }
      form.querySelector<HTMLButtonElement>('[data-testid="sign-in-submit"]')!.click();
      await harness.fixture.whenStable();
      http.expectOne(`${AUTH}/auth/login`).flush({ accessToken: testToken({ accountId: 3 }), refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900 });
      for (let i = 0; i < 5; i++) {
        await Promise.resolve();
      }
      await harness.fixture.whenStable();
      // No return address to follow: the stored landing screen is asked for.
      for (const read of http.match(`${TRADE}/api/v1/accounts/3/preferences`)) {
        read.flush({ accountId: 3, defaultAccountId: 3, landingScreen: 'dashboard', alertChannel: 'EMAIL', contact: null, stored: false });
      }
      for (let i = 0; i < 5; i++) {
        await Promise.resolve();
      }
      await harness.fixture.whenStable();
    }

    it('lands the user where they were going, not on the landing screen', async () => {
      await signInFrom('/sign-in?returnUrl=%2Forders');

      expect(TestBed.inject(Router).url).toBe('/orders');
    });

    it('refuses an off-origin return address and opens on the landing screen instead', async () => {
      await signInFrom('/sign-in?returnUrl=https%3A%2F%2Fevil.example%2Flogin');

      expect(TestBed.inject(Router).url).toBe('/dashboard');
    });
  });
});

