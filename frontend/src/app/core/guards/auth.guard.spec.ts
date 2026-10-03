import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, Routes, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { testToken } from '../../../testing/tokens';
import { routes as appRoutes } from '../../app.routes';
import { Session } from '../session/session';
import { authGuard } from './auth.guard';

@Component({ template: '<p>guarded page</p>' })
class GuardedPage {}

@Component({ template: '<p>sign-in page</p>' })
class SignInPage {}

const routes: Routes = [
  { path: 'sign-in', component: SignInPage },
  { path: '', canActivateChild: [authGuard], children: [{ path: 'orders', component: GuardedPage }] },
];

describe('authGuard', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
  });

  it('blocks unauthenticated navigation and redirects to sign-in, carrying the return address', async () => {
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/orders?status=NEW');

    expect(TestBed.inject(Router).url).toBe('/sign-in?returnUrl=%2Forders%3Fstatus%3DNEW');
    expect(harness.routeNativeElement?.textContent).toContain('sign-in page');
  });

  it('allows authenticated navigation', async () => {
    TestBed.inject(Session).start(testToken());
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/orders');

    expect(TestBed.inject(Router).url).toBe('/orders');
    expect(harness.routeNativeElement?.textContent).toContain('guarded page');
  });

  it('treats an expired session as signed out', async () => {
    TestBed.inject(Session).start(testToken({ exp: Math.floor(Date.now() / 1000) - 1 }));
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/orders');

    expect(TestBed.inject(Router).url).toBe('/sign-in?returnUrl=%2Forders');
  });
});

describe('the application routes', () => {
  it('run the guard on every route but sign-in', () => {
    for (const route of appRoutes) {
      if (route.path === 'sign-in') {
        expect(route.canActivate ?? route.canActivateChild, 'sign-in is open').toBeUndefined();
        continue;
      }
      expect(route.canActivateChild, `route "${route.path}"`).toContain(authGuard);
    }
  });
});
