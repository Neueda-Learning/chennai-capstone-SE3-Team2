import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { testToken } from '../testing/tokens';
import { App } from './app';
import { Session } from './core/session/session';

@Component({ template: '' })
class Blank {}

describe('App', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([{ path: 'sign-in', component: Blank }])],
    }).compileComponents();
  });

  it('renders the shell: a header and the main region the routes render into', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('header .brand')?.textContent).toContain('Trading Desk');
    expect(page.querySelector('main#main router-outlet')).not.toBeNull();
    expect(page.querySelector('[data-testid="sign-out"]')).toBeNull();
  });

  it('signing out clears the session and returns to sign-in', async () => {
    const session = TestBed.inject(Session);
    session.start(testToken({ accountId: 3 }));
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.account')?.textContent).toContain('Account 3');

    page.querySelector<HTMLButtonElement>('[data-testid="sign-out"]')!.click();
    await fixture.whenStable();

    expect(session.accessToken()).toBeNull();
    expect(sessionStorage.length).toBe(0);
    expect(TestBed.inject(Router).url).toBe('/sign-in');
    expect(page.querySelector('[data-testid="sign-out"]')).toBeNull();
  });
});
