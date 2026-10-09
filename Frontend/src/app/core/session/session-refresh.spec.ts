import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { testToken } from '../../../testing/tokens';
import { provideClients } from '../api/provide-clients';
import { Session } from './session';
import { RENEW_BEFORE_MS, RETRY_MS, SESSION_TIMER, SessionRefresh } from './session-refresh';

const AUTH = 'http://auth.test';
const REFRESH = `${AUTH}/auth/refresh`;

@Component({ template: '' })
class Blank {}

describe('SessionRefresh', () => {
  let http: HttpTestingController;
  let session: Session;
  /** Renewals asked for and not yet run, with when they were asked to run. */
  let timers: Array<{ task: () => void; ms: number }>;

  beforeEach(() => {
    sessionStorage.clear();
    timers = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'sign-in', component: Blank },
          { path: 'cash', component: Blank },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideClients({ tradeApiUrl: 'http://trade.test', authApiUrl: AUTH }),
        {
          provide: SESSION_TIMER,
          useValue: (task: () => void, ms: number) => {
            const timer = { task, ms };
            timers.push(timer);
            return () => (timers = timers.filter((t) => t !== timer));
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(Session);
  });

  afterEach(() => http.verify());

  /** Starts the service and lets its effect schedule. */
  function keepAlive(): SessionRefresh {
    const refresh = TestBed.inject(SessionRefresh);
    TestBed.tick();
    return refresh;
  }

  async function fire(): Promise<void> {
    const timer = timers.shift();
    expect(timer, 'a renewal was scheduled').toBeDefined();
    timer!.task();
    await Promise.resolve();
  }

  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
    TestBed.tick();
  }

  const inSeconds = (s: number) => Math.floor(Date.now() / 1000) + s;

  it('schedules the renewal a minute before the access token runs out', () => {
    session.start(testToken({ exp: inSeconds(900) }), 'refresh-1');
    keepAlive();

    expect(timers).toHaveLength(1);
    expect(timers[0].ms).toBeGreaterThan(900_000 - RENEW_BEFORE_MS - 2_000);
    expect(timers[0].ms).toBeLessThanOrEqual(900_000 - RENEW_BEFORE_MS);
  });

  it('exchanges the refresh token for a new pair, keeps both, and schedules the next renewal', async () => {
    session.start(testToken({ exp: inSeconds(900) }), 'refresh-1');
    keepAlive();
    await fire();

    const exchange = http.expectOne(REFRESH);
    expect(exchange.request.method).toBe('POST');
    expect(exchange.request.body).toEqual({ refreshToken: 'refresh-1' });
    const renewed = testToken({ exp: inSeconds(1800) });
    exchange.flush({ accessToken: renewed, refreshToken: 'refresh-2', tokenType: 'Bearer', expiresIn: 900 });
    await settle();

    expect(session.accessToken()).toBe(renewed);
    expect(session.refreshToken()).toBe('refresh-2');
    expect(sessionStorage.getItem('trading-ui.refreshToken')).toBe('refresh-2');
    expect(timers).toHaveLength(1);
    expect(timers[0].ms).toBeGreaterThan(1_700_000);
  });

  it('ends the session and goes to sign-in, coming back here, when the refresh token is refused', async () => {
    session.start(testToken({ exp: inSeconds(900) }), 'refresh-1');
    keepAlive();
    await TestBed.inject(Router).navigateByUrl('/cash');
    await fire();

    http.expectOne(REFRESH).flush({ errorCode: 'AUTH-401', message: 'Unauthorised' }, { status: 401, statusText: 'Unauthorized' });
    await settle();
    await new Promise((resolve) => setTimeout(resolve)); // the navigation to sign-in

    expect(session.accessToken()).toBeNull();
    expect(session.refreshToken()).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/sign-in?returnUrl=%2Fcash');
    expect(timers).toHaveLength(0);
  });

  it('keeps the session and tries again in 30 seconds when the renewal gets no answer', async () => {
    session.start(testToken({ exp: inSeconds(900) }), 'refresh-1');
    keepAlive();
    await fire();

    http.expectOne(REFRESH).error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    await settle();

    expect(session.isSignedIn()).toBe(true);
    expect(session.refreshToken()).toBe('refresh-1');
    expect(timers.map((t) => t.ms)).toEqual([RETRY_MS]);
  });

  it('sends one exchange when two renewals are asked for at once, because the token works only once', async () => {
    session.start(testToken({ exp: inSeconds(900) }), 'refresh-1');
    const refresh = keepAlive();

    const first = refresh.renew();
    const second = refresh.renew();
    http.expectOne(REFRESH).flush({ accessToken: testToken(), refreshToken: 'refresh-2', tokenType: 'Bearer', expiresIn: 900 });

    expect(await first).toBe(true);
    expect(await second).toBe(true);
  });

  it('schedules nothing, and sends nothing, without a refresh token', async () => {
    session.start(testToken({ exp: inSeconds(900) }));
    const refresh = keepAlive();

    expect(timers).toHaveLength(0);
    expect(await refresh.renew()).toBe(false);
  });

  it('stops the renewal on sign-out', () => {
    session.start(testToken({ exp: inSeconds(900) }), 'refresh-1');
    keepAlive();
    session.end();
    TestBed.tick();

    expect(timers).toHaveLength(0);
  });
});
