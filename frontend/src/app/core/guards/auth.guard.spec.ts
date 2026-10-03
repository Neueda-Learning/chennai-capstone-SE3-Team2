import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, Routes, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { testToken } from '../../../testing/tokens';
import { routes as appRoutes } from '../../app.routes';
import { provideClients } from '../api/provide-clients';
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
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: 'http://auth.test' }),
      ],
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

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

  it('renews an access token that ran out, if the refresh token still works, and lets the navigation through', async () => {
    TestBed.inject(Session).start(testToken({ exp: Math.floor(Date.now() / 1000) - 1 }), 'refresh-1');
    const harness = await RouterTestingHarness.create();

    const navigation = harness.navigateByUrl('/orders');
    await new Promise((resolve) => setTimeout(resolve));
    const exchange = TestBed.inject(HttpTestingController).expectOne('http://auth.test/auth/refresh');
    expect(exchange.request.body).toEqual({ refreshToken: 'refresh-1' });
    exchange.flush({ accessToken: testToken(), refreshToken: 'refresh-2', tokenType: 'Bearer', expiresIn: 900 });
    await navigation;

    expect(TestBed.inject(Router).url).toBe('/orders');
  });

  it('sends to sign-in when the access token ran out and the refresh token is refused', async () => {
    TestBed.inject(Session).start(testToken({ exp: Math.floor(Date.now() / 1000) - 1 }), 'refresh-1');
    const harness = await RouterTestingHarness.create();

    const navigation = harness.navigateByUrl('/orders');
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.inject(HttpTestingController)
      .expectOne('http://auth.test/auth/refresh')
      .flush({ errorCode: 'AUTH-401', message: 'Unauthorised' }, { status: 401, statusText: 'Unauthorized' });
    await navigation;

    expect(TestBed.inject(Router).url).toBe('/sign-in?returnUrl=%2Forders');
    expect(TestBed.inject(Session).refreshToken()).toBeNull();
  });

  it('treats an expired session as signed out', async () => {
    TestBed.inject(Session).start(testToken({ exp: Math.floor(Date.now() / 1000) - 1 }));
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/orders');

    expect(TestBed.inject(Router).url).toBe('/sign-in?returnUrl=%2Forders');
  });
});

describe('the application routes', () => {
  it('run the guard on every route but sign-in and opening an account', () => {
    const open = ['sign-in', 'apply'];
    for (const route of appRoutes) {
      if (open.includes(route.path ?? '')) {
        expect(route.canActivate ?? route.canActivateChild, `${route.path} is open`).toBeUndefined();
        continue;
      }
      expect(route.canActivateChild, `route "${route.path}"`).toContain(authGuard);
    }
  });
});
