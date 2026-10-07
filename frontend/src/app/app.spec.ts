import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { testToken } from '../testing/tokens';
import { App } from './app';
import { provideClients } from './core/api/provide-clients';
import { Session } from './core/session/session';

@Component({ template: '' })
class Blank {}

describe('App', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([{ path: 'sign-in', component: Blank }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' }),
      ],
    }).compileComponents();
  });

  it('renders the shell: a header and the main region the routes render into', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('header .brand')?.textContent).toContain('YELLOW // TRADE');
    expect(page.querySelector('main#main router-outlet')).not.toBeNull();
    expect(page.querySelector('[data-testid="sign-out"]')).toBeNull();
    // A visitor has no market watch: it lives beside the signed-in screens.
    expect(page.querySelector('app-market-watch')).toBeNull();
  });

  it('signing out clears the session and returns to sign-in', async () => {
    const session = TestBed.inject(Session);
    session.start(testToken({ accountId: 3 }));
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    TestBed.inject(HttpTestingController).expectOne('http://trade.test/api/v1/accounts/3').flush({
      id: 3, accountId: 'ACC-000003', holderName: 'Rohan Nair', cashBalance: 1, status: 'ACTIVE', version: 1, lastUpdated: '2026-10-05T00:00:00Z',
    });
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    // The customer's name and the account reference they know -- never the
    // token's numeric key, which is the database's.
    expect(page.querySelector('[data-testid="header-account"]')?.textContent).toContain('Rohan Nair');
    expect(page.querySelector('[data-testid="header-account"]')?.textContent).toContain('ACC-000003');
    expect(page.querySelector('header')?.textContent).not.toMatch(/Account\s+3\b/);
    expect(page.querySelector('aside[aria-label="Watchlist"] app-market-watch')).not.toBeNull();

    page.querySelector<HTMLButtonElement>('[data-testid="sign-out"]')!.click();
    await fixture.whenStable();

    expect(session.accessToken()).toBeNull();
    expect(sessionStorage.length).toBe(0);
    expect(TestBed.inject(Router).url).toBe('/sign-in');
    expect(page.querySelector('[data-testid="sign-out"]')).toBeNull();
    expect(page.querySelector('app-market-watch')).toBeNull();
  });
});
